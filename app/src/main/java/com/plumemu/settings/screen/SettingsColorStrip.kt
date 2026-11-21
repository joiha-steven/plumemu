package com.plumemu.settings.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.plumemu.R
import com.plumemu.coreui.components.drawLoupe
import com.plumemu.coreui.components.plainLayer
import com.plumemu.coreui.components.focusPill
import com.plumemu.coreui.theme.ColorShades
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.Rgb
import com.plumemu.coreui.theme.ShadeRole
import com.plumemu.coreui.theme.toComposeColor
import com.plumemu.setup.bringIntoViewWhen
import kotlin.math.roundToInt

/*
 * The Color row and the Shade row of a colour page. Color is a row like the Color code row (the
 * resting fill, the slab when focused) holding a bar of one segment per strip stop, the stop in effect
 * carrying a loupe filled with the colour shown; Shade is the shades as the presets' keys. What the
 * stops and shades are: core-ui ColorShades; the cursor and the writes: SettingsCustomColor.
 * Design rule: design/rules.md › Settings, "Colour strip and shades".
 */

/** Stands for the colour code in the row's spoken text until semantics are read. */
private const val SPOKEN_CODE = "\u0000"

/** The test tags of the colour page's parts that SettingsTags (at the file-length limit) does not list. */
internal const val SHADE_ROW_TAG = "settings_shade_row"
internal const val STRIP_BAR_TAG = "settings_strip_bar"
internal const val STRIP_LOUPE_TAG = "settings_strip_loupe"
internal const val COLOUR_PRESETS_TAG = "settings_colour_presets"
internal const val COLOUR_NAME_TAG = "settings_colour_name"

/** The Color row at panel index [index]: [stop] is the stop in effect and [shown] the colour shown, both read inside so a move recomposes this row alone. */
@Composable
internal fun ColorStripRow(
    role: ShadeRole,
    stop: () -> Int,
    shown: () -> Rgb,
    isFocused: Boolean,
    index: Int,
    touch: SettingsTouch,
) {
    val colors = PlumTheme.colors
    val label = stringResource(R.string.settings_color_strip_label)
    // The colour code is put in when semantics are read, so a move does not recompose this row for it.
    val spokenFormat = stringResource(R.string.settings_color_row_spoken, label, SPOKEN_CODE)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .bringIntoViewWhen(isFocused, ring = PlumDimens.focusRingOutset)
                .focusPill(isFocused, rest = { colors.sel2 })
                .heightIn(min = PlumTheme.layout.settingsHexRowHeight)
                .semantics { contentDescription = spokenFormat.replace(SPOKEN_CODE, shown().toHex()) }
                .testTag(SettingsTags.panelItem(index))
                .tapTarget { touch.onPanelItem(index) }
                .padding(horizontal = PlumDimens.settingsPanelRowPadding, vertical = PlumDimens.settingsSliderRowPad),
        verticalArrangement = Arrangement.spacedBy(PlumDimens.titleLineGap),
    ) {
        RowLabel(label)
        StripBar(role, stop, shown, touch)
    }
}

/** The label of a colour page's row (Color, Shade, Color code): one style for all three. */
@Composable
internal fun RowLabel(
    text: String,
    maxLines: Int = 1,
) {
    val colors = PlumTheme.colors
    BasicText(text, style = PlumTheme.type.meta, color = { colors.muted }, maxLines = maxLines)
}

/** Where the knob of stop [i] is centred on a bar [width] wide: half a [knob] in from the edge at the first stop and at the last. */
internal fun stopCentre(
    i: Int,
    width: Float,
    knob: Float,
    count: Int,
): Float = knob / 2 + (width - knob) * i / (count - 1)

/** The stop under a finger at [x] of a bar [width] wide whose end stops' knobs sit half of [knob] in from its edges. */
internal fun stopAt(
    x: Float,
    width: Float,
    knob: Float,
    count: Int,
): Int = (((x - knob / 2) / (width - knob)) * (count - 1)).roundToInt().coerceIn(0, count - 1)

