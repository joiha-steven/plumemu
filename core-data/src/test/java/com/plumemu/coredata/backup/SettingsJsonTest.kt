package com.plumemu.coredata.backup

import com.plumemu.coredata.settings.AdvancedSettings
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.BoxArtRegionSetting
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.ConfirmButtonSetting
import com.plumemu.coredata.settings.ControlsSettings
import com.plumemu.coredata.settings.FontSetting
import com.plumemu.coredata.settings.GridSizeSetting
import com.plumemu.coredata.settings.HomeScreenSettings
import com.plumemu.coredata.settings.InGameSetting
import com.plumemu.coredata.settings.KeyboardSetting
import com.plumemu.coredata.settings.LeftEdgeSetting
import com.plumemu.coredata.settings.LibrarySettings
import com.plumemu.coredata.settings.OwnColors
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.RecentSizeSetting
import com.plumemu.coredata.settings.SecondScreenSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coredata.settings.SortGamesSetting
import com.plumemu.coredata.settings.SoundLevel
import com.plumemu.coredata.settings.SwitchSystemWithSetting
import com.plumemu.coredata.settings.SystemOrderSetting
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coredata.settings.TextWeightSetting
import com.plumemu.coredata.settings.UpdateSettings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** settings.json: every field survives a write and a read; unknown values fall back. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsJsonTest {
    /** Every field away from its default, so a field the JSON forgot cannot pass by being the default. */
    private val everyFieldChanged =
        PlumSettings(
            appearance = AppearanceSettings(ColorSetting.Custom(0x123ABC), TextColorSetting.Dark, ColorSetting.Preset("Teal"), FontSetting.Mono, TextSizeSetting.Huge, GridSizeSetting.Large, gridSystems = setOf("snes", "psp", "My Hacks"), ownColors = OwnColors(ColorSetting.Custom(0x2B54B5), TextColorSetting.Light, ColorSetting.Preset("Blue"), buttonSet = "Charcoal"), buttonSet = "Ivory", textWeight = TextWeightSetting.Medium, leftEdge = LeftEdgeSetting.Letters, language = "vi"),
            controls = ControlsSettings(ConfirmButtonSetting.Bottom, holdToScrollFaster = false, showButtonHints = false, keyboard = KeyboardSetting.Android, pushRightToPlay = true, switchSystemWith = SwitchSystemWithSetting.Bumpers),
            library =
                LibrarySettings(
                    scanOnStartup = false,
                    sortGames = SortGamesSetting.LastPlayed,
                    boxArtRegion = BoxArtRegionSetting.Japan,
                    boxArtPaused = true,
                    systemOrder = SystemOrderSetting.Manual,
                    manualSystemOrder = listOf("snes", "nes", "My Hacks"),
                    hiddenSystems = setOf("gb", "gbc"),
                    recentSize = RecentSizeSetting.Ten,
                ),
            homeScreen = HomeScreenSettings(showApps = false, showClock = false, showBattery = false, showSystemInfo = false),
            secondScreen = SecondScreenSettings(enabled = false, inGame = InGameSetting.Black),
            advanced = AdvancedSettings(animations = false, sounds = SoundLevel.Quiet, usageStatistics = false, vibration = false),
            updates = UpdateSettings(checkDaily = false),
            flags = SetupFlags(
                setupCompleted = true, homeRoleAsked = true, rescanAfterReset = true, folderMatchVersion = 3, namingRulesVersion = 4, scrapeRulesVersion = 5,
                inGameDriftNoteShown = true,
            ),
        )

    private fun roundTrip(settings: PlumSettings): SettingsJson.Decoded = SettingsJson.decode(SettingsJson.encode(settings).toString())

    @Test
    fun aFileFromBeforeNamingRulesVersionsGivesZeroSoItsLibraryIsNamedAgain() {
        val json = SettingsJson.encode(everyFieldChanged)
        json.getJSONObject("flags").remove("namingRulesVersion")
        val decoded = SettingsJson.decode(json.toString())
        assertEquals(0, decoded.settings.flags.namingRulesVersion)
        assertEquals("a missing key is not an unknown value", emptyList<String>(), decoded.unknownKeys)
    }

    @Test
    fun aFileFromBeforeScrapeRulesVersionsGivesZeroSoItsBoxArtIsRepaired() {
        val json = SettingsJson.encode(everyFieldChanged)
        json.getJSONObject("flags").remove("scrapeRulesVersion")
        val decoded = SettingsJson.decode(json.toString())
        assertEquals(0, decoded.settings.flags.scrapeRulesVersion)
        assertEquals("a missing key is not an unknown value", emptyList<String>(), decoded.unknownKeys)
    }

    @Test
    fun anOldFileWithTheRemovedHideUnidentifiedFilesKeyStillLoads() {
        val json = SettingsJson.encode(everyFieldChanged)
        json.getJSONObject("library").put("hideUnidentifiedFiles", true)
        val decoded = SettingsJson.decode(json.toString())
        assertEquals("the removed key is ignored, everything else is read", everyFieldChanged, decoded.settings)
    }

    @Test
    fun anOldFileWithReturnHereAfterPlayingAndNoRecentSizeStillLoadsWithTwenty() {
        val json = SettingsJson.encode(everyFieldChanged)
        json.getJSONObject("homeScreen").put("returnHereAfterPlaying", false)
        json.getJSONObject("library").remove("recentSize")
        val decoded = SettingsJson.decode(json.toString())
        assertEquals(
            "the removed key is ignored, the missing size reads as 20, everything else is read",
            everyFieldChanged.copy(library = everyFieldChanged.library.copy(recentSize = RecentSizeSetting.Twenty)),
            decoded.settings,
        )
        assertEquals("a missing key is not an unknown value", emptyList<String>(), decoded.unknownKeys)
    }

    @Test
    fun theRecentSizeIsWrittenByNameAndReturnHereAfterPlayingIsNoLongerWritten() {
        val json = SettingsJson.encode(everyFieldChanged)
        assertEquals("Ten", json.getJSONObject("library").getString("recentSize"))
        assertFalse(json.getJSONObject("homeScreen").has("returnHereAfterPlaying"))
    }

    @Test
    fun aBackupFromBeforeLeftEdgeReadsAsNotPickedAndTheDefaultsKeepItNull() {
        val json = SettingsJson.encode(everyFieldChanged)
        json.getJSONObject("appearance").remove("leftEdge")
        val decoded = SettingsJson.decode(json.toString())
        assertEquals(null, decoded.settings.appearance.leftEdge)
        assertEquals("a missing key is not an unknown value", emptyList<String>(), decoded.unknownKeys)
        assertEquals("Letters", SettingsJson.encode(everyFieldChanged).getJSONObject("appearance").getString("leftEdge"))
        assertTrue(SettingsJson.encode(PlumSettings.Defaults).getJSONObject("appearance").isNull("leftEdge"))
    }

    @Test
    fun aBackupFromBeforeTextWeightReadsAsNotPickedAndTheDefaultsKeepItNull() {
        val json = SettingsJson.encode(everyFieldChanged)
        json.getJSONObject("appearance").remove("textWeight")
        val decoded = SettingsJson.decode(json.toString())
        assertEquals(null, decoded.settings.appearance.textWeight)
        assertEquals("a missing key is not an unknown value", emptyList<String>(), decoded.unknownKeys)
        assertEquals("Medium", SettingsJson.encode(everyFieldChanged).getJSONObject("appearance").getString("textWeight"))
        assertTrue(SettingsJson.encode(PlumSettings.Defaults).getJSONObject("appearance").isNull("textWeight"))
    }

    @Test
    fun everyChangedFieldComesBackEqual() {
        val decoded = roundTrip(everyFieldChanged)
        assertEquals(everyFieldChanged, decoded.settings)
        assertEquals(SettingsJson.VERSION, decoded.version)
        assertEquals(emptyList<String>(), decoded.unknownKeys)
    }

    @Test
    fun theDefaultsComeBackEqualWithTextSizeAndSecondScreenStillUndecided() {
        val decoded = roundTrip(PlumSettings.Defaults)
        assertEquals(PlumSettings.Defaults, decoded.settings)
        val json = SettingsJson.encode(PlumSettings.Defaults)
        assertTrue(json.getJSONObject("appearance").isNull("textSize"))
        assertTrue(json.getJSONObject("secondScreen").isNull("enabled"))
    }

    @Test
    fun theFileSpellsValuesAsTheSettingsFileDoes() {
        val json = SettingsJson.encode(everyFieldChanged)
        assertEquals("#123ABC", json.getJSONObject("appearance").getString("background"))
        assertEquals("jp", json.getJSONObject("library").getString("boxArtRegion"))
        assertEquals("manual", json.getJSONObject("library").getString("systemOrder"))
        assertEquals("black", json.getJSONObject("secondScreen").getString("inGame"))
        assertEquals("Android", json.getJSONObject("controls").getString("keyboard"))
        assertEquals(listOf("snes", "nes", "My Hacks"), json.getJSONObject("library").getJSONArray("manualSystemOrder").let { a -> List(a.length()) { a.getString(it) } })
    }

    @Test
    fun aValueThisVersionDoesNotKnowReadsAsItsDefaultAndIsListed() {
        val json = SettingsJson.encode(everyFieldChanged)
        json.getJSONObject("appearance").put("font", "Comic")
        json.getJSONObject("library").put("boxArtRegion", "mars")
        json.getJSONObject("advanced").put("animations", "yes")
        val decoded = SettingsJson.decode(json.toString())
        assertEquals(FontSetting.Sans, decoded.settings.appearance.font)
        assertEquals(BoxArtRegionSetting.Usa, decoded.settings.library.boxArtRegion)
        assertEquals(true, decoded.settings.advanced.animations)
        assertEquals(listOf("appearance.font", "library.boxArtRegion", "advanced.animations").sorted(), decoded.unknownKeys.sorted())
        // The rest is kept.
        assertEquals(everyFieldChanged.library.hiddenSystems, decoded.settings.library.hiddenSystems)
    }

    @Test
    fun anOlderBackupsSoundsSwitchReadsAsALevel() {
        fun decodedWith(sounds: Any) = SettingsJson.encode(everyFieldChanged).also { it.getJSONObject("advanced").put("sounds", sounds) }.let { SettingsJson.decode(it.toString()) }
        assertEquals(SoundLevel.Loud, decodedWith(true).settings.advanced.sounds)
        assertEquals(SoundLevel.Off, decodedWith(false).settings.advanced.sounds)
        assertEquals(emptyList<String>(), decodedWith(true).unknownKeys)
        assertEquals(SoundLevel.Quiet, decodedWith("quiet").settings.advanced.sounds)
        assertEquals(SoundLevel.Loud, decodedWith("shout").settings.advanced.sounds)
        assertEquals(listOf("advanced.sounds"), decodedWith("shout").unknownKeys)
    }

    /** A backup made before updates existed has no `updates` group: Check for updates daily reads as on, its default, and nothing is listed as unknown. */
    @Test
    fun aBackupFromBeforeUpdatesReadsTheDailyCheckAsOn() {
        val json = SettingsJson.encode(everyFieldChanged)
        json.remove("updates")
        val decoded = SettingsJson.decode(json.toString())
        assertEquals(true, decoded.settings.updates.checkDaily)
        assertEquals(emptyList<String>(), decoded.unknownKeys)
        assertEquals(everyFieldChanged.copy(updates = UpdateSettings()), decoded.settings)
    }

    /** An older backup has no `gridSystems`: every system reads as a list, the rest of the group is kept. */
    @Test
    fun aBackupFromBeforeGridSystemsReadsEveryListAndKeepsTheRest() {
        val json = SettingsJson.encode(everyFieldChanged)
        json.getJSONObject("appearance").remove("gridSystems")
        val decoded = SettingsJson.decode(json.toString())
        assertEquals(emptySet<String>(), decoded.settings.appearance.gridSystems)
        assertEquals("a missing key is not an unknown value", emptyList<String>(), decoded.unknownKeys)
        assertEquals(everyFieldChanged.copy(appearance = everyFieldChanged.appearance.copy(gridSystems = emptySet())), decoded.settings)
    }

    @Test
    fun gridSystemsAreWrittenSortedAndComeBackAsTheSameSet() {
        val json = SettingsJson.encode(everyFieldChanged)
        val written = json.getJSONObject("appearance").getJSONArray("gridSystems").let { a -> List(a.length()) { a.getString(it) } }
        assertEquals("sorted: the same settings give the same file", listOf("My Hacks", "psp", "snes"), written)
        assertEquals(everyFieldChanged.appearance.gridSystems, roundTrip(everyFieldChanged).settings.appearance.gridSystems)
        // A damaged value (not a list) is listed and reads as every list; blanks are dropped.
        json.getJSONObject("appearance").put("gridSystems", "snes")
        val damaged = SettingsJson.decode(json.toString())
        assertEquals(emptySet<String>(), damaged.settings.appearance.gridSystems)
        assertEquals(listOf("appearance.gridSystems"), damaged.unknownKeys)
        json.getJSONObject("appearance").put("gridSystems", org.json.JSONArray(listOf("snes", " ", "")))
        assertEquals(setOf("snes"), SettingsJson.decode(json.toString()).settings.appearance.gridSystems)
    }

    /** Recent's, Favorites' and Apps' grids go through a backup like the systems'. */
    @Test
    fun theEntriesGridsSurviveABackupAndABackupFromBeforeThemReadsUnchanged() {
        val withEntries = everyFieldChanged.copy(appearance = everyFieldChanged.appearance.copy(gridSystems = setOf("snes", "recent", "favorites", "apps")))
        val decoded = roundTrip(withEntries)
        assertEquals(setOf("snes", "recent", "favorites", "apps"), decoded.settings.appearance.gridSystems)
        assertEquals(emptyList<String>(), decoded.unknownKeys)
        assertEquals(withEntries, decoded.settings)
        // A backup from before has systems only: they come back as they were, the entries as lists.
        val before = SettingsJson.encode(everyFieldChanged)
        before.getJSONObject("appearance").put("gridSystems", org.json.JSONArray(listOf("psp", "snes")))
        assertEquals(setOf("psp", "snes"), SettingsJson.decode(before.toString()).settings.appearance.gridSystems)
    }

    /** An older backup has no `ownColors`: none kept, the rest of the group as it was. */
    @Test
    fun aBackupFromBeforeOwnColorsReadsNoneAndKeepsTheRest() {
        val json = SettingsJson.encode(everyFieldChanged)
        json.getJSONObject("appearance").remove("ownColors")
        val decoded = SettingsJson.decode(json.toString())
        assertEquals(null, decoded.settings.appearance.ownColors)
        assertEquals("a missing key is not an unknown value", emptyList<String>(), decoded.unknownKeys)
        assertEquals(everyFieldChanged.copy(appearance = everyFieldChanged.appearance.copy(ownColors = null)), decoded.settings)
    }

    /** A custom text colour is `#RRGGBB` in the backup and in the kept own colours; old Light / Dark values read as before. */
    @Test
    fun aCustomTextColourComesBackFromABackupAndOldValuesReadUnchanged() {
        val custom =
            everyFieldChanged.copy(
                appearance =
                    everyFieldChanged.appearance.copy(
                        textColor = TextColorSetting.Custom(0xC8E6C9),
                        ownColors = OwnColors(ColorSetting.Preset("Plum"), TextColorSetting.Custom(0x203020), ColorSetting.Preset("Amber")),
                    ),
            )
        val json = SettingsJson.encode(custom)
        assertEquals("#C8E6C9", json.getJSONObject("appearance").getString("textColor"))
        assertEquals("#203020", json.getJSONObject("appearance").getJSONObject("ownColors").getString("textColor"))
        val back = roundTrip(custom)
        assertEquals(custom.appearance, back.settings.appearance)
        assertEquals(emptyList<String>(), back.unknownKeys)
        // A backup from before: the names.
        val old = SettingsJson.encode(everyFieldChanged)
        assertEquals("Dark", old.getJSONObject("appearance").getString("textColor"))
        assertEquals(TextColorSetting.Dark, SettingsJson.decode(old.toString()).settings.appearance.textColor)
        assertEquals(TextColorSetting.Light, SettingsJson.decode(old.toString()).settings.appearance.ownColors?.textColor)
        // A value this version does not know is the default, and named.
        old.getJSONObject("appearance").put("textColor", "Sepia")
        val unknown = SettingsJson.decode(old.toString())
        assertEquals(TextColorSetting.Light, unknown.settings.appearance.textColor)
        assertTrue(unknown.unknownKeys.contains("appearance.textColor"))
    }

    /** By system and its fallback go through a backup, also in the kept own colours; a backup from before reads as it did. */
    @Test
    fun bySystemComesBackFromABackupWithItsFallback() {
        val bySystem =
            everyFieldChanged.copy(
                appearance =
                    everyFieldChanged.appearance.copy(
                        accent = ColorSetting.BySystem(ColorSetting.Custom(0x336699)),
                        ownColors = OwnColors(ColorSetting.Preset("Plum"), TextColorSetting.Light, ColorSetting.BySystem(ColorSetting.Preset("Amber"))),
                    ),
            )
        val json = SettingsJson.encode(bySystem)
        assertEquals("BySystem:#336699", json.getJSONObject("appearance").getString("accent"))
        assertEquals("BySystem:Amber", json.getJSONObject("appearance").getJSONObject("ownColors").getString("accent"))
        val back = roundTrip(bySystem)
        assertEquals(bySystem.appearance, back.settings.appearance)
        assertEquals(emptyList<String>(), back.unknownKeys)
        // A backup from before: the accent is a name, read unchanged.
        val old = SettingsJson.encode(everyFieldChanged)
        assertEquals(everyFieldChanged.appearance.accent, SettingsJson.decode(old.toString()).settings.appearance.accent)
        // By system is the accent's alone: as a background it is unknown, named, and the default stands.
        old.getJSONObject("appearance").put("background", "BySystem:Red")
        val unknown = SettingsJson.decode(old.toString())
        assertEquals(PlumSettings.Defaults.appearance.background, unknown.settings.appearance.background)
        assertTrue(unknown.unknownKeys.contains("appearance.background"))
    }

    @Test
    fun ownColorsAreWrittenAsTheFileSpellsColoursAndComeBackExactly() {
        val json = SettingsJson.encode(everyFieldChanged)
        val written = json.getJSONObject("appearance").getJSONObject("ownColors")
        assertEquals("#2B54B5", written.getString("background"))
        assertEquals("Light", written.getString("textColor"))
        assertEquals("Blue", written.getString("accent"))
        assertEquals(everyFieldChanged.appearance.ownColors, roundTrip(everyFieldChanged).settings.appearance.ownColors)
        // None kept is written as null, so a restore clears the device's, and read back as none.
        val none = SettingsJson.encode(PlumSettings.Defaults)
        assertTrue(none.getJSONObject("appearance").isNull("ownColors"))
        val decodedNone = SettingsJson.decode(none.toString())
        assertEquals(null, decodedNone.settings.appearance.ownColors)
        assertEquals(emptyList<String>(), decodedNone.unknownKeys)
    }

    /** Settings › Advanced › Language goes through a backup; System default (null) clears a picked one; an older backup follows the system. */
    @Test
    fun theLanguageSurvivesABackupAndABackupFromBeforeItFollowsTheSystem() {
        assertEquals("vi", roundTrip(everyFieldChanged).settings.appearance.language)
        val json = SettingsJson.encode(PlumSettings())
        assertTrue(json.getJSONObject("appearance").isNull("language"))
        assertEquals(null, SettingsJson.decode(json.toString()).settings.appearance.language)
        val older = SettingsJson.encode(everyFieldChanged)
        older.getJSONObject("appearance").remove("language")
        val decoded = SettingsJson.decode(older.toString())
        assertEquals(null, decoded.settings.appearance.language)
        assertEquals(emptyList<String>(), decoded.unknownKeys)
    }

    /** The buttons in use and the ones kept with the own colours go through a backup; an older backup reads undecided. */
    @Test
    fun theButtonSetSurvivesABackupAndABackupFromBeforeItReadsUndecided() {
        val restored = roundTrip(everyFieldChanged).settings.appearance
        assertEquals("Ivory", restored.buttonSet)
        assertEquals("Charcoal", restored.ownColors?.buttonSet)
        val json = SettingsJson.encode(everyFieldChanged)
        assertEquals("Ivory", json.getJSONObject("appearance").getString("buttonSet"))
        json.getJSONObject("appearance").remove("buttonSet")
        json.getJSONObject("appearance").getJSONObject("ownColors").remove("buttonSet")
        val old = SettingsJson.decode(json.toString())
        assertEquals(null, old.settings.appearance.buttonSet)
        assertEquals(null, old.settings.appearance.ownColors?.buttonSet)
        assertEquals(emptyList<String>(), old.unknownKeys)
    }

    @Test
    fun damagedOwnColorsReadAsNoneAndAreListed() {
        for (damaged in listOf<Any>("#2B54B5 Light Blue", JSONObject().put("background", "#2B54B5").put("textColor", "Light"), JSONObject().put("background", "#2B54B5").put("textColor", "Grey").put("accent", "Blue"), JSONObject().put("background", "#12").put("textColor", "Light").put("accent", "Blue"))) {
            val json = SettingsJson.encode(everyFieldChanged)
            json.getJSONObject("appearance").put("ownColors", damaged)
            val decoded = SettingsJson.decode(json.toString())
            assertEquals("$damaged", null, decoded.settings.appearance.ownColors)
            assertEquals("$damaged", listOf("appearance.ownColors"), decoded.unknownKeys)
            assertEquals("the rest of the group is kept", everyFieldChanged.appearance.copy(ownColors = null), decoded.settings.appearance)
        }
    }

    @Test
    fun theDailyCheckOffSurvivesTheFile() {
        val json = SettingsJson.encode(PlumSettings.Defaults.copy(updates = UpdateSettings(checkDaily = false)))
        assertEquals(false, json.getJSONObject("updates").getBoolean("checkDaily"))
        assertEquals(false, SettingsJson.decode(json.toString()).settings.updates.checkDaily)
    }

    @Test
    fun aMissingGroupReadsAsDefaults() {
        val json = SettingsJson.encode(everyFieldChanged)
        json.remove("homeScreen")
        assertEquals(HomeScreenSettings(), SettingsJson.decode(json.toString()).settings.homeScreen)
    }

    @Test(expected = BackupFormatException::class)
    fun textThatIsNotASettingsFileIsRefused() {
        SettingsJson.decode("[1, 2, 3]")
    }

    @Test(expected = BackupFormatException::class)
    fun anObjectWithoutAVersionIsRefused() {
        SettingsJson.decode("{\"appearance\": {}}")
    }

    @Test
    fun anExplicitSystemInfoValueIsRestoredAndAnUnsetOneStaysUnset() {
        for (value in listOf(true, false)) {
            val settings = PlumSettings.Defaults.copy(homeScreen = HomeScreenSettings(showSystemInfo = value))
            assertEquals("explicit $value", value, roundTrip(settings).settings.homeScreen.showSystemInfo)
        }
        assertEquals(null, roundTrip(PlumSettings.Defaults).settings.homeScreen.showSystemInfo)
    }
}
