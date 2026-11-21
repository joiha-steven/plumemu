package com.plumemu.home

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.plumemu.R
import com.plumemu.battery.BatteryPace
import com.plumemu.battery.pickingBatteryCellText
import com.plumemu.coreui.components.EllipsisText
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import kotlin.math.ceil

/*
 * The wide details column's block under the title: a hairline, then one fact per line, a label column (`muted`, as wide as the widest label shown)
 * and the value after it: Genre, Rating ("8.5/10"), Played, Last played, Emulator, Battery (the game's usual time on a charge), in that order,
 * at one even pitch. A fact with nothing to say takes no line; the hairline is always drawn.
 */

/** The facts of the block, in the order they are read. */
internal enum class DetailsCell { GENRE, RATING, PLAYED, LAST, EMULATOR, BATTERY }

/**
 * The block for [details] and the play facts [shown], in one box of a height that depends only on the text size (never on a game's data): the
 * height of [HELD_ROWS] label-over-value rows at the tightest gaps, as before the facts went one to a line. Until [revealed] the box is empty;
 * from the reveal the facts in hand stand in their order, and a value that comes later gets the line it would have had on time. The box never
 * changes height, so nothing outside it moves.
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
            is ShownPlay.Read -> PlayFacts(shown.play.lastPlayedDaysAgo, shown.play.playTimeMinutes, shown.play.emulatorText())
            else -> null
        }
    // One read of each per pass, handed to the texts below.
    val locale = currentLocale()
    val numbers = rememberCountFormat()
    val shownDetails = details.takeIf { revealed }
    val genre = shownDetails?.genre.orEmpty()
    val ratingValue = shownDetails?.rating?.takeIf { it in 1..MAX_RATING }
    val rating = if (ratingValue != null) stringResource(R.string.home_details_rating, ratingNumberOf(ratingValue, locale, numbers)) else ""
    // A game never played says so once, in the Played line; there is no Last played line for it.
    val never = facts?.neverPlayed() == true
    val playTimeText = facts?.let { if (never) stringResource(R.string.second_screen_not_played) else playTime(it.playTimeMinutes, numbers) }.orEmpty()
    val lastPlayedText = facts?.let { if (never) "" else lastPlayed(it.lastPlayedDaysAgo, numbers) }.orEmpty()
    val emulator = facts?.emulator.orEmpty()
    val battery = paceOf(shown)?.let { pickingBatteryCellText(it) }?.full.orEmpty()
    val labels = factLabels()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val meta = PlumTheme.type.meta
    val labelStyle = PlumTheme.type.detailsLabel
    val cellHeight = remember(measurer, density, meta, labelStyle) { cellHeightPx(measurer, density, meta, labelStyle) }
    val lineHeight = remember(measurer, density, meta, labelStyle) { lineHeightPx(measurer, meta, labelStyle) }
    val values =
        if (!revealed) {
            emptyList()
        } else {
            listOf(
                DetailsCell.GENRE to genre,
                DetailsCell.RATING to rating,
                DetailsCell.PLAYED to playTimeText,
                DetailsCell.LAST to lastPlayedText,
                DetailsCell.EMULATOR to emulator,
                DetailsCell.BATTERY to battery,
            ).filter { it.second.isNotEmpty() }
        }
    // The label column is as wide as the widest label shown, so every value starts on one line down the block.
    val labelWidth =
        remember(measurer, labelStyle, values.map { it.first }, labels) {
            values.maxOfOrNull { (cell, _) -> ceil(measurer.measure(labels.of(cell), labelStyle, softWrap = false, maxLines = 1).multiParagraph.maxIntrinsicWidth).toInt() } ?: 0
        }
    val policy = remember(cellHeight, lineHeight) { FactsPolicy(cellHeight, lineHeight) }
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
        val labelDp = with(density) { labelWidth.toDp() }
        values.forEach { (cell, value) -> FactRow(cell, labels.of(cell), value, labelDp) }
    }
}

private class FactLabels(val genre: String, val rating: String, val played: String, val last: String, val emulator: String, val battery: String) {
    fun of(cell: DetailsCell): String =
        when (cell) {
            DetailsCell.GENRE -> genre
            DetailsCell.RATING -> rating
            DetailsCell.PLAYED -> played
            DetailsCell.LAST -> last
            DetailsCell.EMULATOR -> emulator
            DetailsCell.BATTERY -> battery
        }

    override fun equals(other: Any?) = other is FactLabels && DetailsCell.entries.all { of(it) == other.of(it) }

    override fun hashCode() = DetailsCell.entries.fold(0) { h, c -> h * 31 + of(c).hashCode() }
}

@Composable
private fun factLabels() =
    FactLabels(
        stringResource(R.string.home_details_genre),
        stringResource(R.string.home_details_rating_label),
        stringResource(R.string.home_details_play_time),
        stringResource(R.string.home_details_last_played),
        stringResource(R.string.home_details_emulator),
        stringResource(R.string.home_details_battery),
    )

/**
 * One fact on one line: the label in `muted` in a column [labelWidth] wide, the gutter, then the value in `text` (words in `meta`, figures in
 * `factNumber`, the rating Mono `ratingNumber`), cut at the end with an ellipsis when the column is too narrow. The rating is spoken as "Rating, 8.5/10".
 */
