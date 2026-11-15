package com.plumemu.secondscreen

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.getTextLayoutResult
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.plumemu.battery.PickingBatteryLine
import kotlin.math.ceil
import com.plumemu.coreui.theme.EntryCardDimens
import com.plumemu.coreui.theme.MaskOpaque
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumFonts
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.SecondScreenGameCardDimens as D
import com.plumemu.coreui.theme.TypeRole

/** Test tags of the game card (boards `SecondGame*`). */
internal object GameCardTags {
    const val CARD = "second_game_card"
    const val TITLE = "second_game_title"
    const val SYSTEM = "second_game_system"
    const val TILES = "second_game_tiles"
    const val ACHIEVEMENTS = "second_game_achievements"
    const val ACHIEVEMENTS_FIGURE = "second_game_achievements_figure"
    const val POINTS = "second_game_points"
    const val METER = "second_game_meter"
    const val BADGE = "second_game_badge"
    const val LATEST = "second_game_latest"
    const val PLAY = "second_game_play"
    const val PLAY_TOTAL = "second_game_play_total"
    const val SESSIONS = "second_game_sessions"
    const val BARS = "second_game_bars"
    const val THIS_WEEK = "second_game_this_week"
    const val ABOUT = "second_game_about_session"
    const val DESCRIPTION = "second_game_description"
    const val DESCRIPTION_BOX = "second_game_description_box"
    const val BATTERY = "second_game_battery"
    const val FILE = "second_game_file"
}

/**
 * The game card (boards `SecondGame`, `SecondGamePaper`, `SecondGameNoRA`): the title and system, the achievements and play-time
 * tiles, the whole description (it takes the room left and fades out where cut), the battery line and the file name. The cover,
 * last played and total play time are on the top screen's column, not here. Parts that do not fit at the card's height are
 * dropped whole from the bottom (file, battery, description); the header and the tiles stay.
 */
