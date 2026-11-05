package com.plumemu.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.plumemu.R
import com.plumemu.battery.BatteryPace
import com.plumemu.battery.pickingBatteryCellText
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.game.emulatorLabel
import com.plumemu.coreui.theme.PlumTheme
import kotlin.math.ceil
import kotlin.math.roundToInt

/*
 * The wide details column's block under the title, as the board draws it: a hairline, then small capital labels over their values in
 * [HELD_ROWS] rows: GENRE + RATING (five dots), PLAYED + LAST PLAYED, EMULATOR + BATTERY (the game's usual time on a charge). A cell with nothing to say
 * takes no place (the real cells pack with no holes); the hairline is always drawn. Which cells share a row is [arrangeCells]'s call.
 */

/**
 * Dots filled out of [RATING_DOTS] for a rating out of 10: halved, to the nearest half dot; a tie goes down (the board draws 8.5 as four
 * dots). Under 0 and over 10 give 0, so does 0 (no rating).
 */
internal fun ratingDots(outOfTen: Double?): Float {
    if (outOfTen == null || outOfTen <= 0.0 || outOfTen > 10.0) return 0f
    return (ceil(outOfTen - 0.5) / 2.0).toFloat()
}

internal const val RATING_DOTS = 5

/**
 * The block for [details] and the play facts [shown], in one box of a height that depends only on the column's width and the text size
 * (never on a game's data): [HELD_ROWS] rows at the tightest gaps. Until [revealed] the box is empty; from the reveal the cells stand where
 * [arrangeCells] puts the values in hand, and a value that comes later (a slow read, a changed emulator) gets the place it would have had
 * on time: the arrangement is a function of the values, so the same game looks the same whenever its values arrive. The box never changes
 * height, so nothing outside it moves.
 */
@Composable
internal fun ColumnScope.DetailsBlock(
    details: GameDetails?,
    shown: ShownPlay,
    revealed: Boolean = true,
) {
    val colors = PlumTheme.colors
    val facts =
        when (shown) {
            is ShownPlay.Fixed -> shown.play
            is ShownPlay.Read -> PlayFacts(shown.play.lastPlayedDaysAgo, shown.play.playTimeMinutes, emulatorLabel(shown.play.emulator))
            else -> null
        }
    // One read of each per pass, handed to the texts below.
    val locale = currentLocale()
    val numbers = rememberCountFormat()
    val shownDetails = details.takeIf { revealed }
    val fullGenre = shownDetails?.genre.orEmpty()
    val dots = ratingDots(shownDetails?.rating?.takeIf { it in 1..MAX_RATING }?.let { it / 2.0 })
    val ratingValue = shownDetails?.rating?.takeIf { it in 1..MAX_RATING }
    val ratingNumber = if (dots > 0f && ratingValue != null) ratingNumberOf(ratingValue, locale, numbers) else ""
    val rating = if (ratingNumber.isEmpty()) "" else stringResource(R.string.home_details_rating, ratingNumber)
    val playTimeText = facts?.let { playTime(it.playTimeMinutes, numbers) }.orEmpty()
    val lastPlayedText = facts?.let { lastPlayed(it.lastPlayedDaysAgo, numbers) }.orEmpty()
    val emulator = facts?.emulator.orEmpty()
    val pace = paceOf(shown)
    val battery = pace?.let { pickingBatteryCellText(it) }
    val batteryFull = battery?.full.orEmpty()
    // Placed for the widest time it can say: a time that changes while the game rests moves nothing.
    val widestFull = battery?.widestFull.orEmpty()
    val widestShort = battery?.widestShort.orEmpty()
    val labels = factLabels(locale)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val columnWidth = LocalDetailsColumnWidth.current
    val fit = remember(measurer, density, columnWidth) { LabelFit(measurer, density, columnWidth) }
    val meta = PlumTheme.type.meta
    val labelStyle = PlumTheme.type.detailsLabel
    val dotsPx = with(density) { (PlumDimens.ratingNumberGap + PlumDimens.ratingDotSize * RATING_DOTS + PlumDimens.ratingDotGap * (RATING_DOTS - 1)).roundToPx() }
    val cellHeight = remember(measurer, density, meta, labelStyle) { cellHeightPx(measurer, density, meta, labelStyle) }
    // The arrangement follows the values in hand (a pure function of them), from the reveal on.
    val arrangement =
        remember(fit, meta, labelStyle, locale, revealed, fullGenre, ratingNumber, playTimeText, lastPlayedText, emulator, batteryFull, widestFull, widestShort, battery?.short, dotsPx) {
            if (!revealed) {
                null
            } else {
                fun need(label: String, value: String, extra: Int = 0) = fit.cellShareNeeded(label, labelStyle, value, meta, extra)
                val needs =
                    buildMap {
                        if (fullGenre.isNotEmpty()) put(DetailsCell.GENRE, need(labels.genre, fullGenre))
                        if (rating.isNotEmpty()) put(DetailsCell.RATING, need(labels.rating, ratingNumber, dotsPx))
                        if (playTimeText.isNotEmpty()) put(DetailsCell.PLAYED, need(labels.played, playTimeText))
                        if (lastPlayedText.isNotEmpty()) put(DetailsCell.LAST, need(labels.last, lastPlayedText))
                        if (emulator.isNotEmpty()) put(DetailsCell.EMULATOR, need(labels.emulator, emulator))
                        if (batteryFull.isNotEmpty()) put(DetailsCell.BATTERY, maxOf(need(labels.battery, widestFull), need(labels.battery, batteryFull)))
                    }
                // GENRE, then EMULATOR, give way in their row down to a floor: the label and a few letters.
                val floors = mapOf(DetailsCell.GENRE to maxOf(need(labels.genre, FLOOR_TEXT), MIN_SHRUNK_SHARE), DetailsCell.EMULATOR to maxOf(need(labels.emulator, FLOOR_TEXT), MIN_SHRUNK_SHARE))
                arrangeCells(needs, if (batteryFull.isEmpty()) null else maxOf(need(labels.battery, widestShort), need(labels.battery, battery?.short.orEmpty())), HELD_ROWS, floors)
            }
        }
    val batteryText = if (arrangement?.batteryShort == true) battery?.short.orEmpty() else batteryFull
    val policy = remember(cellHeight) { FactsPolicy(cellHeight) }
    // The hairline is drawn in the column's top padding so the facts' own room is unchanged.
    FactsLayout(
        policy = policy,
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(if (revealed) HomeTags.DETAILS_FACTS else HomeTags.DETAILS_FACTS_PENDING)
                // Always drawn, before the reveal too, so it never blinks.
                .drawBehind {
                    drawRect(colors.text.copy(alpha = DIVIDER_ALPHA), Offset.Zero, Size(size.width, PlumDimens.listDividerWidth.toPx()))
                },
    ) {
        if (arrangement != null) FactsCells(arrangement, FactTexts(fullGenre, ratingNumber, rating, dots, playTimeText, lastPlayedText, emulator, batteryText), labels, fit)
    }
}

