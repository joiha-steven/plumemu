package com.plumemu.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.components.TopBarTags
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * An update found shows the accent pill "Update 0.1.1" in the top bar and turns START's hint from "Settings" into "Update"
 * (no dot any more); a tap on the pill sends what START sends. Nothing is shown while no update waits.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeUpdatePillTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val found = MutableStateFlow<String?>(null)
    private var settingsOpened = 0

    private fun show() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            val router = remember { InputRouter(scope) }
            PlumTheme {
                CompositionLocalProvider(LocalUpdateFound provides found) {
                    HomeScreen(FakeHomeContent.board(), router, HomeCallbacks(onSettings = { settingsOpened++ }))
                }
            }
        }
    }

    @Test
    fun thePillFollowsAnUpdateFoundAndSaysTheVersion() {
        show()
        compose.onAllNodesWithTag(TopBarTags.PILL, useUnmergedTree = true).assertCountEquals(0)
        found.value = "0.1.1"
        compose.waitForIdle()
        compose.onNodeWithTag(TopBarTags.PILL, useUnmergedTree = true).assertExists()
        compose.onNode(androidx.compose.ui.test.hasText("Update 0.1.1") and hasAnyAncestor(hasTestTag(HomeTags.TOP_BAR)), useUnmergedTree = true).assertExists()
        found.value = null
        compose.waitForIdle()
        compose.onAllNodesWithTag(TopBarTags.PILL, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun startsHintReadsUpdateWhileOneWaitsAndSettingsOtherwise() {
        show()
        compose.onNode(androidx.compose.ui.test.hasText("Settings") and hasAnyAncestor(hasTestTag(HomeTags.hint(Action.OpenSettings))), useUnmergedTree = true).assertExists()
        found.value = "0.1.1"
        compose.waitForIdle()
        compose.onNode(androidx.compose.ui.test.hasText("Update") and hasAnyAncestor(hasTestTag(HomeTags.hint(Action.OpenSettings))), useUnmergedTree = true).assertExists()
        compose.onNode(androidx.compose.ui.test.hasText("Settings") and hasAnyAncestor(hasTestTag(HomeTags.hint(Action.OpenSettings))), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun hintsForNamesTheUpdateOnlyWhileOneWaits() {
        val content = FakeHomeContent.board()
        val entry = content.entries.first()
        fun startLabel(waiting: Boolean) = hintsFor(content, Column.Systems, entry, updateWaiting = waiting).single { it.action == Action.OpenSettings }.label
        assertEquals(R.string.home_hint_settings, startLabel(false))
        assertEquals(R.string.home_hint_update, startLabel(true))
    }

    @Test
    fun aTapOnThePillSendsWhatStartSends() {
        show()
        found.value = "0.1.1"
        compose.waitForIdle()
        compose.onNodeWithTag(TopBarTags.PILL, useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(1, settingsOpened)
    }

    /** The pill is 18 dp tall (the bar), but Compose widens a touch target to 48 dp around its centre where nothing else is hit first. */
    @Test
    fun aTouchJustAboveThePillStillHitsIt() {
        show()
        found.value = "0.1.1"
        compose.waitForIdle()
        val node = compose.onNodeWithTag(TopBarTags.PILL, useUnmergedTree = true)
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val density = compose.density.density
        val x = bounds.center.x
        // 44 dp area: 13 dp beyond the 18 dp pill each side; test 8 dp above its top edge (below it the list begins and is hit first, so the area cannot grow there).
        compose.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(x, bounds.top - 8 * density)) }
        compose.waitForIdle()
        assertEquals(1, settingsOpened)
    }
}
