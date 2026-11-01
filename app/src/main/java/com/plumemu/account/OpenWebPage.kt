package com.plumemu.account

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri

/** Where a RetroAchievements Web API key is copied from. */
internal const val RA_KEY_PAGE_URL = "https://retroachievements.org/settings"

/**
 * Opens [url] in the device's browser. [start] starts an activity; false when no app handles the link
 * ([ActivityNotFoundException]) or Android refuses it ([SecurityException]).
 */
internal fun openWebPage(
    url: String,
    start: (Intent) -> Unit,
): Boolean =
    try {
        start(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        android.util.Log.w("PlumAccount", "No browser for the key page (${e.javaClass.simpleName})")
        false
    } catch (e: SecurityException) {
        android.util.Log.w("PlumAccount", "The key page was refused (${e.javaClass.simpleName})")
        false
    }