private class FactLabels(val genre: String, val rating: String, val played: String, val last: String, val emulator: String, val battery: String)

@Composable
private fun factLabels(locale: java.util.Locale) =
    FactLabels(
        stringResource(R.string.home_details_genre).uppercase(locale),
        stringResource(R.string.home_details_rating_label).uppercase(locale),
        stringResource(R.string.home_details_play_time).uppercase(locale),
        stringResource(R.string.home_details_last_played).uppercase(locale),
        stringResource(R.string.home_details_emulator).uppercase(locale),
        stringResource(R.string.home_details_battery).uppercase(locale),
    )

/** The values of the cells, "" for a cell with nothing to say. */
private class FactTexts(val genre: String, val ratingNumber: String, val rating: String, val dots: Float, val played: String, val last: String, val emulator: String, val battery: String)

/** The cells with a value, row by row as [arrangement] has them. */
@Composable
private fun FactsCells(arrangement: DetailsArrangement, t: FactTexts, l: FactLabels, fit: LabelFit) {
    fun need(cell: DetailsCell) = arrangement.needs.getValue(cell)
    arrangement.rows.forEachIndexed { row, cells ->
        for (cell in cells) {
            val share = arrangement.shares.getValue(cell)
            when (cell) {
                DetailsCell.GENRE -> Cell(share, l.genre, t.genre, CellId(DropRank.GENRE, share, row, need(DetailsCell.GENRE)), fit, VALUE_LINES)
                DetailsCell.RATING -> RatingCell(share, l.rating, t.ratingNumber, t.rating, t.dots, CellId(DropRank.RATING, share, row, need(DetailsCell.RATING)), fit)
                DetailsCell.PLAYED -> Cell(share, l.played, t.played, CellId(DropRank.PLAYED, share, row, need(DetailsCell.PLAYED)), fit, VALUE_LINES)
                DetailsCell.LAST -> Cell(share, l.last, t.last, CellId(DropRank.LAST_PLAYED, share, row, need(DetailsCell.LAST)), fit, VALUE_LINES)
                DetailsCell.EMULATOR -> Cell(share, l.emulator, t.emulator, CellId(DropRank.EMULATOR, share, row, need(DetailsCell.EMULATOR)), fit, VALUE_LINES, HomeTags.DETAILS_EMULATOR)
                DetailsCell.BATTERY -> Cell(share, l.battery, t.battery, CellId(DropRank.BATTERY, share, row, need(DetailsCell.BATTERY)), fit, VALUE_LINES, HomeTags.DETAILS_BATTERY)
            }
        }
    }
}

