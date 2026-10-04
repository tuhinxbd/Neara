package app.neara.android.call

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.graphics.YuvImage
import android.hardware.Camera
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("DEPRECATION")
class VideoStreamer(
    private val localPort: Int,
    private val remoteIp: String,
    private val remotePort: Int,
    private val onLocalFrame: (Bitmap?) -> Unit,
    private val onRemoteFrame: (Bitmap?) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isRunning = AtomicBoolean(false)
    val isVideoMuted = AtomicBoolean(false)
    var isFrontCamera: Boolean = true
        private set

    private var camera: Camera? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var sendSocket: DatagramSocket? = null
    private var receiveSocket: DatagramSocket? = null
    private var lastFrameTime = 0L

    fun start(frontCamera: Boolean = true) {
        if (isRunning.getAndSet(true)) return
        isFrontCamera = frontCamera

        try {
            sendSocket = DatagramSocket()
            receiveSocket = DatagramSocket(localPort)

            startCamera(isFrontCamera)

            // Video receiver loop
            scope.launch {
                val buffer = ByteArray(65536)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isRunning.get() && isActive) {
                    try {
                        receiveSocket?.receive(packet)
                        val length = packet.length
                        if (length > 0) {
                            val bmp = BitmapFactory.decodeByteArray(packet.data, 0, length)
                            if (bmp != null) {
                                onRemoteFrame(bmp)
                            }
                        }
                    } catch (e: Exception) {
                        if (!isRunning.get()) break
                    }
                }
            }
        } catch (e: Exception) {
            stop()
        }
    }

    private fun startCamera(front: Boolean) {
        try {
            stopCamera()
            val cameraId = getCameraId(front)
            if (cameraId == -1) return

            val cam = Camera.open(cameraId)
            camera = cam

            val params = cam.parameters
            val supportedSizes = params.supportedPreviewSizes
            // Choose optimal size around 320x240 or 352x288
            val targetSize = supportedSizes.minByOrNull {
                val diffW = kotlin.math.abs(it.width - 320)
                val diffH = kotlin.math.abs(it.height - 240)
                diffW + diffH
            } ?: supportedSizes.first()

            params.setPreviewSize(targetSize.width, targetSize.height)
            params.previewFormat = ImageFormat.NV21
            cam.parameters = params

            val previewWidth = targetSize.width
            val previewHeight = targetSize.height
            val bufferSize = previewWidth * previewHeight * 3 / 2

            cam.addCallbackBuffer(ByteArray(bufferSize))
            cam.addCallbackBuffer(ByteArray(bufferSize))

            val dummyTexture = SurfaceTexture(10)
            surfaceTexture = dummyTexture
            cam.setPreviewTexture(dummyTexture)

            val targetAddress = InetAddress.getByName(remoteIp)

            cam.setPreviewCallbackWithBuffer { data, activeCam ->
                if (!isRunning.get() || isVideoMuted.get()) {
                    activeCam.addCallbackBuffer(data)
                    return@setPreviewCallbackWithBuffer
                }

                // Throttle to ~12-15 fps for smooth performance and low network load
                val now = System.currentTimeMillis()
                if (now - lastFrameTime < 70) {
                    activeCam.addCallbackBuffer(data)
                    return@setPreviewCallbackWithBuffer
                }
                lastFrameTime = now

                scope.launch {
                    try {
                        val yuv = YuvImage(data, ImageFormat.NV21, previewWidth, previewHeight, null)
                        val baos = ByteArrayOutputStream()
                        yuv.compressToJpeg(Rect(0, 0, previewWidth, previewHeight), 40, baos)
                        val jpegBytes = baos.toByteArray()

                        if (jpegBytes.size < 65000) {
                            val packet = DatagramPacket(jpegBytes, jpegBytes.size, targetAddress, remotePort)
                            sendSocket?.send(packet)
                        }

                        // Local preview
                        val bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                        if (bmp != null) {
                            val matrix = Matrix()
                            if (front) {
                                matrix.postRotate(270f)
                                matrix.postScale(-1f, 1f) // Mirror front camera
                            } else {
                                matrix.postRotate(90f)
                            }
                            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                            onLocalFrame(rotated)
                        }
                    } catch (e: Exception) {}
                }

                activeCam.addCallbackBuffer(data)
            }

            cam.startPreview()
        } catch (e: Exception) {
            stopCamera()
        }
    }

    private fun stopCamera() {
        try {
            camera?.setPreviewCallbackWithBuffer(null)
            camera?.stopPreview()
            camera?.release()
        } catch (e: Exception) {}
        camera = null

        try {
            surfaceTexture?.release()
        } catch (e: Exception) {}
        surfaceTexture = null
    }

    fun flipCamera() {
        if (!isRunning.get()) return
        isFrontCamera = !isFrontCamera
        startCamera(isFrontCamera)
    }

    private fun getCameraId(front: Boolean): Int {
        val info = Camera.CameraInfo()
        val num = Camera.getNumberOfCameras()
        for (i in 0 until num) {
            Camera.getCameraInfo(i, info)
            if (front && info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                return i
            } else if (!front && info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                return i
            }
        }
        return if (num > 0) 0 else -1
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return

        scope.cancel()
        stopCamera()

        try {
            sendSocket?.close()
        } catch (e: Exception) {}
        sendSocket = null

        try {
            receiveSocket?.close()
        } catch (e: Exception) {}
        receiveSocket = null

        onLocalFrame(null)
        onRemoteFrame(null)
    }
}
