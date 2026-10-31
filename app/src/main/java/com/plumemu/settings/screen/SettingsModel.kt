package com.plumemu.settings.screen

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.plumemu.account.AccountStatus
import com.plumemu.account.signInActionWord
import com.plumemu.report.reportActionWord
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column as InputColumn
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.TextEntry
import com.plumemu.coreui.components.keyboard.KeyboardEvent
import com.plumemu.coreui.components.keyboard.KeyboardPage
import com.plumemu.game.GameRef
import com.plumemu.game.TypingOutcome
import com.plumemu.game.TypingScreen
import com.plumemu.game.onKeyboardEvent
import com.plumemu.game.textEntry
import com.plumemu.game.typingActionWord
import com.plumemu.keyboard.KeyboardChoice
import com.plumemu.settings.ColorRefusal
import com.plumemu.settings.ColorWrite
import com.plumemu.settings.colorSet
import com.plumemu.settings.toKeyboardChoice
import com.plumemu.sound.Outcome

/**
 * Settings' focus and what each press or tap does. Pure decisions over [focus]; writes go to [actions]. The screen feeds it the router's
 * actions and its own taps, with the current settings and geometry.
 */
@Stable
internal class SettingsModel(
    actions: SettingsActions,
    initialFocus: SettingsFocus = SettingsFocus(),
) : SettingsPanelModel(actions, initialFocus) {
    /** The hex code field of a custom palette has the keyboard. */
    val isEditingHex: Boolean get() = custom.isEditingHex

    val hexKeyboardAsks: Int get() = custom.hexKeyboardAsks

    val hexOnUse: Boolean get() = custom.hexOnUse

    val isTypingHex: Boolean get() = custom.isTypingHex

    /** What a finger on a colour slider shows before the store has it; the "Color code" row follows it. */
    val colorPreview: ColorPreview get() = actions.colorPreview

    /** The colour the "+" page's Hue, Saturation and Lightness sliders hold. */
    val sliderHsl: Hsl get() = custom.hsl

    /** A tap on the hex field: back to typing there. */
    fun askHexKeyboard() = custom.askHexKeyboard()

    /** What the hex code field holds while it has the keyboard. */
    val hexText: String get() = custom.hexText

    /** The last typed hex code could not be read. */
    val hexError: Boolean get() = custom.hexError

    /** The typed code was refused as unreadable; [ColorRefusal.nearest] is offered instead. */
    val hexRefusal: ColorRefusal? get() = custom.hexRefusal

    /** The settings were replaced from outside the colour panels (a restore, a reset): what they wrote before no longer stands. */
    fun forgetColorWrites() = custom.forgetWritten()

    /** What the last colour write did (moved the text or accent, or refused the pick), for the colour panels to say. */
    val colorWrite: ColorWrite? get() = custom.lastWrite

    /** The hex field as Plumemu's keyboard types into it (null before the first editing). */
    val hexTyping: HexTyping? get() = custom.hexTyping

    /**
     * The field of the focused page that types with Plumemu's keyboard: the hex
     * code on a custom colour page (once editing started), Fix name…'s / Use my own name's field, or the sign-in
     * form's username or password; null for
     * any other page and for a field typed with Android's keyboard. Its tray is up while
     * [com.plumemu.game.isTrayUp].
     */
    val ownKeyboardField: SettingsTypingField?
        get() {
            val page = focus.page ?: return null
            val field =
                when {
                    page == PanelPage.FixName ->
                        nameFix.open?.let { open -> (open.screen as? TypingScreen)?.let { SettingsTypingField(it.typing, typingActionWord(it)) } }
                    // Kept after the code is used, so the tray slides down rather than vanishing.
                    SettingsCustomColor.isCustomPage(page) -> custom.hexTyping?.let { SettingsTypingField(it, R.string.settings_hint_done, KeyboardPage.Symbols) }
                    // One state for both fields: "Next" keeps the same tray up on the password.
                    page == PanelPage.ScraperSignIn -> signIn.form?.let { form -> form.typing?.let { SettingsTypingField(it, signInActionWord(form)) } }
                    page == PanelPage.ProblemReport -> report.form?.let { form -> form.typing?.let { SettingsTypingField(it, reportActionWord(form)) } }
                    else -> null
                }
            return field?.takeIf { it.typing.keyboardKind == KeyboardChoice.Plumemu }
        }

    /**
     * What the input router must send now: B erasing and typed keys while
     * a field's tray is up, typed keys with the cursor on such a field and its tray down, nothing
     * else anywhere in Settings (Android's keyboard included).
     */
    val textEntry: TextEntry get() = ownKeyboardField?.typing?.textEntry ?: TextEntry.None

    /** A tap on a key of Plumemu's tray: what A on that key would do in the field it types into. */
    fun onTrayKey(event: KeyboardEvent) =
        feel.act {
            cause = Cause.Tap
            when {
                focus.page == PanelPage.FixName -> onNameFixKey(event)
                focus.page == PanelPage.ScraperSignIn -> onSignInKey(event)
                focus.page == PanelPage.ProblemReport -> onReportKey(event)
                isEditingHex -> custom.hexTyping?.let { hex -> onHexKeys(hex) { hex.onKeyboardEvent(event) } }
            }
        }

    /**
     * The hex field's part of an action or key with Plumemu's keyboard ([HexTyping]); false when
     * it is the model's own (Android's keyboard, or A / the D-pad / B with the tray down). The code
     * tried (used, or refused with the format line) is a confirm, as A on "Use this colour" is; a
     * move of the cursor or of the keys is heard as a move.
     */
    private inline fun onHexKeys(
        hex: HexTyping,
        outcomeOf: () -> TypingOutcome,
    ): Boolean {
        val cursor = hex.cursor
        val key = hex.fieldKeys.keys.focus
        val tries = hex.tries
        if (outcomeOf() == TypingOutcome.NotHandled) return false
        when {
            hex.tries != tries || !isEditingHex -> feel.report(Outcome.Activated)
            hex.cursor != cursor || hex.fieldKeys.keys.focus != key -> feel.report(Outcome.Moved)
        }
        return true
    }

    /** The settings of a press or tap: what it may need beyond the focus (which keyboard a field opens with). */
    private fun follow(settings: PlumSettings) {
        keyboardChoice = settings.controls.toKeyboardChoice()
        // Text and Accent palettes are the colours that read on the background shown (a model made on a page, in a test or a shot, never opened it).
        if (SettingsCustomColor.isCustomPage(focus.page)) custom.followBackground(settings)
    }

    fun onAction(
        action: Action,
        settings: PlumSettings,
        geometry: SettingsGeometry,
    ) = feel.act {
        isNarrow = geometry.isNarrow
        follow(settings)
        cause =
            when (action) {
                Action.Back -> Cause.Back
                Action.Enter, Action.Play -> Cause.Enter
                else -> Cause.Move
            }
        handle(action, settings, geometry)
    }

    private fun handle(
        action: Action,
        settings: PlumSettings,
        geometry: SettingsGeometry,
    ) {
        if (isEditingHex) {
            // Plumemu's keyboard first: while its tray is up it takes every action (the panels' button map).
            val hex = custom.hexTyping
            if (hex != null && onHexKeys(hex) { hex.onAction(action) }) return
            // A on the field brings the keyboard back when the system's Back closed it; the keyboard's Done and
            // A on "Use this colour" (Down from the field, or a tap) use the code ([onHexEntered]).
            when (action) {
                Action.Back -> {
                    stopEditingHex()
                    feel.report(Outcome.WentBack)
                }
                // A on "Use this colour" uses the code; A on the field opens the keyboard.
                Action.Enter -> {
                    if (hexOnUse) onHexEntered(hexText) else custom.askHexKeyboard()
                    feel.report(Outcome.Activated)
                }
                is Action.Move ->
                    when (action.direction) {
                        Direction.Down -> custom.moveInHex(true)
                        Direction.Up -> custom.moveInHex(false)
                        else -> Unit
                    }
                else -> Unit
            }
            return
        }
        if (focus.page == PanelPage.FixName) return onNameFixAction(action)
        if (focus.page == PanelPage.ScraperSignIn) return onSignInAction(action)
        if (focus.page == PanelPage.ProblemReport) return onReportAction(action)
        if (focus.page == PanelPage.Systems && focus.column == SettingsColumn.Panel) return onSystemsAction(action, settings, geometry)
        when (focus.column) {
            SettingsColumn.Sections -> onSections(action)
            SettingsColumn.Options -> onOptions(action, settings, geometry)
            SettingsColumn.Panel -> onPanel(action, settings, geometry)
        }
    }

    /** A tap that only focuses a row moves, even from the sections column (the row is not opened). */
    private fun focusOnly(index: Int) {
        cause = Cause.Move
        focusOption(index)
        cause = Cause.Tap
    }

    /** A tap: [run] with taps' sounds (a tap that opens confirms, one that only focuses moves). */
    private fun tapped(run: () -> Unit) =
        feel.act {
            cause = Cause.Tap
            run()
        }

    // Touch: a tap focuses; a second tap, or a tap on the value, acts.

    fun onTapSection(section: SettingsSection) = tapped {
        if (focus.column == SettingsColumn.Sections && focus.section == section) {
            enterOptions()
        } else {
            moveTo(focus.copy(column = SettingsColumn.Sections, section = section, page = null))
        }
    }

    fun onTapOption(
        index: Int,
        settings: PlumSettings,
        geometry: SettingsGeometry,
    ) = tapped {
        isNarrow = geometry.isNarrow
        follow(settings)
        val option = rows().getOrNull(index) ?: return@tapped
        val wasFocused = focus.column == SettingsColumn.Options && optionIndex == index
        focusOnly(index)
        // Switches, and Use as home screen drawn as one, act on the first tap.
        if (wasFocused || option.kind == OptionKind.Switch || option == SettingsOption.UseAsHome) activate(option, settings, geometry)
    }

    fun onTapValue(
        index: Int,
        settings: PlumSettings,
        geometry: SettingsGeometry,
    ) = tapped {
        isNarrow = geometry.isNarrow
        follow(settings)
        val option = rows().getOrNull(index) ?: return@tapped
        val wasFocused = focus.column == SettingsColumn.Options && optionIndex == index
        focusOnly(index)
        // A game row launches the game, so a first tap only focuses it (it could be a mis-tap).
        if (wasFocused || focusedStatisticGame == null) activate(option, settings, geometry)
    }

    /** A tap on item [index] of the panel; in the wide panel beside a focused option it enters the panel. */
    fun onTapPanelItem(
        index: Int,
        settings: PlumSettings,
        geometry: SettingsGeometry,
    ) = tapped {
        isNarrow = geometry.isNarrow
        follow(settings)
        val opensNow = focus.column != SettingsColumn.Panel
        if (opensNow) {
            val option = focusedOption ?: return@tapped
            if (!openPanel(option, settings, geometry)) return@tapped
        }
        val page = focus.page ?: return@tapped
        // Colors opens on the current set's tile: a first tap on that tile only enters the panel there,
        // like a tap on any other tile; the second tap uses it.
        if (page == PanelPage.ColorSets && opensNow) {
            if (focus.panelIndex != index) focusPanelItem(index)
            return@tapped
        }
        if (page == PanelPage.ScraperSignIn) return@tapped onSignInAction(Action.TapItem(InputColumn.Details, index))
        if (page == PanelPage.ProblemReport) return@tapped onReportAction(Action.TapItem(InputColumn.Details, index))
        // A system's checkmark follows the first tap, like a switch; the picker's buttons take two like every button.
        if (page == PanelPage.ScraperSystems && systemsPickActionAt(content, index) == null) {
            focusPanelItem(index)
            onPanelEnter(settings)
            return@tapped
        }
        if (focus.panelIndex == index && !SettingsCustomColor.isCustomPage(page)) {
            onPanelEnter(settings)
            return@tapped
        }
        if (isHexRow(page, index)) {
            // Like reaching it with the D-pad: the colour the page opened with comes back.
            focusPanelItem(index)
            // Already typing: a tap on the field goes back to it with what was typed.
            if (isEditingHex) custom.askHexKeyboard() else startEditingHex()
            return@tapped
        }
        if (focus.panelIndex == index) {
            // The ring opened on this swatch without previewing it: a tap picks it (A before any move keeps the current colour).
            if (!custom.cursorMoved) custom.focus(page, index)
            onPanelEnter(settings)
            return@tapped
        }
        focusPanelItem(index)
        // A tap on a swatch chooses it (the page then keeps it when left).
        if (SettingsCustomColor.isCustomPage(page)) custom.choose(page, custom.palette(page).getOrNull(index)?.let { ColorSetting.Custom(it.value) })
    }

    /**
     * A tap or drag on a colour slider's track at [fraction] (0..1): the row takes the focus, the
     * thumb goes to the nearest value that reads and the colour previews. The panel opens first
     * when it only previews.
     */
    fun onTouchSlider(
        index: Int,
        fraction: Float,
        settings: PlumSettings,
        geometry: SettingsGeometry,
    ) = tapped {
        isNarrow = geometry.isNarrow
        follow(settings)
        if (focus.column != SettingsColumn.Panel) {
            val option = focusedOption ?: return@tapped
            if (!openPanel(option, settings, geometry)) return@tapped
        }
        val page = focus.page ?: return@tapped
        val slider = custom.sliderAt(page, index) ?: return@tapped
        if (focus.panelIndex != index) focusPanelItem(index)
        if (custom.setSlider(page, slider, fraction)) feel.report(Outcome.Moved)
    }

    /** The finger on a colour slider lifted or was cancelled: the thumb settles on the value in effect. */
    fun onTouchSliderEnd() = custom.releaseSlider()

    /** The slider a finger holds and where on its track the thumb is drawn; null when no finger is down. */
    val sliderFinger: Pair<ColorSlider, Float>? get() = custom.fingerOn

    // Library › Systems by touch: a tap focuses a system, a tap on "Hidden" shows it again, a long
    // press on the handle picks it up and the drag carries it.

    /** Opens the Systems panel for the focus if it only previews (wide); false when it cannot open. */
    private fun inSystemsPage(
        settings: PlumSettings,
        geometry: SettingsGeometry,
    ): Boolean {
        if (focus.column == SettingsColumn.Panel && focus.page == PanelPage.Systems) return true
        return focusedOption == SettingsOption.Systems && openPanel(SettingsOption.Systems, settings, geometry)
    }

    fun onTapSystem(
        index: Int,
        settings: PlumSettings,
        geometry: SettingsGeometry,
    ) = tapped {
        if (!inSystemsPage(settings, geometry) || systems.isCarrying) return@tapped
        focusPanelItem(index)
    }

    fun onTapSystemHidden(
        index: Int,
        settings: PlumSettings,
        geometry: SettingsGeometry,
    ) = tapped {
        if (!inSystemsPage(settings, geometry) || systems.isCarrying) return@tapped
        focusPanelItem(index)
        toggleSystemHidden(systems.rows(content, settings), settings)
    }

    fun onSystemDragStart(
        index: Int,
        settings: PlumSettings,
        geometry: SettingsGeometry,
    ) = tapped {
        if (!inSystemsPage(settings, geometry) || systems.isCarrying) return@tapped
        focusPanelItem(index)
        pickUpSystem(systems.rows(content, settings), settings)
    }

    /** The finger travelled one row's height down (+1) or up (-1). */
    fun onSystemDragStep(step: Int) =
        feel.act {
            cause = Cause.Move
            if (systems.isCarrying) carrySystemTo(focus.panelIndex + step)
        }

    fun onSystemDragEnd() =
        feel.act {
            cause = Cause.Enter
            dropSystem()
        }

    fun onSystemDragCancel() =
        feel.act {
            cause = Cause.Back
            cancelSystemCarry()
        }

    /** The keyboard's Done on the hex field: a valid code is used as typed. */
    fun onHexEntered(text: String) {
        custom.enterHex(focus.page ?: return, text)
    }

    fun onHexTextChange(text: String) = custom.onHexTextChange(text)

    fun stopEditingHex() = custom.stopEditingHex()

    /**
     * Settings leaves the composition (closed, or the Activity recreated) on a custom page: the
     * colour previewed there must not stay as the stored one, so the opening colour is written back.
     */
    fun onDispose() {
        if (focusedOption == SettingsOption.RomFolders) work.leaveFolders()
        val page = focus.page
        // A finger still down when the screen goes: its colour is written (if chosen) or put back, never lost or left as an overlay.
        if (page != null && SettingsCustomColor.isCustomPage(page)) custom.leaveAndRelease(page) else custom.releaseSlider()
    }

    private fun onSections(action: Action) {
        val sections = SettingsSection.entries
        val at = focus.section.ordinal
        when (action) {
            is Action.Move ->
                when (action.direction) {
                    Direction.Up -> if (at > 0) moveTo(focus.copy(section = sections[at - 1]))
                    Direction.Down -> if (at < sections.lastIndex) moveTo(focus.copy(section = sections[at + 1]))
                    Direction.Right -> enterOptions()
                    Direction.Left -> Unit
                }
            Action.Enter -> enterOptions()
            Action.Back -> {
                actions.close()
                feel.report(Outcome.WentBack)
            }
            Action.NextSystem -> changeSection(+1, SettingsColumn.Sections)
            Action.PreviousSystem -> changeSection(-1, SettingsColumn.Sections)
            else -> Unit
        }
    }

    private fun onOptions(
        action: Action,
        settings: PlumSettings,
        geometry: SettingsGeometry,
    ) {
        val option = focusedOption ?: return backToSections()
        val at = optionIndex
        when (action) {
            is Action.Move ->
                when (action.direction) {
                    Direction.Up -> if (at > 0) focusOption(at - 1)
                    Direction.Down -> if (at < rows().lastIndex) focusOption(at + 1)
                    Direction.Left -> if (option.kind == OptionKind.Choice) stepChoice(option, -1, wrap = false, settings, geometry) else backToSections()
                    Direction.Right ->
                        when (option.kind) {
                            OptionKind.Choice -> stepChoice(option, +1, wrap = false, settings, geometry)
                            OptionKind.Swatch, OptionKind.Opens -> openPanel(option, settings, geometry)
                            // The explanation, as a further stop on narrow screens (wide shows it already).
                            OptionKind.Switch, OptionKind.Info, OptionKind.Action -> if (geometry.isNarrow && focusedStatisticGame == null) openPanel(option, settings, geometry)
                        }
                }
            Action.Enter -> activate(option, settings, geometry)
            Action.Back -> backToSections()
            Action.NextSystem -> changeSection(+1, SettingsColumn.Options)
            Action.PreviousSystem -> changeSection(-1, SettingsColumn.Options)
            else -> Unit
        }
    }

    /** Starts a game (Statistics' most-played rows); set by the screen to the game menu's Play, so a failure shows the main screen's message. */
    var playGame: (GameRef) -> Unit = {}

    /** The focused row when it is one of Statistics' most-played games; null for every other row. */
    val focusedStatisticGame: StatisticRow.Game?
        get() = if (focusedOption == SettingsOption.Statistic) content.statisticRows.getOrNull(optionIndex) as? StatisticRow.Game else null

    /** A, a second tap or a tap on the value. */
    private fun activate(
        option: SettingsOption,
        settings: PlumSettings,
        geometry: SettingsGeometry,
    ) {
        when (option.kind) {
            // Colors' A opens its preview panel; left / right still step.
            OptionKind.Choice -> if (option == SettingsOption.Colors) openPanel(option, settings, geometry) else stepChoice(option, +1, wrap = true, settings, geometry)
            OptionKind.Switch -> {
                // From the stored value inside the write, like every switch; the second screen's
                // shown value also depends on the screen found, read now.
                val screen = content.secondScreen
                actions.update { if (option == SettingsOption.SecondScreen) it.withSecondScreenFlipped(screen) else option.toggled(it) }
                feel.report(Outcome.Activated)
            }
            OptionKind.Swatch, OptionKind.Opens -> openPanel(option, settings, geometry)
            // A on a most-played game plays it, wide or narrow. Every other Info row is
            // read-only: A and a tap do nothing and make no sound (narrow: Right opens its explanation, "› More").
            OptionKind.Info -> focusedStatisticGame?.let { game -> playGame(GameRef(game.game.id.toString(), game.game.title)) }
            OptionKind.Action -> runAction(option)
        }
    }

    private fun runAction(option: SettingsOption) {
        when (option) {
            SettingsOption.Rescan -> {
                work.rescan()
                // A press on a running scan is acknowledged too (the repo has no "nothing happened" sound): its notice shows under the progress.
                feel.report(Outcome.Activated)
            }
            SettingsOption.DetectEmulators ->
                if (!work.isDetecting(content)) {
                    work.detectEmulators()
                    feel.report(Outcome.Activated)
                }
            SettingsOption.UseAsHome -> {
                work.openHomeSettings()
                feel.report(Outcome.Activated)
            }
            SettingsOption.CheckForUpdates -> {
                actions.updates.checkNow()
                feel.report(Outcome.Activated)
            }
            SettingsOption.ExportLogs -> {
                work.exportLogs()
                feel.report(Outcome.Activated)
            }
            else -> Unit
        }
    }

    private fun stepChoice(
        option: SettingsOption,
        step: Int,
        wrap: Boolean,
        settings: PlumSettings,
        geometry: SettingsGeometry,
    ) {
        val count = option.choiceCount(settings)
        val raw = option.choiceIndex(settings, geometry.defaultTextSize) + step
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
            val raw = option.choiceIndex(stored, geometry.defaultTextSize) + step
            // Colors has one value more while there is a custom look: count what is stored too.
            val storedCount = option.choiceCount(stored)
            option.withChoice(stored, if (wrap) Math.floorMod(raw, storedCount) else raw.coerceIn(0, storedCount - 1))
        }
    }

    /** Opens [option]'s panel for the focus; false when it has nothing to focus here (wide, read only). */
    private fun openPanel(
        option: SettingsOption,
        settings: PlumSettings,
        geometry: SettingsGeometry,
    ): Boolean {
        val (page, index) =
            when (option) {
                SettingsOption.Colors -> PanelPage.ColorSets to colorSetsPanelEntries(settings).indexOf(settings.appearance.colorSet()).coerceAtLeast(0)
                SettingsOption.Background, SettingsOption.TextColor, SettingsOption.Accent -> ColorPages.openingFor(option, custom.withWritten(settings))
                SettingsOption.ResetAll, SettingsOption.ResetLibrary, SettingsOption.ClearRecent -> PanelPage.Confirm to CONFIRM_CANCEL
                SettingsOption.Backup -> PanelPage.Backup to 0
                SettingsOption.UpdateAvailable -> PanelPage.Update to 0
                SettingsOption.Restore -> PanelPage.Restore to 0
                SettingsOption.UndoRestore -> PanelPage.UndoRestore to 0
                SettingsOption.Licenses -> PanelPage.Licenses to 0
                SettingsOption.Language -> PanelPage.Language to languagePanelIndex(settings.appearance.language)
                SettingsOption.ReportProblem -> return openReport().let { true }
                SettingsOption.RomFolders -> PanelPage.Folders to 0
                SettingsOption.HiddenGames -> PanelPage.HiddenGames to 0
                SettingsOption.UnidentifiedGames -> PanelPage.UnidentifiedGames to 0
                SettingsOption.SystemEmulator -> PanelPage.Emulators to (systemAtFocus()?.currentIndex ?: 0)
                SettingsOption.Systems -> PanelPage.Systems to 0
                SettingsOption.UnknownFolder -> PanelPage.UnknownFolder to (SettingsListPages.unknownFolderAt(focus, content)?.let { SettingsListPages.answerIndex(it, content) } ?: 0)
                SettingsOption.ScraperAccount ->
                    if (content.account is AccountStatus.SignedIn) PanelPage.ScraperAccount to 0 else return openSignIn().let { true }
                else -> if (geometry.isNarrow) PanelPage.ReadOnly to 0 else return false
            }
        doneOption = null
        // A count from an earlier Clear must not read as the answer to this ask.
        if (option == SettingsOption.ClearRecent && work.notice?.first == SettingsOption.ClearRecent) work.clearNotice()
        if (SettingsCustomColor.isCustomPage(page)) {
            enterCustomPage(page, settings)
        } else {
            // The panel remembers what it opened with, so browsing presets settles from it.
            if (ColorPages.kindOf(page) != null) custom.open(page, settings)
            moveTo(focus.copy(column = SettingsColumn.Panel, page = page, panelIndex = index, systemId = systemAtFocus()?.systemId, folderKey = SettingsListPages.unknownFolderAt(focus, content)?.key, undoAsk = null))
        }
        return true
    }

    companion object {
        const val CONFIRM_CANCEL = 0
        const val CONFIRM_RESET = 1
    }
}
