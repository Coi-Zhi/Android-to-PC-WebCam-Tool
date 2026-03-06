package com.example.webcam

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import android.util.Log

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var cameraExecutor: ExecutorService

    @Volatile
    private var clientSocket: Socket? = null

    @Volatile
    private var outputStream: DataOutputStream? = null

    private val ioExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        previewView = PreviewView(this)
        setContentView(previewView)

        cameraExecutor = Executors.newSingleThreadExecutor()

        startSocketServer()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                1001
            )
        }
    }

    private fun startSocketServer() {
        ioExecutor.execute {
            try {
                Log.d("WebCamApp", "Starting socket server on 9000")
                val serverSocket = ServerSocket(9000)
                while (true) {
                    Log.d("WebCamApp", "Waiting for PC connection...")
                    val socket = serverSocket.accept()
                    Log.d("WebCamApp", "PC connected")
                    clientSocket = socket
                    outputStream = DataOutputStream(socket.getOutputStream())
                }
            } catch (e: Exception) {
                Log.e("WebCamApp", "Socket server error", e)
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                try {
                    val jpegBytes = imageProxyToJpeg(imageProxy, 70)
                    val out = outputStream
                    if (out != null) {
                        synchronized(out) {
                            out.writeInt(jpegBytes.size)
                            out.write(jpegBytes)
                            out.flush()
                        }
                        Log.d("WebCamApp", "Sending frame size=${jpegBytes.size}")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    closeClient()
                } finally {
                    imageProxy.close()
                }
            }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalysis
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private fun imageProxyToJpeg(image: ImageProxy, quality: Int): ByteArray {
        val nv21 = yuv420888ToNv21(image)
        val yuvImage = YuvImage(
            nv21,
            ImageFormat.NV21,
            image.width,
            image.height,
            null
        )
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            Rect(0, 0, image.width, image.height),
            quality,
            out
        )
        return out.toByteArray()
    }

    private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val width = image.width
        val height = image.height
        val out = ByteArray(width * height * 3 / 2)

        var outputOffset = 0

        val yBuffer = yPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride

        for (row in 0 until height) {
            val rowStart = row * yRowStride
            for (col in 0 until width) {
                out[outputOffset++] = yBuffer.get(rowStart + col * yPixelStride)
            }
        }

        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride

        for (row in 0 until height / 2) {
            val uRowStart = row * uRowStride
            val vRowStart = row * vRowStride
            for (col in 0 until width / 2) {
                out[outputOffset++] = vBuffer.get(vRowStart + col * vPixelStride)
                out[outputOffset++] = uBuffer.get(uRowStart + col * uPixelStride)
            }
        }

        return out
    }

    private fun allPermissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && allPermissionsGranted()) {
            startCamera()
        }
    }

    private fun closeClient() {
        try {
            outputStream?.close()
        } catch (_: Exception) {
        }
        try {
            clientSocket?.close()
        } catch (_: Exception) {
        }
        outputStream = null
        clientSocket = null
    }

    override fun onDestroy() {
        super.onDestroy()
        closeClient()
        cameraExecutor.shutdown()
        ioExecutor.shutdown()
    }
}