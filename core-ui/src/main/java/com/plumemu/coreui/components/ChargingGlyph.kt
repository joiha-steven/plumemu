package com.plumemu.coreui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import com.plumemu.coreui.theme.PlumTheme
import kotlin.math.roundToInt

/*
 * The charging mark of the top bar: a filled bolt drawn on a Canvas like the Wi-Fi glyph (no image
 * file, no icon library). Its ink is as tall as the digits of the bar's text (PlumFont.digitHeightEm)
 * and centred on them, so it scales with the text size; its colour is the text's.
 */

/**
 * Bolt as tall as the digits of [style] (its ink fills its box), [color]ed like the text it sits by and read while
 * drawing; [description] is what a screen reader says.
 */
@Composable
fun ChargingGlyph(
    style: TextStyle,
    color: () -> Color,
    description: String,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val font = PlumTheme.type.font
    val height = with(density) { (style.fontSize.toPx() * font.digitHeightEm).toDp() }
    val width = height * WIDTH_PER_HEIGHT
    // The row centres the box on the text line; the digits' ink sits a little off that line centre.
    val shift = with(density) { (style.fontSize.toPx() * font.digitCentreBelowLineCentreEm).roundToInt() }
    Canvas(modifier.offset { IntOffset(0, shift) }.size(width, height).semantics { contentDescription = description }) {
        val bolt =
            Path().apply {
                BOLT.forEachIndexed { index, (x, y) ->
                    if (index == 0) moveTo(x * size.width, y * size.height) else lineTo(x * size.width, y * size.height)
                }
                close()
            }
        drawPath(bolt, color())
    }
}

private const val WIDTH_PER_HEIGHT = 0.62f

/**
 * The bolt's corners as fractions of its box, clockwise from the top: its ends are short flat edges on the top and
 * bottom of the box, so the ink reaches both (a pointed tip anti-aliases to nothing and the bolt looks shorter than the digits).
 */
private val BOLT =
    listOf(
        0.56f to 0f,
        0.96f to 0f,
        0.64f to 0.42f,
        0.98f to 0.42f,
        0.34f to 1f,
        0.18f to 1f,
        0.38f to 0.58f,
        0.02f to 0.58f,
    )
