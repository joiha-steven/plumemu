package com.plumemu

import android.os.Looper
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.drainMainLooper
import com.plumemu.secondscreen.Presence
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The hand-over from the graph's game-start relay (what `Launching.create` is given) to the
 * Activity's second-screen presence: a start the launcher reports, then the stop, is Playing; the
 * Activity's onDestroy lets go of the relay. Needs no content.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class MainActivityGameStartsTest {
    private val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    private val relay = (controller.get().application as PlumApplication).graph.gameStarts

    @After
    fun tearDown() {
        drainMainLooper()
    }

    @Test
    fun aStartReportedThroughTheGraphMakesTheNextStopPlaying() {
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull("the Activity took the relay in onCreate", relay.target)
        val presence = relay.target!!
        assertEquals(Presence.InPlumemu, presence.current.value)
        relay.beforeStart()
        controller.pause().stop()
        assertEquals(Presence.Playing, presence.current.value)
        controller.destroy()
        assertNull("released in onDestroy", relay.target)
    }

    @Test
    fun aStopWithoutAReportedStartIsAway() {
        val presence = relay.target!!
        controller.pause().stop()
        assertEquals(Presence.Away, presence.current.value)
        controller.destroy()
    }
}
