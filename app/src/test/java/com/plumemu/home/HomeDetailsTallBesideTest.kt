package com.plumemu.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.testing.TableText
import com.plumemu.coreui.theme.CoverShape
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A tall box (under 0.85 wide for one high) has the title beside it, top-aligned with the box; a wide one keeps the title under it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeDetailsTallBesideTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun show(aspect: Float) {
        val rows = listOf(GameRowModel("5", "Persona 3 Portable", "USA", scrapeKey = artKey("5"), systemAbbr = "PSP", systemId = "psp"))
        val board = HomeContent.of(emptyList(), emptyList(), emptyList(), listOf(SystemGames("psp", "PlayStation Portable", rows, CoverShape(aspect), code = "PSP")), showApps = false)
        compose.setContent { PlumTheme(TableText.Default) { CompositionLocalProvider(LocalHomePlayFacts provides FakePlayFacts()) { HomeAt(board, gridActions(POSTER_SNES_INDEX, 0)) } } }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
    }

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    @Test
    fun aTallPosterHasTheTitleBesideItAtTheBoxsTop() {
        show(0.72f)
        val box = bounds(HomeTags.DETAILS_COVER_BOX)
        val title = bounds(HomeTags.DETAILS_TITLE)
        val facts = bounds(HomeTags.DETAILS_FACTS)
        assertTrue("the title is right of the box: $title vs $box", title.left >= box.right)
        assertEquals("the title's top is the box's top", box.top.value, title.top.value, 0.5f)
        assertTrue("the facts are under the band: $facts vs $box", facts.top >= box.bottom)
    }

    @Test
    fun aWidePosterKeepsTheTitleUnderIt() {
        show(1.4f)
        val box = bounds(HomeTags.DETAILS_COVER_BOX)
        assertTrue("the title is under the box", bounds(HomeTags.DETAILS_TITLE).top >= box.bottom)
    }
}
