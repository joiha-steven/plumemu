package com.plumemu.launcher.intent

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import com.plumemu.platforms.ExtraValue
import com.plumemu.platforms.LaunchFlag
import com.plumemu.platforms.LaunchSpec

/** Turns the pure [LaunchSpec] of the platforms module into the Intent that starts the emulator. */
internal object LaunchIntents {
    /** A MIME type the platform data leaves to the file (Daijisho `{file.mime}`). */
    const val FILE_MIME_PLACEHOLDER = "{file.mime}"

    /**
     * [grantUri] is the game's document URI when the emulator takes a URI: the Intent then carries
     * a read grant for that one document, for this launch only. It is put in the ClipData too,
     * because Android applies grant flags to the data and the ClipData, never to an extra, and
     * many emulators (Dolphin, DuckStation, NetherSX2) take the URI in an extra. Only a read grant:
     * Plumemu holds read access to the picked folders and cannot pass on more.
     * [documentMimeType] replaces [FILE_MIME_PLACEHOLDER] (null when the provider gave none).
     */
    fun build(spec: LaunchSpec, grantUri: String?, documentMimeType: String?): Intent {
        val intent = Intent()
        spec.action?.let { intent.action = it }
        val className = spec.className
        if (className != null) intent.setClassName(spec.packageName, className) else intent.setPackage(spec.packageName)
        spec.category?.let { intent.addCategory(it) }
        val mimeType = if (spec.mimeType == FILE_MIME_PLACEHOLDER) documentMimeType else spec.mimeType
        val data = spec.data
        when {
            // Uri.parse keeps the text as given, as other launchers pass it; a path stays a bare
            // path (a file:// URI would throw FileUriExposedException from Android 7).
            data != null -> intent.setDataAndType(Uri.parse(data), mimeType)
            mimeType != null -> intent.type = mimeType
        }
        for ((key, value) in spec.extras) {
            when (value) {
                is ExtraValue.Text -> intent.putExtra(key, value.value)
                is ExtraValue.Flag -> intent.putExtra(key, value.value)
                is ExtraValue.Number -> intent.putExtra(key, value.value)
                is ExtraValue.TextArray -> intent.putExtra(key, value.values.toTypedArray())
            }
        }
        // Plumemu starts emulators from the launcher, as a new task, like any home screen does.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        for (flag in spec.flags) intent.addFlags(flagOf(flag))
        if (grantUri != null) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.clipData = ClipData.newRawUri("", Uri.parse(grantUri))
        }
        return intent
    }

    private fun flagOf(flag: LaunchFlag): Int = when (flag) {
        LaunchFlag.ClearTask -> Intent.FLAG_ACTIVITY_CLEAR_TASK
        LaunchFlag.ClearTop -> Intent.FLAG_ACTIVITY_CLEAR_TOP
        LaunchFlag.NoHistory -> Intent.FLAG_ACTIVITY_NO_HISTORY
    }
}
