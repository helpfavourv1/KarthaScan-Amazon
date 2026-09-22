package com.zdmgold.katharscan.autoscan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AutoScanController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val overlay: CornerOverlayView,
    private val onError: (String) -> Unit
) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val analysisDetector = DocumentEdgeDetector()
    private val captureDetector = DocumentEdgeDetector()
    private val corrector = PerspectiveCorrector()

    fun start() {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                cameraProvider = future.get()
                bindUseCases()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to start camera")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindUseCases() {
        val provider = cameraProvider ?: return

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { ia ->
                ia.setAnalyzer(analysisExecutor) { proxy -> analyzeFrame(proxy) }
            }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
                imageCapture
            )
        } catch (e: Exception) {
            onError(e.message ?: "Failed to bind camera")
        }
    }

    private fun analyzeFrame(image: ImageProxy) {
        try {
            val bitmap = image.toBitmap()
            val rgba = Mat()
            Utils.bitmapToMat(bitmap, rgba)
            val gray = Mat()
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)

            val quad = analysisDetector.detect(gray)
            if (quad != null) {
                val points = quad.toArray()
                if (points.size == 4) {
                    val previewWidth = previewView.width.toFloat()
                    val previewHeight = previewView.height.toFloat()
                    val scaleX = previewWidth / gray.width().toFloat()
                    val scaleY = previewHeight / gray.height().toFloat()
                    val viewCorners = points.map { p ->
                        PointF((p.x * scaleX).toFloat(), (p.y * scaleY).toFloat())
                    }
                    previewView.post { overlay.setCorners(viewCorners) }
                }
                quad.release()
            } else {
                previewView.post { overlay.clear() }
            }
            gray.release()
            rgba.release()
        } catch (_: Throwable) {
            // swallow per-frame errors; camera preview keeps running
        } finally {
            image.close()
        }
    }

    fun captureAndProcess(onComplete: (Bitmap?) -> Unit) {
        val capture = imageCapture ?: run { onComplete(null); return }
        capture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val full = image.toBitmap()
                        val result = processCapture(full)
                        onComplete(result)
                    } catch (e: Exception) {
                        onError(e.message ?: "Capture failed")
                        onComplete(null)
                    } finally {
                        image.close()
                    }
                }
                override fun onError(exception: ImageCaptureException) {
                    onError(exception.message ?: "Capture error")
                    onComplete(null)
                }
            }
        )
    }

    private fun processCapture(full: Bitmap): Bitmap? {
        val rgba = Mat()
        Utils.bitmapToMat(full, rgba)
        val gray = Mat()
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        val quad = captureDetector.detect(gray)
        gray.release()
        rgba.release()

        if (quad == null) return full

        val corrected = corrector.correct(full, quad, PerspectiveCorrector.Filter.ENHANCE)
        quad.release()
        return corrected
    }

    fun stop() {
        cameraProvider?.unbindAll()
        analysisExecutor.shutdown()
        analysisDetector.release()
        captureDetector.release()
    }
}
