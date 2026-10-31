package com.plumemu.settings.screen

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.plumemu.telemetry.ReportResult
import androidx.compose.runtime.Immutable
import com.plumemu.R
import com.plumemu.account.AccountStatus
import com.plumemu.content.OrderFacts
import com.plumemu.coredata.settings.SecondScreenSettings
import com.plumemu.graph.BackupState
import com.plumemu.graph.BuildFeatures
import com.plumemu.update.UpdateView
import com.plumemu.graph.RestoreState
import com.plumemu.graph.WayBackSet
import com.plumemu.graph.ScanReport
import com.plumemu.graph.ScrapeStatus
import com.plumemu.scraper.AccountLimits
import com.plumemu.secondscreen.SecondScreen
import com.plumemu.secondscreen.isSecondScreenOn
import com.plumemu.setup.SetupFolder
import com.plumemu.game.RenameBoxArtNotice

/*
 * What Settings shows besides the stored settings: the picked folders, the scan, the
 * emulator of every system that has games, and whether Plumemu is the home app. The graph builds it
 * (`GraphSettingsLibrary`); the screen only reads it.
 */

/** Library, Emulators and Home screen as they are now; one value per change. */
@Immutable
internal data class SettingsContent(
    /** Every recorded folder, in the order they were picked; [SetupFolder.isUnavailable] when Plumemu cannot read it. */
    val folders: List<SetupFolder> = emptyList(),
    /** A scan runs (or was asked for). */
    val isScanning: Boolean = false,
    /** The scan's counts while it runs and its result after, for Rescan library's panel. */
    val scan: ScanReport = ScanReport(),
    /** Games in the library, for the rescan's result line. */
    val gameCount: Int = 0,
    /** Systems with games, counted from the same rows as [gameCount] (unlike [systems], which the catalog filters). */
    val systemCount: Int = 0,
    /** Systems with games, in the platform data's order. */
    val systems: List<SystemEmulators> = emptyList(),
    /** Emulator apps found by the last detection; null before one ran. */
    val emulatorAppCount: Int? = null,
    /** Plumemu is the home app now (asked again whenever Settings comes back to the front). */
    val isHomeApp: Boolean = false,
    /** Folders whose removal runs in the process (it may have been asked for in an earlier Settings). */
    val removingFolderIds: Set<Long> = emptySet(),
    /** A detection runs in the process. */
    val isDetecting: Boolean = false,
    /** Folders and emulators could not be read; Settings says so (opening it again reads anew). */
    val loadFailed: Boolean = false,
    /** Library › Hidden games, by sort name (the row shows only when there are some). */
    val hiddenGames: List<SettingsGame> = emptyList(),
    /** Library › Unidentified games: named only from the file, by sort name. */
    val unidentifiedGames: List<SettingsGame> = emptyList(),
    /** The device's second screen; Home screen lists its switch only while there is one. */
    val secondScreen: SecondScreen? = null,
    /** The second screen is in use ([isSecondScreenOn] of the stored choice); In game is listed only then. */
    val isSecondScreenOn: Boolean = false,
    /** The ScreenScraper account: its row, its status line and the box art region are listed from it. */
    val account: AccountStatus = AccountStatus.NotInThisBuild,
    /** The box-art run (`AppGraph.scrapeStatus`): the account row's value and its panel's status line. */
    val boxArt: ScrapeStatus = ScrapeStatus.NotInThisBuild,
    /** What the account may do, from ScreenScraper's latest answer; null while unknown or signed out. */
    val accountLimits: AccountLimits? = null,
    /** The space the saved box-art pictures use ("Box art: 312 MB"); null until measured. */
    val boxArtBytes: Long? = null,
    /** Games no longer asked for after server errors on 3 different days; Re-download all asks them again. */
    val boxArtGivenUp: Int = 0,
    /** Advanced › Back up… (`AppGraph.backup`): running with its progress, or how the last one ended. */
    val backup: BackupState = BackupState.Idle,

    /**
     * A statistics server is named (`BuildFeatures.hasStatistics`): without one the "Send usage statistics" and
     * "Report a problem" rows are not listed. Default from this build, so the first content, before anything is read, already lists the right rows.
     */
    val hasStatistics: Boolean = BuildFeatures.hasStatistics,
    /** An update manifest is named (`BuildFeatures.hasUpdates`): without one the About update rows are not listed. */
    val hasUpdates: Boolean = BuildFeatures.hasUpdates,
    /** About's updates (`AppGraph.updates`): the last check, what it found, and an update under way. */
    val update: UpdateView = UpdateView(),
    /** Advanced › Restore… (`AppGraph.restore`): reading, the confirm, replacing, or how the last one ended. */
    val restore: RestoreState = RestoreState.Idle,
    /** The way back the last restore kept; Undo last restore is listed only while it exists. */
    val wayBack: WayBackSet? = null,
    /**
     * Every system with games, also those the platform data does not know, in no particular order:
     * Library › Systems arranges them as the main screen does ([com.plumemu.content.SystemOrder]).
     */
    val librarySystems: List<LibrarySystem> = emptyList(),
    /**
     * Library › Unknown folders: first the folders whose name matches no system (A answers), then the answered
     * ones, so an answer can be changed ([SettingsFolderRow]); each group by picked folder then name.
     */
    val unknownFolders: List<SettingsFolderRow> = emptyList(),
    /** The systems a folder can be answered with, as the rest of Plumemu names them, alphabetical. */
    val folderSystemChoices: List<FolderSystemChoice> = emptyList(),
    /** Answers whose write waits for a running scan or runs now; they stay in the process when Settings closes. */
    val answeringFolders: Set<FolderKey> = emptySet(),
    /** ROM folders › a folder: the sub-folders each picked folder skips, by folder id, A to Z ignoring case. */
    val skippedSubfolders: Map<Long, List<String>> = emptyMap(),
    /** Skips and un-skips whose write waits for a running scan or runs now; they stay in the process when Settings closes. */
    val skippingFolders: Set<FolderKey> = emptySet(),
    /** Statistics' figures, read once when Settings opens ([rememberStatistics]); no rows until they arrive. */
    val statistics: StatisticsState = StatisticsState.NotRead,
) {
    /** Statistics' rows, in order; worked out once per content. */
    val statisticRows: List<StatisticRow> by lazy(LazyThreadSafetyMode.NONE) { statisticRowsOf(statistics) }

    /** False for a row that needs a statistics server or an update manifest this build does not have. */
    fun isInThisBuild(option: SettingsOption): Boolean =
        when (option) {
            SettingsOption.UsageStatistics, SettingsOption.ReportProblem -> hasStatistics
            SettingsOption.CheckForUpdates, SettingsOption.CheckUpdatesDaily, SettingsOption.UpdateAvailable -> hasUpdates
            else -> true
        }

    fun system(id: String?): SystemEmulators? = systems.firstOrNull { it.systemId == id }

    /** The unknown-folder row drawn as row [rowIndex] of Library's rows (null when that row is another option). */
    fun unknownFolderAtRow(rowIndex: Int): SettingsFolderRow? {
        val first = SettingsSection.Library.rowsIn(this).indexOf(SettingsOption.UnknownFolder)
        return if (first < 0) null else unknownFolders.getOrNull(rowIndex - first)
    }

    fun folder(id: Long?): SetupFolder? = folders.firstOrNull { it.id == id }

    /** This content with the second screen found and whether the stored choice ([stored]) uses it. */
    fun withSecondScreen(
        screen: SecondScreen?,
        stored: SecondScreenSettings,
    ): SettingsContent = copy(secondScreen = screen, isSecondScreenOn = isSecondScreenOn(stored, screen))
}

