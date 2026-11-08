package com.plumemu.coreui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.pow
import com.plumemu.coreui.theme.Depth
import com.plumemu.coreui.theme.CollarDimens
import com.plumemu.coreui.theme.CollarLook
import com.plumemu.coreui.theme.CssShadow
import com.plumemu.coreui.theme.SeatLook
import com.plumemu.coreui.theme.FocusHalo
import com.plumemu.coreui.theme.KeyboardCollarDimens
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.SwitchSliderDimens

/** The three collar sizes (the V2 look): [CollarDimens] for a control, a small key, a box cover. */
enum class CollarSize(
    val lip: Dp,
    val lipBottom: Dp,
    val groove: Dp,
    val grooveBottom: Dp,
) {
    Control(CollarDimens.lip, CollarDimens.lipBottom, CollarDimens.groove, CollarDimens.grooveBottom),
    Key(CollarDimens.keyLip, CollarDimens.keyLipBottom, CollarDimens.keyGroove, CollarDimens.keyGrooveBottom),

    /** A hint key's collar: thinner groove than a swatch's. */
    Hint(CollarDimens.hintLip, CollarDimens.hintLipBottom, CollarDimens.hintGroove, CollarDimens.hintGrooveBottom),

    /** The segmented control's collar (the tray well is its own padding inside it). */
    Segment(CollarDimens.segmentLip, CollarDimens.segmentLipBottom, CollarDimens.segmentGroove, CollarDimens.segmentGrooveBottom),

    /** A text field's collar. */
    Field(CollarDimens.fieldLip, CollarDimens.fieldLipBottom, CollarDimens.fieldGroove, CollarDimens.fieldGrooveBottom),
    Cover(CollarDimens.coverLip, CollarDimens.coverLipBottom, CollarDimens.coverGroove, CollarDimens.coverGrooveBottom),

    /** The switch's collar: the track sits straight in the lip, no groove ([SwitchSliderDimens]). */
    Switch(SwitchSliderDimens.switchLip, SwitchSliderDimens.switchLipBottom, 0.dp, 0.dp),

    /** A thin well and no lip or drop shadow: the on-screen keyboard's keys ([KeyboardCollarDimens]). */
    Well(0.dp, 0.dp, KeyboardCollarDimens.WELL_GROOVE, KeyboardCollarDimens.WELL_GROOVE_BOTTOM),
    ;

    /** What the collar adds around the part: lip plus groove. */
    val sidePadding: Dp get() = lip + groove
    val topPadding: Dp get() = lip + groove
    val bottomPadding: Dp get() = lipBottom + grooveBottom

    val padding: PaddingValues get() = PaddingValues(start = lip + groove, top = lip + groove, end = lip + groove, bottom = lipBottom + grooveBottom)

    /** The lip's corner past the groove's: the lip itself, so the rings are concentric (a cover's too). */
    val lipCornerAdd: Dp get() = lip

    /** The outer corner (px) of this collar round a part of corner [partCorner] px in a [size] px box. */
    fun outerCorner(density: Density, partCorner: Float, size: Size): Float =
        with(density) { minOf(partCorner + groove.toPx() + lipCornerAdd.toPx(), minOf(size.width, size.height) / 2) }
}

private const val SHADOW_LAYERS = 3


/**
 * The collar (docs/design/rules.md › Collar) in the whole bounds of the DrawScope: [collarSize] of lip and groove
 * around a part whose own corner is [cornerRadius] px (the outer corner is that plus groove plus lip, so the rings are
 * concentric). Drawn back to front: the shadow (three stacked rounded rects), the lip (the darker bottom band, the body, then the
 * body moved down by a hairline so the lighter top shows as a crescent), then the groove ring. The part is drawn over the
 * groove by the caller, inside the padding [CollarSize.padding].
 *
 * [focus] (0..1, so it can animate) blends the groove to [accent] and draws the focus halo ([glow], `PlumComposeColors.partGlow`:
 * the accent at 30 % on a light theme) outside the lip, faded by it. [grooveOverride] replaces the groove colour (an error's danger
 * colour) and wins over the focus blend; the halo still shows. Plain filled shapes, no blur and no layer, one draw block.
 */
