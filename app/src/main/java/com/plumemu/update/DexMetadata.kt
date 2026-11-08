package com.plumemu.update

import android.content.pm.PackageInstaller
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/**
 * A dex metadata file (`.dm`) published beside the APK, built with it: installed in the same
 * session, it makes Android compile the app with its Baseline Profile at install instead of days
 * later. For devices on API [minSdk] to [maxSdk] (null: no upper bound); [url] is resolved against
 * the manifest's address and on its server.
 */
data class DexMetadataFile(
    val minSdk: Int,
    val maxSdk: Int?,
    val url: String,
    val size: Long,
    /** Lower-case hex, 64 characters. */
    val sha256: String,
)

/** The rules of dex metadata in an update. Nothing here touches the network or a session. */
object DexMetadata {
    /** Android 9: the first that reads a `.dm` in an install session. */
    const val FIRST_SDK = 28

    /** A profile is tens of kilobytes; anything past this is not one. */
    const val MAX_BYTES = 1024L * 1024

    /** One per profile format Android has had, with room to spare. */
    const val MAX_ENTRIES = 8

    /** The entry that zip must hold: the profile itself. */
    const val PROFILE_ENTRY = "primary.prof"

    /** The entry for a device on [sdk]: none below [FIRST_SDK], none when no range holds it. */
    fun forSdk(
        entries: List<DexMetadataFile>,
        sdk: Int,
    ): DexMetadataFile? {
        if (sdk < FIRST_SDK) return null
        return entries.firstOrNull { sdk >= it.minSdk && (it.maxSdk == null || sdk <= it.maxSdk) }
    }

    /** Whether two of [entries] hold the same API level. */
    fun overlap(entries: List<DexMetadataFile>): Boolean {
        val sorted = entries.sortedBy { it.minSdk }
        return sorted.zipWithNext().any { (a, b) -> a.maxSdk == null || a.maxSdk >= b.minSdk }
    }

    /**
     * Whether a failed session that carried a `.dm` may have failed because of it: Android reports a bad
     * one (INSTALL_FAILED_BAD_DEX_METADATA) as [PackageInstaller.STATUS_FAILURE_INVALID], and a generic
     * [PackageInstaller.STATUS_FAILURE] is not ruled out. The person's no, storage, a conflict or an
     * incompatible APK are not.
     */
    fun mayComeFromDexMetadata(
        status: Int,
        message: String?,
    ): Boolean =
        status == PackageInstaller.STATUS_FAILURE_INVALID ||
            status == PackageInstaller.STATUS_FAILURE ||
            message?.contains("DEX_METADATA", ignoreCase = true) == true

    /** Whether [file] is a zip holding [PROFILE_ENTRY]; any read error is a no. */
    fun hasProfile(file: File): Boolean =
        try {
            ZipFile(file).use { it.getEntry(PROFILE_ENTRY) != null }
        } catch (e: IOException) {
            false
        }
}
