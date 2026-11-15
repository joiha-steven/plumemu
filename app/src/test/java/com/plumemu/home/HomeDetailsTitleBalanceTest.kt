package com.plumemu.home

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.CoverShape
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A wrapped details title never leaves a single word on its last line ("Donkey Kong / Country 2", not "Donkey Kong Country / 2"). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeDetailsTitleBalanceTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun lines(title: String): List<String> {
        val rows = listOf(GameRowModel("snes-0", title, "USA", scrapeKey = artKey("snes-0"), systemAbbr = "SNES", systemId = "snes"))
        val board = HomeContent.of(emptyList(), emptyList(), emptyList(), listOf(SystemGames("snes", "Super Nintendo", rows, CoverShape(realAspect("snes", 0)), code = "SFC")), showApps = false)
        compose.setContent { PlumTheme(PlumThemeSettings.Default) { HomeAt(board, gridActions(POSTER_SNES_INDEX, 0)) } }
        compose.waitForIdle()
        var result: TextLayoutResult? = null
        compose.runOnIdle {
            val node = compose.onNodeWithTag(HomeTags.DETAILS_TITLE, useUnmergedTree = true).fetchSemanticsNode()
            val read = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
            val holder = mutableListOf<TextLayoutResult>()
            checkNotNull(read) { "no text layout action" }(holder)
            result = holder.firstOrNull()
        }
        val layout = checkNotNull(result)
        return (0 until layout.lineCount).map { layout.layoutInput.text.text.substring(layout.getLineStart(it), layout.getLineEnd(it)).trim() }
    }

    private fun assertBalanced(title: String) {
        val shown = lines(title)
        assertEquals("$title wraps to two lines: $shown", 2, shown.size)
        assertTrue("$title: the last line has more than one word: $shown", shown.last().contains(' '))
    }

    @Test
    fun donkeyKongCountry2DoesNotLeaveTheTwoAlone() = assertBalanced("Donkey Kong Country 2")

    @Test
    fun aNumberedTitleDoesNotLeaveTheNumberAlone() = assertBalanced("Super Nintendo title 15")
}
