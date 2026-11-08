package com.plumemu.secondscreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.plumemu.R
import com.plumemu.coreui.components.badgeFrame
import com.plumemu.coreui.components.EllipsisText
import com.plumemu.coreui.components.glassTile
import com.plumemu.coreui.theme.EntryCardDimens
import com.plumemu.coreui.theme.GlassDimens
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.SecondScreenGameCardDimens as D
import com.plumemu.coreui.theme.TypeRole
import com.plumemu.game.LocalBadgeImages
import com.plumemu.graph.StoredBadge
import com.plumemu.home.playTime
import com.plumemu.home.rememberCountFormat
import kotlin.math.ceil

/* The game card's two tiles (achievements, play time) and their parts. */

@Composable
internal fun Tiles(content: SecondScreenContent.Game) {
    val stored = content.ra
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max).testTag(GameCardTags.TILES), horizontalArrangement = Arrangement.spacedBy(D.tileGap)) {
        if (stored != null) AchievementsTile(content, Modifier.weight(D.ACHIEVEMENTS_WEIGHT).fillMaxHeight())
        PlayTile(content, Modifier.weight(D.PLAY_WEIGHT).fillMaxHeight())
    }
}

@Composable
private fun Tile(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.glassTile().padding(GlassDimens.edge).padding(horizontal = D.tilePadX, vertical = D.tilePadY),
        verticalArrangement = Arrangement.spacedBy(D.tilePartGap),
        content = content,
    )
}

/** A label that does not fit its tile loses what follows its " · " ("PLAY TIME · LAST 8 WEEKS" becomes "PLAY TIME") instead of being cut mid-word. */
@Composable
private fun TileLabel(text: String) {
    val isJost = isJost()
    val style = gameStyle(TypeRole.SecondFactsRoomy, D.labelSize, weight = LABEL_WEIGHT).copy(color = PlumTheme.colors.meta, letterSpacing = D.labelSpacing).onBoardLine(EntryCardDimens.JOST_OWN_LINE, isJost)
    val full = text.uppercase()
    // The full text's natural width is learned when it is laid out; the short one shows while it does not fit the room.
    var natural by remember(full, style) { mutableIntStateOf(0) }
    var room by remember(full, style) { mutableIntStateOf(Int.MAX_VALUE) }
    val short = full.contains(LABEL_SEPARATOR) && natural > room
    BasicText(
        if (short) full.substringBefore(LABEL_SEPARATOR) else full,
        style = style,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { if (!short) natural = ceil(it.multiParagraph.intrinsics.maxIntrinsicWidth).toInt() },
        modifier = Modifier.fillMaxWidth().onSizeChanged { room = it.width }.boardLine(style, isJost),
    )
}

private const val LABEL_SEPARATOR = " · "

private const val LABEL_WEIGHT = 700

/** Whether the card's texts sit on the board's line boxes ([boardLine]). */
@Composable
internal fun isJost() = PlumTheme.type.font == PlumFont.Jost

@Composable
private fun small(color: Color) = gameStyle(TypeRole.SecondFactsRoomy, D.smallSize).copy(color = color).onBoardLine(EntryCardDimens.LINE_SMALL, isJost())

