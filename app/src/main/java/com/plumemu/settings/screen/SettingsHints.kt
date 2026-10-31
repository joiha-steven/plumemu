package com.plumemu.settings.screen

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.plumemu.R
import com.plumemu.account.signInConfirmHint
import com.plumemu.report.reportConfirmHint
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.hint
import com.plumemu.coreui.components.Hint
import com.plumemu.coreui.components.HintBar
import com.plumemu.coreui.components.keyboard.KeyboardPage
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.WidthClass
import com.plumemu.game.isTrayUp
import com.plumemu.home.TypingHintBar
import com.plumemu.home.typingHints
import com.plumemu.settings.LocalButtonLetters

/**
 * What A does on the focused item, for the hint bar; null when it does nothing. On a text field
 * (the hex code, Fix name, Use my own name, the sign-in fields) A opens the keyboard: "Type".
 */
@StringRes
internal fun confirmHint(model: SettingsModel): Int? {
    val focus = model.focus
    return when {
        // The hex field, or the row that starts it; on "Use this colour" A chooses.
        model.isEditingHex && model.hexOnUse -> R.string.settings_hint_choose
        model.isEditingHex || (focus.column == SettingsColumn.Panel && focus.page != null && model.isHexRow(focus.page, focus.panelIndex)) -> R.string.account_hint_type
        // Fix name's and Use my own name's field.
        model.nameFix.open?.typing?.inField == true && focus.page == PanelPage.FixName -> R.string.account_hint_type
        focus.page == PanelPage.ScraperSignIn -> model.signIn.form?.let { signInConfirmHint(it, R.string.settings_cancel) }
        focus.page == PanelPage.ProblemReport -> model.report.form?.let(::reportConfirmHint)
        // Library › Systems: A picks the system up, and drops it while carried.
        focus.column == SettingsColumn.Panel && focus.page == PanelPage.Systems ->
            if (model.systems.isCarrying) R.string.settings_hint_done else R.string.settings_hint_move
        focus.column == SettingsColumn.Sections -> R.string.settings_hint_select
        focus.column == SettingsColumn.Options && model.focusedStatisticGame != null -> R.string.home_hint_play
        focus.column == SettingsColumn.Options -> model.focusedOption.let { if (it == null) R.string.settings_hint_change else rowConfirmHint(it) }
        focus.page == PanelPage.LicenseText || focus.page == PanelPage.ReadOnly -> null
        else -> R.string.settings_hint_choose
    }
}

@Composable
internal fun SettingsHints(
    settings: PlumSettings,
    model: SettingsModel,
    onHint: (Action) -> Unit,
) {
    val letters = LocalButtonLetters.current
    // While a field's tray is up: the typing hints of the game panel's fields (A Type, B Delete, …).
    val typing by remember(model) { derivedStateOf { model.ownKeyboardField?.takeIf { it.typing.isTrayUp } } }
    typing?.let { field ->
        val onLetters = field.typing.fieldKeys.keys.page == KeyboardPage.Letters
        val hasShift = field.typing.fieldKeys.keys.hasShift
        val hints = remember(onLetters, field.actionWord, hasShift, letters) { typingHints(onLetters, field.actionWord, hasShift, letters) }
        TypingHintBar(hints, onHint, Modifier.testTag(SettingsTags.TYPING_HINTS))
        return
    }
    val focus = model.focus
    val confirm = stringResource(letters.confirm)
    val back = stringResource(letters.back)
    val layout = PlumTheme.layout
    val inSystems = focus.column == SettingsColumn.Panel && focus.page == PanelPage.Systems
    val isCarrying = inSystems && model.systems.isCarrying
    HintBar {
        // Carrying a system: "↑↓ Move · A Done · B Cancel" (the arrows are the D-pad; a finger drags the handle).
        if (isCarrying) Hint(stringResource(R.string.settings_key_up_down), stringResource(R.string.settings_hint_move), Modifier.testTag(SettingsTags.HINT_MOVE))
        val confirmLabel = confirmHint(model)
        if (confirmLabel != null) {
            Hint(confirm, stringResource(confirmLabel), Modifier.hint(Action.Enter, onHint).testTag(SettingsTags.HINT_CONFIRM))
        }
        val backLabel =
            when {
                focus.column == SettingsColumn.Sections -> R.string.settings_hint_close
                isCarrying -> R.string.settings_hint_cancel
                else -> R.string.settings_hint_back
            }
        Hint(back, stringResource(backLabel), Modifier.hint(Action.Back, onHint).testTag(SettingsTags.HINT_BACK))
        if (inSystems && !isCarrying) {
            // Y is the one-press toggle elsewhere too (Favorite); a tap on the hint does the same, so touch can hide.
            val focused = model.systems.rows(model.content, settings).getOrNull(focus.panelIndex)
            if (focused != null) {
                val label = if (focused.id in settings.library.hiddenSystems) R.string.settings_hint_show else R.string.settings_hint_hide
                Hint(stringResource(letters.favorite), stringResource(label), Modifier.hint(Action.ToggleFavorite, onHint).testTag(SettingsTags.HINT_HIDE))
            }
        }
        if (focus.column == SettingsColumn.Options) {
            Hint(stringResource(R.string.home_key_l1_r1), stringResource(R.string.settings_hint_section), Modifier.hint(Action.NextSystem, onHint).testTag(SettingsTags.HINT_SECTION))
            val kind = model.focusedOption?.kind
            if (layout.widthClass == WidthClass.Narrow && model.focusedStatisticGame == null && (kind == OptionKind.Switch || kind == OptionKind.Info || kind == OptionKind.Action)) {
                Hint(
                    stringResource(R.string.settings_key_right),
                    stringResource(R.string.settings_hint_more),
                    Modifier.hint(Action.Move(Direction.Right), onHint).testTag(SettingsTags.HINT_MORE),
                )
            }
        }
    }
}
