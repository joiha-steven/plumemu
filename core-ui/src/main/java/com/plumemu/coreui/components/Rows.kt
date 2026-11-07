package com.plumemu.coreui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import com.plumemu.coreui.theme.FocusSlab
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.LeftColumnDimens
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.SlabDimens
import com.plumemu.coreui.theme.PlumTypography

/*
 * The rows of the main screen's strip, in the one focus language (docs/design/rules.md › Focus
 * and current). Rows take plain values and a look, never the focus state, so a focus move
 * recomposes only the rows whose look changed.
 */

/** How a game row looks. */
enum class GameRowLook {
    /** The cursor: `sel` fill, 6 dp accent dot, `strong` text, weight 500, trailing text `meta`. */
    Focused,

    /** Games have focus, another row: `text2`, trailing `muted`. */
    Normal,

    /** Preview column (systems have focus): `muted`. */
    Preview,

    /** Preview column, the row the system remembers: one step brighter, `text2`. */
    PreviewRemembered,

    /**
     * The current game while the narrow details stop has focus: `muted` title, trailing text
     * (its year, the only part still on screen) in accent, weight 600, no fill.
     */
    Current,

    /**
     * The game whose menu is open beside the list (the panel has the focus): accent title at
     * weight 600 (current, not focused), trailing `muted`, no fill, so there are never two filled rows on screen.
     */
    MenuOpen,
}

/**
 * The look a row is composed with: [GameRowLook.Preview] and [GameRowLook.PreviewRemembered] are
 * laid out and filled like [GameRowLook.Normal] and differ from it only in the title's colour,
 * which [GameRow] reads while drawing from its `drawnLook`. A list that passes this and the live
 * look recolours its titles when the games gain or lose the focus (Enter, Back) without
 * recomposing a row.
 */
fun GameRowLook.composedAs(): GameRowLook =
    when (this) {
        GameRowLook.Preview, GameRowLook.PreviewRemembered -> GameRowLook.Normal
        else -> this
    }

/**
 * A game (or app) row, drawn `gameRowHeight` high (at least 38; the gap under it is part of its tap area): title on one line with an ellipsis, [trailing] (year or region)
 * at the right. A favorite ([isFavorite]) has the star 8 after its title in every look; a long
 * title is cut before the star, never the star. [modifier] carries the tap handling and the width.
 *
 * A [subtitle] ("· Rev 1": what tells this row from another of the same title) follows the title in the
 * trailing text's style, inside the same line, so a narrow row cuts it before the title.
 *
 * A mastered game ([masteredLabel] is its spoken name, null otherwise) has a small trophy after the title
 * (and the star), in `meta`, [PlumDimens.masteredMarkSize] square; like the star, a long title is cut before it.
 *
 * [region] given (maybe empty; a wide single system's list) puts [trailing], the year, and the region in the board's two right-aligned
 * columns ([PlumDimens.rowYearColumn], [PlumDimens.rowRegionColumn], growing with the text size so a region keeps its line); null keeps the one trailing text.
 * Either way a year is set in `rowYear` and a region or system code in `tag` (tracked caps); [trailingIsCode] says the one text is a code.
 *
 * [endPadding] is the right inset, read while laying out (the narrow details stop shows only the year of a slid-out row and passes
 * [PlumDimens.gameRowPeekEndPadding]): a change of stop re-lays out the rows and recomposes none.
 *
 * [endHidden] is read while drawing: true leaves the year and region (the right-hand text) undrawn and out of semantics, so a card over
 * the list's right part never cuts them; it takes no room back (nothing is laid out again) and recomposes nothing.
 *
 * [look] sets the fill, the dot, the weights and the trailing text. The title's colour is read
 * while drawing from [drawnLook] (by default [look]); a caller that passes one gives
 * [look] as its [composedAs], so a change of colour alone redraws the title and recomposes nothing.
 */