@Composable
private fun AchievementsTile(
    content: SecondScreenContent.Game,
    modifier: Modifier,
) {
    val colors = PlumTheme.colors
    val stored = content.ra ?: return
    val numbers = rememberCountFormat()
    val isJost = isJost()
    val big = gameStyle(TypeRole.SecondTitleRoomy, D.bigSize).copy(color = colors.strong).onBoardLine(EntryCardDimens.LINE_BIG, isJost)
    val unit = gameStyle(TypeRole.SettingsOptionFocused, D.unitSize).toSpanStyle().copy(color = colors.text2)
    Tile(modifier.testTag(GameCardTags.ACHIEVEMENTS)) {
        TileLabel(stringResource(R.string.second_game_achievements))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText(
                buildAnnotatedString {
                    append(numbers.format(stored.earned))
                    withStyle(unit) { append(" " + stringResource(R.string.second_game_of_total, numbers.format(stored.total))) }
                },
                style = big,
                maxLines = 1,
                modifier = Modifier.boardLine(big, isJost, sharing = unit.fontSize).alignByBaseline().testTag(GameCardTags.ACHIEVEMENTS_FIGURE),
            )
            stored.points?.let { (earned, total) ->
                BasicText(
                    stringResource(R.string.second_game_points, numbers.format(earned), numbers.format(total)),
                    style = small(colors.text2),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.boardLine(small(colors.text2), isJost).alignByBaseline().testTag(GameCardTags.POINTS),
                )
            }
        }
        Meter(stored.earned.toFloat() / stored.total.coerceAtLeast(1))
        if (stored.recent.isNotEmpty()) {
            Row(Modifier.padding(top = D.badgesTop), horizontalArrangement = Arrangement.spacedBy(D.badgeGap)) {
                stored.recent.take(D.BADGES).forEach { GameBadge(it) }
            }
            LatestLine(stored.recent.first(), content.raLatestDaysAgo)
        }
    }
}

@Composable
private fun Meter(share: Float) {
    val colors = PlumTheme.colors
    val shown = if (share > 0f) share.coerceIn(MIN_METER_SHARE, 1f) else 0f
    Box(Modifier.fillMaxWidth().height(D.meterHeight).testTag(GameCardTags.METER).drawBehind { drawMeter(colors, shown) })
}

/** An earned share too small to see still shows a sliver. */
private const val MIN_METER_SHARE = 0.02f

