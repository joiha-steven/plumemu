package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import com.plumemu.coreui.testing.ReferenceShots
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

/**
 * Installed apps listed as games draw their Android icon, not a poster: Android games as the Apps grid's tiles, a Ports grid
 * with files draws the same tile in the app games' cells, and the details column shows the icon. A missing or failing icon
 * shows the round letter stand-in; nothing throws.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class AppGameIconsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { PlumTheme(PlumThemeSettings.Default) { content() } }
        compose.waitForIdle()
    }

    private fun count(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

    private fun inCell(tag: String, index: Int) =
        compose.onAllNodes(hasTestTag(tag) and hasAnyAncestor(hasTestTag(HomeTags.cover(index))), useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun androidGamesGridDrawsEveryGameAsAnAppsTile() {
        val icons = FakeAppIcons(missing = setOf(gamePackage(3)))
        show { AppGamesBoard(ANDROID_GAMES_ENTRY, icons = icons) }
        assertEquals("no poster, no plain cover tile", 0, count(HomeTags.POSTER_COVER) + count(HomeTags.WIDE_TILE_TITLE) + count(HomeTags.PORTRAIT_TILE_TITLE))
        assertTrue("icons are drawn", count(HomeTags.APP_ICON) > 0)
        assertTrue("the icon of a game is asked by its package", icons.loads.all { it.startsWith("com.example.game") })
        assertEquals("the app without an icon shows the stand-in", 1, count(HomeTags.APP_ICON_STAND_IN))
    }

    @Test
    fun portsGridDrawsACoverCardInAppGameCellsAndAPosterInFileCells() {
        show { AppGamesBoard(PORTS_ENTRY) }
        assertEquals("a file game keeps its poster", 1, inCell(HomeTags.POSTER_COVER, 0))
        assertEquals("an app game has no poster", 0, inCell(HomeTags.POSTER_COVER, 2))
        assertEquals("an app game draws its icon", 1, inCell(HomeTags.APP_ICON, 2))
        assertEquals(1, inCell(HomeTags.APP_ICON, 5))
    }

    @Test
    fun detailsColumnOfAnAppGameShowsItsIconNotAPoster() {
        show { AppGamesBoard(ANDROID_GAMES_ENTRY, focused = 1, grid = false) }
        assertEquals(1, compose.onAllNodes(hasTestTag(HomeTags.APP_GAME_ICON) and hasAnyAncestor(hasTestTag(HomeTags.DETAILS_COVER)), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals("no poster in the details column", 0, count(HomeTags.POSTER_COVER))
    }

    @Test
    fun detailsColumnOfAFileGameKeepsItsPoster() {
        show { AppGamesBoard(PORTS_ENTRY, focused = 0, grid = false) }
        assertEquals(1, count(HomeTags.POSTER_COVER))
        assertEquals(0, count(HomeTags.APP_GAME_ICON))
    }

    @Test
    fun anIconThatIsNotFoundShowsTheStandInInTheDetailsColumn() {
        show { AppGamesBoard(ANDROID_GAMES_ENTRY, focused = 3, grid = false) }
        assertEquals(0, count(HomeTags.APP_GAME_ICON))
        assertEquals(1, count(HomeTags.APP_ICON_STAND_IN))
    }

    @Test
    fun aLookupThatThrowsShowsStandInsAndNothingCrashes() {
        show { AppGamesBoard(ANDROID_GAMES_ENTRY, icons = ThrowingAppIcons()) }
        assertEquals(0, count(HomeTags.APP_ICON))
        assertTrue("tiles keep their stand-in", count(HomeTags.APP_ICON_STAND_IN) > 0)
    }
}
