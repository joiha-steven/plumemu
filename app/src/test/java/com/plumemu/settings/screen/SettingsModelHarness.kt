package com.plumemu.settings.screen

import com.plumemu.account.FakeScraperAccounts
import com.plumemu.account.ScraperAccounts
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.KeyboardSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.coreui.theme.CustomPalette
import org.junit.Assert.assertEquals

/** A [SettingsModel] over fake, late-landing writes, and the presses its tests share. */
internal abstract class SettingsModelHarness {
    /**
     * Writes land later, like DataStore's: the model keeps seeing [shown] until something reads
     * [settings] (the store), which applies the writes made so far in order.
     */
    protected class FakeActions : SettingsActions {
        // Starts on Graphite, the first preset: the colour-page tests walk the presets from index 0
        // (the app default is Black, which is index 1).
        private var stored =
            PlumSettings(
                appearance = AppearanceSettings(background = ColorSetting.Preset("Graphite")),
                flags = SetupFlags(setupCompleted = true),
            )
        private val pending = ArrayDeque<(PlumSettings) -> PlumSettings>()
        var shown = stored
            private set
        var closed = 0
        var resetsAll = 0
        var resetsLibrary = 0
        var writes = 0

        /** What a finger on the colour sliders shows before it is written. */
        override val colorPreview = ColorPreview()

        /** The ScreenScraper account the Library rows show and sign in to. */
        var accountsFake = FakeScraperAccounts()
        override val accounts: ScraperAccounts get() = accountsFake

        var settings: PlumSettings
            get() {
                while (pending.isNotEmpty()) stored = pending.removeFirst()(stored)
                shown = stored
                return stored
            }
            set(value) {
                pending.clear()
                stored = value
                shown = value
            }

        override fun update(transform: (PlumSettings) -> PlumSettings) {
            writes++
            pending.addLast(transform)
        }

        override fun resetAllSettings() {
            resetsAll++
            settings = settings.resetKeepingFlags()
        }

        override fun resetLibrary() {
            resetsLibrary++
        }

        override fun close() {
            closed++
        }

        var hasPicker = true
        var picks = 0
        var scanBusy = false
        var rescans = 0
        var canOpenHome = true
        var homeOpened = 0
        val removed = mutableListOf<Long>()
        val chosen = mutableListOf<Pair<String, String?>>()
        var detections = 0

        /** Callbacks held back until a test answers them, as the graph does later on the main thread. */
        val callbacks = mutableListOf<(Boolean) -> Unit>()

        override fun openFolderPicker(): Boolean {
            picks++
            return hasPicker
        }

        override fun removeFolder(
            folderId: Long,
            onDone: (Boolean) -> Unit,
        ) {
            removed += folderId
            callbacks += onDone
        }

        override fun rescanLibrary(): Boolean {
            rescans++
            return !scanBusy
        }

        var scanResultsForgotten = 0

        override fun forgetScanResult() {
            scanResultsForgotten++
        }

        override fun chooseEmulator(
            systemId: String,
            emulatorId: String?,
            onDone: (Boolean) -> Unit,
        ) {
            chosen += systemId to emulatorId
            callbacks += onDone
        }

        var folderAnswersForgotten = 0

        /** Library › an unknown folder: each answer asked for (picked folder, name, system or null for Ignore), held back until a test answers. */
        val folderAnswers = mutableListOf<Triple<Long, String, String?>>()

        override fun chooseFolderSystem(
            folderId: Long,
            relativePath: String,
            systemId: String?,
            onDone: (Boolean) -> Unit,
        ) {
            folderAnswers += Triple(folderId, relativePath, systemId)
            callbacks += onDone
        }

        /** ROM folders › a folder: each skip asked for (picked folder, path, skip or read again), held back until a test answers. */
        val skips = mutableListOf<Triple<Long, String, Boolean>>()

        override fun skipSubfolder(
            folderId: Long,
            relativePath: String,
            skip: Boolean,
            onDone: (Boolean) -> Unit,
        ) {
            skips += Triple(folderId, relativePath, skip)
            callbacks += onDone
        }

        /** Skip a sub-folder…'s listings asked for, answered by [answerListing]. */
        val listings = mutableListOf<Pair<Long, (List<String>?) -> Unit>>()

        override fun listSubfolders(
            folderId: Long,
            onDone: (List<String>?) -> Unit,
        ) {
            listings += folderId to onDone
        }

        /** Answers the listings asked for with [names] (null: the folder could not be read). */
        fun answerListing(names: List<String>?) {
            val waiting = listings.toList()
            listings.clear()
            waiting.forEach { it.second(names) }
        }

        override fun detectEmulators(onDone: (Boolean) -> Unit) {
            detections++
            callbacks += onDone
        }

