package com.plumemu

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The library error offers a real "Try again" button, as the other empty states offer theirs; the hint bar does not repeat it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class MainScreenLibraryErrorButtonTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private var retries = 0
    private lateinit var router: InputRouter
    private var buttonHeightPx = 0f

    private fun show() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            buttonHeightPx = PlumTheme.layout.setupButtonHeight.value * LocalDensity.current.density
            LibraryError(router, onRetry = { retries++ }, onSettings = {})
        }
        compose.waitForIdle()
    }

    @Test
    fun tryAgainIsOneButtonNotAHint() {
        show()
        val nodes = compose.onAllNodesWithText(context.getString(R.string.home_hint_try_again)).fetchSemanticsNodes()
        assertEquals("one 'Try again' on screen (the button; the hint bar does not repeat it)", 1, nodes.size)
        val height = compose.onNodeWithTag(MainScreenTags.TRY_AGAIN).fetchSemanticsNode().boundsInRoot.height
        assertTrue("it is a button as tall as the other empty states' ($height >= $buttonHeightPx)", height >= buttonHeightPx - 1f)
    }

    @Test
    fun aTapOnTheButtonAndAOnTheGamepadBothRetry() {
        show()
        compose.onNodeWithTag(MainScreenTags.TRY_AGAIN).performClick()
        compose.waitForIdle()
        assertEquals("tap", 1, retries)
        compose.runOnIdle { router.send(Action.Enter) }
        compose.waitForIdle()
        assertEquals("A", 2, retries)
    }
}
