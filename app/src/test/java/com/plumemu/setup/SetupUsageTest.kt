package com.plumemu.setup

import com.plumemu.coreinput.Action
import com.plumemu.secondscreen.ScreenKind
import com.plumemu.usage.RecordingSink
import com.plumemu.usage.assertNoFreeText
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Setup's steps as the statistics see them (`setup_step`, `setup_completed`): where people go on, back or skip. */
class SetupUsageTest {
    private val source = FakeSetupSource()
    private val sink = RecordingSink()
    private var now = 0L

    private fun TestScope.model(): SetupModel {
        val model = SetupModel(backgroundScope, source, telemetry = sink, clock = { now }) { }
        backgroundScope.launch { model.state.collect { } }
        runCurrent()
        return model
    }

    private fun TestScope.pickAndContinue(model: SetupModel) {
        model.onPicked(PickResult.Picked(TREE))
        runCurrent()
        now += 31_000
        model.onAction(Action.Enter) // Continue
        runCurrent()
    }

    @Test
    fun eachStepLeftIsSentWithItsTimeThenTheEnd() = runTest {
        source.coverage = EmulatorCoverage(5, 6, listOf("GameCube"))
        val model = model()
        pickAndContinue(model)
        source.systems.value = listOf(SystemCount("Super Nintendo", 248), SystemCount("Mega Drive", 173))
        now += 4_000
        source.endScan()
        runCurrent()
        now += 2_000
        model.onAction(Action.Enter) // Start playing
        runCurrent()

        assertEquals(
            listOf(
                "setup_step" to mapOf("step" to "folders", "seconds" to "31", "action" to "next"),
                "setup_step" to mapOf("step" to "scan", "seconds" to "4", "action" to "next"),
                "setup_step" to mapOf("step" to "ready", "seconds" to "2", "action" to "next"),
                "setup_completed" to mapOf("seconds" to "37", "artwork" to "no", "folders" to "1", "systems_found" to "2", "emulators_picked" to "5"),
            ),
            sink.sent,
        )
        assertNoFreeText(sink.events)
    }

    @Test
    fun backAndASkippedSecondScreenSaySo() = runTest {
        source.secondScreenQuestion.value = ScreenKind.Unsure
        val model = model()
        pickAndContinue(model)
        assertEquals(SetupStep.SecondScreen, model.state.value.step)
        now += 3_000
        model.onAction(Action.Back)
        runCurrent()
        pickAndContinue(model)
        model.onAction(Action.Enter) // Not now (focused first on an unsure screen)
        runCurrent()
        assertEquals(
            listOf("folders/next", "second_screen/back", "folders/next", "second_screen/skip"),
            sink.sent.filter { it.first == "setup_step" }.map { "${it.second["step"]}/${it.second["action"]}" },
        )
    }

    private companion object {
        const val TREE = "content://com.android.externalstorage.documents/tree/primary%3AROMs"
    }
}
