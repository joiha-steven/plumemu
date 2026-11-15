package com.plumemu.graph

import android.os.Build
import com.plumemu.coredata.settings.AppsScreen
import com.plumemu.coredata.settings.SecondScreenSettings
import com.plumemu.secondscreen.SecondScreen
import com.plumemu.secondscreen.isSecondScreenOn

/**
 * The screen the app menu's "Open on …" row offers: the one [SecondScreenSettings.appsOn] does not name, or null
 * when no second screen is found and switched on (or below API 26). Same gate as [appDisplayId].
 */
internal fun otherAppsScreen(
    settings: SecondScreenSettings,
    screen: SecondScreen?,
    sdk: Int = Build.VERSION.SDK_INT,
): AppsScreen? =
    if (screen == null || sdk < Build.VERSION_CODES.O || !isSecondScreenOn(settings, screen)) {
        null
    } else if (settings.appsOn == AppsScreen.Bottom) {
        AppsScreen.Top
    } else {
        AppsScreen.Bottom
    }

/**
 * The display an app of the Apps entry opens on: the second screen's id, or null for this screen.
 * The second screen only when it is found, switched on and the wanted screen is Bottom, and from API 26
 * (below, `setLaunchDisplayId` does not exist). The wanted screen is the stored [SecondScreenSettings.appsOn],
 * or its opposite for a one-off ([otherScreen]).
 */
internal fun appDisplayId(
    settings: SecondScreenSettings,
    screen: SecondScreen?,
    otherScreen: Boolean,
    sdk: Int = Build.VERSION.SDK_INT,
): Int? {
    if (screen == null || sdk < Build.VERSION_CODES.O || !isSecondScreenOn(settings, screen)) return null
    val bottom = (settings.appsOn == AppsScreen.Bottom) != otherScreen
    return if (bottom) screen.displayId else null
}
