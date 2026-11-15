package com.plumemu.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A game never played says so once, "Not played yet", where it used to say "Played 0 min" and "Last played Never"; a played game keeps
 * both cells. The wide column's cells ([DetailsBlock]) and the text table of the no-art and narrow columns ([DetailsFacts]).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeDetailsNeverPlayedTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val never = PlayFacts(lastPlayedDaysAgo = null, playTimeMinutes = 0, emulator = "RetroArch")
    private val played = PlayFacts(lastPlayedDaysAgo = 2, playTimeMinutes = 252, emulator = "RetroArch")

    private fun shown(content: @Composable ColumnScope.() -> Unit): List<String> {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                Column(Modifier.width(PlumDimens.detailsColumnWidth).testTag(PANEL)) { content() }
            }
        }
        compose.waitForIdle()
        return texts(compose.onNodeWithTag(PANEL, useUnmergedTree = true).fetchSemanticsNode())
    }

    private fun texts(node: SemanticsNode): List<String> =
        (if (SemanticsProperties.Text in node.config && node.layoutInfo.isPlaced) listOf(node.config[SemanticsProperties.Text].joinToString("")) else emptyList()) +
            node.children.flatMap(::texts)

    private fun assertOnePlayLine(texts: List<String>) {
        assertEquals("one Not played yet: $texts", 1, texts.count { it == "Not played yet" })
        assertEquals("one Played label: $texts", 1, texts.count { it == "Played" })
        assertTrue("no Last played cell: $texts", "Last played" !in texts)
        assertTrue("no Never: $texts", "Never" !in texts)
        assertTrue("no 0 min: $texts", texts.none { it.contains("0 min") || it.contains("0 min") })
        assertTrue("the emulator stays: $texts", "RetroArch" in texts)
    }

    @Test
    fun theWideCellsHaveOnePlayCellForANeverPlayedGame() = assertOnePlayLine(shown { DetailsBlock(null, ShownPlay.Fixed(never)) })

    @Test
    fun theWideCellsOfAPlayedGameKeepBoth() {
        val texts = shown { DetailsBlock(null, ShownPlay.Fixed(played)) }
        assertTrue("Played and Last played: $texts", "Played" in texts && "Last played" in texts)
        assertFalse("not 'Not played yet': $texts", "Not played yet" in texts)
    }

    @Test
    fun theTextTableHasOnePlayLineForANeverPlayedGame() = assertOnePlayLine(shown { DetailsFacts(ShownPlay.Fixed(never), PlumTheme.type.smallFact) })

    @Test
    fun theTextTableOfAPlayedGameKeepsBothLines() {
        val texts = shown { DetailsFacts(ShownPlay.Fixed(played), PlumTheme.type.smallFact) }
        assertTrue("Played and Last played: $texts", "Played" in texts && "Last played" in texts)
        assertFalse("not 'Not played yet': $texts", "Not played yet" in texts)
    }

    /** The table is as high for a never-played game as for a played one, so nothing under it moves. */
    @Test
    fun theTextTableKeepsItsHeight() {
        shown {
            Box(Modifier.testTag("never")) { DetailsFacts(ShownPlay.Fixed(never), PlumTheme.type.smallFact) }
            Box(Modifier.testTag("played")) { DetailsFacts(ShownPlay.Fixed(played), PlumTheme.type.smallFact) }
        }
        val neverHeight = compose.onNodeWithTag("never", useUnmergedTree = true).fetchSemanticsNode().size.height
        val playedHeight = compose.onNodeWithTag("played", useUnmergedTree = true).fetchSemanticsNode().size.height
        assertEquals("the same height", playedHeight, neverHeight)
    }

    private companion object {
        const val PANEL = "never_played_panel"
    }
}
