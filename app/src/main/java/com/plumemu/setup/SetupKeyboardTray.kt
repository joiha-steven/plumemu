package com.plumemu.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.plumemu.account.SignInForm
import com.plumemu.account.signInActionWord
import com.plumemu.coreui.components.keyboard.KeyboardEvent
import com.plumemu.coreui.components.keyboard.KeyboardSpec
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.isTrayUp
import com.plumemu.keyboard.KeyboardTray

/**
 * Plumemu's keyboard over setup's box art step: the shared [KeyboardTray], the
 * same look, slide and place as Search's, the game panel's and Settings', for the sign-in form's
 * field ([SignInForm.typing]); nothing for a form typed with Android's keyboard or any other step.
 * Its key is the form's one typing state, so "Next" keeps the same tray up on the password; its
 * keys and action word ("Next", "Sign in") are the focused field's. Taps on its keys go to
 * [onEvent] ([SetupModel.onTrayKey]).
 */
@Composable
internal fun SetupKeyboardTray(
    form: SignInForm?,
    onEvent: (KeyboardEvent) -> Unit,
    belowContent: Dp,
) {
    val typing = form?.typing ?: return
    val keys = typing.fieldKeys.keys
    val actionLabel = stringResource(signInActionWord(form))
    val spec = remember(actionLabel, keys) { KeyboardSpec(actionLabel = actionLabel, hasShift = keys.hasShift) }
    KeyboardTray(
        owner = typing,
        isUp = typing.isTrayUp,
        keys = keys,
        spec = spec,
        onEvent = onEvent,
        belowContent = belowContent,
        tag = SetupTags.TRAY,
    )
}

/** What lies under setup's body on the tray: the hint bar's gap, the hint bar and the bottom margin (as Settings' strip). */
@Composable
internal fun setupTrayBelowBody(): Dp = PlumDimens.barGap + PlumTheme.layout.hintBarHeight + PlumDimens.stripMarginBottom

/** How much of the body's bottom the tray covers while it is up: the form's scroll ends above it. */
@Composable
internal fun setupKeyboardCovers(): Dp = PlumTheme.layout.keyboardTrayHeight - setupTrayBelowBody()