/**
 * One game in a Settings list (Library › Hidden games, Unidentified games): what its row shows
 * and the database id an action is keyed by. [isNotAGame]: hidden because the person said "Not a
 * game"; showing it again puts the name back to automatic.
 */
@Immutable
internal data class SettingsGame(
    val id: Long,
    val title: String,
    val systemName: String,
    val isNotAGame: Boolean = false,
)

/** A folder below a picked folder, by ids: the picked folder's id and the folder's name (the path below it). */
@Immutable
internal data class FolderKey(
    val folderId: Long,
    val relativePath: String,
)

/** What the person said about a folder whose name matches no system. */
internal sealed interface FolderAnswer {
    data object Unanswered : FolderAnswer

    data object Ignored : FolderAnswer

    data class OfSystem(val id: String, val name: String) : FolderAnswer
}

/** One row of Library › Unknown folders: the folder, the files in it no system claims (0 once answered) and its answer. */
@Immutable
internal data class SettingsFolderRow(
    val key: FolderKey,
    val files: Int,
    val answer: FolderAnswer,
) {
    val name: String get() = key.relativePath
}

/** A system a folder can be answered with. */
@Immutable
internal data class FolderSystemChoice(
    val id: String,
    val name: String,
)

/** One system with games, as Library › Systems lists it: the name the main screen shows, its games, and the order's facts. */
@Immutable
internal data class LibrarySystem(
    val id: String,
    val name: String,
    val gameCount: Int,
    val maker: String? = null,
    val year: Int? = null,
) {
    val facts: OrderFacts get() = OrderFacts(id, name, maker, year)
}

