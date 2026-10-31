package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map

/**
 * One saved picture of a game: its file and its size in pixels, so its shape is known before it
 * is decoded. [version] changes when the file is written again under the same path (a
 * re-download: `scraped_game.scraped_at`), so a decoded copy kept in memory is never reused for it.
 */
@Immutable
data class ArtPicture(
    val path: String,
    val width: Int,
    val height: Int,
    val version: Long = 0,
) {
    /** Width over height; a damaged size counts as square rather than dividing by zero. */
    val aspect: Float get() = if (width > 0 && height > 0) width.toFloat() / height else 1f
}

/**
 * What the home screen shows of a game's ScreenScraper result: the box front saved at
 * the grid size ([grid]) and at the large size ([large], the details column), and the facts.
 * [GameDetails.description] is empty and [GameDetails.rating] null here: both are read only for
 * the focused game ([HomeArtSource.extras]).
 */
@Immutable
data class GameArt(
    val grid: ArtPicture?,
    val large: ArtPicture?,
    val details: GameDetails,
)

/** What is known of one game's result on screen. */
@Immutable
sealed interface ArtSlot {
    /** Being read: covers show a plain slot, the list no year yet. */
    data object Unknown : ArtSlot

    /** No result (or no art at all on this screen): the title in the placeholder, the region in the list. */
    data object Absent : ArtSlot

    data class Found(val art: GameArt) : ArtSlot
}

/**
 * What a result says besides [GameArt]'s facts, read only for the focused game (the stored details
 * JSON): the English synopsis ("" when none) and ScreenScraper's rating out of 20 (null when none).
 */
@Immutable
data class DetailsExtras(
    val synopsis: String,
    val rating: Int?,
) {
    companion object {
        val None = DetailsExtras("", null)
    }
}

/**
 * Where the home screen reads ScreenScraper results and decodes their pictures. The library's is
 * `content/LibraryHomeArt`; [None] has nothing (no account, the fakes, the benchmark).
 */
internal interface HomeArtSource {
    /**
     * The results stored under [keys], by key (a key without a result is left out), again after
     * every change to them. Mapping runs off the main thread.
     */
    fun observe(keys: List<String>): Flow<Map<String, GameArt>>

    /** The English synopsis and the rating of the result under [key] (read once, off the main thread). */
    suspend fun extras(key: String): DetailsExtras

    /** Decodes the saved pictures. */
    val images: CoverImages

    /** Where the covers' glow colours are worked out (off the main thread); tests answer an inline dispatcher so a shot never races it. */
    val glowDispatcher: CoroutineDispatcher get() = Dispatchers.Default

    object None : HomeArtSource {
        override fun observe(keys: List<String>): Flow<Map<String, GameArt>> = flowOf(emptyMap())

        override suspend fun extras(key: String): DetailsExtras = DetailsExtras.None

        override val images: CoverImages = CoverImages.None
    }
}

/** Decodes saved pictures at the size they are drawn (covers decoded at cell size). */
internal interface CoverImages {
    /** [picture] decoded to fit [widthPx] x [heightPx], when it is in memory now: drawn at once, with no fade. */
    fun cached(
        picture: ArtPicture,
        widthPx: Int,
        heightPx: Int,
    ): ImageBitmap?

    /** [picture] decoded to fit [widthPx] x [heightPx], off the main thread; null when it cannot be read (the titled placeholder stays). */
    suspend fun load(
        picture: ArtPicture,
        widthPx: Int,
        heightPx: Int,
    ): ImageBitmap?

    object None : CoverImages {
        override fun cached(
            picture: ArtPicture,
            widthPx: Int,
            heightPx: Int,
        ): ImageBitmap? = null

        override suspend fun load(
            picture: ArtPicture,
            widthPx: Int,
            heightPx: Int,
        ): ImageBitmap? = null
    }
}

/**
 * The home screen's view of [source]: one state per game shown, so a result that lands recomposes
 * the one row or cover that shows it and nothing else. Lists and grids follow the window of rows
 * they show ([FollowArt]); only those keys are observed, in blocks, so a held key re-observes once
 * every [ART_BLOCK] rows, not on every move. Without [isLive] (no account, the fakes)
 * every slot is [ArtSlot.Absent] and nothing is observed.
 *
 * Slots are kept for the screen's life (one small entry per game seen, never more than the
 * library); touched on the main thread only.
 */
