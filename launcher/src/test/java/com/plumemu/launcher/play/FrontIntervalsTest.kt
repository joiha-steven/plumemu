package com.plumemu.launcher.play

import com.plumemu.launcher.play.FrontEvent.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

class FrontIntervalsTest {
    private val pkg = "com.emu"

    private fun ev(at: Long, kind: Kind, activity: String? = "A", p: String? = pkg) = FrontEvent(at, p, kind, activity)

    private fun sum(vararg events: FrontEvent) = FrontIntervals.sum(events.toList(), pkg, 1_000, 11_000)

    @Test
    fun noEventsIsTheWholeSpan() = assertEquals(10_000L, sum())

    @Test
    fun inFrontAllAlong() = assertEquals(10_000L, sum(ev(1_500, Kind.IN_FRONT)))

    @Test
    fun otherAppInBetweenIsLeftOut() =
        assertEquals(
            3_000L + 4_000L,
            sum(
                ev(4_000, Kind.NOT_IN_FRONT), ev(4_000, Kind.IN_FRONT, "B", "com.other"),
                ev(7_000, Kind.NOT_IN_FRONT, "B", "com.other"), ev(7_000, Kind.IN_FRONT),
            ),
        )

    @Test
    fun screenOffInTheMiddleIsLeftOut() =
        assertEquals(10_000L - 2_000L, sum(ev(3_000, Kind.SCREEN_OFF, null, null), ev(5_000, Kind.SCREEN_ON, null, null)))

    @Test
    fun twoActivitiesStayInFrontWhileOneIsResumed() =
        assertEquals(
            10_000L,
            sum(ev(2_000, Kind.IN_FRONT), ev(3_000, Kind.IN_FRONT, "B"), ev(3_000, Kind.NOT_IN_FRONT, "A"), ev(3_100, Kind.NOT_IN_FRONT, "A")),
        )

    @Test
    fun leavingAfterTheLastActivityPausedStops() =
        assertEquals(3_000L, sum(ev(2_000, Kind.IN_FRONT), ev(4_000, Kind.NOT_IN_FRONT)))

    @Test
    fun eventsBeforeFromAreIgnored() =
        assertEquals(10_000L, sum(ev(500, Kind.NOT_IN_FRONT), ev(900, Kind.SCREEN_OFF, null, null)))

    @Test
    fun screenOffAtTheEndStopsTheCount() =
        assertEquals(5_000L, sum(ev(6_000, Kind.SCREEN_OFF, null, null)))
}
