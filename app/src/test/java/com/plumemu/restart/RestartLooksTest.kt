package com.plumemu.restart

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [RestartLooks] on a virtual clock: when it looks, and that anything stopping a restart gives the mark back. No process is killed. */
@OptIn(ExperimentalCoroutinesApi::class)
class RestartLooksTest {
    private val key = "412:1763500000000"
    private var isScreenOn = false
    private var busy: String? = null
    private var looksRead = 0
    private var kills = 0
    private var markAtKill: String? = "not killed"
    private var event: CompletableDeferred<Unit>? = null

    private val mark =
        object : RestartMark {
            var stored: String? = null

            override fun read() = stored

            override fun write(installKey: String?): Boolean {
                stored = installKey
                return true
            }
        }

    private fun TestScope.looks() =
        RestartLooks(
            scope = backgroundScope,
            isScreenOn = { isScreenOn },
            inputs = {
                looksRead++
                RestartInputs(
                    isScreenOff = !isScreenOn, isProcessInFront = true, wasHomeInFront = true, isPlayPending = false,
                    busyWork = busy, isSettingsIdle = true, code = StaleCode.Stale("/data/app/a/oat/arm64/base.odex", "deleted"),
                    installKey = key, restartedFor = mark.read(),
                )
            },
            stillBusy = { busy },
            mark = mark,
            recordEvent = { event?.await() },
            kill = {
                kills++
                markAtKill = mark.read()
            },
            log = {},
        )

    private fun TestScope.screenOffAndWaitTheFirstLook(looks: RestartLooks) {
        looks.screenChanged(on = false)
        advanceTimeBy(RestartLooks.FIRST_LOOK_MS + 1)
        runCurrent()
    }

    @Test
    fun allClearCommitsTheMarkBeforeKilling() = runTest {
        screenOffAndWaitTheFirstLook(looks())
        assertEquals(1, kills)
        assertEquals("the mark was stored when kill ran", key, markAtKill)
        assertEquals(key, mark.stored)
    }

    @Test
    fun theScreenOnDuringTheFirstWaitKillsNothing() = runTest {
        val looks = looks()
        looks.screenChanged(on = false)
        advanceTimeBy(RestartLooks.FIRST_LOOK_MS / 2)
        isScreenOn = true
        looks.screenChanged(on = true)
        advanceTimeBy(RestartLooks.LOOK_AGAIN_MS * 2)
        runCurrent()
        assertEquals(0, looksRead)
        assertEquals(0, kills)
        assertNull(mark.stored)
    }

    @Test
    fun theScreenOnWhileTheEventIsRecordedKillsNothingAndGivesTheMarkBack() = runTest {
        event = CompletableDeferred()
        val looks = looks()
        screenOffAndWaitTheFirstLook(looks)
        assertEquals("committed before the event", key, mark.stored)
        isScreenOn = true
        looks.screenChanged(on = true) // cancels the wait for the event
        runCurrent()
        assertEquals(0, kills)
        assertNull("given back", mark.stored)
    }

    @Test
    fun workStartingWhileTheEventIsRecordedKillsNothingAndGivesTheMarkBack() = runTest {
        for (work in listOf("backup", "a Settings write is pending")) {
            event = CompletableDeferred()
            busy = null
            val looks = looks()
            screenOffAndWaitTheFirstLook(looks)
            assertEquals(key, mark.stored)
            busy = work
            event!!.complete(Unit)
            runCurrent()
            assertEquals(work, 0, kills)
            assertNull("$work: given back", mark.stored)
            looks.screenChanged(on = true)
        }
    }

    @Test
    fun theScreenOnRightBeforeTheKillKillsNothing() = runTest {
        event = CompletableDeferred()
        screenOffAndWaitTheFirstLook(looks())
        isScreenOn = true // no broadcast yet: only the last check sees it
        event!!.complete(Unit)
        runCurrent()
        assertEquals(0, kills)
        assertNull(mark.stored)
    }

    @Test
    fun anInstallAlreadyRestartedForKillsNothingAndStopsLooking() = runTest {
        mark.stored = key
        screenOffAndWaitTheFirstLook(looks())
        advanceTimeBy(RestartLooks.LOOK_AGAIN_MS * 3)
        runCurrent()
        assertEquals(0, kills)
        assertEquals("one look, then none", 1, looksRead)
        assertEquals(key, mark.stored)
    }

    @Test
    fun aHoldLooksAgainAfterTheIntervalWhileTheScreenStaysOff() = runTest {
        busy = "library scan"
        screenOffAndWaitTheFirstLook(looks())
        assertEquals(0, kills)
        busy = null
        advanceTimeBy(RestartLooks.LOOK_AGAIN_MS + 1)
        runCurrent()
        assertEquals(2, looksRead)
        assertEquals(1, kills)
    }
}
