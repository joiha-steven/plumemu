package com.plumemu.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.plumemu.R
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The empty state is text and buttons: nothing stands above its title (no mark or picture). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h480dp-land-xhdpi")
class HomeEmptyNoMarkTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun stackTopOverTitle(
        why: EmptyLibrary,
        titleRes: Int,
    ): Float {
        val notices = HomeNotices(emptyLibrary = MutableStateFlow(why))
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { CompositionLocalProvider(LocalHomeNotices provides notices) { HomeAt(FakeHomeContent.noGames()) } } }
        compose.waitForIdle()
        val title = compose.onNode(hasText(ApplicationProvider.getApplicationContext<android.content.Context>().getString(titleRes)), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val stack = compose.onNodeWithTag(HomeTags.EMPTY_MESSAGE, useUnmergedTree = true).getUnclippedBoundsInRoot()
        return title.top.value - stack.top.value
    }

    @Test
    fun noFolderStartsWithItsTitle() = assertEquals("dp between the stack's top and the title", 0f, stackTopOverTitle(EmptyLibrary.NoFolder, R.string.home_empty_title), 0.5f)

    @Test
    fun noGamesStartsWithItsTitle() = assertEquals("dp between the stack's top and the title", 0f, stackTopOverTitle(EmptyLibrary.NoGames, R.string.home_nogames_title), 0.5f)
}
