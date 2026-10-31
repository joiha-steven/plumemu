package com.plumemu.usage

import android.content.Intent
import com.plumemu.telemetry.OpenFrom

/** Set on the intent that opens Plumemu again after its own update (`UpdateRestartReceiver`). */
internal const val EXTRA_OPENED_AFTER_UPDATE = "com.plumemu.extra.OPENED_AFTER_UPDATE"

/**
 * `app_open.from` of the intent that opened or brought back the main screen: the Home key (Plumemu
 * is the home app), the app's icon, the reopening after an update, or anything else (a return from
 * a game, recents). Reads only the action, categories and one flag extra.
 */
internal fun openFromOf(intent: Intent?): OpenFrom {
    if (intent == null) return OpenFrom.Other
    // Any app can send the home intent with any extras: a bundle that cannot be read is no update.
    val afterUpdate =
        try {
            intent.getBooleanExtra(EXTRA_OPENED_AFTER_UPDATE, false)
        } catch (e: RuntimeException) {
            false
        }
    return when {
        afterUpdate -> OpenFrom.Update
        intent.action != Intent.ACTION_MAIN -> OpenFrom.Other
        intent.hasCategory(Intent.CATEGORY_HOME) -> OpenFrom.HomeKey
        intent.hasCategory(Intent.CATEGORY_LAUNCHER) -> OpenFrom.Icon
        else -> OpenFrom.Other
    }
}
