package com.plumemu.game

import android.text.format.Formatter
import androidx.compose.animation.core.animate
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import com.plumemu.coreui.components.ListEdgeFade
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.plumemu.R
import com.plumemu.battery.BatteryPace
import com.plumemu.battery.PickingBatteryLine
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.lastPlayed
import com.plumemu.home.playTime
import com.plumemu.home.variantLabel
import com.plumemu.identify.name.VariantTags
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Info: read-only facts in the details' table style (labels `muted`, values `text2`, 12 apart,
 * lines 4 apart), each value on one line with an ellipsis. Titles and file names are shown, never
 * logged.
 *
 * The facts and "Name from" scroll when the panel is too short for them (Largest in Serif on 375 high):
 * Info's one row to stand on is Back, so Up and Down ([scroll]) scroll them instead, each press showing
 * the next row whole, and the rows still hidden on a side are always visibly cut at that edge, so it is
 * plain there is more ([nextFactsScroll], [factsFit]); a finger scrolls them too. When they fit
 * nothing moves. No row is ever left out or cut for good.
 */
@Composable
internal fun InfoFacts(
    facts: GameFacts,
    scroll: InfoScroll,
    modifier: Modifier = Modifier,
) {
    val colors = PlumTheme.colors
    val style = PlumTheme.type.meta
    val lines = infoLines(facts)
    val battery = facts.batteryPace
    // Label column as wide as the widest label; each fact is its own row so a value that wraps keeps its label beside it.
    val measurer = rememberTextMeasurer()
    val labelStyle = style.copy(color = colors.muted)
    val density = LocalDensity.current
    // Keyed on the labels, not the list: infoLines builds a new list on every recomposition.
    val labels = lines.map { it.first }
    val labelWidth = remember(labels, labelStyle, density) { with(density) { labels.maxOf { measurer.measure(it, labelStyle, maxLines = 1).size.width }.toDp() } }
    // Up to Huge a value may take two lines before it is cut; at Largest the table keeps one line each so
    // fewer rows need the scroll.
    val valueLines = if (PlumTheme.layout.textScale > VALUE_TWO_LINES_MAX_SCALE) 1 else 2
    val state = rememberScrollState()
    val rows = remember { FactsRows() }
    FollowPresses(scroll, state, rows)
    // The height Back leaves decides how high the scroll is and how much room follows the last row (factsFit).
    BoxWithConstraints(modifier) {
        val available = if (constraints.hasBoundedHeight) constraints.maxHeight else Constraints.Infinity
        val shape = rows.shape
        val fit = remember(available, shape) { factsFit(available, shape) }
        Box(Modifier.atMostHigh(fit.viewport)) {
        Column(Modifier.verticalScroll(state).then(rows.contentModifier).testTag(GamePanelTags.FACTS_SCROLL)) {
            InfoRows(lines, battery, labelStyle, labelWidth, valueLines, rows)
            val afterTable = lines.size + (if (battery == null) 0 else 1)
            facts.name?.let { NameFrom(it) { line -> rows.place(afterTable + line) } }
            // The rows of "Name from" (its label and each line) come before the description's.
            val afterName = afterTable + (facts.name?.let { 1 + nameFromLines(it).size } ?: 0)
            facts.scraped?.description?.takeIf { it.isNotEmpty() }?.let { Description(it) { line -> rows.place(afterName + line) } }
            if (fit.endRoom > 0) Spacer(Modifier.exactlyHigh(fit.endRoom))
        }
        // The cut row fades out at the edge it continues past (design/rules.md > Lists that scroll).
        if (state.canScrollForward) ListEdgeFade(atBottom = true, Modifier.align(Alignment.BottomCenter).testTag(GamePanelTags.FADE_BOTTOM))
        if (state.canScrollBackward) ListEdgeFade(atBottom = false, Modifier.align(Alignment.TopCenter).testTag(GamePanelTags.FADE_TOP))
        }
    }
}

