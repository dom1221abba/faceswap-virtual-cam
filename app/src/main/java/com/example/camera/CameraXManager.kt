package com.example.camera

import android.content.Context
import android.graphics.ImageFormat
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Manages the physical camera capture using Android Jetpack CameraX.
 * Extracts YUV_420_888 frames from the physical camera and forwards NV21 frames
 * to the Virtual Camera pipeline when pass-through mode is active.
 */
class CameraXManager(
    private val context: Context,
    private val onPhysicalFrameAvailable: (nv21: ByteArray) -> Unit
) {

    companion object {
        private const val TAG = "CameraXManager"
        private const val TARGET_WIDTH = 640
        private const val TARGET_HEIGHT = 480
    }

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null

    // Pre-allocated buffer for incoming physical frames to prevent GC pressure
    private var physicalNv21Buffer: ByteArray? = null

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView? = null,
        onStatusChanged: (isAvailable: Boolean) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                // Check if any back/front camera exists
                val hasBackCamera = provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                val hasFrontCamera = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)

                if (!hasBackCamera && !hasFrontCamera) {
                    Log.w(TAG, "[VirtualCam] Nenhuma câmera física encontrada no dispositivo. Usando gerador de frame neutro para pass-through.")
                    onStatusChanged(false)
                    return@addListener
                }

                val cameraSelector = if (hasFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                val preview = Preview.Builder()
                    .setTargetResolution(Size(TARGET_WIDTH, TARGET_HEIGHT))
                    .build()

                if (previewView != null) {
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(TARGET_WIDTH, TARGET_HEIGHT))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    try {
                        processImageProxy(imageProxy)
                    } finally {
                        imageProxy.close()
                    }
                }

                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
                Log.i(TAG, "[VirtualCam] CameraX iniciado com sucesso: 640x480 YUV_420_888")
                onStatusChanged(true)

            } catch (e: Exception) {
                Log.e(TAG, "[VirtualCam] Erro ao iniciar CameraX", e)
                onStatusChanged(false)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun processImageProxy(image: ImageProxy) {
        if (image.format != ImageFormat.YUV_420_888) return

        val width = image.width
        val height = image.height
        val expectedSize = width * height * 3 / 2

        if (physicalNv21Buffer == null || physicalNv21Buffer!!.size != expectedSize) {
            physicalNv21Buffer = ByteArray(expectedSize)
        }

        val outBuffer = physicalNv21Buffer ?: return

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride

        var outIndex = 0

        // Copy Y plane
        if (yPixelStride == 1 && yRowStride == width) {
            val ySize = width * height
            yBuffer.get(outBuffer, 0, ySize)
            outIndex = ySize
        } else {
            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                for (col in 0 until width) {
                    outBuffer[outIndex++] = yBuffer.get()
                    if (yPixelStride > 1 && col < width - 1) {
                        yBuffer.position(yBuffer.position() + yPixelStride - 1)
                    }
                }
            }
        }

        // Copy interleaved VU for NV21 format
        val uvHeight = height / 2
        val uvWidth = width / 2
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride

        var uvOutIndex = width * height
        for (row in 0 until uvHeight) {
            val vRowPos = row * vRowStride
            val uRowPos = row * uRowStride

            for (col in 0 until uvWidth) {
                val vByte = vBuffer.get(vRowPos + col * vPixelStride)
                val uByte = uBuffer.get(uRowPos + col * uPixelStride)

                // NV21 interleaves V, then U
                outBuffer[uvOutIndex++] = vByte
                outBuffer[uvOutIndex++] = uByte
            }
        }

        onPhysicalFrameAvailable(outBuffer)
    }

    fun stop() {
        try {
            cameraProvider?.unbindAll()
            cameraExecutor.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parar CameraX", e)
        }
    }
}
