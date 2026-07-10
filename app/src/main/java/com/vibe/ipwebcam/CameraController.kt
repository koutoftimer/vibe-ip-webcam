package com.vibe.ipwebcam

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range

class CameraController(private val context: Context) {

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null

    var onFrame: ((ByteArray) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    val isActive: Boolean
        get() = cameraDevice != null

    @SuppressLint("MissingPermission")
    fun open(width: Int, height: Int, fps: Int) {
        handlerThread = HandlerThread("CameraThread").apply { start() }
        handler = Handler(handlerThread!!.looper)

        imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 3).apply {
            setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    onFrame?.invoke(bytes)
                } finally {
                    image.close()
                }
            }, handler)
        }

        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = findBackCamera(manager) ?: run {
            onError?.invoke("No back camera found")
            return
        }

        // Check if the requested resolution is supported
        val chars = manager.getCameraCharacteristics(cameraId)
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = map?.getOutputSizes(ImageFormat.JPEG)
        Log.i(TAG, "Supported JPEG sizes: ${sizes?.joinToString()}")

        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                Log.i(TAG, "Camera opened: $cameraId")
                cameraDevice = camera
                startCapture(width, height, fps)
            }

            override fun onDisconnected(camera: CameraDevice) {
                Log.w(TAG, "Camera disconnected")
                camera.close()
                cameraDevice = null
            }

            override fun onError(camera: CameraDevice, error: Int) {
                Log.e(TAG, "Camera error: $error")
                camera.close()
                cameraDevice = null
                onError?.invoke("Camera error: $error")
            }
        }, handler)
    }

    private fun startCapture(width: Int, height: Int, fps: Int) {
        val camera = cameraDevice ?: return
        val surface = imageReader?.surface ?: return

        try {
            val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(fps, fps))
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }

            camera.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        session.setRepeatingRequest(builder.build(), null, handler)
                        Log.i(TAG, "Capture session started: ${width}x${height} @ ${fps}fps")
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Capture session configuration failed")
                        onError?.invoke("Camera configuration failed")
                    }
                },
                handler
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start capture", e)
            onError?.invoke("Failed to start capture: ${e.message}")
        }
    }

    fun close() {
        try {
            captureSession?.close()
        } catch (_: Exception) {}
        try {
            cameraDevice?.close()
        } catch (_: Exception) {}
        try {
            imageReader?.close()
        } catch (_: Exception) {}
        handlerThread?.quitSafely()
        captureSession = null
        cameraDevice = null
        imageReader = null
        handlerThread = null
        handler = null
        Log.i(TAG, "Camera closed")
    }

    private fun findBackCamera(manager: CameraManager): String? {
        for (id in manager.cameraIdList) {
            val chars = manager.getCameraCharacteristics(id)
            val facing = chars.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_BACK) return id
        }
        return manager.cameraIdList.firstOrNull()
    }

    companion object {
        private const val TAG = "CameraController"
    }
}