@Composable
private fun LatestLine(
    badge: StoredBadge,
    daysAgo: Int?,
) {
    val style = small(PlumTheme.colors.meta)
    val ago = daysAgo?.let { stringResource(R.string.second_screen_separator) + agoWords(it) }.orEmpty()
    Row(Modifier.fillMaxWidth().boardLine(style, isJost()).testTag(GameCardTags.LATEST)) {
        EllipsisText(stringResource(R.string.second_game_latest, badge.title), style = style, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
        if (ago.isNotEmpty()) BasicText(ago, style = style, maxLines = 1, softWrap = false)
    }
}

/** "today", "yesterday", "3 days ago": the words after a title (the system card's). */
@Composable
private fun agoWords(daysAgo: Int): String =
    when (daysAgo) {
        0 -> stringResource(R.string.second_screen_ago_today)
        1 -> stringResource(R.string.second_screen_ago_yesterday)
        else -> pluralStringResource(R.plurals.second_screen_ago_days, daysAgo, rememberCountFormat().format(daysAgo))
    }

/** One badge: its cached picture (the disk cache only, never the network), or the empty face while there is none. */
@Composable
private fun GameBadge(badge: StoredBadge) {
    val colors = PlumTheme.colors
    val images = LocalBadgeImages.current
    val sizePx = with(LocalDensity.current) { D.badgeSize.roundToPx() }
    val url = badge.url
    var bitmap: ImageBitmap? by remember(url, sizePx) { mutableStateOf(url?.let { images.cached(it, sizePx) }) }
    if (url != null && bitmap == null) LaunchedEffect(url, sizePx) { bitmap = images.loadCached(url, sizePx) }
    val shape = RoundedCornerShape(D.badgeCorner)
    val shown = bitmap
    Box(Modifier.size(D.badgeSize).badgeFrame(D.badgeCorner).clip(shape).testTag(GameCardTags.BADGE)) {
        if (shown != null) {
            Image(bitmap = shown, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(colors.sel).drawBehind { drawGhost(colors.muted, colors.sel) })
        }
    }
}

/** The board's empty badge: a pixel ghost (an 11 x 11 glyph) on the face. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGhost(
    ghost: Color,
    eyes: Color,
) {
    val cell = size.width * D.BADGE_GHOST_SHARE / GHOST_GRID
    val origin = Offset((size.width - cell * GHOST_GRID) / 2, (size.height - cell * GHOST_GRID) / 2)
    fun rect(x: Int, y: Int, w: Int, h: Int, color: Color) = drawRect(color, origin + Offset(x * cell, y * cell), Size(w * cell, h * cell))
    // Rows of the board's path "M4 1h3v1h1v2h1v3H8v2H7v1H4V9H3V7H2V4h1V2h1z".
    rect(4, 1, 3, 1, ghost)
    rect(3, 2, 5, 1, ghost)
    rect(3, 3, 5, 1, ghost)
    rect(2, 4, 7, 3, ghost)
    rect(3, 7, 5, 2, ghost)
    rect(4, 9, 3, 1, ghost)
    rect(4, 4, 1, 2, eyes)
    rect(6, 4, 1, 2, eyes)
}

private const val GHOST_GRID = 11

@Composable
private fun PlayTile(
    content: SecondScreenContent.Game,
    modifier: Modifier,
) {
    val colors = PlumTheme.colors
    val numbers = rememberCountFormat()
    val weeks = content.weeks?.takeIf { content.isPlayKnown }
    val isJost = isJost()
    val big = gameStyle(TypeRole.SecondTitleRoomy, D.bigSize).copy(color = colors.strong).onBoardLine(EntryCardDimens.LINE_BIG, isJost)
    val neverPlayed = content.lastPlayedDaysAgo == null
    Tile(modifier.testTag(GameCardTags.PLAY)) {
        TileLabel(stringResource(R.string.second_game_play_label))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            // Until the play facts are read the figures are blank text of the same style: the tile keeps its height.
            val total =
                when {
                    weeks == null -> ""
                    neverPlayed -> stringResource(R.string.second_screen_not_played)
                    else -> playTime((weeks.totalSeconds / SECONDS_PER_MINUTE).toInt())
                }
            BasicText(total, style = big, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).boardLine(big, isJost).alignByBaseline().testTag(GameCardTags.PLAY_TOTAL))
            if (weeks != null && weeks.sessions > 0) {
                BasicText(
                    pluralStringResource(R.plurals.second_game_sessions, weeks.sessions, numbers.format(weeks.sessions)),
                    style = small(colors.text2),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.boardLine(small(colors.text2), isJost).alignByBaseline().testTag(GameCardTags.SESSIONS),
                )
            }
        }
        WeekBars(weeks?.weekSeconds ?: List(D.WEEKS_SHOWN) { 0L })
        val about = weeks?.averageMinutes
        // An empty line of the same style when there is nothing to say, so the tile is as tall as with it.
        BasicText(if (about != null) stringResource(R.string.second_game_about_session, playTime(about)) else "", style = small(colors.meta), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.boardLine(small(colors.meta), isJost).testTag(GameCardTags.ABOUT))
    }
}

private const val SECONDS_PER_MINUTE = 60L

/** The last eight weeks as columns against the busiest one, the current week in the accent and labelled under it. */
@Composable
private fun WeekBars(seconds: List<Long>) {
    val colors = PlumTheme.colors
    val isJost = isJost()
    val label = gameStyle(TypeRole.SecondFactsRoomy, D.barsLabelSize).onBoardLine(EntryCardDimens.JOST_OWN_LINE, isJost)
    val most = seconds.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val last = seconds.lastIndex
    EvenColumns(D.barsGap, Modifier.testTag(GameCardTags.BARS)) {
        seconds.forEachIndexed { index, value ->
            val isNow = index == last
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(D.barsLabelGap)) {
                Box(Modifier.fillMaxWidth().height(D.barsHeight), contentAlignment = Alignment.BottomCenter) {
                    val barHeight = if (value <= 0) D.barEmpty else maxOf(D.barMin, D.barsHeight * (value.toFloat() / most))
                    val shape = RoundedCornerShape(D.barCornerTop, D.barCornerTop, D.barCornerBottom, D.barCornerBottom)
                    val bar = Modifier.fillMaxWidth(D.BAR_WIDTH_SHARE).height(barHeight)
                    if (isNow) {
                        Box(bar.latestBarFill(shape))
                    } else {
                        Box(bar.background(if (value > 0) colors.playedBar else colors.line, shape))
                    }
                }
                BasicText(
                    if (isNow) stringResource(R.string.second_game_this_week) else "",
                    style = label.copy(color = if (isNow) colors.text else colors.meta),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.wrapContentWidth(unbounded = true).boardLine(label, isJost),
                )
            }
        }
    }
}

