package com.plumemu

import android.content.ActivityNotFoundException
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.plumemu.graph.AppGraph

/**
 * Android's open picker for Settings › Advanced › Restore… (`ACTION_OPEN_DOCUMENT`; no permission:
 * the picker grants read access to the one file). Its answer starts the reading in the graph, which
 * outlives the Activity. Made while the Activity is constructed (a result launcher must be
 * registered before it starts).
 */
internal class RestorePicker(
    activity: ComponentActivity,
    private val graph: () -> AppGraph,
) {
    private val launcher =
        activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            // No uri: the person backed out of the picker; nothing was read.
            if (uri == null) return@registerForActivityResult
            if (!graph().restore.read(graph().restoreInput(uri))) Log.w(TAG, "A restore already runs; the picked file is left alone")
        }

    /** False when the device has no open picker. */
    fun open(): Boolean =
        try {
            // A zip first; any file as well, because some providers name a zip's type differently.
            launcher.launch(arrayOf(ZIP_TYPE, ANY_TYPE))
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No open picker on this device (${e.javaClass.simpleName})")
            false
        }

    private companion object {
        const val TAG = "PlumRestore"
        const val ZIP_TYPE = "application/zip"
        const val ANY_TYPE = "*/*"
    }
}