/** One cell's height in px: its label line, the gap, and a value line (or the rating dots if taller). The same for every cell. */
private fun cellHeightPx(
    measurer: androidx.compose.ui.text.TextMeasurer,
    density: Density,
    meta: TextStyle,
    label: TextStyle,
): Int {
    val labelHeight = measurer.measure(HELD_LINE, label, maxLines = 1).size.height
    val valueHeight = measurer.measure(HELD_LINE, meta, maxLines = 1).size.height
    return with(density) { labelHeight + PlumDimens.detailsLabelGap.roundToPx() + maxOf(valueHeight, PlumDimens.ratingDotSize.roundToPx()) }
}

private fun paceOf(shown: ShownPlay): BatteryPace? =
    when (shown) {
        is ShownPlay.Read -> shown.play.batteryPace
        is ShownPlay.Fixed -> shown.play.takeIf { it.batteryMinutesOnCharge != null || it.batteryPercentPerHour != null }?.let { BatteryPace(it.batteryMinutesOnCharge, it.batteryPercentPerHour) }
        else -> null
    }

/**
 * Drop order of the cells when the box really cannot hold them (Largest): a smaller rank leaves first. [LAST_PLAYED] and [PLAYED] never do.
 * The battery goes first, then the emulator (both in Info or a menu), so the cover keeps its height.
 */
private object DropRank {
    const val BATTERY = 1
    const val EMULATOR = 2
    const val GENRE = 3
    const val RATING = 4
    const val PLAYED = 5
    const val LAST_PLAYED = 0
}

/** A cell's place in [FactsPolicy]: its drop rank, its share of the column's width, its row and the share it needs to stand whole. */
private data class CellId(val dropRank: Int, val share: Float, val row: Int, val need: Float)

// Parent data (not a layout id) so the intrinsic measurables can read it too.
private fun Modifier.cellId(id: CellId): Modifier =
    this.then(
        object : ParentDataModifier {
            override fun Density.modifyParentData(parentData: Any?): Any = id
        },
    )

@Composable
private fun FactsLayout(
    policy: FactsPolicy,
    modifier: Modifier,
    content: @Composable () -> Unit,
) = Layout(content = content, modifier = modifier, measurePolicy = policy)

/**
 * The facts box: [HELD_ROWS] rows of cells of [cellHeight] at the tightest gaps, whatever the cells are, and so also when it has none (before
 * the reveal): the height is never read from the data and never grows with the room (what is left below it is the synopsis's). Cells stand in
 * the rows [arrangeCells] gave them. When the box cannot hold them (the cover is at its floor and the text is large), cells leave whole in rank
 * order (BATTERY, EMULATOR, GENRE, then RATING; PLAYED and LAST PLAYED stay) and the rest keep their rows; a cell is never cut. Intrinsic heights
 * are the box's, so the cover is sized from it before any value is in.
 */
private class FactsPolicy(private val cellHeight: Int) : MeasurePolicy {
    private class Measured(val id: CellId, val width: Int, val placeable: Placeable)

    private fun held(rows: Int, gap: Int) = rows * (cellHeight + gap)

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val width = constraints.maxWidth
        val gap = PlumDimens.detailsDividerGapTight.roundToPx().coerceAtMost(PlumDimens.detailsDividerGap.roundToPx())
        val box = minOf(constraints.maxHeight, held(HELD_ROWS, gap))
        val ids = measurables.map { it.parentData as CellId }

        fun rowsWithout(dropped: Int) = measurables.indices.filter { ids[it].dropRank !in DROP_ORDER.take(dropped) }.groupBy { ids[it].row }.toSortedMap().values.toList()

