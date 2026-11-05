package com.plumemu

import android.os.Looper
import androidx.compose.ui.platform.AndroidUiDispatcher
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.EmptyCoroutineContext

/** What Robolectric's looper clearing does to Compose's dispatcher, and the reset that undoes it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class ComposeDispatcherResetTest {
    @Test
    fun aDispatcherWhoseLooperWasClearedRunsItsWorkAfterTheReset() {
        val dispatcher = AndroidUiDispatcher.Main[ContinuationInterceptor] as CoroutineDispatcher
        var ran = false
        dispatcher.dispatch(EmptyCoroutineContext) { ran = true }
        // What Robolectric does between two tests.
        shadowOf(Looper.getMainLooper().queue).reset()
        assertTrue("the clearing leaves the dispatcher marked as scheduled", composeDispatcherIsStuck())
        assertFalse("the clearing dropped the task", ran)

        resetComposeDispatcher()

        assertTrue("the reset runs the task that was left", ran)
        assertFalse("the reset clears the marks", composeDispatcherIsStuck())
    }

    @Test
    fun aDispatcherThatNothingMadeIsLeftAlone() {
        // Whether Main exists depends on earlier tests of the JVM; either way the call must not throw.
        resetComposeDispatcher()
        assertFalse(composeDispatcherIsStuck())
    }
}
