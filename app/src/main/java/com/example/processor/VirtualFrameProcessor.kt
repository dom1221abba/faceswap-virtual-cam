package com.example.processor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.YuvImage
import android.graphics.ImageFormat
import android.graphics.BitmapFactory
import android.util.Log
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * High-performance frame converter and processor for Virtual Camera pipelines.
 *
 * Implements ITU-R BT.601 Color Matrix conversion from RGB (Bitmap) to NV21 (YUV420sp).
 *
 * --- MATEMÁTICA DA CONVERSÃO RGB PARA YUV (ITU-R BT.601) ---
 * O padrão ITU-R BT.601 define a relação entre o espaço de cores linear RGB e o espaço YUV:
 *
 * 1. Luminância (Y): Representa o brilho do pixel perceptível pelo olho humano.
 *    O olho humano é muito mais sensível ao verde do que ao azul:
 *    Y = 0.299000 * R + 0.587000 * G + 0.114000 * B
 *
 * 2. Crominância Azul (U / Cb):
 *    U = -0.168736 * R - 0.331264 * G + 0.500000 * B + 128
 *
 * 3. Crominância Vermelha (V / Cr):
 *    V = 0.500000 * R - 0.418688 * G - 0.081312 * B + 128
 *
 * OTIMIZAÇÃO PARA 30+ FPS (Aritmética de Ponto Fixo Inteiro):
 * Em vez de operações de ponto flutuante lentas por pixel, multiplicamos os coeficientes
 * por 256 (2^8) e usamos operações inteiras com deslocamento de bits (shr 8):
 *
 *    Y = ( 77 * R + 150 * G +  29 * B + 128) >> 8
 *    U = (-43 * R -  85 * G + 128 * B + 128) >> 8 + 128
 *    V = (128 * R - 107 * G -  21 * B + 128) >> 8 + 128
 *
 * ESTRUTURA DO BUFFER NV21 (YUV420sp):
 * - Tamanho total: width * height * 3 / 2 bytes.
 * - Plano Y: width * height bytes consecutivos (offset 0 até (W*H - 1)).
 * - Plano VU: (width * height) / 2 bytes com sub-amostragem 4:2:0.
 *   Para cada bloco 2x2 de pixels Y, há um par de crominância onde V precede U:
 *   [V0, U0, V1, U1, V2, U2, ...]
 */
class VirtualFrameProcessor {

    companion object {
        private const val TAG = "VirtualFrameProcessor"
        const val DEFAULT_WIDTH = 640
        const val DEFAULT_HEIGHT = 480
    }

    // Cached pre-allocated buffers to prevent GC pauses during continuous 30fps streaming
    private var cachedWidth = 0
    private var cachedHeight = 0
    private var pixelBuffer: IntArray = IntArray(0)
    private var scaledBitmap: Bitmap? = null
    private var scalingCanvas: Canvas? = null
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    /**
     * Resizes [sourceBitmap] to match target [width]x[height] and converts it directly into
     * [targetNv21Buffer] in-place, overwriting any previous frame data.
     *
     * @param sourceBitmap The RGB image selected by the user.
     * @param targetNv21Buffer Destination byte array of size (width * height * 3 / 2).
     * @param width Frame width (e.g., 640).
     * @param height Frame height (e.g., 480).
     * @return Execution latency in milliseconds.
     */
    fun processFrame(
        sourceBitmap: Bitmap,
        targetNv21Buffer: ByteArray,
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT
    ): Long {
        val startTime = System.nanoTime()
        val expectedSize = width * height * 3 / 2

        require(targetNv21Buffer.size >= expectedSize) {
            "Buffer size (${targetNv21Buffer.size}) is smaller than required NV21 size ($expectedSize)"
        }

        // Initialize or update cached buffers if resolution changed
        ensureBuffers(width, height)

        if (sourceBitmap.width == width && sourceBitmap.height == height) {
            sourceBitmap.getPixels(pixelBuffer, 0, width, 0, 0, width, height)
        } else {
            val targetCanvas = scalingCanvas ?: return 0L
            val targetBmp = scaledBitmap ?: return 0L
            val srcRect = Rect(0, 0, sourceBitmap.width, sourceBitmap.height)
            val dstRect = Rect(0, 0, width, height)
            targetCanvas.drawBitmap(sourceBitmap, srcRect, dstRect, paint)
            targetBmp.getPixels(pixelBuffer, 0, width, 0, 0, width, height)
        }

        // 3. High-speed in-place conversion from ARGB8888 to NV21
        encodeRgbToNv21(pixelBuffer, targetNv21Buffer, width, height)

        return (System.nanoTime() - startTime) / 1_000_000
    }

