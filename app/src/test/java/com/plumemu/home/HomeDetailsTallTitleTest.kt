package com.plumemu.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.testing.TableText
import com.plumemu.coreui.theme.CoverShape
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The title beside a tall box is never clipped: it stands beside the box only when it fits whole there (no ellipsis, no break inside a word), else
 * it stays under the box as for a wide one. And what happens while the picture is still unknown (the system's shape, then the picture's).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class TallTitleCases {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun show(
        title: String,
        aspect: Float,
    ) {
        val rows = listOf(GameRowModel("5", title, "USA", scrapeKey = artKey("5"), systemAbbr = "NES", systemId = "nes"))
        val board = HomeContent.of(emptyList(), emptyList(), emptyList(), listOf(SystemGames("nes", "Nintendo Entertainment System", rows, CoverShape(aspect), code = "NES")), showApps = false)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Default.copy(textSize = TextSize.entries.last())) {
                CompositionLocalProvider(LocalHomePlayFacts provides FakePlayFacts()) { HomeAt(board, gridActions(POSTER_SNES_INDEX, 0)) }
            }
        }
        compose.waitForIdle()
    }

    private fun assertWhole(
        title: String,
        aspect: Float,
    ) {
        show(title, aspect)
        val node = compose.onNodeWithTag(HomeTags.DETAILS_TITLE, useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.first()
        for (line in 0 until layout.lineCount) {
            val end = layout.getLineEnd(line)
            assertTrue("'$title' box $aspect: line $line is not ellipsised", !layout.isLineEllipsized(line))
            assertTrue("'$title' box $aspect: line $line does not break inside a word (ends at $end)", end >= title.length || title[end - 1].isWhitespace() || title[end - 1] == '-' || title[end].isWhitespace())
        }
        assertEquals("'$title' box $aspect: the node holds every line", layout.getLineBottom(layout.lineCount - 1), node.size.height.toFloat(), 1f)
    }

    @Test
    fun castlevaniaBesideATallBox() = assertWhole("Castlevania III: Dracula's Curse", 0.72f)

    @Test
    fun castlevaniaBesideTheLimitBox() = assertWhole("Castlevania III: Dracula's Curse", 0.84f)
}

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeDetailsTallTitle768Test : TallTitleCases()

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-xhdpi")
class HomeDetailsTallTitle837Test : TallTitleCases()

/** Box art: the layout follows the stored shape, else the system's, until the picture lands (it is the picture's once it has). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeDetailsTallPictureTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun besideTheBox(): Boolean {
        val box = compose.onNodeWithTag(HomeTags.DETAILS_COVER_BOX, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val title = compose.onNodeWithTag(HomeTags.DETAILS_TITLE, useUnmergedTree = true).getUnclippedBoundsInRoot()
        return title.left >= box.right && title.top.value < box.bottom.value
    }

    @Test
    fun aPictureThatLandsTallOnAWideSystemMovesTheTitleBesideIt() {
        val source = realCoverSource()
        compose.setContent { PlumTheme(TableText.Default) { RealCoverDetails("snes", 4, source) } }
        compose.waitForIdle()
        val before = besideTheBox()
        compose.runOnIdle { source.withLargeAspect("snes", 4, 0.6f) }
        compose.waitForIdle()
        // Known cost: with no stored shape the first frame follows the system's, so a picture of the other class moves the title once.
        assertEquals("wide system: under at first", false, before)
        assertTrue("then beside the tall picture", besideTheBox())
    }
}
