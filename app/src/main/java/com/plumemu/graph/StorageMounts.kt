package com.plumemu.graph

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

/**
 * Hears storage being mounted (an SD card or USB drive put in) while Plumemu is started, so the
 * folders on it are checked for new games (`StartupWork.onStorageMounted`). Held by
 * `MainActivity` (it keeps nothing between starts). Registered at runtime from `onStart` to `onStop` only: nothing
 * listens while Plumemu is in the background or a game is playing; a card put in then
 * is found by the check when Plumemu comes back to the front. Needs no permission.
 */
internal class StorageMounts(
    context: Context,
    private val onMounted: () -> Unit,
) {
    private val app = context.applicationContext
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onMounted()
    }
    private var isRegistered = false

    @Synchronized
    fun onStart() {
        if (isRegistered) return
        // Media broadcasts carry the volume's path as a file: URI; without the scheme none match.
        val filter = IntentFilter(Intent.ACTION_MEDIA_MOUNTED).apply { addDataScheme("file") }
        // Exported: a protected system broadcast, as in PackageChanges.
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        isRegistered = true
    }

    @Synchronized
    fun onStop() {
        if (isRegistered) app.unregisterReceiver(receiver)
        isRegistered = false
    }
}
