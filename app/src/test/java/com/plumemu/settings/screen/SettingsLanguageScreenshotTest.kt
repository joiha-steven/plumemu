package com.plumemu.settings.screen

import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Advanced's new rows (Sounds' three steps, Vibration, Language) and the language list, in the reference sizes and looks. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsLanguageScreenshotTest {
    /** Sounds focused (‹ Loud ›), Vibration and Language rows under it. */
    @Test
    fun advancedList() =
        ReferenceShots.capture("settings_advanced_sounds") { SettingsAt(optionsAt(SettingsOption.Sounds), content = ShotContent.library) }

    /** The Language row focused: "English »" and the list beside it (wide). */
    @Test
    fun languageRow() =
        ReferenceShots.capture("settings_advanced_language") { SettingsAt(optionsAt(SettingsOption.Language), content = ShotContent.library) }

    /** The list entered, the cursor on the third language: every language in its own name and script, the current one marked. */
    @Test
    fun languagePanel() =
        ReferenceShots.capture("settings_language_panel") { SettingsAt(panelAt(SettingsOption.Language, PanelPage.Language, 2), content = ShotContent.library) }
}