    /**
     * Fast in-place conversion from ARGB_8888 pixel array to NV21 byte array.
     */
    private fun encodeRgbToNv21(
        pixels: IntArray,
        nv21: ByteArray,
        width: Int,
        height: Int
    ) {
        val frameSize = width * height
        var yIndex = 0
        var uvIndex = frameSize

        var r: Int
        var g: Int
        var b: Int
        var y: Int
        var u: Int
        var v: Int

        for (j in 0 until height) {
            val isEvenRow = (j and 1) == 0
            val rowOffset = j * width

            for (i in 0 until width) {
                val color = pixels[rowOffset + i]

                r = (color shr 16) and 0xFF
                g = (color shr 8) and 0xFF
                b = color and 0xFF

                // ITU-R BT.601 integer fixed-point math
                // Y = 0.299*R + 0.587*G + 0.114*B
                y = (77 * r + 150 * g + 29 * b + 128) shr 8
                nv21[yIndex++] = (if (y < 0) 0 else if (y > 255) 255 else y).toByte()

                // Subsampling 4:2:0: calculate U and V only on even rows and even columns
                if (isEvenRow && (i and 1) == 0) {
                    // U = -0.1687*R - 0.3313*G + 0.5*B + 128
                    // V =  0.5*R - 0.4187*G - 0.0813*B + 128
                    u = ((-43 * r - 85 * g + 128 * b + 128) shr 8) + 128
                    v = ((128 * r - 107 * g - 21 * b + 128) shr 8) + 128

                    // NV21 layout: V (Cr) then U (Cb) interleaved
                    nv21[uvIndex++] = (if (v < 0) 0 else if (v > 255) 255 else v).toByte()
                    nv21[uvIndex++] = (if (u < 0) 0 else if (u > 255) 255 else u).toByte()
                }
            }
        }
    }

    /**
     * Generates a neutral test pattern frame (used when physical camera is off/pass-through fallback).
     */
    fun createNeutralFrame(
        targetNv21Buffer: ByteArray,
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT,
        label: String = "CAMERA FÍSICA / PASS-THROUGH"
    ) {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(20, 24, 33))

        val gridPaint = Paint().apply {
            color = Color.rgb(40, 50, 70)
            strokeWidth = 2f
        }

        val step = 40
        for (x in 0..width step step) {
            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), gridPaint)
        }
        for (y in 0..height step step) {
            canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), gridPaint)
        }

        val textPaint = Paint().apply {
            color = Color.rgb(0, 229, 255)
            textSize = 28f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }

        canvas.drawText("FaceSwap Virtual Cam", width / 2f, height / 2f - 30f, textPaint)

        val subPaint = Paint().apply {
            color = Color.rgb(180, 195, 210)
            textSize = 20f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText(label, width / 2f, height / 2f + 15f, subPaint)
        canvas.drawText("640x480 NV21 (YUV420sp) @ 30 FPS", width / 2f, height / 2f + 50f, subPaint)

        processFrame(bmp, targetNv21Buffer, width, height)
        bmp.recycle()
    }

    /**
     * Converts NV21 byte array to a JPEG byte array (used for HTTP MJPEG streaming to browsers).
     */
    fun nv21ToJpeg(
        nv21: ByteArray,
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT,
        quality: Int = 80
    ): ByteArray {
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), quality, out)
        return out.toByteArray()
    }

    /**
     * Converts an NV21 byte array back into a Bitmap for live UI inspection in Jetpack Compose.
     */
    fun nv21ToBitmap(
        nv21: ByteArray,
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT
    ): Bitmap? {
        return try {
            val jpegBytes = nv21ToJpeg(nv21, width, height, quality = 85)
            BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding NV21 to Bitmap", e)
            null
        }
    }

    private fun ensureBuffers(width: Int, height: Int) {
        if (cachedWidth != width || cachedHeight != height || pixelBuffer.size != width * height) {
            cachedWidth = width
            cachedHeight = height
            pixelBuffer = IntArray(width * height)
            scaledBitmap?.recycle()
            val newBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            scaledBitmap = newBmp
            scalingCanvas = Canvas(newBmp)
        }
    }
}
