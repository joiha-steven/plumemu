package com.plumemu.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.R
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** An empty library asks for a game folder: A or a tap opens the folder picker. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeEmptyLibraryTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var folderAsked = 0

    private fun show(content: HomeContent, notices: HomeNotices = HomeNotices()) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            val router = remember { InputRouter(scope) }
            PlumTheme {
                CompositionLocalProvider(LocalHomeNotices provides notices) {
                    HomeScreen(content, router, HomeCallbacks(onAddFolder = { folderAsked++ }, notices = notices))
                }
            }
        }
        compose.waitForIdle()
    }

    private fun hasText(text: String) = compose.onAllNodesWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun withFoldersBeingReadTheMessageSaysSo() {
        show(FakeHomeContent.noGames(), HomeNotices(emptyLibrary = MutableStateFlow(EmptyLibrary.Scanning)))
        assertTrue(hasText("Reading your games"))
        assertFalse(hasText("No game folder yet"))
    }

    @Test
    fun withFoldersReadAndNoGamesTheMessageAsksToCheckTheFolder() {
        show(FakeHomeContent.noGames(), HomeNotices(emptyLibrary = MutableStateFlow(EmptyLibrary.NoGames)))
        assertTrue(hasText("No games found yet"))
        assertTrue(hasText("Check that the folder holds games."))
        assertFalse(hasText("No game folder yet"))
    }

    @Test
    fun aRefusedFolderAskSaysWhyInTheMessageNotInAPopUp() {
        show(FakeHomeContent.noGames(), HomeNotices(folderMessage = MutableStateFlow(R.string.setup_message_no_picker)))
        assertTrue(hasText("This device has no folder picker"))
        assertTrue("the ask itself stays", hasText("No game folder yet"))
    }

    @Test
    fun theHintOffersAddFolderAndItOpensThePicker() {
        show(FakeHomeContent.noGames())
        compose.onNodeWithTag(HomeTags.hint(Action.Enter), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(1, folderAsked)
    }

    @Test
    fun aTapOnTheMessageOpensThePicker() {
        show(FakeHomeContent.noGames())
        compose.onNodeWithTag(HomeTags.EMPTY_MESSAGE, useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(1, folderAsked)
    }

    @Test
    fun aLibraryWithGamesShowsNoMessageAndNeverAsksForAFolder() {
        show(FakeHomeContent.board())
        compose.onAllNodesWithTag(HomeTags.EMPTY_MESSAGE, useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithTag(HomeTags.hint(Action.Enter), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(0, folderAsked)
    }
}