@Composable
fun GameRow(
    title: String,
    trailing: String,
    look: GameRowLook,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    drawnLook: (() -> GameRowLook)? = null,
    subtitle: String = "",
    masteredLabel: String? = null,
    region: String? = null,
    endPadding: () -> Dp = DefaultRowEndPadding,
    trailingIsCode: Boolean = false,
    endHidden: () -> Boolean = NeverHidden,
) {
    val colors = PlumTheme.colors
    val focused = look == GameRowLook.Focused
    val titleColor = remember(colors, look, drawnLook) { ColorProducer { titleColorOf(drawnLook?.invoke() ?: look, colors) } }
    // The row's box is its tap area: the drawn row plus the gap under it, so a game row is never under
    // 40 dp to touch. The list puts no spacing of its own between rows.
    Box(modifier.fillMaxWidth().height(PlumTheme.layout.gameRowHeight + PlumDimens.listRowGap)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(PlumTheme.layout.gameRowHeight)
                    .testTag(GAME_ROW_DRAWN_TAG)
                    .focusPill(focused)
                    .padding(start = PlumDimens.gameRowPadding)
                    .endInset(endPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
                // The title and the year share one baseline (the year is smaller, so centring each on its own put
            // it 2 dp high): they sit in one group that is centred in the row. The star is measured before
            // the title (it has no weight), so the title takes what is left and its ellipsis comes before the star.
            // LastBaseline, not FirstBaseline: a Row reports the lowest baseline of its children as its last and
            // the highest as its first, and the smaller star sits higher than the title, so the first would be the star's.
            Row(Modifier.weight(1f)) {
                Row(Modifier.weight(1f).alignBy(LastBaseline), verticalAlignment = Alignment.CenterVertically) {
                    RowTitle(title, subtitle, look, titleColor)
                    FavoriteMark(isFavorite)
                    if (masteredLabel != null) MasteredMark(masteredLabel)
                }
                RowEnd(trailing, region, look, trailingIsCode, endHidden)
            }
        }
    }
}

/**
 * The row's right-hand text in the board's styles: with a [region] (maybe empty) the year and region columns, each
 * right-aligned in its own width so an empty year cell never moves the region; otherwise [trailing] alone, a year in the
 * year style or, when [isCode] (a region or a system's short name), in the region's tracked caps. Inline: no scope of its own.
 */
@Composable
private inline fun RowScope.RowEnd(
    trailing: String,
    region: String?,
    look: GameRowLook,
    isCode: Boolean,
    noinline hidden: () -> Boolean,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val scale = PlumTheme.layout.textScale
    val gone = Modifier.hiddenWhile(hidden).shiftedBy(LocalRowEndShift.current)
    if (region != null) {
        Spacer(Modifier.width(PlumDimens.rowTrailingGap))
        BasicText(text = trailing, style = yearStyle(look, colors, type).copy(textAlign = TextAlign.End), maxLines = 1, modifier = gone.width(PlumDimens.rowYearColumn * scale).alignBy(LastBaseline))
        BasicText(text = region, style = codeStyle(look, colors, type).copy(textAlign = TextAlign.End), maxLines = 1, modifier = gone.width(PlumDimens.rowRegionColumn * scale).alignBy(LastBaseline))
    } else if (trailing.isNotEmpty()) {
        Spacer(Modifier.width(PlumDimens.rowTrailingGap))
        BasicText(text = trailing, style = if (isCode) codeStyle(look, colors, type) else yearStyle(look, colors, type), maxLines = 1, modifier = gone.alignBy(LastBaseline))
    }
}

/**
 * The year in [look]: resting `muted`, current accent 600, on the focused slab brighter and one face heavier (the board's #D6D2C8 at 500 over
 * 400). Focused weights move from the resting style's, so they follow the text weight setting like every other role.
 */
private fun yearStyle(
    look: GameRowLook,
    colors: PlumComposeColors,
    type: PlumTypography,
): TextStyle =
    when (look) {
        GameRowLook.Focused -> type.rowYear.let { it.copy(color = colors.slab.year, fontWeight = it.heavierBy(FocusSlab.YEAR_WEIGHT - BOARD_YEAR_WEIGHT)) }
        GameRowLook.Current -> type.rowYearCurrent.copy(color = colors.accent)
        else -> type.rowYear.copy(color = colors.muted)
    }

