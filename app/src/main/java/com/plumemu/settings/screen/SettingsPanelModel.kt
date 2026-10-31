package com.plumemu.settings.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.plumemu.account.AccountStatus
import com.plumemu.content.SystemOrder
import com.plumemu.coredata.backup.BackupKind
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SystemOrderSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.theme.Rgb
import com.plumemu.library.scrape.AskAgain
import com.plumemu.settings.withColors
import com.plumemu.sound.Outcome

/**
 * What the panel column does with presses: colour pages, licences, folders, emulators, the confirm
 * step, Fix name…. [SettingsModel] adds the sections and options columns on top.
 */
internal abstract class SettingsPanelModel(
    protected val actions: SettingsActions,
    initialFocus: SettingsFocus,
) : SettingsFolderModel(initialFocus, SettingsWork(actions)) {
    protected val custom = SettingsCustomColor(actions)

    /** The "+" page's check: the colour it opened with, then the one chosen. */
    val selectedColour: Rgb? get() = custom.selectedColour

    /** The swatches the "+" page draws and the cursor walks, fixed when it opened. */
    fun customPalette(page: PanelPage): List<Rgb> = custom.palette(page)

    /** Up (-1) and down (+1) presses in an open licence text; the screen scrolls by them. */
    var licenseScroll: Int by mutableIntStateOf(0)
        protected set

    /** Whether the open licence text can scroll that way; the page sets it (it knows its size). */
    var licenseCanScroll: (down: Boolean) -> Boolean = { true }

    /** A on the hex row: typing starts, with the keyboard Settings › Controls › Keyboard names now. */
    protected fun startEditingHex() {
        custom.startEditingHex(focus.page ?: return, keyboardChoice)
    }

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
        if (page == PanelPage.LicenseText) {
            when (direction) {
                Direction.Up -> licenseScroll--
                Direction.Down -> licenseScroll++
                else -> return
            }
            if (licenseCanScroll(direction == Direction.Down)) feel.report(Outcome.Moved)
            return
        }
        if (SettingsCustomColor.isCustomPage(page)) return moveInCustomPage(page, direction)
        val grid = PanelGrid.of(page, geometry, content, focus, settings, custom.palette(page).size, pickableSubfolders(work.subfolders, content).size) ?: return
        val next = grid.move(focus.panelIndex, direction)
        if (next != focus.panelIndex) return focusPanelItem(next)
        if (direction == Direction.Left && (page == PanelPage.ColorSets || ColorPages.kindOf(page) != null)) leaveColourPageToItsRow(page)
    }

    /** The "+" page: the swatch grid, then the sliders (Left and Right step the focused one), then the hex row. */
    private fun moveInCustomPage(
        page: PanelPage,
        direction: Direction,
    ) {
        val index = focus.panelIndex
        val slider = custom.sliderAt(page, index)
        if (slider != null) {
            // A slider keeps Left and Right: at its start Left does nothing, it never leaves the page.
            val steps = if (direction == Direction.Left) -1 else if (direction == Direction.Right) 1 else 0
            if (steps != 0) {
                if (custom.step(page, slider, steps)) feel.report(Outcome.Moved)
                return
            }
        }
        val next = custom.nextIndex(page, index, direction)
        if (next != index) return focusPanelItem(next)
        // Left leaves from the grid's leftmost tiles and from the hex row only.
        val leftmost = index < custom.palette(page).size && index % ColorPages.PALETTE_COLUMNS == 0
        if (direction == Direction.Left && (leftmost || custom.isHexRow(page, index))) leaveColourPageToItsRow(page)
    }

    /**
     * Left from the leftmost item of a colour page goes back to its row in the options column, as B
     * does (a custom page first returns to its presets page): the colour the page opened with is put
     * back unless one was chosen.
     */
    private fun leaveColourPageToItsRow(page: PanelPage) {
        onPanelBack(page)
        val now = focus.page
        if (now != null && now != page && focus.column == SettingsColumn.Panel) onPanelBack(now)
    }

    /**
     * Focus panel item [index]. On a colour page its colour applies at once (the ring marks the
     * current one); on a custom page that is a preview, and the hex row puts back the colour the
     * page opened with.
     */
    protected fun focusPanelItem(index: Int) {
        val page = focus.page ?: return
        moveTo(focus.copy(panelIndex = index))
        custom.focus(page, index)
    }

    /** Opens a custom page with the cursor on the swatch nearest the current colour; applies nothing, so A before any move keeps the current colour. */
    protected fun enterCustomPage(
        page: PanelPage,
        settings: PlumSettings,
    ) {
        val at = custom.open(page, settings)
        moveTo(focus.copy(column = SettingsColumn.Panel, page = page, panelIndex = at))
    }

    protected fun onPanelEnter(settings: PlumSettings) {
        val page = focus.page ?: return
        val index = focus.panelIndex
        when (page) {
            PanelPage.ColorSets -> useColorSet(index, settings)
            PanelPage.BackgroundPresets, PanelPage.AccentPresets, PanelPage.TextPresets ->
                if (page == PanelPage.TextPresets && custom.textBlocked) {
                    // Nothing reads on this background: the panel says so and offers nothing to pick.
                    feel.report(Outcome.Nothing)
                } else if (index == custom.presetCount(page) - 1) {
                    enterCustomPage(ColorPages.customOf(ColorPages.kindOf(page) ?: return), settings)
                } else if (custom.lastWrite?.refusal != null) {
                    // The colour under the cursor does not read: A stays here, with the reason shown.
                    feel.report(Outcome.Nothing)
                } else {
                    closePanel()
                }
            PanelPage.BackgroundCustom, PanelPage.AccentCustom, PanelPage.TextCustom ->
                if (isHexRow(page, index)) {
                    startEditingHex()
                    feel.report(Outcome.Activated)
                } else {
                    // A chooses the swatch: nothing to put back.
                    custom.choose(page, null)
                    closePanel()
                }
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
            PanelPage.UnidentifiedGames -> openNameFix(index)
            PanelPage.FixName, PanelPage.ScraperSignIn, PanelPage.ProblemReport -> Unit
            PanelPage.ScraperAccount -> onAccountEnter(accountPageItems(content).getOrNull(index))
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
                val option = focusedOption
                closePanel()
                if (index == SettingsModel.CONFIRM_RESET && option != null) {
                    if (option == SettingsOption.ClearRecent) {
                        work.clearRecent()
                    } else if (option == SettingsOption.ResetAll) {
                        custom.forgetWritten()
                        actions.resetAllSettings()
                    } else {
                        actions.resetLibrary()
                    }
                    doneOption = option
                }
            }
            PanelPage.Backup -> onBackupEnter(backupPanelItems(content.backup).getOrNull(index))
            PanelPage.Update -> onUpdateEnter(updatePanelItems(content.update).getOrNull(index))
            PanelPage.Restore -> onRestoreEnter(restorePanelItems(content.restore).getOrNull(index))
            PanelPage.UndoRestore -> onUndoEnter(undoPanelItems(content, focus.undoAsk).getOrNull(index))
            PanelPage.Licenses -> moveTo(focus.copy(page = PanelPage.LicenseText, licenseIndex = index)).also { licenseScroll = 0 }
            PanelPage.LicenseText -> Unit
            PanelPage.ReadOnly -> closePanel()
        }
    }

    /**
     * Colors' tile [index]: that set (or Custom), through the row's own write ([withColors], so the
     * person's own colours are kept exactly as the row keeps them), then back to the row. The entry
     * is taken from what this panel shows; the write reads the store, so a quick second press
     * picking what is already current changes nothing.
     */
    private fun useColorSet(
        index: Int,
        settings: PlumSettings,
    ) {
        val entries = colorSetsPanelEntries(settings)
        if (index !in entries.indices) return closePanel()
        val entry = entries[index]
        custom.forgetWritten()
        actions.update { stored -> stored.withAppearance { it.withColors(entry) } }
        closePanel()
    }

    protected fun onPanelBack(page: PanelPage) {
        when (page) {
            PanelPage.BackgroundCustom, PanelPage.AccentCustom, PanelPage.TextCustom -> {
                val presets = ColorPages.presetsOf(ColorPages.kindOf(page) ?: return)
                moveTo(focus.copy(page = presets, panelIndex = custom.presetCount(presets) - 1))
            }
            PanelPage.LicenseText -> moveTo(focus.copy(page = PanelPage.Licenses, panelIndex = focus.licenseIndex))
            PanelPage.RemoveFolder -> backToFolderPage(FolderPageItem.Remove)
            PanelPage.Folder -> backToFolders()
            PanelPage.SkipSubfolder -> backToFolderPage(FolderPageItem.SkipOne)
            PanelPage.ScraperSystems -> backToAccountPage(AccountPageItem.ChooseSystems)
            PanelPage.ScraperConfirm -> backFromBoxArtConfirm()
            PanelPage.ScraperSignOutConfirm -> backToAccountPage(AccountPageItem.SignOut)
            PanelPage.ScraperClearConfirm -> backToAccountPage(AccountPageItem.ClearBoxArt)
            // B at Undo's own confirm goes back to its first step, on the button it asked about.
            PanelPage.UndoRestore -> focus.undoAsk?.let { moveTo(focus.copy(undoAsk = null, panelIndex = it.ordinal)) } ?: closePanel()
            else -> closePanel()
        }
    }

    /**
     * A on Library › an unknown folder: item 0 is Ignore this folder, the others the systems. Applies
     * to the folder the panel opened for (by id), whatever the list does meanwhile; a folder that is
     * gone closes the panel. A second A while the answer is being saved does nothing.
     */
    private fun onUnknownFolderEnter(index: Int) {
        val key = focus.folderKey ?: return closePanel()
        if (content.unknownFolders.none { it.key == key }) return closePanel()
        val systemId = if (index == 0) null else (content.folderSystemChoices.getOrNull(index - 1) ?: return).id
        if (!work.chooseFolderSystem(key, systemId, content)) return feel.report(Outcome.Nothing)
        feel.report(Outcome.Activated)
        closePanel()
    }

    // Library › ScreenScraper's box art.

    /**
     * The account page's buttons and the picker's rows follow the run and the library (a refusal,
     * a scan adding a system): the cursor stays on the same action or system when it is still
     * there, else it is kept inside the list, so A never acts on another button.
     */
    override fun focusKeptIn(
        before: SettingsContent,
        after: SettingsContent,
    ): SettingsFocus? {
        aboutFocusKept(focus, before, after)?.let { return it }
        val libraryKept = SettingsListPages.libraryFocusKept(focus, before, after)
        val focus = libraryKept ?: focus
        if (focus.column != SettingsColumn.Panel) return libraryKept
        val at = focus.panelIndex
        val next =
            when (focus.page) {
                PanelPage.ScraperAccount -> {
                    val now = accountPageItems(after)
                    accountPageItems(before).getOrNull(at)?.let { now.indexOf(it) }?.takeIf { it >= 0 } ?: at.coerceAtMost(now.lastIndex)
                }
                // A backup that starts or ends swaps the buttons: the cursor stays on one of them.
                PanelPage.Backup -> at.coerceAtMost(backupPanelItems(after.backup).size - 1)
                // Each step has its own buttons: the cursor goes to the first, so A never lands on another one.
                PanelPage.Update -> if (updatePanelItems(before.update) == updatePanelItems(after.update)) at else 0
                // Reading, the confirm and the result each have their own buttons: the cursor goes to the first.
                PanelPage.Restore -> {
                    val now = restorePanelItems(after.restore)
                    // The confirm opens on Cancel: a quick double A must not replace the library.
                    if (restorePanelItems(before.restore) == now) at else now.indexOf(RestorePanelItem.Cancel).coerceAtLeast(0)
                }
                PanelPage.UndoRestore -> at.coerceAtMost(undoPanelItems(after, focus.undoAsk).size - 1)
                // A folder's page and its pick: a folder that left goes back to the list.
                PanelPage.Folder, PanelPage.SkipSubfolder, PanelPage.RemoveFolder ->
                    folderIndexKept(focus, before, after) ?: return SettingsListPages.backToFolders(focus, after)
                // The carried system stays under the cursor; otherwise the cursor stays inside the list.
                PanelPage.Systems -> systems.follow(after) ?: at.coerceAtMost(after.librarySystems.size - 1)
                PanelPage.ScraperSystems -> {
                    val kept =
                        if (at < before.systems.size) {
                            after.systems.indexOfFirst { it.systemId == before.systems[at].systemId }.takeIf { it >= 0 } ?: at
                        } else {
                            after.systems.size + (at - before.systems.size)
                        }
                    kept.coerceAtMost(systemsPickCount(after, focus) - 1)
                }
                else -> return libraryKept
            }.coerceAtLeast(0)
        return if (next == at) libraryKept else focus.copy(panelIndex = next)
    }

    private fun onAccountEnter(item: AccountPageItem?) {
        when (item) {
            AccountPageItem.SignInAgain -> openSignIn((content.account as? AccountStatus.SignedIn)?.username.orEmpty())
            AccountPageItem.DownloadMissing -> {
                work.askAgain(AskAgain.Missing, null)
                feel.report(Outcome.Activated)
            }
            AccountPageItem.RedownloadAll -> openBoxArtConfirm(null)
            // Asks first: the pictures cannot come back without a download (audit item 21).
            AccountPageItem.ClearBoxArt -> moveTo(focus.copy(page = PanelPage.ScraperClearConfirm, panelIndex = ClearConfirmItem.Cancel.ordinal))
            AccountPageItem.ChooseSystems -> moveTo(focus.copy(page = PanelPage.ScraperSystems, panelIndex = 0, chosenSystems = emptySet()))
            AccountPageItem.PauseOrResume -> {
                work.setBoxArtPaused(!isPausedByPerson(content))
                feel.report(Outcome.Activated)
            }
            // Asks first: signing out turns box art off at once.
            AccountPageItem.SignOut -> moveTo(focus.copy(page = PanelPage.ScraperSignOutConfirm, panelIndex = SignOutConfirmItem.Cancel.ordinal))
            null -> closePanel()
        }
    }

    /** Update starts it (or asks first on mobile data); the step's buttons answer its question; Cancel stops it. */
    private fun onUpdateEnter(item: UpdatePanelItem?) {
        when (item) {
            UpdatePanelItem.Update -> actions.updates.startUpdate(allowMetered = false)
            UpdatePanelItem.DownloadAnyway -> actions.updates.startUpdate(allowMetered = true)
            UpdatePanelItem.AllowInstalls -> actions.updates.openInstallPermission()
            UpdatePanelItem.Cancel -> actions.updates.cancel()
            null -> return
        }
        feel.report(Outcome.Activated)
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
                    closePanel()
                } else {
                    custom.forgetWritten()
                    actions.restore.undo()
                    moveTo(focus.copy(undoAsk = null, panelIndex = 0))
                }
            null -> return
        }
        feel.report(Outcome.Activated)
    }

    /** A system's checkmark flips; the two actions work on the systems checked. */
    private fun onSystemsEnter(index: Int) {
        val chosen = chosenSystemsIn(content, focus)
        when (systemsPickActionAt(content, index)) {
            null -> {
                val system = content.systems.getOrNull(index) ?: return
                val next = if (system.systemId in chosen) chosen - system.systemId else chosen + system.systemId
                moveTo(focus.copy(chosenSystems = next))
            }
            SystemsPickAction.DownloadMissing -> {
                if (chosen.isEmpty()) return
                work.askAgain(AskAgain.Missing, chosen)
                cause = Cause.Enter
                backToAccountPage(AccountPageItem.ChooseSystems)
            }
            SystemsPickAction.Redownload -> if (chosen.isNotEmpty()) openBoxArtConfirm(chosen)
        }
    }

    private fun openBoxArtConfirm(systems: Set<String>?) =
        moveTo(focus.copy(page = PanelPage.ScraperConfirm, panelIndex = BoxArtConfirmItem.Cancel.ordinal, redownloadSystems = systems))

    private fun onBoxArtConfirmEnter(item: BoxArtConfirmItem?) {
        if (item == BoxArtConfirmItem.Redownload) {
            work.askAgain(AskAgain.All, focus.redownloadSystems)
            cause = Cause.Enter
            backToAccountPage(if (focus.redownloadSystems == null) AccountPageItem.RedownloadAll else AccountPageItem.ChooseSystems)
        } else {
            backFromBoxArtConfirm()
        }
    }

    private fun onClearConfirmEnter(item: ClearConfirmItem?) {
        if (item == ClearConfirmItem.Clear) {
            work.clearBoxArt()
            cause = Cause.Enter
        }
        backToAccountPage(AccountPageItem.ClearBoxArt)
    }

    private fun onSignOutConfirmEnter(item: SignOutConfirmItem?) {
        if (item == SignOutConfirmItem.SignOut) {
            work.signOut()
            closePanel()
        } else {
            backToAccountPage(AccountPageItem.SignOut)
        }
    }

    /** Back or Cancel: to the button that opened it (Re-download all, or the picker's Re-download). */
    private fun backFromBoxArtConfirm() {
        val systems = focus.redownloadSystems ?: return backToAccountPage(AccountPageItem.RedownloadAll)
        val at = content.systems.size + SystemsPickAction.Redownload.ordinal
        moveTo(focus.copy(page = PanelPage.ScraperSystems, panelIndex = at, chosenSystems = systems, redownloadSystems = null))
    }

    private fun backToAccountPage(on: AccountPageItem) {
        val index = accountPageItems(content).indexOf(on).coerceAtLeast(0)
        moveTo(focus.copy(page = PanelPage.ScraperAccount, panelIndex = index, chosenSystems = emptySet(), redownloadSystems = null))
    }

    protected fun systemAtFocus(): SystemEmulators? = SettingsListPages.systemAt(focus, content)

    /**
     * Every press on Library › Systems. Carrying: Up / Down move the system (a held key repeats
     * through the router), L1 / R1 take it to the top / bottom, A drops it, B puts it back.
     * Otherwise: Up / Down move the cursor, A picks the system up, Y hides or shows it, B leaves.
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
                        Direction.Up -> carrySystemTo(focus.panelIndex - 1)
                        Direction.Down -> carrySystemTo(focus.panelIndex + 1)
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
        when (action) {
            is Action.Move -> movePanel(PanelPage.Systems, action.direction, settings, geometry)
            Action.Enter, Action.Play -> pickUpSystem(rows, settings)
            Action.ToggleFavorite -> toggleSystemHidden(rows, settings)
            Action.Back -> onPanelBack(PanelPage.Systems)
            else -> Unit
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
        val carry = systems.pickUp(rows, focus.panelIndex.coerceIn(0, rows.lastIndex)) ?: return
        if (focus.panelIndex != carry.fromIndex) moveTo(focus.copy(panelIndex = carry.fromIndex))
        if (settings.library.systemOrder != SystemOrderSetting.Manual) {
            val shown = carry.order
            actions.update { stored -> stored.withManualSystems(shown) }
            work.say(SettingsOption.Systems, SettingsNotice.SystemOrderNowManual)
        }
        feel.report(Outcome.Activated)
    }

    protected fun carrySystemTo(index: Int) {
        val to = systems.carryTo(index) ?: return
        moveTo(focus.copy(panelIndex = to))
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
        if (at != focus.panelIndex) moveTo(focus.copy(panelIndex = at))
        feel.report(Outcome.WentBack)
    }

    /** Y: hides the focused system, or shows it again; the last one shown is never hidden. */
    protected fun toggleSystemHidden(
        rows: List<LibrarySystem>,
        settings: PlumSettings,
    ) {
        val system = rows.getOrNull(focus.panelIndex) ?: return
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

    fun isHexRow(
        page: PanelPage,
        index: Int,
    ): Boolean = custom.isHexRow(page, index)
}

/** These settings with the system order Manual and [arranged] (every system with games, top to bottom) as its list ([SystemOrder.manualAfterMove]). */
internal fun PlumSettings.withManualSystems(arranged: List<String>): PlumSettings =
    copy(library = library.copy(systemOrder = SystemOrderSetting.Manual, manualSystemOrder = SystemOrder.manualAfterMove(arranged, library.manualSystemOrder)))
