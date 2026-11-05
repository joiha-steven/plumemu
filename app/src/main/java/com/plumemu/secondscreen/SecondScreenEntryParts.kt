package com.plumemu.secondscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plumemu.R
import com.plumemu.coreui.components.glassTile
import com.plumemu.coreui.components.EllipsisText
import com.plumemu.coreui.theme.EntryCardDimens
import com.plumemu.coreui.theme.GlassDimens
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.TypeRole
import com.plumemu.home.currentLocale
import com.plumemu.home.rememberCountFormat
import kotlin.math.roundToInt

/** Test tags of the system column's cards. */
internal object EntryTags {
    const val CARD = "second_screen_entry"
    const val TITLE = "second_screen_entry_title"
    const val COUNT = "second_screen_entry_count"
    const val LABEL = "second_screen_entry_label"
    const val COVERS = "second_screen_entry_covers"
    const val COVER = "second_screen_entry_cover"
    const val WHEN = "second_screen_entry_when"
    const val CAPTION_SYSTEM = "second_screen_entry_caption_system"
    const val TILE_PLAY = "second_screen_entry_tile_play"
    const val TILE_PLAYED = "second_screen_entry_tile_played"
    const val TILE_ACHIEVEMENTS = "second_screen_entry_tile_achievements"
    const val STATUS = "second_screen_entry_status"
    const val FOOT = "second_screen_entry_foot"
    const val WEEK = "second_screen_entry_week"
    const val WEEK_TOTAL = "second_screen_entry_week_total"
    const val WEEK_GAMES = "second_screen_entry_week_games"
    const val WEEK_LAST = "second_screen_entry_week_last"
    const val EMPTY = "second_screen_entry_empty"

    fun weekBar(day: Int) = "second_screen_entry_week_bar_$day"

    fun favoritesRow(row: Int) = "second_screen_entry_favorites_row_$row"
}

/** The text styles of the three cards: the board's sizes and, in Jost, its lines, scaled by the text-size setting. */
@Immutable
internal class EntryStyles(
    val title: TextStyle,
    val count: TextStyle,
    val label: TextStyle,
    val big: TextStyle,
    val unit: SpanStyle,
    val of: SpanStyle,
    val small: TextStyle,
    val status: TextStyle,
    val foot: TextStyle,
    val captionWhen: TextStyle,
    val captionSystem: TextStyle,
    val weekDay: TextStyle,
    /** Whether the texts sit on the board's line boxes ([boardLine]); only in Jost, whose metrics the boxes are worked out from. */
    val onBoardLines: Boolean,
)