/** One emulator a person may pick for a system. */
@Immutable
internal data class EmulatorEntry(
    val id: String,
    val name: String,
    /** Listed and choosable, never picked automatically; its row says so. */
    val isLegalRisk: Boolean,
    val isInstalled: Boolean = true,
)

/**
 * A system's row in Settings › Emulators. [chosen] is the person's choice for every game of the
 * system; null means nothing is chosen and the first game played asks. [automatic] is
 * the recommendation that question puts first (the first installed of the system's defaults, never
 * legal-risk); it starts nothing by itself. Both follow the launch's order (`EmulatorChoices.resolve`).
 */
@Immutable
internal data class SystemEmulators(
    val systemId: String,
    val name: String,
    val automatic: EmulatorEntry?,
    val chosen: EmulatorEntry?,
    /** The system's installed emulators in the platform data's order. */
    val installed: List<EmulatorEntry>,
) {
    /** The panel's choices after "Automatic": the installed ones, and a chosen one since uninstalled. */
    val choices: List<EmulatorEntry>
        get() = if (chosen != null && installed.none { it.id == chosen.id }) installed + chosen else installed

    /** Panel item of the current choice: 0 for Automatic. */
    val currentIndex: Int
        get() = chosen?.let { c -> choices.indexOfFirst { it.id == c.id } + 1 }?.takeIf { it > 0 } ?: 0
}

