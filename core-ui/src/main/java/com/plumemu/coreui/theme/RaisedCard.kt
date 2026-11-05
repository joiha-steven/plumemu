package com.plumemu.coreui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The raised card the game panel and its questions stand on (docs/design/rules.md > Cards): the board's PANEL. Its fill is the
 * CSS gradient from [top] to [bottom]; [fill] is their mean, for what is drawn over the card as a flat colour (the slab's under-colour).
 * Dark: the background moved toward the cool surface ink (`CollarLook.surfaceInk`), [TOP_DARK] at the top and [BOTTOM_DARK] at the
 * bottom (the board's rgba(44,46,52,.92) and rgba(26,27,31,.94) over #111214 resolve to #2A2C31 and #191A1E); light: toward white,
 * [TOP_LIGHT] and [BOTTOM_LIGHT] (the board's #FBFAF7 and #F3F0EA). Derived from the background and the text only, so every colour
 * set has one. The edge and shadow shares are the board's.
 */
object RaisedCard {
    fun top(
        bg: Rgb,
        text: Rgb,
        dark: Boolean,
    ): Rgb = if (dark) ColorMath.mix(bg, CollarLook.surfaceInk(text), TOP_DARK) else ColorMath.mix(bg, WHITE, TOP_LIGHT)

    fun bottom(
        bg: Rgb,
        text: Rgb,
        dark: Boolean,
    ): Rgb = if (dark) ColorMath.mix(bg, CollarLook.surfaceInk(text), BOTTOM_DARK) else ColorMath.mix(bg, WHITE, BOTTOM_LIGHT)

    fun fill(
        bg: Rgb,
        text: Rgb,
        dark: Boolean,
    ): Rgb = ColorMath.mix(top(bg, text, dark), bottom(bg, text, dark), 0.5)

    fun shadowAlpha(dark: Boolean) = if (dark) SHADOW_DARK else SHADOW_LIGHT

    const val TOP_DARK = 0.12
    const val BOTTOM_DARK = 0.04
    const val TOP_LIGHT = 0.78
    const val BOTTOM_LIGHT = 0.35

    /** The CSS border: white at .14 with its top side at .22 on dark; the board's brown at .06 with a white top side on light. */
    const val EDGE_DARK = 0.14f
    const val EDGE_TOP_DARK = 0.22f
    const val EDGE_LIGHT = 0.06f
    const val SHADOW_DARK = 1.0
    const val SHADOW_LIGHT = 0.45

    /** The peak alpha of the near (`0 2px 4px`) and the far (`0 30px 60px`) shadow at strength 1 (Graphite; Paper is 0.45 of it, brown). */
    const val SHADOW_NEAR = 0.4
    const val SHADOW_FAR = 0.6
    private val WHITE = Rgb(0xFFFFFF)
}

/**
 * [RaisedCard] as Compose colours: the gradient [top] to [bottom] and its opaque mean [fill], the border sides [edge] with its top side
 * [edgeTop], and the [shadow] (its tint; alpha = the strength both shadows are scaled by).
 */
@Immutable
class CardLook(
    val fill: Color,
    val edge: Color,
    val shadow: Color,
    val top: Color = fill,
    val bottom: Color = fill,
    val edgeTop: Color = edge,
) {
    internal companion object {
        fun of(
            bg: Rgb,
            text: Rgb,
            dark: Boolean,
        ) = CardLook(
            fill = RaisedCard.fill(bg, text, dark).toComposeColor(),
            edge = if (dark) Color.White.copy(alpha = RaisedCard.EDGE_DARK) else CollarLook.SHADE_BROWN.toComposeColor().copy(alpha = RaisedCard.EDGE_LIGHT),
            shadow = FocusSlab.shadowTint(dark).toComposeColor().copy(alpha = RaisedCard.shadowAlpha(dark).toFloat()),
            top = RaisedCard.top(bg, text, dark).toComposeColor(),
            bottom = RaisedCard.bottom(bg, text, dark).toComposeColor(),
            edgeTop = if (dark) Color.White.copy(alpha = RaisedCard.EDGE_TOP_DARK) else Color.White,
        )
    }
}

/** Sizes of the raised card: the board's px / 1.5. */
object CardDimens {
    /** The board's 26 px corner. */
    val corner = 17.33.dp

    /** A small tile inside a card (the second screen's figures): the board's 18 px corner. */
    val tileCorner = 12.dp

    /** The CSS 1 px border (0.67 dp), drawn inside the card's box. */
    val edge = 0.67.dp

    /** The board's two shadows (`0 2px 4px` and `0 30px 60px`) as drop and Gaussian sigma (px / 1.5; a CSS blur of B is a sigma of B / 2). */
    val shadowNearDrop = 1.333.dp
    val shadowNearSigma = 1.333.dp
    val shadowFarDrop = 20.dp
    val shadowFarSigma = 20.dp

    /** Padding inside the game panel's card above the title (the board's 22 px less its 1 px border) and below the last row or button. */
    val padTop = 15.dp
    val padBottom = 15.dp

    /** The game panel's width: the board's menu card (440 px), and the wider card of the pickers that put a "Current" badge beside an emulator's name. */
    val menuWidth = 293.33.dp
    val pickerWidth = 360.dp

    /** The question card (the board's dialog): 10 px from title to body, 28 px from body to buttons, 16 px between buttons; body on a 28 px line at 19 px. */
    val questionBodyGap = 6.67.dp
    val questionButtonsGap = 18.67.dp
    val questionButtonSpacing = 10.67.dp
    const val QUESTION_BODY_LEADING = 28f / 19f

    /** Where a card's list scrolls under its title: the first stretch of the top fade is solid, so a sliver of a cut row never shows. */
    val listEdgeLead = 12.dp
}
