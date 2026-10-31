package com.plumemu.library.scan

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Test-only activity: opens ACTION_OPEN_DOCUMENT_TREE and hands the picked URI to the test. */
class TreePickerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                // Hidden extra the picker honours on Android 6: show internal storage in the drawer.
                .putExtra("android.content.extra.SHOW_ADVANCED", true)
            @Suppress("DEPRECATION") // No AndroidX activity in this test APK, so no result API.
            startActivityForResult(intent, REQUEST)
        }
    }

    @Deprecated("No AndroidX activity in this test APK, so no result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") // Matches the deprecated override.
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        pickedUri = if (resultCode == RESULT_OK) uri else null
        returned.countDown()
        finish()
    }

    companion object {
        private const val REQUEST = 1

        // CountDownLatch rather than CompletableFuture, which needs API 24.
        @Volatile
        private var returned = CountDownLatch(1)

        @Volatile
        private var pickedUri: Uri? = null

        fun reset() {
            returned = CountDownLatch(1)
            pickedUri = null
        }

        /** The picked tree, or an error when the picker was cancelled or did not return in time. */
        fun await(timeoutMs: Long): Uri {
            check(returned.await(timeoutMs, TimeUnit.MILLISECONDS)) { "folder picker did not return" }
            return pickedUri ?: error("folder picker was cancelled")
        }
    }
}