/** A plain line about something Settings tried, shown with the option it belongs to. */
internal enum class SettingsNotice(
    @StringRes val text: Int = 0,
    /** A plural of a count ([SettingsWork.noticeCount]) in place of [text]; read through [lineText]. */
    @PluralsRes val plural: Int = 0,
) {
    ScanBusy(R.string.settings_rescan_busy),
    NoFolderPicker(R.string.setup_message_no_picker),
    FolderRemoveFailed(R.string.settings_folder_remove_failed),
    EmulatorChoiceFailed(R.string.settings_emulator_choice_failed),
    DetectDone(R.string.settings_detect_done),
    DetectFailed(R.string.settings_detect_failed),
    HomeRoleUnavailable(R.string.settings_home_unavailable),

    /** Advanced › Export logs: no log yet, the file could not be written or nothing can share it. */
    ExportLogsFailed(R.string.settings_export_logs_failed),
    UnhideFailed(R.string.settings_unhide_failed),
    NameFixFailed(R.string.settings_name_fix_failed),
    GameGone(R.string.settings_name_fix_game_gone),
    SignOutFailed(R.string.settings_account_sign_out_failed),
    BoxArtAskFailed(R.string.settings_box_art_ask_failed),
    BoxArtPauseFailed(R.string.settings_box_art_pause_failed),
    BoxArtClearFailed(R.string.settings_box_art_clear_failed),
    BoxArtClearInUse(R.string.settings_box_art_clear_in_use),
    BoxArtClearRunGoing(R.string.settings_box_art_clear_run_going),

    /** Library › Clear Recent…: how many games left Recent, that it was already empty, or that it could not be written. */
    RecentCleared(plural = R.plurals.settings_clear_recent_done),
    RecentAlreadyEmpty(R.string.settings_clear_recent_empty),
    RecentClearFailed(R.string.settings_clear_recent_failed),

    /** Advanced › Back up…: the device has no save picker. */
    NoSavePicker(R.string.settings_backup_no_picker),

    /** Advanced › Restore…: the device has no open picker. */
    NoOpenPicker(R.string.settings_restore_no_picker),

    /** Library › Systems: a system picked up while the order was A–Z or Maker & year. */
    SystemOrderNowManual(R.string.settings_systems_now_manual),

    /** Library › Unknown folders: the answer was not stored (it waits for nothing: the person tries again). */
    FolderAnswerFailed(R.string.settings_unknown_folder_failed),

    /** ROM folders › a folder: a skip or un-skip was not stored. */
    SkipFailed(R.string.settings_folder_skip_failed),

    /** Library › Systems: Y on the only system still shown. */
    LastSystemShown(R.string.settings_systems_last_shown),

    /** Unidentified games: the name is saved, box art was not looked up by it ([afterRename]). */
    RenameNoWifi(R.string.settings_rename_box_art_no_wifi),
    RenameSignIn(R.string.settings_rename_box_art_sign_in),
    RenamePaused(R.string.settings_rename_box_art_paused),
    RenameDailyLimit(R.string.settings_rename_box_art_daily_limit),
    RenameServerClosed(R.string.settings_rename_box_art_server_closed),

    /** About › Report a problem: the answer to a report whose form was left before it came. */
    ReportSent(R.string.report_sent),
    ReportSaved(R.string.report_saved),
    ReportRefused(R.string.report_error_refused),
    ReportNotSaved(R.string.report_error_not_saved),
    ReportUnavailable(R.string.report_error_unavailable),
    ;

    companion object {
        /** The line for a report's [result] said when its form is gone. */
        fun of(result: ReportResult): SettingsNotice =
            when (result) {
                ReportResult.Sent -> ReportSent
                ReportResult.Saved -> ReportSaved
                ReportResult.Refused -> ReportRefused
                ReportResult.NotSaved, ReportResult.Empty -> ReportNotSaved
                ReportResult.Unavailable -> ReportUnavailable
            }

        /**
         * The line for [notice] after a rename. Settings' lines take no arguments, so the time a
         * limit or closing ends (said in the game menu) is not given here.
         */
        fun afterRename(notice: RenameBoxArtNotice): SettingsNotice =
            when (notice) {
                RenameBoxArtNotice.NoWifi -> RenameNoWifi
                RenameBoxArtNotice.SignInNeeded -> RenameSignIn
                RenameBoxArtNotice.Paused -> RenamePaused
                is RenameBoxArtNotice.DailyLimit -> RenameDailyLimit
                is RenameBoxArtNotice.ServerClosed -> RenameServerClosed
            }
    }
}

/** What the picker gave Settings › Library › ROM folders › + Add folder, until the next pick. */
internal enum class FolderAddState {
    Idle,
    Adding,
    Added,
    AlreadyAdded,

    /** Android gave no lasting access. */
    NotKept,

    /** The database or the provider failed. */
    CouldNotOpen,
}
