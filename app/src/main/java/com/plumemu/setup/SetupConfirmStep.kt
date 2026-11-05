package com.plumemu.setup

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.plumemu.R
import com.plumemu.coredata.settings.ConfirmButtonSetting
import com.plumemu.coreinput.Action

/**
 * "Which button confirms?": two rows like every other step, Use A and Use B. A (or a tap) chooses the focused row,
 * B goes back. First visit: either row stores its answer and goes on. Reached again by Back: a line says which
 * button is active (SetupModel.chooseConfirmRow).
 */
@Composable
internal fun ColumnScope.ConfirmStepContent(
    state: SetupUiState,
    onAction: (Action) -> Unit,
) {
    listOf(SetupControl.ConfirmKeep to R.string.setup_confirm_keep, SetupControl.ConfirmUseB to R.string.setup_confirm_use_b).forEach { (control, label) ->
        SetupInfoRow(
            title = stringResource(label),
            body = null,
            isFocused = state.focus == control,
            modifier = Modifier.setupControl(state, control, onAction),
        )
    }
    // A message (a failed save) takes the line's place, as everywhere.
    if (state.message != null || state.confirmAnswer != null) {
        val active = if (state.confirmAnswer == ConfirmButtonSetting.Bottom) R.string.setup_confirm_active_b else R.string.setup_confirm_active_a
        StepNote(state.message, active)
    }
}
