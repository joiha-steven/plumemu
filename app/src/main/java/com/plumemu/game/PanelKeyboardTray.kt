package com.plumemu.game

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.plumemu.R
import com.plumemu.coreui.components.keyboard.KeyboardEvent
import com.plumemu.coreui.components.keyboard.KeyboardSpec
import com.plumemu.keyboard.KeyboardChoice
import com.plumemu.keyboard.KeyboardTray

/**
 * Plumemu's keyboard over the open panel's field: the shared [KeyboardTray], up
 * while [isTrayUp], with shift (titles have capitals) and the field's action word on its action key
 * ([typingActionWord]). Nothing for a panel without a field or one typed with Android's keyboard.
 * Taps on its keys go to [onEvent] (the controller's `onKeyboardEvent`).
 */
@Composable
internal fun PanelKeyboardTray(
    panel: OpenPanel?,
    onEvent: (KeyboardEvent) -> Unit,
    belowContent: Dp,
) {
    val screen = panel?.screen as? TypingScreen ?: return
    val typing = screen.typing
    if (typing.keyboardKind != KeyboardChoice.Plumemu) return
    val actionLabel = stringResource(typingActionWord(screen))
    val spec = remember(actionLabel) { KeyboardSpec(actionLabel = actionLabel, hasShift = true) }
    KeyboardTray(
        owner = typing,
        isUp = typing.isTrayUp,
        keys = typing.fieldKeys.keys,
        spec = spec,
        onEvent = onEvent,
        belowContent = belowContent,
        tag = FixNameTags.TRAY,
    )
}

/** The field's action, on the keyboard's action key and after START in the hints: "Search" for Fix name… and Fix box art…, "Save" for Use my own name. */
@StringRes
internal fun typingActionWord(screen: TypingScreen): Int =
    when (screen) {
        is PanelScreen.OwnName -> R.string.home_hint_save
        else -> R.string.home_hint_search
    }