/** A region or system code in [look]: tracked caps 600, resting `muted`, current accent, on the focused slab brighter and as heavy (dark) or one face heavier (light; the board's 600 / 700). */
private fun codeStyle(
    look: GameRowLook,
    colors: PlumComposeColors,
    type: PlumTypography,
): TextStyle {
    val region = type.tag
    return when (look) {
        GameRowLook.Focused -> region.copy(color = colors.slab.region, fontWeight = region.heavierBy(colors.slab.regionWeight - BOARD_REGION_WEIGHT))
        GameRowLook.Current -> region.copy(color = colors.accent)
        else -> region.copy(color = colors.muted)
    }
}

/** The board's resting weights of the year and the region, from which the focused ones step. */
private const val BOARD_YEAR_WEIGHT = 400
private const val BOARD_REGION_WEIGHT = 600

/** This style's weight [by] heavier (whole faces, capped at the heaviest, 800). */
private fun TextStyle.heavierBy(by: Int): FontWeight = FontWeight(((fontWeight?.weight ?: BOARD_YEAR_WEIGHT) + by).coerceAtMost(800))

private val DefaultRowEndPadding: () -> Dp = { PlumDimens.gameRowEndPadding }

private val NeverHidden: () -> Boolean = { false }

/**
 * Not drawn, and invisible to semantics, while [hidden] is true; read in the draw and semantics passes only, so a change recomposes and
 * lays out nothing. Used for text a card over the list would cut by its edge.
 */
private fun Modifier.hiddenWhile(hidden: () -> Boolean): Modifier =
    drawWithContent { if (!hidden()) drawContent() }.semantics { if (hidden()) invisibleToUser() }

/** Leaves [inset] free at the right of the content; read in the layout pass, so a changing inset does not recompose. */
private fun Modifier.endInset(inset: () -> Dp): Modifier =
    layout { measurable, constraints ->
        val end = inset().roundToPx()
        val placeable = measurable.measure(constraints.offset(horizontal = -end))
        layout(placeable.width + end, placeable.height) { placeable.place(0, 0) }
    }

/**
 * The title (with its [subtitle]) of a game row, filling what the star and trophy leave. Inline so it
 * has no recompose scope of its own: a call with a [ColorProducer] is never skippable, and a row
 * that did not change must not recompose when the list moves.
 */
@Composable
private inline fun RowScope.RowTitle(
    title: String,
    subtitle: String,
    look: GameRowLook,
    titleColor: ColorProducer,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val focused = look == GameRowLook.Focused
    // The subtitle (what tells this row from another of the same title) is part of the title's text, in the
    // trailing text's style: one line with one ellipsis, so a narrow row cuts the subtitle before the title.
    val titleStyle =
        when {
            focused -> type.gameTitleFocused
            look == GameRowLook.MenuOpen -> type.gameTitleCurrent
            else -> type.gameTitle
        }
    if (subtitle.isEmpty()) {
        // The common row keeps the plain String text (the main scroll path).
        BasicText(
            text = title,
            style = titleStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
            color = titleColor,
        )
    } else {
        val subtitleColor =
            when (look) {
                GameRowLook.Focused -> colors.meta
                GameRowLook.Current -> colors.accent
                else -> colors.muted
            }
        val smallFact = if (look == GameRowLook.Current) type.smallFactCurrent else type.smallFact
        val text = remember(title, subtitle, smallFact, subtitleColor) { titleWithSubtitle(title, subtitle, smallFact, subtitleColor) }
        BasicText(
            text = text,
            style = titleStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
            color = titleColor,
        )
    }
}