        override fun openHomeSettings(): Boolean {
            homeOpened++
            return canOpenHome
        }

        /** About › Report a problem: each report asked for, and the answers held back until a test gives them. */
        val reports = mutableListOf<Triple<String, String?, Boolean>>()
        val reportAnswers = mutableListOf<(com.plumemu.telemetry.ReportResult) -> Unit>()

        override val problemReports: com.plumemu.report.ProblemReportActions =
            object : com.plumemu.report.ProblemReportActions {
                override fun submit(
                    text: String,
                    email: String?,
                    includeDetails: Boolean,
                    onDone: (com.plumemu.telemetry.ReportResult) -> Unit,
                ) {
                    reports += Triple(text, email, includeDetails)
                    reportAnswers += onDone
                }
            }

        /** Advanced › Export logs: each ask, answered with [canShareLogs]. */
        var logExports = 0
        var canShareLogs = true

        override fun exportLogs(onDone: (Boolean) -> Unit) {
            logExports++
            onDone(canShareLogs)
        }

        override fun forgetFolderAnswer() {
            folderAnswersForgotten++
        }

        val unhidden = mutableListOf<Long>()

        override fun unhideGame(
            gameId: Long,
            onDone: (Boolean) -> Unit,
        ) {
            unhidden += gameId
            callbacks += onDone
        }

        /** Unidentified games and Fix name…; a test sets its own fake. */
        var nameActions: SettingsNameActions = SettingsNameActions.None

        override val names: SettingsNameActions get() = nameActions

        /** Clear Recent…: calls made, and how many games it says left (null: the write failed). */
        var recentClears = 0
        var recentCleared: Int? = 3

        override val recent: SettingsRecentActions =
            object : SettingsRecentActions {
                override fun clearRecent(onDone: (Int?) -> Unit) {
                    recentClears++
                    onDone(recentCleared)
                }
            }

        /** Box art's ask again, pause and resume: calls recorded, answered with [boxArtWorks]. */
        val boxArtCalls = mutableListOf<String>()
        var boxArtWorks = true

        /** Clear box art's answer when it is not simply [boxArtWorks]. */
        var clearResult: com.plumemu.graph.BoxArtClearResult? = null

        override val boxArt: SettingsBoxArtActions =
            object : SettingsBoxArtActions {
                override fun askAgain(
                    which: com.plumemu.library.scrape.AskAgain,
                    systemIds: Set<String>?,
                    onDone: (Boolean) -> Unit,
                ) {
                    boxArtCalls += "${which.name} ${systemIds?.sorted() ?: "all"}"
                    onDone(boxArtWorks)
                }

                override fun setPaused(
                    isPaused: Boolean,
                    onDone: (Boolean) -> Unit,
                ) {
                    boxArtCalls += if (isPaused) "pause" else "resume"
                    onDone(boxArtWorks)
                }

                override fun clearBoxArt(onDone: (com.plumemu.graph.BoxArtClearResult) -> Unit) {
                    boxArtCalls += "clear"
                    onDone(clearResult ?: if (boxArtWorks) com.plumemu.graph.BoxArtClearResult.Done else com.plumemu.graph.BoxArtClearResult.Failed)
                }
            }

        /** Advanced › Back up…: the kinds the save picker was opened for, Cancel and forget presses. */
        val backupPicks = mutableListOf<com.plumemu.coredata.backup.BackupKind>()
        var hasSavePicker = true
        var backupCancels = 0
        var backupForgets = 0

        override val backup: SettingsBackupActions =
            object : SettingsBackupActions {
                override fun pick(kind: com.plumemu.coredata.backup.BackupKind): Boolean {
                    backupPicks += kind
                    return hasSavePicker
                }

                override fun cancel() {
                    backupCancels++
                }

                override fun forgetResult() {
                    backupForgets++
                }
            }

        /** Advanced › Restore… and Undo last restore: what Settings asked of the graph, in order. */
        val restoreCalls = mutableListOf<String>()
        var hasOpenPicker = true

        override val restore: SettingsRestoreActions =
            object : SettingsRestoreActions {
                override fun pick(): Boolean {
                    restoreCalls += "pick"
                    return hasOpenPicker
                }

                override fun cancel() {
                    restoreCalls += "cancel"
                }

                override fun confirm() {
                    restoreCalls += "confirm"
                }

                override fun undo() {
                    restoreCalls += "undo"
                }

                override fun forgetCopy() {
                    restoreCalls += "forget copy"
                }

                override fun forgetResult() {
                    restoreCalls += "forget result"
                }
            }

        /** Answers every held callback with [result]. */
        fun answer(result: Boolean) {
            val waiting = callbacks.toList()
            callbacks.clear()
            waiting.forEach { it(result) }
        }
    }

