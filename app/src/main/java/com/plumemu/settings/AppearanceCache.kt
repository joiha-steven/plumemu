package com.plumemu.settings

import android.content.Context
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextMode

/**
 * The resolved background colour and text mode, kept beside the settings file in a tiny
 * SharedPreferences file that `onCreate` can read at once. The settings themselves arrive a little
 * after the first frame (DataStore reads off the main thread); the window paints this colour
 * meanwhile, so a cold start or a recreated Activity never flashes another background.
 * One small XML file of two values: its synchronous read costs well under a millisecond.
 */
class AppearanceCache(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** What the window shows before the settings arrive; Black with light text on a first launch. */
    fun read(): CachedAppearance =
        CachedAppearance(
            backgroundArgb = prefs.getInt(KEY_BACKGROUND, PlumThemeSettings.Default.backgroundColor.toArgb()),
            isTextDark = prefs.getBoolean(KEY_TEXT_DARK, false),
        )

    /** Called for every stored appearance change; writes only when the cached values differ. */
    fun write(appearance: AppearanceSettings) {
        val theme = appearance.toThemeSettings(animationsOn = true)
        val next = CachedAppearance(theme.backgroundColor.toArgb(), theme.textMode == TextMode.Dark)
        if (next == read()) return
        prefs.edit().putInt(KEY_BACKGROUND, next.backgroundArgb).putBoolean(KEY_TEXT_DARK, next.isTextDark).apply()
    }

    private companion object {
        const val FILE_NAME = "appearance_cache"
        const val KEY_BACKGROUND = "background_argb"
        const val KEY_TEXT_DARK = "text_dark"
    }
}

data class CachedAppearance(val backgroundArgb: Int, val isTextDark: Boolean) {
    val textMode: TextMode get() = if (isTextDark) TextMode.Dark else TextMode.Light
}
