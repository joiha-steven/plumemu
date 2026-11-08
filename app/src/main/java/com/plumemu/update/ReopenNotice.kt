package com.plumemu.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.plumemu.MainActivity
import com.plumemu.R
import com.plumemu.usage.EXTRA_OPENED_AFTER_UPDATE
import java.util.concurrent.atomic.AtomicInteger

/**
 * The one notification Plumemu ever shows: "Plumemu updated, tap to open", posted by
 * [UpdateRestartReceiver] only when Android refused to reopen the main screen after Plumemu's own
 * update (a start from the background is refused silently on Android 10+, even for the home app).
 * Low importance, no sound, no vibration, nothing sent anywhere. Asked for (Android 13+) only when
 * the person presses Update ([Updates.noticeAsk]). [MainActivity] counts its resumes ([onResumed])
 * and the next front cancels the notice ([cancel]).
 */
object ReopenNotice {
    const val CHANNEL_ID = "updates"
    const val NOTICE_ID = 1

    /** How long the receiver waits for the main screen to resume before it posts the notice. */
    const val WAIT_MS = 2_000L

    private const val TAG = "PlumUpdate"

    private val resumes = AtomicInteger()

    @Volatile private var resumedNow = false

    /** [MainActivity] resumed in this process. A counter, not a clock: "since the attempt" is "the count changed". */
    fun onResumed() {
        resumedNow = true
        resumes.incrementAndGet()
    }

    /** [MainActivity] paused: it is not in front now. */
    fun onPaused() {
        resumedNow = false
    }

    internal fun resumeCount(): Int = resumes.get()

    /** Notifications are on for Plumemu (Android 13+: the permission is granted). A binder call: never per frame. */
    fun areAllowed(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Posts the notice unless [MainActivity] resumed after [resumesAtAttempt] was read; true when posted. */
    internal fun postUnlessFront(
        context: Context,
        resumesAtAttempt: Int,
    ): Boolean {
        if (resumeCount() != resumesAtAttempt || resumedNow) {
            Log.i(TAG, "Plumemu came to the front after the update; no notice")
            return false
        }
        return post(context)
    }

    internal fun post(context: Context): Boolean =
        try {
            postNow(context)
        } catch (e: RuntimeException) {
            // Never let the notice crash the process that has just been updated.
            Log.w(TAG, "Could not post the notice (${e.javaClass.simpleName})")
            false
        }

    private fun postNow(context: Context): Boolean {
        if (!areAllowed(context)) {
            Log.i(TAG, "Notifications are off; no notice after the update")
            return false
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(channel(context))
        val open =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .setAction(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    // Counted as an open after an update, like the reopen the receiver tries (app_open.from).
                    .putExtra(EXTRA_OPENED_AFTER_UPDATE, true),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notice =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(context.getString(R.string.update_notice_title))
                .setContentText(context.getString(R.string.update_notice_text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .build()
        manager.notify(NOTICE_ID, notice)
        Log.i(TAG, "Posted the notice after the update (the reopen was refused)")
        return true
    }

    /** The next start of Plumemu: the notice is of no use now. Off the main thread (a binder call). */
    fun cancel(context: Context) {
        try {
            context.getSystemService(NotificationManager::class.java)?.cancel(NOTICE_ID)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not cancel the notice (${e.javaClass.simpleName})")
        }
    }

    @RequiresApi(26)
    private fun channel(context: Context): NotificationChannel =
        NotificationChannel(CHANNEL_ID, context.getString(R.string.update_notice_channel), NotificationManager.IMPORTANCE_LOW).apply {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
}
