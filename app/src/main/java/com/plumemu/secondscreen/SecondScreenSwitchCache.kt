package com.plumemu.secondscreen

import android.content.Context
import com.plumemu.coredata.settings.SecondScreenSettings

/**
 * The stored second-screen switch (`second_screen.enabled`: on, off or not decided), kept in a tiny
 * SharedPreferences file that `onCreate` can read at once, like `AppearanceCache`. The settings
 * themselves arrive after the first frame and the window only after it, so without this the top
 * screen would draw its first frame with the details column and then lose it.
 */
internal class SecondScreenSwitchCache(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** The switch as last stored; null when not decided (or never cached: a first launch). */
    fun read(): Boolean? = if (prefs.contains(KEY_ENABLED)) prefs.getBoolean(KEY_ENABLED, false) else null

    /** Called for every stored change of the switch; writes only when the cached value differs. */
    fun write(enabled: Boolean?) {
        if (enabled == read()) return
        val edit = prefs.edit()
        if (enabled == null) edit.remove(KEY_ENABLED) else edit.putBoolean(KEY_ENABLED, enabled)
        edit.apply()
    }

    private companion object {
        const val FILE_NAME = "second_screen_cache"
        const val KEY_ENABLED = "enabled"
    }
}

/**
 * Whether the window will most likely be shown, known before the first frame: a
 * second screen among [displays] (the same choice `SecondScreenDisplays` makes) and the switch
 * [enabled] (cached) on, or not decided and the screen built in, or [forcedOn] (benchmark builds).
 * The host corrects it: a window refused, or a stored switch that turns out off, makes it false and
 * the details column comes back.
 */
internal fun isWindowLikelyShown(
    enabled: Boolean?,
    displays: List<DisplayFacts>,
    forcedOn: Boolean = false,
): Boolean {
    val screen = chooseSecondScreen(displays) ?: return false
    return isSecondScreenOn(SecondScreenSettings(enabled = enabled), screen) || forcedOn
}
