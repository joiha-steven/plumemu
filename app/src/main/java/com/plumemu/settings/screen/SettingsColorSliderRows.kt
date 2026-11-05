package com.plumemu.settings.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.plumemu.R
import com.plumemu.coreui.components.drawFlatFace
import com.plumemu.coreui.components.flatFaceShift
import com.plumemu.coreui.theme.FlatFace
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.Rgb
import com.plumemu.coreui.theme.toComposeColor
import com.plumemu.setup.bringIntoViewWhen
import com.plumemu.coreui.components.focusPill

/*
 * Hue, Saturation and Lightness rows of the "+" page, between the palette and
 * the Color code row. Each row is a label and the value on the row-text lines, over a track that
 * draws its own gradient; only values that read can be stopped on (SettingsColorSliders.kt), so every
 * track draws its whole length, the stretches that cannot be stopped on dimmed.
 * Design rule: design/rules.md › Settings, "Colour sliders".
 */

/** The rows for the page's sliders at panel indexes [firstIndex] and on; [hsl] is where the thumbs are and [finger] where a finger holds one (both read inside the rows), [focusedIndex] the focused row (-1: none). */
@Composable
internal fun ColorSliderRows(
    hsl: () -> Hsl,
    reach: SliderReach,
    firstIndex: Int,
    focusedIndex: Int,
    touch: SettingsTouch,
    finger: () -> Pair<ColorSlider, Float>? = { null },
) {
    ColorSlider.entries.forEachIndexed { i, slider ->
        ColorSliderRow(slider, hsl, reach, isFocused = focusedIndex == firstIndex + i, index = firstIndex + i, touch, finger)
    }
}

@Composable
private fun ColorSliderRow(
    slider: ColorSlider,
    hslNow: () -> Hsl,
    reach: SliderReach,
    isFocused: Boolean,
    index: Int,
    touch: SettingsTouch,
    fingerNow: () -> Pair<ColorSlider, Float>?,
) {
    // Read here, not by the panel: a move of a thumb then recomposes these three rows and nothing around them.
    val hsl = hslNow()
    val fingerAt = fingerNow()?.takeIf { it.first == slider }?.second
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val label = stringResource(sliderLabel(slider))
    val value = hsl[slider]
    val valueText = stringResource(if (slider == ColorSlider.Hue) R.string.settings_slider_degrees else R.string.settings_slider_percent, value)
    val spoken = stringResource(R.string.settings_slider_spoken, label, valueText)
    val thumb = PlumDimens.settingsSliderThumb
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .bringIntoViewWhen(isFocused, ring = PlumDimens.focusRingOutset)
                .focusPill(isFocused, rest = { colors.sel2 })
                .heightIn(min = PlumTheme.layout.settingsHexRowHeight)
                .semantics { contentDescription = spoken }
                .testTag(SettingsTags.panelItem(index))
                .padding(horizontal = PlumDimens.settingsPanelRowPadding, vertical = PlumDimens.settingsSliderRowPad)
                .sliderGesture(index, thumb.value, touch),
        verticalArrangement = Arrangement.spacedBy(PlumDimens.titleLineGap),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PlumDimens.settingsPresetSwatchGap)) {
            BasicText(label, style = type.meta.copy(color = colors.muted), maxLines = 2, modifier = Modifier.weight(1f))
            BasicText(valueText, style = type.meta.copy(color = colors.text), maxLines = 1)
        }
        // The gradient and which stretches read depend on the other two values only, not on this thumb.
        val others = hsl.with(slider, 0)
        val stretches = remember(slider, others, reach) { reach.drawnStretches(others, slider) }
        // What the track draws is its own: the rainbow at full saturation and mid lightness, grey to the hue's full colour at mid lightness, black through the colour to white; so a dark colour never makes a track black.
        val drawn = remember(slider, others) { drawnBase(slider, others) }
        val stops = remember(slider, drawn) { Array(slider.count) { drawn.with(slider, it * slider.step).rgb().toComposeColor() } }
        SliderTrack(stops, TrackSpans.of(stretches, slider.max), slider.max, fraction = fingerAt ?: (value / slider.max.toFloat()), thumbColor = hsl.rgb(), held = fingerAt != null)
    }
}

/**
 * What a track paints: the whole gradient, [dimmed] over every value from the first to the
 * last, and [bright] over the runs a thumb can rest on. Never a gap, so a Text or Accent track is as long as a
 * Background one; which values count as reachable is [SliderReach]'s, unchanged.
 */
internal data class TrackSpans(
    val dimmed: IntRange,
    val bright: List<IntRange>,
) {
    companion object {
        fun of(
            reachable: List<IntRange>,
            maxValue: Int,
        ) = TrackSpans(0..maxValue, reachable)
    }
}

/** The colour a track's gradient varies from: [others] holds the other two values; Hue and Saturation tracks ignore the lightness (and Hue the saturation) so they always show. */
private fun drawnBase(
    slider: ColorSlider,
    others: Hsl,
): Hsl =
    when (slider) {
        ColorSlider.Hue -> Hsl(0, VIVID_SATURATION, MID_LIGHTNESS)
        ColorSlider.Saturation -> others.copy(lightness = MID_LIGHTNESS)
        ColorSlider.Lightness -> others
    }

private const val VIVID_SATURATION = 100
private const val MID_LIGHTNESS = 50

private fun sliderLabel(slider: ColorSlider): Int =
    when (slider) {
        ColorSlider.Hue -> R.string.settings_slider_hue
        ColorSlider.Saturation -> R.string.settings_slider_saturation
        ColorSlider.Lightness -> R.string.settings_slider_lightness
    }

