package com.plumemu.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.plumemu.R
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.components.collar
import com.plumemu.coreui.theme.CollarDimens
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme

/**
 * The details column on the Collections folders page: the card of the focused folder, as the second screen's folder card says it
 * (nothing lives only on the second screen). Up to [MOSAIC_GAMES] of its box fronts (posters where a game has none), its name,
 * "11 games · 6 systems", what a built-in folder holds in one line, its systems with their counts, then the first game titles in the
 * height left. A folder with no game says why and how it fills, never a bare 0; New collection… says what a collection is. The text
 * comes first ([WideDetailsLayout] `minCover`): the mosaic takes the height left and leaves under [PlumDimens.posterMinHeight].
 */
@Composable
internal fun FolderDetails(
    content: HomeContent,
    row: GameRowModel,
    textHidden: Boolean,
    modifier: Modifier,
    width: Dp,
) {
    val kind = folderKindOf(row.id) ?: return
    val folder = content.folderEntry(row.id)
    val games = folder?.rows.orEmpty()
    val shown = remember(games) { games.take(MOSAIC_GAMES) }
    PrefetchMosaicArt(shown)
    WideDetailsLayout(
        cover = { if ((!textHidden || detailsCoverFitsWithSystemsOpen(PlumTheme.layout, content.hasBoxArt))) FolderMosaic(folder, shown, width) },
        titleBlock = {
            if (!textHidden) {
                TitleBlock(PlumDimens.titleLineGap) {
                    DetailsTitle(folderTitle(row.id, folder?.name ?: row.title), maxLines = 2)
                    if (kind != FolderKind.New) FactLine(countsLine(games), Modifier.testTag(FolderTags.COUNTS), maxLines = 1)
                }
            }
        },
        rest = {
            if (!textHidden) {
                // New collection…'s line is the whole card: it may take the lines it needs.
                folderAbout(kind)?.let { FactLine(it, Modifier.testTag(FolderTags.ABOUT), maxLines = if (kind == FolderKind.New) NEW_ABOUT_LINES else 2) }
                if (games.isEmpty() && kind != FolderKind.New) {
                    FactLine(folderEmptyText(kind), Modifier.testTag(FolderTags.EMPTY))
                } else if (games.isNotEmpty()) {
                    FolderSystems(games)
                }
            }
        },
        tail = { if (!textHidden && games.isNotEmpty()) WholeLines(games.take(TITLES_CARRIED).map { it.title }) },
        modifier = modifier.testTag(FolderTags.DETAILS),
        minCover = PlumDimens.posterMinHeight,
        coverHugs = true,
    )
}

/** "11 games · 6 systems". */
@Composable
private fun countsLine(games: List<GameRowModel>): String {
    val numbers = rememberCountFormat()
    val systems = remember(games) { games.mapTo(HashSet()) { it.systemId }.size }
    val gameText = pluralStringResource(R.plurals.home_games_count, games.size, numbers.format(games.size))
    if (games.isEmpty()) return gameText
    return stringResource(R.string.home_details_pair, gameText, pluralStringResource(R.plurals.home_systems_count, systems, numbers.format(systems)))
}

/** "SNES 6 · GBA 3 · N64 2": the folder's systems, most games first, two lines at most. */
@Composable
private fun FolderSystems(games: List<GameRowModel>) {
    val numbers = rememberCountFormat()
    val separator = stringResource(R.string.second_screen_separator)
    val items = remember(games, numbers) { systemCountsOf(games).map { (name, count) -> systemItem(name, numbers.format(count)) } }
    if (items.isEmpty()) return
    val style = PlumTheme.type.meta.copy(color = PlumTheme.colors.meta)
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.testTag(FolderTags.SYSTEMS)) {
        val widthPx = constraints.maxWidth
        // Whole systems only: what does not fit in two lines is dropped item by item, never cut inside one.
        val text = remember(items, separator, style, widthPx) {
            systemsLineThatFits(items, separator) { candidate ->
                !measurer.measure(candidate, style, overflow = TextOverflow.Clip, maxLines = SYSTEM_LINES, constraints = Constraints(maxWidth = widthPx)).didOverflowHeight
            }
        }
        FactLine(text, maxLines = SYSTEM_LINES)
    }
}

/** The systems line's lines. */
private const val SYSTEM_LINES = 2

/** Each system of [games] by its short name with its number of games, most first (ties in the order they first appear). */
internal fun systemCountsOf(games: List<GameRowModel>): List<Pair<String, Int>> {
    val counts = LinkedHashMap<String, Int>()
    for (game in games) {
        val name = game.systemAbbr.ifEmpty { game.systemId }
        if (name.isEmpty()) continue
        counts[name] = (counts[name] ?: 0) + 1
    }
    return counts.entries.sortedByDescending { it.value }.map { it.key to it.value }
}

/**
 * The folder's first box fronts in the height [WideDetailsLayout] gives, as ONE row justified like the home grid's mixed shapes
 * ([mosaicRow]): as many as keep the pictures tall enough, all one height with one gap, the row ending exactly at the column's edges;
 * only when two cannot fit, one cover left-aligned. The row stands at the top of the column and the text follows directly under it. Each
 * cover is the details column's collared cover ([CollaredDetailsCover]), so a poster stands in for a game without a picture. A folder
 * with no game (and New collection...) shows empty frames in their place: the card keeps its shape and its title its place.
 */
