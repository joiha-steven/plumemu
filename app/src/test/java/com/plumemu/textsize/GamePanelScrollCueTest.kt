package com.plumemu.textsize

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.game.GamePanelTags
import com.plumemu.game.GameRef
import com.plumemu.game.NameCertainty
import com.plumemu.game.NameFacts
import com.plumemu.game.NameOrigin
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.game.sampleFacts
import com.plumemu.home.BoardGamesFocused
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeAt
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A game panel list that does not fit shows it scrolls (design/rules.md › Lists that scroll): the row cut at the bottom edge fades out, and a fade is there
 * exactly while the list can still scroll that way. Checked for the game menu (six rows, seven from Recent) and for Info
 * with a three-line title, at Large to Largest on 768x432 and 576x432. The case that scrolls must exist, or the test proves nothing.
 */
abstract class GamePanelScrollCueBase {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun fades(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Whether the list tagged [scrolling] can still scroll [down] (null when it has no scroll). */
    private fun canScroll(
        scrolling: String,
        down: Boolean,
    ): Boolean? {
        val range = compose.onNodeWithTag(scrolling, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) ?: return null
        return if (down) range.value() < range.maxValue() else range.value() > 0f
    }

    private fun check(
        label: String,
        scrolling: String,
        screen: PanelScreen,
        sizes: List<TextSize>,
    ): Int {
        var size by mutableStateOf(sizes.first())
        val panel = OpenPanel(GameRef("snes-6", "Super Metroid"), screen)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) { HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = panel) } }
        val problems = mutableListOf<String>()
        var scrollingCases = 0
        for (each in sizes) {
            size = each
            compose.waitForIdle()
            val down = canScroll(scrolling, down = true)
            val up = canScroll(scrolling, down = false)
            if (down == true) scrollingCases++
            if (down != null && fades(GamePanelTags.FADE_BOTTOM) != down) problems += "$label ${each.name}: can scroll down = $down but bottom fade is ${fades(GamePanelTags.FADE_BOTTOM)}"
            if (up != null && fades(GamePanelTags.FADE_TOP) != up) problems += "$label ${each.name}: can scroll up = $up but top fade is ${fades(GamePanelTags.FADE_TOP)}"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
        return scrollingCases
    }

    protected fun plainMenuShowsItScrolls() {
        val cases = check("menu", GamePanelTags.ROWS, PanelScreen.Menu(sampleFacts()), SIZES)
        assertTrue("the menu scrolls at some size (Largest)", cases > 0)
    }

    protected fun recentMenuShowsItScrolls() {
        val cases = check("recent menu", GamePanelTags.ROWS, PanelScreen.Menu(sampleFacts(canRemoveFromRecent = true)), SIZES)
        assertTrue("the Recent menu scrolls at some size (Largest)", cases > 0)
    }

    protected fun longInfoShowsItScrolls() {
        val facts =
            sampleFacts(title = LONG_TITLE, lastPlayedAt = 1_700_000_000_000L, name = NameFacts(NameOrigin.GameCode, NameCertainty.Sure, "Nintendo - Super Nintendo Entertainment System", hasFix = false))
                .copy(fileName = "Super Metroid.sfc")
        val cases = check("info", GamePanelTags.FACTS_SCROLL, PanelScreen.Info(facts), SIZES)
        assertTrue("Info with a three-line title scrolls at some size", cases > 0)
    }

    private companion object {
        val SIZES = listOf(TextSize.Large, TextSize.Huge, TextSize.Largest)
        const val LONG_TITLE = "The Extraordinarily Long Adventure of a Hero Whose Name Never Seems to End in One or Even Two Lines (Disc 1 of 2)"
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class GamePanelScrollCue768Test : GamePanelScrollCueBase() {
    @Test fun theMenuShowsItScrolls() = plainMenuShowsItScrolls()

    @Test fun theRecentMenuShowsItScrolls() = recentMenuShowsItScrolls()

    @Test fun infoWithALongNameShowsItScrolls() = longInfoShowsItScrolls()
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class GamePanelScrollCue576Test : GamePanelScrollCueBase() {
    @Test fun theMenuShowsItScrolls() = plainMenuShowsItScrolls()

    @Test fun theRecentMenuShowsItScrolls() = recentMenuShowsItScrolls()

    @Test fun infoWithALongNameShowsItScrolls() = longInfoShowsItScrolls()
}