@Composable
private fun StripBar(
    role: ShadeRole,
    stop: () -> Int,
    shown: () -> Rgb,
    touch: SettingsTouch,
) {
    val colors = PlumTheme.colors
    val count = ColorShades.stopCount(role)
    val segments = remember(role) { List(count) { ColorShades.stripColor(role, it).toComposeColor() } }
    val motionOn by rememberUpdatedState(PlumTheme.motion.isOn)
    // A finger's drag lifts the loupe above the strip (translation only); a tap or the D-pad keeps it seated.
    var dragging by remember { mutableStateOf(false) }
    val lift = remember { Animatable(0f) }
    LaunchedEffect(dragging) { lift.animateTo(if (dragging) 1f else 0f, if (motionOn) tween(PlumMotion.RAISE_MS, easing = PlumMotion.EaseOut) else snap()) }
    var width by remember { mutableIntStateOf(0) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(PlumDimens.settingsStripBand)
            .onSizeChanged { width = it.width }
            .testTag(STRIP_BAR_TAG)
            .pointerInput(role, touch) {
                detectTapGestures { touch.onStop(stopAt(it.x, size.width.toFloat(), PlumDimens.settingsSliderThumb.toPx(), count)) }
            }.pointerInput(role, touch) {
                // A drag after horizontal touch slop moves the stop under the finger; a vertical swipe is left to the page's scroll.
                fun stopOf(x: Float) = stopAt(x, size.width.toFloat(), PlumDimens.settingsSliderThumb.toPx(), count)
                try {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragging = true
                            touch.onStopDrag(stopOf(it.x))
                        },
                        onDragEnd = {
                            dragging = false
                            touch.onStopEnd()
                        },
                        onDragCancel = {
                            dragging = false
                            touch.onStopEnd()
                        },
                        onHorizontalDrag = { change, _ ->
                            touch.onStopDrag(stopOf(change.position.x))
                            change.consume()
                        },
                    )
                } finally {
                    // The row leaving the composition (B mid-drag) cancels this coroutine without onDragCancel: the finger's colour is still let go of.
                    if (dragging) {
                        dragging = false
                        touch.onStopEnd()
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val knob = PlumDimens.settingsSliderThumb.toPx()
            val barHeight = PlumDimens.settingsSliderTrack.toPx()
            val top = (size.height - barHeight) / 2
            // Stop i is centred where its loupe rests; its segment runs flush to the middle between it and its neighbours, on whole pixels so no seam shows.
            fun centre(i: Int) = stopCentre(i, w, knob, count)
            fun edge(i: Int) = if (i <= 0) 0f else if (i >= count) w else ((centre(i - 1) + centre(i)) / 2).roundToInt().toFloat()
            val bar = Path().apply { addRoundRect(RoundRect(0f, top, w, top + barHeight, CornerRadius(barHeight / 2))) }
            clipPath(bar) {
                segments.forEachIndexed { i, colour ->
                    drawRect(colour, topLeft = Offset(edge(i), top), size = Size(edge(i + 1) - edge(i), barHeight))
                }
            }
            // A hairline round the bar keeps a pale hue (yellow) from melting into a light row.
            val line = PlumDimens.settingsSwatchEdge.toPx()
            drawRoundRect(colors.chip, topLeft = Offset(line / 2, top + line / 2), size = Size(w - line, barHeight - line), cornerRadius = CornerRadius((barHeight - line) / 2), style = Stroke(line))
        }
        Canvas(
            Modifier
                .size(PlumDimens.settingsSliderThumb)
                .testTag(STRIP_LOUPE_TAG)
                .plainLayer {
                    val knob = size.width
                    translationX = stopCentre(stop().coerceIn(0, count - 1), width.toFloat(), knob, count) - knob / 2
                    translationY = -PlumDimens.settingsLoupeLift.toPx() * lift.value
                },
        ) { drawLoupe(shown().toComposeColor()) }
    }
}

/** The Shade row: the shades as the presets' keys ([corner] is the presets' for this setting), the one equal to [shown] carrying the check, the one at [focusedIndex] the focus. Shade i is panel index [firstIndex] + i. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ShadeRow(
    shades: () -> List<Rgb>,
    shown: () -> Rgb,
    firstIndex: Int,
    focusedIndex: Int,
    corner: Dp,
    touch: SettingsTouch,
) {
    val colors = PlumTheme.colors
    val list = shades()
    val now = shown()
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(SHADE_ROW_TAG)
                .drawWithContent {
                    drawRoundRect(colors.sel2, cornerRadius = CornerRadius(PlumDimens.cornerRow.toPx()))
                    drawContent()
                }.padding(horizontal = PlumDimens.settingsPanelRowPadding, vertical = PlumDimens.settingsSliderRowPad),
        verticalArrangement = Arrangement.spacedBy(PlumDimens.titleLineGap),
    ) {
        RowLabel(stringResource(R.string.settings_color_shade_label))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(PlumDimens.settingsPresetSwatchGap),
            verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPresetSwatchGap),
            modifier = Modifier.padding(vertical = PlumDimens.settingsSwatchInset),
        ) {
            list.forEachIndexed { i, rgb ->
                val focused = focusedIndex == firstIndex + i
                Swatch(
                    rgb.toComposeColor(),
                    corner,
                    isFocused = focused,
                    Modifier.swatchTap(firstIndex + i, touch).bringIntoViewWhen(focused, ring = PlumDimens.focusRingOutset),
                    size = Modifier.size(PlumDimens.settingsPresetSwatch),
                    bleed = true,
                    selected = rgb == now,
                )
            }
        }
    }
}
