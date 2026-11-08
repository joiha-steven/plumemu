package com.plumemu.coreui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.plumemu.coreui.theme.ChoiceDimens
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumTheme

/**
 * One of a few options as a sunken pill tray in a [CollarSize.Segment] collar (V2 board "Segmented"). The [selected]
 * segment is a raised cap that slides to its place ([PlumMotion.DURATION_MS], at once when motion is off); the others are plain
 * text. [focused] lights the collar's groove and halo. When a gamepad's left/right would land on a segment other than the
 * selected one ([focusedIndex]), that segment gets the quiet accent tint of a focused row behind its label. Off ([enabled]
 * false) is a flat cap and muted text. Tapping a segment calls [onSelect] with its index.
 */
@Composable
fun PlumSegmented(
    options: List<String>,
    selected: Int,
    focused: Boolean,
    focusedIndex: Int?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val layout = PlumTheme.layout
    val trayPad = ChoiceDimens.segmentTrayPad
    // Each segment's left and width in the row, read only while drawing.
    val lefts = remember(options.size) { FloatArray(options.size) }
    val widths = remember(options.size) { FloatArray(options.size) }
    val placed = remember { mutableStateOf(0) }
    val slide = remember { Animatable(selected.toFloat()) }
    val motionOn = PlumTheme.motion.isOn
    LaunchedEffect(selected, motionOn) {
        slide.animateTo(selected.toFloat(), if (motionOn) tween(PlumMotion.DURATION_MS, easing = PlumMotion.Decelerate) else snap())
    }
    val showFocusMark = focused && enabled && focusedIndex != null && focusedIndex != selected
    Row(
        modifier
            // LIP and WELL (the groove is the tray: the group inside it is transparent).
            .collar(CollarSize.Segment, focused = focused && enabled)
            .padding(trayPad)
            .drawBehind {
                placed.value // redraw once the segments are placed
                val n = options.size
                if (n == 0) return@drawBehind
                val h = size.height
                if (showFocusMark && focusedIndex != null && focusedIndex in 0 until n) {
                    val r = CornerRadius(h / 2)
                    drawRoundRect(colors.pill, Offset(lefts[focusedIndex], 0f), Size(widths[focusedIndex], h), r)
                }
                val p = slide.value.coerceIn(0f, (n - 1).toFloat())
                val a = p.toInt().coerceAtMost(n - 1)
                val b = (a + 1).coerceAtMost(n - 1)
                val t = p - a
                val left = lefts[a] + (lefts[b] - lefts[a]) * t
                val w = widths[a] + (widths[b] - widths[a]) * t
                val seat = colors.collar.seat
                if (enabled) drawCssCap(seat.buttonCap, Offset(left, 0f), Size(w, h)) else drawCssCap(seat.offCap, Offset(left, 0f), Size(w, h), press = 1f, moveFace = false)
            }
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ChoiceDimens.segmentGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { i, text ->
            val on = i == selected
            Box(
                Modifier
                    .heightIn(min = ChoiceDimens.segmentHeight * layout.textScale)
                    .onPlaced {
                        lefts[i] = it.positionInParent().x
                        widths[i] = it.size.width.toFloat()
                        placed.value = lefts.contentHashCode() * 31 + widths.contentHashCode()
                    }
                    .selectable(
                        selected = on,
                        enabled = enabled,
                        role = Role.RadioButton,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(i) },
                    )
                    .padding(horizontal = ChoiceDimens.segmentPaddingX),
                contentAlignment = Alignment.Center,
            ) {
                val color =
                    when {
                        !enabled -> colors.muted
                        on -> colors.strong
                        else -> colors.meta
                    }
                BasicText(
                    text,
                    style = type.settingsOption.copy(color = color, fontWeight = if (on) FontWeight.SemiBold else null),
                    maxLines = 1,
                )
            }
        }
    }
}
