package com.plumemu.settings.screen

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.plumemu.account.ScraperAccounts
import com.plumemu.report.ProblemReportActions
import com.plumemu.coredata.backup.BackupKind
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.graph.LibraryResetState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/** What Settings shows of work it started: the last Reset library, a failed write, the last folder picked. */
@Immutable
internal data class SettingsStatus(
    val libraryReset: LibraryResetState = LibraryResetState.Idle,
    val saveFailed: Boolean = false,
    val folderAdd: FolderAddState = FolderAddState.Idle,
)

internal val LocalSettingsStatus = staticCompositionLocalOf { SettingsStatus() }

/**
 * What the graph does for Settings' Library and Emulators sections. Each call returns
 * at once; results come back through the callbacks, on the main thread.
 */
internal interface SettingsLibraryActions {
    fun removeFolder(
        folderId: Long,
        onDone: (Boolean) -> Unit,
    )

    fun rescanLibrary(): Boolean

    /** The person left the Rescan panel: the last scan's result is not shown again. */
    fun forgetScanResult()

    fun chooseEmulator(
        systemId: String,
        emulatorId: String?,
        onDone: (Boolean) -> Unit,
    )

    fun detectEmulators(onDone: (Boolean) -> Unit)

    /** A picker answer is coming: [SettingsHost.folderAdd] goes back to idle. */
    fun startFolderPick()

    /** The person left ROM folders: the picker's last answer is not news any more. */
    fun forgetFolderAnswer()

    fun unhideGame(
        gameId: Long,
        onDone: (Boolean) -> Unit,
    )

    fun chooseFolderSystem(
        folderId: Long,
        relativePath: String,
        systemId: String?,
        onDone: (Boolean) -> Unit,
    )

    /** ROM folders › a folder: [relativePath] of [folderId] skipped ([skip]) or read again, then a scan; [onDone] says whether it was stored. */
    fun skipSubfolder(
        folderId: Long,
        relativePath: String,
        skip: Boolean,
        onDone: (Boolean) -> Unit,
    )

    /** [folderId]'s first-level sub-folders read from disk, A to Z; null when the folder cannot be read. */
    fun listSubfolders(
        folderId: Long,
        onDone: (List<String>?) -> Unit,
    )

    object None : SettingsLibraryActions {
        override fun skipSubfolder(
            folderId: Long,
            relativePath: String,
            skip: Boolean,
            onDone: (Boolean) -> Unit,
        ) = onDone(false)

        override fun listSubfolders(
            folderId: Long,
            onDone: (List<String>?) -> Unit,
        ) = onDone(null)

        override fun removeFolder(
            folderId: Long,
            onDone: (Boolean) -> Unit,
        ) = onDone(false)

        override fun rescanLibrary(): Boolean = false

        override fun forgetScanResult() = Unit

        override fun chooseEmulator(
            systemId: String,
            emulatorId: String?,
            onDone: (Boolean) -> Unit,
        ) = onDone(false)

        override fun detectEmulators(onDone: (Boolean) -> Unit) = onDone(false)

        override fun startFolderPick() = Unit

        override fun forgetFolderAnswer() = Unit

        override fun unhideGame(
            gameId: Long,
            onDone: (Boolean) -> Unit,
        ) = onDone(false)

        override fun chooseFolderSystem(
            folderId: Long,
            relativePath: String,
            systemId: String?,
            onDone: (Boolean) -> Unit,
        ) = onDone(false)
    }
}

/** What only the Activity can do for Settings: the folder picker and Android's home-app screens. */
internal interface SettingsActivityActions {
    /** False when the device has no folder picker. */
    fun openFolderPicker(): Boolean

    /** False when the device offers no screen to pick the home app. */
    fun openHomeSettings(): Boolean

    /** Asked each time Settings comes back to the front (a binder call; never per frame). */
    fun isHomeApp(): Boolean

    /** Android's save picker for a [kind] backup (Advanced › Back up…); false when the device has none. */
    fun openBackupPicker(kind: BackupKind): Boolean

    /** Android's open picker for a backup file (Advanced › Restore…); false when the device has none. */
    fun openRestorePicker(): Boolean

    /** Advanced › Export logs: the log file to Android's share sheet; [onDone] on the main thread. */
    fun exportLogs(onDone: (Boolean) -> Unit) = onDone(false)

    object None : SettingsActivityActions {
        override fun openFolderPicker(): Boolean = false

        override fun openBackupPicker(kind: BackupKind): Boolean = false

        override fun openRestorePicker(): Boolean = false

        override fun openHomeSettings(): Boolean = false

        override fun isHomeApp(): Boolean = false
    }
}

/**
 * What the Activity hands the main screen for Settings: the store's writes, the two resets, the
 * Library and Emulators work, the version to show. `MainScreen` adds closing (back to the main
 * screen) to make [SettingsActions].
 */
