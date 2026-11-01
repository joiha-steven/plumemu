package com.plumemu.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
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
import com.plumemu.coreui.components.PanelTitleWithDot
import com.plumemu.coreui.components.panelChrome
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.WidthClass
import com.plumemu.settings.ButtonLetters
import com.plumemu.setup.SetupButton
import com.plumemu.sound.Feel
import com.plumemu.sound.HearFeel
import com.plumemu.sound.Outcome
import com.plumemu.update.UpdateManifest
import com.plumemu.update.ui.NotesScroll
import com.plumemu.update.ui.ReleaseNotesTags
import com.plumemu.update.ui.ScrollingReleaseNotes

/**
 * The card shown once over an idle main screen after Plumemu updated itself: "What's new in 0.1.1", the release's notes
 * and OK. Up and Down scroll the notes (the button stays in view), A, B or a tap on OK close it. [release] is
 * what `Updates.whatsNew` held; the caller has already told `Updates` it was shown, so it never comes again.
 */
@Stable
internal class WhatsNewState(val release: UpdateManifest) {
    var isClosed by mutableStateOf(false)
        private set

    /** Up and Down presses, applied by the notes (the card's size is theirs to know). */
    val notes = NotesScroll()

    val feel = Feel()

    fun onAction(action: Action) {
        if (isClosed) return
        feel.act {
            when (action) {
                is Action.Move ->
                    when (action.direction) {
                        Direction.Up, Direction.Down -> {
                            if (notes.press(action.direction == Direction.Down)) feel.report(Outcome.Moved)
                        }
                        else -> Unit
                    }
                Action.Enter, Action.Back -> close(if (action == Action.Back) Outcome.WentBack else Outcome.Activated)
                is Action.TapItem -> if (action.index == OK) close(Outcome.Activated)
                else -> Unit
            }
        }
    }

    private fun close(outcome: Outcome) {
        isClosed = true
        feel.report(outcome)
    }

    companion object {
        const val OK = 0
    }
}

/** The card over the main screen; it takes the router's actions while it shows. */
@Composable
internal fun WhatsNewCard(
    state: WhatsNewState,
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
    WhatsNewCardLayout(state, letters, send)
}

/** A `sel2` card over the dimmed main screen, with its own hint bar; the notes scroll, the button stays in view. */
@Composable
internal fun WhatsNewCardLayout(
    state: WhatsNewState,
    letters: ButtonLetters,
    onAction: (Action) -> Unit,
) {
    val colors = PlumTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bg.copy(alpha = WHATS_NEW_SCRIM_ALPHA))
            // Taps outside the card land here, not on the main screen underneath.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(top = PlumDimens.stripMarginTop + PlumTheme.layout.topBarHeight + PlumDimens.barGap, bottom = PlumDimens.stripMarginBottom)
            .testTag(WhatsNewTags.CARD),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = PlumDimens.stripMarginSide), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .widthIn(max = PlumDimens.homeRolePromptWidth)
                    .panelChrome({ colors.sel2 })
                    .padding(PlumDimens.homeRolePromptPadding),
                verticalArrangement = Arrangement.spacedBy(PlumDimens.setupTitleGapWide),
            ) {
                PanelTitleWithDot(
                    text = stringResource(R.string.home_whatsnew_title, state.release.versionName),
                    style = PlumTheme.type.setupTitle(WidthClass.Narrow).copy(color = colors.text),
                    titleModifier = Modifier.testTag(WhatsNewTags.TITLE),
                )
                WhatsNewNotes(state, Modifier.weight(1f, fill = false))
                Spacer(Modifier.height(PlumDimens.setupButtonGap))
                SetupButton(
                    text = stringResource(R.string.home_role_ok),
                    isPrimary = true,
                    isFocused = true,
                    modifier = Modifier.tapItem(StripColumn.Games, WhatsNewState.OK, onAction).testTag(WhatsNewTags.OK),
                )
            }
        }
        Spacer(Modifier.height(PlumDimens.barGap))
        // Opaque: the main screen's own hints sit at the same place and would show through.
        HintBar(Modifier.background(colors.bg)) {
            Hint(
                keyCap = stringResource(letters.confirm),
                label = stringResource(R.string.home_role_ok),
                modifier = Modifier.hint(Action.Enter, onAction).testTag(WhatsNewTags.HINT_OK),
            )
            Hint(
                keyCap = stringResource(letters.back),
                label = stringResource(R.string.home_hint_back),
                modifier = Modifier.hint(Action.Back, onAction).testTag(WhatsNewTags.HINT_BACK),
            )
        }
    }
}

/** The notes, scrolling by touch and by Up and Down presses ([WhatsNewState.notes]). */
@Composable
private fun WhatsNewNotes(
    state: WhatsNewState,
    modifier: Modifier,
) {
    ScrollingReleaseNotes(state.release, state.notes, WHATS_NEW_NOTES_TAGS, modifier)
}

private val WHATS_NEW_NOTES_TAGS = ReleaseNotesTags(WhatsNewTags.NOTES, "release_notes_heading", "release_notes_note", "release_notes_dot")

/** The main screen shows through, dimmed, as under the home-app question. */
private const val WHATS_NEW_SCRIM_ALPHA = 0.9f

internal object WhatsNewTags {
    const val CARD = "whats_new_card"
    const val TITLE = "whats_new_title"
    const val NOTES = "whats_new_notes"
    const val OK = "whats_new_ok"
    const val HINT_OK = "whats_new_hint_ok"
    const val HINT_BACK = "whats_new_hint_back"
}