@Composable
internal fun rememberEntryStyles(): EntryStyles {
    val type = PlumTheme.type
    val colors = PlumTheme.colors
    val base = type.card(TypeRole.SmallFact)
    // The ramp has no role at these sizes: the board's size follows the setting through the base role's own scale.
    val scale = base.fontSize.value / TypeRole.SmallFact.sizeSp
    // Off Jost the line is the shared card line; in Jost each text takes the board's line and box (Modifier.boardLine).
    val cardEm = base.lineHeight.value / base.fontSize.value
    val isJost = type.font == PlumFont.Jost
    // The board's weights are the design weights; the Text weight setting moves them as it moves every other text.
    val textWeight = type.textWeight
    val text = colors.text
    val text2 = colors.text2
    val meta = colors.meta
    val strong = colors.strong
    return remember(base, scale, isJost, textWeight, text, text2, meta, strong) {
        fun asked(design: FontWeight) = FontWeight(textWeight.apply(design.weight))

        fun style(
            size: TextUnit,
            weight: FontWeight,
            color: Color,
            jostEm: Float = EntryCardDimens.JOST_OWN_LINE,
        ): TextStyle {
            val scaled = size.value * scale
            val lineEm = if (isJost) jostEm else cardEm
            val line = if (isJost) BoardLineStyle else base.lineHeightStyle
            // Animated: glyphs keep their fractional advances, as the board's browser lays them (Static rounds each to a pixel); figures
            // of one width, as the boards set them.
            return base.copy(
                fontSize = scaled.sp, lineHeight = (scaled * lineEm).sp, lineHeightStyle = line, fontWeight = asked(weight), color = color,
                textMotion = TextMotion.Animated, fontFeatureSettings = TABULAR_FIGURES,
            )
        }
        EntryStyles(
            title = style(EntryCardDimens.headerTitle, FontWeight.SemiBold, strong),
            count = style(EntryCardDimens.headerMeta, FontWeight.Normal, meta),
            label = style(EntryCardDimens.label, FontWeight.SemiBold, meta).copy(letterSpacing = (EntryCardDimens.labelSpacing.value * scale).sp),
            big = style(EntryCardDimens.big, FontWeight.SemiBold, strong, EntryCardDimens.LINE_BIG),
            unit = SpanStyle(fontSize = (EntryCardDimens.unit.value * scale).sp, fontWeight = asked(FontWeight.Medium), color = text2),
            of = SpanStyle(fontSize = (EntryCardDimens.of.value * scale).sp, fontWeight = asked(FontWeight.Normal), color = text2),
            small = style(EntryCardDimens.small, FontWeight.Normal, text2, EntryCardDimens.LINE_SMALL),
            status = style(EntryCardDimens.status, FontWeight.Normal, text2, EntryCardDimens.LINE_STATUS),
            foot = style(EntryCardDimens.aboutText, FontWeight.Normal, meta, EntryCardDimens.LINE_FOOT),
            captionWhen = style(EntryCardDimens.captionWhen, FontWeight.Normal, text2, EntryCardDimens.LINE_CAPTION_WHEN),
            captionSystem = style(EntryCardDimens.captionSystem, FontWeight.Normal, meta, EntryCardDimens.LINE_CAPTION_SYSTEM),
            weekDay = style(EntryCardDimens.weekDay, FontWeight.Normal, meta),
            onBoardLines = isJost,
        )
    }
}


/** One part of an [EntryColumn]: the order it claims height in (lowest first), the gap above it, and whether it rests on the bottom edge. */
@Immutable
internal class EntryPart(
    val priority: Int,
    val gapAbove: Dp = 0.dp,
    val atBottom: Boolean = false,
)

/** A part of the card; [content] is measured at the full width. */
@Composable
internal fun EntryPartBox(
    part: EntryPart,
    content: @Composable () -> Unit,
) {
    Box(Modifier.layoutId(part)) { content() }
}

/**
 * The parts top to bottom with their gaps. Each is measured at the full width and kept only if the card, with the gaps its neighbours
 * give, still fits the height: they are tried in [EntryPart.priority] order and a part that does not fit is left out whole (never cut),
 * the lower ones still tried. A part with [EntryPart.atBottom] rests on the bottom edge (the board's `margin-top: auto`).
 */
@Composable
internal fun EntryColumn(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        val measured: List<Pair<EntryPart, Placeable>> = measurables.map { (it.layoutId as EntryPart) to it.measure(Constraints(maxWidth = width)) }
        val limit = if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE

        fun heightOf(kept: List<Pair<EntryPart, Placeable>>): Int = kept.mapIndexed { at, (part, placed) -> placed.height + if (at == 0) 0 else part.gapAbove.roundToPx() }.sum()
        val chosen = mutableListOf<Pair<EntryPart, Placeable>>()
        for (part in measured.sortedBy { it.first.priority }) {
            val tried = measured.filter { it in chosen || it === part }
            if (heightOf(tried) <= limit) chosen += part
        }
        val kept = measured.filter { it in chosen }
        val height = if (constraints.hasBoundedHeight) limit else heightOf(kept)
        layout(width, height) {
            var y = 0
            kept.forEachIndexed { at, (part, placed) ->
                if (at > 0) y += part.gapAbove.roundToPx()
                if (part.atBottom) placed.place(0, height - placed.height) else placed.place(0, y)
                y += placed.height
            }
        }
    }
}

