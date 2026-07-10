package ua.pp.ruslan_kovtun.ipwebcam

import android.util.Log
import java.io.BufferedOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class MjpegServer(
    private val port: Int,
    private val bufferPool: FrameBufferPool
) {

    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newFixedThreadPool(
        (Runtime.getRuntime().availableProcessors().coerceAtLeast(4)) * 4
    )

    @Volatile
    private var latestFrame: Pair<ByteArray, Int>? = null

    @Volatile
    private var frameId: Long = 0

    private val frameLock = Object()
    private val clientCount = AtomicInteger(0)

    var onClientCountChanged: ((Int) -> Unit)? = null

    val isRunning: Boolean
        get() = serverSocket?.isClosed == false

    fun getClientCount(): Int = clientCount.get()

    fun pushFrame(frame: ByteArray, length: Int) {
        synchronized(frameLock) {
            val previous = latestFrame
            latestFrame = frame to length
            bufferPool.retain(frame)
            if (previous != null) bufferPool.release(previous.first)
            frameId++
            frameLock.notifyAll()
        }
    }

    fun start() {
        serverSocket = ServerSocket(port)
        executor.submit { acceptLoop() }
        Log.i(TAG, "Server started on port $port")
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        executor.shutdownNow()
        latestFrame = null
        Log.i(TAG, "Server stopped")
    }

    private fun acceptLoop() {
        while (serverSocket?.isClosed != true) {
            try {
                val client = serverSocket!!.accept()
                executor.submit { handleClient(client) }
            } catch (e: Exception) {
                if (serverSocket?.isClosed != true) {
                    Log.e(TAG, "Accept error", e)
                }
            }
        }
    }

    private fun handleClient(socket: Socket) {
        clientCount.incrementAndGet()
        onClientCountChanged?.invoke(clientCount.get())
        try {
            socket.soTimeout = 5000
            val input = socket.getInputStream().bufferedReader()
            val requestLine = input.readLine() ?: return

            // Consume remaining headers
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
            }

            socket.tcpNoDelay = true
            val output = BufferedOutputStream(socket.getOutputStream())

            if (requestLine.contains("/video")) {
                serveMjpeg(output, socket)
            } else {
                serveStatus(output)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Client disconnected: ${e.message}")
        } finally {
            clientCount.decrementAndGet()
            onClientCountChanged?.invoke(clientCount.get())
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun serveMjpeg(output: BufferedOutputStream, socket: Socket) {
        val headers = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: multipart/x-mixed-replace; boundary=frame\r\n")
            append("Cache-Control: no-cache\r\n")
            append("Pragma: no-cache\r\n")
            append("Connection: close\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("\r\n")
        }
        output.write(headers.toByteArray())
        output.flush()

        var lastSeenId = 0L

        while (!socket.isClosed) {
            val (frame, length) = synchronized(frameLock) {
                while (frameId == lastSeenId) {
                    frameLock.wait(500)
                    if (frameId == lastSeenId) {
                        if (socket.isClosed) throw InterruptedException("Socket closed")
                    }
                }
                val (f, len) = latestFrame!!
                bufferPool.retain(f)
                lastSeenId = frameId
                f to len
            }

            try {
                output.write(BOUNDARY_BYTES)
                output.write(length.toString().toByteArray())
                output.write(HEADER_END)
                output.write(frame, 0, length)
                output.write(FRAME_SUFFIX)
                output.flush()
            } catch (e: Exception) {
                Log.d(TAG, "Write failed, client gone: ${e.message}")
                break
            } finally {
                bufferPool.release(frame)
            }
        }
    }

    private fun serveStatus(output: BufferedOutputStream) {
        val html = STATUS_HTML.replace("{{CLIENTS}}", clientCount.get().toString())
            .replace("{{PORT}}", port.toString())
        val htmlBytes = html.toByteArray()
        val response = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Content-Length: ${htmlBytes.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        output.write(response.toByteArray())
        output.write(htmlBytes)
        output.flush()
    }

    companion object {
        private const val TAG = "MjpegServer"

        private val BOUNDARY_BYTES = "--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ".toByteArray()
        private val HEADER_END = "\r\n\r\n".toByteArray()
        private val FRAME_SUFFIX = "\r\n".toByteArray()

        private const val STATUS_HTML = """<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>IP Webcam</title>
    <style>
        body { font-family: monospace; background: #1a1a2e; color: #e0e0e0; padding: 2rem; }
        h1 { color: #00d4aa; }
        .status { background: #16213e; padding: 1rem; border-radius: 8px; margin: 1rem 0; }
        .live { color: #00ff88; font-weight: bold; }
        a { color: #4fc3f7; }
    </style>
</head>
<body>
    <h1>IP Webcam Server</h1>
    <div class="status">
        <p>Status: <span class="live">STREAMING</span></p>
        <p>Clients connected: {{CLIENTS}}</p>
        <p>Stream URL: <a href="/video">http://&lt;phone-ip&gt;:{{PORT}}/video</a></p>
    </div>
    <p>Use with ffmpeg on Linux:</p>
    <code>ffmpeg -i http://&lt;phone-ip&gt;:{{PORT}}/video -vf format=yuv420p -f v4l2 /dev/video1</code>
</body>
</html>"""

        fun getDeviceIpAddress(): String {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val iface = interfaces.nextElement()
                    if (iface.isLoopback || !iface.isUp) continue
                    val addresses = iface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            return addr.hostAddress ?: "Unknown"
                        }
                    }
                }
            } catch (_: Exception) {}
            return "Unknown"
        }
    }
}
