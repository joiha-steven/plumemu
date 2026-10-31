package com.plumemu.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.components.HINT_DOT_TAG
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** An update found puts the accent dot on START Settings, and only there; no toast, no pop-up. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeUpdateDotTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val found = MutableStateFlow(false)

    @Test
    fun theDotFollowsAnUpdateFound() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            val router = remember { InputRouter(scope) }
            // As MainScreen provides it from HomeCallbacks.updateFound.
            PlumTheme { CompositionLocalProvider(LocalUpdateFound provides found) { HomeScreen(FakeHomeContent.board(), router, HomeCallbacks()) } }
        }
        compose.onAllNodesWithTag(HINT_DOT_TAG, useUnmergedTree = true).assertCountEquals(0)
        found.value = true
        compose.waitForIdle()
        compose.onAllNodesWithTag(HINT_DOT_TAG, useUnmergedTree = true).assertCountEquals(1)
        compose
            .onAllNodes(hasTestTag(HINT_DOT_TAG) and hasAnyAncestor(hasTestTag(HomeTags.hint(Action.OpenSettings))), useUnmergedTree = true)
            .assertCountEquals(1)
        found.value = false
        compose.waitForIdle()
        compose.onAllNodesWithTag(HINT_DOT_TAG, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun onlyTheSettingsHintCarriesIt() {
        val hints = hintsFor(FakeHomeContent.board(), com.plumemu.coreinput.Column.Systems, FakeHomeContent.board().entries.first(), settingsDot = true)
        org.junit.Assert.assertEquals(listOf(Action.OpenSettings), hints.filter { it.hasDot }.map { it.action })
    }
}