@Composable
private fun FactRow(
    cell: DetailsCell,
    label: String,
    value: String,
    labelWidth: Dp,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val isFigure = cell == DetailsCell.PLAYED || cell == DetailsCell.LAST || cell == DetailsCell.BATTERY
    val tag =
        when (cell) {
            DetailsCell.EMULATOR -> Modifier.testTag(HomeTags.DETAILS_EMULATOR)
            DetailsCell.BATTERY -> Modifier.testTag(HomeTags.DETAILS_BATTERY)
            DetailsCell.RATING -> Modifier.testTag(HomeTags.DETAILS_RATING).clearAndSetSemantics { contentDescription = "$label, $value" }
            else -> Modifier
        }
    Row(Modifier.cellId(CellId(dropRankOf(cell))).then(tag), verticalAlignment = Alignment.CenterVertically) {
        BasicText(text = label, style = type.detailsLabel.copy(color = colors.muted), maxLines = 1, modifier = Modifier.width(labelWidth))
        Spacer(Modifier.width(PlumDimens.factsColumnGap))
        EllipsisText(text = value, style = when { cell == DetailsCell.RATING -> type.ratingNumber; isFigure -> type.factNumber; else -> type.meta }, color = { colors.text }, maxLines = 1)
    }
}

private fun dropRankOf(cell: DetailsCell): Int =
    when (cell) {
        DetailsCell.GENRE -> DropRank.GENRE
        DetailsCell.RATING -> DropRank.RATING
        DetailsCell.PLAYED -> DropRank.PLAYED
        DetailsCell.LAST -> DropRank.LAST_PLAYED
        DetailsCell.EMULATOR -> DropRank.EMULATOR
        DetailsCell.BATTERY -> DropRank.BATTERY
    }

/** The height in px of one label-over-value row (the box's unit): its label line, the gap, and a value line. */
private fun cellHeightPx(
    measurer: androidx.compose.ui.text.TextMeasurer,
    density: Density,
    meta: TextStyle,
    label: TextStyle,
): Int {
    val labelHeight = measurer.measure(HELD_LINE, label, maxLines = 1).size.height
    val valueHeight = measurer.measure(HELD_LINE, meta, maxLines = 1).size.height
    return with(density) { labelHeight + PlumDimens.detailsLabelGap.roundToPx() + valueHeight }
}

/** One fact's line in px: the taller of the label's line and the value's. */
private fun lineHeightPx(
    measurer: androidx.compose.ui.text.TextMeasurer,
    meta: TextStyle,
    label: TextStyle,
): Int = maxOf(measurer.measure(HELD_LINE, label, maxLines = 1).size.height, measurer.measure(HELD_LINE, meta, maxLines = 1).size.height)

private fun paceOf(shown: ShownPlay): BatteryPace? =
    when (shown) {
        is ShownPlay.Read -> shown.play.batteryPace
        is ShownPlay.Fixed -> shown.play.takeIf { it.batteryMinutesOnCharge != null || it.batteryPercentPerHour != null }?.let { BatteryPace(it.batteryMinutesOnCharge, it.batteryPercentPerHour) }
        else -> null
    }

