package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.components.HINT_KEY_CAP_TAG
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.search.SearchIndex
import com.plumemu.home.search.SearchState
import com.plumemu.home.search.SearchTags
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Home's system list and games list and Search's results end [footerGapToKeys] above what the hint bar draws highest (the
 * top of its keys' collars), not above the bar's bounds, at Medium and Largest. A row cut by the list's own edge (the scroll
 * cue) is left out: only rows that end inside the list count.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeSearchBarGapTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private enum class Scene { Systems, Games, Search }

    private fun check(scene: Scene) {
        var size by mutableStateOf(TextSize.Small)
        var density = Density(1f)
        val content = FakeHomeContent.withFavorites()
        val search =
            SearchState().apply {
                open()
                onQueryChange("super")
                showResults("super", SearchIndex.of(content).find("super"))
                toResults()
            }
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) {
                density = LocalDensity.current
                when (scene) {
                    Scene.Systems -> HomeAt(content, BoardSystemsFocused)
                    Scene.Games -> HomeAt(content, BoardGamesFocused)
                    Scene.Search -> HomeAt(content, BoardGamesFocused, search = search)
                }
            }
        }
        val problems = mutableListOf<String>()
        for (each in listOf(TextSize.Small, TextSize.Largest)) {
            size = each
            compose.waitForIdle()
            val caps = compose.onAllNodesWithTag(HINT_KEY_CAP_TAG, useUnmergedTree = true).fetchSemanticsNodes()
            val keyTop = with(density) { caps.minOf { it.boundsInRoot.top }.toDp() } - (CollarSize.Hint.lip + CollarSize.Hint.groove)
            val listTag = if (scene == Scene.Search) SearchTags.RESULTS else HomeTags.STRIP
            val listBottom = compose.onNodeWithTag(listTag, useUnmergedTree = true).getUnclippedBoundsInRoot().bottom
            val tags = (0 until ROWS).map { if (scene == Scene.Systems) HomeTags.system(it) else if (scene == Scene.Games) HomeTags.game(it) else SearchTags.result(it) }
            var seen = 0
            for (tag in tags) {
                val node = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull() ?: continue
                seen++
                val bottom = with(density) { node.boundsInRoot.bottom.toDp() }
                val gap = keyTop - bottom
                if (bottom <= listBottom + 0.5.dp && gap < footerGapToKeys(each) - SLACK) problems += "$each $tag ends ${gap.value} dp above the keys"
            }
            // The list itself must stop that far above the keys, or rows that fit it would not.
            if (keyTop - listBottom < footerGapToKeys(each) - SLACK) problems += "$each list ends ${(keyTop - listBottom).value} dp above the keys"
            assertTrue("$scene $each: no rows found", seen > 0)
        }
        assertTrue("needs the footer gap less the collars' reach:\n${problems.joinToString("\n")}", problems.isEmpty())
    }

    private companion object {
        val SLACK = 0.5.dp
        const val ROWS = 30
    }

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun systemsAt832x468() = check(Scene.Systems)

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun gamesAt832x468() = check(Scene.Games)

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun searchAt832x468() = check(Scene.Search)

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun systemsAt768x432() = check(Scene.Systems)

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun gamesAt768x432() = check(Scene.Games)

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun searchAt768x432() = check(Scene.Search)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun systemsAt576x432() = check(Scene.Systems)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun gamesAt576x432() = check(Scene.Games)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun searchAt576x432() = check(Scene.Search)

    @Test
    @Config(qualifiers = "w640dp-h480dp-land-xhdpi")
    fun systemsAt640x480() = check(Scene.Systems)

    @Test
    @Config(qualifiers = "w640dp-h480dp-land-xhdpi")
    fun gamesAt640x480() = check(Scene.Games)

    @Test
    @Config(qualifiers = "w640dp-h480dp-land-xhdpi")
    fun searchAt640x480() = check(Scene.Search)
}
