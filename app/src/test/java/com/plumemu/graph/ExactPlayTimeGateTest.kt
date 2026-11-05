package com.plumemu.graph

import com.plumemu.launcher.play.ForegroundTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExactPlayTimeGateTest {
    private var asked = 0
    private val delegate =
        ForegroundTime { _, from, to ->
            asked++
            to - from
        }

    @Test
    fun offAnswersNullWithoutAskingTheSystem() {
        val gate = ExactPlayTimeGate({ false }, delegate)
        assertNull(gate.inFrontMs("com.example", 1_000, 5_000))
        assertEquals(0, asked)
    }

    @Test
    fun onAsksTheDelegateAndPassesItsAnswerOn() {
        val gate = ExactPlayTimeGate({ true }, delegate)
        assertEquals(4_000L, gate.inFrontMs("com.example", 1_000, 5_000))
        assertEquals(1, asked)
    }

    @Test
    fun theSwitchIsReadOnEveryAsk() {
        var on = false
        val gate = ExactPlayTimeGate({ on }, delegate)
        assertNull(gate.inFrontMs("com.example", 0, 10))
        on = true
        assertEquals(10L, gate.inFrontMs("com.example", 0, 10))
    }

    private val lines = mutableListOf<String>()

    @Test
    fun aFailedSwitchReadIsOffAndLogsOneLineWithTheKindOnly() {
        val isOn = exactPlayTimeSwitch({ throw IllegalStateException("secret detail") }, { lines += it })
        assertEquals(false, isOn())
        assertEquals(1, lines.size)
        assertEquals(true, lines.single().contains("IllegalStateException"))
        assertEquals(false, lines.single().contains("secret detail"))
    }

    @Test
    fun cancellationOfTheSwitchReadIsNotSwallowed() {
        val isOn = exactPlayTimeSwitch({ throw kotlinx.coroutines.CancellationException("stop") }, { lines += it })
        assertEquals(true, runCatching { isOn() }.exceptionOrNull() is kotlinx.coroutines.CancellationException)
        assertEquals(emptyList<String>(), lines)
    }

    @Test
    fun theStoredSwitchValueIsPassedOn() {
        assertEquals(true, exactPlayTimeSwitch({ true }, { lines += it })())
    }
}
