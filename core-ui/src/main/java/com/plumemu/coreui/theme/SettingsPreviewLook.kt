package com.plumemu.coreui.theme

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.FilterQuality
import com.plumemu.coreui.components.SoftImages
import com.plumemu.coreui.components.drawPlaced
import com.plumemu.coreui.components.drawSoft
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/** The look preview of Settings' right column (the board's `Settings.png`, CSS px / 1.5): its card and the parts drawn in it. */
object SettingsPreviewDimens {
    val width = 205.33.dp
    val height = 114.67.dp
    val corner = 12.dp

    /** The card's top, below the right column's top (the options' first row): the board's y 56. */
    val top = 9.dp

    /** The gap between the card and the title under it. */
    val gapBelow = 14.67.dp

    /** The list lines: left, top, width, height (all lines are [lineHeight] high, pills). */
    val lineLeft = 12.dp
    val lineHeight = 6.67.dp
    val lineTops = listOf(14.67.dp, 30.67.dp, 77.33.dp, 93.33.dp)
    val lineWidths = listOf(100.dp, 85.33.dp, 93.33.dp, 73.33.dp)

    /** The focused row's slab and its text bar. */
    val slabLeft = 6.67.dp
    val slabTop = 46.67.dp
    val slabWidth = 117.33.dp
    val slabHeight = 20.dp
    val slabCorner = 6.67.dp
    /** The focused slab's top-edge stroke, and the room above and below a sample line in the focused row. */
    val slabEdge = 0.67.dp

    /** The mini slab's drop shadow (`0 6px 14px` black .5: a drop and sigma = blur / 2) and its glow's sigma (`0 0 22px`). */
    val slabDrop = 4.dp * Depth.SHADOW_REACH
    val slabSigma = 4.67.dp * Depth.SHADOW_REACH
    val slabGlowSigma = 7.33.dp * Depth.GLOW_REACH
    val samplePad = 3.dp
    val barLeft = 13.33.dp
    val barWidth = 72.dp
    val barHeight = 5.33.dp

    /** The cover tile (from the right edge and the top) and the ball on its corner. */
    val tileRight = 12.dp
    val tileTop = 14.67.dp
    val tileWidth = 61.33.dp
    val tileHeight = 46.67.dp
    val tileCorner = 6.67.dp
    val ballRight = 4.dp
    val ballTop = 9.33.dp
    val ballDiameter = 30.67.dp

    /** The tile's radial gradient: centre as shares of its box; the radius is to the far corner. */
    const val TILE_CENTRE_X = 0.28f
    const val TILE_CENTRE_Y = 0.18f

    /** The ball's radial gradient: centre as shares of its box; the radius is to the far corner. */
    const val BALL_CENTRE_X = 0.34f
    const val BALL_CENTRE_Y = 0.30f

    /** The tile's shadows (`0 8px 18px` black and `0 0 30px` glow, blur / 2 = sigma) and the card's (`inset 0 2px 6px`, `0 1px 0` white). */
    val tileShadowDrop = 5.33.dp
    val tileShadowSigma = 6.dp
    val tileGlowSigma = 10.dp
    val frameInsetDrop = 1.33.dp
    val frameInsetSigma = 2.dp
    val frameLineDrop = 0.67.dp

    /** The board's right-column text (title 22 px, description 16 px on 24, gap 8 px) as shares of the panel title (16) and body (13) sizes. */
    const val BOARD_TITLE_SCALE = 14.67f / 16f
    const val BOARD_BODY_SCALE = 10.67f / 13f
    val boardTextGap = 5.33.dp
}

/** The colours of the look preview, from the theme's own tokens; computed where they are drawn. */
object SettingsPreviewLook {
    /** The card's well: the background pulled toward black (dark themes, the board's #0C0D0F on #101114), toward the text on a light one. */
    fun well(
        bg: Color,
        text: Color,
    ): Color = if (bg.luminance() < LIGHT_BELOW) lerp(bg, Color.Black, WELL_DARKEN) else lerp(bg, text, WELL_LIGHT_MIX)

