package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.testing.MediumText
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.BOX_ART_SEARCH_KEY
import com.plumemu.game.FixBoxArtState
import com.plumemu.game.FixBoxArtTags
import com.plumemu.game.FixNameState
import com.plumemu.game.FixNameTags
import com.plumemu.game.GameMenuEntryId
import com.plumemu.game.GamePanelTags
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.game.THUMBNAIL_SLOT
import com.plumemu.game.boxArtHitKey
import com.plumemu.game.sampleFacts
import com.plumemu.home.BoxArtPanelFixtures.game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Fix box art… measured, wide (768) and narrow (576): the field, Search and
 * every game's row share the panel's left and right edges (the field is the same as Fix name's);
 * Search is 40 high like Fix name's rows; each thumbnail has the same fixed slot whether its
 * picture is there, still coming or missing, and the slot does not move with the cursor; the game
 * menu with its seventh row keeps the focused row fully in the panel, Info included.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class FixBoxArtBoundsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun assertDp(
        what: String,
        expected: Dp,
        actual: Dp,
    ) = assertEquals(what, expected.value, actual.value, 0.5f)

    private fun show(panel: OpenPanel) = compose.setContent { PlumTheme(MediumText.Default) { BoxArtPanelFixtures.Screen(panel) } }

    private fun assertLayout() {
        val panel = bounds(GamePanelTags.PANEL)
        val field = bounds(FixBoxArtTags.FIELD)
        val search = bounds(GamePanelTags.row(BOX_ART_SEARCH_KEY))
        assertDp("field row 40 high, as Fix name's", 40.dp, field.bottom - field.top)
        assertDp("Search row 40 high", 40.dp, search.bottom - search.top)
        assertDp("field spans the panel", panel.right - panel.left, field.right - field.left)
        assertDp("Search on the field's left edge", field.left, search.left)
        assertDp("Search on the field's right edge", field.right, search.right)
        assertDp("Search one list gap (2) under the field", field.bottom + 2.dp, search.top)
        val first = bounds(GamePanelTags.row(boxArtHitKey(BoxArtPanelFixtures.portrait)))
        val second = bounds(GamePanelTags.row(boxArtHitKey(BoxArtPanelFixtures.landscape)))
        for ((what, row) in listOf("first game" to first, "second game" to second)) {
            assertDp("$what on the field's left edge", field.left, row.left)
            assertDp("$what on the field's right edge", field.right, row.right)
            assertTrue("$what inside the panel", row.right <= panel.right && row.left >= panel.left)
        }
        assertDp("first game one list gap under Search", search.bottom + 2.dp, first.top)
        assertTrue("the second game sits under the first one's note line", second.top > first.bottom)
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun wideRowsShareTheFieldsEdges() {
        show(BoxArtPanelFixtures.panel(BoxArtPanelFixtures.results()))
        assertLayout()
    }

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun narrowRowsShareTheFieldsEdges() {
        show(BoxArtPanelFixtures.panel(BoxArtPanelFixtures.results()))
        assertLayout()
    }

    /** The field is Fix name's field to the pixel: same row, same place, same width. */
    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun theFieldIsFixNamesField() {
        var panel by mutableStateOf(OpenPanel(game, PanelScreen.FixName(FixNameState("super metroid", finder = null))))
        compose.setContent { PlumTheme(MediumText.Default) { BoxArtPanelFixtures.Screen(panel) } }
        val fixName = bounds(FixNameTags.FIELD)
        panel = BoxArtPanelFixtures.panel(BoxArtPanelFixtures.results())
        compose.waitForIdle()
        val fixBoxArt = bounds(FixBoxArtTags.FIELD)
        assertEquals(fixName, fixBoxArt)
    }

    private fun assertSlots(state: FixBoxArtState) {
        val hits = BoxArtPanelFixtures.hits.take(3)
        val slots = hits.map { bounds(FixBoxArtTags.thumbnail(it.id)) }
        for ((hit, slot) in hits.zip(slots)) {
            val row = bounds(GamePanelTags.row(boxArtHitKey(hit)))
            assertDp("${hit.id}: slot as wide as the constant", THUMBNAIL_SLOT, slot.right - slot.left)
            assertDp("${hit.id}: slot as high as the constant", THUMBNAIL_SLOT, slot.bottom - slot.top)
            assertTrue("${hit.id}: slot inside its row", slot.left >= row.left && slot.right <= row.right && slot.top >= row.top && slot.bottom <= row.bottom)
        }
        assertEquals("every slot on one left edge", 1, slots.map { it.left.value }.distinct().size)
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun thumbnailSlotsAreFixedAndInsideTheirRowsWide() {
        val state = BoxArtPanelFixtures.results()
        show(BoxArtPanelFixtures.panel(state))
        assertSlots(state)
    }

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun thumbnailSlotsAreFixedAndInsideTheirRowsNarrow() {
        val state = BoxArtPanelFixtures.results()
        show(BoxArtPanelFixtures.panel(state))
        assertSlots(state)
    }

    /** A thumbnail that has not landed (or never will) leaves every row exactly where the landed one does. */
    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun aMissingOrLateThumbnailMovesNothing() {
        val state = BoxArtPanelFixtures.results()
        val landed = state.hits.associate { it.id to state.thumbnailOf(it.id) }
        val late = FixBoxArtState("super metroid", "Super Nintendo").also {
            it.searching("super metroid")
            it.showSearch("super metroid", com.plumemu.game.BoxArtSearchOutcome.Found(BoxArtPanelFixtures.hits))
        }
        var shown by mutableStateOf(late)
        compose.setContent { PlumTheme(MediumText.Default) { BoxArtPanelFixtures.Screen(BoxArtPanelFixtures.panel(shown)) } }
        val rowTags = BoxArtPanelFixtures.hits.take(3).map { GamePanelTags.row(boxArtHitKey(it)) }
        val before = rowTags.map(::bounds)
        val slotsBefore = BoxArtPanelFixtures.hits.take(3).map { bounds(FixBoxArtTags.thumbnail(it.id)) }
        assertTrue("the late state has no picture yet", late.hits.all { late.thumbnailOf(it.id) == null })
        late.hits.forEach { hit -> landed[hit.id]?.let { late.thumbnailReady(hit.id, it) } }
        compose.waitForIdle()
        assertEquals("rows stay put when the pictures land", before, rowTags.map(::bounds))
        assertEquals("slots stay put when the pictures land", slotsBefore, BoxArtPanelFixtures.hits.take(3).map { bounds(FixBoxArtTags.thumbnail(it.id)) })
    }

    /** The cursor moving from a game to the next (focus fill and dot) does not move a thumbnail. */
    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun theCursorDoesNotMoveThumbnails() {
        val state = BoxArtPanelFixtures.results()
        show(BoxArtPanelFixtures.panel(state))
        val before = BoxArtPanelFixtures.hits.take(3).map { bounds(FixBoxArtTags.thumbnail(it.id)) }
        repeat(2) { compose.runOnIdle { state.onAction(Action.Move(Direction.Down)) } }
        compose.waitForIdle()
        assertEquals(before, BoxArtPanelFixtures.hits.take(3).map { bounds(FixBoxArtTags.thumbnail(it.id)) })
    }

    private fun assertMenuRowVisible(focus: Int, entry: GameMenuEntryId) {
        val menu = OpenPanel(game, PanelScreen.Menu(sampleFacts(title = "Super Metroid", canFixBoxArt = true)), focus = focus)
        compose.setContent { PlumTheme(MediumText.Default) { BoxArtPanelFixtures.Screen(menu) } }
        val panel = bounds(GamePanelTags.PANEL)
        val row = bounds(GamePanelTags.row(entry.name))
        assertDp("${entry.name}: 40 high", 40.dp, row.bottom - row.top)
        assertTrue("${entry.name} fully inside the panel (${row.top}..${row.bottom} of ${panel.top}..${panel.bottom})", row.top >= panel.top && row.bottom <= panel.bottom)
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun sevenRowMenuShowsInfoWhenFocusedWide() = assertMenuRowVisible(6, GameMenuEntryId.Info)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun sevenRowMenuShowsInfoWhenFocusedNarrow() = assertMenuRowVisible(6, GameMenuEntryId.Info)

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun sevenRowMenuShowsFixBoxArtWhenFocusedWide() = assertMenuRowVisible(5, GameMenuEntryId.FixBoxArt)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun sevenRowMenuShowsPlayWhenFocusedNarrow() = assertMenuRowVisible(0, GameMenuEntryId.Play)
}
