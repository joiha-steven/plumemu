package com.plumemu.secondscreen

import com.plumemu.coredata.settings.InGameSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which stop is for a game Plumemu started, and what the window shows for each. */
class SecondScreenPresenceTest {
    private val presence = SecondScreenPresence()

    @Test
    fun awayUntilTheFirstStart() {
        assertEquals(Presence.Away, presence.current.value)
        presence.onStart()
        assertEquals(Presence.InPlumemu, presence.current.value)
    }

    @Test
    fun aStopAfterALaunchIsPlaying() {
        presence.onStart()
        presence.onLaunching()
        presence.onStop()
        assertEquals(Presence.Playing, presence.current.value)
    }

    @Test
    fun aStopWithoutALaunchIsAway() {
        presence.onStart()
        presence.onStop()
        assertEquals("Home to another launcher, recents, an app started from outside", Presence.Away, presence.current.value)
    }

    @Test
    fun theFlagIsClearedOnStart() {
        presence.onStart()
        presence.onLaunching()
        presence.onStop()
        presence.onStart()
        assertEquals(Presence.InPlumemu, presence.current.value)
        presence.onStop()
        assertEquals("back from the game, then left another way", Presence.Away, presence.current.value)
    }

    @Test
    fun aLaunchThatStartedNothingDoesNotCountLater() {
        presence.onStart()
        presence.onLaunching()
        presence.onLaunchFailed()
        presence.onStop()
        assertEquals(Presence.Away, presence.current.value)
    }

    @Test
    fun aFailureReportedAfterTheStopChangesNothing() {
        presence.onStart()
        presence.onLaunching()
        presence.onStop()
        presence.onLaunchFailed()
        assertEquals(Presence.Playing, presence.current.value)
        presence.onStart()
        presence.onStop()
        assertEquals(Presence.Away, presence.current.value)
    }

    /** An emulator that finishes in its onCreate, or a translucent one: Plumemu only pauses and resumes. */
    @Test
    fun aResumeWithoutAStopClearsTheFlag() {
        presence.onStart()
        presence.onResume()
        presence.onLaunching()
        presence.onResume()
        presence.onStop()
        assertEquals("a later Recents or store exit is not a game", Presence.Away, presence.current.value)
    }

    @Test
    fun theModeForEachPresence() {
        assertEquals(WindowMode.Picking, windowModeOf(Presence.InPlumemu, InGameSetting.Clock))
        assertEquals(WindowMode.Picking, windowModeOf(Presence.InPlumemu, InGameSetting.Black))
        assertEquals(WindowMode.InGameClock, windowModeOf(Presence.Playing, InGameSetting.Clock))
        assertEquals(WindowMode.InGameBlack, windowModeOf(Presence.Playing, InGameSetting.Black))
        assertNull(windowModeOf(Presence.Away, InGameSetting.Clock))
        assertNull(windowModeOf(Presence.Away, InGameSetting.Black))
    }
}