fun DrawScope.drawCollar(
    look: CollarLook,
    collarSize: CollarSize,
    cornerRadius: Float,
    focus: Float = 0f,
    grooveOverride: Color? = null,
    accent: Color = Color.Transparent,
    glow: Color = Color.Transparent,
    focusRing: Dp = 0.dp,
) {
    // A box cover sits in the same lip and well as every part (docs/design/rules.md › Cover collar); only its sizes differ.
    val seat = if (collarSize == CollarSize.Cover) look.seat.coverGlass else look.seat
    drawSeatCollar(seat, collarSize, cornerRadius, focus, grooveOverride, accent, glow, focusRing)
}

/**
 * The kit boards' collar, layer by layer (design/rules.md › Seated parts): the focus glow `0 0 22px` (faded by [focus]), the LIP
 * (radial gradient and its six layers), then the WELL inside it (`#0F1012`, `inset 0 1px 2px .6`), lit toward [accent] by [focus] with
 * `inset 0 1px 2px .35` and its own `0 0 12px` glow; [grooveOverride] (an error) keeps the rest inset. A [CollarSize.Well] (the keyboard) has no lip
 * and its lit slot glows `0 0 22px`. Soft layers are cached images ([drawCssShadows]); the focus states cross-fade by alpha, so an animated focus
 * makes no new image.
 */
private fun DrawScope.drawSeatCollar(
    seat: SeatLook,
    collarSize: CollarSize,
    cornerRadius: Float,
    focus: Float,
    grooveOverride: Color?,
    accent: Color,
    glow: Color,
    focusRing: Dp,
) {
    if (PerfSwitches.noCollar) return
    // At rest it is one cached image; while that image is built (or when the images in use fill the budget) the layers below.
    if (focus <= 0f && grooveOverride == null && collarSize.lip > 0.dp && drawRestingCollar(seat, collarSize, cornerRadius)) return
    drawSeatCollarLayers(seat, collarSize, cornerRadius, focus, grooveOverride, accent, glow, focusRing)
}

/** [drawSeatCollar] layer by layer, uncached. */
internal fun DrawScope.drawSeatCollarLayers(
    seat: SeatLook,
    collarSize: CollarSize,
    cornerRadius: Float,
    focus: Float,
    grooveOverride: Color?,
    accent: Color,
    glow: Color,
    focusRing: Dp,
) {
    val lip = collarSize.lip.toPx()
    val lipBottom = collarSize.lipBottom.toPx()
    val groove = collarSize.groove.toPx()
    val w = size.width
    val h = size.height
    val outer = collarSize.outerCorner(this, cornerRadius, Size(w, h))
    val lit = if (glow.alpha > 0f) focus else 0f
    if (lip > 0f && lit > 0f) drawCssShadows(listOf(CssShadow(glow, 0.dp, CollarDimens.glowBlur, glow = true)), Offset.Zero, Size(w, h), outer, lit)
    if (focusRing > 0.dp && focus > 0f) drawCssShadows(listOf(CssShadow(accent, 0.dp, spread = focusRing, glow = true)), Offset.Zero, Size(w, h), outer, focus)
    if (focus > 0f && grooveOverride != null) {
        val ring = PlumDimens.focusRingWidth.toPx()
        drawRoundRect(accent.copy(alpha = accent.alpha * focus), Offset(-ring / 2, -ring / 2), Size(w + ring, h + ring), CornerRadius(outer + ring / 2), style = strokeOf(ring))
    }
    if (lip > 0f) drawLip(seat, Offset.Zero, Size(w, h), outer)
    if (groove <= 0f) return
    // Whole pixels, as the cached shadow images: the fill and its inset meet without a seam.
    val at = Offset(Math.round(lip).toFloat(), Math.round(lip).toFloat())
    val grooveSize = Size(Math.round(w - lip).toFloat() - at.x, Math.round(h - lipBottom).toFloat() - at.y)
    val grooveCorner = minOf(cornerRadius + groove, grooveSize.minDimension / 2)
    val grooveGlow = if (lip > 0f) CollarDimens.grooveGlowBlur else CollarDimens.glowBlur
    if (lit > 0f && grooveOverride == null) drawCssShadows(listOf(CssShadow(glow, 0.dp, grooveGlow, glow = true)), at, grooveSize, grooveCorner, lit)
    val fill = grooveOverride ?: if (focus > 0f) lerp(seat.well, accent, focus) else seat.well
    drawRoundRect(fill, at, grooveSize, CornerRadius(grooveCorner))
    val shown = if (grooveOverride != null) 0f else focus
    if (shown < 1f) drawCssShadows(listOf(seat.wellShade), at, grooveSize, grooveCorner, 1f - shown)
    if (shown > 0f) drawCssShadows(listOf(seat.focusShade), at, grooveSize, grooveCorner, shown)
}