/** [title], then [subtitle] (when there is one) in [style] and [color]; just [title] otherwise. */
private fun titleWithSubtitle(
    title: String,
    subtitle: String,
    style: TextStyle,
    color: Color,
): AnnotatedString =
    buildAnnotatedString {
        append(title)
        if (subtitle.isNotEmpty()) {
            append(' ')
            withStyle(style.toSpanStyle().copy(color = color)) { append(subtitle) }
        }
    }

/** A game row's title colour in [look]. */
private fun titleColorOf(
    look: GameRowLook,
    colors: PlumComposeColors,
): Color =
    when (look) {
        GameRowLook.Focused -> colors.strong
        GameRowLook.Normal, GameRowLook.PreviewRemembered -> colors.text2
        GameRowLook.Preview, GameRowLook.Current -> colors.muted
        GameRowLook.MenuOpen -> colors.accent
    }

/**
 * The favorite star after a title (docs/design/rules.md › Favorite mark): small facts' size in `meta`. It grows
 * in (alpha 0→1, scale 0.6→1, 180 ms ease-out, `graphicsLayer` only) when the row turns favorite
 * while on screen; a row that comes into view already a favorite shows it at rest, and with
 * animations off it never moves.
 */
@Composable
private fun FavoriteMark(isFavorite: Boolean) {
    // Whether this row has been seen without the star: only then is a new star an event to show.
    val seenWithout = remember { SeenFlag(!isFavorite) }
    if (!isFavorite) {
        seenWithout.value = true
        return
    }
    val animate = seenWithout.value && PlumTheme.motion.isOn
    val shown = remember { Animatable(if (animate) 0f else 1f) }
    if (animate) {
        val spec = PlumTheme.motion.slideSpec<Float>()
        LaunchedEffect(Unit) { shown.animateTo(1f, spec) }
    }
    Spacer(Modifier.width(PlumDimens.favoriteMarkGap))
    BasicText(
        text = FAVORITE_STAR,
        style = PlumTheme.type.smallFact.copy(color = PlumTheme.colors.meta),
        maxLines = 1,
        modifier =
            Modifier.graphicsLayer {
                val t = shown.value
                alpha = t
                scaleX = PlumMotion.STAR_START_SCALE + (1f - PlumMotion.STAR_START_SCALE) * t
                scaleY = scaleX
            },
    )
}

/** The trophy of a mastered game (docs/design/rules.md › "Mastered mark"): drawn, so it needs no glyph the fonts may lack. */
@Composable
private fun MasteredMark(label: String) {
    val color = PlumTheme.colors.meta
    Spacer(Modifier.width(PlumDimens.favoriteMarkGap))
    Canvas(
        Modifier
            .size(PlumDimens.masteredMarkSize)
            .testTag(MASTERED_MARK_TAG)
            .semantics { contentDescription = label },
    ) {
        val unit = size.width
        val bowl =
            Path().apply {
                moveTo(unit * 0.22f, unit * 0.06f)
                lineTo(unit * 0.78f, unit * 0.06f)
                lineTo(unit * 0.78f, unit * 0.38f)
                quadraticBezierTo(unit * 0.78f, unit * 0.64f, unit * 0.5f, unit * 0.66f)
                quadraticBezierTo(unit * 0.22f, unit * 0.64f, unit * 0.22f, unit * 0.38f)
                close()
            }
        drawPath(bowl, color)
        val handles =
            Path().apply {
                moveTo(unit * 0.22f, unit * 0.14f)
                lineTo(unit * 0.07f, unit * 0.14f)
                lineTo(unit * 0.07f, unit * 0.30f)
                lineTo(unit * 0.24f, unit * 0.42f)
                moveTo(unit * 0.78f, unit * 0.14f)
                lineTo(unit * 0.93f, unit * 0.14f)
                lineTo(unit * 0.93f, unit * 0.30f)
                lineTo(unit * 0.76f, unit * 0.42f)
            }
        drawPath(handles, color, style = Stroke(width = unit * 0.09f))
        drawRect(color, Offset(unit * 0.44f, unit * 0.62f), Size(unit * 0.12f, unit * 0.2f))
        drawRect(color, Offset(unit * 0.28f, unit * 0.8f), Size(unit * 0.44f, unit * 0.14f))
    }
}

