package com.plumemu.game

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri

/**
 * An emulator's store page: the store app (`market://details?id=`), else the Play page in a
 * browser; false when neither opens. [start] starts an activity and throws
 * [ActivityNotFoundException] when nothing handles the Intent (no `<queries>` needed for that).
 */
internal fun openStorePage(
    packageName: String,
    start: (Intent) -> Unit,
): Boolean =
    storePageUris(packageName).any { uri ->
        try {
            start(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            // Tried in order: the store app first, then the web page; the caller reports when both fail.
            false
        }
    }

internal fun storePageUris(packageName: String): List<String> =
    listOf("market://details?id=$packageName", "https://play.google.com/store/apps/details?id=$packageName")
