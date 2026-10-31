package com.plumemu.settings.screen

import com.plumemu.language.AppLanguages
import androidx.compose.runtime.Immutable
import com.plumemu.account.ScraperAccounts
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coreinput.Direction
import com.plumemu.report.ProblemReportActions

/** What Settings asks of the app. Writes apply at once: the theme and the router follow the store. */
internal interface SettingsActions {
    /** One read-change-write of the store (two quick changes never lose each other). */
    fun update(transform: (PlumSettings) -> PlumSettings)

    /** Advanced › Reset all settings, after its confirm step. */
    fun resetAllSettings()

    /** Advanced › Reset library, after its confirm step: forget games and person data, keep folders, rescan. */
    fun resetLibrary()

    /** Back from the sections column: the main screen, with its focus where it was. */
    fun close()

    /** Library › ROM folders › + Add folder: Android's folder picker; false when the device has none. */
    fun openFolderPicker(): Boolean

    /** Remove, after its confirm step: that folder's games and grant go, the others stay. [onDone] gets whether it worked. */
    fun removeFolder(
        folderId: Long,
        onDone: (Boolean) -> Unit,
    )

    /** Library › Rescan library: false when a scan already runs (nothing new is started). */
    fun rescanLibrary(): Boolean

    /** The focus left Rescan library (or Settings closed): its last result is forgotten. */
    fun forgetScanResult()

    /** Emulators › a system: [emulatorId] for all its games, or null for Automatic. */
    fun chooseEmulator(
        systemId: String,
        emulatorId: String?,
        onDone: (Boolean) -> Unit,
    )

    /**
     * Library › an unknown folder: stores the person's system for [relativePath] of picked folder [folderId] (null:
     * Ignore this folder) and asks for the scan that applies it. Runs in the process's scope, waiting for a running
     * scan; [onDone] gets whether the answer was stored.
     */
    fun chooseFolderSystem(
        folderId: Long,
        relativePath: String,
        systemId: String?,
        onDone: (Boolean) -> Unit,
    ) = onDone(false)

    /**
     * ROM folders › a folder: skips the sub-folder [relativePath] of [folderId] ([skip]) or reads it again, then asks
     * for the scan that applies it. Runs in the process's scope; [onDone] gets whether it was stored.
     */
    fun skipSubfolder(
        folderId: Long,
        relativePath: String,
        skip: Boolean,
        onDone: (Boolean) -> Unit,
    ) = onDone(false)

    /** ROM folders › a folder › Skip a sub-folder…: its first-level sub-folders read from disk, A to Z; null when unreadable. */
    fun listSubfolders(
        folderId: Long,
        onDone: (List<String>?) -> Unit,
    ) = onDone(null)

    /** Emulators › Detect new emulators: asks Android again and refreshes the defaults. */
    fun detectEmulators(onDone: (Boolean) -> Unit)

    /**
     * Home screen › Use as home screen: Android's home-app question, or its home settings when
     * Plumemu already is the home app (to switch back). False when this device offers neither.
     */
    fun openHomeSettings(): Boolean

    /** Advanced › Export logs: Android's share sheet with the log file; [onDone] gets false when it could not open. */
    fun exportLogs(onDone: (Boolean) -> Unit) = onDone(false)

    /** The focus left Library › ROM folders (or Settings closed): the picker's last answer goes. */
    fun forgetFolderAnswer()

    /** Library › Hidden games › a game: back in its lists. [onDone] gets whether it was stored. */
    fun unhideGame(
        gameId: Long,
        onDone: (Boolean) -> Unit,
    )

    /** Library › Unidentified games and its Fix name…. */
    val names: SettingsNameActions get() = SettingsNameActions.None

    /** The colours a finger shows on the colour sliders before they are written (see [ColorPreview]). */
    val colorPreview: ColorPreview get() = ColorPreview.None

    /** Library's ScreenScraper account: sign in and out. */
    val accounts: ScraperAccounts get() = ScraperAccounts.Unavailable

