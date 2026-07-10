package com.vibe.ipwebcam

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.util.Log

class StreamService : Service() {

    private val binder = LocalBinder()
    private var cameraController: CameraController? = null
    private var mjpegServer: MjpegServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentPort = 0

    var onStreamingStateChanged: ((Boolean) -> Unit)? = null
    var onClientCountChanged: ((Int) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): StreamService = this@StreamService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra(EXTRA_ACTION)
        when (action) {
            ACTION_START -> {
                val port = intent.getIntExtra(EXTRA_PORT, Prefs.DEFAULT_PORT)
                val width = intent.getIntExtra(EXTRA_WIDTH, Prefs.DEFAULT_WIDTH)
                val height = intent.getIntExtra(EXTRA_HEIGHT, Prefs.DEFAULT_HEIGHT)
                val fps = intent.getIntExtra(EXTRA_FPS, Prefs.DEFAULT_FPS)
                startStreaming(port, width, height, fps)
            }
            ACTION_STOP -> stopStreaming()
        }
        return START_NOT_STICKY
    }

    fun startStreaming(port: Int, width: Int, height: Int, fps: Int) {
        if (mjpegServer?.isRunning == true) return

        currentPort = port

        // Acquire partial wake lock
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vibe:ipwebcam").apply {
            acquire(10 * 60 * 1000L) // 10 minutes max, re-acquired by camera frame callback
        }

        // Start HTTP server
        mjpegServer = MjpegServer(port).apply {
            onClientCountChanged = { count ->
                this@StreamService.onClientCountChanged?.invoke(count)
                updateNotification(count)
            }
            start()
        }

        // Start camera
        cameraController = CameraController(this).apply {
            onFrame = { frame ->
                // Keep wake lock alive while frames are flowing
                if (wakeLock?.isHeld == true) {
                    wakeLock?.acquire(10 * 60 * 1000L)
                }
                mjpegServer?.pushFrame(frame)
            }
            onError = { error ->
                this@StreamService.onError?.invoke(error)
                stopStreaming()
            }
            open(width, height, fps)
        }

        startForeground(NOTIFICATION_ID, createNotification("Streaming on port $port"))
        onStreamingStateChanged?.invoke(true)
        Log.i(TAG, "Streaming started: ${width}x${height} @ ${fps}fps on port $port")
    }

    fun stopStreaming() {
        cameraController?.close()
        cameraController = null
        mjpegServer?.stop()
        mjpegServer = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        currentPort = 0
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        onStreamingStateChanged?.invoke(false)
        onClientCountChanged?.invoke(0)
        Log.i(TAG, "Streaming stopped")
    }

    fun isStreaming(): Boolean = mjpegServer?.isRunning == true

    fun getPort(): Int = currentPort

    fun getClientCount(): Int = mjpegServer?.getClientCount() ?: 0

    private fun updateNotification(clientCount: Int) {
        val text = "Streaming on port $currentPort | $clientCount client(s)"
        val notification = createNotification(text)
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(text: String): Notification {
        val intent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, StreamService::class.java).apply {
                putExtra(EXTRA_ACTION, ACTION_STOP)
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, App.CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(intent)
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(R.string.notification_action_stop),
                    stopIntent
                ).build()
            )
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopStreaming()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "StreamService"
        const val NOTIFICATION_ID = 1001

        const val EXTRA_ACTION = "action"
        const val EXTRA_PORT = "port"
        const val EXTRA_WIDTH = "width"
        const val EXTRA_HEIGHT = "height"
        const val EXTRA_FPS = "fps"

        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"

        fun startIntent(context: Context, port: Int, width: Int, height: Int, fps: Int): Intent {
            return Intent(context, StreamService::class.java).apply {
                putExtra(EXTRA_ACTION, ACTION_START)
                putExtra(EXTRA_PORT, port)
                putExtra(EXTRA_WIDTH, width)
                putExtra(EXTRA_HEIGHT, height)
                putExtra(EXTRA_FPS, fps)
            }
        }

        fun stopIntent(context: Context): Intent {
            return Intent(context, StreamService::class.java).apply {
                putExtra(EXTRA_ACTION, ACTION_STOP)
            }
        }
    }
}
