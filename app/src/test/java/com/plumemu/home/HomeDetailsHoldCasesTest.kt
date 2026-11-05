package com.plumemu.home

import android.content.Intent
import android.os.BatteryManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onAllNodesWithTag
import com.plumemu.battery.BATTERY_LINE_SAMPLES
import com.plumemu.battery.BatteryPace
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.game.EmulatorToStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Nothing in the details column moves or resizes after a game's first frame, whatever its late data says: stepped frame by frame through
 * the art result landing (Unknown to Found or Absent) and the play facts being read, with the longest values the cells can hold, a read
 * that is slow or never ends, and a content that is emitted again every 100 ms.
 */
abstract class HomeDetailsHoldCasesBase(
    private val narrow: Boolean,
    private val size: TextSize = TextSize.Small,
    private val font: PlumFont = PlumFont.Jost,
) {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val metroid = LibraryLikeContent.SUPER_METROID
    private val metroidId = LibraryLikeContent.idAt(metroid).toString()
    private val emulator = EmulatorToStart("RetroArch: Snes9x", isInstalled = true)

    /** A read that answers [play] after [afterMs] of the test clock, or never when [afterMs] is null. */
    private class Slow(private val play: GamePlay?, private val afterMs: Long?) : HomePlayFactsSource {
        override suspend fun read(gameId: Long): GamePlay? {
            if (afterMs == null) awaitCancellation()
            delay(afterMs)
            return play
        }
    }

    private class Frame(val boxes: Map<String, Rect>, val texts: List<Pair<String, Rect>>)

    private fun dp(r: Rect): Rect {
        val d = compose.density.density
        return Rect(r.left / d, r.top / d, r.right / d, r.bottom / d)
    }

    private fun textsUnder(node: SemanticsNode): List<Pair<String, Rect>> {
        // A cell the box has no room for is measured but not placed: it is not on show.
        val own =
            if (SemanticsProperties.Text in node.config && node.layoutInfo.isPlaced) {
                val text = node.config[SemanticsProperties.Text].joinToString()
                if (text.isBlank()) emptyList() else listOf(text to dp(node.boundsInRoot))
            } else {
                emptyList()
            }
        return own + node.children.flatMap { textsUnder(it) }
    }

    private fun first(tag: String): SemanticsNode? = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()

    private fun frame(): Frame {
        val boxes = mutableMapOf<String, Rect>()
        for (tag in listOf(HomeTags.DETAILS_COVER, HomeTags.DETAILS_TITLE, HomeTags.DETAILS_SUBTITLE, HomeTags.DETAILS_SYSTEM, HomeTags.DETAILS_SYNOPSIS, HomeTags.DETAILS_COVER_SLOT)) first(tag)?.let { boxes[tag] = dp(it.boundsInRoot) }
        (first(HomeTags.DETAILS_FACTS) ?: first(HomeTags.DETAILS_FACTS_PENDING))?.let { boxes["facts"] = dp(it.boundsInRoot) }
        val texts = first(HomeTags.DETAILS)?.let { details -> textsUnder(details).filter { (_, r) -> r.width > 0f } }.orEmpty()
        return Frame(boxes, texts)
    }

    private fun details(publisher: String = "Nintendo") = GameDetails(year = 1994, publisher = publisher, genre = "Action", players = "1", description = "", rating = 17)

    private fun battery(level: Int) {
        @Suppress("DEPRECATION")
        RuntimeEnvironment.getApplication().sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_LEVEL, level).putExtra(BatteryManager.EXTRA_SCALE, 100))
    }

    /**
     * Shows [content] with [plays] and [art], steps [FRAMES] frames, calling [each] with the frame number before each, and checks nothing
     * moved: every box exists from the first frame at the same place and size, and a text keeps its place from the frame it first shows.
     */
    private fun run(
        plays: HomePlayFactsSource,
        hasBoxArt: Boolean = true,
        publisher: String = "Nintendo",
        title: String = "Super Metroid",
        art: ((FakeArtSource) -> Unit)? = null,
        hasStoredResult: Boolean = false,
        contentAt: ((Int) -> HomeContent)? = null,
        focus: List<Action>? = null,
        each: (Int) -> Unit = {},
    ): List<Frame> {
        val base =
            LibraryLikeContent.of(hasBoxArt = hasBoxArt, metroidDetails = details(publisher).takeIf { hasBoxArt }, metroidTitle = title)
                .let { if (hasStoredResult) it.asLibrary() else it }
        val source =
            if (hasStoredResult) {
                coverArtSource(content = base, synopses = emptyMap(), ratings = emptyMap())
            } else {
                coverArtSource(content = base, synopses = mapOf(artKey(metroidId) to SUPER_METROID_SYNOPSIS), ratings = mapOf(artKey(metroidId) to SUPER_METROID_RATING))
            }
        var content by mutableStateOf(base)
        val actions = (focus ?: LibraryLikeContent.gamesFocused(metroid)) + if (narrow && hasBoxArt) listOf(Action.Move(Direction.Right)) else emptyList()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size, font = font)) {
                CompositionLocalProvider(LocalHomePlayFacts provides plays) {
                    if (hasBoxArt) HomeWithArt(source, actions, content) else HomeAt(content, actions)
                }
            }
        }
        art?.invoke(source)
        val frames = mutableListOf<Frame>()
        compose.mainClock.advanceTimeByFrame()
        frames += frame()
        repeat(FRAMES) { i ->
            each(i)
            contentAt?.let { content = it(i) }
            compose.mainClock.advanceTimeByFrame()
            frames += frame()
        }
        check(frames)
        return frames
    }

    private fun same(what: String, a: Rect, b: Rect, widthToo: Boolean = true) {
        assertEquals("$what left", a.left, b.left, TOL)
        assertEquals("$what top", a.top, b.top, TOL)
        assertEquals("$what height", a.height, b.height, TOL)
        if (widthToo) assertEquals("$what width", a.width, b.width, TOL)
    }

    private fun check(frames: List<Frame>) {
        val firstFrame = frames.first()
        assertTrue("the title is there in the first frame", HomeTags.DETAILS_TITLE in firstFrame.boxes)
        assertTrue("the facts box is there in the first frame", "facts" in firstFrame.boxes)
        frames.forEachIndexed { n, f ->
            for ((tag, box) in firstFrame.boxes) {
                if (tag == HomeTags.DETAILS_SYNOPSIS) continue
                val now = f.boxes[tag]
                assertTrue("$tag exists in frame $n", now != null)
                // The narrow stop's cover is a picture fitted inside its slot (the slot is checked as strictly as the rest).
                if (tag == HomeTags.DETAILS_COVER && HomeTags.DETAILS_COVER_SLOT in firstFrame.boxes) {
                    val slot = firstFrame.boxes.getValue(HomeTags.DETAILS_COVER_SLOT)
                    assertTrue("the cover $now lies inside its slot $slot in frame $n", now!!.left >= slot.left - TOL && now.top >= slot.top - TOL && now.right <= slot.right + TOL && now.bottom <= slot.bottom + TOL)
                    continue
                }
                // The narrow stop's table is as wide as its words; its top and height hold.
                same("$tag in frame $n", box, now!!, widthToo = !(narrow && tag == "facts"))
            }
            f.texts.forEach { (text, r) ->
                // A text keeps its place from the first frame it shows to the last (widths too, but the narrow table's values grow with their words).
                val later = frames.drop(n + 1).mapNotNull { g -> g.texts.firstOrNull { it.first == text }?.second }
                later.forEach { same("'$text' from frame $n", r, it, widthToo = !narrow) }
            }
        }
        // Whatever is in the facts box stays inside it.
        val box = frames.last().boxes.getValue("facts")
        frames.last().texts.filter { (t, _) -> t in frames.last().texts.map { it.first } }.forEach { (text, r) ->
            if (r.top >= box.top - TOL && r.top < box.bottom) assertTrue("'$text' ends inside the facts box (${r.bottom} <= ${box.bottom})", r.bottom <= box.bottom + TOL || narrow)
        }
    }

    private val full = GamePlay(lastPlayedDaysAgo = 2, playTimeMinutes = 252, emulator = emulator)

    @Test
    fun theUsualGame() {
        val frames = run(Slow(full, 100))
        assertTrue("the facts arrived", frames.last().texts.any { "2 days ago" in it.first })
    }

    @Test
    fun theLongestLastPlayedAndPlayTime() {
        val frames = run(Slow(GamePlay(lastPlayedDaysAgo = 365, playTimeMinutes = 100 * 60 + 59, emulator = emulator), 100))
        assertTrue("the long values arrived", frames.last().texts.any { it.first.startsWith("365") } && frames.last().texts.any { it.first.startsWith("100") })
    }

    /** The settled frame has a text starting with [start], placed inside the facts box (its coordinates, not the existence of a node). */
    private fun assertShownInBox(frames: List<Frame>, start: String, also: (String) -> Boolean = { true }) {
        val last = frames.last()
        val box = last.boxes.getValue("facts")
        val shown = last.texts.filter { it.first.startsWith(start) && also(it.first) }
        assertTrue("'$start' is on show: ${last.texts.map { it.first }}", shown.isNotEmpty())
        shown.forEach { (t, r) -> assertTrue("'$t' $r lies inside the box $box", r.top >= box.top - TOL && r.bottom <= box.bottom + TOL && r.left >= box.left - TOL && r.right <= box.right + TOL) }
    }

    @Test
    fun aLongEmulatorName() {
        val frames = run(Slow(GamePlay(2, 252, EmulatorToStart("RetroArch: Genesis Plus GX Wide Super Long Core Name Edition", isInstalled = true)), 100))
        // At the largest text the box cannot hold every cell and the policy leaves the emulator out whole (it is in Info).
        if (size != TextSize.Largest) assertShownInBox(frames, "RetroArch: Genesis")
    }

    @Test
    fun aShortBatteryPace() = batteryCase(BATTERY_LINE_SAMPLES.first().level, BATTERY_LINE_SAMPLES.first().pace)

    @Test
    fun aLongBatteryPace() = batteryCase(BATTERY_LINE_SAMPLES[1].level, BATTERY_LINE_SAMPLES[1].pace)

    private fun batteryCase(
        level: Int,
        pace: BatteryPace,
    ) {
        battery(level)
        // The battery level changes twice while the game rests: nothing moves, and the cell is on show with its time.
        val frames = run(Slow(GamePlay(2, 252, emulator, batteryPace = pace), 100), each = { if (it == 20) battery(level / 2) else if (it == 50) battery(level / 3) })
        val last = frames.last()
        val time = last.texts.filter { (t, _) -> t.replace('\u00A0', ' ').contains("an hour") }
        if (narrow) {
            // The narrow stop's table has the pace as a line under it.
            assertTrue("the battery pace is on show: ${last.texts.map { it.first }}", time.isNotEmpty())
        } else if (size != TextSize.Largest) {
            assertShownInBox(frames, "BATTERY")
            val box = last.boxes.getValue("facts")
            val value = last.texts.filter { (t, _) -> t.replace('\u00A0', ' ').let { " h" in it || " min" in it } && t != "4 h 12 min" }
            assertTrue("the battery time is on show: ${last.texts.map { it.first }}", value.isNotEmpty() && value.all { (_, r) -> r.bottom <= box.bottom + TOL })
        }
    }

    @Test
    fun aLongPublisher() {
        run(Slow(full, 100), publisher = "Sony Computer Entertainment Europe Limited and Partners")
    }

    @Test
    fun aGameWithoutBoxArtKeepsItsPosterAndTitle() {
        assumeFalse("the text-only column is wide screens only", narrow)
        val frames = run(Slow(full, 100), hasBoxArt = false)
        assertTrue("the facts arrived", frames.last().texts.any { "2 days ago" in it.first })
    }

    @Test
    fun theArtResultLandsAfterTheFirstFrames() {
        // Nothing known at first (the slot is not Found), then the result lands twelve frames in.
        lateinit var landing: FakeArtSource
        lateinit var all: Map<String, GameArt>
        run(Slow(full, 100), art = { landing = it; all = it.results.value; it.results.value = emptyMap() }, each = { if (it == 12) landing.results.value = all })
    }

    @Test
    fun aResultWithNeitherSynopsisNorRatingRevealsWhenItIsRead() {
        // Read as DetailsExtras.None, equal by value to the marker of a read still pending: the state kept the marker, so every such
        // game waited for the late timer.
        val frames = run(Slow(full, 10), hasStoredResult = true)
        val shown = frames.indexOfFirst { f -> f.texts.any { "2 days ago" in it.first } }
        assertTrue("the facts show right after the read, not at the late timer (frame $shown)", shown in 0 until REVEAL_DEADLINE_FRAMES)
    }

    @Test
    fun theArtResultNeverComes() {
        run(Slow(full, 100), art = { it.results.value = emptyMap() })
    }

    @Test
    fun aReadThatNeverFinishesRevealsWhatIsKnownOnceAndMovesNothing() {
        val frames = run(Slow(null, null))
        assertTrue("the box shows what is known after the wait (the genre)", frames.last().texts.any { it.first == "Action" || "Action" in it.first })
    }

    /** The mixed lists name a game's system once the focus has rested: the line is held (a blank line has a height) so the facts below never move. */
    @Test
    fun theSystemLineArrivesInRecentWithoutBoxArt() {
        assumeFalse("the text-only column is wide screens only", narrow)
        val frames = run(Slow(full, 100), hasBoxArt = false, focus = listOf(Action.TapItem(Column.Systems, 0), Action.Enter, Action.TapItem(Column.Games, 1)))
        assertTrue("the system line arrived", frames.last().texts.any { "Super Nintendo" in it.first })
    }

    @Test
    fun theSystemLineArrivesInFavoritesWithoutBoxArt() {
        assumeFalse("the text-only column is wide screens only", narrow)
        val frames = run(Slow(full, 100), hasBoxArt = false, focus = listOf(Action.TapItem(Column.Systems, 1), Action.Enter, Action.TapItem(Column.Games, 0)))
        assertTrue("the system line arrived", frames.last().texts.any { "Super Nintendo" in it.first })
    }

    /**
     * The stored shape of the picture arrives while the game rests (the shapes pass): strict. The slot, the title and the text beside the cover
     * keep their place and width in every frame (the slot's width is that of the first frame); the picture only fits inside the slot.
     */
    private fun storedShapeArrives(aspect: Float) {
        assumeFalse("only the narrow stop has the text beside the cover", !narrow)
        val title = "The Legend of Zelda: A Link to the Past Four Swords Anniversary"
        val before = LibraryLikeContent.of(hasBoxArt = true, metroidDetails = details(), metroidTitle = title)
        val after = before.withEntries(before.entries.map { e -> e.copy(rows = e.rows.map { r -> r.copy(artAspect = aspect) }) })
        run(Slow(full, 100), title = title, contentAt = { i -> if (i < 20) before else after })
    }

    @Test
    fun aPortraitStoredShapeArrivesWhileResting() = storedShapeArrives(0.6f)

    @Test
    fun aWideStoredShapeArrivesWhileResting() = storedShapeArrives(1.6f)

    @Test
    fun aReadThatAnswersNothing() {
        run(Slow(null, 100))
    }

    @Test
    fun aContentEmittedAgainEvery100Ms() {
        val base = LibraryLikeContent.of(hasBoxArt = true, metroidDetails = details())
        val frames = run(Slow(full, 100), contentAt = { i -> LibraryLikeContent.of(hasBoxArt = true, metroidDetails = details(), favoriteMarked = (i / 6) % 2 == 0).also { assertTrue(it.entries.isNotEmpty() && base.entries.isNotEmpty()) } })
        assertTrue("the read is not starved by the content changing: the facts arrived", frames.last().texts.any { "2 days ago" in it.first })
    }

    private companion object {
        const val FRAMES = 90

        /** 320 ms of test frames: well past the settle and a read, well before the late timer (550 ms). */
        const val REVEAL_DEADLINE_FRAMES = 20
        const val TOL = 0.5f
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeDetailsHoldCases768Test : HomeDetailsHoldCasesBase(narrow = false)

/** The largest text in the serif font at 768 x 432: the tightest the column gets. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeDetailsHoldCasesLargestSerif768Test : HomeDetailsHoldCasesBase(narrow = false, size = TextSize.Largest, font = PlumFont.Serif)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h480dp-land-xhdpi")
class HomeDetailsHoldCasesNarrowTest : HomeDetailsHoldCasesBase(narrow = true)