@Composable
internal fun BoxScope.GameCard(content: SecondScreenContent.Game) {
    val colors = PlumTheme.colors
    val file = content.fileName?.takeIf { it.isNotBlank() && content.isPlayKnown }
    val description = content.details?.description?.trim()?.replace(WHITESPACE, " ").orEmpty()
    val descriptionStyle = gameStyle(TypeRole.SecondFactsRoomy, D.descriptionSize).copy(color = colors.text)
    // Text heights the description's layouts reported, by (characters, width, box height): a measure the layout node skips as unchanged reports nothing.
    val laidOut = remember(description, descriptionStyle) { HashMap<Triple<Int, Int, Int>, Int>() }
    // How many characters of the description to lay out (0: the first guess); raised, for a next measure, when a layout shows too little.
    val limitState = remember(description, descriptionStyle) { mutableIntStateOf(0) }
    // The foot parts the last measure left out at this size: they are not composed again (a dropped part leaves no node, and one composition is enough for a kept one).
    val goneState = remember(content, PlumTheme.type) { mutableStateOf(GoneParts(null, emptySet())) }
    // Read here, not in the measure: a write then recomposes the card, which the layout needs to measure again.
    val goneBefore = goneState.value
    val batteryStyle = gameStyle(TypeRole.SecondFactsRoomy, D.footSize).copy(color = colors.text2).onBoardLine(EntryCardDimens.JOST_OWN_LINE, isJost())
    // Keyed by the type settings: the parts are measured inside the layout's own measure pass, which a font change in a part alone does not rerun.
    key(PlumTheme.type) {
    SubcomposeLayout(
        Modifier.align(Alignment.TopStart).fillMaxSize().padding(start = D.marginSide, top = D.marginTop, end = D.marginSide, bottom = D.marginBottom).testTag(GameCardTags.CARD),
    ) { constraints ->
        CardLayoutCount.note("cardMeasures")
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val loose = Constraints(maxWidth = width)
        val header = cardTrace("SS:header") { subcompose("header") { Header(content) }.map { it.measure(loose) } }
        val tiles = cardTrace("SS:tiles") { subcompose("tiles") { Tiles(content) }.map { it.measure(loose) } }
        val hasBattery = content.batteryPace != null && content.isPlayKnown
        val sizeKey = Pair(width, height)
        val gone = goneBefore.let { if (it.key == sizeKey) it.parts else emptySet() }
        // Composed once and measured. A part that does not fit stays unplaced for this measure and is not composed in the next (see [GoneParts]).
        val batteryParts = if (hasBattery && BATTERY !in gone) subcompose("battery") { Battery(content, batteryStyle) }.map { it.measure(loose) } else emptyList()
        val fileParts = if (file != null && FILE !in gone) subcompose("file") { FileLine(file) }.map { it.measure(loose) } else emptyList()
        val headerH = header.sumOf { it.height }
        val tilesH = tiles.sumOf { it.height }
        val batteryH = batteryParts.sumOf { it.height }
        val fileH = fileParts.sumOf { it.height }
        val gapHeader = D.headerGap.roundToPx()
        val gapTiles = D.tilesBottomGap.roundToPx()
        val footTop = D.footTop.roundToPx()
        val footGap = D.footGap.roundToPx()
        val upToTiles = headerH + gapHeader + tilesH
        // The text layout rounds a line up to a whole pixel; one line is that tall.
        val oneLine = if (description.isEmpty()) 0 else ceil(descriptionStyle.lineHeight.toPx()).toInt()

        fun foot(withBattery: Boolean, withFile: Boolean): Int {
            val b = if (withBattery) batteryH else 0
            val f = if (withFile) fileH else 0
            return if (b + f == 0) 0 else footTop + b + (if (b > 0 && f > 0) footGap else 0) + f
        }
        // The whole foot first, then without the file, then without the battery too; each must leave the description one line.
        val keep =
            listOf(true to true, true to false, false to false).firstOrNull { (b, f) ->
                upToTiles + (if (description.isEmpty() && b == false) 0 else gapTiles) + oneLine + foot(b, f) <= height
            } ?: (false to false)
        val showBattery = keep.first && batteryH > 0
        val showFile = keep.second && fileH > 0
        val footH = foot(showBattery, showFile)
        val leftOut = buildSet { if (!showBattery && batteryParts.isNotEmpty()) add(BATTERY); if (!showFile && fileParts.isNotEmpty()) add(FILE) }
        if (leftOut.isNotEmpty()) goneState.value = GoneParts(sizeKey, gone + leftOut)
        val descriptionTop = upToTiles + gapTiles
        // The description's box starts this far above its first line, so the tallest marks are not cut by its clip.
        val inkTop = D.descriptionInkTop.roundToPx()
        // Whole lines only: a part of the next line under the clip would show as a faded sliver above the file name.
        // The text layout rounds a line up to a whole pixel.
        val lineH = ceil(descriptionStyle.lineHeight.toPx())
        val descriptionRoom = height - footH - descriptionTop
        val descriptionH = if (lineH > 0f && description.isNotEmpty()) (descriptionRoom / lineH).toInt() * lineH.toInt() else descriptionRoom
        val descriptionParts =
            if (description.isNotEmpty() && descriptionH >= oneLine) {
                cardTrace("SS:description") {
                    val boxH = descriptionH + inkTop
                    val limit = maxOf(limitState.intValue, descriptionLimit(description, width, descriptionStyle.fontSize.toPx(), ceil((boxH + lineH) / lineH).toInt() + 2))
                    val shown = descriptionCut(description, limit)
                    val key = Triple(shown.length, width, boxH)
                    val parts = subcompose("description") { Description(shown, description, descriptionStyle) { laidOut[key] = it } }.map { it.measure(Constraints.fixed(width, boxH)) }
                    // Enough when the layout runs a whole line past the room (the fade and the shown lines then match the whole text's), or is the whole text.
                    // If not, the next measure takes twice the text: that one frame shows fewer lines than it should.
                    if (shown.length < description.length && (laidOut[key] ?: -1) <= boxH + lineH) limitState.intValue = limit * 2
                    parts
                }
            } else {
                emptyList()
            }
        layout(width, height) {
            header.forEach { it.placeRelative(0, 0) }
            tiles.forEach { it.placeRelative(0, headerH + gapHeader) }
            descriptionParts.forEach { it.placeRelative(0, descriptionTop - inkTop) }
            var y = height - footH + (if (footH > 0) footTop else 0)
            if (showBattery) {
                batteryParts.forEach { it.placeRelative(0, y) }
                y += batteryH + footGap
            }
            if (showFile) fileParts.forEach { it.placeRelative(0, y) }
        }
    }
    }
}

