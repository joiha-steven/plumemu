package com.plumemu.secondscreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import com.plumemu.R
import com.plumemu.battery.BatteryPace
import com.plumemu.battery.PickingBatteryLine
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.SecondScreenDimens
import com.plumemu.home.ArtPicture
import com.plumemu.home.CoverImages
import com.plumemu.home.GameDetails
import com.plumemu.home.fitInside
import com.plumemu.home.ratingLabel
import com.plumemu.home.yearAndPublisher

/*
 * The details card: a game with a ScreenScraper
 * result shows its cover, facts and description on the second screen. Every part is one the top
 * screen's details column also shows, in its words (nothing is only on the second screen).
 */

/** The lines a game's details give the card; each empty when it has nothing to say (left out, the rest close up). */
internal class DetailLines(
    val yearAndPublisher: String,
    /** "Racing · 8/10", the genre cut by measuring so the rating stays; null: neither is known. */
    val genreAndRating: Facts?,
    val description: String,
) {
    val hasAny: Boolean get() = yearAndPublisher.isNotEmpty() || genreAndRating != null || description.isNotEmpty()
}

/** [details] in the details column's words ("1994 · Nintendo", "Racing · 8/10"); none for null. */
@Composable
internal fun detailLinesOf(details: GameDetails?): DetailLines {
    if (details == null) return DetailLines("", null, "")
    val rating = ratingLabel(details)
    val genreLine =
        when {
            details.genre.isEmpty() && rating.isEmpty() -> null
            // The top screen's "%1$s · %2$s", split so the genre can be cut and the rating kept.
            else -> cutFirstFacts(details.genre.ifEmpty { null }, AnnotatedString(rating), stringResource(R.string.second_screen_separator), Color.Unspecified)
        }
    return DetailLines(
        yearAndPublisher(details),
        genreLine,
        // Paragraph breaks would spend the few lines there are on blank space: one paragraph here.
        details.description.trim().replace(WHITESPACE, " "),
    )
}

private val WHITESPACE = Regex("\\s+")

/**
 * A picking card: for a game with any detail or a cover that can be read, the details card; for
 * every other card, and a game with neither, the picking block. One call site for the picking block
 * whatever the content, so moving between a game and a system recomposes it in place. A cover
 * whose file cannot be read ([unreadable]) leaves the card as it is without art (the text block on
 * the bottom margin, or the picking block when nothing else is known).
 */
@Composable
internal fun BoxScope.PickingCard(
    content: SecondScreenContent,
    dimens: SecondScreenDimens,
    images: CoverImages,
) {
    var unreadable by remember { mutableStateOf<ArtPicture?>(null) }
    val game = content as? SecondScreenContent.Game
    // Over a grid the top screen shows the cover: the card leaves it out, and nothing is decoded for it.
    val isCoverOnTop = game?.isCoverOnTopScreen == true
    val picture = game?.art?.takeIf { dimens.showsArt && it != unreadable && !isCoverOnTop }
    val lines = detailLinesOf(game?.details)
    val texts = if (game != null && (picture != null || lines.hasAny)) cardTexts(game, separatorColor = PlumTheme.colors.muted) else null
    // Not a detail: a file name alone never turns the picking card into the details card.
    val fileLine = game?.fileName?.takeIf { it.isNotBlank() }?.let(::fileNameWithoutExtension)
    when {
        texts != null && picture != null -> DetailsWithArt(texts, lines, fileLine, picture, dimens, images, game?.batteryPace, onUnreadable = { unreadable = picture })
        texts != null -> DetailsWithoutArt(texts, lines, fileLine, dimens, if (isCoverOnTop) dimens.descriptionMaxLinesBesideGrid else dimens.descriptionMaxLines, game?.batteryPace)
        else -> PickingBlock(content, dimens)
    }
}

