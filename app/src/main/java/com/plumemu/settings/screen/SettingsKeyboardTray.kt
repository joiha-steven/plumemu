package com.plumemu.settings.screen

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.plumemu.coreui.components.keyboard.KeyboardPage
import com.plumemu.coreui.components.keyboard.KeyboardSpec
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.TypingState
import com.plumemu.game.isTrayUp
import com.plumemu.keyboard.KeyboardTray

/**
 * A Settings field typed with Plumemu's keyboard: its state, the word on the
 * tray's action key and after START in the hints ("Done" for the hex code, "Search" for Fix name…,
 * "Save" for Use my own name) and the page its keys open on (the hex code's digits).
 */
@Immutable
internal data class SettingsTypingField(
    val typing: TypingState,
    @StringRes val actionWord: Int,
    val startPage: KeyboardPage = KeyboardPage.Letters,
)

/**
 * Plumemu's keyboard over Settings' panel: the shared [KeyboardTray], the same
 * look, slide and place as Search's and the game panel's, for the focused page's field
 * ([SettingsModel.ownKeyboardField]); nothing for any other page. Taps on its keys go to
 * [SettingsModel.onTrayKey]. Only this reads the field, so a key move recomposes nothing else.
 */
@Composable
internal fun SettingsKeyboardTray(
    model: SettingsModel,
    belowContent: Dp,
) {
    val field by remember(model) { derivedStateOf { model.ownKeyboardField } }
    val shown = field ?: return
    val keys = shown.typing.fieldKeys.keys
    val actionLabel = stringResource(shown.actionWord)
    val spec = remember(actionLabel, keys, shown.startPage) { KeyboardSpec(actionLabel = actionLabel, hasShift = keys.hasShift, startPage = shown.startPage) }
    KeyboardTray(
        owner = shown.typing,
        isUp = shown.typing.isTrayUp,
        keys = keys,
        spec = spec,
        onEvent = model::onTrayKey,
        belowContent = belowContent,
        tag = SettingsTags.TRAY,
    )
}

/** What lies under Settings' strip on the tray: the hint bar's gap, the hint bar and the bottom margin. */
@Composable
internal fun settingsTrayBelowStrip(): Dp = PlumDimens.barGap + PlumTheme.layout.hintBarHeight + PlumDimens.stripMarginBottom

/** How much of the strip's bottom the tray covers while it is up: a page with a field is laid out above it. */
@Composable
internal fun settingsKeyboardCovers(): Dp = PlumTheme.layout.keyboardTrayHeight - settingsTrayBelowStrip()
