package com.plumemu.secondscreen

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The wiring from a game start to the in-game mode: the launcher's observer (the relay in
 * `AppGraph`) reaches the Activity's presence, and only a stop after a real start is Playing.
 */
class GameStartRelayTest {
    private val relay = GameStartRelay()
    private val presence = SecondScreenPresence().also { relay.target = it }

    @Test
    fun aStartThenTheStopIsPlaying() {
        presence.onStart()
        presence.onResume()
        relay.beforeStart()
        presence.onStop()
        assertEquals(Presence.Playing, presence.current.value)
    }

    @Test
    fun aStartThatThrewLeavesNoFlag() {
        presence.onStart()
        relay.beforeStart()
        relay.startFailed()
        presence.onStop()
        assertEquals(Presence.Away, presence.current.value)
    }

    @Test
    fun noStartNoFlag() {
        presence.onStart()
        presence.onStop()
        assertEquals("a launch that stopped earlier or was cancelled never calls the relay", Presence.Away, presence.current.value)
    }

    @Test
    fun aRecreatedActivityTakesOverAndTheOldOneDoesNotClearIt() {
        val newer = SecondScreenPresence()
        relay.target = newer
        relay.release(presence)
        newer.onStart()
        relay.beforeStart()
        newer.onStop()
        assertEquals(Presence.Playing, newer.current.value)
        relay.release(newer)
        relay.beforeStart()
    }
}