/**
 * The cover top-left, fitted in the [SecondScreenDimens.artSlot] square at its own shape; beside it
 * the top line, title and fact lines; under both, full width, the description in as many of its
 * lines as fit; the file line ([fileLine], none when null) right above the played line on the
 * bottom margin.
 */
@Composable
private fun DetailsWithArt(
    texts: CardTexts,
    lines: DetailLines,
    fileLine: String?,
    picture: ArtPicture,
    dimens: SecondScreenDimens,
    images: CoverImages,
    pace: BatteryPace?,
    onUnreadable: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(dimens.margin).testTag(SecondScreenTags.DETAILS)) {
        Row(Modifier.fillMaxWidth()) {
            SecondScreenCover(picture, dimens.artSlot, images, onUnreadable)
            Spacer(Modifier.width(SecondScreenDimens.artToTextGap))
            Column(Modifier.weight(1f)) { HeadLines(texts, lines, dimens) }
        }
        DescriptionAndFileLine(lines.description, fileLine, dimens, descriptionGap = SecondScreenDimens.artToDescriptionGap, fill = true)
        PlayedLine(texts.facts, dimens, topGap = playedLineGap(fileLine))
        BatteryLine(pace, texts.facts, dimens)
    }
}

/**
 * No cover: the picking card's place, one block on the bottom margin, full width, with the fact
 * lines, the description (at most [descriptionMaxLines], fewer when they do not fit) and the file
 * line added above the played line.
 */
@Composable
private fun DetailsWithoutArt(
    texts: CardTexts,
    lines: DetailLines,
    fileLine: String?,
    dimens: SecondScreenDimens,
    descriptionMaxLines: Int,
    pace: BatteryPace?,
) {
    Column(Modifier.fillMaxSize().padding(dimens.margin).testTag(SecondScreenTags.DETAILS), verticalArrangement = Arrangement.Bottom) {
        HeadLines(texts, lines, dimens)
        DescriptionAndFileLine(lines.description, fileLine, dimens, descriptionGap = SecondScreenDimens.descriptionGap, fill = false, descriptionMaxLines = descriptionMaxLines)
        PlayedLine(texts.facts, dimens, topGap = playedLineGap(fileLine))
        BatteryLine(pace, texts.facts, dimens)
    }
}

/** The top line, the title (its star kept, cut before it) and "1994 · Nintendo", "Racing · 8/10". */
@Composable
private fun ColumnScope.HeadLines(
    texts: CardTexts,
    lines: DetailLines,
    dimens: SecondScreenDimens,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    TopLine(texts.top, type[SecondScreenDimens.detailsTopRole].copy(color = colors.meta))
    Spacer(Modifier.height(SecondScreenDimens.topToTitleGap))
    SecondScreenTitle(
        title = texts.title,
        isFavorite = texts.isFavorite,
        titleStyle = type[dimens.detailsTitleRole].copy(color = colors.text),
        starStyle = type[dimens.starRole],
        starColor = colors.meta,
        maxLines = dimens.detailsTitleMaxLines,
        modifier = Modifier.testTag(SecondScreenTags.TITLE),
    )
    val factStyle = type[SecondScreenDimens.detailsFactRole].copy(color = colors.text2)
    if (lines.yearAndPublisher.isNotEmpty()) {
        Spacer(Modifier.height(SecondScreenDimens.titleToFactLinesGap))
        BasicText(lines.yearAndPublisher, style = factStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag(SecondScreenTags.FACT_LINE))
    }
    lines.genreAndRating?.let { genre ->
        Spacer(Modifier.height(if (lines.yearAndPublisher.isNotEmpty()) SecondScreenDimens.factLineGap else SecondScreenDimens.titleToFactLinesGap))
        FactsLine(genre, factStyle, tag = SecondScreenTags.FACT_LINE)
    }
}

/**
 * The description, [topGap] under what is above it, in at most [maxLines] lines
 * ([SecondScreenDimens.descriptionMaxLines]; more beside a grid) and never more than the height left fits (at the largest text sizes fewer lines; none when
 * not one fits), the last one cut with "…".
 */