/**
 * The focus halo of a collar whose outer edge is the rounded rectangle at [topLeft] of [size] (px), faded by [focus] (0..1): what
 * [drawCollar] draws itself, for a parent that draws it where the part's own clip would cut it. [cornerRadius] is the part's corner, as [drawCollar]'s.
 */
fun DrawScope.drawCollarHalo(
    collarSize: CollarSize,
    cornerRadius: Float,
    topLeft: Offset,
    size: Size,
    focus: Float,
    glow: Color,
) {
    if (focus <= 0f || glow.alpha <= 0f || PerfSwitches.noGlow) return
    val outer = collarSize.outerCorner(this, cornerRadius, size)
    // The kit's `0 0 22px` glow as the lit collar draws it: one cached Gaussian image, outside the collar only.
    drawCssShadows(listOf(CssShadow(glow, 0.dp, CollarDimens.glowBlur, glow = true)), topLeft, size, outer, focus)
}

/**
 * The lit groove's own `0 0 12px` glow of a collar whose outer edge is the rounded rectangle at [topLeft] of [size] (px), faded by
 * [focus] (0..1): what [drawCollar] draws itself, for a parent that draws the halo outside a clip. Drawn over the lip, outside the
 * groove only (CSS never paints an outer shadow under its own box), so it may come after the part. [cornerRadius] is the part's corner.
 */
fun DrawScope.drawCollarGrooveGlow(
    collarSize: CollarSize,
    cornerRadius: Float,
    topLeft: Offset,
    size: Size,
    focus: Float,
    glow: Color,
) {
    if (focus <= 0f || glow.alpha <= 0f || PerfSwitches.noGlow || collarSize.groove <= 0.dp || collarSize.lip <= 0.dp) return
    val lip = collarSize.lip.toPx()
    // Whole pixels, as [drawSeatCollar] places the groove.
    val at = Offset(Math.round(topLeft.x + lip).toFloat(), Math.round(topLeft.y + lip).toFloat())
    val grooveSize = Size(Math.round(topLeft.x + size.width - lip).toFloat() - at.x, Math.round(topLeft.y + size.height - collarSize.lipBottom.toPx()).toFloat() - at.y)
    val grooveCorner = minOf(cornerRadius + collarSize.groove.toPx(), grooveSize.minDimension / 2)
    drawCssShadows(listOf(CssShadow(glow, 0.dp, CollarDimens.grooveGlowBlur, glow = true)), at, grooveSize, grooveCorner, focus)
}

/**
 * The haze round a focused cover's collar on a light theme (the board's blue-grey one, peak about 35 of 255 and reaching about 20 dp):
 * the accent at [CollarDimens.COVER_LIGHT_GLOW_ALPHA], blurred by [CollarDimens.coverLightGlowSigma], faded by [focus] (0..1), following the collar's rounded corners. One cached image, one draw.
 */
fun DrawScope.drawCoverLightGlow(
    collarSize: CollarSize,
    cornerRadius: Float,
    topLeft: Offset,
    size: Size,
    focus: Float,
    accent: Color,
) {
    if (focus <= 0f || PerfSwitches.noGlow) return
    val outer = collarSize.outerCorner(this, cornerRadius, size)
    // Drawn over the cover (the halo comes after its content), so the collar's own area is cut out.
    val collar = Path().apply { addRoundRect(RoundRect(Rect(topLeft, size), CornerRadius(outer))) }
    clipPath(collar, ClipOp.Difference) {
        drawSoft(topLeft, size, outer, 0f, CollarDimens.coverLightGlowSigma.toPx(), accent.copy(alpha = CollarDimens.COVER_LIGHT_GLOW_ALPHA * focus), true)
    }
}