@Composable
private fun Battery(
    content: SecondScreenContent.Game,
    style: TextStyle,
) {
    val pace = content.batteryPace ?: return
    CardLayoutCount.note("battery")
    PickingBatteryLine(pace, style, Modifier.testTag(GameCardTags.BATTERY).boardLine(style, isJost()), withPace = false)
}

/**
 * The battery and file lines a measure of the card left out, for the card's [key] (its size). A kept part is composed once;
 * a part that did not fit is measured, left unplaced for that one measure (it would stay in the semantics tree), and not composed again.
 */
private class GoneParts(val key: Any?, val parts: Set<String>)

private const val BATTERY = "battery"
private const val FILE = "file"

private val WHITESPACE = Regex("\\s+")

/** [role]'s shared style at [size] (the font's own line, scaled with it, so no mark is cut); the font and text size settings still scale it. */
@Composable
internal fun gameStyle(
    role: TypeRole,
    size: TextUnit,
    weight: Int = role.weight,
): TextStyle {
    val type = PlumTheme.type
    val base = type[role]
    val k = size.value / role.sizeSp
    return base.copy(
        fontSize = base.fontSize * k,
        lineHeight = base.lineHeight * k,
        fontWeight = FontWeight(role.weightFor(type.font, type.textMode) + weight - role.weight),
        // Glyphs keep their fractional advances, as the board's browser lays them; Static rounds each to a pixel, which over the
        // description's 735 px line is some 13 px wider, so a word the board keeps goes to the next line.
        textMotion = TextMotion.Animated,
        // Figures of one width, as the boards set them (tabular-nums).
        fontFeatureSettings = TABULAR_FIGURES,
    )
}

@Composable
private fun Header(content: SecondScreenContent.Game) = HeaderLine(content.title, content.isFavorite, content.systemShort.ifEmpty { content.systemName })

/** The game title (one line, its star kept) with a small tag at its right on the title's baseline: the system on a game card, "APP" on an app's. */
@Composable
internal fun HeaderLine(
    title: String,
    isFavorite: Boolean,
    tag: String,
) {
    val colors = PlumTheme.colors
    val titleStyle = gameStyle(TypeRole.SecondTitleRoomy, D.titleSize).copy(color = colors.strong)
    val system = gameStyle(TypeRole.SecondFactsRoomy, D.systemSize).copy(color = colors.meta)
    val lift = with(LocalDensity.current) { ((titleStyle.fontSize.value - system.fontSize.value) * BASELINE_DESCENT_EM).sp.toDp() }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(D.headerItemGap)) {
        Box(Modifier.weight(1f)) {
            SecondScreenTitle(title, isFavorite, titleStyle, gameStyle(TypeRole.SecondStarRoomy, D.systemSize), colors.meta, maxLines = 1, modifier = Modifier.testTag(GameCardTags.TITLE))
        }
        BasicText(
            tag,
            style = system,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = lift).testTag(GameCardTags.SYSTEM),
        )
    }
}

/** How much of a font size a line's descent takes, to put two sizes on one baseline when they share a bottom edge. */
private const val BASELINE_DESCENT_EM = 0.375f

