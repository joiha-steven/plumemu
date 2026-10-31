package com.plumemu.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.components.CoverShadow
import com.plumemu.coreui.components.CoverTile
import com.plumemu.coreui.components.coverShadow
import com.plumemu.coreui.components.drawCoverRim
import com.plumemu.coreui.theme.CoverClass
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumTheme
import kotlin.math.roundToInt

/**
 * A game's cover in a slot of [size]: the saved
 * [picture] at its own shape, fitted inside the slot (never cropped, never stretched) and placed
 * by [alignment] (the grid's shelf: bottom centre). Every state names the game: until the picture
 * is decoded, and for good when its file cannot be read, its place is the titled tile
 * ([CoverTile]) in the picture's shape, so nothing moves when it lands; the picture then fades in
 * over it in 110 ms (alpha only; at once with motion off or when it was already in memory).
 * Without a picture (none, or the result still being read) the titled tile fills the slot.
 * [subtitle] is the year when known. [pictureModifier] goes on the drawn cover itself (the
 * grid's focused scale). [titleStyle] is the grid size's (`PlumTypography.coverTitle`); a null
 * [title] leaves the tile untitled (the details column, whose title is right under the cover).
 * [hasEdge] draws the grid's 1 dp rim just inside the picture's edge (the rim light on a dark background, `coverEdge` on a light one, design/rules.md
 * › Grid): a cover's black border then does not melt into a dark background. Inside, so nothing moves.
 * [shadow] draws the grid's soft shadow behind the cover ([shadowAlpha] dims it with a dimmed cover).
 * [shrinksToFit]: given less height or width than [size] (the wide details column at Largest in Serif,
 * `WideDetailsLayout`), the picture shrinks into it keeping its shape, at layout time; the tile fills it.
 */
@Composable
internal fun CoverArt(
    title: String?,
    picture: ArtPicture?,
    size: DpSize,
    alignment: Alignment,
    corner: Dp,
    titlePadding: Dp,
    modifier: Modifier = Modifier,
    pictureModifier: Modifier = Modifier,
    subtitle: String? = null,
    titleStyle: TextStyle = PlumTheme.type.gridFocusTitle,
    hasEdge: Boolean = false,
    shrinksToFit: Boolean = false,
    shadow: CoverShadow? = null,
    shadowAlpha: Float = 1f,
) {
    // Outside the focus modifier and the clip: the focused cover's larger copy does not carry it, and nothing cuts it.
    val shadowed = Modifier.coverShadow(shadow, corner, shadowAlpha)
    if (picture != null) {
        val fitted = fitInside(picture.aspect, size)
        Box(modifier = modifier.size(size), contentAlignment = alignment) {
            CoverPicture(title, subtitle, picture, fitted, corner, titlePadding, titleStyle, shadowed.then(if (shrinksToFit) Modifier.shrinkInto(fitted).then(pictureModifier) else pictureModifier), hasEdge, shadow?.rim ?: Color.Transparent)
        }
    } else {
        TitledTile(title, subtitle, size, corner, titlePadding, titleStyle, modifier.size(size).then(shadowed).then(pictureModifier), rim = shadow?.rim ?: Color.Transparent)
    }
}

/**
 * The titled tile at [size]: as many title lines as it holds, one kept for [subtitle]. Tiles
 * taller than wide (portrait cells) tag their title [HomeTags.PORTRAIT_TILE_TITLE], the others
 * [HomeTags.WIDE_TILE_TITLE]: a test may allow an ellipsis under one of them and nowhere else in the
 * grid.
 */