        // Every cell is as high as [cellHeight], so what leaves is known before anything is measured.
        var dropped = 0
        var kept = rowsWithout(0)
        while (kept.size * (cellHeight + gap) > box && dropped < DROP_ORDER.size) {
            dropped++
            kept = rowsWithout(dropped)
        }
        // A cell that was cut to an ellipsis for a neighbour that has left takes back the room, up to its need. Each cell is measured once.
        val rows =
            kept.map { row ->
                var slack = 1f - row.sumOf { ids[it].share.toDouble() }.toFloat()
                row.map { i ->
                    val id = ids[i]
                    val back = if (slack > 0f && id.need > id.share) minOf(slack, id.need - id.share) else 0f
                    slack -= back
                    val w = ((id.share + back) * width).roundToInt().coerceAtMost(width)
                    Measured(id, w, measurables[i].measure(Constraints(minWidth = w, maxWidth = w)))
                }
            }
        return layout(width, box.coerceIn(constraints.minHeight, constraints.maxHeight.coerceAtLeast(constraints.minHeight))) {
            var y = 0
            rows.forEach { row ->
                y += gap
                var x = 0
                row.forEach {
                    it.placeable.place(x, y)
                    x += it.width
                }
                y += row.maxOf { it.placeable.height }
            }
        }
    }

    /** The least the box can take (the cover never gives up more): PLAYED and LAST PLAYED, a row each, at the tightest gaps. */
    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) =
        held(MIN_ROWS, PlumDimens.detailsDividerGapTight.roundToPx().coerceAtMost(PlumDimens.detailsDividerGap.roundToPx()))

    /** The cover is sized from [HELD_ROWS] rows at the tightest gaps. */
    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) =
        held(HELD_ROWS, PlumDimens.detailsDividerGapTight.roundToPx().coerceAtMost(PlumDimens.detailsDividerGap.roundToPx()))

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) = measurables.maxOfOrNull { it.minIntrinsicWidth(height) } ?: 0

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) = measurables.maxOfOrNull { it.maxIntrinsicWidth(height) } ?: 0

    private companion object {
        /** The least the box can take. */
        const val MIN_ROWS = 2
        val DROP_ORDER = listOf(DropRank.BATTERY, DropRank.EMULATOR, DropRank.GENRE, DropRank.RATING)
    }
}

/**
 * The rows the box holds: GENRE + RATING; PLAYED + LAST PLAYED; EMULATOR + BATTERY (or a long emulator name on its own row with the battery
 * a third cell of another row). All six cells at Medium in Jost fit three rows (`HomeDetailsFullBlockTest`).
 */
private const val HELD_ROWS = 3

/** A label (one line) over its value (at most [valueLines], one); only cells with a value are composed. */
@Composable
private fun Cell(
    share: Float,
    label: String,
    value: String,
    id: CellId,
    fit: LabelFit,
    valueLines: Int,
    testTag: String? = null,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    Column(Modifier.cellId(id).padding(end = PlumDimens.factsColumnGap).then(if (testTag != null) Modifier.testTag(testTag) else Modifier), verticalArrangement = Arrangement.spacedBy(PlumDimens.detailsLabelGap)) {
        BasicText(text = label, style = fit.labelStyle(label, type.detailsLabel, share).copy(color = colors.muted), maxLines = LABEL_LINES)
        BasicText(text = value, style = type.meta.copy(color = colors.text), maxLines = valueLines, overflow = TextOverflow.Ellipsis)
    }
}

/** The rating cell: the number, then five dots; spoken as one "Rating, 8.5/10". */
@Composable
private fun RatingCell(
    share: Float,
    label: String,
    number: String,
    spoken: String,
    dots: Float,
    id: CellId,
    fit: LabelFit,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val description = "${stringResource(R.string.home_details_rating_label)}, $spoken"
    Column(
        Modifier.cellId(id).padding(end = PlumDimens.factsColumnGap).clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(PlumDimens.detailsLabelGap),
    ) {
        BasicText(text = label, style = fit.labelStyle(label, type.detailsLabel, share).copy(color = colors.muted), maxLines = LABEL_LINES)
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(text = number, style = type.meta.copy(color = colors.text), maxLines = 1)
            if (dots > 0f) {
                Spacer(Modifier.width(PlumDimens.ratingNumberGap))
                RatingDots(dots)
            }
        }
    }
}

