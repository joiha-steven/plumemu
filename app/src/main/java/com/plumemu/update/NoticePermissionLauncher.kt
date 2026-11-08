package com.plumemu.update

import android.Manifest
import android.content.ActivityNotFoundException
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Android 13+'s notification question for an Activity ([ReopenNotice]). Built while the Activity is made (it registers for the
 * result). After two refusals Android answers at once without showing anything; the asker is told either way.
 */
internal class NoticePermissionLauncher(
    activity: ComponentActivity,
    /** Told of every answer, also one that arrives at a recreated Activity whose asker is gone. */
    private val onAnswer: () -> Unit,
) {
    private var afterAnswer: (() -> Unit)? = null

    private val question =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.i(TAG, "Notification question closed (granted: $granted)")
            val told = afterAnswer
            afterAnswer = null
            onAnswer()
            told?.invoke()
        }

    /** Shows the question; [onAnswered] when it closes. False (and nothing shown) below Android 13 or when the device has no screen for it. */
    fun ask(onAnswered: () -> Unit): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        afterAnswer = onAnswered
        return try {
            question.launch(Manifest.permission.POST_NOTIFICATIONS)
            true
        } catch (e: ActivityNotFoundException) {
            afterAnswer = null
            Log.w(TAG, "Notification question could not open (${e.javaClass.simpleName})")
            false
        }
    }

    private companion object {
        const val TAG = "PlumNotice"
    }
}
