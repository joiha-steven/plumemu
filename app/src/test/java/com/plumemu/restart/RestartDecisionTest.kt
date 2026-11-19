package com.plumemu.restart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [decideRestart]: a restart only with every condition met; each one missing alone holds. */
class RestartDecisionTest {
    private val stale = StaleCode.Stale("/data/app/x/com.plumemu-1/oat/arm64/base.odex", "deleted")
    private val allMet =
        RestartInputs(
            isScreenOff = true,
            isProcessInFront = true,
            wasHomeInFront = true,
            isPlayPending = false,
            busyWork = null,
            isSettingsIdle = true,
            code = stale,
            installKey = "412:1763500000000",
            restartedFor = null,
        )

    private fun holds(inputs: RestartInputs) = decideRestart(inputs) is RestartVerdict.Hold

    @Test
    fun everyConditionMetRestarts() {
        assertEquals(RestartVerdict.Restart(stale), decideRestart(allMet))
        assertEquals("an earlier install's mark", RestartVerdict.Restart(stale), decideRestart(allMet.copy(restartedFor = "411:1763000000000")))
    }

    @Test
    fun eachConditionMissingAloneHolds() {
        val cases =
            mapOf(
                "screen on" to allMet.copy(isScreenOff = false),
                "a game or another app in front" to allMet.copy(isProcessInFront = false),
                "the home was not in front at sleep" to allMet.copy(wasHomeInFront = false),
                "a play pending" to allMet.copy(isPlayPending = true),
                "a scan running" to allMet.copy(busyWork = "library scan"),
                "WorkManager work running" to allMet.copy(busyWork = "WorkManager work"),
                "a Settings write pending" to allMet.copy(isSettingsIdle = false),
                "code current" to allMet.copy(code = StaleCode.Fresh),
                "code unknown" to allMet.copy(code = StaleCode.Unknown("maps not readable")),
                "already restarted for this install" to allMet.copy(restartedFor = allMet.installKey),
            )
        cases.forEach { (name, inputs) -> assertTrue(name, holds(inputs)) }
    }

    @Test
    fun aHoldSaysWhy() {
        assertEquals(RestartVerdict.Hold("backup running"), decideRestart(allMet.copy(busyWork = "backup")))
    }

    @Test
    fun foregroundAndTopSleepingCountAsInFrontOthersDoNot() {
        assertTrue(isInFrontImportance(100))
        assertTrue("top sleeping, Android 9+", isInFrontImportance(325))
        assertTrue("top sleeping before Android 9", isInFrontImportance(150))
        listOf(125, 200, 230, 300, 400, 1000).forEach { assertFalse("importance $it", isInFrontImportance(it)) }
    }
}
