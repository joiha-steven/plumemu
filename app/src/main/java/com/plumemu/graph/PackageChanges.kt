package com.plumemu.graph

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Notices apps installed, removed, updated or enabled/disabled, so the app list and emulator
 * detection run again only when something changed. One per process (`AppGraph.packageChanges`):
 * the place where the package list stood at the last stop must outlive the Activity (swiped from
 * Recents, recreated while stopped).
 *
 * While Plumemu is started a receiver listens (nothing listens in the background).
 * A change while it was stopped (an emulator installed from the store) is found at the next
 * start: from Android 8 by asking what changed since the stop; before Android 8 there is no such
 * question, so any stop counts as a possible change.
 */
internal class PackageChanges(
    context: Context,
    private val sequence: PackageSequence = AndroidPackageSequence,
    /**
     * Told of every change; [isTemporaryRemoval] is true for `ACTION_PACKAGE_REMOVED` with
     * `EXTRA_REPLACING` (an update: the package is back with the `ADDED` that follows) and for
     * `ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE` (apps on storage that went away), false otherwise,
     * also for a change made while stopped. App games never sync on such a removal (`GraphAppGames`).
     */
    private val onChanged: (isTemporaryRemoval: Boolean) -> Unit,
) {
    private val app = context.applicationContext
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onChanged(isTemporaryRemoval(intent))
    }
    private val storageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onChanged(isTemporaryRemoval(intent))
    }
    private var isRegistered = false

    /** Where the package list stood at the last stop; null before the first stop. */
    private var sequenceAtStop: Int? = null

    /** From `onStart`: reports a change made while stopped, then listens. */
    @Synchronized
    fun onStart() {
        val atStop = sequenceAtStop
        sequenceAtStop = null
        if (atStop != null && (atStop == NO_SEQUENCE || sequence.changedSince(app, atStop) != null)) onChanged(false)
        if (isRegistered) return
        val filter = IntentFilter().apply {
            ACTIONS.forEach(::addAction)
            addDataScheme("package")
        }
        // Exported: these are protected system broadcasts; on Android 6-12 a "not exported"
        // receiver is guarded by the app's own permission and the system's broadcast is refused.
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        // Apps on removable or adopted storage come and go with it; these carry no package data scheme.
        val storage = IntentFilter().apply { STORAGE_ACTIONS.forEach(::addAction) }
        ContextCompat.registerReceiver(app, storageReceiver, storage, ContextCompat.RECEIVER_EXPORTED)
        isRegistered = true
    }

    /** From `onStop`: stops listening and remembers where the package list stands. */
    @Synchronized
    fun onStop() {
        if (isRegistered) {
            app.unregisterReceiver(receiver)
            app.unregisterReceiver(storageReceiver)
        }
        isRegistered = false
        sequenceAtStop = sequence.current(app) ?: NO_SEQUENCE
    }

    companion object {
        val ACTIONS = listOf(
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REMOVED,
            Intent.ACTION_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_CHANGED,
        )

        /** Apps on removable or adopted storage becoming available or not. */
        val STORAGE_ACTIONS = listOf(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE, Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE)

        /**
         * A removal that undoes itself: an update's first broadcast (the package comes back with the
         * next one), or storage with apps on it going away (they come back when it is mounted).
         */
        fun isTemporaryRemoval(intent: Intent): Boolean =
            (intent.action == Intent.ACTION_PACKAGE_REMOVED && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) ||
                intent.action == Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE

        /** Stopped on a system that cannot say what changed: treat as changed. */
        private const val NO_SEQUENCE = -1
    }
}

/** Android's package change counter (`PackageManager.getChangedPackages`, Android 8+). */
internal interface PackageSequence {
    /** The counter now; null when the system has no counter (before Android 8). */
    fun current(context: Context): Int?

    /** The new counter if any package changed since [sequence], else null. */
    fun changedSince(context: Context, sequence: Int): Int?
}

internal object AndroidPackageSequence : PackageSequence {
    override fun current(context: Context): Int? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) changedSince(context, 0) ?: 0 else null

    override fun changedSince(context: Context, sequence: Int): Int? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.packageManager.getChangedPackages(sequence)?.sequenceNumber else null
}