/**
 * The drop shadow of a collar whose outer edge is the rounded rectangle at [topLeft] of [size] with corner [outer] (px): on a dark theme three
 * stacked layers growing out ([CollarDimens.shadowReach]); on a light one the board's soft Gaussian ([CollarLook.softShadow]), layers
 * placed where a blur of [CollarDimens.softShadowSigma] has equal shares left.
 */
internal fun DrawScope.drawCollarShadow(
    look: CollarLook,
    topLeft: Offset,
    size: Size,
    outer: Float,
) {
    if (PerfSwitches.noCollar) return
    if (look.softShadow) {
        val n = CollarDimens.SOFT_SHADOW_LAYERS
        val sigma = CollarDimens.softShadowSigma.toPx()
        val drop = CollarDimens.softShadowDrop.toPx()
        val layer = 1f - (1f - look.shadow.alpha).pow(1f / n)
        if (layer <= 0.0005f) return
        for (k in 0 until n) {
            val g = SOFT_SHADOW_EDGES[k] * sigma
            drawRoundRect(
                look.shadow.copy(alpha = layer),
                topLeft = Offset(topLeft.x - g, topLeft.y + drop - g),
                size = Size((size.width + 2 * g).coerceAtLeast(0f), (size.height + 2 * g).coerceAtLeast(0f)),
                cornerRadius = CornerRadius((outer + g).coerceAtLeast(0f)),
            )
        }
        return
    }
    val half = CollarDimens.shadowReach.toPx()
    val drop = CollarDimens.shadowDrop.toPx()
    val layerAlpha = look.shadow.alpha / SHADOW_LAYERS
    if (layerAlpha > 0.002f) {
        for (i in 1..SHADOW_LAYERS) {
            val grow = half * i / SHADOW_LAYERS
            drawRoundRect(
                look.shadow.copy(alpha = layerAlpha),
                topLeft = Offset(topLeft.x - grow / 2, topLeft.y + drop * i / SHADOW_LAYERS - grow / 2),
                size = Size(size.width + grow, size.height + grow),
                cornerRadius = CornerRadius(outer + grow / 2),
            )
        }
    }
}

/**
 * Edge distance (in sigmas) of soft-shadow layer k: where a Gaussian edge has a level of its strength left, from [CollarDimens.SOFT_SHADOW_OUTER_LEVEL] (k = 0) to near full. The outermost level
 * sets how far the shadow shows (the board's measured reach, 8.7 dp for a 7.3 dp sigma), the others spread evenly up to near full strength.
 */
private val SOFT_SHADOW_EDGES: FloatArray =
    FloatArray(CollarDimens.SOFT_SHADOW_LAYERS) { k ->
        val n = CollarDimens.SOFT_SHADOW_LAYERS
        val outer = CollarDimens.SOFT_SHADOW_OUTER_LEVEL
        val target = outer + (SOFT_SHADOW_INNER_LEVEL - outer) * k / (n - 1)
        var lo = -6f
        var hi = 6f
        repeat(40) {
            val mid = (lo + hi) / 2
            if (FocusHalo.softEdge(mid, 1f) > target) lo = mid else hi = mid
        }
        (lo + hi) / 2
    }

private const val SOFT_SHADOW_INNER_LEVEL = 0.9f

/**
 * The focus glow of a part: the board's `0 0 blur` shadow of the accent, a Gaussian edge of [sigma] px whose strength [glow] (alpha) is
 * half there at the part's edge, drawn as [CollarDimens.GLOW_BANDS] bands out to [CollarDimens.GLOW_REACH_SIGMAS] sigmas, starting [start] px outside the
 * rounded rectangle at [topLeft] of [itemSize] with corner [corner].
 */
fun DrawScope.drawPartGlow(
    topLeft: Offset,
    itemSize: Size,
    corner: Float,
    start: Float,
    glow: Color,
    sigma: Float = CollarDimens.glowSigma.toPx(),
) {
    if (glow.alpha <= 0f) return
    val bands = CollarDimens.GLOW_BANDS
    val reach = sigma * CollarDimens.GLOW_REACH_SIGMAS
    val band = reach / bands
    for (i in 0 until bands) {
        val d = (i + 0.5f) * band
        val inflate = start + d
        drawRoundRect(
            color = glow.copy(alpha = glow.alpha * FocusHalo.softEdge(d, sigma)),
            topLeft = Offset(topLeft.x - inflate, topLeft.y - inflate),
            size = Size(itemSize.width + inflate * 2, itemSize.height + inflate * 2),
            cornerRadius = CornerRadius(corner + inflate),
            style = Stroke(band),
        )
    }
}

