package com.plumemu.launcher

import android.os.Environment
import com.plumemu.library.storage.ExternalStorageIds
import com.plumemu.library.storage.StorageRoot
import java.io.File

/** Says whether the storage volume a game's file lives on is out (an SD card or USB drive taken out). */
internal fun interface StorageVolumes {
    /** [documentUri] is a tree-based document URI. Blocking only for a moment; call off the main thread. */
    fun isAbsent(documentUri: String): Boolean

    companion object {
        val AllPresent = StorageVolumes { false }

        /**
         * The volume id of an ExternalStorageProvider tree URI (`1A2B-3C4D` in `.../tree/1A2B-3C4D%3AROMs`);
         * null for the device's own storage (`primary`, `home`) and for any other provider, whose volumes
         * Plumemu cannot tell. A pure string read so it works in plain JVM tests.
         */
        fun removableVolumeOf(documentUri: String): String? =
            (ExternalStorageIds.treeDocument(documentUri)?.root as? StorageRoot.Volume)?.id
    }
}

/**
 * Android's own answer for `/storage/<volume id>`. Absent only on an explicit "the card is out" state;
 * read-only, unknown (USB drives some versions hide from apps), unmountable and unreadable-card states, and
 * a failed binder call, all count as present, so the old message (and its Rescan) stays.
 */
internal class AndroidStorageVolumes(
    private val stateOf: (File) -> String = Environment::getExternalStorageState,
    private val log: LauncherLog = AndroidLauncherLog,
) : StorageVolumes {
    override fun isAbsent(documentUri: String): Boolean {
        val volume = StorageVolumes.removableVolumeOf(documentUri) ?: return false
        return try {
            stateOf(File("/storage/$volume")) in ABSENT_STATES
        } catch (e: RuntimeException) {
            log.warn("launch: storage volume state failed (${e.kind()})")
            false
        }
    }

    companion object {
        val ABSENT_STATES = setOf(Environment.MEDIA_REMOVED, Environment.MEDIA_UNMOUNTED, Environment.MEDIA_BAD_REMOVAL, Environment.MEDIA_EJECTING)
    }
}
