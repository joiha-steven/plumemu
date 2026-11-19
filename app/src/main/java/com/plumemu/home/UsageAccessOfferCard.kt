package com.plumemu.home

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.withStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreinput.hint
import com.plumemu.coreinput.tapItem
import com.plumemu.coreui.components.Hint
import com.plumemu.coreui.components.HintBar
import com.plumemu.coreui.components.pageBackground
import com.plumemu.coreui.components.PanelTitleWithDot
import com.plumemu.coreui.components.raisedCard
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.WidthClass
import com.plumemu.settings.ButtonLetters
import com.plumemu.sound.Feel
import com.plumemu.sound.HearFeel
import com.plumemu.sound.Outcome

import com.plumemu.setup.SetupButton
import com.plumemu.setup.bringIntoViewWhen
/**
 * The one-time card offering Android's Usage access (Exact play time), shown over an idle main screen after the first real play.
 * Allow calls [onAllow] (the caller opens Android's screen) and closes; Not now and B only close. Left and Right move between
 * the buttons, A presses the focused one, a tap presses the one tapped. The caller has already stored that it was shown.
 */
@Stable
internal class UsageAccessOfferState(private val onAllow: () -> Unit) {
    /** 0 = Allow, 1 = Not now. */
    var focus by mutableIntStateOf(ALLOW)
        private set
    var isClosed by mutableStateOf(false)
        private set

    val feel = Feel()

    fun onAction(action: Action) {
        if (isClosed) return
        feel.act {
            when (action) {
                is Action.Move -> {
                    val before = focus
                    when (action.direction) {
                        Direction.Left, Direction.Up -> focus = ALLOW
                        Direction.Right, Direction.Down -> focus = NOT_NOW
                    }
                    if (focus != before) feel.report(Outcome.Moved)
                }
                Action.Enter -> press(focus)
                Action.Back -> press(NOT_NOW, Outcome.WentBack)
                is Action.TapItem -> if (action.index == ALLOW || action.index == NOT_NOW) press(action.index)
                else -> Unit
            }
        }
    }

    private fun press(
        index: Int,
        outcome: Outcome = Outcome.Activated,
    ) {
        focus = index
        isClosed = true
        feel.report(outcome)
        if (index == ALLOW) onAllow()
    }

    companion object {
        const val ALLOW = 0
        const val NOT_NOW = 1
    }
}

/**
 * The offer's card state while it shows, else null. It is raised once [HomeCallbacks.usageAccessOfferDue] says so, when the home is
 * idle, in front and not leaving for Settings, and [blocked] is false (the home-app question or a "What's new" card); only then is it
 * counted as shown. [isOpen] is saved, so a re-created Activity gets the card back without counting it again.
 */
@Composable
internal fun rememberUsageAccessOffer(
    callbacks: HomeCallbacks,
    isOpen: MutableState<Boolean>,
    isHomeIdle: State<Boolean>,
    blocked: Boolean,
): UsageAccessOfferState? {
    val due by callbacks.usageAccessOfferDue.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnShown by rememberUpdatedState(callbacks.onUsageAccessOfferShown)
    val currentOnAllow by rememberUpdatedState(callbacks.onUsageAccessAllow)
    val currentIsLeaving by rememberUpdatedState(callbacks.isLeaving)
    var offer by remember { mutableStateOf<UsageAccessOfferState?>(null) }
    fun newState() = UsageAccessOfferState(onAllow = { currentOnAllow() })
    // Back after a re-creation: the card was counted when it first showed.
    LaunchedEffect(isOpen.value) { if (isOpen.value && offer == null) offer = newState() }
    LaunchedEffect(offer) {
        val shown = offer ?: return@LaunchedEffect
        snapshotFlow { shown.isClosed }.first { it }
        isOpen.value = false
    }
    LaunchedEffect(due, blocked) {
        if (!due || blocked) return@LaunchedEffect
        snapshotFlow { isHomeIdle.value }.collectLatest { isIdle ->
            if (!isIdle || isOpen.value) return@collectLatest
            // The home stays composed under Settings; a card counted as shown there would be lost. Waits on the state, not on frames: a hidden home must cost no frame.
            // Settings opening while it waits for the start sends it back to waiting, not to the next idle change.
            do {
                snapshotFlow { currentIsLeaving() }.first { !it }
                lifecycle.withStarted { }
            } while (currentIsLeaving())
            if (!isHomeIdle.value) return@collectLatest
            offer = newState()
            isOpen.value = true
            currentOnShown()
        }
    }
    return offer?.takeIf { !it.isClosed && isOpen.value }
}

