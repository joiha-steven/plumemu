package com.plumemu.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreui.theme.PlumDimens

/**
 * "Use the second screen?": only on a device with a second
 * screen nobody has decided about, after the folder step while the scan runs. The title and its one
 * line come from the title block; here only Use it and Not now, side by side like the home screen
 * question's buttons.
 */
@Composable
internal fun ColumnScope.SecondScreenStepContent(
    state: SetupUiState,
    onAction: (Action) -> Unit,
) {
    // At the content's top: on wide screens the buttons line up with the title beside them.
    Row(horizontalArrangement = Arrangement.spacedBy(PlumDimens.setupButtonGap)) {
        SetupButton(
            text = stringResource(R.string.setup_second_screen_use),
            isPrimary = true,
            isFocused = state.focus == SetupControl.UseSecondScreen,
            modifier = Modifier.setupControl(state, SetupControl.UseSecondScreen, onAction),
        )
        SetupButton(
            text = stringResource(R.string.setup_second_screen_not_now),
            isPrimary = false,
            isFocused = state.focus == SetupControl.SkipSecondScreen,
            modifier = Modifier.setupControl(state, SetupControl.SkipSecondScreen, onAction),
        )
    }
}
