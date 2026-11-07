package com.plumemu.settings.screen

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.plumemu.account.RA_KEY_PAGE_URL
import com.plumemu.account.SignInForm
import com.plumemu.account.SignInKind
import com.plumemu.account.SignInStep
import com.plumemu.coreinput.Action
import com.plumemu.coreui.components.keyboard.KeyboardEvent
import com.plumemu.game.TitleList
import com.plumemu.game.TypingState
import com.plumemu.game.onKeyboardEvent
import com.plumemu.report.ProblemReportForm
import com.plumemu.report.ReportStep
import com.plumemu.keyboard.KeyboardChoice
import com.plumemu.sound.Feel
import com.plumemu.sound.Outcome

/**
 * Where Settings' cursor is and the moves every column shares: into and out of the options, L1 /
 * R1, closing a panel. [SettingsModel] decides what presses do on top of it.
 */
@Stable
internal abstract class SettingsNavigation(
    initialFocus: SettingsFocus,
    /** Removals, detection and the plain line of the option that tried something. */
    val work: SettingsWork,
) {
    var focus: SettingsFocus by mutableStateOf(initialFocus)
        private set

    /** Folders, scan, systems' emulators, home app: set by the screen whenever they change. */
    var content: SettingsContent
        get() = shownContent
        set(value) {
            val before = shownContent
            shownContent = value
            // Not a move: no sound, only the same item kept under the cursor. The first content is
            // what the focus was made for (the placeholder before it has no rows to follow).
            if (hasContent && before != value) focusKeptIn(before, value)?.let { focus = it }
            // A section that left (Second screen with its device, Emulators with the last game): the focus goes to the section above it.
            // Not moveTo: no move sound for a change nobody made, and these sections open no page that onFocusChanging must leave.
            val shown = SettingsSection.shownIn(value)
            if (focus.section !in shown) {
                val above = SettingsSection.entries.take(focus.section.ordinal).lastOrNull { it in shown } ?: shown.first()
                focus = focus.copy(column = SettingsColumn.Sections, section = above, page = null, panelIndex = 0)
            }
            // "Already reading your games" stays under the progress line until the scan ends.
            if (before.isScanning && !value.isScanning) work.clearScanBusyNotice()
            markResultsSeen(focus.page)
            hasContent = true
        }

    private var shownContent: SettingsContent by mutableStateOf(SettingsContent())
    private var hasContent = false

    /** The focus after [before] became [after] while a panel page is open, when it must move to stay on its item; null to leave it. */
    protected open fun focusKeptIn(
        before: SettingsContent,
        after: SettingsContent,
    ): SettingsFocus? = null

    /** The option whose reset just ran: its panel says it is done until the focus leaves it. */
    var doneOption: SettingsOption? by mutableStateOf(null)
        protected set

    /** The focused (or remembered) option of the focused section in [content]. */
    val focusedOption: SettingsOption? get() = focus.optionIn(content)

    /** The option whose panel shows: the focused row's, or the item of Colors', Back up and restore's or Reset…'s panel whose page is up. */
    val panelOption: SettingsOption?
        get() =
            when (focus.page) {
                PanelPage.BackgroundPresets, PanelPage.BackgroundCustom -> SettingsOption.Background
                PanelPage.TextPresets, PanelPage.TextCustom -> SettingsOption.TextColor
                PanelPage.Backup -> SettingsOption.Backup
                PanelPage.Restore -> SettingsOption.Restore
                PanelPage.UndoRestore -> SettingsOption.UndoRestore
                PanelPage.ResetAllConfirm -> SettingsOption.ResetAll
                PanelPage.ResetLibraryConfirm -> SettingsOption.ResetLibrary
                else -> focusedOption
            }

    /** The focused row of the section in [content]. */
    val optionIndex: Int get() = focus.optionIndexIn(content)

    /** Called before the focus becomes [next]. */
    protected abstract fun onFocusChanging(next: SettingsFocus)

    protected fun rows(): List<SettingsOption> = focus.section.rowsIn(content)

    protected fun closePanel() = moveTo(focus.copy(column = SettingsColumn.Options, page = null, panelIndex = 0))

    protected fun enterOptions() {
        // A section with nothing that works yet stays in the sections column.
        if (rows().isEmpty()) return
        moveTo(focus.copy(column = SettingsColumn.Options, page = null))
    }

    protected fun backToSections() = moveTo(focus.copy(column = SettingsColumn.Sections, page = null))

    protected fun focusOption(index: Int) {
        // Already on that row: the first row of a section has no remembered entry, which would read as a move.
        if (focus.column == SettingsColumn.Options && focus.page == null && (focus.rememberedOption[focus.section] ?: 0) == index) return
        moveTo(focus.copy(column = SettingsColumn.Options, page = null, rememberedOption = focus.rememberedOption + (focus.section to index)))
    }

    /** L1 / R1: the previous or next section; from the options, to that section's options (one with none is skipped). */
    protected fun changeSection(
        step: Int,
        column: SettingsColumn,
    ) {
        val sections = SettingsSection.shownIn(content)
        var at = sections.indexOf(focus.section) + step
        if (column == SettingsColumn.Options) {
            while (at in sections.indices && sections[at].rowsIn(content).isEmpty()) at += step
        }
        val next = sections.getOrNull(at) ?: return
        moveTo(focus.copy(column = column, section = next, page = null))
    }

    /** Library › Unidentified games › Fix name…. */
    val nameFix = SettingsNameFix()

    /**
     * A or a tap on unidentified game [index]: Fix name… opens at once ("Reading the names…" the
     * first time per system) and gets its list when it is read, if the page is still open for it.
     */
    protected fun openNameFix(index: Int) {
        val game = content.unidentifiedGames.getOrNull(index) ?: return
        val opened = nameFix.showLoading(game, index, keyboardChoice)
        moveTo(focus.copy(page = PanelPage.FixName))
        work.titleList(game.id) { list ->
            if (nameFix.open !== opened || focus.page != PanelPage.FixName) return@titleList
            when (list) {
                null -> nameFix.showUnreadable(game, index)
                // The game left the library: back to the list, which no longer has it, with the notice on its row.
                TitleList.GameGone -> leaveNameFix(opened.listIndex)
                else -> nameFix.show(game, index, list)
            }
        }
    }

    /** The game under Fix name… is no longer in the library: say so, and go back to the list (or close it when nothing is left). */
    private fun leaveNameFix(listIndex: Int) {
        nameFix.close()
        work.say(SettingsOption.UnidentifiedGames, SettingsNotice.GameGone)
        // The library has not refreshed the list yet: the gone game is still on it.
        val remaining = content.unidentifiedGames.size - 1
        if (remaining <= 0) return closePanel()
        moveTo(focus.copy(page = PanelPage.UnidentifiedGames, panelIndex = listIndex.coerceAtMost(remaining - 1)))
    }

    /** Every action while Fix name… is open; leaving it (after a pick, or with B) returns to the list. */
    protected fun onNameFixAction(action: Action) = onNameFixStep { nameFix.onAction(action) }

    /** A tap on a key of Plumemu's tray over Fix name…'s field: as the same key pressed with A. */
    protected fun onNameFixKey(event: KeyboardEvent) = onNameFixStep { nameFix.onTyping { it.onKeyboardEvent(event) } }

    /** What Fix name… made of an action or a key: a move of the cursor or of the keys is heard; a leave stores and returns to the list. */
    private inline fun onNameFixStep(stepOf: () -> SettingsNameFix.Step) {
        val typing: TypingState? = nameFix.open?.typing
        val cursor = typing?.cursor
        val key = typing?.fieldKeys?.keys?.focus
        val step = stepOf() as? SettingsNameFix.Step.Leave
        if (step == null) {
            if (typing?.cursor != cursor || typing?.fieldKeys?.keys?.focus != key) feel.report(Outcome.Moved)
            return
        }
        val page = nameFix.open
        if (page != null && step.command != null) work.fixName(page.gameId, step.command)
        nameFix.close()
        // A fixed game leaves the list: the next one moves up into its place.
        val remaining = content.unidentifiedGames.size - if (step.command != null) 1 else 0
        if (remaining <= 0) return closePanel()
        moveTo(focus.copy(page = PanelPage.UnidentifiedGames, panelIndex = (page?.listIndex ?: 0).coerceAtMost(remaining - 1)))
    }

    /** Accounts › ScreenScraper while signed out: the sign-in form. */
    val signIn = SettingsSignIn()

    /**
     * The sign-in form for the account row, the cursor on its first empty field and the keyboard down, like setup's: Cancel
     * (and RetroAchievements' key page button) are in view at once; A on a field brings the keyboard up. [username] pre-filled
     * for Sign in again.
     */
    protected fun openSignIn(
        username: String = "",
        kind: SignInKind = SignInKind.ScreenScraper,
    ) {
        signIn.open(SettingsSignIn.newForm(username, keyboardChoice, kind)).focusFirstEmpty()
        moveTo(focus.copy(column = SettingsColumn.Panel, page = PanelPage.ScraperSignIn, panelIndex = 0))
    }

    /** The RetroAchievements sign-in form; [username] pre-filled for Sign in again. */
    protected fun openRaSignIn(username: String = "") = openSignIn(username, SignInKind.RetroAchievements)

    /** Every action while the sign-in form is open: Back and Cancel close it, a sign-in that worked closes it too. */
    protected fun onSignInAction(action: Action) {
        val form = signIn.form ?: return closePanel()
        onSignInStep(form, form.onAction(action), action)
    }

    /** A tap on a key of Plumemu's tray over the sign-in form: as the same key pressed with A (its action key may sign in). */
    protected fun onSignInKey(event: KeyboardEvent) {
        val form = signIn.form ?: return
        onSignInStep(form, form.onKeyboardKey(event), action = null)
    }

    /** What the form made of [action] (null: a key of the tray). */
    private fun onSignInStep(
        form: SignInForm,
        step: SignInStep,
        action: Action?,
    ) {
        when (step) {
            is SignInStep.Stay -> if (step.moved) feel.report(Outcome.Moved)
            is SignInStep.Submit -> {
                val isOpen = { signIn.form === form && focus.page == PanelPage.ScraperSignIn }
                work.signIn(form, step.username, step.password, isOpen) { if (isOpen()) leaveSignIn() }
                feel.report(Outcome.Activated)
            }
            SignInStep.OpenKeyPage -> {
                work.openWebPage(form, RA_KEY_PAGE_URL)
                feel.report(Outcome.Activated)
            }
            SignInStep.Leave, SignInStep.Done, SignInStep.NotHandled -> {
                // Back on an open form: the check (if any) goes on in the graph, its answer lands nowhere.
                if (step == SignInStep.NotHandled && action != Action.Back) return
                cause = Cause.Back
                leaveSignIn()
            }
        }
    }

    /** About › Report a problem: the form. */
    val report = SettingsProblemReport()

    protected fun openReport() {
        report.open(keyboardChoice)
        moveTo(focus.copy(column = SettingsColumn.Panel, page = PanelPage.ProblemReport, panelIndex = 0))
    }

    /** Every action while the report form is open: Back and Cancel close it, and so does Done after a report was sent or saved. */
    protected fun onReportAction(action: Action) {
        val form = report.form ?: return closePanel()
        onReportStep(form, form.onAction(action), action)
    }

    /** A tap on a key of Plumemu's tray over the report form: as the same key pressed with A. */
    protected fun onReportKey(event: KeyboardEvent) {
        val form = report.form ?: return
        onReportStep(form, form.onKeyboardKey(event), action = null)
    }

    private fun onReportStep(
        form: ProblemReportForm,
        step: ReportStep,
        action: Action?,
    ) {
        when (step) {
            is ReportStep.Stay -> if (step.moved) feel.report(Outcome.Moved)
            is ReportStep.Submit -> {
                work.submitReport(form, step) { report.form === form && focus.page == PanelPage.ProblemReport }
                feel.report(Outcome.Activated)
            }
            ReportStep.ShareLog -> {
                work.exportLogs(SettingsOption.ReportProblem)
                feel.report(Outcome.Activated)
            }
            ReportStep.Leave, ReportStep.NotHandled -> {
                if (step == ReportStep.NotHandled && action != Action.Back) return
                cause = Cause.Back
                report.close()
                closePanel()
            }
        }
    }

    private fun leaveSignIn() {
        signIn.close()
        closePanel()
    }

    /** Says what each action did, for the sounds (set by the screen). */
    val feel = Feel()

    /** What is being handled: decides whether going deeper or shallower is a move, a confirm or a back. */
    protected var cause = Cause.Move

    protected enum class Cause { Move, Back, Enter, Tap }

    private fun SettingsFocus.depth(): Int =
        column.ordinal * 2 + (if (page in DEEP_PAGES) 1 else 0) + (if (page in PAGES_UNDER_COLORS || page in PAGES_UNDER_LISTS) 1 else 0)

    private fun reportMove(
        from: SettingsFocus,
        to: SettingsFocus,
    ) {
        if (from == to) return
        val steps = to.depth() - from.depth()
        feel.report(
            when {
                steps != 0 ->
                    when (cause) {
                        Cause.Move -> Outcome.Moved
                        Cause.Back -> if (steps < 0) Outcome.WentBack else Outcome.Moved
                        Cause.Enter -> Outcome.Activated
                        // A tap that goes deeper opens something; one that only steps back or across moves.
                        Cause.Tap -> if (steps > 0) Outcome.Activated else Outcome.Moved
                    }
                cause == Cause.Enter -> Outcome.Activated
                else -> Outcome.Moved
            },
        )
    }

    /** The move being handled is a held key's repeat, not a press of its own. */
    protected var isHeldMove = false

    /** The last press or tap came with a narrow geometry: a row's panel shows only once opened. */
    protected var isNarrow = false

    /**
     * The keyboard Settings › Controls › Keyboard named at the last press or tap: a field opened
     * now (the hex code, Fix name…) types with it until it closes.
     */
    protected var keyboardChoice = KeyboardChoice.Plumemu

    /** Back up…'s page, the one place that says how a backup ended, was open while the standing result was there to read. */
    private var backupResultSeen = false

    /** A restore's or an Undo's standing result was on the screen, in Restore…'s page or Undo's. */
    private var restoreResultSeen = false

    /** Puts the focus on About's option row [index] (the row the main screen's update notice leads to). */
    fun focusAboutRow(index: Int) =
        moveTo(focus.copy(section = SettingsSection.About, column = SettingsColumn.Options, rememberedOption = focus.rememberedOption + (SettingsSection.About to index)))

    /** Puts the focus on Library's option row [index] (the row the home's empty state leads to). */
    fun focusLibraryRow(index: Int) =
        moveTo(focus.copy(section = SettingsSection.Library, column = SettingsColumn.Options, rememberedOption = focus.rememberedOption + (SettingsSection.Library to index)))

    protected fun moveTo(next: SettingsFocus) {
        val from = focus
        markResultsSeen(focus.page)
        markResultsSeen(next.page)
        // Another option: what was said about the last one goes.
        if (next.section != focus.section || next.optionIndexIn(content) != focus.optionIndexIn(content)) {
            doneOption = null
            work.clearNotice()
            if (focus.optionIn(content) == SettingsOption.RomFolders) work.leaveFolders()
            // A result was seen once its page showed it: the row's and the list's word alone leave it waiting.
            if (focus.optionIn(content) == SettingsOption.BackupAndRestore) {
                if (backupResultSeen) work.leaveBackup()
                if (restoreResultSeen) work.leaveRestore()
                backupResultSeen = false
                restoreResultSeen = false
            }
            if (focus.optionIn(content) == SettingsOption.Rescan || next.optionIn(content) == SettingsOption.Rescan) work.leaveRescan()
        } else if (focus.optionIn(content) == SettingsOption.Rescan && leavesRescanView(next)) {
            work.leaveRescan()
        } else if (next.optionIn(content) == SettingsOption.Rescan && focus.column == SettingsColumn.Sections && next.column != SettingsColumn.Sections) {
            // Coming in from the sections: a result that arrived while nobody looked is not shown.
            work.leaveRescan()
        }
        onFocusChanging(next)
        focus = next
        reportMove(from, next)
    }

    /** [page] is on the screen now: what it shows of a standing result counts as read. No result standing, nothing was seen: a page opened before the result does not count. */
    private fun markResultsSeen(page: PanelPage?) {
        val backup = content.backup
        val restore = content.restore
        backupResultSeen = backup.isResult && (backupResultSeen || page == PanelPage.Backup)
        restoreResultSeen = restore.isResult && (restoreResultSeen || page == PanelPage.Restore || (page == PanelPage.UndoRestore && undoPageShows(restore)))
    }

    /** B from Rescan's panel to the list (narrow), or from its row to the sections (wide): the person is done with the result. */
    private fun leavesRescanView(next: SettingsFocus) =
        next.column == SettingsColumn.Sections || (focus.column == SettingsColumn.Panel && next.column != SettingsColumn.Panel)

    internal companion object {
        /** Pages one step deeper because Colors' panel opened them. */
        private val PAGES_UNDER_COLORS = setOf(PanelPage.BackgroundPresets, PanelPage.TextPresets, PanelPage.BackgroundCustom, PanelPage.TextCustom)

        /** Pages one step deeper because the list of Back up and restore's or Reset…'s panel opened them; they keep the column, unlike [DEEP_PAGES]. */
        private val PAGES_UNDER_LISTS = setOf(PanelPage.Backup, PanelPage.Restore, PanelPage.UndoRestore, PanelPage.ResetAllConfirm, PanelPage.ResetLibraryConfirm)

        /** Pages one step deeper than their column (a colour's custom page, a licence text, a confirm, Fix name…). */
        val DEEP_PAGES = setOf(PanelPage.BackgroundCustom, PanelPage.AccentCustom, PanelPage.TextCustom, PanelPage.LicenseText, PanelPage.Folder, PanelPage.SkipSubfolder, PanelPage.RemoveFolder, PanelPage.FixName, PanelPage.ScraperSignIn, PanelPage.ProblemReport, PanelPage.ScraperSystems, PanelPage.ScraperConfirm, PanelPage.ScraperSignOutConfirm, PanelPage.RaSignOutConfirm, PanelPage.ScraperClearConfirm, PanelPage.UnknownFolder)
    }
}