/** The name at the left and the count at the right, on one baseline. */
@Composable
internal fun EntryHeader(
    name: String,
    gameCount: Int,
    styles: EntryStyles,
) {
    val numbers = rememberCountFormat()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        EllipsisText(name, style = styles.title, maxLines = 1, modifier = Modifier.weight(1f, fill = false).alignByBaseline().testTag(EntryTags.TITLE))
        BasicText(
            pluralStringResource(R.plurals.home_games_count, gameCount, numbers.format(gameCount)),
            style = styles.count,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(start = EntryCardDimens.headerGap).alignByBaseline().testTag(EntryTags.COUNT),
        )
    }
}

/** A small capital label ("LAST PLAYED"): one line; a label wider than its room steps down in size (in thirds of a sp) instead of breaking mid-word. */
@Composable
internal fun EntryLabel(
    text: String,
    styles: EntryStyles,
) {
    val locale = currentLocale()
    var shrink by remember(text, styles.label) { mutableFloatStateOf(1f) }
    val base = styles.label
    val size = if (shrink < 1f) (base.fontSize.value * shrink * 3).toInt() / 3f else base.fontSize.value
    val style = if (shrink < 1f) base.copy(fontSize = size.sp, lineHeight = (base.lineHeight.value * size / base.fontSize.value).sp, letterSpacing = (base.letterSpacing.value * size / base.fontSize.value).sp) else base
    BasicText(
        text.uppercase(locale),
        style = style,
        maxLines = 1,
        softWrap = false,
        onTextLayout = { if (it.didOverflowWidth && shrink > MIN_LABEL_SHRINK) shrink *= LABEL_SHRINK_STEP },
        modifier = Modifier.boardLine(style, styles).testTag(EntryTags.LABEL),
    )
}

private const val LABEL_SHRINK_STEP = 0.94f
private const val MIN_LABEL_SHRINK = 0.6f

/** [boardLine] for one of [styles]' texts: its own size and line, only in Jost. */
internal fun Modifier.boardLine(
    style: TextStyle,
    styles: EntryStyles,
    sharing: TextUnit? = null,
): Modifier = boardLine(style, styles.onBoardLines, sharing)

/** A raised tile: the label over its lines inside the glass edge (the board's 1 px border), [tag] on the whole. */
@Composable
internal fun EntryTile(
    tag: String,
    modifier: Modifier = Modifier,
    verticalPad: Boolean = false,
    content: @Composable () -> Unit,
) {
    val pad =
        if (verticalPad) {
            Modifier.padding(horizontal = EntryCardDimens.weekPadSide, vertical = EntryCardDimens.weekPadVertical)
        } else {
            Modifier.padding(start = EntryCardDimens.tilePadSide, end = EntryCardDimens.tilePadSide, top = EntryCardDimens.tilePadTop, bottom = EntryCardDimens.tilePadBottom)
        }
    Column(
        modifier.glassTile().padding(GlassDimens.edge).then(pad).testTag(tag),
        verticalArrangement = Arrangement.spacedBy(EntryCardDimens.tileLineGap),
    ) { content() }
}

/** The tiles in a row, one height. */
@Composable
internal fun EntryTileRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(EntryCardDimens.tileGap), content = content)
}

/** A tile's big figure and its unit or "/ total" after it, as one text so a long one wraps. */
@Composable
internal fun BigFigure(
    value: String,
    styles: EntryStyles,
    unit: String? = null,
    of: String? = null,
    modifier: Modifier = Modifier,
) {
    val words =
        buildAnnotatedString {
            append(value)
            if (unit != null) withStyle(styles.unit) { append(" "); append(unit) }
            if (of != null) withStyle(styles.of) { append(" / "); append(of) }
        }
    BasicText(words, style = styles.big, modifier = modifier.boardLine(styles.big, styles, sharing = if (unit != null) styles.unit.fontSize else null))
}

