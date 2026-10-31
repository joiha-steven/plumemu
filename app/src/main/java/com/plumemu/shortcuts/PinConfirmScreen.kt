package com.plumemu.shortcuts

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column as StripColumn
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreinput.hint
import com.plumemu.coreinput.tapItem
import com.plumemu.coreui.components.Hint
import com.plumemu.coreui.components.HintBar
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.WidthClass
import com.plumemu.settings.ButtonLetters
import com.plumemu.setup.SetupButton
import com.plumemu.setup.bringIntoViewWhen
import com.plumemu.sound.HearFeel

/** The confirm screen; it takes the router's actions (the Activity follows `closedFlow` to finish). */
@Composable
internal fun PinConfirmScreen(
    state: PinConfirmState,
    input: InputRouter,
    letters: ButtonLetters,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    HearFeel(state.feel)
    LaunchedEffect(state, input, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            input.actions.collect(state::onAction)
        }
    }
    val send = remember(input) { { action: Action -> input.send(action) } }
    PinConfirmLayout(state.screen, state.focus, letters, send)
}

/**
 * A `sel2` panel in the home screen question's look over the dimmed app that asked, with its own
 * hint bar: the title names the game and its list, two buttons (Add, Cancel), or a message and OK.
 */
@Composable
internal fun PinConfirmLayout(
    screen: PinScreen,
    focus: Int,
    letters: ButtonLetters,
    onAction: (Action) -> Unit,
) {
    if (screen is PinScreen.Reading) return
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val isAsking = screen is PinScreen.Asking
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bg.copy(alpha = SCRIM_ALPHA))
            // Taps outside the panel land here, not on the app underneath.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(top = PlumDimens.stripMarginTop + PlumTheme.layout.topBarHeight + PlumDimens.barGap, bottom = PlumDimens.stripMarginBottom)
            .testTag(PinConfirmTags.SCREEN),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = PlumDimens.stripMarginSide), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .widthIn(max = PlumDimens.homeRolePromptWidth)
                    .background(colors.sel2, RoundedCornerShape(PlumDimens.cornerSetupChoice))
                    // Scrolls when the text is large and the screen short; the focused button comes into view.
                    .testTag(PinConfirmTags.SCROLL)
                    .verticalScroll(rememberScrollState())
                    .padding(PlumDimens.homeRolePromptPadding),
                verticalArrangement = Arrangement.spacedBy(PlumDimens.setupTitleGapWide),
            ) {
                BasicText(text = titleOf(screen), style = type.setupTitle(WidthClass.Narrow).copy(color = colors.text), modifier = Modifier.testTag(PinConfirmTags.TITLE))
                BasicText(text = bodyOf(screen), style = type.description.copy(color = colors.muted))
                Spacer(Modifier.height(PlumDimens.setupButtonSpacer))
                Row(horizontalArrangement = Arrangement.spacedBy(PlumDimens.setupButtonGap)) {
                    if (isAsking) {
                        PinButton(R.string.pin_confirm_add, index = 0, isPrimary = true, focus, onAction)
                        PinButton(R.string.pin_confirm_cancel, index = 1, isPrimary = false, focus, onAction)
                    } else {
                        PinButton(R.string.pin_ok, index = 0, isPrimary = true, focus, onAction)
                    }
                }
            }
        }
        Spacer(Modifier.height(PlumDimens.barGap))
        HintBar(Modifier.background(colors.bg)) {
            Hint(
                keyCap = stringResource(letters.confirm),
                label = stringResource(if (isAsking) R.string.pin_hint_select else R.string.pin_ok),
                modifier = Modifier.hint(Action.Enter, onAction),
            )
            if (isAsking) {
                Hint(
                    keyCap = stringResource(letters.back),
                    label = stringResource(R.string.pin_hint_cancel),
                    modifier = Modifier.hint(Action.Back, onAction).testTag(PinConfirmTags.HINT_CANCEL),
                )
            }
        }
    }
}

@Composable
private fun titleOf(screen: PinScreen): String =
    when (screen) {
        is PinScreen.Asking -> stringResource(R.string.pin_confirm_title, screen.label, screen.systemName)
        is PinScreen.Message ->
            when (screen.message) {
                PinMessage.Widget -> stringResource(R.string.pin_widget_title)
                PinMessage.Expired -> stringResource(R.string.pin_expired_title)
                PinMessage.NotReady -> stringResource(R.string.pin_not_ready_title)
                PinMessage.NotAccepted -> stringResource(R.string.pin_not_accepted_title, screen.label)
                PinMessage.NotSaved -> stringResource(R.string.pin_not_saved_title, screen.label)
            }
        PinScreen.Reading -> ""
    }

@Composable
private fun bodyOf(screen: PinScreen): String =
    when (screen) {
        is PinScreen.Asking -> stringResource(R.string.pin_confirm_body)
        is PinScreen.Message ->
            when (screen.message) {
                PinMessage.Widget -> stringResource(R.string.pin_widget_body)
                PinMessage.Expired -> stringResource(R.string.pin_expired_body)
                PinMessage.NotReady -> stringResource(R.string.pin_not_ready_body)
                PinMessage.NotAccepted -> stringResource(R.string.pin_not_accepted_body)
                PinMessage.NotSaved -> stringResource(R.string.pin_not_saved_body)
            }
        PinScreen.Reading -> ""
    }

@Composable
private fun PinButton(
    text: Int,
    index: Int,
    isPrimary: Boolean,
    focus: Int,
    onAction: (Action) -> Unit,
) {
    SetupButton(
        text = stringResource(text),
        isPrimary = isPrimary,
        isFocused = focus == index,
        modifier = Modifier.bringIntoViewWhen(focus == index).tapItem(StripColumn.Games, index, onAction).testTag(PinConfirmTags.button(index)),
    )
}

/** The app that asked shows through, dimmed, so the question reads as asked about it. */
private const val SCRIM_ALPHA = 0.9f

internal object PinConfirmTags {
    const val SCREEN = "pin_confirm"
    const val TITLE = "pin_confirm_title"
    const val SCROLL = "pin_confirm_scroll"
    const val HINT_CANCEL = "pin_confirm_hint_cancel"

    fun button(index: Int): String = "pin_confirm_button_$index"
}