@Composable
private fun FolderMosaic(
    folder: HomeEntry?,
    shown: List<GameRowModel>,
    width: Dp,
) {
    val art = LocalHomeArt.current
    val games: List<GameRowModel?> = if (folder == null || shown.isEmpty()) List(MOSAIC_GAMES) { null } else shown
    BoxWithConstraints(Modifier.testTag(FolderTags.MOSAIC)) {
        // The facts table's column gap: the cells read as one block with the text under them.
        val gap = PlumDimens.factsColumnGap
        val blockWidth = minOf(maxWidth, width)
        // The shape each box is drawn at: the grid's order (`coverBoxAspect`); an app's icon is square; no shape known yet, the empty frame's.
        val aspects =
            games.map { game ->
                val slot = if (game == null || folder == null) null else art.slot(game.scrapeKey)
                when {
                    game == null || folder == null || slot == null -> EMPTY_FRAME_ASPECT
                    game.appPackage != null -> 1f
                    else -> {
                        val posterAspect = folder.shapeOf(game).widthOverHeight
                        val picture = (slot as? ArtSlot.Found)?.art?.detailsPicture()
                        coverBoxAspect(slot, picture, game.artAspect, posterAspect) ?: posterAspect
                    }
                }
            }
        val chromeX = CollarSize.Cover.sidePadding.value * 2
        val chromeY = (CollarSize.Cover.topPadding + CollarSize.Cover.bottomPadding).value
        val row = mosaicRow(aspects, blockWidth.value, maxHeight.value, gap.value, chromeX, chromeY)
        Layout(
            content = {
                row?.boxes?.forEachIndexed { i, box ->
                    val game = games[i]
                    val cell = DpSize(box.width.dp, row.height.dp)
                    if (game == null || folder == null) {
                        EmptyFrame(cell, Alignment.Center)
                    } else {
                        CollaredDetailsCover(art.slot(game.scrapeKey), cell, folder.posterOf(game), folder.shapeOf(game).widthOverHeight, game.appPackage, game.artAspect, Alignment.Center)
                    }
                }
            },
        ) { measurables, constraints ->
            val h = row?.height?.dp?.roundToPx() ?: 0
            val placed =
                row?.boxes?.mapIndexed { i, box ->
                    val left = box.x.dp.roundToPx()
                    // The last box ends exactly at the column's edge, whatever the rounding left.
                    val right = (box.x + box.width).dp.roundToPx()
                    measurables[i].measure(Constraints.fixed((right - left).coerceAtLeast(0), h)) to left
                }.orEmpty()
            layout(constraints.maxWidth, h) { placed.forEach { (p, x) -> p.place(x, 0) } }
        }
    }
}

/** An empty place in the mosaic: the cover's collar around nothing, so the page shows through (no fill to stand out on a light set). */
@Composable
private fun EmptyFrame(
    cell: DpSize,
    alignment: Alignment,
) {
    val inner = coverInner(cell)
    val box = fitInside(EMPTY_FRAME_ASPECT, inner)
    val whole = DpSize(box.width + CollarSize.Cover.sidePadding * 2, box.height + CollarSize.Cover.topPadding + CollarSize.Cover.bottomPadding)
    Box(Modifier.size(cell).testTag(FolderTags.EMPTY_FRAME), contentAlignment = alignment) {
        Box(Modifier.shrinkInto(whole).testTag(HomeTags.DETAILS_COVER_WHOLE)) {
            Box(Modifier.collar(CollarSize.Cover, CollarDimens.coverBoxCorner, halo = false) { 0f }) { Spacer(Modifier.size(box)) }
        }
    }
}

/** Reads the mosaic's pictures once after the focus rests (the folders page follows no game's art of its own). */
@Composable
private fun PrefetchMosaicArt(games: List<GameRowModel>) {
    val art = LocalHomeArt.current
    if (!art.isLive) return
    LaunchedEffect(art, games) {
        awaitDetailsSettle()
        art.prefetchSlots(games.mapNotNull { it.scrapeKey })
    }
}

/** The first game titles, one line each, as many whole lines as the height leaves (never a line cut in half). */
@Composable
private fun WholeLines(titles: List<String>) {
    val style = PlumTheme.type.meta.copy(color = PlumTheme.colors.text2)
    Layout(
        content = { titles.forEach { BasicText(it, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag(FolderTags.TITLE_LINE)) } },
        modifier = Modifier.testTag(FolderTags.TITLES),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val gap = PlumDimens.titleLineGap.roundToPx()
        // The screen's bottom fade lies over the strip's last band: a line there would read as cut, so none is placed in it.
        val limit = constraints.maxHeight - PlumDimens.listFadeBand.roundToPx()
        val placed = mutableListOf<Placeable>()
        var used = 0
        for (m in measurables) {
            val p = m.measure(loose)
            val next = used + (if (placed.isEmpty()) 0 else gap) + p.height
            if (constraints.hasBoundedHeight && next > limit) break
            placed += p
            used = next
        }
        layout(constraints.maxWidth, used) {
            var y = 0
            placed.forEach {
                it.place(0, y)
                y += it.height + gap
            }
        }
    }
}

/** An empty frame's shape: the landscape box most systems have. */
private const val EMPTY_FRAME_ASPECT = 4f / 3f

/** New collection…'s line may wrap this far (it is the card's only text). */
private const val NEW_ABOUT_LINES = 4

/** The most box fronts the mosaic shows. */
internal const val MOSAIC_GAMES = 4

/** The most titles carried under the card: more than the tallest column shows. */
private const val TITLES_CARRIED = 12

/** Test tags of the folder card (kept out of `HomeTags`, whose file holds the bars). */
internal object FolderTags {
    const val DETAILS = "folder_details"
    const val MOSAIC = "folder_mosaic"
    const val EMPTY_FRAME = "folder_empty_frame"
    const val COUNTS = "folder_counts"
    const val ABOUT = "folder_about"
    const val EMPTY = "folder_empty"
    const val SYSTEMS = "folder_systems"
    const val TITLES = "folder_titles"
    const val TITLE_LINE = "folder_title_line"
}