/** A tile's small line, [dim] in the meta colour. */
@Composable
internal fun SmallLine(
    text: String,
    styles: EntryStyles,
    dim: Boolean = true,
    modifier: Modifier = Modifier,
) {
    BasicText(text, style = if (dim) styles.small.copy(color = PlumTheme.colors.meta) else styles.small, modifier = modifier.boardLine(styles.small, styles))
}

/** A thin meter: [share] (0..1) of its width in the accent over a sunken track. */
@Composable
internal fun EntryMeter(share: Float) {
    val colors = PlumTheme.colors
    val shown = if (share > 0f) share.coerceIn(MIN_METER_SHARE, 1f) else 0f
    Box(Modifier.fillMaxWidth().height(EntryCardDimens.meterHeight).drawBehind { drawMeter(colors, shown) })
}

private const val MIN_METER_SHARE = 0.02f

/** The status segments: each in the text colour, or [Segment.isStrong] in the brighter one, joined by the muted dot, wrapping at a segment's edge. */
@Immutable
internal class Segment(
    val text: String,
    val isStrong: Boolean = false,
)

@Composable
internal fun StatusLine(
    segments: List<Segment>,
    styles: EntryStyles,
    tag: String,
    modifier: Modifier = Modifier,
) {
    val colors = PlumTheme.colors
    val separator = androidx.compose.ui.res.stringResource(R.string.second_screen_separator)
    val words: AnnotatedString =
        buildAnnotatedString {
            segments.forEachIndexed { at, segment ->
                if (at > 0) withStyle(SpanStyle(color = colors.muted)) { append(separator) }
                withStyle(SpanStyle(color = if (segment.isStrong) colors.text else colors.text2)) { append(segment.text) }
            }
        }
    BasicText(words, style = styles.status, modifier = modifier.fillMaxWidth().boardLine(styles.status, styles).testTag(tag))
}

/** The card's place: inside the margins of the Thor's bottom screen. */
internal fun Modifier.entryCardPlace(): Modifier =
    this.fillMaxHeight().fillMaxWidth().padding(
        start = EntryCardDimens.marginSide,
        end = EntryCardDimens.marginSide,
        top = EntryCardDimens.marginTop,
        bottom = EntryCardDimens.marginBottom,
    ).testTag(EntryTags.CARD)

/** A bar's fill: today in the accent, a day with play muted, a day without a stub. */
@Composable
internal fun Modifier.weekBar(
    seconds: Long,
    isToday: Boolean,
    heightFor: Dp,
): Modifier {
    val colors = PlumTheme.colors
    val shape =
        androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = EntryCardDimens.weekBarCorner,
            topEnd = EntryCardDimens.weekBarCorner,
            bottomEnd = EntryCardDimens.weekBarFootCorner,
            bottomStart = EntryCardDimens.weekBarFootCorner,
        )
    val bar = this.fillMaxWidth(EntryCardDimens.WEEK_BAR_WIDTH).height(heightFor)
    if (isToday) return bar.latestBarFill(shape)
    return bar.background(if (seconds > 0) colors.playedBar else colors.line, shape)
}

/**
 * Columns of one width with [gap] between them, each edge at its exact share of the width rounded on its own, as a
 * browser places flex columns: a weighted Row hands every spare pixel to the same columns, so they drift by a pixel or two.
 */
@Composable
internal fun EvenColumns(
    gap: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content, modifier.fillMaxWidth()) { measurables, constraints ->
        if (measurables.isEmpty()) return@Layout layout(constraints.maxWidth, 0) {}
        val gapPx = gap.toPx()
        val pitch = (constraints.maxWidth + gapPx) / measurables.size
        val starts = List(measurables.size) { (it * pitch).roundToInt() }
        val placeables =
            measurables.mapIndexed { index, measurable ->
                val width = (((index + 1) * pitch - gapPx).roundToInt() - starts[index]).coerceAtLeast(0)
                measurable.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = constraints.maxHeight))
            }
        layout(constraints.maxWidth, placeables.maxOf { it.height }) {
            placeables.forEachIndexed { index, placeable -> placeable.place(starts[index], 0) }
        }
    }
}
