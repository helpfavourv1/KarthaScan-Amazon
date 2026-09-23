package com.zdmgold.katharscan.autoscan

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import androidx.activity.ComponentActivity
import com.zdmgold.katharscan.R
import org.opencv.android.OpenCVLoader
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import java.io.File
import java.io.FileOutputStream

class CropConfirmActivity : ComponentActivity() {

    companion object {
        const val EXTRA_IMAGE_PATH = "crop_image_path"
        const val EXTRA_CORNERS = "crop_corners"
        const val EXTRA_RESULT_PATH = "crop_result_path"
        const val RESULT_RETAKE = 42
    }

    private lateinit var imageView: ImageView
    private lateinit var overlay: CornerOverlayView
    private lateinit var confirmButton: Button
    private lateinit var retakeButton: Button
    private var rawBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!OpenCVLoader.initLocal()) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        setContentView(R.layout.activity_crop_confirm)
        imageView = findViewById(R.id.cropImageView)
        overlay = findViewById(R.id.cropOverlay)
        confirmButton = findViewById(R.id.confirmButton)
        retakeButton = findViewById(R.id.retakeButton)

        val rawPath = intent.getStringExtra(EXTRA_IMAGE_PATH)
        val corners = intent.getFloatArrayExtra(EXTRA_CORNERS)

        if (rawPath == null) { setResult(Activity.RESULT_CANCELED); finish(); return }
        rawBitmap = BitmapFactory.decodeFile(rawPath)
        if (rawBitmap == null) { setResult(Activity.RESULT_CANCELED); finish(); return }

        imageView.setImageBitmap(rawBitmap)
        imageView.post {
            overlay.setCorners(viewCorners(corners))
        }

        retakeButton.setOnClickListener {
            setResult(RESULT_RETAKE)
            finish()
        }

        confirmButton.setOnClickListener { confirmCrop() }
    }

    private fun viewCorners(rawCorners: FloatArray?): List<PointF> {
        val bmp = rawBitmap ?: return emptyList()
        val vw = imageView.width.toFloat()
        val vh = imageView.height.toFloat()
        if (vw <= 0f || vh <= 0f) return emptyList()

        val scale = minOf(vw / bmp.width, vh / bmp.height)
        val dispW = bmp.width * scale
        val dispH = bmp.height * scale
        val offsetX = (vw - dispW) / 2f
        val offsetY = (vh - dispH) / 2f

        return if (rawCorners != null && rawCorners.size == 8) {
            listOf(
                PointF(offsetX + rawCorners[0] * scale, offsetY + rawCorners[1] * scale),
                PointF(offsetX + rawCorners[2] * scale, offsetY + rawCorners[3] * scale),
                PointF(offsetX + rawCorners[4] * scale, offsetY + rawCorners[5] * scale),
                PointF(offsetX + rawCorners[6] * scale, offsetY + rawCorners[7] * scale)
            )
        } else {
            listOf(
                PointF(offsetX + 0.1f * dispW, offsetY + 0.1f * dispH),
                PointF(offsetX + 0.9f * dispW, offsetY + 0.1f * dispH),
                PointF(offsetX + 0.9f * dispW, offsetY + 0.9f * dispH),
                PointF(offsetX + 0.1f * dispW, offsetY + 0.9f * dispH)
            )
        }
    }

    private fun confirmCrop() {
        val raw = rawBitmap ?: run { setResult(Activity.RESULT_CANCELED); finish(); return }
        val userCorners = overlay.getCorners()
        if (userCorners.size != 4) { setResult(Activity.RESULT_CANCELED); finish(); return }

        val vw = imageView.width.toFloat()
        val vh = imageView.height.toFloat()
        val scale = minOf(vw / raw.width, vh / raw.height)
        val dispW = raw.width * scale
        val dispH = raw.height * scale
        val offsetX = (vw - dispW) / 2f
        val offsetY = (vh - dispH) / 2f

        val bmpCorners = userCorners.map {
            PointF((it.x - offsetX) / scale, (it.y - offsetY) / scale)
        }

        val matCorners = MatOfPoint2f(
            Point(bmpCorners[0].x.toDouble(), bmpCorners[0].y.toDouble()),
            Point(bmpCorners[1].x.toDouble(), bmpCorners[1].y.toDouble()),
            Point(bmpCorners[2].x.toDouble(), bmpCorners[2].y.toDouble()),
            Point(bmpCorners[3].x.toDouble(), bmpCorners[3].y.toDouble())
        )

        try {
            val corrected = PerspectiveCorrector().correct(
                raw, matCorners, PerspectiveCorrector.Filter.ENHANCE
            )
            val outDir = File(cacheDir, "autoscan_crop")
            if (!outDir.exists()) outDir.mkdirs()
            val outFile = File(outDir, "crop_${System.currentTimeMillis()}.png")
            FileOutputStream(outFile).use {
                corrected.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            val data = Intent().putExtra(EXTRA_RESULT_PATH, outFile.absolutePath)
            setResult(Activity.RESULT_OK, data)
        } catch (e: Exception) {
            setResult(Activity.RESULT_CANCELED)
        } finally {
            matCorners.release()
            finish()
        }
    }
}
