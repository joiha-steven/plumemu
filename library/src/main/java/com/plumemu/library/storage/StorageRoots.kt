package com.plumemu.library.storage

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.util.Log
import java.io.File

/** Where the roots of [ExternalStorageIds] are on this device. */
interface StorageRoots {
    /** This user's own storage: `/storage/emulated/0` for the first user, `/storage/emulated/<n>` for another user or a work profile. */
    fun internal(): File?

    /** Where the volume [id] is mounted; null when it has no path. */
    fun volume(id: String): File?
}

/**
 * Android's answer. Internal storage is `Environment.getExternalStorageDirectory()` (per user on every
 * version). A volume is asked of `StorageManager` on Android 11+ (`StorageVolume.getDirectory`);
 * before that, and when Android does not know the id or it is not mounted, `/storage/<id>`, where
 * Android mounts every public volume (the answer older versions gave, so existing folders keep it).
 */
class AndroidStorageRoots(context: Context) : StorageRoots {
    private val storageManager = context.applicationContext.getSystemService(StorageManager::class.java)

    @Suppress("DEPRECATION") // Still the per-user answer; its replacement (StorageVolume.getDirectory) is API 30.
    override fun internal(): File? = Environment.getExternalStorageDirectory()

    override fun volume(id: String): File = askedDirectory(id) ?: File("/storage", id)

    private fun askedDirectory(id: String): File? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || storageManager == null) return null
        return try {
            storageManager.storageVolumes.firstOrNull { it.uuid.equals(id, ignoreCase = true) }?.directory
        } catch (e: RuntimeException) {
            // A failed binder call: fall back to the mount point. Only the class name: no paths in the log.
            Log.w(TAG, "storage volumes unreadable (${e.javaClass.simpleName}); using /storage/<id>")
            null
        }
    }

    private companion object {
        const val TAG = "PlumemuStorage"
    }
}
