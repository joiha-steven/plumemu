package com.plumemu

import android.content.ActivityNotFoundException
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.plumemu.coredata.backup.BackupKind
import com.plumemu.graph.AppGraph
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Android's save picker for Settings › Advanced › Back up… (`ACTION_CREATE_DOCUMENT`; no
 * permission). Its answer starts the backup in the graph, which outlives the Activity. Made while
 * the Activity is constructed (a result launcher must be registered before it starts).
 */
internal class BackupPicker(
    private val activity: ComponentActivity,
    private val graph: () -> AppGraph,
) {
    /** The kind asked for; kept in the saved state, because the picker may outlive the Activity. */
    private var pending: BackupKind? = null

    private val launcher =
        activity.registerForActivityResult(ActivityResultContracts.CreateDocument(ZIP_TYPE)) { uri ->
            val kind = pending
            pending = null
            // No uri: the person backed out of the picker, and nothing was made.
            if (kind == null || uri == null) return@registerForActivityResult
            val document = graph().backupDocument(uri)
            if (!graph().backup.start(kind, document)) {
                // Refused: one already runs (Settings shows Cancel then, so only a double answer), a restore
                // replaces the library, or box art is being cleared (the backup's state says so to the person).
                Log.w(TAG, "No backup was started (${graph().backup.state.value}); the new empty file is removed")
                runCatching { document.delete() }.onFailure { Log.w(TAG, "The new empty file could not be removed (${it.javaClass.simpleName})") }
            }
        }

    /** False when the device has no save picker. */
    fun open(kind: BackupKind): Boolean =
        try {
            pending = kind
            launcher.launch(defaultName(kind))
            true
        } catch (e: ActivityNotFoundException) {
            pending = null
            Log.w(TAG, "No save picker on this device (${e.javaClass.simpleName})")
            false
        }

    fun restore(saved: Bundle?) {
        pending = saved?.getString(STATE_KIND)?.let { code -> BackupKind.entries.firstOrNull { it.code == code } }
    }

    fun save(out: Bundle) {
        pending?.let { out.putString(STATE_KIND, it.code) }
    }

    /** "Plumemu backup <yyyy-MM-dd>.zip" or "Plumemu settings <yyyy-MM-dd>.zip" (the date as ISO, the same in every language). */
    private fun defaultName(kind: BackupKind): String {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
        val name = if (kind == BackupKind.Everything) R.string.settings_backup_file_everything else R.string.settings_backup_file_settings
        return activity.getString(name, day)
    }

    private companion object {
        const val TAG = "PlumBackup"
        const val ZIP_TYPE = "application/zip"
        const val STATE_KIND = "backup_picker_kind"
    }
}
