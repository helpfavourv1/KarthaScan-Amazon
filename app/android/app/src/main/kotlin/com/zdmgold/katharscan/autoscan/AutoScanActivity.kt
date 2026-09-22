package com.zdmgold.katharscan.autoscan

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream

class AutoScanActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlay: CornerOverlayView
    private lateinit var captureButton: Button
    private lateinit var cancelButton: Button
    private var controller: AutoScanController? = null

    private val requestCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startScanner() else finishWithCancel()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auto_scan)

        previewView = findViewById(R.id.previewView)
        overlay = findViewById(R.id.cornerOverlay)
        captureButton = findViewById(R.id.captureButton)
        cancelButton = findViewById(R.id.cancelButton)

        cancelButton.setOnClickListener { finishWithCancel() }
        captureButton.setOnClickListener { captureNow() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startScanner()
        } else {
            requestCamera.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startScanner() {
        val c = AutoScanController(
            context = this,
            lifecycleOwner = this,
            previewView = previewView,
            overlay = overlay,
            onError = { /* swallow; scan continues */ }
        )
        controller = c
        c.start()
    }

    private fun captureNow() {
        val c = controller ?: return
        captureButton.isEnabled = false
        c.captureAndProcess { bitmap ->
            captureButton.isEnabled = true
            if (bitmap == null) { finishWithCancel(); return@captureAndProcess }
            val path = saveBitmapToCache(bitmap)
            if (path == null) finishWithCancel() else finishWithResult(path)
        }
    }

    private fun saveBitmapToCache(bitmap: Bitmap): String? {
        return try {
            val dir = File(cacheDir, "autoscan")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "scan_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun finishWithResult(path: String) {
        val data = Intent().apply {
            putExtra(AutoScanChannel.EXTRA_IMAGE_PATH, path)
        }
        setResult(Activity.RESULT_OK, data)
        AutoScanChannel.completeResult(path)
        finish()
    }

    private fun finishWithCancel() {
        setResult(Activity.RESULT_CANCELED)
        AutoScanChannel.completeResult(null)
        finish()
    }

    override fun onDestroy() {
        controller?.stop()
        controller = null
        super.onDestroy()
    }
}