    /** About › Report a problem: queues the report and tries to send it. */
    val problemReports: ProblemReportActions get() = ProblemReportActions.None

    /** Library › Clear Recent…. */
    val recent: SettingsRecentActions get() = SettingsRecentActions.None

    /** Library's box art: ask again, pause and resume. */
    val boxArt: SettingsBoxArtActions get() = SettingsBoxArtActions.None

    /** Statistics' figures, read once per opening ([rememberStatistics]). */
    val statistics: SettingsStatisticsSource get() = SettingsStatisticsSource.None

    /** Advanced › Back up…: the save picker, Cancel, forgetting the last result. */
    val backup: SettingsBackupActions get() = SettingsBackupActions.None

    /** Advanced › Restore… and Undo last restore: the open picker, Cancel, Restore, Undo, Forget the copy. */
    val restore: SettingsRestoreActions get() = SettingsRestoreActions.None

    /** About's updates: Check for updates, Update and its steps. */
    val updates: SettingsUpdateActions get() = SettingsUpdateActions.None
}

internal enum class SettingsColumn { Sections, Options, Panel }

/**
 * Where the cursor is. [rememberedOption] keeps each section's option, so L1 / R1 and coming back
 * land where the person was. [page] and [panelIndex] say what is focused in the panel while
 * [column] is [SettingsColumn.Panel].
 */
@Immutable
internal data class SettingsFocus(
    val column: SettingsColumn = SettingsColumn.Sections,
    val section: SettingsSection = SettingsSection.entries.first(),
    val rememberedOption: Map<SettingsSection, Int> = emptyMap(),
    val page: PanelPage? = null,
    val panelIndex: Int = 0,
    /** The licence whose text is open (or was last open), to go back to it in the list. */
    val licenseIndex: Int = 0,
    /** The folder a Remove confirm is about, by id: the list may change while it is open. */
    val folderId: Long? = null,
    /** The system whose emulators the panel lists, by id: a scan may add systems before it. */
    val systemId: String? = null,
    /** The unknown folder the panel answers, by id: the list reorders as folders get answered. */
    val folderKey: FolderKey? = null,
    /** Library › ScreenScraper › Choose systems…: the systems checked, by id (none when it opens). */
    val chosenSystems: Set<String> = emptySet(),
    /** What the Re-download confirm is about: these systems, or null for every game. */
    val redownloadSystems: Set<String>? = null,
    /** Undo restore's panel: the button whose own confirm is shown (Undo or Forget), or null for the first step. */
    val undoAsk: UndoPanelItem? = null,
) {
    /** The remembered row of the section, kept inside its rows when the rows change. */
    fun optionIndexIn(content: SettingsContent): Int = (rememberedOption[section] ?: 0).coerceIn(0, (section.rowsIn(content).size - 1).coerceAtLeast(0))

    /** The focused (or, in the sections column, the remembered) option of the section; null when it has none. */
    fun optionIn(content: SettingsContent): SettingsOption? = section.rowsIn(content).getOrNull(optionIndexIn(content))
}

/** Colors' preview tiles per row in the wide panel at Medium (the model's default; [settingsGeometry] measures). */
internal const val COLOR_SET_COLUMNS_WIDE = 3

/** What the model needs of the screen's size: narrow screens make every panel a further stop. */
@Immutable
internal data class SettingsGeometry(
    val isNarrow: Boolean,
    /** Preset swatches per row in the panel (5 in the wide panel, 10 in the narrow stop). */
    val presetColumns: Int,
    /** The text size in use while the person has not picked one (it follows the screen width), so stepping starts from it. */
    val defaultTextSize: TextSizeSetting,
    /** Colors' preview tiles per row ([colorSetColumns]): 3 in the wide panel, 5 in the narrow stop. */
    val colorSetColumns: Int = COLOR_SET_COLUMNS_WIDE,
)

/**
 * The focusable items of a panel page as a grid: [count] items, [columns] per row. On a custom
 * page the last item is the hex row under the palette, reached by down from the last row.
 */
