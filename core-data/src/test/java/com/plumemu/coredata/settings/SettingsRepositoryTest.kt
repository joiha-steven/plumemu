package com.plumemu.coredata.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Plain JVM: DataStore's Preferences file needs no Android. */
class SettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val logLines = CopyOnWriteArrayList<String>()
    private val log = SettingsLog { logLines += it }
    private val scopes = mutableListOf<Job>()

    private val file: File by lazy { File(tmp.root, "settings.preferences_pb") }

    /** A repository on [file]; only one may be open on a file at a time, so [closeAll] first. */
    private fun open(): SettingsRepository {
        val job = Job()
        scopes += job
        return SettingsRepository.create({ file }, CoroutineScope(Dispatchers.IO + job), log)
    }

    private suspend fun closeAll() {
        scopes.forEach { it.cancelAndJoin() }
        scopes.clear()
    }

    @After
    fun tearDown() = runBlocking { closeAll() }

    @Test
    fun aFreshInstallReadsTheProductDefaults() = runBlocking {
        val s = open().settings.first()

        assertEquals(PlumSettings.Defaults, s)
        assertEquals(ColorSetting.Preset("Black"), s.appearance.background)
        assertEquals(TextColorSetting.Light, s.appearance.textColor)
        assertEquals(ColorSetting.Preset("Red"), s.appearance.accent)
        assertEquals(FontSetting.Sans, s.appearance.font)
        assertNull("text size is not picked until the person picks one", s.appearance.textSize)
        assertEquals("the grid at Medium", GridSizeSetting.Medium, s.appearance.gridSize)
        assertEquals("every system is a list until SELECT", emptySet<String>(), s.appearance.gridSystems)
        assertEquals("no own colours kept until a set is picked over them", null, s.appearance.ownColors)
        assertEquals("the right face button confirms (decision 0005)", ConfirmButtonSetting.Right, s.controls.confirmButton)
        assertEquals(true, s.controls.holdToScrollFaster)
        assertEquals(true, s.controls.showButtonHints)
        assertEquals("Plumemu's own keyboard", KeyboardSetting.Plumemu, s.controls.keyboard)
        assertEquals("push right to play is off by default", false, s.controls.pushRightToPlay)
        assertEquals(true, s.library.scanOnStartup)
        assertEquals(SortGamesSetting.Name, s.library.sortGames)
        assertEquals("box art falls back to the USA box", BoxArtRegionSetting.Usa, s.library.boxArtRegion)
        assertEquals("box art is not paused", false, s.library.boxArtPaused)
        assertEquals("systems A to Z", SystemOrderSetting.Name, s.library.systemOrder)
        assertEquals(emptyList<String>(), s.library.manualSystemOrder)
        assertEquals(emptySet<String>(), s.library.hiddenSystems)
        assertEquals(HomeScreenSettings(showApps = true, showClock = true, showBattery = true), s.homeScreen)
        assertNull("the second screen is not decided until the person decides", s.secondScreen.enabled)
        assertEquals("clock and battery while a game runs", InGameSetting.Clock, s.secondScreen.inGame)
        assertEquals(true, s.advanced.animations)
        assertEquals("sounds are Loud for new and existing installs (the keys are absent)", SoundLevel.Loud, s.advanced.sounds)
        assertEquals("vibration is on by default", true, s.advanced.vibration)
        assertEquals("statistics are off for a new install", false, s.advanced.usageStatistics)
        assertEquals(SetupFlags(setupCompleted = false, homeRoleAsked = false, rescanAfterReset = false), s.flags)
        assertTrue("no warning for a missing file: $logLines", logLines.isEmpty())
    }

    @Test
    fun everyFieldSurvivesAWriteAndAFreshRead() = runBlocking {
        val changed = PlumSettings(
            appearance = AppearanceSettings(
                background = ColorSetting.Custom(0x123456),
                textColor = TextColorSetting.Dark,
                accent = ColorSetting.Custom(0xABCDEF),
                font = FontSetting.Mono,
                textSize = TextSizeSetting.Largest,
                gridSize = GridSizeSetting.Small,
                gridSystems = setOf("snes", "psp"),
                ownColors = OwnColors(ColorSetting.Preset("Plum"), TextColorSetting.Light, ColorSetting.Custom(0xA79CF5), buttonSet = "Super"),
                buttonSet = "Ivory",
                language = "zh-TW",
            ),
            controls = ControlsSettings(ConfirmButtonSetting.Bottom, holdToScrollFaster = false, showButtonHints = false, keyboard = KeyboardSetting.Android, pushRightToPlay = true, switchSystemWith = SwitchSystemWithSetting.Bumpers),
            library = LibrarySettings(scanOnStartup = false, sortGames = SortGamesSetting.LastPlayed, boxArtRegion = BoxArtRegionSetting.Japan, boxArtPaused = true,
                systemOrder = SystemOrderSetting.Maker, manualSystemOrder = listOf("snes", "sg-1000", "gba"), hiddenSystems = setOf("psx", "tg-cd"), recentSize = RecentSizeSetting.Fifty,
            ),
            homeScreen = HomeScreenSettings(showApps = false, showClock = false, showBattery = false, showSystemInfo = false),
            secondScreen = SecondScreenSettings(enabled = true, inGame = InGameSetting.Black),
            advanced = AdvancedSettings(animations = false, sounds = SoundLevel.Quiet, usageStatistics = false, vibration = false),
            updates = UpdateSettings(checkDaily = false),
            flags = SetupFlags(
                setupCompleted = true, homeRoleAsked = true, rescanAfterReset = true, folderMatchVersion = 7, namingRulesVersion = 8, scrapeRulesVersion = 9,
                inGameDriftNoteShown = true,
            ),
        )
        // Every field differs from its default, so a field the codec forgot reads back wrong.
        open().update { changed }
        closeAll()

        assertEquals(changed, open().settings.first())

        val presets = changed.copy(
            appearance = changed.appearance.copy(background = ColorSetting.Preset("Paper"), accent = ColorSetting.Preset("Blue")),
        )
        closeAll()
        open().update { presets }
        closeAll()
        assertEquals(presets, open().settings.first())
    }

    @Test
    fun groupSettersAndFlagsChangeOnlyTheirPart() = runBlocking {
        val repo = open()
        repo.setControls(ControlsSettings(confirmButton = ConfirmButtonSetting.Bottom))
        repo.setHomeScreen(HomeScreenSettings(showApps = false))
        repo.setLibrary(LibrarySettings(scanOnStartup = false))
        repo.setAdvanced(AdvancedSettings(usageStatistics = false))
        repo.setAppearance(AppearanceSettings(font = FontSetting.Serif))
        repo.markSetupCompleted()
        repo.markHomeRoleAsked()

        val s = repo.settings.first()
        assertEquals(ConfirmButtonSetting.Bottom, s.controls.confirmButton)
        assertEquals(false, s.homeScreen.showApps)
        assertEquals(false, s.library.scanOnStartup)
        assertEquals(false, s.advanced.usageStatistics)
        assertEquals(FontSetting.Serif, s.appearance.font)
        assertEquals(SetupFlags(setupCompleted = true, homeRoleAsked = true), s.flags)
    }

    @Test
    fun twoChangesAtOnceAreBothKept() = runBlocking {
        val repo = open()
        val a = launch(Dispatchers.IO) { repeat(20) { repo.update { it.copy(controls = it.controls.copy(holdToScrollFaster = !it.controls.holdToScrollFaster)) } } }
        val b = launch(Dispatchers.IO) { repo.markSetupCompleted() }
        a.join()
        b.join()

        val s = repo.settings.first()
        assertEquals("20 flips end where they started", true, s.controls.holdToScrollFaster)
        assertEquals(true, s.flags.setupCompleted)
    }

    @Test
    fun aDamagedFileGivesDefaultsAndOneLogLine() = runBlocking {
        file.writeBytes(byteArrayOf(0x0A, 0x7F, 0x13, 0x00, 0x55, 0x01, 0x02))

        val repo = open()
        assertEquals(PlumSettings.Defaults, repo.settings.first())
        assertEquals(logLines.toString(), 1, logLines.count { "damaged" in it })
        assertTrue(logLines.toString(), logLines.all { "CorruptionException" in it })

        // The file was replaced: the next change is stored normally.
        repo.markSetupCompleted()
        closeAll()
        assertEquals(true, open().settings.first().flags.setupCompleted)
    }

    @Test
    fun textSizeKeepsTheNamesOlderInstallsStoredAndStoresNothingUntilPicked() = runBlocking {
        val job = Job()
        val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        for ((name, expected) in listOf("Small" to TextSizeSetting.Small, "Medium" to TextSizeSetting.Medium, "Large" to TextSizeSetting.Large)) {
            // With the marker (a save by this version) every name is a pick.
            raw.edit {
                it[SettingsCodec.Keys.textSize] = name
                it[SettingsCodec.Keys.textSizePickMarked] = true
            }
            val read = raw.data.first()
            assertEquals(expected, SettingsCodec(log).decode(read).appearance.textSize)
        }
        job.cancelAndJoin()

        // Picked, then written: the name is stored; not picked: the key is absent.
        val repo = open()
        repo.setAppearance(AppearanceSettings(textSize = TextSizeSetting.Huge))
        closeAll()
        assertEquals(TextSizeSetting.Huge, open().settings.first().appearance.textSize)
        closeAll()
        val again = open()
        again.setAppearance(AppearanceSettings(textSize = null))
        closeAll()
        assertNull(open().settings.first().appearance.textSize)
        assertEquals(listOf("Small", "Medium", "Large", "Larger", "Huge", "Largest"), TextSizeSetting.entries.map { it.name })
    }

    @Test
    fun aMediumSavedByAnOlderBuildIsNotAPickButSmallAndLargeAre() = runBlocking {
        suspend fun readStoredByOldBuild(name: String): TextSizeSetting? {
            val job = Job()
            val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
            raw.edit {
                it.clear()
                it[SettingsCodec.Keys.textSize] = name
            }
            job.cancelAndJoin()
            val read = open().settings.first().appearance.textSize
            closeAll()
            return read
        }
        assertNull("old default Medium reads as not picked", readStoredByOldBuild("Medium"))
        assertEquals(TextSizeSetting.Small, readStoredByOldBuild("Small"))
        assertEquals(TextSizeSetting.Large, readStoredByOldBuild("Large"))
    }

    @Test
    fun aMediumPickedAfterTheMigrationStaysMedium() = runBlocking {
        val job = Job()
        val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        raw.edit { it[SettingsCodec.Keys.textSize] = "Medium" }
        job.cancelAndJoin()
        val repo = open()
        assertNull(repo.settings.first().appearance.textSize)
        // Any save writes the marker; picking Medium then keeps it.
        repo.setAppearance(AppearanceSettings(textSize = TextSizeSetting.Medium))
        closeAll()
        assertEquals(TextSizeSetting.Medium, open().settings.first().appearance.textSize)
        closeAll()
        // And a save that picks nothing never turns the unset size into a pick.
        val again = open()
        again.setAppearance(AppearanceSettings(textSize = null))
        closeAll()
        assertNull(open().settings.first().appearance.textSize)
    }

    @Test
    fun aTextSizeNameThisVersionDoesNotKnowReadsAsNotPickedAndLogsTheKey() = runBlocking {
        val job = Job()
        val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        raw.edit { it[SettingsCodec.Keys.textSize] = "Gigantic" }
        job.cancelAndJoin()
        assertNull(open().settings.first().appearance.textSize)
        assertTrue(logLines.toString(), logLines.any { "appearance.text_size" in it })
        assertTrue("values are never logged: $logLines", logLines.none { "Gigantic" in it })
    }

    @Test
    fun aValueThisVersionDoesNotKnowReadsAsItsDefault() = runBlocking {
        val job = Job()
        val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        raw.edit {
            it[SettingsCodec.Keys.font] = "Pixel"
            it[SettingsCodec.Keys.background] = "#12345"
            it[SettingsCodec.Keys.holdToScrollFaster] = false
        }
        job.cancelAndJoin()

        val s = open().settings.first()
        assertEquals(FontSetting.Sans, s.appearance.font)
        assertEquals(ColorSetting.Preset("Black"), s.appearance.background)
        assertEquals("known values next to it still read", false, s.controls.holdToScrollFaster)
        assertTrue(logLines.toString(), logLines.any { "appearance.font" in it })
        assertTrue("values are never logged: $logLines", logLines.none { "Pixel" in it || "#12345" in it })
    }

    @Test
    fun aStoredHideUnidentifiedFilesKeyFromAnOlderBuildIsIgnored() = runBlocking {
        val job = Job()
        val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        raw.edit { it[booleanPreferencesKey("library.hide_unidentified_files")] = true; it[SettingsCodec.Keys.scanOnStartup] = false }
        job.cancelAndJoin()
        assertEquals("the other library values still load", false, open().settings.first().library.scanOnStartup)
        assertTrue("an old key is not logged as damaged: $logLines", logLines.isEmpty())
    }

    @Test
    fun theBoxArtRegionIsStoredAsScreenScraperCodeAndAnUnknownOneReadsAsUsa() = runBlocking {
        open().update { it.copy(library = it.library.copy(boxArtRegion = BoxArtRegionSetting.World)) }
        closeAll()
        val job = Job()
        val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        assertEquals("the file holds the code the scraper is given", "wor", raw.data.first()[SettingsCodec.Keys.boxArtRegion])
        raw.edit { it[SettingsCodec.Keys.boxArtRegion] = "mars" }
        job.cancelAndJoin()

        assertEquals(BoxArtRegionSetting.Usa, open().settings.first().library.boxArtRegion)
        assertTrue(logLines.toString(), logLines.any { "library.box_art_region" in it })
        assertTrue("values are never logged: $logLines", logLines.none { "mars" in it })
    }

    @Test
    fun theGridSizeIsMediumUntilPickedStoredByNameAndAnUnknownOneReadsAsMedium() = runBlocking {
        // An install from before the grid sizes: other appearance keys written, no grid size.
        var job = Job()
        var raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        raw.edit { it[SettingsCodec.Keys.font] = "Serif" }
        job.cancelAndJoin()
        val before = open().settings.first().appearance
        assertEquals(GridSizeSetting.Medium, before.gridSize)
        assertEquals("the person's other choices stay", FontSetting.Serif, before.font)
        assertTrue("a missing key is not an unknown value: $logLines", logLines.isEmpty())
        closeAll()

        open().update { it.copy(appearance = it.appearance.copy(gridSize = GridSizeSetting.Large)) }
        closeAll()
        job = Job()
        raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        assertEquals("the file holds the name", "Large", raw.data.first()[SettingsCodec.Keys.gridSize])
        raw.edit { it[SettingsCodec.Keys.gridSize] = "Huge" }
        job.cancelAndJoin()

        val damaged = open().settings.first().appearance
        assertEquals(GridSizeSetting.Medium, damaged.gridSize)
        assertEquals(FontSetting.Serif, damaged.font)
        assertTrue(logLines.toString(), logLines.any { "appearance.grid_size" in it })
        assertTrue("values are never logged: $logLines", logLines.none { "Huge" in it })
    }

    @Test
    fun aStoredGridSizeStaysWhenTheDefaultChangesAndAfterOtherSaves() = runBlocking {
        // Every save writes every field, so a stored Medium may be a pick or the old default: it
        // cannot be told apart and is kept (only the default becomes Medium, so a stored Small stays Small).
        for (stored in listOf("Small", "Medium", "Large")) {
            val job = Job()
            val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
            raw.edit { it[SettingsCodec.Keys.gridSize] = stored }
            job.cancelAndJoin()
            assertEquals(stored, open().settings.first().appearance.gridSize.name)
            closeAll()
            open().update { it.copy(appearance = it.appearance.copy(font = FontSetting.Mono)) }
            closeAll()
            assertEquals("another save keeps $stored", stored, open().settings.first().appearance.gridSize.name)
            closeAll()
        }
    }
    /** An older 3-part own_colours value reads with no kept buttons. */
    @Test
    fun anOldThreePartOwnColoursValueReadsWithNoButtonSet() = runBlocking {
        val job = Job()
        val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        raw.edit { it[SettingsCodec.Keys.ownColors] = "#123456 Light Amber" }
        job.cancelAndJoin()
        val own = open().settings.first().appearance.ownColors
        assertEquals(OwnColors(ColorSetting.Custom(0x123456), TextColorSetting.Light, ColorSetting.Preset("Amber")), own)
        assertEquals(null, own?.buttonSet)
        closeAll()
    }

    /** The set whose buttons are in use is one key holding its name; absent means not decided yet. */
    @Test
    fun theButtonSetSurvivesARestartAndAFileFromBeforeItReadsUndecided() = runBlocking {
        assertEquals("a fresh file is undecided", null, open().settings.first().appearance.buttonSet)
        closeAll()
        open().update { it.copy(appearance = it.appearance.copy(buttonSet = "Ivory", ownColors = OwnColors(ColorSetting.Preset("Plum"), TextColorSetting.Light, ColorSetting.Preset("Amber"), "None"))) }
        closeAll()
        val job = Job()
        val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        assertEquals("Ivory", raw.data.first()[SettingsCodec.Keys.buttonSet])
        assertEquals("#123456 Light Amber".replace("#123456", "Plum") + " None", raw.data.first()[SettingsCodec.Keys.ownColors])
        job.cancelAndJoin()
        val back = open().settings.first().appearance
        assertEquals("Ivory", back.buttonSet)
        assertEquals("None", back.ownColors?.buttonSet)
        closeAll()
    }

    /** The person's own colours outlive the process; a file from before them reads none, a damaged value none and logged. */
    @Test
    fun ownColorsSurviveARestartAndAFileFromBeforeThemReadsNone() = runBlocking {
        // An older install: other keys written, no own colours.
        val job = Job()
        val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        raw.edit { it[SettingsCodec.Keys.background] = "#123456" }
        job.cancelAndJoin()
        val old = open().settings.first().appearance
        assertEquals("the person's colours stay", ColorSetting.Custom(0x123456), old.background)
        assertEquals("none kept", null, old.ownColors)
        assertTrue("a missing key is not an unknown value: $logLines", logLines.isEmpty())
        closeAll()

        val own = OwnColors(ColorSetting.Custom(0x123456), TextColorSetting.Light, ColorSetting.Preset("Amber"))
        open().update { it.copy(appearance = it.appearance.copy(ownColors = own)) }
        closeAll()
        val restarted = open()
        assertEquals("a new repository on the same file reads them back", own, restarted.settings.first().appearance.ownColors)
        // Another setting written later leaves them alone.
        restarted.update { it.copy(appearance = it.appearance.copy(gridSize = GridSizeSetting.Medium)) }
        closeAll()
        val rawJob = Job()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + rawJob), produceFile = { file })
        assertEquals("one value under appearance.own_colors", "#123456 Light Amber", store.data.first()[SettingsCodec.Keys.ownColors])
        // Damaged: read as none, the key logged (never the value).
        store.edit { it[SettingsCodec.Keys.ownColors] = "#123456 Light" }
        rawJob.cancelAndJoin()
        assertEquals(null, open().settings.first().appearance.ownColors)
        assertTrue("$logLines", logLines.isNotEmpty() && logLines.all { it.contains("appearance.own_colors") })
        assertFalse("the value is not logged", logLines.any { it.contains("#123456") })
        closeAll()

        // Cleared (Reset all settings) removes the key.
        open().update { it.copy(appearance = it.appearance.copy(ownColors = null)) }
        closeAll()
        val clearJob = Job()
        val cleared = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + clearJob), produceFile = { file }).data.first()
        clearJob.cancelAndJoin()
        assertEquals(null, cleared[SettingsCodec.Keys.ownColors])
    }

    @Test
    fun aFileFromBeforeTheSystemOrderReadsAToZWithNothingHidden() = runBlocking {
        // An older install: other keys written, none of the three new ones.
        val job = Job()
        val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        raw.edit {
            it[SettingsCodec.Keys.sortGames] = "LastPlayed"
            it[SettingsCodec.Keys.scanOnStartup] = false
        }
        job.cancelAndJoin()

        val library = open().settings.first().library
        assertEquals("the person's other choices stay", SortGamesSetting.LastPlayed, library.sortGames)
        assertEquals(false, library.scanOnStartup)
        assertEquals(SystemOrderSetting.Name, library.systemOrder)
        assertEquals(emptyList<String>(), library.manualSystemOrder)
        assertEquals(emptySet<String>(), library.hiddenSystems)
        assertTrue("missing keys are not unknown values: $logLines", logLines.isEmpty())
    }

    @Test
    fun theSystemOrderIsStoredInLowerCaseAndTheManualOrderKeepsItsSequence() = runBlocking {
        val manual = listOf("psx", "snes", "gba", "sg-1000", "megadrive")
        open().update { it.copy(library = it.library.copy(systemOrder = SystemOrderSetting.Manual, manualSystemOrder = manual, hiddenSystems = setOf("gba"))) }
        closeAll()
        val job = Job()
        val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        val stored = raw.data.first()
        assertEquals("manual", stored[SettingsCodec.Keys.systemOrder])
        assertEquals(setOf("gba"), stored[SettingsCodec.Keys.hiddenSystems])
        job.cancelAndJoin()

        val library = open().settings.first().library
        assertEquals("the order is kept, not just the set", manual, library.manualSystemOrder)
        closeAll()
        // Switching away keeps the person's arrangement for when they come back to Manual.
        open().update { it.copy(library = it.library.copy(systemOrder = SystemOrderSetting.Name)) }
        closeAll()
        val back = open().settings.first().library
        assertEquals(SystemOrderSetting.Name, back.systemOrder)
        assertEquals(manual, back.manualSystemOrder)
    }

    @Test
    fun anUnknownSystemOrderReadsAToZAndADamagedManualListDropsBlanksAndRepeats() = runBlocking {
        open().update { it }
        closeAll()
        val job = Job()
        val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        raw.edit {
            it[SettingsCodec.Keys.systemOrder] = "by-colour"
            it[SettingsCodec.Keys.manualSystemOrder] = "snes\n\n gba \nsnes\npsx"
        }
        job.cancelAndJoin()

        val library = open().settings.first().library
        assertEquals(SystemOrderSetting.Name, library.systemOrder)
        assertEquals("one place per system", listOf("snes", "gba", "psx"), library.manualSystemOrder)
        assertTrue(logLines.toString(), logLines.any { "library.system_order" in it })
        assertTrue("values are never logged: $logLines", logLines.none { "by-colour" in it })
    }

    @Test
    fun theSecondScreenSwitchStaysUndecidedUntilWrittenAndKeepsEachAnswer() = runBlocking {
        // Another save leaves the key absent: the kind of screen still decides.
        open().setHomeScreen(HomeScreenSettings(showApps = false))
        closeAll()
        assertNull(open().settings.first().secondScreen.enabled)
        closeAll()
        val job = Job()
        val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        assertTrue("no key written for an undecided switch", SettingsCodec.Keys.secondScreenEnabled !in raw.data.first().asMap())
        assertEquals("clock", raw.data.first()[SettingsCodec.Keys.secondScreenInGame])
        job.cancelAndJoin()

        for (answer in listOf(false, true)) {
            open().setSecondScreen(SecondScreenSettings(enabled = answer))
            closeAll()
            assertEquals(answer, open().settings.first().secondScreen.enabled)
            closeAll()
        }
        open().setSecondScreen(SecondScreenSettings(enabled = false, inGame = InGameSetting.Black))
        closeAll()
        val readJob = Job()
        val readBack = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + readJob), produceFile = { file }).data.first()
        readJob.cancelAndJoin()
        assertEquals("black", readBack[SettingsCodec.Keys.secondScreenInGame])
        assertEquals(false, readBack[SettingsCodec.Keys.secondScreenEnabled])
    }

    @Test
    fun aDecidedSwitchWrittenBackAsUndecidedRemovesItsKey() = runBlocking {
        // What Reset all settings does: enabled back to null, in game back to clock.
        open().setSecondScreen(SecondScreenSettings(enabled = true, inGame = InGameSetting.Black))
        closeAll()
        assertEquals(true, open().settings.first().secondScreen.enabled)
        closeAll()
        open().setSecondScreen(SecondScreenSettings())
        closeAll()
        assertEquals(SecondScreenSettings(), open().settings.first().secondScreen)
        closeAll()
        val job = Job()
        val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }).data.first()
        job.cancelAndJoin()
        assertTrue("the key is removed, not stored as false", SettingsCodec.Keys.secondScreenEnabled !in raw.asMap())
        assertEquals("clock", raw[SettingsCodec.Keys.secondScreenInGame])
    }

    @Test
    fun anInGameValueThisVersionDoesNotKnowReadsAsClockAndLogsTheKey() = runBlocking {
        val job = Job()
        val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        raw.edit {
            it[SettingsCodec.Keys.secondScreenInGame] = "aquarium"
            it[SettingsCodec.Keys.secondScreenEnabled] = false
        }
        job.cancelAndJoin()

        val s = open().settings.first().secondScreen
        assertEquals(InGameSetting.Clock, s.inGame)
        assertEquals("the switch next to it still reads", false, s.enabled)
        assertTrue(logLines.toString(), logLines.any { "second_screen.in_game" in it })
        assertTrue("values are never logged: $logLines", logLines.none { "aquarium" in it })
    }

    @Test
    fun aReadErrorGivesDefaultsAndLaterChangesStillApply() = runTest {
        val backing = MutableStateFlow(emptyPreferences())
        var failuresLeft = 2
        val store = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow {
                if (failuresLeft > 0) {
                    failuresLeft--
                    throw IOException("storage busy")
                }
                emitAll(backing)
            }

            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                transform(backing.value).also { backing.value = it }
        }
        val repo = SettingsRepository(store, log)
        val seen = mutableListOf<PlumSettings>()
        val collecting = launch { repo.settings.collect { seen += it } }

        runCurrent()
        assertEquals("defaults while the file cannot be read", listOf(PlumSettings.Defaults), seen)
        advanceTimeBy(readRetryDelayMs(0) + readRetryDelayMs(1) + 1)
        runCurrent()
        repo.markSetupCompleted()
        runCurrent()

        assertEquals("a change after the storage came back reaches the collector", true, seen.last().flags.setupCompleted)
        assertEquals(logLines.toString(), 2, logLines.count { "could not be read (IOException" in it })
        collecting.cancel()
    }

    @Test
    fun readRetriesSlowDownAndStopGrowing() {
        assertEquals(listOf(1_000L, 2_000L, 4_000L), (0L..2L).map { readRetryDelayMs(it) })
        assertEquals(readRetryDelayMs(8), readRetryDelayMs(1_000))
    }

    @Test
    fun colourCodes() {
        assertEquals("#0A0B0C", SettingsCodec.encodeColor(ColorSetting.Custom(0x0A0B0C)))
        assertEquals("#FFFFFF", SettingsCodec.encodeColor(ColorSetting.Custom(-1)))
        assertEquals(ColorSetting.Custom(0x0A0B0C), SettingsCodec.decodeColor("#0a0b0c"))
        assertEquals(ColorSetting.Preset("Midnight"), SettingsCodec.decodeColor("Midnight"))
        for (bad in listOf("", "#", "#12345", "#1234567", "#-12345", "#GGGGGG", "Red Wine", "  ")) {
            assertNull(bad, SettingsCodec.decodeColor(bad))
        }
    }
}
