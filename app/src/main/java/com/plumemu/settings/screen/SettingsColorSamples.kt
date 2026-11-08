package com.plumemu.settings.screen

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    val style = PlumTheme.type.systemNameFocused.copy(color = colors.strong)
    // The name is decoration: where it does not fit beside the switch (Jost at the middle sizes in a narrow panel) the shorter sample takes its place.
    var isShort by remember(style) { mutableStateOf(false) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(PlumTheme.layout.rowHeight)
                .focusPillSample()
                .padding(horizontal = PlumDimens.gameRowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            stringResource(if (isShort) R.string.settings_sample_game_2_short else R.string.settings_sample_game_2),
            style = style,
            maxLines = 1,
            onTextLayout = { if (!isShort && (it.hasVisualOverflow || it.didOverflowWidth)) isShort = true },
            modifier = Modifier.weight(1f),
        )
        SetupSwitch(isOn = true, stateText = stringResource(R.string.settings_switch_on))
    }
}