@Composable
private fun TitledTile(
    title: String?,
    subtitle: String?,
    size: DpSize,
    corner: Dp,
    padding: Dp,
    titleStyle: TextStyle,
    modifier: Modifier,
    rim: Color = Color.Transparent,
) {
    val type = PlumTheme.type
    val density = LocalDensity.current
    val fitter = currentTileTitleFit()
    val measurer = fitter.measurer
    // The title steps down to the ramp's smallest tile step when a word does not fit the tile's width (TileTitleFit).
    // Strict line breaking (no break before a ":" or "!"), as the word segmentation of the fit assumes.
    val steps =
        remember(titleStyle, type) {
            (if (type.smallTileTitle.fontSize < titleStyle.fontSize) listOf(titleStyle, type.smallTileTitle) else listOf(titleStyle)).map { it.copy(lineBreak = LineBreak.Paragraph) }
        }
    // As the tile lays its text area out: the tile and each side's padding rounded to px on their own.
    val widthPx = with(density) { size.width.roundToPx() - padding.roundToPx() * 2 }
    val fitted = if (title == null) null else remember(fitter, title, steps, widthPx) { fitter.fit(title, steps, widthPx) }
    val shownStyle = steps[fitted?.step ?: 0]
    // Never larger than the title above it.
    val factStyle = if ((fitted?.step ?: 0) > 0) type.smallTileYear else type.coverSubtitle(PlumTheme.layout.gridSize)
    // Line heights as laid out (font, text size and line height all count), measured once per style.
    val lineHeight = remember(measurer, shownStyle, density) { with(density) { measurer.measure(LINE_PROBE, shownStyle, maxLines = 1).size.height.toDp() } }
    val factHeight = remember(measurer, factStyle, density) { with(density) { measurer.measure(LINE_PROBE, factStyle, maxLines = 1).size.height.toDp() } }
    // The title comes first: when it needs the year's line, the year goes (one more pass, for
    // that tile only, when its title is laid out longer than the lines it was given).
    var hasYearRoom by remember(title, subtitle, size, lineHeight) { mutableStateOf(!subtitle.isNullOrEmpty()) }
    val subtitleHeight = if (hasYearRoom) factHeight + PlumDimens.titleLineGap else 0.dp
    val lines = ((size.height - padding * 2 - subtitleHeight) / lineHeight).toInt().coerceAtLeast(1)
    val isPortrait = size.width < size.height
    CoverTile(
        title = fitted?.text,
        corner = corner,
        padding = padding,
        maxLines = lines,
        subtitle = if (hasYearRoom) subtitle else null,
        modifier = modifier,
        titleModifier = Modifier.testTag(if (isPortrait) HomeTags.PORTRAIT_TILE_TITLE else HomeTags.WIDE_TILE_TITLE),
        titleStyle = shownStyle,
        subtitleStyle = factStyle,
        onTitleLayout = { if (hasYearRoom && it.hasVisualOverflow) hasYearRoom = false },
        rim = rim,
    )
}

