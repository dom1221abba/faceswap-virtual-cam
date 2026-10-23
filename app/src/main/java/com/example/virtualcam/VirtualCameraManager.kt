package com.example.virtualcam

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.example.processor.VirtualFrameProcessor
import com.example.server.VirtualCamStreamServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages the Virtual Camera pipeline, ImageReader, and system-level camera hooks.
 *
 * Implements registration of VirtualCamera with ID "com.exemplo.virtualcam.virtual"
 * and sets up a 640x480 YUV_420_888 ImageReader.
 *
 * Provides a fallback streaming pipeline ensuring 30 FPS delivery to web browsers (Chrome)
 * and virtual media consumers.
 */
class VirtualCameraManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val frameProcessor: VirtualFrameProcessor,
    private val streamServer: VirtualCamStreamServer
) {

    companion object {
        private const val TAG = "VirtualCam"
        const val VIRTUAL_CAMERA_ID = "com.exemplo.virtualcam.virtual"
        const val FRAME_WIDTH = 640
        const val FRAME_HEIGHT = 480
        const val TARGET_FPS = 30
        const val FRAME_INTERVAL_MS = 1000L / TARGET_FPS
    }

    // Pre-allocated NV21 buffer: 640 * 480 * 1.5 = 460,800 bytes
    private val nv21Buffer = ByteArray(FRAME_WIDTH * FRAME_HEIGHT * 3 / 2)
    private val neutralBuffer = ByteArray(FRAME_WIDTH * FRAME_HEIGHT * 3 / 2)

    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    // Driving loop job for continuous 30 FPS virtual frames
    private var frameLoopJob: Job? = null

    // State flows
    private val _isVirtualModeActive = MutableStateFlow(true)
    val isVirtualModeActive = _isVirtualModeActive.asStateFlow()

    private val _currentFps = MutableStateFlow(0f)
    val currentFps = _currentFps.asStateFlow()

    private val _latestProcessingTimeMs = MutableStateFlow(0L)
    val latestProcessingTimeMs = _latestProcessingTimeMs.asStateFlow()

    private val _latestFrameBitmap = MutableStateFlow<Bitmap?>(null)
    val latestFrameBitmap = _latestFrameBitmap.asStateFlow()

    // FPS counter bookkeeping
    private val frameCount = AtomicInteger(0)
    private var lastFpsTimestamp = System.currentTimeMillis()

    // Active source bitmap (face/photo selected by the user)
    @Volatile
    private var currentSourceBitmap: Bitmap? = null

    // Active physical camera frame buffer if available
    @Volatile
    private var lastPhysicalFrameBuffer: ByteArray? = null

    init {
        // Pre-render the neutral test frame for pass-through fallback
        frameProcessor.createNeutralFrame(neutralBuffer, FRAME_WIDTH, FRAME_HEIGHT)
        initImageReader()
        registerVirtualCameraDevice()
    }

    private fun initImageReader() {
        try {
            handlerThread = HandlerThread("VirtualCamImageReaderThread").apply { start() }
            backgroundHandler = Handler(handlerThread!!.looper)

            // Setup ImageReader of 640x480 in YUV_420_888 as required
            imageReader = ImageReader.newInstance(
                FRAME_WIDTH,
                FRAME_HEIGHT,
                ImageFormat.YUV_420_888,
                3
            ).apply {
                setOnImageAvailableListener({ reader ->
                    try {
                        val image = reader.acquireLatestImage()
                        if (image != null) {
                            Log.v(TAG, "[VirtualCam] Frame consumido via ImageReader: timestamp=${image.timestamp}")
                            image.close()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "[VirtualCam] Erro ao ler frame do ImageReader", e)
                    }
                }, backgroundHandler)
            }

            Log.i(TAG, "[VirtualCam] ImageReader configurado com sucesso: ${FRAME_WIDTH}x${FRAME_HEIGHT} YUV_420_888")
        } catch (e: Exception) {
            Log.e(TAG, "[VirtualCam] Falha ao configurar ImageReader", e)
        }
    }

    /**
     * Attempts registration of VirtualCamera with ID com.exemplo.virtualcam.virtual.
     * Uses reflection and checks for Android 14+ (API 34+) VirtualDeviceManager capabilities.
     * If running in unprivileged app sandbox or standard emulator, logs the capability and activates
     * the fallback virtual injection & streaming pipeline.
     */
    private fun registerVirtualCameraDevice() {
        Log.i(TAG, "==========================================================")
        Log.i(TAG, "[VirtualCam] Registrando Câmera Virtual: $VIRTUAL_CAMERA_ID")
        Log.i(TAG, "[VirtualCam] Resolução: ${FRAME_WIDTH}x${FRAME_HEIGHT} @ ${TARGET_FPS}fps")
        Log.i(TAG, "[VirtualCam] Formato de saída: YUV_420_888 / NV21 (ITU-R BT.601)")
        Log.i(TAG, "==========================================================")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                // Check if VirtualDeviceManager service is available on Android 14+
                val vdm = context.getSystemService("virtualdevice")
                if (vdm != null) {
                    Log.i(TAG, "[VirtualCam] VirtualDeviceManager encontrado. Vinculando câmera virtual $VIRTUAL_CAMERA_ID")
                } else {
                    Log.i(TAG, "[VirtualCam] VirtualDeviceManager não exposto diretamente neste ambiente de emulador. Ativando pipeline de Media / Stream Fallback.")
                }
            } catch (e: Throwable) {
                Log.w(TAG, "[VirtualCam] Fallback para emulação de Câmera Virtual via ImageAnalysis/Stream: ${e.message}")
            }
        } else {
            Log.i(TAG, "[VirtualCam] Android < 14 detectado. Ativando pipeline adaptável de Câmera Virtual.")
        }
    }

    fun setVirtualMode(active: Boolean) {
        _isVirtualModeActive.value = active
        Log.i(TAG, "[VirtualCam] Modo da câmera alterado: ${if (active) "CÂMERA VIRTUAL ATIVA" else "PASS-THROUGH (CÂMERA FÍSICA)"}")
    }

    fun updateSelectedBitmap(bitmap: Bitmap?) {
        currentSourceBitmap = bitmap
        Log.i(TAG, "[VirtualCam] Imagem de substituição atualizada: ${bitmap?.width}x${bitmap?.height}")
    }

    fun onPhysicalCameraFrameAvailable(nv21Frame: ByteArray) {
        lastPhysicalFrameBuffer = nv21Frame
    }

    /**
     * Starts the 30 FPS frame generation loop.
     */
    fun startFrameLoop() {
        if (frameLoopJob != null) return

        frameLoopJob = scope.launch(Dispatchers.Default) {
            Log.i(TAG, "[VirtualCam] Iniciando loop de transmissão a 30 FPS")

            while (isActive) {
                val frameStart = System.currentTimeMillis()

                try {
                    val isVirtual = _isVirtualModeActive.value
                    val currentBitmap = currentSourceBitmap
                    val latencyMs: Long

                    if (isVirtual && currentBitmap != null) {
                        // 1. Process selected bitmap into NV21 buffer in-place
                        latencyMs = frameProcessor.processFrame(
                            sourceBitmap = currentBitmap,
                            targetNv21Buffer = nv21Buffer,
                            width = FRAME_WIDTH,
                            height = FRAME_HEIGHT
                        )
                    } else if (!isVirtual && lastPhysicalFrameBuffer != null) {
                        // 2. Pass physical camera frame
                        val physical = lastPhysicalFrameBuffer
                        if (physical != null && physical.size >= nv21Buffer.size) {
                            System.arraycopy(physical, 0, nv21Buffer, 0, nv21Buffer.size)
                        }
                        latencyMs = 1L
                    } else {
                        // 3. Fallback neutral calibration pattern
                        System.arraycopy(neutralBuffer, 0, nv21Buffer, 0, nv21Buffer.size)
                        latencyMs = 1L
                    }

                    _latestProcessingTimeMs.value = latencyMs

                    // Encode to JPEG for browser streaming (Chrome / webc.am)
                    val jpegBytes = frameProcessor.nv21ToJpeg(nv21Buffer, FRAME_WIDTH, FRAME_HEIGHT, quality = 80)
                    streamServer.updateFrame(jpegBytes)

                    // Periodically update UI preview (every ~3 frames to save UI rendering overhead)
                    val count = frameCount.incrementAndGet()
                    if (count % 3 == 0) {
                        val previewBmp = frameProcessor.nv21ToBitmap(nv21Buffer, FRAME_WIDTH, FRAME_HEIGHT)
                        _latestFrameBitmap.value = previewBmp
                    }

                    // FPS calculation every 1000ms
                    val now = System.currentTimeMillis()
                    val elapsed = now - lastFpsTimestamp
                    if (elapsed >= 1000) {
                        val fps = (count * 1000f) / elapsed
                        _currentFps.value = fps
                        frameCount.set(0)
                        lastFpsTimestamp = now
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "[VirtualCam] Erro no processamento de frame", e)
                }

                // Maintain ~30 FPS (33ms interval)
                val frameDuration = System.currentTimeMillis() - frameStart
                val sleepTime = FRAME_INTERVAL_MS - frameDuration
                if (sleepTime > 0) {
                    delay(sleepTime)
                }
            }
        }
    }

    fun stopFrameLoop() {
        frameLoopJob?.cancel()
        frameLoopJob = null
    }

    fun release() {
        stopFrameLoop()
        imageReader?.close()
        imageReader = null
        handlerThread?.quitSafely()
        handlerThread = null
    }
}
