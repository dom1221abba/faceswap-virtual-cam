package com.example.server

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Embedded HTTP server delivering a live 30 FPS MJPEG stream from the Virtual Camera pipeline.
 *
 * Allows Chrome (or any web browser on Android or desktop via ADB reverse/WebUSB)
 * to consume the virtual camera stream directly at http://localhost:8080/stream or http://localhost:8080.
 *
 * Emits Logcat events when web consumers (e.g., Chrome, webc.am test page) connect and stream.
 */
class VirtualCamStreamServer(
    private val port: Int = 8080,
    private val scope: CoroutineScope
) {

    companion object {
        private const val TAG = "VirtualCam"
    }

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val activeClients = CopyOnWriteArrayList<ClientSession>()

    // Current latest JPEG frame from VirtualFrameProcessor
    @Volatile
    private var latestJpegFrame: ByteArray? = null

    // Listeners for UI and Logcat notifications
    var onClientConnectedListener: ((clientInfo: String) -> Unit)? = null
    var onClientDisconnectedListener: ((clientInfo: String) -> Unit)? = null

    val isRunning: Boolean
        get() = serverSocket != null && !(serverSocket?.isClosed ?: true)

    val clientCount: Int
        get() = activeClients.size

    fun start() {
        if (isRunning) return

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                val server = ServerSocket(port)
                serverSocket = server
                Log.i(TAG, "[VirtualCam] Servidor de streaming da Câmera Virtual iniciado na porta $port")
                Log.i(TAG, "[VirtualCam] Acesse http://localhost:$port/stream no Chrome ou navegador web")

                while (isActive && !server.isClosed) {
                    try {
                        val client = server.accept()
                        handleClient(client)
                    } catch (e: Exception) {
                        if (!server.isClosed) {
                            Log.e(TAG, "[VirtualCam] Erro ao aceitar conexão de cliente", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "[VirtualCam] Falha ao iniciar servidor de streaming na porta $port", e)
            }
        }
    }

    fun stop() {
        try {
            activeClients.forEach { it.close() }
            activeClients.clear()
            serverSocket?.close()
            serverSocket = null
            serverJob?.cancel()
            Log.i(TAG, "[VirtualCam] Servidor de streaming encerrado")
        } catch (e: Exception) {
            Log.e(TAG, "[VirtualCam] Erro ao parar servidor de streaming", e)
        }
    }

    /**
     * Updates the latest processed frame to be streamed to all connected browsers.
     */
    fun updateFrame(jpegBytes: ByteArray) {
        latestJpegFrame = jpegBytes
        for (client in activeClients) {
            client.pushFrame(jpegBytes)
        }
    }

    private fun handleClient(socket: Socket) {
        scope.launch(Dispatchers.IO) {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val firstLine = reader.readLine() ?: return@launch
            val parts = firstLine.split(" ")
            val path = if (parts.size > 1) parts[1] else "/"

            var userAgent = "Desconhecido"
            var line: String? = reader.readLine()
            while (!line.isNullOrEmpty()) {
                if (line.startsWith("User-Agent:", ignoreCase = true)) {
                    userAgent = line.substring(11).trim()
                }
                line = reader.readLine()
            }

            val clientAddr = socket.inetAddress.hostAddress ?: "127.0.0.1"
            val clientDesc = "$userAgent ($clientAddr)"

            // Requirement: Log explicitly when a new app (like Chrome) opens the Virtual Camera
            Log.i(TAG, "[VirtualCam] CONSUMIDOR DETECTADO! Novo cliente conectado: $clientDesc - Rota: $path")
            withContext(Dispatchers.Main) {
                onClientConnectedListener?.invoke(clientDesc)
            }

            val out = socket.getOutputStream()

            when {
                path.startsWith("/stream") || path.startsWith("/video") -> {
                    // Send MJPEG stream header
                    val header = (
                        "HTTP/1.1 200 OK\r\n" +
                        "Connection: close\r\n" +
                        "Server: FaceSwapVirtualCam/1.0\r\n" +
                        "Cache-Control: no-store, no-cache, must-revalidate, pre-check=0, post-check=0, max-age=0\r\n" +
                        "Pragma: no-cache\r\n" +
                        "Content-Type: multipart/x-mixed-replace; boundary=--frame\r\n" +
                        "Access-Control-Allow-Origin: *\r\n\r\n"
                    ).toByteArray(Charsets.US_ASCII)
                    out.write(header)
                    out.flush()

                    val session = ClientSession(socket, out) {
                        activeClients.remove(it)
                        Log.i(TAG, "[VirtualCam] Consumidor desconectou da câmera virtual: $clientDesc")
                        scope.launch(Dispatchers.Main) {
                            onClientDisconnectedListener?.invoke(clientDesc)
                        }
                    }
                    activeClients.add(session)

                    // Send current frame immediately if available
                    latestJpegFrame?.let { session.pushFrame(it) }
                }

                path.startsWith("/snapshot") -> {
                    val frame = latestJpegFrame
                    if (frame != null) {
                        val header = (
                            "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: image/jpeg\r\n" +
                            "Content-Length: ${frame.size}\r\n" +
                            "Access-Control-Allow-Origin: *\r\n\r\n"
                        ).toByteArray(Charsets.US_ASCII)
                        out.write(header)
                        out.write(frame)
                        out.flush()
                    } else {
                        val resp = "HTTP/1.1 503 Service Unavailable\r\n\r\nNo frame yet"
                        out.write(resp.toByteArray())
                    }
                    socket.close()
                }

                else -> {
                    // Serve HTML landing page for quick browser testing
                    val html = getTestPortalHtml(port)
                    val resp = (
                        "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/html; charset=utf-8\r\n" +
                        "Content-Length: ${html.toByteArray().size}\r\n" +
                        "Access-Control-Allow-Origin: *\r\n\r\n" +
                        html
                    )
                    out.write(resp.toByteArray(Charsets.UTF_8))
                    out.flush()
                    socket.close()
                }
            }
        }
    }

    private fun getTestPortalHtml(port: Int): String {
        return """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>FaceSwap Virtual Cam - Stream Test</title>
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, sans-serif;
                        background: #0d1117;
                        color: #f0f6fc;
                        margin: 0;
                        padding: 20px;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                    }
                    .card {
                        background: #161b22;
                        border: 1px solid #30363d;
                        border-radius: 12px;
                        padding: 24px;
                        max-width: 680px;
                        width: 100%;
                        box-shadow: 0 8px 24px rgba(0,0,0,0.5);
                    }
                    h1 { color: #00e5ff; font-size: 22px; margin-top: 0; }
                    .badge {
                        display: inline-block;
                        padding: 4px 10px;
                        border-radius: 20px;
                        background: #00e676;
                        color: #000;
                        font-weight: bold;
                        font-size: 12px;
                        margin-bottom: 16px;
                    }
                    .stream-view {
                        width: 100%;
                        max-width: 640px;
                        height: auto;
                        border-radius: 8px;
                        border: 2px solid #00e5ff;
                        box-shadow: 0 0 16px rgba(0, 229, 255, 0.3);
                        display: block;
                        margin: 0 auto;
                        background: #000;
                    }
                    .info {
                        background: #21262d;
                        border-radius: 8px;
                        padding: 12px;
                        margin-top: 16px;
                        font-size: 13px;
                        line-height: 1.6;
                    }
                    code { background: #30363d; padding: 2px 6px; border-radius: 4px; color: #79c0ff; }
                </style>
            </head>
            <body>
                <div class="card">
                    <span class="badge">● AO VIVO • VIRTUAL CAMERA ATIVA</span>
                    <h1>FaceSwap Virtual Cam — Teste no Navegador</h1>
                    <p style="color:#8b949e; font-size: 14px;">
                        Transmitindo feed processado em <strong>640x480 NV21 (YUV420sp)</strong> a 30 FPS diretamente para o navegador.
                    </p>
                    <img class="stream-view" src="/stream" alt="Virtual Cam Stream (640x480)">
                    <div class="info">
                        <strong>Integração com sites (como webc.am / Chrome):</strong><br>
                        • URL direta do stream MJPEG: <code>http://localhost:$port/stream</code><br>
                        • Virtual Camera ID registrada: <code>com.exemplo.virtualcam.virtual</code><br>
                        • Formato: <code>YUV_420_888 / NV21</code> (ITU-R BT.601)
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private class ClientSession(
        private val socket: Socket,
        private val out: OutputStream,
        private val onClosed: (ClientSession) -> Unit
    ) {
        @Volatile
        private var isClosed = false

        fun pushFrame(jpegBytes: ByteArray) {
            if (isClosed) return
            try {
                val frameHeader = (
                    "--frame\r\n" +
                    "Content-Type: image/jpeg\r\n" +
                    "Content-Length: ${jpegBytes.size}\r\n\r\n"
                ).toByteArray(Charsets.US_ASCII)

                synchronized(out) {
                    out.write(frameHeader)
                    out.write(jpegBytes)
                    out.write("\r\n".toByteArray(Charsets.US_ASCII))
                    out.flush()
                }
            } catch (e: Exception) {
                close()
            }
        }

        fun close() {
            if (isClosed) return
            isClosed = true
            try {
                socket.close()
            } catch (_: Exception) {}
            onClosed(this)
        }
    }
}
