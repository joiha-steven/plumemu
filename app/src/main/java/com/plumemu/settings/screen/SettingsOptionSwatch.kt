package com.plumemu.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import com.plumemu.R
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coreui.components.borderDrawn
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme

/** Which theme colour an option's swatch follows while the colour sliders preview. */
internal enum class LiveSwatch { Background, Text, Accent }

/** An option row's colour dot and name. The dot's colour is read while drawing, so a colour slider's preview moves it without recomposing the row. */
@Composable
internal fun SwatchValue(
    value: OptionValue.Swatch,
    style: TextStyle,
    color: () -> Color,
) {
    val colors = PlumTheme.colors
    val preview = LocalColorPreview.current
    // A derived state, so a move recomposes nothing. Only a dragged accent of its own hides the By
    // system disc; a Background or Text drag keeps it.
    val accentDragged by remember(preview) { derivedStateOf { preview.appearance?.accent.let { it != null && it !is ColorSetting.BySystem } } }
    Row(horizontalArrangement = Arrangement.spacedBy(PlumDimens.settingsValueGap), verticalAlignment = Alignment.CenterVertically) {
        // A dragged accent replaces the By system disc while the finger is on a slider.
        if (value.bySystem && !accentDragged) {
            BySystemDisc(Modifier.size(PlumDimens.settingsValueDot).clip(CircleShape).testTag(SettingsTags.VALUE_BY_SYSTEM))
        } else {
            // With no overlay on, the dot shows the stored colour, as the row's value says.
            val shown: () -> Color = {
                val live = value.live
                if (live == null || !preview.isActive) {
                    value.color
                } else {
                    when (live) {
                        LiveSwatch.Background -> colors.bg
                        LiveSwatch.Text -> colors.text
                        LiveSwatch.Accent -> colors.accent
                    }
                }
            }
            Box(
                Modifier
                    .size(PlumDimens.settingsValueDot)
                    .drawBehind { drawOutline(CircleShape.createOutline(size, layoutDirection, this), shown()) }
                    .then(if (value.hasRing) Modifier.borderDrawn(PlumDimens.switchOffRing, { colors.chip }) else Modifier),
            )
        }
        BasicText(stringResource(R.string.settings_value_opens, value.name), style = style, color = color, maxLines = 1)
    }
}
