package com.plumemu.home

import android.os.Looper
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A test that ends with a focused text field must leave nothing on the main looper once
 * [rulesAround] has torn it down (DrainMainLooper in ComposeHostRule.kt): Robolectric clears what is
 * left before the next test, and a dropped `AndroidUiDispatcher.Main` task leaves that process-wide
 * dispatcher marked as scheduled, so every later capture in the JVM draws without frames.
 *
 * Checked right after the teardown, inside this test, rather than from the next test: background
 * work leaked by other tests can stick the dispatcher too, and would make such a check fail here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class ComposeHostTeardownTest {
    private val compose = appComposeRule()

    private class NothingLeftOnTheLooper : ExternalResource() {
        override fun after() {
            val looper = shadowOf(Looper.getMainLooper())
            if (!looper.isLooperEmpty()) {
                throw AssertionError("the main looper still holds tasks after the teardown (next at ${looper.nextScheduledTaskTime})")
            }
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(NothingLeftOnTheLooper()).around(rulesAround(compose))

    @Test
    fun aTestThatEndsWithAFocusedFieldLeavesTheLooperEmpty() {
        compose.setContent {
            val focus = remember { FocusRequester() }
            BasicTextField(value = "name", onValueChange = {}, modifier = Modifier.focusRequester(focus))
            LaunchedEffect(Unit) { focus.requestFocus() }
        }
        compose.waitForIdle()
    }
}