/** The decoded [picture] drawn at [size] over the titled tile, decoded at that size in pixels; [hasEdge] as [CoverArt]'s. */
@Composable
private fun CoverPicture(
    title: String?,
    subtitle: String?,
    picture: ArtPicture,
    size: DpSize,
    corner: Dp,
    padding: Dp,
    titleStyle: TextStyle,
    modifier: Modifier,
    hasEdge: Boolean,
    rim: Color,
) {
    // The grid's rim light on a dark background; on a light one the hairline (the shadow carries the depth there).
    val edge = if (rim == Color.Transparent) PlumTheme.colors.coverEdge else rim
    val images = LocalHomeArt.current.images
    val glowColors = LocalHomeArt.current.glowColors
    val density = LocalDensity.current
    val widthPx = with(density) { size.width.roundToPx() }.coerceAtLeast(1)
    val heightPx = with(density) { size.height.roundToPx() }.coerceAtLeast(1)
    // Read when the fade starts (not captured at composition), so Animations turned off meanwhile is honoured.
    val isMotionOn by rememberUpdatedState(PlumTheme.motion.isOn)
    val cached = remember(images, picture, widthPx, heightPx) { images.cached(picture, widthPx, heightPx) }
    if (cached != null) LaunchedEffect(glowColors, picture, cached) { glowColors.learn(picture, cached) }
    val bitmap = remember(images, picture, widthPx, heightPx) { mutableStateOf(cached) }
    val alpha = remember(images, picture, widthPx, heightPx) { Animatable(if (cached != null) 1f else 0f) }
    // The tile stays composed until the picture covers it fully; read here, so a landing picture
    // recomposes this cover once.
    val isCovered = remember(images, picture, widthPx, heightPx) { mutableStateOf(cached != null) }
    if (cached == null) {
        LaunchedEffect(images, picture, widthPx, heightPx) {
            // Null: the file is gone or damaged; the titled tile stays (CoilCoverImages logs why).
            val loaded = images.load(picture, widthPx, heightPx) ?: return@LaunchedEffect
            glowColors.learn(picture, loaded)
            bitmap.value = loaded
            if (isMotionOn) alpha.animateTo(1f, tween(PlumMotion.FADE_IN_MS, easing = PlumMotion.EaseOut)) else alpha.snapTo(1f)
            isCovered.value = true
        }
    }
    val shape = RoundedCornerShape(corner)
    Box(
        modifier =
            modifier
                .size(size)
                .clip(shape)
                .drawWithContent {
                    drawContent()
                    // Read while drawing: a landing picture and its fade redraw this box only.
                    val image = bitmap.value ?: return@drawWithContent
                    drawImage(
                        image = image,
                        dstSize = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt()),
                        alpha = alpha.value,
                        filterQuality = FilterQuality.Low,
                    )
                    if (hasEdge) drawCoverRim(edge, corner.toPx(), alpha.value)
                }.testTag(HomeTags.COVER_PICTURE),
    ) {
        if (!isCovered.value) TitledTile(title, subtitle, size, corner, padding, titleStyle, Modifier.size(size))
    }
}

/**
 * [size], scaled down (never up) to fit the room it is given, its shape kept. The picture is still
 * loaded at [size] and drawn into the smaller box.
 */
private fun Modifier.shrinkInto(size: DpSize): Modifier =
    layout { measurable, constraints ->
        val width = size.width.toPx()
        val height = size.height.toPx()
        val scale = minOf(1f, constraints.maxWidth / width, constraints.maxHeight / height)
        val placeable = measurable.measure(Constraints.fixed((width * scale).roundToInt(), (height * scale).roundToInt()))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

/** Text measured for one line's height: a cap, an x-height letter and a descender. */
private const val LINE_PROBE = "Ag"

/** The corner of a picture of [aspect] where no system's cover class is at hand: its own class's, 8 from 1.2 up, 6 below (design/rules.md › Grid). */
internal fun pictureCorner(aspect: Float): Dp =
    if (CoverClass.of(aspect) == CoverClass.Landscape) PlumDimens.cornerCoverLandscape else PlumDimens.cornerCoverPortrait

/** A picture of [aspect] (width over height) at its largest inside [slot], keeping its shape. */
internal fun fitInside(
    aspect: Float,
    slot: DpSize,
): DpSize {
    val slotAspect = slot.width / slot.height
    return if (aspect >= slotAspect) DpSize(slot.width, slot.width / aspect) else DpSize(slot.height * aspect, slot.height)
}

/**
 * Where a picture of [aspect] stands in a grid cell at [cell] (px): fitted and bottom-centred, as
 * [CoverArt] draws it, so the grid's ring follows the cover's real shape.
 */
internal fun shelfRect(
    aspect: Float,
    cell: Rect,
): Rect {
    val cellAspect = cell.width / cell.height
    val width = if (aspect >= cellAspect) cell.width else cell.height * aspect
    val height = if (aspect >= cellAspect) cell.width / aspect else cell.height
    val left = cell.left + (cell.width - width) / 2f
    return Rect(left, cell.bottom - height, left + width, cell.bottom)
}