/** [RATING_DOTS] dots, [filled] of them in the accent (a half dot is filled on its left half), the rest faint. */
@Composable
internal fun RatingDots(filled: Float) {
    val colors = PlumTheme.colors
    val size = PlumDimens.ratingDotSize
    val gap = PlumDimens.ratingDotGap
    Canvas(Modifier.size(size * RATING_DOTS + gap * (RATING_DOTS - 1), size).testTag(HomeTags.DETAILS_RATING_DOTS)) {
        val diameter = size.toPx()
        val radius = diameter / 2
        val step = diameter + gap.toPx()
        for (i in 0 until RATING_DOTS) {
            val centre = Offset(i * step + radius, radius)
            drawCircle(colors.text.copy(alpha = EMPTY_DOT_ALPHA), radius, centre)
            val share = (filled - i).coerceIn(0f, 1f)
            if (share >= 1f) {
                drawCircle(colors.accent, radius, centre)
            } else if (share > 0f) {
                clipRect(left = i * step, right = i * step + diameter * share) { drawCircle(colors.accent, radius, centre) }
            }
        }
    }
}

/**
 * Picks how a label is set in its cell: with its letter spacing, else without it, and when even that is too wide the
 * caller lets it take a second line (never an ellipsis). A cell is [share] of the wide details column.
 */
internal class LabelFit(
    private val measurer: androidx.compose.ui.text.TextMeasurer,
    private val density: androidx.compose.ui.unit.Density,
    /** The column the cells divide: 248 beside the list, less on a narrow screen's games stop. */
    private val columnWidth: Dp = PlumDimens.detailsColumnWidth,
) {
    fun labelStyle(
        label: String,
        spaced: TextStyle,
        share: Float,
    ): TextStyle {
        val width = with(density) { (columnWidth * share).roundToPx() } - gutterPx
        fun fits(style: TextStyle) =
            measurer.measure(label, style, softWrap = false, maxLines = 1, constraints = Constraints(maxWidth = Int.MAX_VALUE))
                .multiParagraph.intrinsics.maxIntrinsicWidth <= width
        return if (fits(spaced)) spaced else spaced.copy(letterSpacing = TextUnit.Unspecified)
    }

    /** The width of [text] in [style] on one line, in px. */
    fun naturalWidth(
        text: String,
        style: TextStyle,
    ): Int = ceil(measurer.measure(text, style, softWrap = false, maxLines = 1, constraints = Constraints(maxWidth = Int.MAX_VALUE)).multiParagraph.intrinsics.maxIntrinsicWidth).toInt()

    /** The room between one cell's text and the next cell's, in px: a cell's text is this much narrower than its share of the column. */
    val gutterPx: Int = with(density) { PlumDimens.factsColumnGap.roundToPx() }

    /** The share of the column [text] in [style] needs to stand on one line, with the gutter. */
    fun shareNeeded(
        text: String,
        style: TextStyle,
    ): Float = (naturalWidth(text, style) + gutterPx).toFloat() / columnPx()

    /** The share of the column a cell of [label] over a value of [valuePx] (a text and what stands beside it) needs, with the gutter. */
    fun cellShareNeeded(
        label: String,
        labelStyle: TextStyle,
        value: String,
        valueStyle: TextStyle,
        extraPx: Int,
    ): Float {
        val labelWidth = naturalWidth(label, labelStyle(label, labelStyle, THIRD))
        return (maxOf(labelWidth, naturalWidth(value, valueStyle) + extraPx) + gutterPx).toFloat() / columnPx()
    }

    /** The details column's width in px. */
    fun columnPx(): Int = with(density) { columnWidth.roundToPx() }

    /** Whether [value] in [style] sets in [maxLines] lines of a cell [share] of the column wide, without an ellipsis. */
    fun valueFits(
        value: String,
        style: TextStyle,
        share: Float,
        maxLines: Int,
    ): Boolean {
        val width = with(density) { (columnWidth * share).roundToPx() } - gutterPx
        return !measurer.measure(value, style, overflow = TextOverflow.Ellipsis, maxLines = maxLines, constraints = Constraints(maxWidth = width)).hasVisualOverflow
    }
}

/** The width of the details column the facts block is set in: [PlumDimens.detailsColumnWidth] unless a narrow screen's games stop gives less. */
internal val LocalDetailsColumnWidth = androidx.compose.runtime.staticCompositionLocalOf { PlumDimens.detailsColumnWidth }

private const val THIRD = 1f / 3f
private const val LABEL_LINES = 1

/** Every value is one line that ends in an ellipsis when its cell is too narrow. */
private const val VALUE_LINES = 1
private const val FLOOR_TEXT = "Wwwwww…"
private const val MIN_SHRUNK_SHARE = 0.3f

private const val DIVIDER_ALPHA = 0.10f
private const val EMPTY_DOT_ALPHA = 0.22f
