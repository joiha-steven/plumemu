package com.plumemu.secondscreen

import org.junit.Assert.assertEquals
import org.junit.Test

/** The chart's vertical scale and the steady "empty at". */
class InGameBatteryScaleTest {
    private fun chart(vararg percents: Float, ahead: Float? = null) =
        BatteryChart(
            startMillis = 0,
            minutes = 180f,
            played = percents.mapIndexed { i, p -> ChartPoint(i * 10f, p) },
            aheadTo = ahead?.let { ChartPoint(180f, it) },
            usualFrom = null,
            usualTo = null,
            hourMinutes = emptyList(),
            aheadEnds = true,
        )

    @Test
    fun aSlowLineIsNotLeftHuggingTheTopOfAWholeBox() {
        // 65% now, 41% at the chart's end (a device without a counter): 30 to 70, not 0 to 100.
        assertEquals(30f..70f, chart(66f, 65f, ahead = 41f).scale())
    }

    @Test
    fun theScaleIsAtLeastFortyPointsSoASlowDrainDoesNotLookLikeACliff() {
        assertEquals(40f..80f, chart(78f, 77f, ahead = 72f).scale())
        assertEquals(0f..40f, chart(8f, 7f, ahead = 2f).scale())
    }

    @Test
    fun aFullBatteryAndAnEmptyOneStayInside() {
        assertEquals(0f..100f, chart(100f, 60f, ahead = 0f).scale())
        assertEquals(60f..100f, chart(97f, 98f, ahead = 100f).scale())
    }

    @Test
    fun noPointsIsTheWholeBox() {
        assertEquals(0f..100f, chart().scale())
    }

    @Test
    fun emptyAtIsOnFiveMinutesOfTheWallClock() {
        val nine = 9 * 3_600_000L
        // 8 h 34.4 min after 9:00:20 is 17:34:44: shown as 17:35.
        assertEquals(nine + (8 * 60 + 35) * 60_000L, endsAt(nine + 20_000, 8 * 60 + 34.4))
        // A minute later at the same pace the time left is a minute less: the same 17:35.
        assertEquals(nine + (8 * 60 + 35) * 60_000L, endsAt(nine + 80_000, 8 * 60 + 33.4))
    }
}
