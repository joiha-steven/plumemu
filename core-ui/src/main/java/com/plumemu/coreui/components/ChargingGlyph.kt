package com.plumemu.coreui.components

import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import kotlin.math.floor
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme

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
    /** The font the text beside it is drawn in: the person's, unless the screen fixes its own (the in-game card draws in Plum Sans). */
    font: PlumFont = PlumTheme.type.font,
) {
    val density = LocalDensity.current
    val height = with(density) { (style.fontSize.toPx() * font.digitHeightEm).toDp() }
    val width = height * WIDTH_PER_HEIGHT
    // The row centres the box on the text line; the digits' ink sits a little off that line centre.
    // Whole pixels: a fractional top edge fades into an extra row of ink and the glyph reads taller and off the digits. Rounded down: the
    // bolt's box is a row shorter than the digits' ink, so a round-to-nearest shift left it a row low at the top bar's 13.
    val shift = with(density) { floor(style.fontSize.toPx() * font.digitCentreBelowLineCentreEm) }
    Canvas(modifier.graphicsLayer { translationY = shift }.size(width, height).semantics { contentDescription = description }) {
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
