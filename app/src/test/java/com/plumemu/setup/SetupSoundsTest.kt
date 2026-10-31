package com.plumemu.setup

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.sound.Outcome
import com.plumemu.sound.Reported
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What setup reports for each press. The model says it at the moment it acts, so the answer does
 * not wait for its state flow: every check here reads it before the flow has caught up.
 */
class SetupSoundsTest {
    private val source = FakeSetupSource()
    private val reported = Reported()

    private fun TestScope.model(): SetupModel {
        val model = SetupModel(backgroundScope, source) {}
        reported.listenTo(model.feel)
        backgroundScope.launch { model.state.collect { } }
        runCurrent()
        return model
    }

    private fun SetupModel.press(action: Action): List<Outcome> {
        onAction(action)
        return reported.drain() // no runCurrent: the state flow has not caught up
    }

    @Test
    fun theFolderStepConfirmsTheButtonAndIgnoresMovesPastItsEndsAndBack() = runTest {
        val model = model()
        assertEquals(listOf(Outcome.Activated), model.press(Action.Enter))
        assertEquals("Up on the first control", emptyList<Outcome>(), model.press(Action.Move(Direction.Up)))
        assertEquals("B on the first step has nowhere to go", emptyList<Outcome>(), model.press(Action.Back))
        assertEquals(listOf(Outcome.Activated), model.press(Action.TapItem(Column.Games, 0)))
    }

    @Test
    fun movesConfirmsAndBackThroughTheSteps() = runTest {
        val model = model()
        model.onPicked(PickResult.Picked(TREE))
        runCurrent()
        assertEquals(listOf(SetupControl.FolderCard0, SetupControl.AddFolder, SetupControl.Continue), model.state.value.controls)
        assertEquals(listOf(Outcome.Moved), model.press(Action.Move(Direction.Up)))
        runCurrent()
        assertEquals(listOf(Outcome.Moved), model.press(Action.Move(Direction.Up)))
        runCurrent()
        assertEquals("a second Up on the first control", emptyList<Outcome>(), model.press(Action.Move(Direction.Up)))
        assertEquals(listOf(Outcome.Moved), model.press(Action.Move(Direction.Down)))
        runCurrent()
        assertEquals(listOf(Outcome.Moved), model.press(Action.Move(Direction.Down)))
        runCurrent()
        assertEquals("Continue starts the scan", listOf(Outcome.Activated), model.press(Action.Enter))
        runCurrent()
        assertEquals(listOf(Outcome.WentBack), model.press(Action.Back))
    }

    private companion object {
        const val TREE = "content://tree/roms"
    }
}