internal data class PanelGrid(
    val count: Int,
    val columns: Int,
) {
    fun move(
        index: Int,
        direction: Direction,
    ): Int =
        when (direction) {
            Direction.Left -> if (index % columns > 0) index - 1 else index
            Direction.Right -> if (index % columns < columns - 1 && index + 1 < count) index + 1 else index
            Direction.Up -> if (index >= columns) index - columns else index
            // Into a shorter last row (or the hex row): its last item.
            Direction.Down -> if (index + columns < count) index + columns else if (index / columns < (count - 1) / columns) count - 1 else index
        }

    companion object {
        fun of(
            page: PanelPage,
            geometry: SettingsGeometry,
            content: SettingsContent,
            focus: SettingsFocus,
            settings: PlumSettings,
            paletteSize: Int = ColorPages.palette(page, ColorPages.backgroundOf(settings)).size,
            /** The sub-folders "Skip a sub-folder…" offers ([pickableSubfolders]); the model's, since the listing is its work's. */
            skipPickCount: Int = 0,
        ): PanelGrid? =
            when (page) {
                PanelPage.ColorSets -> PanelGrid(colorSetsPanelEntries(settings).size, geometry.colorSetColumns)
                PanelPage.Language -> PanelGrid(AppLanguages.all.size + 1, 1)
                PanelPage.Folders -> PanelGrid(content.folders.size + 1, 1)
                PanelPage.HiddenGames -> PanelGrid(content.hiddenGames.size, 1)
                PanelPage.UnidentifiedGames -> PanelGrid(content.unidentifiedGames.size, 1)
                PanelPage.RemoveFolder -> PanelGrid(2, 2)
                PanelPage.Folder -> PanelGrid(folderPageItems(content, focus.folderId).size, 1)
                PanelPage.SkipSubfolder -> skipPickCount.takeIf { it > 0 }?.let { PanelGrid(it, 1) }
                PanelPage.Emulators -> PanelGrid(1 + (content.system(focus.systemId)?.choices?.size ?: 0), 1)
                PanelPage.BackgroundPresets, PanelPage.AccentPresets, PanelPage.TextPresets -> PanelGrid(ColorPages.presetCount(page, ColorPages.backgroundOf(settings)), geometry.presetColumns)
                PanelPage.BackgroundCustom, PanelPage.AccentCustom, PanelPage.TextCustom ->
                    PanelGrid(paletteSize + ColorSlider.entries.size + 1, ColorPages.PALETTE_COLUMNS)
                PanelPage.Confirm -> PanelGrid(2, 2)
                PanelPage.Backup -> PanelGrid(backupPanelItems(content.backup).size, 1)
                PanelPage.Update -> updatePanelItems(content.update).takeIf { it.isNotEmpty() }?.let { PanelGrid(it.size, 1) }
                PanelPage.Restore -> PanelGrid(restorePanelItems(content.restore).size, 1)
                PanelPage.UndoRestore -> PanelGrid(undoPanelItems(content, focus.undoAsk).size, 1)
                PanelPage.Licenses -> PanelGrid(LicenseNotices.all.size, 1)
                PanelPage.ScraperAccount -> PanelGrid(accountPageItems(content).size, 1)
                PanelPage.ScraperSystems -> PanelGrid(systemsPickCount(content, focus), 1)
                PanelPage.ScraperConfirm -> PanelGrid(BoxArtConfirmItem.entries.size, 1)
                PanelPage.ScraperSignOutConfirm -> PanelGrid(SignOutConfirmItem.entries.size, 1)
                PanelPage.ScraperClearConfirm -> PanelGrid(ClearConfirmItem.entries.size, 1)
                PanelPage.Systems -> PanelGrid(content.librarySystems.size, 1)
                PanelPage.UnknownFolder -> PanelGrid(1 + content.folderSystemChoices.size, 1)
                PanelPage.LicenseText, PanelPage.ReadOnly, PanelPage.FixName, PanelPage.ScraperSignIn, PanelPage.ProblemReport -> null
            }
    }
}
