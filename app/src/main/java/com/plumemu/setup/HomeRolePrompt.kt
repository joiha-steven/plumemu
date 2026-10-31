package com.plumemu.setup

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
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.WidthClass
import com.plumemu.settings.ButtonLetters
import com.plumemu.sound.Feel
import com.plumemu.sound.HearFeel
import com.plumemu.sound.Outcome

/** What the home screen question needs from the Activity and the settings. */
internal interface HomeRoleActions {
    /** Plumemu already is the home app (then nothing is asked). */
    fun isHomeApp(): Boolean

    /** Opens Android's screen for picking the home app; false when this device has none. */
    fun requestHomeRole(): Boolean

    /** Stores `flags.home_role_asked`: the question is never asked again. */
    fun markAsked()
}

/**
 * "Use Plumemu as your home screen?", asked once after the library first opens. Either answer
 * marks it asked; Yes opens Android's own screen (or, where there is none, says where to look).
 */
@Stable
internal class HomeRolePromptState(private val actions: HomeRoleActions) {
    /** 0 = Yes (or OK under the message), 1 = Not now. */
    var focus by mutableIntStateOf(0)
        private set
    var showsUnavailable by mutableStateOf(false)
        private set
    var isClosed by mutableStateOf(false)
        private set

    private val buttonCount: Int get() = if (showsUnavailable) 1 else 2

    /** Says what each action did, for the sounds (set by the screen). */
    val feel = Feel()

    fun onAction(action: Action) {
        if (isClosed) return
        feel.act {
            when (action) {
                is Action.Move -> {
                    val before = focus
                    when (action.direction) {
                        Direction.Left, Direction.Up -> focus = (focus - 1).coerceAtLeast(0)
                        Direction.Right, Direction.Down -> focus = (focus + 1).coerceAtMost(buttonCount - 1)
                    }
                    if (focus != before) feel.report(Outcome.Moved)
                }
                Action.Enter -> {
                    activate(focus)
                    feel.report(Outcome.Activated)
                }
                Action.Back -> {
                    if (showsUnavailable) isClosed = true else activate(NOT_NOW)
                    feel.report(Outcome.WentBack)
                }
                is Action.TapItem ->
                    if (action.index in 0 until buttonCount) {
                        activate(action.index)
                        feel.report(Outcome.Activated)
                    }
                else -> Unit
            }
        }
    }

    private fun activate(index: Int) {
        focus = index
        if (showsUnavailable) {
            isClosed = true
            return
        }
        actions.markAsked()
        if (index == YES && !actions.requestHomeRole()) {
            showsUnavailable = true
            focus = 0
        } else {
            isClosed = true
        }
    }

    private companion object {
        const val YES = 0
        const val NOT_NOW = 1
    }
}

/** The question over the main screen; it takes the router's actions while it shows. */
@Composable
internal fun HomeRolePrompt(
    state: HomeRolePromptState,
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
    HomeRolePromptLayout(state.showsUnavailable, state.focus, letters, send)
}

/** A `sel2` panel in setup's language over the dimmed main screen, with its own hint bar. */
@Composable
internal fun HomeRolePromptLayout(
    showsUnavailable: Boolean,
    focus: Int,
    letters: ButtonLetters,
    onAction: (Action) -> Unit,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bg.copy(alpha = SCRIM_ALPHA))
            // Taps outside the panel land here, not on the main screen underneath.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(top = PlumDimens.stripMarginTop + PlumTheme.layout.topBarHeight + PlumDimens.barGap, bottom = PlumDimens.stripMarginBottom)
            .testTag(HomeRoleTags.PROMPT),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = PlumDimens.stripMarginSide), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .widthIn(max = PlumDimens.homeRolePromptWidth)
                    .background(colors.sel2, RoundedCornerShape(PlumDimens.cornerSetupChoice))
                    // Scrolls when the text is large and the screen short; the focused button comes into view.
                    .testTag(HomeRoleTags.SCROLL)
                    .verticalScroll(rememberScrollState())
                    .padding(PlumDimens.homeRolePromptPadding),
                verticalArrangement = Arrangement.spacedBy(PlumDimens.setupTitleGapWide),
            ) {
                BasicText(text = stringResource(R.string.home_role_title), style = type.setupTitle(WidthClass.Narrow).copy(color = colors.text))
                val body = if (showsUnavailable) R.string.home_role_unavailable else R.string.home_role_body
                BasicText(text = stringResource(body), style = type.description.copy(color = colors.muted))
                Spacer(Modifier.height(PlumDimens.setupButtonSpacer))
                Row(horizontalArrangement = Arrangement.spacedBy(PlumDimens.setupButtonGap)) {
                    if (showsUnavailable) {
                        PromptButton(R.string.home_role_ok, index = 0, isPrimary = true, focus, onAction)
                    } else {
                        PromptButton(R.string.home_role_yes, index = 0, isPrimary = true, focus, onAction)
                        PromptButton(R.string.home_role_not_now, index = 1, isPrimary = false, focus, onAction)
                    }
                }
            }
        }
        Spacer(Modifier.height(PlumDimens.barGap))
        // Opaque: the main screen's own hints sit at the same place and would show through.
        HintBar(Modifier.background(colors.bg)) {
            Hint(
                keyCap = stringResource(letters.confirm),
                label = stringResource(if (showsUnavailable) R.string.home_role_ok else R.string.home_role_hint_select),
                modifier = Modifier.hint(Action.Enter, onAction),
            )
            if (!showsUnavailable) {
                Hint(
                    keyCap = stringResource(letters.back),
                    label = stringResource(R.string.home_role_hint_not_now),
                    modifier = Modifier.hint(Action.Back, onAction).testTag(HomeRoleTags.HINT_NOT_NOW),
                )
            }
        }
    }
}

@Composable
private fun PromptButton(
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
        modifier = Modifier.bringIntoViewWhen(focus == index).tapItem(StripColumn.Games, index, onAction).testTag(HomeRoleTags.button(index)),
    )
}

/** The main screen shows through, dimmed, so the question reads as asked about it. */
private const val SCRIM_ALPHA = 0.9f

internal object HomeRoleTags {
    const val PROMPT = "home_role_prompt"
    const val SCROLL = "home_role_scroll"
    const val HINT_NOT_NOW = "home_role_hint_not_now"

    fun button(index: Int): String = "home_role_button_$index"
}