/** The trophy of a mastered game's row. */
const val MASTERED_MARK_TAG = "mastered_mark"

/** A plain holder: written during composition, never read to recompose anything. */
private class SeenFlag(var value: Boolean)

/** U+2605 BLACK STAR (docs/design/rules.md › Favorite mark). */
const val FAVORITE_STAR = "\u2605"

/** The drawn part of a game row (what the focus fill covers), inside its taller tap area. */
const val GAME_ROW_DRAWN_TAG = "game_row_drawn"


/** How a system row looks. */
enum class SystemRowLook {
    /** The cursor on the system column: `sel` fill, accent dot, `strong` text 500, count `meta`. */
    Focused,

    /** Another entry: name `text2`, count `muted`. */
    Normal,

    /** The current system while games have focus: accent text 600, no fill, count accent. */
    Current,
}

/**
 * An entry of the system column, 34 high (the board's height): name with an ellipsis, [count]
 * at the right. With [showName] false (the 48 dp peek) the name keeps its place but is not
 * drawn, so only counts show and a long name never pokes in (docs/design/rules.md › The strip).
 */
@Composable
fun SystemRow(
    name: String,
    count: String,
    look: SystemRowLook,
    showName: Boolean,
    modifier: Modifier = Modifier,
    bleedStart: Dp,
    hasDot: Boolean = false,
    /** False: no count and none of its room (Settings' sections in three columns, whose labels need it); [endPadding] 0 there too. */
    showCount: Boolean = true,
    endPadding: Dp = PlumDimens.systemRowPadding,
    barInset: Dp = LeftColumnDimens.systemBarInset,
) = SystemRow(name, count, look, nameAlpha = if (showName) NameShown else NameHidden, modifier = modifier, bleedStart = bleedStart, hasDot = hasDot, showCount = showCount, endPadding = endPadding, barInset = barInset)

/**
 * The same row with the name drawn at [nameAlpha], read while drawing: the main screen fades the
 * names with the strip's position as it slides to the peek, without recomposing a row per frame.
 *
 * The row is [bleedStart] wider on the left (Home and Settings pass the page margin): the label
 * starts at [bleedStart] whether focused or not, the focus dot sits centred in that space, and the
 * selector fills the whole row with square left corners, as if it ran on under the screen edge.
 */
@Composable
fun SystemRow(
    name: String,
    count: String,
    look: SystemRowLook,
    nameAlpha: () -> Float,
    modifier: Modifier = Modifier,
    bleedStart: Dp,
    hasDot: Boolean = false,
    /** True: the count fades with the names (the letter rail takes its place in the peek). False: it stays, as counts and system codes do. */
    fadeCount: Boolean = false,
    showCount: Boolean = true,
    endPadding: Dp = PlumDimens.systemRowPadding,
    /** Read while drawing: true leaves the count undrawn (a card over the page would cut it); its room stays. */
    countHidden: () -> Boolean = NeverHidden,
    /** The accent bar's distance from the slab's left edge (the slab itself starts at [LeftColumnDimens.systemPillStart]). */
    barInset: Dp = LeftColumnDimens.systemBarInset,
) {
    BleedingSystemRow(name, count, look, nameAlpha, bleedStart, modifier, hasDot, fadeCount, showCount, endPadding, countHidden, barInset)
}