@Composable
private fun Description(
    text: String,
    maxLines: Int,
    topGap: Dp,
) {
    val style = PlumTheme.type.secondDescription.copy(color = PlumTheme.colors.text2)
    val density = LocalDensity.current
    BoxWithConstraints {
        val linePx = with(density) { style.lineHeight.toPx() }
        val room = constraints.maxHeight - with(density) { topGap.roundToPx() }
        // A pixel short of a whole line: the laid-out text rounds its height up.
        val fitting = if (constraints.hasBoundedHeight) ((room - 1) / linePx).toInt() else maxLines
        val shown = minOf(maxLines, fitting)
        if (shown >= 1) {
            BasicText(
                text = text,
                style = style,
                maxLines = shown,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = topGap).testTag(SecondScreenTags.DESCRIPTION),
            )
        }
    }
}

/**
 * The flexible part between the head lines and the played line: the description ([descriptionGap]
 * under what is above, in the lines that fit) and under it, right above the played line, the file
 * line ([fileLine]; nothing, not even its gap, when null). [fill]: the part takes all the height
 * left (beside a cover, the description from the top and the file line at the bottom), else only
 * what it needs (the block on the bottom margin).
 *
 * The description's lines give way first. The file line itself gives way only to the description's
 * last line: when both do not fit (at Largest: on Compact, and in Plum Serif on Roomy) the
 * file line is left out and the description keeps its lines, so the card never loses its
 * description to it; with no room for a description line anyway, the file line shows if it fits.
 * The head lines and the played line are never pushed; the played line then keeps its usual 16
 * above it.
 */
@Composable
private fun ColumnScope.DescriptionAndFileLine(
    description: String,
    fileLine: String?,
    dimens: SecondScreenDimens,
    descriptionGap: Dp,
    fill: Boolean,
    descriptionMaxLines: Int = dimens.descriptionMaxLines,
) {
    if (fileLine == null) {
        // As before the file line existed, to the pixel.
        if (fill) {
            Box(Modifier.weight(1f).fillMaxWidth()) { if (description.isNotEmpty()) Description(description, descriptionMaxLines, topGap = descriptionGap) }
        } else if (description.isNotEmpty()) {
            Box(Modifier.weight(1f, fill = false).fillMaxWidth()) { Description(description, descriptionMaxLines, topGap = descriptionGap) }
        }
        return
    }
    val fileStyle = PlumTheme.type[SecondScreenDimens.detailsFactRole].copy(color = PlumTheme.colors.muted)
    val descriptionLine = PlumTheme.type.secondDescription.lineHeight
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.weight(1f, fill = fill).fillMaxWidth()) {
        val fileHeight = measurer.measure(fileLine, fileStyle, overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = constraints.maxWidth)).size.height
        val fileBlock = with(density) { SecondScreenDimens.descriptionGap.roundToPx() } + fileHeight
        // What one description line needs, as Description counts it (its gap, a line and the pixel it rounds up).
        val oneDescriptionLine = if (description.isEmpty()) 0f else with(density) { descriptionGap.roundToPx() + descriptionLine.toPx() } + 1
        // Left out only when that is what keeps the description's last line: with no room for even
        // one description line anyway, the file line shows if it fits by itself.
        val room = constraints.maxHeight
        val restOfGap = with(density) { (SecondScreenDimens.descriptionGap - SecondScreenDimens.factLineGap).roundToPx() }
        val descriptionFitsWithFile = room - fileBlock >= oneDescriptionLine
        val descriptionFitsAlone = room - restOfGap >= oneDescriptionLine
        val showsFile = !constraints.hasBoundedHeight || descriptionFitsWithFile || (!descriptionFitsAlone && room >= fileBlock)
        Column(if (fill) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
            if (description.isNotEmpty()) {
                Box(Modifier.weight(1f, fill = fill).fillMaxWidth()) { Description(description, descriptionMaxLines, topGap = descriptionGap) }
            } else if (fill) {
                Spacer(Modifier.weight(1f))
            }
            if (showsFile) {
                Spacer(Modifier.height(SecondScreenDimens.descriptionGap))
                BasicText(fileLine, style = fileStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag(SecondScreenTags.FILE_NAME))
            } else {
                // The played line sits the fact lines' gap under this part: the rest of its usual 16.
                Spacer(Modifier.height(SecondScreenDimens.descriptionGap - SecondScreenDimens.factLineGap))
            }
        }
    }
}

