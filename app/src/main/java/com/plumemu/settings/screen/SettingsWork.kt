package com.plumemu.settings.screen

import com.plumemu.graph.BoxArtClearResult
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.plumemu.account.SignInForm
import com.plumemu.report.ProblemReportForm
import com.plumemu.report.ReportStep
import com.plumemu.coredata.backup.BackupKind
import com.plumemu.library.scrape.AskAgain
import com.plumemu.account.SignInResult
import com.plumemu.game.PanelCommand
import com.plumemu.game.RenameBoxArtNotice
import com.plumemu.game.TitleList

/**
 * What Settings started outside the store and how it went: the folder being removed,
 * a detection running, and one plain line ([notice]) for the option that tried something. Results
 * arrive on the main thread through the [actions]' callbacks.
 */
@Stable
internal class SettingsWork(
    private val actions: SettingsActions,
) {
    /** The last plain line and the option it belongs to; shown only while that option is focused. */
    var notice: Pair<SettingsOption, SettingsNotice>? by mutableStateOf(null)
        private set

    /** The count the current [notice] is about (Clear Recent…'s "N games"). */
    var noticeCount: Int by mutableStateOf(0)
        private set

    /** Folders asked to be removed whose removal has not ended (it waits for a running scan). */
    var removing: Set<Long> by mutableStateOf(emptySet())
        private set

    var isDetecting: Boolean by mutableStateOf(false)
        private set

    /** The scan moved on: its "already reading" notice is stale. */
    fun clearScanBusyNotice() {
        if (notice?.second == SettingsNotice.ScanBusy) notice = null
    }

    fun clearNotice() {
        notice = null
    }

    /** A plain line about [option] that no outside work produced (Library › Systems says them itself). */
    fun say(
        option: SettingsOption,
        line: SettingsNotice,
    ) {
        notice = option to line
    }

    /** [line] is no longer true (Library › Systems: a system was hidden or shown after the refusal). */
    fun unsay(line: SettingsNotice) {
        if (notice?.second == line) notice = null
    }

    /** The focus left ROM folders: the picker's answer is not shown any more. */
    fun leaveFolders() = actions.forgetFolderAnswer()

    /** The focus left Rescan library: the result of its last scan is not shown again. */
    fun leaveRescan() = actions.forgetScanResult()

    /** Removing [folderId], asked here or in an earlier Settings of the process ([content]). */
    fun isRemoving(
        folderId: Long,
        content: SettingsContent,
    ): Boolean = folderId in removing || folderId in content.removingFolderIds

    /** A detection runs, started here or in an earlier Settings of the process. */
    fun isDetecting(content: SettingsContent): Boolean = isDetecting || content.isDetecting

    /** Rescan library: a scan, or "Already reading your games" when one runs. */
    fun rescan() {
        if (!actions.rescanLibrary()) notice = SettingsOption.Rescan to SettingsNotice.ScanBusy
    }

    fun openFolderPicker() {
        if (!actions.openFolderPicker()) notice = SettingsOption.RomFolders to SettingsNotice.NoFolderPicker
    }

    fun removeFolder(folderId: Long) {
        if (folderId in removing) return
        removing = removing + folderId
        actions.removeFolder(folderId) { removed ->
            removing = removing - folderId
            if (!removed) notice = SettingsOption.RomFolders to SettingsNotice.FolderRemoveFailed
        }
    }

    /** Library › Unidentified games › a game: its system's list for Fix name…; a failed read is said on the page itself. */
    fun titleList(
        gameId: Long,
        onDone: (TitleList?) -> Unit,
    ) {
        actions.names.titleList(gameId, onDone)
    }

    /**
     * Fix name…'s pick or typed name for [gameId]; the game leaves the list. A failure is said in
     * the panel, and so is why box art could not be looked up by the new name, as in the game menu.
     */
    fun fixName(
        gameId: Long,
        command: PanelCommand,
    ) {
        val answered = { stored: Boolean, boxArt: RenameBoxArtNotice? ->
            val line = if (!stored) SettingsNotice.NameFixFailed else boxArt?.let(SettingsNotice::afterRename)
            if (line != null) notice = SettingsOption.UnidentifiedGames to line
        }
        when (command) {
            is PanelCommand.PickTitle -> actions.names.setChosenTitle(gameId, command.choice, answered)
            is PanelCommand.SaveOwnName -> actions.names.setOwnName(gameId, command.name, answered)
            else -> Unit
        }
    }

    /** Library › Hidden games: the game goes back to its lists; a failure is said in the panel. */
    fun unhideGame(gameId: Long) {
        actions.unhideGame(gameId) { stored ->
            if (!stored) notice = SettingsOption.HiddenGames to SettingsNotice.UnhideFailed
        }
    }

    /** Answers asked in this Settings and not yet stored (the graph's own set outlives it: [SettingsContent.answeringFolders]). */
    var answering: Set<FolderKey> by mutableStateOf(emptySet())
        private set

    /**
     * Library › an unknown folder: [systemId] (null: Ignore) for [key], stored and then scanned by the graph in
     * the process's scope, so closing Settings does not lose it. False when this folder's answer is still being stored.
     * A failure is said in the panel.
     */
    fun chooseFolderSystem(
        key: FolderKey,
        systemId: String?,
        content: SettingsContent,
    ): Boolean {
        if (key in answering || key in content.answeringFolders) return false
        answering = answering + key
        actions.chooseFolderSystem(key.folderId, key.relativePath, systemId) { stored ->
            answering = answering - key
            if (!stored) notice = SettingsOption.UnknownFolder to SettingsNotice.FolderAnswerFailed
        }
        return true
    }

    /** ROM folders › a folder › Skip a sub-folder…: the listing asked for last; null when that page is not open. */
    var subfolders: SubfolderPick? by mutableStateOf(null)
        private set

    /** Reads [folderId]'s first-level sub-folders off the main thread; until they arrive the page says it is reading. */
    fun listSubfolders(folderId: Long) {
        subfolders = SubfolderPick(folderId, SubfolderState.Loading)
        actions.listSubfolders(folderId) { names ->
            // Only into the page still open for this folder: one closed meanwhile shows nothing.
            if (subfolders?.folderId == folderId) {
                subfolders = SubfolderPick(folderId, names?.let(SubfolderState::Listed) ?: SubfolderState.Unreadable)
            }
        }
    }

    /** The pick page closed: its listing is not shown again (the next one reads the folder anew). */
    fun forgetSubfolders() {
        subfolders = null
    }

    /** Skips and un-skips asked in this Settings and not yet stored (the graph's own set: [SettingsContent.skippingFolders]). */
    var skipping: Set<FolderKey> by mutableStateOf(emptySet())
        private set

    /**
     * ROM folders › a folder: skips the sub-folder [key] ([skip]) or reads it again, stored and then scanned by
     * the graph in the process's scope, so closing Settings loses nothing. False while the same sub-folder's
     * change is still being stored. A failure is said on the folder's page.
     */
    fun skipSubfolder(
        key: FolderKey,
        skip: Boolean,
        content: SettingsContent,
    ): Boolean {
        if (key in skipping || key in content.skippingFolders) return false
        skipping = skipping + key
        actions.skipSubfolder(key.folderId, key.relativePath, skip) { stored ->
            skipping = skipping - key
            if (!stored) notice = SettingsOption.RomFolders to SettingsNotice.SkipFailed
        }
        return true
    }

    fun chooseEmulator(
        systemId: String,
        emulatorId: String?,
    ) {
        actions.chooseEmulator(systemId, emulatorId) { stored ->
            if (!stored) notice = SettingsOption.SystemEmulator to SettingsNotice.EmulatorChoiceFailed
        }
    }

    /** Detect new emulators; a second press while it runs does nothing more. */
    fun detectEmulators() {
        if (isDetecting) return
        isDetecting = true
        actions.detectEmulators { detected ->
            isDetecting = false
            notice = SettingsOption.DetectEmulators to (if (detected) SettingsNotice.DetectDone else SettingsNotice.DetectFailed)
        }
    }

    /** Sign in from the panel's form; the form shows the answer, [onSignedIn] runs when ScreenScraper accepted. */
    fun signIn(
        form: SignInForm,
        username: String,
        password: String,
        onSignedIn: () -> Unit,
    ) {
        actions.accounts.signIn(username, password) { result ->
            form.showResult(result)
            if (result is SignInResult.SignedIn) onSignedIn()
        }
    }

    /**
     * Send from the report form; the form shows the answer. When the form was left before the
     * answer ([isOpen] false) the answer is said under the row instead, so it is never silent.
     */
    fun submitReport(
        form: ProblemReportForm,
        step: ReportStep.Submit,
        isOpen: () -> Boolean,
    ) {
        actions.problemReports.submit(step.text, step.email, step.includeDetails) { result ->
            form.showResult(result)
            if (!isOpen()) notice = SettingsOption.ReportProblem to SettingsNotice.of(result)
        }
    }

    /** Sign out; a failure is said in the account's panel. */
    fun signOut() {
        actions.accounts.signOut { done -> if (!done) notice = SettingsOption.ScraperAccount to SettingsNotice.SignOutFailed }
    }

    /** Download missing or Re-download for [systemIds] (null: all); a failure is said in the account's panel. */
    fun askAgain(
        which: AskAgain,
        systemIds: Set<String>?,
    ) {
        actions.boxArt.askAgain(which, systemIds) { marked -> if (!marked) notice = SettingsOption.ScraperAccount to SettingsNotice.BoxArtAskFailed }
    }

    /** Clear box art, after its confirm; a failure is said in the account's panel. */
    fun clearBoxArt() {
        actions.boxArt.clearBoxArt { result ->
            val failure =
                when (result) {
                    BoxArtClearResult.Done -> null
                    BoxArtClearResult.InUse -> SettingsNotice.BoxArtClearInUse
                    BoxArtClearResult.RunStillGoing -> SettingsNotice.BoxArtClearRunGoing
                    BoxArtClearResult.Failed -> SettingsNotice.BoxArtClearFailed
                }
            if (failure != null) notice = SettingsOption.ScraperAccount to failure
        }
    }

    /** Clear Recent, after its confirm; the answer is the row's notice (a count, "already empty" or the failure). */
    fun clearRecent() {
        actions.recent.clearRecent { cleared ->
            noticeCount = cleared ?: 0
            notice = SettingsOption.ClearRecent to
                when {
                    cleared == null -> SettingsNotice.RecentClearFailed
                    cleared == 0 -> SettingsNotice.RecentAlreadyEmpty
                    else -> SettingsNotice.RecentCleared
                }
        }
    }

    /** Pause or Resume; a failure is said in the account's panel. */
    fun setBoxArtPaused(isPaused: Boolean) {
        actions.boxArt.setPaused(isPaused) { stored -> if (!stored) notice = SettingsOption.ScraperAccount to SettingsNotice.BoxArtPauseFailed }
    }

    /** The focus left Back up…: how the last backup ended is not shown again. */
    fun leaveBackup() = actions.backup.forgetResult()

    /** Advanced › Back up…: the save picker for [kind]; the graph starts the backup with its answer. */
    fun startBackup(kind: BackupKind) {
        // A result still shown would read as the answer to this new choice.
        actions.backup.forgetResult()
        if (!actions.backup.pick(kind)) notice = SettingsOption.Backup to SettingsNotice.NoSavePicker
    }

    /** The focus left Restore… or Undo last restore: how the last one ended is not shown again. */
    fun leaveRestore() = actions.restore.forgetResult()

    /** Advanced › Restore…: the open picker; the graph reads the file with its answer. */
    fun startRestore() {
        actions.restore.forgetResult()
        if (!actions.restore.pick()) notice = SettingsOption.Restore to SettingsNotice.NoOpenPicker
    }

    /** Advanced › Export logs: Android's share sheet with the log file; a failure is said under the row. */
    fun exportLogs() {
        actions.exportLogs { shared -> if (!shared) notice = SettingsOption.ExportLogs to SettingsNotice.ExportLogsFailed }
    }

    fun openHomeSettings() {
        if (!actions.openHomeSettings()) notice = SettingsOption.UseAsHome to SettingsNotice.HomeRoleUnavailable
    }
}
