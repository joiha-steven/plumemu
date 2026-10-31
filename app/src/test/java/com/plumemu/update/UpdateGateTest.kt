package com.plumemu.update

import com.plumemu.update.UpdateGate.DAY_MS
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the daily check is due. */
class UpdateGateTest {
    private val now = 1_800_000_000_000L

    private fun due(
        on: Boolean = true,
        network: NetworkKind = NetworkKind.Wifi,
        last: Long? = now - DAY_MS,
        failedAt: Long? = null,
    ) = UpdateGate.isDailyCheckDue(on, network, last, now, failedAt)

    @Test fun dueAfterADayOnAnUnmeteredNetwork() = assertTrue(due())

    @Test fun dueWhenNeverChecked() = assertTrue(due(last = null))

    @Test fun notDueWithTheSwitchOff() = assertFalse(due(on = false, last = null))

    @Test fun notDueOnAMeteredNetwork() = assertFalse(due(network = NetworkKind.Metered, last = null))

    @Test fun notDueOnAnUnmeteredNetworkThatIsNotWifi() = assertFalse(due(network = NetworkKind.Unmetered, last = null))

    @Test fun aFailedAutomaticTryWaitsSixHours() {
        assertFalse(due(last = null, failedAt = now - UpdateGate.RETRY_MS + 1))
        assertTrue(due(last = null, failedAt = now - UpdateGate.RETRY_MS))
        // A clock that moved back since the failure counts as due.
        assertTrue(due(last = null, failedAt = now + 60_000))
    }

    @Test fun notDueWithoutANetwork() = assertFalse(due(network = NetworkKind.None, last = null))

    @Test fun notDueUnderADay() = assertFalse(due(last = now - DAY_MS + 1))

    @Test fun aClockThatMovedBackCountsAsDue() = assertTrue(due(last = now + 60_000))

    @Test
    fun theManualCheckNeedsOnlyANetwork() {
        assertTrue(UpdateGate.canCheckNow(NetworkKind.Metered))
        assertTrue(UpdateGate.canCheckNow(NetworkKind.Unmetered))
        assertFalse(UpdateGate.canCheckNow(NetworkKind.None))
    }
}
