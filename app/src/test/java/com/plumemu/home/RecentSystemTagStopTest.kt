package com.plumemu.home

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.plumemu.coreinput.Column as CoreColumn

/**
 * Recent, Favorites and Search rows show the system's short name at every stop; only the narrow
 * details stop (list slid out to 56 dp) swaps it for the year or region (docs/design/rules.md,
 * right-hand column). On a wide screen the details sit beside the list at full width, so focus on
 * them must not hide the tag (AYN Thor).
 */
abstract class RecentSystemTagStopBase {
    protected val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val toGames = listOf(Action.TapItem(CoreColumn.Systems, 0), Action.Enter)
    private val toDetails = toGames + Action.Move(Direction.Right)

    /** How many "GBA" tags Recent's second row (Pokemon Emerald) shows. */
    private fun tagCount(
        actions: List<Action>,
        withBoxArt: Boolean,
    ): Int {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) { HomeAt(FakeHomeContent.mixedSystems(withBoxArt), actions) }
        }
        compose.waitForIdle()
        // The row must be there, or a count of 0 would pass at the details stop without a hidden tag.
        compose.onNodeWithTag(HomeTags.game(1), useUnmergedTree = true).assertExists()
        return compose.onAllNodes(hasText("GBA") and hasAnyAncestor(hasTestTag(HomeTags.game(1))), useUnmergedTree = true).fetchSemanticsNodes().size
    }

    protected fun gamesFocusedShowsTags(withBoxArt: Boolean) {
        check(tagCount(toGames, withBoxArt) == 1) { "games focused, boxArt=$withBoxArt: system tag missing" }
    }

    protected fun detailsFocusedShowsTags(
        withBoxArt: Boolean,
        expected: Int,
    ) {
        val n = tagCount(toDetails, withBoxArt)
        check(n == expected) { "details focused, boxArt=$withBoxArt: $n tag(s), expected $expected" }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-367dpi")
class RecentSystemTagStopThorTest : RecentSystemTagStopBase() {
    @Test fun gamesFocusedBoxArt() = gamesFocusedShowsTags(true)

    @Test fun detailsFocusedBoxArt() = detailsFocusedShowsTags(true, 1)

    @Test fun detailsFocusedNoBoxArt() = detailsFocusedShowsTags(false, 1)
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class RecentSystemTagStop768Test : RecentSystemTagStopBase() {
    @Test fun detailsFocusedBoxArt() = detailsFocusedShowsTags(true, 1)
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class RecentSystemTagStop576Test : RecentSystemTagStopBase() {
    @Test fun gamesFocusedBoxArt() = gamesFocusedShowsTags(true)

    @Test fun detailsStopHidesTheTag() = detailsFocusedShowsTags(true, 0)
}
