package com.plumemu.usage

import android.content.SharedPreferences

/**
 * The version name of Plumemu that last ran on this device, for `update_installed`. A small
 * SharedPreferences file of its own; read and written off the main thread (`AppGraph.trackUpdateInstalled`).
 */
internal class VersionSeen(private val preferences: SharedPreferences) {
    /** Stores [current] and returns the version seen before it; null at the first run after an install. */
    @Synchronized
    fun swap(current: String): String? {
        val previous = preferences.getString(KEY_VERSION, null)
        if (previous != current) preferences.edit().putString(KEY_VERSION, current).commit()
        return previous
    }

    companion object {
        const val FILE_NAME = "plumemu_version_seen"
        private const val KEY_VERSION = "version_name"
    }
}
