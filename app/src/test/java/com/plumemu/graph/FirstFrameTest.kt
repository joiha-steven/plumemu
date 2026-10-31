package com.plumemu.graph

import android.app.Activity
import android.os.Looper
import android.widget.FrameLayout
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The startup work's trigger runs only after a draw, once. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class FirstFrameTest {
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val view = FrameLayout(activity).also { activity.setContentView(it) }

    @Test
    fun runsAfterTheFirstDrawAndOnlyOnce() {
        var runs = 0
        view.doAfterFirstDraw { runs++ }
        idle()
        assertEquals("not before anything is drawn", 0, runs)

        view.viewTreeObserver.dispatchOnDraw()
        assertEquals("not during the draw itself", 0, runs)
        idle()
        assertEquals(1, runs)

        view.viewTreeObserver.dispatchOnDraw()
        idle()
        assertEquals("later frames do not run it again", 1, runs)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
