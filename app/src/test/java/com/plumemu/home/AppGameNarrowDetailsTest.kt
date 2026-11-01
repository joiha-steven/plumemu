package com.plumemu.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The narrow details stop (576 wide), signed in: a game that has no picture shows its poster there (it showed an empty box),
 * and an app game shows its Android icon.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class AppGameNarrowDetailsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    /** Ports with a file game that has no scrape key (no picture for good) and the app game after it. */
    private fun show(index: Int) {
        val rows = listOf(PortsRows[0].copy(scrapeKey = null), PortsRows[2])
        val content = HomeContent.of(emptyList(), emptyList(), emptyList(), listOf(SystemGames("ports", "Ports", rows)), hasBoxArt = true, showApps = false)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Default) {
                CompositionLocalProvider(LocalHomeAppIcons provides FakeAppIcons(missing = emptySet())) {
                    HomeAt(content, gridActions(2, index) + Action.Move(Direction.Right))
                }
            }
        }
        compose.waitForIdle()
    }

    private fun inDetailsCover(tag: String) =
        compose.onAllNodes(hasTestTag(tag) and hasAnyAncestor(hasTestTag(HomeTags.DETAILS_COVER)), useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun aGameWithNoPictureShowsItsPosterAtTheDetailsStop() {
        show(0)
        assertEquals(1, inDetailsCover(HomeTags.POSTER_COVER))
    }

    @Test
    fun anAppGameShowsItsIconAtTheDetailsStop() {
        show(1)
        assertEquals(1, inDetailsCover(HomeTags.APP_GAME_ICON))
        assertEquals(0, inDetailsCover(HomeTags.POSTER_COVER))
    }
}