@Stable
internal class HomeArt(
    val source: HomeArtSource,
    val isLive: Boolean,
) {
    private val slots = HashMap<String, MutableState<ArtSlot>>()

    // Bumped when slots are added. A reader of a key with no slot yet reads it, so it runs again
    // once its slot exists: slots are only made by [write], never while composing (a state made in
    // a composition and written before that reader ran again was seen to invalidate nothing).
    private val added = mutableIntStateOf(0)
    private val synopses =
        object : LinkedHashMap<String, DetailsExtras>(SYNOPSES_KEPT, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DetailsExtras>?): Boolean = size > SYNOPSES_KEPT
        }

    /** The pictures' decoder. */
    val images: CoverImages get() = source.images

    /** The colour each decoded cover glows in when focused, learnt as the pictures are loaded. */
    val glowColors = CoverGlowColors(source.glowDispatcher)

    /** [key]'s result as far as it is known; reading it in composition or drawing observes it. */
    fun slot(key: String?): ArtSlot {
        if (!isLive || key == null) return ArtSlot.Absent
        val slot = slots[key] ?: return ArtSlot.Unknown.also { added.intValue }
        return slot.value
    }

    /** The synopsis and rating read last for [key], if they are still kept. */
    fun keptExtras(key: String): DetailsExtras? = synopses[key]

    /** [key]'s synopsis and rating, read through [source] once and kept for the last few games. */
    suspend fun extras(key: String): DetailsExtras = synopses[key] ?: source.extras(key).also { synopses[key] = it }

    /** Observes each window [windows] gives (the latest only) and writes the slots that changed. */
    suspend fun follow(windows: Flow<List<String>>) {
        if (!isLive) return
        windows.distinctUntilChanged().collectLatest { keys ->
            source.observe(keys).collect { found -> write(keys, found) }
        }
    }

    private fun write(
        keys: List<String>,
        found: Map<String, GameArt>,
    ) {
        var isNew = false
        for (key in keys) {
            val value = found[key]?.let(ArtSlot::Found) ?: ArtSlot.Absent
            val slot = slots[key]
            if (slot == null) {
                slots[key] = mutableStateOf(value)
                isNew = true
            } else if (slot.value != value) {
                slot.value = value
                // A changed result may carry another synopsis or rating: read them again when next shown.
                synopses.remove(key)
            }
        }
        if (isNew) added.intValue++
    }

    companion object {
        /** Rows observed per block; a window is the block shown and one each side. */
        const val ART_BLOCK = 48
        private const val SYNOPSES_KEPT = 16
        private const val LOAD_FACTOR = 0.75f

        val None = HomeArt(HomeArtSource.None, isLive = false)
    }
}

/** The home screen's [HomeArt]; [HomeArt.None] outside one (tests, previews). */
internal val LocalHomeArt = staticCompositionLocalOf { HomeArt.None }

/** Gives [content] a [HomeArt] over [source], observed only when [hasBoxArt]. */
@Composable
internal fun ProvideHomeArt(
    source: HomeArtSource,
    hasBoxArt: Boolean,
    content: @Composable () -> Unit,
) {
    val art = remember(source, hasBoxArt) { HomeArt(source, isLive = hasBoxArt && source !== HomeArtSource.None) }
    CompositionLocalProvider(LocalHomeArt provides art, content = content)
}

/**
 * Observes the results of the rows a list or grid shows: the [HomeArt.ART_BLOCK] holding
 * [firstVisible] and one block on each side. [firstVisible] is read in a snapshot flow, so a
 * scroll changes the window without recomposing anything.
 */
@Composable
internal fun FollowArt(
    rows: List<GameRowModel>,
    firstVisible: () -> Int,
) {
    val art = LocalHomeArt.current
    if (!art.isLive) return
    val latestRows = rememberUpdatedState(rows)
    val latestFirst = rememberUpdatedState(firstVisible)
    LaunchedEffect(art) {
        val windows =
            snapshotFlow { latestRows.value to latestFirst.value().coerceAtLeast(0) / HomeArt.ART_BLOCK }
                .distinctUntilChanged()
                .map { (list, block) -> artWindow(list, block) }
        art.follow(windows)
    }
}

/** The scrape keys of [rows] in block [block] and the blocks on each side of it. */
internal fun artWindow(
    rows: List<GameRowModel>,
    block: Int,
): List<String> {
    val from = ((block - 1) * HomeArt.ART_BLOCK).coerceIn(0, rows.size)
    val to = ((block + 2) * HomeArt.ART_BLOCK).coerceIn(from, rows.size)
    return rows.subList(from, to).mapNotNull { it.scrapeKey }
}

/** The facts to show for [row]: its own (the fakes) or its ScreenScraper result's, if any. */
internal fun detailsOf(
    row: GameRowModel,
    slot: ArtSlot,
): GameDetails? = row.details ?: (slot as? ArtSlot.Found)?.art?.details