    /** The list lines: the text colour, faint. */
    fun line(text: Color): Color = text.copy(alpha = LINE_ALPHA)

    /** The cover tile's radial gradient (the board's #3470D8, #1F4FA8, #143677 on the blue accent): the accent darkened, at 0, 45 and 100 %. */
    fun tileStops(accent: Color): List<Color> = TILE_DARKEN.map { mix(accent, Color.Black, it) }

    /** The tile's glow (`rgba(47,99,196,0.30)`): the accent a little darker, at 30 %. */
    fun tileGlow(accent: Color): Color = mix(accent, Color.Black, GLOW_DARKEN).copy(alpha = GLOW_ALPHA)

    /** The ball's radial gradient (the board's #FFE08A, #F4BA34, #C88910): the accent lightened, itself, and darkened, at 0, 42 and 100 %. */
    fun ballStops(accent: Color): List<Color> = listOf(mix(accent, Color.White, BALL_LIGHTEN), accent, mix(accent, Color.Black, BALL_DARKEN))

    /** The shadows' own colours: black at the board's alphas (half of them on a [light] theme, where the same weight reads as dirt); the card's bottom line is white. */
    fun tileShadow(light: Boolean): Color = Color.Black.copy(alpha = if (light) 0.25f else 0.5f)

    fun frameInset(light: Boolean): Color = Color.Black.copy(alpha = if (light) 0.35f else 0.7f)

    val frameLine: Color = Color.White.copy(alpha = 0.08f)

    val TILE_STOPS = listOf(0f, 0.45f, 1f)
    val BALL_STOPS = listOf(0f, 0.42f, 1f)

    /** [a] toward [b] by [t] channel by channel in sRGB: the board's blues are scaled so, where Compose's `lerp` (Oklab) darkens the blue too much. */
    private fun mix(
        a: Color,
        b: Color,
        t: Float,
    ) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, a.alpha)

    private const val LIGHT_BELOW = 0.5f
    private const val WELL_DARKEN = 0.13f
    private const val WELL_LIGHT_MIX = 0.05f
    const val LINE_ALPHA = 0.16f
    private val TILE_DARKEN = listOf(0.10f, 0.30f, 0.50f)
    private const val GLOW_DARKEN = 0.2f
    private const val GLOW_ALPHA = 0.3f
    private const val BALL_LIGHTEN = 0.45f
    private const val BALL_DARKEN = 0.38f
}

/** The card's bottom line (`0 1px 0` white): outside the card's shape, so it is drawn before the card's clip; the shape itself is cut out first. */
fun DrawScope.drawPreviewFrameLine(corner: Float) {
    val shape = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, CornerRadius(corner))) }
    clipPath(shape, ClipOp.Difference) {
        drawRoundRect(SettingsPreviewLook.frameLine, topLeft = Offset(0f, SettingsPreviewDimens.frameLineDrop.toPx()), size = size, cornerRadius = CornerRadius(corner))
    }
}

/** The card's inset shadow (`inset 0 2px 6px` black .7), inside the card's shape; draw it over the fill. */
fun DrawScope.drawPreviewInset(corner: Float, light: Boolean) {
    val w = Math.round(size.width)
    val h = Math.round(size.height)
    val look = SoftImages.Inset(SettingsPreviewLook.frameInset(light), SettingsPreviewDimens.frameInsetDrop.toPx(), SettingsPreviewDimens.frameInsetSigma.toPx())
    val placed = SoftImages.inset(w, h, corner, 0f, look, null) ?: return
    drawPlaced(placed, IntOffset.Zero, w, h)
}

/**
 * The cover tile and the ball on its corner as the board draws them: the tile's glow under its drop shadow under its radial gradient
 * (the box-shadow list's first layer is on top), then the ball's gradient.
 */
