package com.plumemu.settings.screen

import com.plumemu.coredata.backup.SettingsJson
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SortGamesSetting
import com.plumemu.game.SortChoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coreui.theme.AccentChoice
import com.plumemu.coreui.theme.AccentPreset
import com.plumemu.settings.toThemeSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.plumemu.coreui.testing.ReferenceShots

/** Rows Settings no longer has (Sort games, Clock and battery, Accent color, Show button hints, Grid size, System info): a backup holding their values restores, and the fixed behaviour applies. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
internal class SettingsRemovedRowsBackupTest {
    private fun restored(settings: PlumSettings): PlumSettings = SettingsJson.decode(SettingsJson.encode(settings).toString()).settings

    @Test
    fun aRestoredSortStaysTheSortInUseAndSettingsHasNoRowForIt() {
        val stored = PlumSettings.Defaults.copy(library = PlumSettings.Defaults.library.copy(sortGames = SortGamesSetting.LastPlayed))
        val sort = restored(stored).library.sortGames
        assertEquals(SortGamesSetting.LastPlayed, sort)
        // That the home list is then ordered by last played: LibraryHomeContentTest.aRestoredBackupHoldingLastPlayedOrdersTheListByLastPlayed.
        assertEquals("the games list's Sort by... lists it", true, sort in SortChoices(sort, hasYear = false, hasRating = false).options)
        val content = SettingsContent(librarySystems = emptyList())
        assertEquals(listOf(SettingsOption.RomFolders, SettingsOption.Rescan, SettingsOption.FilesNotAdded, SettingsOption.BoxArtSource, SettingsOption.RetroAchievementsAccount), SettingsSection.Library.rowsIn(content))
    }

    @Test
    fun aRestoredClockAndBatteryOffHasNoRowInHomeScreen() {
        val home = PlumSettings.Defaults.homeScreen.copy(showClock = false, showBattery = false)
        val back = restored(PlumSettings.Defaults.copy(homeScreen = home)).homeScreen
        assertFalse(back.showClock)
        assertFalse(back.showBattery)
        assertEquals(
            listOf(SettingsOption.UseAsHome, SettingsOption.ShowApps),
            SettingsSection.HomeScreen.rowsIn(SettingsContent()),
        )
    }

    /** Show button hints is no row: a backup holding it off restores, and neither Controls nor Home screen lists it, or Grid size, which it used to bring. */
    @Test
    fun aRestoredHintsOffHasNoRowAndBringsNoGridSizeRow() {
        val old = PlumSettings.Defaults.copy(controls = PlumSettings.Defaults.controls.copy(showButtonHints = false))
        val back = restored(old)
        assertFalse(back.controls.showButtonHints)
        assertEquals(
            listOf(SettingsOption.ConfirmBottom, SettingsOption.SwitchSystemWith, SettingsOption.PushRightToPlay, SettingsOption.Keyboard, SettingsOption.Sounds, SettingsOption.Vibration),
            SettingsSection.Controls.rowsIn(SettingsContent()),
        )
        assertEquals(listOf(SettingsOption.UseAsHome, SettingsOption.ShowApps), SettingsSection.HomeScreen.rowsIn(SettingsContent()))
        // That the main screen then shows the hint bar: SettingsScreenTest.aStoredHintsOffStillShowsTheMainScreensHints.
    }

    /** System info is no row: a backup holding either value restores it, and Home screen lists neither. */
    @Test
    fun aRestoredSystemInfoChoiceHasNoRow() {
        for (stored in listOf(true, false)) {
            val back = restored(PlumSettings.Defaults.copy(homeScreen = PlumSettings.Defaults.homeScreen.copy(showSystemInfo = stored)))
            assertEquals(stored, back.homeScreen.showSystemInfo)
            assertEquals(listOf(SettingsOption.UseAsHome, SettingsOption.ShowApps), SettingsSection.HomeScreen.rowsIn(SettingsContent()))
        }
        // That the band follows the second screen only: SystemInfoBandOnMainScreenTest.
    }

    private fun withAccent(accent: ColorSetting): PlumSettings =
        restored(PlumSettings.Defaults.copy(appearance = PlumSettings.Defaults.appearance.copy(accent = accent)))

    /** Accent color is a page of Colors' panel, not a row: a backup holding it, By system included, restores, applies and opens on its choice. */
    @Test
    fun aRestoredBySystemAccentAppliesAndItsPageOpensOnTheTile() {
        val settings = withAccent(ColorSetting.BySystem(ColorSetting.Preset("Amber")))
        assertEquals(ColorSetting.BySystem(ColorSetting.Preset("Amber")), settings.appearance.accent)
        assertTrue("the colours apply", settings.toThemeSettings().accent is AccentChoice.BySystem)
        assertFalse(SettingsOption.Accent in SettingsSection.Appearance.rowsIn(SettingsContent()))
        assertEquals(PanelPage.AccentPresets to ColorPages.bySystemIndex, ColorPages.openingFor(SettingsOption.Accent, settings))
    }

    @Test
    fun aRestoredOwnAndAPresetAccentApplyAndTheirPagesOpenOnThem() {
        val own = withAccent(ColorSetting.Custom(0x336699))
        assertEquals(ColorSetting.Custom(0x336699), own.appearance.accent)
        assertFalse(own.toThemeSettings().accent is AccentChoice.BySystem)
        val (ownPage, ownIndex) = ColorPages.openingFor(SettingsOption.Accent, own)
        assertEquals(PanelPage.AccentPresets, ownPage)
        assertTrue("an own colour opens below the presets, on a shade", ownIndex > AccentPreset.entries.size)
        val preset = withAccent(ColorSetting.Preset("Green"))
        assertEquals(PanelPage.AccentPresets, ColorPages.openingFor(SettingsOption.Accent, preset).first)
        assertEquals(AccentPreset.Green.ordinal, ColorPages.openingFor(SettingsOption.Accent, preset).second)
    }
}
