package com.plumemu.secondscreen

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import com.plumemu.coreui.components.drawFocusHalo
import com.plumemu.coreui.components.drawInsetShadow
import com.plumemu.coreui.theme.BackdropGlowDimens
import com.plumemu.coreui.theme.INSET_ALPHA_LIGHT
import com.plumemu.coreui.theme.MeterTrackToken
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.PlumTheme

/**
 * A meter: a sunken track with [share] (0..1) of it filled from the accent to `accentLight` and lit by `partGlow` at its end
 * (the boards' `linear-gradient(90deg, accent, accentLight)` with `0 0 8px glow`, clipped to the track). Draws the whole thing in one block.
 */
internal fun DrawScope.drawMeter(
    colors: PlumComposeColors,
    share: Float,
) {
    drawTrack(colors)
    if (share <= 0f) return
    val fill = Size(size.width * share, size.height)
    val glow = colors.partGlow.copy(alpha = colors.partGlow.alpha * GLOW_SHARE)
    val track = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(size.height / 2))) }
    clipPath(track) {
        drawFocusHalo(Offset.Zero, fill, size.height / 2, 0f, glow, BackdropGlowDimens.meterGlow.toPx())
        drawRoundRect(Brush.horizontalGradient(listOf(colors.accent, colors.accentLight), startX = 0f, endX = fill.width), size = fill, cornerRadius = CornerRadius(size.height / 2))
    }
}

/**
 * The track: `meterTrack` with the boards' `inset 0 3px 6px` shade ([MeterTrackToken.shade]), its `inset 0 -1px 0` lit bottom line and
 * the `0 1px 0` lip under it ([MeterTrackToken.lip]).
 */
private fun DrawScope.drawTrack(colors: PlumComposeColors) {
    val dark = colors.inset.alpha > INSET_ALPHA_LIGHT
    val radius = size.height / 2
    val line = BackdropGlowDimens.meterLine.toPx()
    val track = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius))) }
    clipPath(track, ClipOp.Difference) { drawRoundRect(MeterTrackToken.lip(dark), Offset(0f, line), size, CornerRadius(radius)) }
    drawRoundRect(colors.meterTrack, cornerRadius = CornerRadius(radius))
    // CSS draws the first shadow of a list on top: the shade lies over the lit line.
    drawInsetShadow(Offset.Zero, size, radius, -line, 0f, MeterTrackToken.bottomLine)
    drawInsetShadow(Offset.Zero, size, radius, BackdropGlowDimens.meterShadeDrop.toPx(), BackdropGlowDimens.meterShadeSigma.toPx(), MeterTrackToken.shade(dark))
}

/** A bar of the latest period: the accent at its foot, `accentLight` at its head (the boards' `linear-gradient(180deg, accentLight, accent)`). */
@Composable
internal fun Modifier.latestBarFill(shape: RoundedCornerShape): Modifier {
    val colors = PlumTheme.colors
    return drawBehind { drawOutline(shape.createOutline(size, layoutDirection, this), Brush.verticalGradient(listOf(colors.accentLight, colors.accent))) }
}

/** The boards show the glow about half as strong as the glow colour at its peak (measured), so a meter's end is lit, not blazing. */
private const val GLOW_SHARE = 0.5f