/**
 * The bar from one row-text line to the other, the thumb's edges on those lines at both ends (its
 * centre runs from half a thumb in to half a thumb short of the end). [stops] are the colours at
 * each step; a stretch outside [stretches] (runs of whole values up to [maxValue]) that cannot be stopped on is drawn dimmed ([dimUnreachable]) or not at all.
 */
@Composable
private fun SliderTrack(
    stops: Array<Color>,
    spans: TrackSpans,
    maxValue: Int,
    fraction: Float,
    thumbColor: Rgb,
    held: Boolean,
) {
    val colors = PlumTheme.colors
    // The thumb goes down into its edge while a finger holds or drags it; with animations off it does at once.
    val pressed = animateFloatAsState(
        targetValue = if (held) 1f else 0f,
        animationSpec = if (PlumTheme.motion.isOn) tween(if (held) PlumMotion.PRESS_IN_MS else PlumMotion.KEY_PRESS_OUT_MS, easing = PlumMotion.EaseOut) else snap(),
        label = "slider thumb",
    )
    val thumbFill = thumbColor.toComposeColor()
    val face = remember(thumbFill) { FlatFace.accent(thumbFill) }
    Canvas(Modifier.fillMaxWidth().height(PlumDimens.settingsSliderThumb)) {
        val width = size.width
        val thumbSize = PlumDimens.settingsSliderThumb.toPx()
        val barHeight = PlumDimens.settingsSliderTrack.toPx()
        val top = (size.height - barHeight) / 2
        val usable = width - thumbSize
        // [stretches] are in whole values of the slider, [stops] one colour per D-pad step.
        val unitsPerStop = maxValue / (stops.size - 1)
        fun centre(unit: Int) = thumbSize / 2 + usable * unit / maxValue
        val brush = Brush.horizontalGradient(colorStops = Array(stops.size) { centre(it * unitsPerStop) / width to stops[it] }, startX = 0f, endX = width)
        val bar = Path().apply { addRoundRect(RoundRect(0f, top, width, top + barHeight, CornerRadius(barHeight / 2))) }
        clipPath(bar) {
            // The whole gradient first, dimmed; the stretches a thumb can rest on are drawn over it at full strength.
            val dimmed = spans.dimmed
            val dimLeft = if (dimmed.first == 0) 0f else (centre(dimmed.first) + centre(dimmed.first - 1)) / 2
            val dimRight = if (dimmed.last == maxValue) width else (centre(dimmed.last) + centre(dimmed.last + 1)) / 2
            clipRect(dimLeft, top, dimRight, top + barHeight) { drawRect(brush, alpha = PlumDimens.SETTINGS_SLIDER_DIMMED_ALPHA) }
            for (run in spans.bright) {
                val left = if (run.first == 0) 0f else (centre(run.first) + centre(run.first - 1)) / 2
                val right = if (run.last == maxValue) width else (centre(run.last) + centre(run.last + 1)) / 2
                clipRect(left, top, right, top + barHeight) { drawRect(brush) }
            }
            // The track is sunken: a 1 dp inset shadow on its top edge.
            drawRect(colors.inset, topLeft = Offset(0f, top), size = Size(width, PlumDimens.insetShadow.toPx()))
        }
        val at = Offset(centre(0) + usable * fraction, size.height / 2)
        val ringWidth = PlumDimens.settingsSliderThumbRing.toPx()
        // The thumb is a key: flat face in the colour shown, its edge and shadow under it, the ring as before.
        val left = at.x - thumbSize / 2
        val faceTop = at.y - thumbSize / 2
        val down = pressed.value
        inset(left, faceTop, width - left - thumbSize, size.height - faceTop - thumbSize) { drawFlatFace(face, press = down) }
        drawCircle(colors.strong, radius = thumbSize / 2 - ringWidth / 2, center = at.copy(y = at.y + flatFaceShift(down, 0f)), style = Stroke(ringWidth))
    }
}

/**
 * A tap on the row sets the slider where the finger is, and a drag does after horizontal touch slop:
 * [SettingsTouch.onSlider] gets the row and the place on the track (0..1, thumb-centre range), and
 * [SettingsTouch.onSliderEnd] when the finger lifts or the gesture is cancelled. A
 * vertical swipe is left alone so the page scrolls (it starts on a row as often as not).
 */
private fun Modifier.sliderGesture(
    index: Int,
    thumbDp: Float,
    touch: SettingsTouch,
): Modifier =
    pointerInput(index, touch) {
        val thumbPx = thumbDp * density
        fun fractionOf(x: Float) = ((x - thumbPx / 2) / (size.width - thumbPx)).coerceIn(0f, 1f)
        detectTapGestures {
            touch.onSlider(index, fractionOf(it.x))
            touch.onSliderEnd()
        }
    }.pointerInput(index, touch) {
        val thumbPx = thumbDp * density
        fun fractionOf(x: Float) = ((x - thumbPx / 2) / (size.width - thumbPx)).coerceIn(0f, 1f)
        var dragging = false
        try {
            detectHorizontalDragGestures(
                onDragStart = {
                    dragging = true
                    touch.onSlider(index, fractionOf(it.x))
                },
                onDragEnd = {
                    dragging = false
                    touch.onSliderEnd()
                },
                onDragCancel = {
                    dragging = false
                    touch.onSliderEnd()
                },
                onHorizontalDrag = { change, _ ->
                    touch.onSlider(index, fractionOf(change.position.x))
                    change.consume()
                },
            )
        } finally {
            // The row leaving the composition (B mid-drag) cancels this coroutine without onDragCancel: the finger's colour must still be let go of.
            if (dragging) touch.onSliderEnd()
        }
    }
