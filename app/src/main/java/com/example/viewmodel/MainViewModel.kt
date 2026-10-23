package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.camera.CameraXManager
import com.example.processor.VirtualFrameProcessor
import com.example.server.VirtualCamStreamServer
import com.example.virtualcam.VirtualCameraManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class VirtualCamUiState(
    val isVirtualCamActive: Boolean = true,
    val selectedImageUri: Uri? = null,
    val selectedBitmap: Bitmap? = null,
    val previewBitmap: Bitmap? = null,
    val currentFps: Float = 0f,
    val latencyMs: Long = 0L,
    val isStreamServerRunning: Boolean = false,
    val streamPort: Int = 8080,
    val connectedClients: List<String> = emptyList(),
    val isPhysicalCameraAvailable: Boolean = false,
    val activePresetIndex: Int = 0,
    val logMessages: List<String> = emptyList(),
    val virtualCameraId: String = VirtualCameraManager.VIRTUAL_CAMERA_ID
)

class MainViewModel : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
        private const val MAX_LOGS = 30
    }

    private val _uiState = MutableStateFlow(VirtualCamUiState())
    val uiState = _uiState.asStateFlow()

    private val frameProcessor = VirtualFrameProcessor()
    private var streamServer: VirtualCamStreamServer? = null
    private var virtualCameraManager: VirtualCameraManager? = null
    private var cameraXManager: CameraXManager? = null

    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun initialize(context: Context) {
        if (virtualCameraManager != null) return

        appendLog("Inicializando FaceSwap Virtual Cam...")

        // 1. Initialize Stream Server on port 8080
        val server = VirtualCamStreamServer(port = 8080, scope = viewModelScope)
        streamServer = server

        server.onClientConnectedListener = { clientDesc ->
            val msg = "Novo app conectado à câmera virtual: $clientDesc"
            appendLog(msg)
            _uiState.update { state ->
                state.copy(
                    connectedClients = (state.connectedClients + clientDesc).distinct()
                )
            }
        }

        server.onClientDisconnectedListener = { clientDesc ->
            val msg = "App desconectado da câmera virtual: $clientDesc"
            appendLog(msg)
            _uiState.update { state ->
                state.copy(
                    connectedClients = state.connectedClients.filter { it != clientDesc }
                )
            }
        }

        server.start()
        _uiState.update { it.copy(isStreamServerRunning = true) }
        appendLog("Servidor de streaming ativo em http://localhost:8080/stream")

        // 2. Initialize VirtualCameraManager
        val vcm = VirtualCameraManager(
            context = context.applicationContext,
            scope = viewModelScope,
            frameProcessor = frameProcessor,
            streamServer = server
        )
        virtualCameraManager = vcm

        // Collect stats from VirtualCameraManager
        viewModelScope.launch {
            vcm.currentFps.collect { fps ->
                _uiState.update { it.copy(currentFps = fps) }
            }
        }

        viewModelScope.launch {
            vcm.latestProcessingTimeMs.collect { latency ->
                _uiState.update { it.copy(latencyMs = latency) }
            }
        }

        viewModelScope.launch {
            vcm.latestFrameBitmap.collect { bmp ->
                if (bmp != null) {
                    _uiState.update { it.copy(previewBitmap = bmp) }
                }
            }
        }

        // 3. Load default Cyberpunk Preset avatar
        loadPresetAvatar(index = 0, context = context)

        // 4. Start 30 FPS driver loop
        vcm.startFrameLoop()
        appendLog("Loop da Câmera Virtual iniciado a 30 FPS (640x480 NV21)")
    }

    fun toggleVirtualCam(active: Boolean) {
        _uiState.update { it.copy(isVirtualCamActive = active) }
        virtualCameraManager?.setVirtualMode(active)
        val modeStr = if (active) "CÂMERA VIRTUAL (Imagem Ativa)" else "PASS-THROUGH (Câmera Física)"
        appendLog("Modo alternado: $modeStr")
    }

    fun onImagePicked(uri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                appendLog("Carregando imagem da galeria...")
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (bitmap != null) {
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                selectedImageUri = uri,
                                selectedBitmap = bitmap,
                                activePresetIndex = 2
                            )
                        }
                        virtualCameraManager?.updateSelectedBitmap(bitmap)
                        appendLog("Imagem selecionada carregada (${bitmap.width}x${bitmap.height})")
                    }
                } else {
                    appendLog("Aviso: Não foi possível decodificar a imagem selecionada")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar imagem selecionada", e)
                appendLog("Erro ao ler imagem: ${e.message}")
            }
        }
    }

    fun loadPresetAvatar(index: Int, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resId = when (index) {
                    0 -> R.drawable.sample_face_cyber
                    else -> R.drawable.ic_app_icon_foreground
                }

                val bitmap = BitmapFactory.decodeResource(context.resources, resId)
                if (bitmap != null) {
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                selectedBitmap = bitmap,
                                selectedImageUri = null,
                                activePresetIndex = index
                            )
                        }
                        virtualCameraManager?.updateSelectedBitmap(bitmap)
                        appendLog("Preset ${if (index == 0) "Cyber Avatar" else "Studio Icon"} selecionado")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar preset", e)
            }
        }
    }

    fun bindCameraX(cameraX: CameraXManager) {
        cameraXManager = cameraX
    }

    fun updatePhysicalCameraStatus(isAvailable: Boolean) {
        _uiState.update { it.copy(isPhysicalCameraAvailable = isAvailable) }
        appendLog(
            if (isAvailable) "Câmera física detectada e pronta para pass-through"
            else "Câmera física não disponível (usando padrão neutro para pass-through)"
        )
    }

    fun onPhysicalCameraFrame(nv21: ByteArray) {
        virtualCameraManager?.onPhysicalCameraFrameAvailable(nv21)
    }

    fun appendLog(message: String) {
        val time = timeFormatter.format(Date())
        val entry = "[$time] $message"
        Log.i(TAG, entry)

        _uiState.update { state ->
            val updated = (listOf(entry) + state.logMessages).take(MAX_LOGS)
            state.copy(logMessages = updated)
        }
    }

    override fun onCleared() {
        super.onCleared()
        virtualCameraManager?.release()
        streamServer?.stop()
        cameraXManager?.stop()
    }
}
