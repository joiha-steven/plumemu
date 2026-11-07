package com.plumemu.settings.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.plumemu.account.AccountStatus
import com.plumemu.content.SystemOrder
import com.plumemu.coredata.backup.BackupKind
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SystemOrderSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.update.ui.NotesScroll
import com.plumemu.library.scrape.AskAgain
import com.plumemu.sound.Outcome

/**
 * What the panel column does with presses: colour pages, licences, folders, emulators, the confirm
 * step, Fix name…. [SettingsModel] adds the sections and options columns on top.
 */
internal abstract class SettingsPanelModel(
    actions: SettingsActions,
    initialFocus: SettingsFocus,
) : SettingsColorModel(actions, initialFocus) {
    init {
        report.draft = actions.reportDraft
    }

    /** Up (-1) and down (+1) presses in an open licence text or a panel of text; the screen scrolls by them. */
    var licenseScroll: Int by mutableIntStateOf(0)
        protected set

    /** Whether the open licence text can scroll that way; the page sets it (it knows its size). */
    var licenseCanScroll: (down: Boolean) -> Boolean = { true }

    /** The panel beside the row is text that runs past its column: only then the row's "More" enters it. */
    var panelTextScrolls: Boolean by mutableStateOf(false)

    /** The update panel's notes, scrolled by Up and Down past its buttons. */
    val updateNotes = NotesScroll()

    /** Library › Systems: the system being carried and the order the panel holds ([SettingsSystems]). */
    val systems = SettingsSystems()

    protected fun onPanel(
        action: Action,
        settings: PlumSettings,
        geometry: SettingsGeometry,
    ) {
        val page = focus.page ?: return closePanel()
        when (action) {
            is Action.Move -> movePanel(page, action.direction, settings, geometry)
            Action.Enter -> onPanelEnter(settings)
            Action.Back -> onPanelBack(page)
            else -> Unit
        }
    }

    protected fun movePanel(
        page: PanelPage,
        direction: Direction,
        settings: PlumSettings,
        geometry: SettingsGeometry,
    ) {
        if (page == PanelPage.LicenseText || (page == PanelPage.ReadOnly && panelTextScrolls)) {
            when (direction) {
                Direction.Up -> licenseScroll--
                Direction.Down -> licenseScroll++
                else -> return
            }
            if (licenseCanScroll(direction == Direction.Down)) feel.report(Outcome.Moved)
            return
        }
        if (page == PanelPage.Update) return moveInUpdatePage(direction, settings, geometry)
        if (SettingsCustomColor.isCustomPage(page)) return moveInCustomPage(page, direction)
        if (page == PanelPage.ColorSets) return moveInColorSets(direction, settings, geometry)
        val grid = PanelGrid.of(page, geometry, content, focus, settings, custom.palette(page).size, pickableSubfolders(work.subfolders, content).size) ?: return
        var next = grid.move(focus.panelIndex, direction)
        // Two buttons side by side stack when the text is large, so Down and Up walk them too.
        if (page in TWO_BUTTON_PAGES && next == focus.panelIndex) {
            if (direction == Direction.Down) next = grid.move(focus.panelIndex, Direction.Right)
            if (direction == Direction.Up) next = grid.move(focus.panelIndex, Direction.Left)
        }
        // Files not added: its headings are no stops, the cursor goes on to the line under one.
        if (page == PanelPage.FilesNotAdded) {
            var from = focus.panelIndex
            while (next != from && content.notAdded.getOrNull(next) is NotAddedItem.Heading) {
                from = next
                next = grid.move(next, direction)
            }
            if (content.notAdded.getOrNull(next) is NotAddedItem.Heading) return
        }
        if (next != focus.panelIndex) return focusPanelItem(next)
        if (direction == Direction.Left && ColorPages.kindOf(page) != null) leaveColourPageToItsRow(page)
    }

    /** Updates' panel: Up and Down walk its items; past the first item, and past the update's last button while the notes run on, they scroll the notes. */
    private fun moveInUpdatePage(
        direction: Direction,
        settings: PlumSettings,
        geometry: SettingsGeometry,
    ) {
        if (direction != Direction.Up && direction != Direction.Down) return
        val down = direction == Direction.Down
        val at = focus.panelIndex
        val hasNotes = content.update.found != null
        val lastButton = (updateStepItems(content.update).size - 1).coerceAtLeast(0)
        if (hasNotes && (if (down) at == lastButton && updateNotes.canScroll(true) else at == 0)) {
            if (updateNotes.press(down)) feel.report(Outcome.Moved)
            return
        }
        val next = PanelGrid.of(PanelPage.Update, geometry, content, focus, settings)?.move(at, direction) ?: at
        if (next != at) focusPanelItem(next)
    }

    protected fun onPanelEnter(settings: PlumSettings) {
        val page = focus.page ?: return
        val index = focus.panelIndex
        when (page) {
            PanelPage.ColorSets -> onColorSetsEnter(index, settings)
            PanelPage.BackgroundPresets, PanelPage.AccentPresets, PanelPage.TextPresets -> onPresetsEnter(page, index, settings)
            PanelPage.BackgroundCustom, PanelPage.AccentCustom, PanelPage.TextCustom -> onCustomPageEnter(page, index)
            // A applies the language under the cursor (the screen recreates itself as it always did); B closes with no change.
            PanelPage.Language -> {
                val tag = languagePanelTag(index)
                actions.update { stored -> stored.withAppearance { it.copy(language = tag) } }
                cause = Cause.Enter // a tap on the item is also an activation: confirm sound and click, not the back/move one
                closePanel()
            }
            PanelPage.Folders -> onFolderEnter(index)
            PanelPage.RemoveFolder -> onRemoveEnter(index)
            PanelPage.Folder -> onFolderPageEnter(index)
            PanelPage.SkipSubfolder -> onSkipPickEnter(index)
            PanelPage.HiddenGames -> SettingsListPages.unhideAt(index, content, work, focus)?.let(::moveTo) ?: closePanel()
            PanelPage.FilesNotAdded -> SettingsListPages.toUnknownFolder(index, content, focus)?.let(::moveTo) ?: feel.report(Outcome.Nothing)
            PanelPage.UnidentifiedGames -> openNameFix(index)
            PanelPage.FixName, PanelPage.ScraperSignIn, PanelPage.ProblemReport -> Unit
            PanelPage.BoxArtSource ->
                when (val entry = boxArtPanelEntries(content).getOrNull(index)) {
                    is BoxArtPanelEntry.Work -> onAccountEnter(entry.item)
                    is BoxArtPanelEntry.Choice -> onBoxArtSourceEnter(entry.source)
                    null -> onBoxArtSourceEnter(null)
                }
            PanelPage.ScraperAccount -> onAccountEnter(accountPageItems(content).getOrNull(index))
            PanelPage.RaAccount -> enterRaItem(raPageItems(content).getOrNull(index), content, ::askRaSignOut, ::openRaSignIn) { if (focus.page == PanelPage.RaAccount) closePanel() }
            PanelPage.RaSignOutConfirm -> onRaSignOutConfirmEnter(SignOutConfirmItem.entries.getOrNull(index))
            PanelPage.ScraperSystems -> onSystemsEnter(index)
            PanelPage.ScraperConfirm -> onBoxArtConfirmEnter(BoxArtConfirmItem.entries.getOrNull(index))
            PanelPage.ScraperSignOutConfirm -> onSignOutConfirmEnter(SignOutConfirmItem.entries.getOrNull(index))
            PanelPage.ScraperClearConfirm -> onClearConfirmEnter(ClearConfirmItem.entries.getOrNull(index))
            // Its presses go through onSystemsAction, which needs the settings shown.
            PanelPage.Systems -> Unit
            PanelPage.UnknownFolder -> onUnknownFolderEnter(index)
            PanelPage.Emulators -> {
                val system = content.system(focus.systemId) ?: return closePanel()
                // Item 0 is Automatic: the person's choice is forgotten.
                val chosen = if (index == 0) null else system.choices.getOrNull(index - 1) ?: return
                work.chooseEmulator(system.systemId, chosen?.id)
                closePanel()
            }
            PanelPage.Confirm -> {
                closePanel()
                if (index == SettingsModel.CONFIRM_RESET) {
                    work.clearRecent()
                    doneOption = SettingsOption.ClearRecent
                }
            }
            PanelPage.Reset -> onResetEnter(ResetPanelItem.entries.getOrNull(index))
            PanelPage.ResetAllConfirm -> onResetConfirmEnter(ResetPanelItem.All, index)
            PanelPage.ResetLibraryConfirm -> onResetConfirmEnter(ResetPanelItem.Library, index)
            PanelPage.BackupRestore -> onBackupRestoreEnter(backupRestoreItems(content).getOrNull(index))
            PanelPage.Backup -> onBackupEnter(backupPanelItems(content.backup).getOrNull(index))
            PanelPage.Update -> onUpdateEnter(updatesPanelItems(content.update).getOrNull(index))
            PanelPage.Restore -> onRestoreEnter(restorePanelItems(content.restore).getOrNull(index))
            PanelPage.UndoRestore -> onUndoEnter(undoPanelItems(content, focus.undoAsk).getOrNull(index))
            PanelPage.Licenses -> moveTo(focus.copy(page = PanelPage.LicenseText, licenseIndex = index)).also { licenseScroll = 0 }
            PanelPage.LicenseText -> Unit
            PanelPage.ReadOnly -> closePanel()
        }
    }

    final override fun onPanelBack(page: PanelPage) {
        when (page) {
            PanelPage.BackgroundCustom, PanelPage.AccentCustom, PanelPage.TextCustom -> {
                val presets = ColorPages.presetsOf(ColorPages.kindOf(page) ?: return)
                moveTo(focus.copy(page = presets, panelIndex = custom.presetCount(presets) - 1))
            }
            PanelPage.BackgroundPresets, PanelPage.TextPresets -> closeColourPage(page)
            PanelPage.LicenseText -> moveTo(focus.copy(page = PanelPage.Licenses, panelIndex = focus.licenseIndex))
            PanelPage.RemoveFolder -> backToFolderPage(FolderPageItem.Remove)
            PanelPage.Folder -> backToFolders()
            PanelPage.SkipSubfolder -> backToFolderPage(FolderPageItem.SkipOne)
            PanelPage.ScraperSystems -> backToAccountPage(AccountPageItem.ChooseSystems)
            PanelPage.ScraperConfirm -> backFromBoxArtConfirm()
            PanelPage.ScraperSignOutConfirm -> backToAccountPage(AccountPageItem.SignOut)
            PanelPage.RaSignOutConfirm -> backToRaPage()
            PanelPage.ScraperClearConfirm -> backToAccountPage(AccountPageItem.ClearBoxArt)
            PanelPage.UnknownFolder -> backToFilesNotAdded()
            // B at Undo's own confirm goes back to its first step, on the button it asked about.
            PanelPage.UndoRestore -> focus.undoAsk?.let { moveTo(focus.copy(undoAsk = null, panelIndex = it.ordinal)) } ?: backToBackupRestore(BackupRestoreItem.Undo)
            PanelPage.Backup -> backToBackupRestore(BackupRestoreItem.Backup)
            PanelPage.Restore -> backToBackupRestore(BackupRestoreItem.Restore)
            PanelPage.ResetAllConfirm -> backToReset(ResetPanelItem.All)
            PanelPage.ResetLibraryConfirm -> backToReset(ResetPanelItem.Library)
            else -> closePanel()
        }
    }

    // Accounts › ScreenScraper's box art.

    /**
     * The account page's buttons and the picker's rows follow the run and the library (a refusal,
     * a scan adding a system): the cursor stays on the same action or system when it is still
     * there, else it is kept inside the list, so A never acts on another button.
     */
    override fun focusKeptIn(
        before: SettingsContent,
        after: SettingsContent,
    ): SettingsFocus? {
        val rowKept = SettingsListPages.libraryFocusKept(focus, before, after) ?: SettingsListPages.accountsFocusKept(focus, before, after) ?: SettingsListPages.homeScreenFocusKept(focus, before, after)
        // The row under ScreenScraper's form left: the form goes with it, or the next one would open with its words.
        if (rowKept != null && rowKept.page == null && this.focus.page == PanelPage.ScraperSignIn) signIn.close()
        val focus = rowKept ?: focus
        if (focus.column != SettingsColumn.Panel) return rowKept
        val at = focus.panelIndex
        val next =
            when (focus.page) {
                PanelPage.RaAccount -> raIndexAfter(before, after, at)
                PanelPage.ScraperAccount -> {
                    val now = accountPageItems(after)
                    accountPageItems(before).getOrNull(at)?.let { now.indexOf(it) }?.takeIf { it >= 0 } ?: at.coerceAtMost(now.lastIndex)
                }
                // A backup that starts or ends swaps the buttons: the cursor stays on one of them.
                PanelPage.Backup -> at.coerceAtMost(backupPanelItems(after.backup).size - 1)
                // The way back that left takes Undo's item with it.
                PanelPage.BackupRestore -> at.coerceAtMost(backupRestoreItems(after).size - 1)
                // A check that finds an update puts its button above: Check now and the switch keep the cursor. Each step
                // has its own buttons: from one the cursor goes to the first, so A never lands on another one.
                PanelPage.Update -> {
                    val item = updatesPanelItems(before.update).getOrNull(at)
                    when {
                        item == UpdatePanelItem.CheckNow || item == UpdatePanelItem.CheckDaily -> updatesPanelItems(after.update).indexOf(item)
                        updateStepItems(before.update) == updateStepItems(after.update) -> at
                        else -> 0
                    }
                }
                // Reading, the confirm and the result each have their own buttons: the cursor goes to the first.
                PanelPage.Restore -> {
                    val now = restorePanelItems(after.restore)
                    // The confirm opens on Cancel: a quick double A must not replace the library.
                    if (restorePanelItems(before.restore) == now) at else now.indexOf(RestorePanelItem.Cancel).coerceAtLeast(0)
                }
                PanelPage.UndoRestore -> {
                    // The copy is gone (undone, or forgotten from elsewhere): its page goes with it.
                    if (after.wayBack == null) return focus.copy(page = PanelPage.BackupRestore, panelIndex = backupRestoreItems(after).indexOf(BackupRestoreItem.Restore), undoAsk = null)
                    at.coerceAtMost(undoPanelItems(after, focus.undoAsk).size - 1)
                }
                // A folder's page and its pick: a folder that left goes back to the list.
                PanelPage.Folder, PanelPage.SkipSubfolder, PanelPage.RemoveFolder ->
                    folderIndexKept(focus, before, after) ?: return SettingsListPages.backToFolders(focus, after)
                // The carried system stays under the cursor; otherwise the cursor stays inside the list.
                PanelPage.Systems -> systems.follow(after)?.plus(FIRST_SYSTEM_ITEM) ?: at.coerceAtMost(after.librarySystems.size)
                PanelPage.ScraperSystems -> {
                    val kept =
                        if (at < before.systems.size) {
                            after.systems.indexOfFirst { it.systemId == before.systems[at].systemId }.takeIf { it >= 0 } ?: at
                        } else {
                            after.systems.size + (at - before.systems.size)
                        }
                    kept.coerceAtMost(systemsPickCount(after, focus) - 1)
                }
                PanelPage.BoxArtSource -> {
                    val now = boxArtPanelEntries(after)
                    boxArtPanelEntries(before).getOrNull(at)?.let { now.indexOf(it) }?.takeIf { it >= 0 } ?: at.coerceAtMost(now.lastIndex)
                }
                PanelPage.FilesNotAdded -> SettingsListPages.filesNotAddedLineAfter(at, before, after)
                // A folder that left goes back to the list; while it is there the page stays on the same item.
                PanelPage.UnknownFolder ->
                    if (SettingsListPages.unknownFolderAt(focus, after) == null) return SettingsListPages.backToFilesNotAdded(focus, after) else at
                else -> return rowKept
            }.coerceAtLeast(0)
        return if (next == at) rowKept else focus.copy(panelIndex = next)
    }

    private fun onAccountEnter(item: AccountPageItem?) {
        when (item) {
            AccountPageItem.SignInAgain -> openSignIn((content.account as? AccountStatus.SignedIn)?.username.orEmpty())
            AccountPageItem.DownloadMissing -> {
                work.askAgain(AskAgain.Missing, null, boxArtRow)
                feel.report(Outcome.Activated)
            }
            AccountPageItem.RedownloadAll -> openBoxArtConfirm(null)
            // Asks first: the pictures cannot come back without a download (audit item 21).
            AccountPageItem.ClearBoxArt -> moveTo(focus.copy(page = PanelPage.ScraperClearConfirm, panelIndex = ClearConfirmItem.Cancel.ordinal))
            AccountPageItem.ChooseSystems -> moveTo(focus.copy(page = PanelPage.ScraperSystems, panelIndex = 0, chosenSystems = emptySet()))
            AccountPageItem.PauseOrResume -> {
                work.setBoxArtPaused(!isPausedByPerson(content), boxArtRow)
                feel.report(Outcome.Activated)
            }
            AccountPageItem.BoxArtRegion -> actions.update { withNextBoxArtRegion(it) }.also { feel.report(Outcome.Activated) }
            // Asks first: signing out turns box art off at once.
            AccountPageItem.SignOut -> moveTo(focus.copy(page = PanelPage.ScraperSignOutConfirm, panelIndex = SignOutConfirmItem.Cancel.ordinal))
            null -> closePanel()
        }
    }

    /** Update starts it (or asks first on mobile data); the step's buttons answer its question; Cancel stops it; Check now checks, whatever the switch and the last check say. */
    private fun onUpdateEnter(item: UpdatePanelItem?) {
        when (item) {
            UpdatePanelItem.Update -> actions.updates.startUpdate(allowMetered = false)
            UpdatePanelItem.DownloadAnyway -> actions.updates.startUpdate(allowMetered = true)
            UpdatePanelItem.AllowInstalls -> actions.updates.openInstallPermission()
            UpdatePanelItem.Cancel -> actions.updates.cancel()
            UpdatePanelItem.CheckNow -> actions.updates.checkNow()
            // From the stored value inside the write, like every switch.
            UpdatePanelItem.CheckDaily -> actions.update { SettingsOption.CheckUpdatesDaily.toggled(it) }
            null -> return
        }
        feel.report(Outcome.Activated)
    }

    /** An item of Back up and restore's list: its own panel, as it always was. */
    private fun onBackupRestoreEnter(item: BackupRestoreItem?) {
        val page =
            when (item) {
                BackupRestoreItem.Backup -> PanelPage.Backup
                BackupRestoreItem.Restore -> PanelPage.Restore
                BackupRestoreItem.Undo -> PanelPage.UndoRestore
                null -> return
            }
        // A confirm left waiting opens on Cancel too: a quick double A must not replace the library.
        val first = if (page == PanelPage.Restore) restorePanelItems(content.restore).indexOf(RestorePanelItem.Cancel).coerceAtLeast(0) else 0
        moveTo(focus.copy(page = page, panelIndex = first, undoAsk = null))
    }

    private fun backToBackupRestore(on: BackupRestoreItem) {
        // Undo's item leaves with the copy: the cursor stays inside the list.
        val items = backupRestoreItems(content)
        moveTo(focus.copy(page = PanelPage.BackupRestore, panelIndex = items.indexOf(on).takeIf { it >= 0 } ?: items.lastIndex, undoAsk = null))
    }

    /** An item of Reset…'s list: its own confirm, the cursor on Cancel. */
    private fun onResetEnter(item: ResetPanelItem?) {
        val page =
            when (item) {
                ResetPanelItem.All -> PanelPage.ResetAllConfirm
                ResetPanelItem.Library -> PanelPage.ResetLibraryConfirm
                null -> return
            }
        doneOption = null
        moveTo(focus.copy(page = page, panelIndex = SettingsModel.CONFIRM_CANCEL))
    }

    private fun backToReset(on: ResetPanelItem) = moveTo(focus.copy(page = PanelPage.Reset, panelIndex = on.ordinal))

    /** Cancel or Reset at [item]'s confirm: back on its item, where a reset says how it went. */
    private fun onResetConfirmEnter(
        item: ResetPanelItem,
        index: Int,
    ) {
        backToReset(item)
        if (index != SettingsModel.CONFIRM_RESET) return
        when (item) {
            ResetPanelItem.All -> {
                custom.forgetWritten()
                actions.resetAllSettings()
            }
            ResetPanelItem.Library -> actions.resetLibrary()
        }
        doneOption = item.option
    }

    /** Settings or Everything opens the save picker (the backup starts with its answer); Cancel stops the running one. */
    private fun onBackupEnter(item: BackupPanelItem?) {
        when (item) {
            BackupPanelItem.Settings -> work.startBackup(BackupKind.Settings)
            BackupPanelItem.Everything -> work.startBackup(BackupKind.Everything)
            BackupPanelItem.Cancel -> actions.backup.cancel()
            null -> return
        }
        feel.report(Outcome.Activated)
    }

    /** Choose file… opens the open picker; Cancel stops the reading or drops the confirm; Restore replaces. */
    private fun onRestoreEnter(item: RestorePanelItem?) {
        when (item) {
            RestorePanelItem.ChooseFile -> work.startRestore()
            RestorePanelItem.Cancel -> actions.restore.cancel()
            RestorePanelItem.Restore -> {
                // The backup's colours replace the ones the colour panels wrote: those must not come back.
                custom.forgetWritten()
                actions.restore.confirm()
            }
            null -> return
        }
        feel.report(Outcome.Activated)
    }

    /**
     * Undo restore in two steps: Undo the restore… or Forget the copy… asks again, saying what is
     * lost, with the cursor on Cancel; only the second A does it.
     */
    private fun onUndoEnter(item: UndoPanelItem?) {
        val ask = focus.undoAsk
        when (item) {
            UndoPanelItem.Undo, UndoPanelItem.Forget -> moveTo(focus.copy(undoAsk = item, panelIndex = undoPanelItems(content, item).indexOf(UndoPanelItem.Cancel)))
            UndoPanelItem.Cancel -> moveTo(focus.copy(undoAsk = null, panelIndex = ask?.ordinal ?: 0))
            UndoPanelItem.Confirm ->
                if (ask == UndoPanelItem.Forget) {
                    actions.restore.forgetCopy()
                    backToBackupRestore(BackupRestoreItem.Undo)
                } else {
                    custom.forgetWritten()
                    actions.restore.undo()
                    moveTo(focus.copy(undoAsk = null, panelIndex = 0))
                }
            null -> return
        }
        feel.report(Outcome.Activated)
    }

    protected fun systemAtFocus(): SystemEmulators? = SettingsListPages.systemAt(focus, content)

    /**
     * Every press on Library › Systems. Carrying: Up / Down move the system (a held key repeats
     * through the router), L1 / R1 take it to the top / bottom, A drops it, B puts it back.
     * Otherwise: Up / Down move the cursor, A picks the system up, Y hides or shows it, B leaves. On
     * the System order item above the systems (Up from the first system) Left / Right step it, stopping at the ends, never while held, and A steps it, wrapping.
     */
    protected fun onSystemsAction(
        action: Action,
        settings: PlumSettings,
        geometry: SettingsGeometry,
    ) {
        val rows = systems.rows(content, settings)
        if (rows.isEmpty()) return closePanel()
        if (systems.isCarrying) {
            when (action) {
                is Action.Move ->
                    when (action.direction) {
                        Direction.Up -> carrySystemTo(focusedSystemIndex - 1)
                        Direction.Down -> carrySystemTo(focusedSystemIndex + 1)
                        else -> Unit
                    }
                Action.PreviousSystem -> carrySystemTo(0)
                Action.NextSystem -> carrySystemTo(rows.lastIndex)
                Action.Enter, Action.Play -> dropSystem()
                Action.Back -> cancelSystemCarry()
                else -> Unit
            }
            return
        }
        val onOrder = focus.panelIndex == SYSTEM_ORDER_ITEM
        when (action) {
            is Action.Move ->
                when {
                    // A held key steps nothing: only a press of its own changes the order.
                    onOrder && (action.direction == Direction.Left || action.direction == Direction.Right) ->
                        if (!action.isRepeat) stepSystemOrder(if (action.direction == Direction.Left) -1 else +1, wrap = false, settings, geometry)
                    else -> movePanel(PanelPage.Systems, action.direction, settings, geometry)
                }
            Action.Enter, Action.Play -> if (onOrder) stepSystemOrder(+1, wrap = true, settings, geometry) else pickUpSystem(rows, settings)
            Action.ToggleFavorite -> if (!onOrder) toggleSystemHidden(rows, settings)
            Action.Back -> onPanelBack(PanelPage.Systems)
            else -> Unit
        }
    }

    /** The system row under the cursor, counted among the systems (the panel's items start with System order). */
    protected val focusedSystemIndex: Int get() = focus.panelIndex - FIRST_SYSTEM_ITEM

    /** System order's next value: the order the panel held after a drop gives way, and "Order set to Manual" stops being true. */
    protected fun stepSystemOrder(
        step: Int,
        wrap: Boolean,
        settings: PlumSettings,
        geometry: SettingsGeometry,
    ) {
        systems.leave()
        work.unsay(SettingsNotice.SystemOrderNowManual)
        stepChoice(SettingsOption.SystemOrder, step, wrap, settings, geometry)
    }

    protected fun stepChoice(
        option: SettingsOption,
        step: Int,
        wrap: Boolean,
        settings: PlumSettings,
        geometry: SettingsGeometry,
    ) {
        val count = option.choiceCount(settings, content.sortValues)
        val raw = option.choiceIndex(settings, geometry.defaultTextSize, content.sortValues) + step
        // At the end of a list a press changes nothing, and says nothing. The Sounds row is silent itself: Off must not
        // click, and the screen plays one confirm at the new volume once the level is stored (SettingsScreen).
        if (option == SettingsOption.Sounds) {
            feel.hush()
        } else if (wrap || raw in 0 until count) {
            feel.report(if (cause == Cause.Move) Outcome.Moved else Outcome.Activated)
        }
        // A set picked by stepping replaces whatever the colour panels wrote before.
        if (option == SettingsOption.Colors) custom.forgetWritten()
        // From the stored value inside the write: presses faster than the store answers each count.
        actions.update { stored ->
            val raw = option.choiceIndex(stored, geometry.defaultTextSize, content.sortValues) + step
            // Colors has one value more while there is a custom look: count what is stored too.
            val storedCount = option.choiceCount(stored, content.sortValues)
            option.withChoice(stored, if (wrap) Math.floorMod(raw, storedCount) else raw.coerceIn(0, storedCount - 1), content.sortValues)
        }
    }

    /**
     * Picks up the focused system. With A–Z or Maker & year in force the order becomes Manual,
     * starting from the order on screen, and the panel says so once.
     */
    protected fun pickUpSystem(
        rows: List<LibrarySystem>,
        settings: PlumSettings,
    ) {
        val carry = systems.pickUp(rows, focusedSystemIndex.coerceIn(0, rows.lastIndex)) ?: return
        if (focusedSystemIndex != carry.fromIndex) moveTo(focus.copy(panelIndex = FIRST_SYSTEM_ITEM + carry.fromIndex))
        if (settings.library.systemOrder != SystemOrderSetting.Manual) {
            val shown = carry.order
            actions.update { stored -> stored.withManualSystems(shown) }
            work.say(SettingsOption.Systems, SettingsNotice.SystemOrderNowManual)
        }
        feel.report(Outcome.Activated)
    }

    protected fun carrySystemTo(index: Int) {
        val to = systems.carryTo(index) ?: return
        moveTo(focus.copy(panelIndex = FIRST_SYSTEM_ITEM + to))
    }

    /** A: the system stays where it is now; the whole order is stored as Manual. */
    protected fun dropSystem() {
        val order = systems.drop(content) ?: return
        actions.update { stored -> stored.withManualSystems(order) }
        feel.report(Outcome.Activated)
    }

    /** B: the system goes back to where it was picked up; nothing more is stored. */
    protected fun cancelSystemCarry() {
        val at = systems.cancel() ?: return
        if (at != focusedSystemIndex) moveTo(focus.copy(panelIndex = FIRST_SYSTEM_ITEM + at))
        feel.report(Outcome.WentBack)
    }

    /** Y: hides the focused system, or shows it again; the last one shown is never hidden. */
    protected fun toggleSystemHidden(
        rows: List<LibrarySystem>,
        settings: PlumSettings,
    ) {
        val system = rows.getOrNull(focusedSystemIndex) ?: return
        val present = content.librarySystems.map { it.id }
        val hidden = settings.library.hiddenSystems
        if (system.id !in hidden && present.count { it !in hidden } <= 1) {
            work.say(SettingsOption.Systems, SettingsNotice.LastSystemShown)
            feel.report(Outcome.Activated)
            return
        }
        // From the stored set inside the write: quick presses all count, and two of them can never hide the last two.
        actions.update { stored ->
            val now = stored.library.hiddenSystems
            val next = if (system.id in now) now - system.id else now + system.id
            if (present.none { it !in next }) stored else stored.copy(library = stored.library.copy(hiddenSystems = next))
        }
        work.unsay(SettingsNotice.LastSystemShown)
        feel.report(Outcome.Activated)
    }

    /** Leaving a page stops the hex field; a custom page left without A puts back its opening colour. */
    override fun onFocusChanging(next: SettingsFocus) {
        if (focus.page == PanelPage.Systems && next.page != PanelPage.Systems) systems.leave()
        if (next.page != focus.page || next.panelIndex != focus.panelIndex) custom.stopEditingHex()
        val leaving = focus.page
        if (next.page != leaving && leaving != null && SettingsCustomColor.isCustomPage(leaving)) custom.leave(leaving)
    }
}

/** These settings with the system order Manual and [arranged] (every system with games, top to bottom) as its list ([SystemOrder.manualAfterMove]). */
internal fun PlumSettings.withManualSystems(arranged: List<String>): PlumSettings =
    copy(library = library.copy(systemOrder = SystemOrderSetting.Manual, manualSystemOrder = SystemOrder.manualAfterMove(arranged, library.manualSystemOrder)))

/** Pages of two buttons in one row of [PanelGrid]. */
private val TWO_BUTTON_PAGES = setOf(PanelPage.Confirm, PanelPage.ResetAllConfirm, PanelPage.ResetLibraryConfirm, PanelPage.RemoveFolder)
