package com.plumemu.home

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumLayout
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The covers of the neighbouring systems are decoded ahead at the shown size: bounded, cancelled by a switch, and nothing runs once done. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class NeighbourCoversTest {
    private val layout = PlumLayout(width = 768.dp, height = 432.dp)
    private val density = Density(2f)
    private val memory = ImageBitmap(1, 1)

    private fun picture(
        system: String,
        i: Int,
    ) = ArtPicture("p-$system-$i", 450, 380 + i % 5 * 20)

    private fun entry(
        id: String,
        games: Int = 100,
    ) = HomeEntry(id, EntryKind.System, id, List(games) { GameRowModel("$id-$it", "Game $it", "USA", scrapeKey = "k-$id-$it") })

    private val ids = listOf("a", "b", "c", "d", "e")
    private val content = HomeContent.Empty.withEntries(ids.map { entry(it) })

    private fun source(images: CoverImages): FakeArtSource =
        FakeArtSource(
            ids.flatMap { id -> List(100) { i -> "k-$id-$i" to GameArt(picture(id, i), null, ArtworkFakes.detailsFor("k-$id-$i", i)) } }.toMap(),
            images = images,
        )

    private fun coloured(): ImageBitmap = android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF336699.toInt()) }.asImageBitmap()

    /** Remembers what it was asked to decode; every load waits for [gate]. */
    private inner class SpyImages(val gate: CompletableDeferred<Unit>? = null) : CoverImages {
        val started = mutableListOf<String>()
        val done = mutableSetOf<String>()
        var cancelled = 0

        override fun cached(
            picture: ArtPicture,
            widthPx: Int,
            heightPx: Int,
        ): ImageBitmap? = if (picture.path in done) coloured() else null

        override suspend fun load(
            picture: ArtPicture,
            widthPx: Int,
            heightPx: Int,
        ): ImageBitmap? {
            started += picture.path
            try {
                gate?.await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                cancelled++
                throw e
            }
            done += picture.path
            return coloured()
        }
    }

    @Test
    fun theNeighboursGlowColoursAreLearntAheadToo() =
        runTest {
            val art = HomeArt(source(SpyImages()), isLive = true)
            val shown = MutableSharedFlow<Int>(replay = 1)
            shown.tryEmit(2)
            backgroundScope.launch { prefetchWhileShown(shown, { content }, { true }, { 0 }, art, layout, density) }
            advanceTimeBy(START_DELAY_MS + 1)
            runCurrent()
            assertTrue("a neighbour's cover has its glow colour before it is shown", art.glowColors[picture("b", 0)] != null)
            assertTrue("a cover of a system that is not a neighbour has none", art.glowColors[picture("a", 0)] == null)
        }

    @Test
    fun theNeighboursAreTheNearestGridsEitherSide() {
        val withGaps = content.withEntries(content.entries.mapIndexed { i, e -> if (i == 1) e.copy(rows = emptyList()) else e })
        assertEquals(listOf("c", "e"), neighbourEntries(withGaps, 3) { true }.map { it.id }.sorted())
        assertEquals(listOf("c"), neighbourEntries(withGaps, 3) { it.id != "e" && it.id != "a" }.map { it.id })
        assertEquals(emptyList<HomeEntry>(), neighbourEntries(content.withEntries(listOf(entry("a"))), 0) { true })
    }

    @Test
    fun theAsksAreTheFirstScreenAtTheSizeTheGridDecodesAt() {
        val e = entry("a")
        val asks = coverAsks(e, 0, { GameArt(picture("a", it.id.removePrefix("a-").toInt()), null, ArtworkFakes.detailsFor(it.id, 1)) }, layout, density)
        assertEquals(COVERS_PER_SYSTEM, asks.size)
        asks.forEach { ask ->
            assertTrue(ask.widthPx > 0 && ask.heightPx > 0)
            val shape = ask.picture.aspect
            assertEquals("a cover keeps its picture's shape", shape, ask.widthPx.toFloat() / ask.heightPx, 0.03f)
        }
    }

    @Test
    fun aGameWithoutAPictureIsNotAsked() {
        assertEquals(emptyList<CoverAsk>(), coverAsks(entry("a"), 0, { null }, layout, density))
    }

    @Test
    fun bothNeighboursAreDecodedWithinTheBoundThenNothingRuns() =
        runTest {
            val images = SpyImages()
            val art = HomeArt(source(images), isLive = true)
            val shown = MutableSharedFlow<Int>(replay = 1)
            shown.tryEmit(2)
            backgroundScope.launch { prefetchWhileShown(shown, { content }, { true }, { 0 }, art, layout, density) }
            runCurrent()
            assertTrue("nothing before the wait", images.started.isEmpty())
            advanceTimeBy(START_DELAY_MS + 1)
            runCurrent()
            assertEquals("b and d, at most the bound each", 2 * COVERS_PER_SYSTEM, images.started.size)
            assertTrue(images.started.all { it.startsWith("p-b-") || it.startsWith("p-d-") })
            val after = images.started.size
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals("idle once done", after, images.started.size)
        }

    @Test
    fun aSwitchCancelsTheWorkInProgress() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val images = SpyImages(gate)
            val art = HomeArt(source(images), isLive = true)
            val shown = MutableSharedFlow<Int>(replay = 1)
            shown.tryEmit(0)
            backgroundScope.launch { prefetchWhileShown(shown, { content }, { true }, { 0 }, art, layout, density) }
            advanceTimeBy(START_DELAY_MS + 1)
            runCurrent()
            assertEquals(1, images.started.size)
            shown.emit(2)
            runCurrent()
            assertEquals("the decode of the system left behind is cancelled", 1, images.cancelled)
            advanceTimeBy(START_DELAY_MS + 1)
            runCurrent()
            assertEquals(2, images.started.size)
            gate.complete(Unit)
            advanceTimeBy(60_000)
            runCurrent()
            assertTrue(images.started.drop(1).all { it.startsWith("p-b-") || it.startsWith("p-d-") })
            assertEquals(2 * COVERS_PER_SYSTEM + 1, images.started.size)
        }

    @Test
    fun theNeighboursResultsAreReadOnceAndKept() =
        runTest {
            val src = source(SpyImages())
            val art = HomeArt(src, isLive = true)
            val keys = windowKeys(entry("b"), 0)
            art.prefetchSlots(keys)
            assertTrue(keys.all { art.slot(it) is ArtSlot.Found })
            val reads = src.observed.size
            art.prefetchSlots(keys)
            assertEquals("a second ask reads nothing", reads, src.observed.size)
        }
}
