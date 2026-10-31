package com.plumemu.settings.screen

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.plumemu.R
import com.plumemu.coreui.components.focusPillSample
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.setup.SetupSwitch

// What the colour panels draw under their swatches: the focused colour's name line and Accent's sample row.

/** The focused colour's name, and its hex code at the right when there is one, on the row-text line. */
@Composable
internal fun ColorNameLine(
    name: String,
    hex: String?,
) {
    val colors = PlumTheme.colors
    Row(Modifier.fillMaxWidth().onRowTextLine()) {
        BasicText(name, style = PlumTheme.type.meta.copy(color = colors.text), modifier = Modifier.weight(1f))
        if (hex != null) BasicText(hex, style = PlumTheme.type.meta.copy(color = colors.muted))
    }
}

/** The board's focused row with a switch under the accent swatches. */
@Composable
internal fun AccentSampleRow() {
    val colors = PlumTheme.colors
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(PlumTheme.layout.rowHeight)
                .focusPillSample()
                .padding(horizontal = PlumDimens.gameRowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(stringResource(R.string.settings_sample_game_2), style = PlumTheme.type.systemNameFocused.copy(color = colors.strong), maxLines = 1, modifier = Modifier.weight(1f))
        SetupSwitch(isOn = true, stateText = stringResource(R.string.settings_switch_on))
    }
}
