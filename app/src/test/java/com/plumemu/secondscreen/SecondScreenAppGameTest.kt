package com.plumemu.secondscreen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.FakeAppIcons
import com.plumemu.home.HomeTags
import com.plumemu.home.LocalHomeAppIcons
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Layout C for an app game: its Android icon stands in the cover slot, not the poster; a missing icon shows the letter stand-in. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w538dp-h468dp-land-xhdpi")
class SecondScreenAppGameTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun show(game: SecondScreenContent.Game, icons: FakeAppIcons) {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Default) { CompositionLocalProvider(LocalHomeAppIcons provides icons) { SecondScreenCard(game) } }
        }
        compose.waitForIdle()
    }

    private fun count(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private val appGame = POSTER_GAME.copy(title = "Sky Racer", appPackage = "com.example.sky")

    @Test
    fun theIconStandsWhereThePosterWould() {
        show(appGame, FakeAppIcons(missing = emptySet()))
        assertEquals(1, count(SecondScreenTags.DETAILS))
        assertEquals(0, count(HomeTags.POSTER_COVER))
        compose.onAllNodes(hasTestTag(HomeTags.APP_GAME_ICON) and hasAnyAncestor(hasTestTag(SecondScreenTags.COVER)), useUnmergedTree = true).fetchSemanticsNodes().size.let { assertEquals(1, it) }
    }

    @Test
    fun anAppWithoutAnIconShowsTheLetter() {
        show(appGame, FakeAppIcons(missing = setOf("com.example.sky")))
        assertEquals(0, count(HomeTags.APP_GAME_ICON))
        assertEquals(1, count(HomeTags.APP_ICON_STAND_IN))
        assertEquals(0, count(HomeTags.POSTER_COVER))
    }

    @Test
    fun aFileGameKeepsItsPoster() {
        show(POSTER_GAME, FakeAppIcons(missing = emptySet()))
        assertEquals(1, count(HomeTags.POSTER_COVER))
    }
}
