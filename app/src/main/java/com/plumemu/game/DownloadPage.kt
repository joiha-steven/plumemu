package com.plumemu.game

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri

/**
 * An emulator's own page in a browser; false when nothing opens it. [url] is from the platform data,
 * which keeps only http(s) pages. [start] starts an activity and throws
 * [ActivityNotFoundException] when nothing handles the Intent (no `<queries>` needed for that).
 */
internal fun openDownloadPage(
    url: String,
    start: (Intent) -> Unit,
): Boolean =
    try {
        start(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
