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
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.plumemu.battery.PickingBatteryLine
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
    const val ABOUT = "second_game_about_session"
    const val DESCRIPTION = "second_game_description"
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
    val batteryStyle = gameStyle(TypeRole.SecondFactsRoomy, D.footSize).copy(color = colors.text2).onBoardLine(EntryCardDimens.JOST_OWN_LINE, isJost())
    // Keyed by the type settings: the parts are measured inside the layout's own measure pass, which a font change in a part alone does not rerun.
    key(PlumTheme.type) {
    SubcomposeLayout(
        Modifier.align(Alignment.TopStart).fillMaxSize().padding(start = D.marginSide, top = D.marginTop, end = D.marginSide, bottom = D.marginBottom).testTag(GameCardTags.CARD),
    ) { constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val loose = Constraints(maxWidth = width)
        val header = cardTrace("SS:header") { subcompose("header") { Header(content) }.map { it.measure(loose) } }
        val tiles = cardTrace("SS:tiles") { subcompose("tiles") { Tiles(content) }.map { it.measure(loose) } }
        val hasBattery = content.batteryPace != null && content.isPlayKnown
        // Probes (no semantics, never placed) give the heights; only a part that is kept is composed for real, so a dropped one leaves no node.
        val batteryProbe = cardTrace("SS:probes") { if (hasBattery) subcompose("battery_probe") { Box(Modifier.clearAndSetSemantics {}) { Battery(content, batteryStyle) } }.map { it.measure(loose) } else emptyList() }
        val fileProbe = cardTrace("SS:probes") { if (file != null) subcompose("file_probe") { Box(Modifier.clearAndSetSemantics {}) { FileLine(file) } }.map { it.measure(loose) } else emptyList() }
        val headerH = header.sumOf { it.height }
        val tilesH = tiles.sumOf { it.height }
        val batteryH = batteryProbe.sumOf { it.height }
        val fileH = fileProbe.sumOf { it.height }
        val gapHeader = D.headerGap.roundToPx()
        val gapTiles = D.tilesBottomGap.roundToPx()
        val footTop = D.footTop.roundToPx()
        val footGap = D.footGap.roundToPx()
        val upToTiles = headerH + gapHeader + tilesH
        val oneLine = if (description.isEmpty()) 0 else descriptionStyle.lineHeight.toPx().toInt() + 1

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
        val battery = if (showBattery) subcompose("battery") { Battery(content, batteryStyle) }.map { it.measure(loose) } else emptyList()
        val fileLine = if (showFile && file != null) subcompose("file") { FileLine(file) }.map { it.measure(loose) } else emptyList()
        val descriptionTop = upToTiles + gapTiles
        // The description's box starts this far above its first line, so the tallest marks are not cut by its clip.
        val inkTop = D.descriptionInkTop.roundToPx()
        val descriptionH = height - footH - descriptionTop
        val descriptionParts =
            if (description.isNotEmpty() && descriptionH >= oneLine) {
                cardTrace("SS:description") { subcompose("description") { Description(description, descriptionStyle) }.map { it.measure(Constraints.fixed(width, descriptionH + inkTop)) } }
            } else {
                emptyList()
            }
        layout(width, height) {
            header.forEach { it.placeRelative(0, 0) }
            tiles.forEach { it.placeRelative(0, headerH + gapHeader) }
            descriptionParts.forEach { it.placeRelative(0, descriptionTop - inkTop) }
            var y = height - footH + (if (footH > 0) footTop else 0)
            if (showBattery) {
                battery.forEach { it.placeRelative(0, y) }
                y += batteryH + footGap
            }
            if (showFile) fileLine.forEach { it.placeRelative(0, y) }
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
    PickingBatteryLine(pace, style, Modifier.testTag(GameCardTags.BATTERY).boardLine(style, isJost()), withPace = false)
}

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
        fontWeight = FontWeight(role.weightFor(type.font, type.textMode, type.textWeight) + weight - role.weight),
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

/** The whole description in the room it is given; where it is cut the last part fades out (the board's mask over its last 18 %). */
@Composable
private fun Description(
    text: String,
    style: TextStyle,
) {
    var boxHeight by remember { mutableIntStateOf(0) }
    var textHeight by remember { mutableIntStateOf(0) }
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { boxHeight = it.height }
            .clipToBounds()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
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
            modifier = Modifier.padding(top = D.descriptionInkTop).wrapContentHeight(Alignment.Top, unbounded = true).testTag(GameCardTags.DESCRIPTION),
            onTextLayout = { textHeight = it.size.height },
        )
    }
}

/** The file name as stored, in the mono face (the board's file line). */
@Composable
internal fun FileLine(name: String, modifier: Modifier = Modifier) {
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
