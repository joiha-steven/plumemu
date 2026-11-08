package com.plumemu.home

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.testing.TableText
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Box art on the main screen: the grid's shelves (covers at their own shape,
 * bottoms on one line, centred in their cells), the placeholder of a cover still decoding has the
 * picture's shape, pictures decoded at the size drawn, the details' cover and synopsis, the list's
 * years, what is observed (only the rows shown), and the viewed system for the runs.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeCoverArtTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) = compose.setContent { PlumTheme(TableText.Default) { content() } }

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    /** Every decoded picture on screen, in dp, with its cell (the cover node it sits in). */
    private fun pictures(): List<Pair<Rect, Rect>> {
        val density = compose.density.density
        // Unclipped (a row cut by the strip's edge still stands on its shelf); the focused cover's
        // layer scale is not in its size, so [focusedPicture] measures that one apart.
        fun SemanticsNode.dpRect(): Rect = Rect(positionInRoot.x / density, positionInRoot.y / density, (positionInRoot.x + size.width) / density, (positionInRoot.y + size.height) / density)
        val cells = compose.onAllNodes(hasTestTagPrefix("home_cover_"), useUnmergedTree = true).fetchSemanticsNodes()
        return cells.mapNotNull { cell ->
            val picture = cell.children.firstOrNull { node -> node.config.any { it.value == HomeTags.COVER_PICTURE } } ?: return@mapNotNull null
            cell.dpRect() to picture.dpRect()
        }
    }

    private fun hasTestTagPrefix(prefix: String) =
        androidx.compose.ui.test.SemanticsMatcher("tag starts with $prefix") { node ->
            node.config.any { (key, value) -> key.name == "TestTag" && (value as? String)?.let { it.startsWith(prefix) && it.removePrefix(prefix).all(Char::isDigit) } == true }
        }

    /** [pictures] without the focused cover's (its groove is lit; the box is where it is at rest). */
    private fun restingPictures(focusedIndex: Int): List<Pair<Rect, Rect>> {
        val focused = bounds(HomeTags.cover(focusedIndex))
        return pictures().filterNot { abs(it.first.left - focused.left.value) < EPSILON && abs(it.first.top - focused.top.value) < EPSILON }
    }

    private fun assertShelf(
        label: String,
        focusedIndex: Int,
    ) {
        val shown = restingPictures(focusedIndex)
        assertTrue("$label: pictures drawn", shown.size >= 3)
        for ((cell, picture) in shown) {
            // The box stands in its collar; the collar stands on the cell's bottom and fills the cell one way.
            val side = CollarSize.Cover.sidePadding.value
            val bottom = CollarSize.Cover.bottomPadding.value
            assertEquals("$label: a collar stands on its cell's bottom (the shelf)", cell.bottom - bottom, picture.bottom, EPSILON)
            assertEquals("$label: centred across its cell", cell.left - picture.left, picture.right - cell.right, EPSILON)
            assertTrue("$label: never wider or taller than its cell", picture.width <= cell.width + EPSILON && picture.height <= cell.height + EPSILON)
            assertTrue("$label: fills its cell one way (fitted, not shrunk)", abs(picture.width - (cell.width - 2 * side)) < EPSILON || abs(picture.height - (cell.height - CollarSize.Cover.topPadding.value - bottom)) < EPSILON)
        }
        // One shelf per row: every cover of a row ends on the same line.
        shown.groupBy { it.first.top }.values.forEach { row -> assertEquals("$label: one bottom line per row", 1, row.map { Math.round(it.second.bottom * 10) }.distinct().size) }
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideGridCoversStandOnShelves() {
        show { CoverGridBoard(rememberSource { coverArtSource() }) }
        assertShelf("768x432 Super Nintendo", MEGA_MAN_X)
        // The focus lifts the cover (it grows about a pivot, so at least one side moves out): on some side a pixel just past the laid-out box is its picture, not the collar's groove.
        val density = compose.density.density
        val resting = pictures().first { (cell, _) -> abs(cell.left - bounds(HomeTags.cover(MEGA_MAN_X)).left.value) < EPSILON && abs(cell.top - bounds(HomeTags.cover(MEGA_MAN_X)).top.value) < EPSILON }.second
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        fun alike(
            out: androidx.compose.ui.geometry.Offset,
            edge: androidx.compose.ui.geometry.Offset,
        ): Boolean {
            val beside = pixels[(edge.x + out.x).toInt(), (edge.y + out.y).toInt()]
            val inside = pixels[(edge.x - out.x * 2f).toInt(), (edge.y - out.y * 2f).toInt()]
            return abs(beside.red - inside.red) < 0.1f && abs(beside.green - inside.green) < 0.1f && abs(beside.blue - inside.blue) < 0.1f
        }
        val grown =
            alike(androidx.compose.ui.geometry.Offset(-1f, 0f), androidx.compose.ui.geometry.Offset(resting.left * density, resting.center.y * density)) ||
                alike(androidx.compose.ui.geometry.Offset(1f, 0f), androidx.compose.ui.geometry.Offset(resting.right * density, resting.center.y * density)) ||
                alike(androidx.compose.ui.geometry.Offset(0f, -1f), androidx.compose.ui.geometry.Offset(resting.center.x * density, resting.top * density))
        assertTrue("the focused cover is not drawn larger: on no side does its picture lie past its laid-out edge", grown)
    }

    @Test
    @Config(qualifiers = NARROW)
    fun narrowPortraitGridCoversStandOnShelves() {
        show { CoverGridPortraitBoard(rememberSource { coverArtSource() }) }
        assertShelf("576x432 PSP", PERSONA_3_PORTABLE)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun picturesKeepTheirShapeAndAreDecodedAtTheDrawnSize() {
        val source = coverArtSource()
        show { CoverGridBoard(source) }
        val density = compose.density.density
        val art = source.results.value
        for ((_, picture) in restingPictures(MEGA_MAN_X)) {
            val drawn = picture.width / picture.height
            val asked = (source.images as FakeCoverImages).decoded
            assertTrue("a decode at the drawn size", asked.any { (_, w, h) -> abs(w - picture.width * density) <= 1f && abs(h - picture.height * density) <= 1f })
            assertTrue("the drawn shape is a saved picture's (no stretch)", art.values.any { a -> a.grid != null && abs(a.grid!!.aspect - drawn) < ASPECT_SLACK })
        }
    }

    @Test
    @Config(qualifiers = WIDE)
    fun aCoverStillDecodingKeepsThePicturesShape() {
        val pending = "/media/${snesRow(DONKEY_KONG_COUNTRY_2).id}/box-front-grid.webp"
        show { CoverGridBoard(rememberSource { coverArtSource(pending = setOf(pending)) }) }
        val cell = bounds(HomeTags.cover(DONKEY_KONG_COUNTRY_2))
        val slots = pictures().filter { abs(it.first.left - cell.left.value) < EPSILON && abs(it.first.top - cell.top.value) < EPSILON }
        assertEquals("the waiting cover's slot is drawn", 1, slots.size)
        val (box, slot) = slots.single()
        // Donkey Kong Country 2's saved box front is 1.36 wide for one high; the cell is about 1.40.
        assertEquals("the slot has the picture's shape", 1.36f, slot.width / slot.height, ASPECT_SLACK)
        assertTrue("narrower than its cell", slot.width < box.width - 1f)
        assertEquals("on the shelf", box.bottom - CollarSize.Cover.bottomPadding.value, slot.bottom, EPSILON)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun theGridBarNamesTheFocusedGamesYearAndPublisher() {
        show {
            CoverGridBoard(rememberSource { coverArtSource() })
        }
        compose.onNodeWithText("Mega Man X 1993 · Capcom", useUnmergedTree = true).assertExists()
    }

    /** At 768 the focused game's year and publisher and "L1 R1 Size" stay; "L2 R2 Switch system" is the first to go, so it is never kept without Size (in Jost it still fits here). */
    @Test
    @Config(qualifiers = WIDE)
    fun at768AGameGridKeepsYearAndPublisherAndSize() {
        show { CoverGridBoard(rememberSource { coverArtSource() }) }
        compose.onNodeWithText("Mega Man X 1993 · Capcom", useUnmergedTree = true).assertExists()
        assertTrue("L1 R1 Size stays", isPlaced(Action.NextSystem))
        assertTrue("Switch system is not kept over Size", !isPlaced(Action.JumpLetterNext) || isPlaced(Action.NextSystem))
    }

    /** At 576 the grid bar keeps "L1 R1 Size" if anything and drops "L2 R2 Switch system" first (never the reverse). */
    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun at576AGameGridNeverKeepsSwitchSystemWithoutSize() {
        show { CoverGridBoard(rememberSource { coverArtSource() }) }
        assertTrue("Switch system is not kept over Size", !isPlaced(Action.JumpLetterNext) || isPlaced(Action.NextSystem))
        assertTrue("L1 R1 Size stays at 576", isPlaced(Action.NextSystem))
    }

    private fun isPlaced(action: Action): Boolean =
        compose.onAllNodesWithTag(HomeTags.hint(action), useUnmergedTree = true).fetchSemanticsNodes().any { it.layoutInfo.isPlaced }

    /** The Thor (832 x 468 dp, density about 2.3): the year and publisher stay, and so does "L1 R1 Size"; "L2 R2 Switch system" gives way. */
    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xxhdpi")
    fun theGridBarOnTheThorKeepsYearPublisherAndSize() {
        show {
            CoverGridBoard(rememberSource { coverArtSource() })
        }
        compose.onNodeWithText("Mega Man X 1993 · Capcom", useUnmergedTree = true).assertExists()
        assertTrue("Size stays on the Thor", isPlaced(Action.NextSystem))
    }

    @Test
    @Config(qualifiers = WIDE)
    fun aResultThatLandsLaterReachesTheDetailsAndTheList() {
        val source = coverArtSource()
        val all = source.results.value
        source.results.value = emptyMap()
        show { CoverDetailsBoard(source) }
        compose.waitForIdle()
        compose.runOnIdle { source.results.value = all }
        compose.waitForIdle()
        compose.onNodeWithText("Nintendo, 1994", useUnmergedTree = true).assertExists()
        assertTrue("the list's years", compose.onAllNodes(hasText("1993", substring = true) and hasAnyAncestor(hasTestTag(HomeTags.GAMES)), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    @Config(qualifiers = WIDE)
    fun aResultThatLandsLaterReachesTheGridAndItsBar() {
        val source = coverArtSource()
        val all = source.results.value
        source.results.value = emptyMap()
        show { CoverGridBoard(source) }
        compose.waitForIdle()
        assertTrue("no pictures yet", pictures().isEmpty())
        compose.runOnIdle { source.results.value = all }
        compose.waitForIdle()
        assertTrue("the covers land", pictures().size >= 3)
        compose.onNodeWithText("Mega Man X 1993 · Capcom", useUnmergedTree = true).assertExists()
    }

    @Test
    @Config(qualifiers = WIDE)
    fun aGameWithoutArtWearsItsPosterInsideItsCell() {
        show { CoverGridBoard(rememberSource { coverArtSource(noArt = setOf("EarthBound")) }) }
        val poster = compose.onNode(hasContentDescription("EarthBound") and hasAnyAncestor(hasTestTag(HomeTags.cover(2))), useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("it is a poster cover", compose.onAllNodes(hasTestTag(HomeTags.POSTER_COVER) and hasAnyAncestor(hasTestTag(HomeTags.cover(2))), useUnmergedTree = true).fetchSemanticsNodes().size == 1)
        val cell = bounds(HomeTags.cover(2))
        assertTrue("the poster inside its cell", poster.left >= cell.left && poster.right <= cell.right && poster.top >= cell.top && poster.bottom <= cell.bottom)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideDetailsShowTheLargeCoverAtItsShapeAndTheFacts() {
        show { CoverDetailsBoard(rememberSource { coverArtSource() }) }
        val slot = bounds(HomeTags.DETAILS_COVER)
        val picture = pictureIn(slot)
        val collar = com.plumemu.coreui.components.CollarSize.Cover
        assertEquals("on the left edge of the 248x178 slot", slot.left.value + CollarSize.Cover.sidePadding.value, picture.left, EPSILON)
        assertEquals("in its collar on the slot's bottom: the title below never moves", slot.bottom.value - collar.bottomPadding.value, picture.bottom, EPSILON)
        assertEquals("the slot's height less the collar (Super Metroid's box is wider than tall but narrower than the slot)", (slot.bottom - slot.top).value - collar.topPadding.value - collar.bottomPadding.value, picture.height, EPSILON)
        compose.onNodeWithText("Nintendo, 1994", useUnmergedTree = true).assertExists()
        // The rating (17 of 20) is not in the result: it is read with the synopsis, once the focus has rested.
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.waitForIdle()
        // The details block: the genre under GENRE (first genre alone when the list does not fit one line), the number and five dots under RATING (spoken as one "Rating, 8.5/10").
        compose.onNodeWithText("Action", substring = true, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("8.5", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(HomeTags.DETAILS_RATING_DOTS, useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Rating, 8.5/10", useUnmergedTree = true).assertExists()
    }

    @Test
    @Config(qualifiers = NARROW)
    fun narrowDetailsStopShowsTheSynopsisUnderTheCover() {
        val source = coverArtSource()
        show { CoverDetailsBoard(source) }
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.waitForIdle()
        val synopsis = bounds(HomeTags.DETAILS_SYNOPSIS)
        assertTrue("under the cover", synopsis.top >= bounds(HomeTags.DETAILS_COVER).bottom)
        assertTrue("within the strip (above the hint bar)", synopsis.bottom <= bounds(HomeTags.HINT_BAR).top)
        assertEquals("read once, for the focused game", listOf(artKey(SUPER_METROID_ID)), source.synopsisReads)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideDetailsKeepTheTitleStillWhateverTheBoxsShape() {
        val source = realCoverSource()
        show { RealCoverDetails("psp", PERSONA_3_PORTABLE, source) }
        val slot = bounds(HomeTags.DETAILS_COVER)
        val column = bounds(HomeTags.DETAILS)
        // The cover is what the held title and facts block leave of the column (at most 178): the same for every game, so it never changes when data arrives.
        val slotHeight = (slot.bottom - slot.top).value
        assertTrue("the slot is at most its full height and at least its floor: $slotHeight", slotHeight <= PlumDimens.detailsCoverHeightWide.value + EPSILON && slotHeight >= PlumDimens.detailsCoverMinWide.value - EPSILON)
        val portrait = pictureIn(slot)
        val collar = com.plumemu.coreui.components.CollarSize.Cover
        assertEquals("a portrait box fills the slot's height less its collar", slotHeight - collar.topPadding.value - collar.bottomPadding.value, portrait.height, EPSILON)
        assertEquals("at its own shape (PSP, 0.58 wide for one high)", 0.58f, portrait.width / portrait.height, ASPECT_SLACK)
        assertEquals("on the column's left edge, where the title starts", column.left.value + CollarSize.Cover.sidePadding.value, portrait.left, EPSILON)
        val titleTop = bounds(HomeTags.DETAILS_TITLE).top
        // The same game's box saved again much wider than the slot (1.6): shorter, the title stays.
        compose.runOnIdle { source.withLargeAspect("psp", PERSONA_3_PORTABLE, 1.6f) }
        compose.waitForIdle()
        val wide = pictureIn(bounds(HomeTags.DETAILS_COVER))
        assertEquals("the wide box at its shape", 1.6f, wide.width / wide.height, ASPECT_SLACK)
        assertEquals("in its collar on the slot's bottom", slot.bottom.value - collar.bottomPadding.value, wide.bottom, EPSILON)
        assertEquals("the title has not moved", titleTop, bounds(HomeTags.DETAILS_TITLE).top)
    }

    // The box's shape is stored when the focus lands (the shapes pass has run): the slot is as wide as the box, so the freed width is the text's.
    @Test
    @Config(qualifiers = NARROW)
    fun narrowDetailsGiveAPortraitBoxsFreedWidthToTheText() {
        show { RealCoverDetails("psp", PERSONA_3_PORTABLE, storedAspect = 0.58f) }
        val box = bounds(HomeTags.DETAILS_COVER)
        val height = (box.bottom - box.top).value
        assertEquals("the slot's full height", PlumDimens.detailsStopCoverHeight.value, height, EPSILON)
        assertEquals("the box's own shape", 0.58f, (box.right - box.left).value / height, ASPECT_SLACK)
        assertTrue("narrower than the full slot", (box.right - box.left) < PlumDimens.detailsStopCoverWidth)
        assertEquals("the box hugs the column's right edge and the freed width is the text's", bounds(HomeTags.DETAILS).right.value, box.right.value, EPSILON)
    }

    // Only the picture knows the shape: the slot has the system's shape from the first frame and the text keeps its width; the box fits inside the slot.
    @Test
    @Config(qualifiers = NARROW)
    fun narrowDetailsKeepTheTextWidthWhenOnlyThePictureKnowsItsShape() {
        show { RealCoverDetails("psp", PERSONA_3_PORTABLE) }
        val slot = bounds(HomeTags.DETAILS_COVER_SLOT)
        val box = bounds(HomeTags.DETAILS_COVER)
        assertEquals("the slot hugs the column's right edge", bounds(HomeTags.DETAILS).right.value, slot.right.value, EPSILON)
        assertEquals("the box's own shape", 0.58f, (box.right - box.left).value / (box.bottom - box.top).value, ASPECT_SLACK)
        assertTrue("the box lies inside its slot", box.left >= slot.left - EPSILON.dp && box.right <= slot.right + EPSILON.dp && box.bottom <= slot.bottom + EPSILON.dp)
        assertEquals("the box hugs the column's right edge as a stored one does", bounds(HomeTags.DETAILS).right.value, box.right.value, EPSILON)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun theListShowsTheYearsAndTheRegionWhereThereIsNoResult() {
        show { HomeWithArt(rememberSource { coverArtSource(absent = setOf("EarthBound")) }, BoardGamesFocused) }
        assertTrue("years from the results", compose.onAllNodes(hasText("1993", substring = true) and hasAnyAncestor(hasTestTag(HomeTags.GAMES)), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        val earthBound = snesRow(2)
        assertEquals("EarthBound has no result: its region", "EarthBound", earthBound.title)
        assertTrue("its region shows", compose.onAllNodesWithText(earthBound.region, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    @Config(qualifiers = WIDE)
    fun onlyTheRowsShownAreObservedAt10000Games() {
        val large = largeWithBoxArt().asLibrary()
        val source = FakeArtSource(emptyMap())
        lateinit var state: StripState
        show { HomeWithArt(source, listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX), Action.Enter), large, onState = { state = it }) }
        val rows = large.entries[FakeHomeContent.BOARD_SNES_INDEX].rows.size
        assertTrue("a system of more than three blocks", rows > 3 * HomeArt.ART_BLOCK)
        assertTrue("observed: blocks around the rows shown, not the system", source.observed.isNotEmpty() && source.observed.all { it.size <= 3 * HomeArt.ART_BLOCK })
        val before = source.observed.size
        repeat(10) { compose.runOnIdle { state.dispatch(Action.Move(Direction.Down)) } }
        compose.waitForIdle()
        assertEquals("ten moves inside a block observe nothing new", before, source.observed.size)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun withoutAnAccountNothingIsObserved() {
        val source = FakeArtSource(emptyMap())
        show { HomeWithArt(source, BoardGamesFocused, FakeHomeContent.board().asLibrary()) }
        assertEquals("no box art, no reads", emptyList<List<String>>(), source.observed)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun theShownSystemIsReportedForTheRuns() {
        val shown = mutableListOf<String?>()
        lateinit var state: StripState
        show {
            HomeWithArt(rememberSource { coverArtSource() }, emptyList(), onState = { state = it })
            FollowShownSystem(CoverArtLibrary, state) { shown += it }
        }
        compose.runOnIdle { state.dispatch(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX)) }
        compose.waitForIdle()
        compose.runOnIdle { state.dispatch(Action.TapItem(Column.Systems, 0)) }
        compose.waitForIdle()
        assertEquals("Recent first (no system), then Super Nintendo, then Recent again", listOf(null, "snes", null), shown)
    }

    private fun pictureIn(slot: DpRect): Rect = pictures().firstOrNull()?.second ?: run {
        val density = compose.density.density
        val node = compose.onAllNodes(hasTestTag(HomeTags.COVER_PICTURE), useUnmergedTree = true).fetchSemanticsNodes().first { n ->
            n.boundsInRoot.left / density >= slot.left.value - EPSILON && n.boundsInRoot.top / density >= slot.top.value - EPSILON
        }
        node.boundsInRoot.let { Rect(it.left / density, it.top / density, it.right / density, it.bottom / density) }
    }

    private fun snesRow(index: Int) = FakeHomeContent.withArtwork().entries[FakeHomeContent.BOARD_SNES_INDEX].rows[index]

    private companion object {
        const val EPSILON = 0.6f
        const val ASPECT_SLACK = 0.02f
        const val SETTLE_MS = 400L
        const val DONKEY_KONG_COUNTRY_2 = 1
        const val WIDE = "w768dp-h432dp-land-xhdpi"
        const val NARROW = "w576dp-h432dp-land-xhdpi"
    }
}

/** [FakeHomeContent.large] with box art set up (the grid and the details column). */
internal fun largeWithBoxArt(totalGames: Int = 10_000): HomeContent {
    val large = FakeHomeContent.large(totalGames)
    val systems = large.entries.filter { it.kind == EntryKind.System }.map { SystemGames(it.id, it.name.orEmpty(), it.rows, it.coverShape) }
    val fixed = { kind: EntryKind -> large.entries.first { it.kind == kind }.rows }
    return HomeContent.of(fixed(EntryKind.Recent), fixed(EntryKind.Favorites), fixed(EntryKind.Apps), systems, hasBoxArt = true)
}