/** Above the played line: the fact lines' gap under the file line (or the rest of 16 in its part when it gave way), else the description's. */
private fun playedLineGap(fileLine: String?): Dp = if (fileLine != null) SecondScreenDimens.factLineGap else SecondScreenDimens.descriptionGap

/**
 * "Played yesterday · 3 h 20 min" / "Not played yet" on the bottom margin, [topGap] under what is
 * above. Its place is kept while the play facts are being read (no line until they are known), so
 * the description above does not move when they arrive.
 */
@Composable
private fun PlayedLine(
    facts: Facts?,
    dimens: SecondScreenDimens,
    topGap: Dp,
) {
    val style = PlumTheme.type[dimens.factsRole].copy(color = PlumTheme.colors.text2)
    Spacer(Modifier.height(topGap))
    if (facts != null) {
        FactsLine(facts, style)
    } else {
        // An empty text of the same style: exactly the line's height, to the pixel, and nothing to read.
        BasicText("", style = style, modifier = Modifier.clearAndSetSemantics {})
    }
}

/**
 * The battery line right under the played line, [SecondScreenDimens.factLineGap] below
 * it, in the same fact role; none for a game without a usual pace, and none until the play facts
 * are known (the line arrives with them).
 */
@Composable
internal fun BatteryLine(
    pace: BatteryPace?,
    playedFacts: Facts?,
    dimens: SecondScreenDimens,
) {
    if (pace == null || playedFacts == null) return
    PickingBatteryLine(pace, PlumTheme.type[dimens.factsRole].copy(color = PlumTheme.colors.text2), topGap = SecondScreenDimens.factLineGap)
}

/**
 * The cover in its real shape (aspect kept, never cropped or stretched), fitted in a square of
 * [slot]: a portrait box takes its height, a landscape one its width. Its place is reserved at the
 * picture's shape from the start; the pixels are drawn when decoded, at once (no fade on the
 * second screen) and off the main thread; the text is never waiting for them. [onUnreadable] when
 * the file cannot be read.
 */
@Composable
private fun SecondScreenCover(
    picture: ArtPicture,
    slot: Dp,
    images: CoverImages,
    onUnreadable: () -> Unit,
) {
    val density = LocalDensity.current
    val size = fitInside(picture.aspect, DpSize(slot, slot))
    val widthPx = with(density) { size.width.roundToPx() }.coerceAtLeast(1)
    val heightPx = with(density) { size.height.roundToPx() }.coerceAtLeast(1)
    val cached = remember(images, picture, widthPx, heightPx) { images.cached(picture, widthPx, heightPx) }
    var bitmap by remember(images, picture, widthPx, heightPx) { mutableStateOf(cached) }
    if (cached == null) {
        LaunchedEffect(images, picture, widthPx, heightPx) {
            // Null: the file is gone or damaged (CoilCoverImages logs why); the card goes on without art.
            val loaded = images.load(picture, widthPx, heightPx)
            if (loaded == null) onUnreadable() else bitmap = loaded
        }
    }
    val corner = if (picture.aspect >= 1f) PlumDimens.cornerCoverLandscape else PlumDimens.cornerCoverPortrait
    Box(Modifier.size(size).clip(RoundedCornerShape(corner)).testTag(SecondScreenTags.COVER)) {
        bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.Low, modifier = Modifier.fillMaxSize()) }
    }
}
