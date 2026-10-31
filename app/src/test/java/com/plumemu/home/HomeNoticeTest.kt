package com.plumemu.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.components.SystemRowTags
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.graph.LibraryNotice
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The home's quiet news after a change check: the top-left text says what changed
 * for a few seconds, then reads as usual again; a dot marks a system that got games until it is opened.
 * Nothing takes focus and no pop-up appears.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeNoticeTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val content = FakeHomeContent.board()
    private val notice = MutableStateFlow<LibraryNotice?>(null)
    private val dots = MutableStateFlow<Set<String>>(emptySet())
    private val shown = mutableListOf<LibraryNotice>()
    private val opened = mutableListOf<String>()
    private val firstSystem get() = content.entries.first { it.kind == EntryKind.System }

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val scope = rememberCoroutineScope()
            val router = remember { InputRouter(scope) }
            val notices = HomeNotices(notice, dots, { shown += it; notice.value = null }, { opened += it })
            PlumTheme { CompositionLocalProvider(LocalHomeNotices provides notices) { HomeScreen(content, router, HomeCallbacks()) } }
        }
        compose.mainClock.advanceTimeBy(50)
    }

    private fun post(notice: LibraryNotice) {
        this.notice.value = notice
        compose.mainClock.advanceTimeBy(400)
    }

    private fun usual() = "${content.systemCount} systems"

    @Test
    fun withNoNoticeTheHeaderReadsAsUsual() {
        show()
        compose.onNodeWithText(usual(), substring = true, useUnmergedTree = true).assertExists()
    }

    @Test
    fun newGamesInOneSystemNameItThenTheHeaderFadesBack() {
        show()
        post(LibraryNotice(1, added = 24, removed = 0, addedSystems = listOf(firstSystem.id)))
        compose.onNodeWithText("+24 games · ${firstSystem.name}", substring = true, useUnmergedTree = true).assertExists()
        compose.mainClock.advanceTimeBy(NOTICE_SHOWN_MS + 600)
        compose.onNodeWithText(usual(), substring = true, useUnmergedTree = true).assertExists()
        assertEquals("the graph is told it was shown, so the screen never shows it twice", 1, shown.size)
    }

    @Test
    fun turningAnimationsOffWhileANoticeShowsMakesItsFadeBackInstant() {
        var animations by mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val scope = rememberCoroutineScope()
            val router = remember { InputRouter(scope) }
            val notices = HomeNotices(notice, dots, { shown += it; notice.value = null }, { opened += it })
            PlumTheme(PlumThemeSettings(animationsOn = animations)) {
                CompositionLocalProvider(LocalHomeNotices provides notices) { HomeScreen(content, router, HomeCallbacks()) }
            }
        }
        compose.mainClock.advanceTimeBy(50)
        post(LibraryNotice(1, added = 24, removed = 0, addedSystems = listOf(firstSystem.id)))
        animations = false
        // The notice fades in 110 ms, stays 4 s, then fades out and the usual text fades in (110 ms each with
        // motion on, about 4.33 s in all); with motion off by then there is no fade, so it is back at about 4.0 s.
        compose.mainClock.advanceTimeBy(NOTICE_SHOWN_MS - 100)
        compose.onNodeWithText(usual(), substring = true, useUnmergedTree = true).assertExists()
    }

    @Test
    fun severalSystemsSayHowMany() {
        show()
        val ids = content.entries.filter { it.kind == EntryKind.System }.take(3).map { it.id }
        post(LibraryNotice(1, added = 24, removed = 0, addedSystems = ids))
        compose.onNodeWithText("+24 games · 3 systems", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test
    fun removalsShowOnlyTheCount() {
        show()
        post(LibraryNotice(1, added = 0, removed = 3, addedSystems = emptyList()))
        compose.onNodeWithText("−3 games", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test
    fun oneNewGameIsSingular() {
        show()
        post(LibraryNotice(1, added = 1, removed = 0, addedSystems = listOf(firstSystem.id)))
        compose.onNodeWithText("+1 game ·", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test
    fun theNoticeLeavesTheScreenInPlace() {
        show()
        post(LibraryNotice(1, added = 5, removed = 0, addedSystems = listOf(firstSystem.id)))
        compose.onAllNodesWithTag(HomeTags.GAMES, useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithTag(HomeTags.TOP_BAR, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aNoticeForTheSystemAlreadyOpenSetsNoDot() {
        show()
        val row = HomeTags.system(content.firstSystemIndex)
        compose.onNodeWithTag(row, useUnmergedTree = true).performTouchInput { click() }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag(row, useUnmergedTree = true).performTouchInput { click() }
        compose.mainClock.advanceTimeBy(400)
        opened.clear()
        dots.value = setOf(firstSystem.id)
        compose.mainClock.advanceTimeBy(200)
        assertEquals("its games are in front, so the dot is cleared at once", listOf(firstSystem.id), opened.distinct())
    }

    @Test
    fun aSystemWithNewGamesShowsADotAndOpeningItClearsIt() {
        show()
        compose.onAllNodesWithTag(SystemRowTags.NEW_DOT, useUnmergedTree = true).assertCountEquals(0)
        dots.value = setOf(firstSystem.id)
        compose.mainClock.advanceTimeBy(100)
        val row = HomeTags.system(content.firstSystemIndex)
        compose.onAllNodes(hasTestTag(SystemRowTags.NEW_DOT) and hasAnyAncestor(hasTestTag(row)), useUnmergedTree = true).assertCountEquals(1)
        assertTrue("a view of the list is not an opening", opened.isEmpty())
        // The first tap focuses the system, the second opens its games.
        compose.onNodeWithTag(row, useUnmergedTree = true).performTouchInput { click() }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag(row, useUnmergedTree = true).performTouchInput { click() }
        compose.mainClock.advanceTimeBy(400)
        assertEquals(listOf(firstSystem.id), opened.distinct())
    }
}
