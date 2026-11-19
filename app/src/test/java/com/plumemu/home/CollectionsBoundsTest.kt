package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onRoot
import com.plumemu.coreinput.Action
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.textsize.FitAllowance
import com.plumemu.textsize.textProblems
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Collections screens measured, not looked at: no text cut, clipped or overlapped, and nothing past the right margin
 * (24 dp) except a list under its fade, in Graphite and Paper at the two reference sizes.
 */
abstract class CollectionsBoundsBase(
    private val widthDp: Int,
) {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun measure(
        what: String,
        actions: List<Action>,
        grid: Boolean = false,
        content: HomeContent = CollectionsShotsBase.content(),
    ) {
        var settings by mutableStateOf(PlumThemeSettings.Graphite)
        compose.setContent { PlumTheme(settings) { HomeAt(content, actions, gridIds = if (grid) CollectionsShotsBase.gridIds else emptySet()) } }
        for (each in listOf(PlumThemeSettings.Graphite, PlumThemeSettings.Paper)) {
            settings = each
            compose.waitForIdle()
            val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
            val allow = FitAllowance(setOf(HomeTags.GAMES, HomeTags.GRID), isLargest = false, stripTags = setOf(HomeTags.SYSTEMS, HomeTags.GAMES), barTags = setOf(HomeTags.TOP_BAR, HomeTags.HINT_BAR))
            val problems = textProblems(root, allow)
            assertTrue("$what $widthDp ${each.background}: $problems", problems.isEmpty())
        }
    }

    @Test fun column() = measure("column", CollectionsShotsBase.column)

    @Test fun folders() = measure("folders", CollectionsShotsBase.folders)

    @Test fun folderGrid() = measure("grid", CollectionsShotsBase.rpg, grid = true)

    @Test fun folderList() = measure("list", CollectionsShotsBase.rpg)

    @Test fun emptyOwn() = measure("empty own", CollectionsShotsBase.emptyOwn)

    @Test fun emptyMostPlayed() = measure("empty most played", CollectionsShotsBase.mostPlayedOpen, content = CollectionsShotsBase.content(mostPlayed = 0))
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class CollectionsBoundsRp5Test : CollectionsBoundsBase(768)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class CollectionsBoundsNovaTest : CollectionsBoundsBase(576)