/**
 * Drop order of the facts when the box really cannot hold them (Largest): a smaller rank leaves first. [LAST_PLAYED] and [PLAYED] never do.
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

/** A fact's place in [FactsPolicy]: its drop rank. */
private data class CellId(val dropRank: Int)

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
 * The facts box: as high as [HELD_ROWS] label-over-value rows of [cellHeight] at the tightest gaps, whatever the facts are, and so also when it
 * has none (before the reveal): the height is never read from the data. The facts stand one to a line of [lineHeight] at one pitch, the same for
 * every game at a text size: the room of [HELD_LINES] lines shared out, between [PlumDimens.factsLineGapMin] and [PlumDimens.factsLineGapMax]. When the
 * box cannot hold them (the text is large), facts leave whole in rank order (BATTERY, EMULATOR, GENRE, then RATING; PLAYED and LAST PLAYED stay)
 * and the rest keep their order; a fact is never cut. Intrinsic heights are the box's, so the cover is sized from it before any value is in.
 */
private class FactsPolicy(private val cellHeight: Int, private val lineHeight: Int) : MeasurePolicy {
    private fun held(rows: Int, gap: Int) = rows * (cellHeight + gap)

    private fun Density.tight() = PlumDimens.detailsDividerGapTight.roundToPx().coerceAtMost(PlumDimens.detailsDividerGap.roundToPx())

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val width = constraints.maxWidth
        val tight = tight()
        val box = minOf(constraints.maxHeight, held(HELD_ROWS, tight))
        // The six lines share the room the three held rows had (their two inner label gaps and three row gaps), so all six always fit.
        val gap = ((box - HELD_LINES * lineHeight) / HELD_LINES).coerceIn(PlumDimens.factsLineGapMin.roundToPx(), PlumDimens.factsLineGapMax.roundToPx())
        val ids = measurables.map { it.parentData as CellId }
        fun keptWithout(dropped: Int) = measurables.indices.filter { ids[it].dropRank !in DROP_ORDER.take(dropped) }
        var dropped = 0
        var kept = keptWithout(0)
        while (kept.size * (lineHeight + gap) > box && dropped < DROP_ORDER.size) {
            dropped++
            kept = keptWithout(dropped)
        }
        val placeables = kept.map { measurables[it].measure(Constraints(minWidth = 0, maxWidth = width)) }
        return layout(width, box.coerceIn(constraints.minHeight, constraints.maxHeight.coerceAtLeast(constraints.minHeight))) {
            var y = 0
            placeables.forEach {
                y += gap
                it.place(0, y + (lineHeight - it.height) / 2)
                y += lineHeight
            }
        }
    }

    /** The least the box can take (the cover never gives up more): [MIN_ROWS] rows at the tightest gaps. */
    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) = held(MIN_ROWS, tight())

    /** The cover is sized from [HELD_ROWS] rows at the tightest gaps. */
    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) = held(HELD_ROWS, tight())

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) = measurables.maxOfOrNull { it.minIntrinsicWidth(height) } ?: 0

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) = measurables.maxOfOrNull { it.maxIntrinsicWidth(height) } ?: 0

    private companion object {
        /** The least the box can take. */
        const val MIN_ROWS = 2
        val DROP_ORDER = listOf(DropRank.BATTERY, DropRank.EMULATOR, DropRank.GENRE, DropRank.RATING)
    }
}

/** The box is as high as this many label-over-value rows were (the cover and the synopsis are sized from it). */
private const val HELD_ROWS = 3

/** The facts the box's pitch is shared out for: all six. */
private const val HELD_LINES = 6


/** The width of the details column the facts block is set in: [PlumDimens.detailsColumnWidth] unless a narrow screen's games stop gives less. */
internal val LocalDetailsColumnWidth = androidx.compose.runtime.staticCompositionLocalOf { PlumDimens.detailsColumnWidth }

private const val DIVIDER_ALPHA = 0.10f
