package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.textsize.FitAllowance
import com.plumemu.textsize.findTagged
import com.plumemu.textsize.textProblems
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Favorites with no game yet: the games area is not blank, one muted line says how to add one
 * (the favorite key, Y), whole and inside the right margin, at Large and Largest, 768x432 and 576x432. Other entries with games show no such line.
 */
abstract class HomeEmptyFavoritesBase(
    private val widthDp: Int,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var size by mutableStateOf(TextSize.Default)
    private var shown = false

    /** Shows the main screen once with [entry] of the system column focused; a later call only changes the text size. */
    private fun show(
        content: HomeContent,
        entry: Int,
        each: TextSize,
    ) {
        size = each
        if (!shown) {
            shown = true
            compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) { HomeAt(content, listOf(Action.TapItem(Column.Systems, entry))) } }
        }
        compose.waitForIdle()
    }

    @Test
    fun emptyFavoritesNamesTheKeyThatAddsOne() {
        for (size in listOf(TextSize.Default, TextSize.Largest)) {
            show(FakeHomeContent.noFavorites(), entry = 1, each = size)
            val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
            val line = findTagged(root, HomeTags.EMPTY_FAVORITES)
            assertTrue("${size.name} ${widthDp}: no empty-Favorites line", line != null)
            val text = line!!.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
            assertTrue("the line names the Y key: '$text'", text.contains("Y"))
            val density = root.boundsInRoot.width / widthDp
            assertTrue("the line is cut or past the right margin (${line.boundsInRoot}): ${textProblems(line, FitAllowance(isLargest = size == TextSize.Largest))}", textProblems(line, FitAllowance(isLargest = size == TextSize.Largest)).isEmpty())
            assertTrue("the line starts left of the list's start: ${line.boundsInRoot.left / density}", line.boundsInRoot.left / density >= 200f)
            assertTrue("the line runs into the right margin: ${line.boundsInRoot.right / density} of $widthDp", line.boundsInRoot.right / density <= widthDp - 24f + 0.5f)
        }
    }

    @Test
    fun anEntryWithGamesShowsNoSuchLine() {
        show(FakeHomeContent.board(), entry = 1, each = TextSize.Default)
        val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        assertTrue("Favorites with games shows the empty line", findTagged(root, HomeTags.EMPTY_FAVORITES) == null)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeEmptyFavoritesRp5Test : HomeEmptyFavoritesBase(768)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class HomeEmptyFavoritesNovaTest : HomeEmptyFavoritesBase(576)