/** Label at [bleedStart]; the focus pill starts at [LeftColumnDimens.systemPillStart] and its accent bar [barInset] in (Home and Settings). */
@Composable
private fun BleedingSystemRow(
    name: String,
    count: String,
    look: SystemRowLook,
    nameAlpha: () -> Float,
    bleedStart: Dp,
    modifier: Modifier,
    hasDot: Boolean,
    fadeCount: Boolean,
    showCount: Boolean,
    endPadding: Dp,
    countHidden: () -> Boolean,
    barInset: Dp,
) {
    val colors = PlumTheme.colors
    val focused = look == SystemRowLook.Focused
    // The pill starts [systemPillStart] from the screen edge and the label never moves with focus. It is drawn by focusPill so it dips with the row;
    // the empty PILL box only keeps the tag for the layout test.
    val inset = LeftColumnDimens.systemPillStart
    // The press (dip and finger) is on the whole row, the touch target; only the drawn pill starts at [inset].
    Box(modifier = modifier.fillMaxWidth().height(PlumTheme.layout.systemRowHeight).focusPill(focused, shape = remember(inset, barInset) { InsetFocusPillShape(inset, barInset) })) {
        if (focused) Box(Modifier.fillMaxSize().padding(start = inset).testTag(SystemRowTags.PILL))
        Row(
            modifier = Modifier.fillMaxSize().padding(start = bleedStart, end = endPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(modifier = Modifier.weight(1f).fadedBy(nameAlpha), verticalAlignment = Alignment.CenterVertically) {
                SystemName(name, look)
            }
            if (hasDot) {
                // Something new here (games added by a change check): the hint bar's accent dot, gone with the name in the peek.
                Spacer(Modifier.width(PlumDimens.hintKeyGap))
                Box(Modifier.fadedBy(nameAlpha).size(PlumDimens.hintDot).drawBehind { drawCircle(colors.accent) }.testTag(SystemRowTags.NEW_DOT))
            }
            // Faded with the names only while the letter rail takes the peek (Settings › Home screen › Left edge).
            if (showCount) SystemCount(count, look, nameAlpha, fadeCount, countHidden)
        }
    }
}

@Composable
private fun SystemName(
    name: String,
    look: SystemRowLook,
) {
    val type = PlumTheme.type
    val colors = PlumTheme.colors
    val style =
        when (look) {
            SystemRowLook.Focused -> type.systemNameFocused
            SystemRowLook.Normal -> type.systemName
            SystemRowLook.Current -> type.systemNameCurrent
        }
    // The colour is read while drawing, so a moved theme colour redraws the name without recomposing it.
    val color: () -> Color =
        when (look) {
            SystemRowLook.Focused -> { { colors.strong } }
            SystemRowLook.Normal -> { { colors.text2 } }
            SystemRowLook.Current -> { { colors.accent } }
        }
    EllipsisText(text = name, style = style, color = color, maxLines = 1, modifier = Modifier.testTag(SystemRowTags.LABEL))
}

@Composable
private fun SystemCount(
    count: String,
    look: SystemRowLook,
    nameAlpha: () -> Float,
    fade: Boolean,
    hidden: () -> Boolean,
) {
    val colors = PlumTheme.colors
    val color: () -> Color =
        when (look) {
            SystemRowLook.Focused -> { { colors.meta } }
            SystemRowLook.Normal -> { { colors.muted } }
            SystemRowLook.Current -> { { colors.accent } }
        }
    Spacer(Modifier.width(PlumDimens.rowTrailingGap))
    BasicText(text = count, modifier = (if (fade) Modifier.fadedBy(nameAlpha) else Modifier).hiddenWhile(hidden).testTag(SystemRowTags.COUNT), style = PlumTheme.type.meta, color = color, maxLines = 1)
}

/** Draws at [alpha], read while drawing; no offscreen buffer per row while the names fade (dot and name do not overlap). */
private fun Modifier.fadedBy(alpha: () -> Float): Modifier =
    graphicsLayer {
        this.alpha = alpha()
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }

/** Test tags inside a [SystemRow], for the layout test (read from the unmerged tree). */
object SystemRowTags {
    const val PILL = "system_row_pill"
    const val LABEL = "system_row_label"

    /** The count (or system code) at the row's end; faded out with the names only when the letter rail covers its place. */
    const val COUNT = "system_row_count"

    /** The dot of a system with new games ([SystemRow] `hasDot`). */
    const val NEW_DOT = "system_row_new_dot"
}

private val NameShown: () -> Float = { 1f }
private val NameHidden: () -> Float = { 0f }