/**
 * How many characters of [text] to lay out first: a layout of the whole synopsis (1,000+ characters) costs ~20 ms on the Thor's main
 * thread and only [lines] lines of the room show. About [AVERAGE_ADVANCE_EM] of the font size per character; too few is found when the
 * layout is measured and doubled, so the shown lines never depend on this guess.
 */
internal fun descriptionLimit(text: String, widthPx: Int, fontSizePx: Float, lines: Int): Int =
    if (fontSizePx <= 0f) text.length else ceil(lines * widthPx / (AVERAGE_ADVANCE_EM * fontSizePx)).toInt().coerceAtLeast(1)

private const val AVERAGE_ADVANCE_EM = 0.44f

/** [text] up to [limit] characters, cut at a word end (a space; none in the first half of it, cut at the limit), no pair of a surrogate split. */
internal fun descriptionCut(text: String, limit: Int): String {
    if (limit >= text.length) return text
    val space = text.lastIndexOf(' ', limit)
    if (space > 0) return text.substring(0, space)
    return text.substring(0, if (text[limit - 1].isHighSurrogate()) limit - 1 else limit)
}

/** The description text given, in the room it is given (a cut of it, see [descriptionCut]); where it is cut the last part fades out (the board's mask over its last 18 %). */
@Composable
private fun Description(
    text: String,
    full: String,
    style: TextStyle,
    onLaidOut: (Int) -> Unit,
) {
    var boxHeight by remember { mutableIntStateOf(0) }
    var textHeight by remember { mutableIntStateOf(0) }
    val layout = remember { arrayOfNulls<TextLayoutResult>(1) }
    Box(
        Modifier
            .fillMaxSize()
            .testTag(GameCardTags.DESCRIPTION_BOX)
            .onSizeChanged { boxHeight = it.height }
            .clipToBounds()
            .graphicsLayer { compositingStrategy = if (textHeight > boxHeight) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
            .drawWithContent {
                drawContent()
                if (textHeight > boxHeight) {
                    val from = size.height * (1f - D.DESCRIPTION_FADE_SHARE)
                    drawRect(
                        Brush.verticalGradient(listOf(MaskOpaque, Color.Transparent), startY = from, endY = size.height),
                        topLeft = Offset(0f, from),
                        size = Size(size.width, size.height - from),
                        blendMode = BlendMode.DstIn,
                    )
                }
            },
    ) {
        BasicText(
            text,
            style = style,
            modifier = Modifier.padding(top = D.descriptionInkTop).wrapContentHeight(Alignment.Top, unbounded = true)
                // Only a start of the text is laid out; a screen reader still reads all of it.
                .clearAndSetSemantics {
                    testTag = GameCardTags.DESCRIPTION
                    this.text = AnnotatedString(full)
                    getTextLayoutResult { results -> layout[0]?.let { results.add(it) } != null }
                },
            onTextLayout = {
                layout[0] = it
                textHeight = it.size.height
                CardLayoutCount.note(CardLayoutCount.DESCRIPTION, text.length)
                CardLayoutCount.note(CardLayoutCount.DESCRIPTION_LAYOUTS)
                onLaidOut(it.size.height)
            },
        )
    }
}

/** The file name as stored, in the mono face (the board's file line). */
@Composable
internal fun FileLine(name: String, modifier: Modifier = Modifier) {
    CardLayoutCount.note("file")
    val type = PlumTheme.type
    val style =
        gameStyle(TypeRole.SecondFactsRoomy, D.fileSize).copy(
            color = PlumTheme.colors.meta,
            fontFamily = PlumFonts.familyOf(PlumFont.Mono),
            fontSize = (D.fileSize.value * PlumFont.Mono.sizeFactor * type.sizeFactor * type.physicalScale).sp,
            letterSpacing = D.fileSpacing,
        )
    val box = if (isJost()) Modifier.boardMonoLine(D.fileSize * type.sizeFactor * type.physicalScale) else Modifier
    BasicText(name, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier.testTag(GameCardTags.FILE).then(box))
}
