package com.plumemu.coreui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A text field's well in its collar (the design board's Fields). */
object FieldDimens {
    /** The well's corner: 14 board px (1.5x); a field shorter than twice this is a pill. */
    val radius = 9.3.dp

    /** Gap between a field's label and the field's collar, and between two field rows: the board's 16 px row gap. */
    val gap = (32f / 3f).dp

    /** The well's side padding: board 18 px. */
    val wellPadX = 12.dp

    /** The well's height at Medium text (board: 56 px). */
    val wellHeight = (112f / 3f).dp

    /** How much of the text size's growth the well takes: the line grows in full, the well around it by a quarter, so two fields still fit over the keyboard at the largest text. */
    const val WELL_GROWTH = 0.25f

    /** The well's height at text scale [textScale]. */
    fun wellHeightAt(textScale: Float): Dp = wellHeight * (1f + (textScale - 1f) * WELL_GROWTH)

    /** How far from the background toward black the well is: board Graphite #07080A on #111214; Paper's fill is #E0DBD0 (its ring is the collar's #CBC4B7). */
    private const val WELL_DARK = 0.60f
    private const val WELL_LIGHT = 0.07f

    /** Mixed per channel (the board's way), not with `lerp`, which blends in Oklab and lands much darker. */
    /** A field in error also tints its well this far toward the danger colour, so it differs from a focused field even when the accent is red. */
    private const val ERROR_TINT = 0.16f

    /** [well] tinted toward [danger], mixed per channel like the well. */
    fun errorWell(bg: Color, danger: Color): Color = tint(well(bg), danger)

    /** [w] tinted toward [danger] by the error share, per channel. */
    fun tint(w: Color, danger: Color): Color {
        return Color(w.red + (danger.red - w.red) * ERROR_TINT, w.green + (danger.green - w.green) * ERROR_TINT, w.blue + (danger.blue - w.blue) * ERROR_TINT, w.alpha)
    }

    private val SHADE = CollarLook.SHADE_BROWN.toComposeColor()

    fun well(bg: Color): Color {
        // Dark: toward black; light: toward the board's brown shade (CollarLook.SHADE_BROWN), which keeps Paper's wells warm.
        if (bg.luminance() < 0.5f) {
            val keep = 1f - WELL_DARK
            return Color(bg.red * keep, bg.green * keep, bg.blue * keep, bg.alpha)
        }
        return Color(
            bg.red + (SHADE.red - bg.red) * WELL_LIGHT,
            bg.green + (SHADE.green - bg.green) * WELL_LIGHT,
            bg.blue + (SHADE.blue - bg.blue) * WELL_LIGHT,
            bg.alpha,
        )
    }
}