internal class SettingsHost(
    val versionName: String,
    val update: ((PlumSettings) -> PlumSettings) -> Unit,
    val resetAllSettings: () -> Unit,
    val resetLibrary: () -> Unit,
    /** The last Reset library of this process (forgetting, forgotten, failed). */
    val libraryReset: StateFlow<LibraryResetState> = MutableStateFlow(LibraryResetState.Idle),
    /** The last settings write failed. */
    val saveFailed: StateFlow<Boolean> = MutableStateFlow(false),
    /** Folders, scan, systems' emulators (without [SettingsContent.isHomeApp], which the screen asks). */
    val content: Flow<SettingsContent> = flowOf(SettingsContent()),
    /** The last folder picked from Settings. */
    val folderAdd: StateFlow<FolderAddState> = MutableStateFlow(FolderAddState.Idle),
    val library: SettingsLibraryActions = SettingsLibraryActions.None,
    /** Unidentified games and Fix name…. */
    val names: SettingsNameActions = SettingsNameActions.None,
    /** The ScreenScraper account. */
    val accounts: ScraperAccounts = ScraperAccounts.Unavailable,
    /** Library › Clear Recent…. */
    val recent: SettingsRecentActions = SettingsRecentActions.None,
    /** Box art's Download missing, Re-download, Pause and Resume. */
    val boxArt: SettingsBoxArtActions = SettingsBoxArtActions.None,
    /** The folder picker and the home-app screens (the Activity's). */
    val activity: SettingsActivityActions = SettingsActivityActions.None,
    /** Statistics' figures and the covers' loader. */
    val statistics: SettingsStatisticsSource = SettingsStatisticsSource.None,
    /** Advanced › Back up…'s Cancel and its result (the picker is [activity]'s). */
    val backupWork: BackupWork = BackupWork.None,
    /** Advanced › Restore… and Undo last restore (the picker is [activity]'s). */
    val restoreWork: RestoreWork = RestoreWork.None,
    /** About's Check for updates and Update. */
    val updates: SettingsUpdateActions = SettingsUpdateActions.None,
    /** About › Report a problem. */
    val problemReports: ProblemReportActions = ProblemReportActions.None,
) {
    /** What a finger on the colour sliders shows before it is written; the main screen lays it over the stored settings. */
    val colorPreview = ColorPreview()

    fun actions(close: () -> Unit): SettingsActions =
        object : SettingsActions {
            override fun update(transform: (PlumSettings) -> PlumSettings) = this@SettingsHost.update(transform)

            override val colorPreview: ColorPreview get() = this@SettingsHost.colorPreview

            override fun resetAllSettings() = this@SettingsHost.resetAllSettings()

            override fun resetLibrary() = this@SettingsHost.resetLibrary()

            override fun close() = close()

            override fun openFolderPicker(): Boolean {
                library.startFolderPick()
                return activity.openFolderPicker()
            }

            override fun removeFolder(
                folderId: Long,
                onDone: (Boolean) -> Unit,
            ) = library.removeFolder(folderId, onDone)

            override fun rescanLibrary(): Boolean = library.rescanLibrary()

            override fun forgetScanResult() = library.forgetScanResult()

            override fun chooseEmulator(
                systemId: String,
                emulatorId: String?,
                onDone: (Boolean) -> Unit,
            ) = library.chooseEmulator(systemId, emulatorId, onDone)

            override fun detectEmulators(onDone: (Boolean) -> Unit) = library.detectEmulators(onDone)

            override fun openHomeSettings(): Boolean = activity.openHomeSettings()

            override fun exportLogs(onDone: (Boolean) -> Unit) = activity.exportLogs(onDone)

            override fun forgetFolderAnswer() = library.forgetFolderAnswer()

            override fun unhideGame(
                gameId: Long,
                onDone: (Boolean) -> Unit,
            ) = library.unhideGame(gameId, onDone)

            override fun chooseFolderSystem(
                folderId: Long,
                relativePath: String,
                systemId: String?,
                onDone: (Boolean) -> Unit,
            ) = library.chooseFolderSystem(folderId, relativePath, systemId, onDone)

            override fun skipSubfolder(
                folderId: Long,
                relativePath: String,
                skip: Boolean,
                onDone: (Boolean) -> Unit,
            ) = library.skipSubfolder(folderId, relativePath, skip, onDone)

            override fun listSubfolders(
                folderId: Long,
                onDone: (List<String>?) -> Unit,
            ) = library.listSubfolders(folderId, onDone)

            override val names: SettingsNameActions get() = this@SettingsHost.names

            override val accounts: ScraperAccounts get() = this@SettingsHost.accounts

            override val problemReports: ProblemReportActions get() = this@SettingsHost.problemReports

            override val boxArt: SettingsBoxArtActions get() = this@SettingsHost.boxArt

            override val recent: SettingsRecentActions get() = this@SettingsHost.recent

            override val statistics: SettingsStatisticsSource get() = this@SettingsHost.statistics

            override val backup: SettingsBackupActions =
                object : SettingsBackupActions {
                    override fun pick(kind: BackupKind): Boolean = activity.openBackupPicker(kind)

                    override fun cancel() = backupWork.cancel()

                    override fun forgetResult() = backupWork.forgetResult()
                }

            override val updates: SettingsUpdateActions get() = this@SettingsHost.updates

            override val restore: SettingsRestoreActions =
                object : SettingsRestoreActions {
                    override fun pick(): Boolean = activity.openRestorePicker()

                    override fun cancel() = restoreWork.cancel()

                    override fun confirm() = restoreWork.confirm()

                    override fun undo() = restoreWork.undo()

                    override fun forgetCopy() = restoreWork.forgetCopy()

                    override fun forgetResult() = restoreWork.forgetResult()
                }
        }

    /** The graph's side of Restore… and Undo last restore that Settings drives. */
    interface RestoreWork {
        fun cancel()

        fun confirm()

        fun undo()

        fun forgetCopy()

        fun forgetResult()

        object None : RestoreWork {
            override fun cancel() = Unit

            override fun confirm() = Unit

            override fun undo() = Unit

            override fun forgetCopy() = Unit

            override fun forgetResult() = Unit
        }
    }

    /** The graph's side of Back up… that Settings drives: Cancel and forgetting the result. */
    interface BackupWork {
        fun cancel()

        fun forgetResult()

        object None : BackupWork {
            override fun cancel() = Unit

            override fun forgetResult() = Unit
        }
    }
}