/** The card over the main screen; it takes the router's actions while it shows. */
@Composable
internal fun UsageAccessOfferCard(
    state: UsageAccessOfferState,
    input: InputRouter,
    letters: ButtonLetters,
    isLeaving: () -> Boolean = { false },
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentIsLeaving by rememberUpdatedState(isLeaving)
    HearFeel(state.feel)
    LaunchedEffect(state, input, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // The main screen lies hidden under Settings: a press made there is Settings' own.
            input.actions.collect { if (!currentIsLeaving()) state.onAction(it) }
        }
    }
    val send = remember(input) { { action: Action -> input.send(action) } }
    UsageAccessOfferLayout(state.focus, letters, send)
}

/** A `sel2` card in setup's language over the dimmed main screen, with its own hint bar; it scrolls when the text is large and the screen short. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UsageAccessOfferLayout(
    focus: Int,
    letters: ButtonLetters,
    onAction: (Action) -> Unit,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    Column(
        Modifier
            .fillMaxSize()
            .pageBackground(alpha = OFFER_SCRIM_ALPHA)
            // Taps outside the card land here, not on the main screen underneath.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(top = PlumDimens.stripMarginTop + PlumTheme.layout.topBarHeight + PlumDimens.barGap, bottom = PlumDimens.footerBelow)
            .testTag(UsageAccessOfferTags.CARD),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = PlumDimens.stripMarginSide), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .widthIn(max = PlumDimens.homeRolePromptWidth)
                    .raisedCard()
                    .testTag(UsageAccessOfferTags.SCROLL)
                    .verticalScroll(rememberScrollState())
                    .padding(PlumDimens.homeRolePromptPadding),
                verticalArrangement = Arrangement.spacedBy(PlumDimens.setupTitleGapWide),
            ) {
                PanelTitleWithDot(
                    text = stringResource(R.string.home_usage_offer_title),
                    style = type.setupTitle(WidthClass.Narrow).copy(color = colors.text),
                    titleModifier = Modifier.testTag(UsageAccessOfferTags.TITLE),
                )
                BasicText(text = stringResource(R.string.home_usage_offer_body), style = type.description.copy(color = colors.muted))
                Spacer(Modifier.height(PlumDimens.setupButtonSpacer))
                // Wraps: a long first button at the largest text must not squeeze the second one empty.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(PlumDimens.setupButtonGap),
                    verticalArrangement = Arrangement.spacedBy(PlumDimens.setupButtonGap),
                ) {
                    OfferButton(R.string.home_usage_offer_allow, UsageAccessOfferState.ALLOW, isPrimary = true, focus, onAction)
                    // The hint's "Not now": French's is short enough for both buttons to share a row at the largest text.
                    OfferButton(R.string.home_role_hint_not_now, UsageAccessOfferState.NOT_NOW, isPrimary = false, focus, onAction)
                }
            }
        }
        Spacer(Modifier.height(PlumDimens.footerGap))
        // Opaque: the main screen's own hints sit at the same place and would show through.
        HintBar(Modifier.pageBackground()) {
            Hint(
                keyCap = stringResource(letters.confirm),
                label = stringResource(R.string.home_role_hint_select),
                modifier = Modifier.hint(Action.Enter, onAction).testTag(UsageAccessOfferTags.HINT_SELECT),
            )
            Hint(
                keyCap = stringResource(letters.back),
                label = stringResource(R.string.home_role_hint_not_now),
                modifier = Modifier.hint(Action.Back, onAction).testTag(UsageAccessOfferTags.HINT_NOT_NOW),
            )
        }
    }
}

@Composable
private fun OfferButton(
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
        modifier = Modifier.bringIntoViewWhen(focus == index).tapItem(StripColumn.Games, index, onAction).testTag(UsageAccessOfferTags.button(index)),
    )
}

/** The main screen shows through, dimmed, as under the home-app question. */
private const val OFFER_SCRIM_ALPHA = 0.9f

internal object UsageAccessOfferTags {
    const val CARD = "usage_offer_card"
    const val SCROLL = "usage_offer_scroll"
    const val TITLE = "usage_offer_title"
    const val HINT_SELECT = "usage_offer_hint_select"
    const val HINT_NOT_NOW = "usage_offer_hint_not_now"

    fun button(index: Int): String = "usage_offer_button_$index"
}