/** The groove's colour: [override] (an error) wins; else [look]'s groove lit toward [accent] by [focus] (0..1). */
internal fun grooveColor(
    look: CollarLook,
    focus: Float,
    override: Color?,
    accent: Color,
): Color =
    when {
        override != null -> override
        focus > 0f -> lerp(look.ring, accent, focus)
        else -> look.ring
    }

/**
 * Puts the part under it in its collar: pads by [CollarSize.padding] (the part keeps its own size, the collar is added outside it)
 * and draws [drawCollar] behind. [radius] is the part's corner; null is a pill (half the part's height). [focus] is 0..1 and
 * is read only while drawing, so a move of it redraws and nothing recomposes; [error] draws the groove in the danger colour.
 * [halo] false leaves the focus halo to a parent that draws it outside a clip ([drawCollarHalo]).
 * Reaches [CollarDimens.shadowReach] below its bounds and the focus halo around it: leave that unclipped. A focused grid cover has none ([halo] false).
 */
@Composable
fun Modifier.collar(
    collarSize: CollarSize,
    radius: Dp? = null,
    error: Boolean = false,
    halo: Boolean = true,
    focus: () -> Float,
): Modifier {
    val colors = PlumTheme.colors
    return drawBehind {
        val partHeight = size.height - (collarSize.topPadding + collarSize.bottomPadding).toPx()
        drawCollar(
            look = colors.collar,
            collarSize = collarSize,
            cornerRadius = radius?.toPx() ?: (partHeight / 2),
            focus = focus(),
            grooveOverride = if (error) colors.danger else null,
            accent = colors.accent,
            glow = if (halo) colors.partGlow else Color.Transparent,
        )
    }.padding(collarSize.padding)
}

/** [collar] for a part that is focused or not: the groove's light and the halo grow in [PlumMotion.FOCUS_GROW_MS] (at once when motion is off). */
@Composable
fun Modifier.collar(
    collarSize: CollarSize,
    focused: Boolean,
    radius: Dp? = null,
    error: Boolean = false,
): Modifier {
    val progress = remember { Animatable(if (focused) 1f else 0f) }
    val motionOn = PlumTheme.motion.isOn
    LaunchedEffect(focused, motionOn) {
        val target = if (focused) 1f else 0f
        progress.animateTo(target, if (motionOn) tween(PlumMotion.FOCUS_GROW_MS, easing = PlumMotion.Decelerate) else snap())
    }
    return collar(collarSize, radius, error) { progress.value }
}

/**
 * The small drop shadow of a box inside its cover collar (the Covers board: a hard 2 px one at 0.55 and a soft 3 px one at 0.4):
 * the hard one is a plain rounded rectangle, the soft one a few stacked ones growing. Drawn behind the box, whose
 * corner is [corner]; no blur and no layer. [alpha] dims it with a dimmed cover.
 */
@Composable
fun Modifier.coverBoxShadow(
    corner: Dp,
    alpha: Float = 1f,
): Modifier {
    val colors = PlumTheme.colors
    return drawBehind {
        val black = colors.collar.shadow
        val radius = corner.toPx()
        val hard = CollarDimens.boxShadowHardDrop.toPx()
        val soft = CollarDimens.boxShadowSoftDrop.toPx()
        // `0 3px 4px` at 0.4: one cached Gaussian image under the box (the box is opaque, so only what shows past it is seen).
        drawSoft(Offset.Zero, size, radius, soft, CollarDimens.boxShadowSoftSigma.toPx(), black.copy(alpha = BOX_SHADOW_SOFT_ALPHA * alpha), true)
        drawRoundRect(black.copy(alpha = BOX_SHADOW_HARD_ALPHA * alpha), Offset(0f, hard), size, CornerRadius(radius))
    }
}

private const val BOX_SHADOW_HARD_ALPHA = 0.55f
private const val BOX_SHADOW_SOFT_ALPHA = 0.4f * Depth.SHADOW
