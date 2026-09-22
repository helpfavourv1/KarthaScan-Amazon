package com.zdmgold.katharscan.autoscan

import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/**
 * MethodChannel bridge between Dart and AutoScanActivity.
 *
 * Dart calls "scan" on channel "com.zdmgold.katharscan/autoscan".
 * This class launches AutoScanActivity, holds the pending MethodChannel.Result,
 * and completes it when the Activity returns with a result.
 *
 * The Activity-to-Channel handshake uses a static companion reference. This is
 * the standard pattern for Activity-based platform channels — no EventChannel
 * needed, no Flutter widget hosting.
 */
class AutoScanChannel(
    messenger: BinaryMessenger,
    private val host: ComponentActivity,
) : MethodChannel.MethodCallHandler {

    companion object {
        const val CHANNEL_NAME = "com.zdmgold.katharscan/autoscan"
        const val REQUEST_CODE = 0x5CA7
        const val EXTRA_IMAGE_PATH = "image_path"

        @Volatile
        private var pendingResult: MethodChannel.Result? = null

        /**
         * Called by AutoScanActivity.finishWithResult() or finishWithCancel().
         * Delivers the result back to Dart on the main thread.
         */
        fun completeResult(imagePath: String?) {
            val result = pendingResult ?: return
            pendingResult = null
            result.success(imagePath)
        }
    }

    private val channel = MethodChannel(messenger, CHANNEL_NAME).also {
        it.setMethodCallHandler(this)
    }

    fun register() {
        // Channel is registered in the initializer via setMethodCallHandler.
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "scan" -> launchScanner(result)
            else -> result.notImplemented()
        }
    }

    private fun launchScanner(result: MethodChannel.Result) {
        if (pendingResult != null) {
            result.error("SCAN_IN_PROGRESS", "A scan is already in progress.", null)
            return
        }
        pendingResult = result
        val intent = Intent(host, AutoScanActivity::class.java)
        host.startActivityForResult(intent, REQUEST_CODE)
    }

    /**
     * Optional: call from host's onActivityResult if you route it here.
     * AutoScanActivity calls completeResult directly, so this is a fallback.
     */
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_CODE) return
        val path = if (resultCode == Activity.RESULT_OK) {
            data?.getStringExtra(EXTRA_IMAGE_PATH)
        } else null
        completeResult(path)
    }
}
