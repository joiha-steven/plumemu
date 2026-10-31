package com.plumemu.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.plumemu.R
import com.plumemu.account.SignInBody
import com.plumemu.coreinput.Action
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreinput.Column as StripColumn

/**
 * "Box art from ScreenScraper": first the question, Use my account and Not
 * now side by side like the second screen's Use it and Not now; after Use my account the sign-in
 * form, Sign in and Skip. The title block says what it is and that the account is the person's
 * own, free, from screenscraper.fr. Taps reach the model through the router like setup's other
 * controls (`TapItem` with the control's or the form's item place).
 */
@Composable
internal fun ColumnScope.BoxArtStepContent(
    state: SetupUiState,
    onAction: (Action) -> Unit,
) {
    val form = state.boxArt
    if (form == null) {
        BoxArtQuestion(state, onAction)
        return
    }
    SignInBody(
        form = form,
        leaveLabel = R.string.setup_box_art_skip,
        onTap = { index -> onAction(Action.TapItem(StripColumn.Games, index)) },
        onAction = onAction,
        gap = PlumDimens.setupContentGap,
        modifier = Modifier.testTag(SetupTags.BOX_ART),
    )
}

/** At the content's top, as the second screen's: on wide screens the buttons line up with the title beside them. */
@Composable
private fun BoxArtQuestion(
    state: SetupUiState,
    onAction: (Action) -> Unit,
) {
    Row(Modifier.testTag(SetupTags.BOX_ART_QUESTION), horizontalArrangement = Arrangement.spacedBy(PlumDimens.setupButtonGap)) {
        SetupButton(
            text = stringResource(R.string.setup_box_art_use),
            isPrimary = true,
            isFocused = state.focus == SetupControl.UseBoxArt,
            modifier = Modifier.setupControl(state, SetupControl.UseBoxArt, onAction),
        )
        SetupButton(
            text = stringResource(R.string.setup_box_art_not_now),
            isPrimary = false,
            isFocused = state.focus == SetupControl.SkipBoxArt,
            modifier = Modifier.setupControl(state, SetupControl.SkipBoxArt, onAction),
        )
    }
}
