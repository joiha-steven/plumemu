package com.plumemu.restart

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [HomeFrontState]: the home counts as in front at sleep only when the screen going off paused it. */
class HomeFrontStateTest {
    private val state = HomeFrontState().apply { created(isHome = true) }

    private fun sleepFromHome() {
        state.resumed(isHome = true)
        state.paused(isHome = true, isInteractive = false)
        state.saved(isHome = true)
    }

    @Test
    fun theScreenGoingOffOverTheHomeCounts() {
        sleepFromHome()
        assertTrue(state.wasInFrontAtSleep())
    }

    @Test
    fun notBeforeTheStateIsSaved() {
        state.resumed(isHome = true)
        state.paused(isHome = true, isInteractive = false)
        assertFalse(state.wasInFrontAtSleep())
    }

    @Test
    fun leavingForAGameOrAnAppWithTheScreenOnDoesNotCount() {
        state.resumed(isHome = true)
        state.paused(isHome = true, isInteractive = true)
        state.saved(isHome = true)
        assertFalse(state.wasInFrontAtSleep())
    }

    @Test
    fun anotherOfTheAppsScreensInFrontLastDoesNotCount() {
        sleepFromHome()
        state.created(isHome = false)
        state.resumed(isHome = false)
        assertFalse(state.wasInFrontAtSleep())
    }

    @Test
    fun aHomeResumedAgainNoLongerCounts() {
        sleepFromHome()
        state.resumed(isHome = true)
        assertFalse(state.wasInFrontAtSleep())
    }

    @Test
    fun noHomeAliveDoesNotCount() {
        sleepFromHome()
        state.destroyed(isHome = true)
        assertFalse(state.wasInFrontAtSleep())
    }
}