    protected val actions = FakeActions()
    protected val model = SettingsModel(actions)
    protected val wide = SettingsGeometry(isNarrow = false, presetColumns = 5, defaultTextSize = TextSizeSetting.Medium)
    protected val narrow = SettingsGeometry(isNarrow = true, presetColumns = 10, defaultTextSize = TextSizeSetting.Medium)

    protected fun press(
        action: Action,
        geometry: SettingsGeometry = wide,
    ) = model.onAction(action, actions.shown, geometry)

    /** Walks the presets page to its "+" (the last item), bounded: Right stops at the end of a row, Down goes on. */
    protected fun walkToPlus() {
        val last = ColorPages.presetCount(model.focus.page!!, ColorPages.backgroundOf(actions.shown)) - 1
        repeat(30) {
            if (model.focus.panelIndex == last) return
            val before = model.focus.panelIndex
            move(Direction.Right)
            if (model.focus.panelIndex == before) move(Direction.Down)
        }
        assertEquals("reached +", last, model.focus.panelIndex)
    }

    /** Taps "+" of the open presets page (a first tap puts the cursor on it, a second opens the palette) without previewing the presets on the way. */
    protected fun openPlus() {
        val plus = ColorPages.presetCount(model.focus.page!!, ColorPages.backgroundOf(actions.shown)) - 1
        repeat(2) { model.onTapPanelItem(plus, actions.shown, wide) }
    }

    /** On a custom colour page the cursor lands on a swatch; the hex code is the last row, below the grid and the three sliders. */
    protected fun toHexRow() {
        repeat(CustomPalette.ROWS + ColorSlider.entries.size + 1) { move(Direction.Down) }
    }

    /** From the hex row back up through the three sliders to the last row of swatches (its first one). */
    protected fun upToGrid() {
        repeat(ColorSlider.entries.size + 1) { move(Direction.Up) }
    }

    protected fun move(
        direction: Direction,
        geometry: SettingsGeometry = wide,
    ) = press(Action.Move(direction), geometry)

    protected fun openSection(section: SettingsSection) {
        // Bounded: a move that does nothing (a layout change) fails here instead of looping forever.
        repeat(SettingsSection.entries.size) { if (model.focus.section.ordinal < section.ordinal) move(Direction.Down) }
        assertEquals("the sections column did not reach $section", section, model.focus.section)
        press(Action.Enter)
        assertEquals(SettingsColumn.Options, model.focus.column)
    }

    protected fun focusOption(option: SettingsOption) {
        // Downward only, bounded by the rows on screen: an option above the focus, or a focus that
        // is not in the options column, fails with the reason instead of looping forever.
        assertEquals("focusOption($option) needs the focus in the options column", SettingsColumn.Options, model.focus.column)
        repeat(model.focus.section.rowsIn(model.content).size) { if (model.focusedOption != option) move(Direction.Down) }
        assertEquals("focusOption($option): not reached moving down from the focus", option, model.focusedOption)
    }

    protected val appearance: AppearanceSettings get() = actions.settings.appearance

    /**
     * Appearance › Colors' panel with the D-pad: A on the row opens it, the cursor walks to
     * [entry]'s tile (null: Custom) and A uses it. Bounded by the tile count: a move that does
     * nothing fails here instead of looping.
     */
    protected fun pickInColorsPanel(
        entry: ColorSet?,
        geometry: SettingsGeometry = wide,
    ) {
        assertEquals("pickInColorsPanel needs the Colors row focused", SettingsOption.Colors, model.focusedOption)
        press(Action.Enter, geometry)
        assertEquals("A on Colors opens its panel", PanelPage.ColorSets, model.focus.page)
        val entries = colorSetsPanelEntries(actions.shown)
        val target = entries.indexOf(entry)
        check(target >= 0) { "$entry has no tile in $entries" }
        val columns = geometry.colorSetColumns
        repeat(entries.size) {
            val at = model.focus.panelIndex
            when {
                at / columns > target / columns -> move(Direction.Up, geometry)
                at / columns < target / columns -> move(Direction.Down, geometry)
                at % columns > target % columns -> move(Direction.Left, geometry)
                at % columns < target % columns -> move(Direction.Right, geometry)
            }
        }
        assertEquals("the cursor did not reach $entry's tile", target, model.focus.panelIndex)
        press(Action.Enter, geometry)
    }

    /**
     * Settings › Controls › Keyboard on Android: the hex code and Fix name… type with Android's
     * keyboard, the path every Settings field takes without Plumemu's keyboard (Plumemu's is the default).
     */
    protected fun useAndroidKeyboard() {
        actions.settings = actions.settings.let { it.copy(controls = it.controls.copy(keyboard = KeyboardSetting.Android)) }
    }
}