/** The facts' table: a row per fact, the label column [labelWidth] wide. */
@Composable
private fun InfoRows(
    lines: List<Pair<String, String>>,
    battery: BatteryPace?,
    labelStyle: TextStyle,
    labelWidth: Dp,
    valueLines: Int,
    rows: FactsRows,
) {
    val style = PlumTheme.type.meta.copy(color = PlumTheme.colors.text2)
    Column(
        Modifier.padding(horizontal = PlumDimens.gameRowPadding).testTag(GamePanelTags.FACTS),
        verticalArrangement = Arrangement.spacedBy(PlumDimens.factsRowGap),
    ) {
        lines.forEachIndexed { index, (label, value) ->
            Row(rows.place(index).testTag(GamePanelTags.fact(index))) {
                BasicText(text = label, style = labelStyle, maxLines = 1, modifier = Modifier.width(labelWidth))
                Spacer(Modifier.width(PlumDimens.factsColumnGap))
                BasicText(text = value, style = style, maxLines = valueLines, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
        // The last row: the usual pace of a game measured before. The glyph names it, so it has no label,
        // and it takes the table's whole width (the value column is too narrow on the side panel), like "Name from".
        if (battery != null) {
            PickingBatteryLine(battery, style, modifier = rows.place(lines.size).testTag(GamePanelTags.fact(lines.size)), maxLines = INFO_BATTERY_LINES)
        }
    }
}

/** At most [px] high (exact pixels: the stops are worked out in them). */
private fun Modifier.atMostHigh(px: Int): Modifier =
    layout { measurable, constraints ->
        val height = minOf(px, constraints.maxHeight)
        val placeable = measurable.measure(constraints.copy(minHeight = minOf(constraints.minHeight, height), maxHeight = height))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

/** Exactly [px] high, no width. */
private fun Modifier.exactlyHigh(px: Int): Modifier = layout { _, _ -> layout(0, px) {} }

/**
 * Info's Up and Down. Held by its screen (`PanelScreen.Info`), not the panel: a press must never
 * replace the panel, or the name's source or the arcade set read meanwhile would no longer find it
 * (`GameMenuController.showIfStill`). Main thread only.
 */
@Stable
internal class InfoScroll {
    /** Set by [InfoFacts] while it shows (never read while composing): moves the facts a row, false when nothing moves. */
    internal var mover: ((isDown: Boolean) -> Boolean)? = null

    /** One press of Down ([isDown]) or Up; false, and nothing changes, when the facts cannot move that way. */
    fun press(isDown: Boolean): Boolean = mover?.invoke(isDown) ?: false
}

/**
 * "Name from" under the facts, in the same label and value colours: the sentences are longer than
 * the table's values, so they take the panel's width and may wrap to a second line. [place]: the
 * modifier of its line [index] (0 the label), for the scroll ([FactsRows]).
 */
@Composable
private fun NameFrom(
    name: NameFacts,
    place: (index: Int) -> Modifier,
) {
    val colors = PlumTheme.colors
    val style = PlumTheme.type.meta
    Column(
        modifier = Modifier.padding(start = PlumDimens.gameRowPadding, end = PlumDimens.gameRowPadding, top = PlumDimens.panelBlockGap).testTag(GamePanelTags.NAME_FROM),
        verticalArrangement = Arrangement.spacedBy(PlumDimens.factsRowGap),
    ) {
        BasicText(text = stringResource(R.string.game_info_name_from), style = style.copy(color = colors.muted), maxLines = 1, modifier = place(0).testTag(GamePanelTags.nameFromLine(0)))
        nameFromLines(name).forEachIndexed { index, line ->
            BasicText(text = line.resolve(), style = style.copy(color = colors.text2), maxLines = NAME_FROM_LINES, overflow = TextOverflow.Ellipsis, modifier = place(index + 1).testTag(GamePanelTags.nameFromLine(index + 1)))
        }
    }
}

/**
 * The synopsis under the facts, whole. It is cut into blocks of [DESCRIPTION_BLOCK_LINES] lines at the
 * width it is drawn at, each block one row of the scroll ([place]: the modifier of row [index], 0 the
 * label), so every Up and Down shows the next block whole and a finger scrolls it the same way. A
 * synopsis is never cut with an ellipsis: that is the point of the block.
 */
@Composable
private fun Description(
    text: String,
    place: (index: Int) -> Modifier,
) {
    val colors = PlumTheme.colors
    val style = PlumTheme.type.meta
    val measurer = rememberTextMeasurer()
    val padding = PlumDimens.gameRowPadding
    Column(
        modifier = Modifier.padding(start = padding, end = padding, top = PlumDimens.panelBlockGap).testTag(GamePanelTags.DESCRIPTION),
        verticalArrangement = Arrangement.spacedBy(PlumDimens.factsRowGap),
    ) {
        BasicText(text = stringResource(R.string.game_info_description), style = style.copy(color = colors.muted), maxLines = 1, modifier = place(0).testTag(GamePanelTags.descriptionBlock(0)))
        BoxWithConstraints {
            val width = constraints.maxWidth
            val blocks = remember(text, width, style) { descriptionBlocks(text) { paragraph -> measuredLines(measurer, paragraph, style, width) } }
            Column(verticalArrangement = Arrangement.spacedBy(PlumDimens.factsRowGap)) {
                blocks.forEachIndexed { index, block ->
                    BasicText(text = block, style = style.copy(color = colors.text2), modifier = place(index + 1).testTag(GamePanelTags.descriptionBlock(index + 1)))
                }
            }
        }
    }
}

/** [paragraph] wrapped at [width] px: the text of each line it takes. */
private fun measuredLines(
    measurer: TextMeasurer,
    paragraph: String,
    style: TextStyle,
    width: Int,
): List<String> {
    if (width <= 0) return listOf(paragraph)
    val layout = measurer.measure(paragraph, style, constraints = Constraints(maxWidth = width))
    return (0 until layout.lineCount).map { paragraph.substring(layout.getLineStart(it), layout.getLineEnd(it)).trimEnd() }
}

/**
 * [text] as blocks of at most [DESCRIPTION_BLOCK_LINES] lines, joined by line breaks; a paragraph ends a
 * block. [linesOf] wraps a paragraph at the width it is drawn at. Blank paragraphs are dropped.
 */
internal fun descriptionBlocks(
    text: String,
    linesOf: (paragraph: String) -> List<String>,
): List<String> =
    text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.flatMap { paragraph ->
        linesOf(paragraph).chunked(DESCRIPTION_BLOCK_LINES).map { it.joinToString("\n") }
    }

/** A block of the description holds at most this many lines: a Down press then moves a few lines, not a page. */
private const val DESCRIPTION_BLOCK_LINES = 3

/**
 * Where Info's rows are in the scrolled content, from the last layout: each one's top and bottom (px,
 * exclusive), top to bottom. The content's own positions, so a scroll moving meanwhile (a slide, a
 * fling) never shifts them. Main thread only.
 */
private class FactsRows {
    private val placed = HashMap<Int, LayoutCoordinates>()
    private val modifiers = HashMap<Int, Modifier>()

    /**
     * State: new positions work out the scroll's fit again (`factsFit`), a frame after the rows are
     * first placed or change; equal ones (on every scroll frame) do not.
     */
    var shape by mutableStateOf(emptyList<IntRange>(), structuralEqualityPolicy())
        private set

    /** On the scrolled content (inside the scroll): reads where the rows are once all are placed. */
    val contentModifier: Modifier =
        Modifier.onGloballyPositioned { content ->
            shape =
                placed.entries.sortedBy { it.key }.mapNotNull { (_, row) ->
                    if (!row.isAttached) return@mapNotNull null
                    val box = content.localBoundingBoxOf(row, clipBounds = false)
                    box.top.roundToInt() until box.bottom.roundToInt()
                }
        }

    /** Row [index]'s modifier, kept: a recomposition creates none. */
    fun place(index: Int): Modifier = modifiers.getOrPut(index) { Modifier.onPlaced { placed[index] = it } }
}

/**
 * Lets [scroll]'s presses move the facts while they show: each goes to [nextFactsScroll] from where
 * the last press aimed (a held key's presses add up while sliding) and slides there (180 ms ease-out)
 * with motion on, jumps with it off. Both run as a scroll of the state, which a fling's equals: a press
 * during a fling takes over from it (a bare `dispatchRawDelta` would jump, then let the fling carry on).
 */
@Composable
private fun FollowPresses(
    scroll: InfoScroll,
    state: ScrollState,
    rows: FactsRows,
) {
    val motion = PlumTheme.motion
    val spec = rememberUpdatedState(motion.slideSpec<Float>())
    val isMotionOn = rememberUpdatedState(motion.isOn)
    val scope = rememberCoroutineScope()
    DisposableEffect(scroll, state, rows) {
        var slide: Job? = null
        var aim = 0
        val mover = { isDown: Boolean ->
            val from = if (slide?.isActive == true) aim else state.value
            val target = nextFactsScroll(from, state.viewportSize, state.maxValue, rows.shape, isDown)
            if (target != from) {
                aim = target
                slide?.cancel()
                // Undispatched: with nothing else scrolling, a jump lands before this returns.
                slide =
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        state.scroll {
                            if (!isMotionOn.value) {
                                scrollBy((target - state.value).toFloat())
                            } else {
                                var at = state.value.toFloat()
                                animate(at, target.toFloat(), animationSpec = spec.value) { value, _ -> at += scrollBy(value - at) }
                            }
                        }
                    }
            }
            target != from
        }
        scroll.mover = mover
        onDispose { if (scroll.mover === mover) scroll.mover = null }
    }
}

private const val NAME_FROM_LINES = 2

/** The Battery row may wrap to three lines on the side panel's width before it is cut. */
private const val INFO_BATTERY_LINES = 3
private const val VALUE_TWO_LINES_MAX_SCALE = 1.3f

/** The emulator Play starts, in the Info panel's words; also the second screen's played line. */
@Composable
internal fun emulatorLabel(emulator: EmulatorToStart?): String =
    when {
        emulator == null -> stringResource(R.string.game_info_no_emulator)
        emulator.isNotChosenYet -> stringResource(R.string.game_info_emulator_not_chosen)
        emulator.isInstalled -> emulator.name
        else -> stringResource(R.string.game_info_not_installed, emulator.name)
    }

@Composable
private fun infoLines(facts: GameFacts): List<Pair<String, String>> {
    val context = LocalContext.current
    val none = stringResource(R.string.game_info_none)
    val emulator = emulatorLabel(facts.emulator)
    // Rev 1, English translation...: read from the file name; no row for a plain name or a tag with no word.
    val version = remember(facts.fileName, context) { variantLabel(context.resources, VariantTags.of(facts.fileName.substringBeforeLast('.'))) }
    val rows = listOf(
        stringResource(R.string.game_info_system) to facts.systemName,
        stringResource(R.string.game_info_region) to facts.regions.split(',').filter { it.isNotBlank() }.joinToString(", ").ifEmpty { none },
        // An arcade set the pack lists shows the set in the file's place: its short name is the file's
        // base name, and one row instead of two keeps Info's height (it already fills 432 at Largest).
        facts.arcadeSet?.let { set -> stringResource(R.string.game_info_arcade_set) to set.details.fold(set.shortName) { shown, detail -> stringResource(R.string.game_info_arcade_set_value, shown, detail) } }
            ?: (stringResource(R.string.game_info_file) to facts.fileName),
        stringResource(R.string.game_info_size) to Formatter.formatShortFileSize(context, facts.sizeBytes),
        stringResource(R.string.game_info_folder) to facts.folderName,
        stringResource(R.string.game_info_emulator) to emulator,
        stringResource(R.string.game_info_last_played) to lastPlayed(facts.lastPlayedAt?.let { daysAgo(it, System.currentTimeMillis()) }),
        stringResource(R.string.game_info_play_time) to playTime(TimeUnit.SECONDS.toMinutes(facts.totalPlaySeconds).toInt()),
    )
    val about = scrapedRows(facts.scraped)
    val versionRows = if (version.isEmpty()) emptyList() else listOf(stringResource(R.string.game_info_version) to version)
    return rows.take(VERSION_ROW_AT) + versionRows + about + rows.drop(VERSION_ROW_AT)
}

/** Year, Publisher, Developer, Genre and Players, each only when ScreenScraper gave it; they follow Region (and Version). */
@Composable
private fun scrapedRows(scraped: ScrapedFacts?): List<Pair<String, String>> {
    if (scraped == null) return emptyList()
    return listOfNotNull(
        scraped.year?.let { stringResource(R.string.game_info_year) to it.toString() },
        scraped.publisher.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.game_info_publisher) to it },
        scraped.developer.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.game_info_developer) to it },
        scraped.genre.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.game_info_genre) to it },
        scraped.players.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.game_info_players) to it },
    )
}

/** Info's Version row comes right after Region. */
private const val VERSION_ROW_AT = 2

/** Calendar days between [at] and [now] in [zone] (the device's by default; 0 today, 1 yesterday); never negative (a clock set back reads as today). */
internal fun daysAgo(
    at: Long,
    now: Long,
    zone: TimeZone = TimeZone.getDefault(),
): Int {
    fun dayStart(millis: Long): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    // Rounded: a day with a daylight-saving change is 23 or 25 hours long.
    val days = Math.round((dayStart(now) - dayStart(at)) / TimeUnit.DAYS.toMillis(1).toDouble()).toInt()
    return days.coerceAtLeast(0)
}