fun DrawScope.drawPreviewCover(accent: Color, light: Boolean) {
    val d = SettingsPreviewDimens
    val tileSize = Size(d.tileWidth.toPx(), d.tileHeight.toPx())
    val tileTopLeft = Offset(size.width - (d.tileRight + d.tileWidth).toPx(), d.tileTop.toPx())
    val corner = d.tileCorner.toPx()
    drawSoft(tileTopLeft, tileSize, corner, 0f, d.tileGlowSigma.toPx(), SettingsPreviewLook.tileGlow(accent), false)
    drawSoft(tileTopLeft, tileSize, corner, d.tileShadowDrop.toPx(), d.tileShadowSigma.toPx(), SettingsPreviewLook.tileShadow(light), false)
    val cx = tileSize.width * SettingsPreviewDimens.TILE_CENTRE_X
    val cy = tileSize.height * SettingsPreviewDimens.TILE_CENTRE_Y
    val tileCentre = tileTopLeft + Offset(cx, cy)
    // CSS `circle` sizes to the farthest corner.
    val far = kotlin.math.hypot(maxOf(cx, tileSize.width - cx), maxOf(cy, tileSize.height - cy))
    drawRoundRect(
        Brush.radialGradient(*SettingsPreviewLook.TILE_STOPS.zip(SettingsPreviewLook.tileStops(accent)).toTypedArray(), center = tileCentre, radius = far),
        tileTopLeft, tileSize, CornerRadius(corner),
    )
    val ball = d.ballDiameter.toPx()
    val ballTopLeft = Offset(size.width - (d.ballRight.toPx() + ball), d.ballTop.toPx())
    val ballCentre = ballTopLeft + Offset(ball * SettingsPreviewDimens.BALL_CENTRE_X, ball * SettingsPreviewDimens.BALL_CENTRE_Y)
    val ballFar = ball * kotlin.math.hypot(1f - SettingsPreviewDimens.BALL_CENTRE_X, 1f - SettingsPreviewDimens.BALL_CENTRE_Y)
    drawCircle(
        Brush.radialGradient(*SettingsPreviewLook.BALL_STOPS.zip(SettingsPreviewLook.ballStops(accent)).toTypedArray(), center = ballCentre, radius = ballFar),
        radius = ball / 2,
        center = ballTopLeft + Offset(ball / 2, ball / 2),
    )
}

/**
 * The mini focused row as the board's `Settings.png` card draws it: the slab's gradient and border (the theme's own, [PlumComposeColors.slab]) in the
 * box exactly, no overhang; its drop shadow and glow are clipped out of the box first (the slab is translucent on the board), the glow under the drop.
 */
fun DrawScope.drawPreviewSlab(colors: PlumComposeColors) {
    val d = SettingsPreviewDimens
    val corner = d.slabCorner.toPx()
    val look = colors.slab
    val shape = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(corner))) }
    clipPath(shape, ClipOp.Difference) {
        drawSoft(Offset.Zero, size, corner, 0f, d.slabGlowSigma.toPx(), colors.slabGlow, false)
        look.near?.let { drawSoft(Offset.Zero, size, corner, d.slabDrop.toPx(), d.slabSigma.toPx(), it.color.copy(alpha = it.color.alpha * SLAB_DROP_SCALE), false) }
    }
    val stops = look.stops
    drawRoundRect(Brush.verticalGradient(0f to stops[0], FocusSlab.MID_AT to stops[1], 1f to stops[2], startY = 0f, endY = size.height), cornerRadius = CornerRadius(corner))
    val edge = d.slabEdge.toPx()
    // The real slab's rim: lit at two opposite corners.
    drawRoundRect(look.rim(Offset.Zero, Offset(size.width, size.height)), topLeft = Offset(edge / 2, edge / 2), size = Size(size.width - edge, size.height - edge), cornerRadius = CornerRadius(corner - edge / 2), style = Stroke(edge))
}

/** The mini slab's lift against the theme's near layer: a small slab needs it a little stronger to read. */
private const val SLAB_DROP_SCALE = 1.43f
