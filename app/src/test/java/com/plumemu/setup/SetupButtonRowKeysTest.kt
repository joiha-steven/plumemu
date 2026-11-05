package com.plumemu.setup

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The key presses on Setup's pairs of buttons, which wrap one under the other when the text is large: Left and Right walk the
 * pair however it is laid out, Up and Down too. Controls in a column keep Up and Down only (a Nova showed Right doing nothing).
 */
class SetupButtonRowKeysTest {
    private val source = FakeSetupSource()

    private fun TestScope.model(): SetupModel {
        val model = SetupModel(backgroundScope, source) { }
        backgroundScope.launch { model.state.collect { } }
        runCurrent()
        return model
    }

    private fun TestScope.press(
        model: SetupModel,
        direction: Direction,
    ): SetupControl? {
        model.onAction(Action.Move(direction))
        runCurrent()
        return model.state.value.focus
    }

    private fun TestScope.assertWalksThePair(
        model: SetupModel,
        first: SetupControl,
        second: SetupControl,
    ) {
        assertEquals(first, model.state.value.focus)
        assertEquals("Left stops at the end", first, press(model, Direction.Left))
        assertEquals(second, press(model, Direction.Right))
        assertEquals("Right stops at the end", second, press(model, Direction.Right))
        assertEquals(first, press(model, Direction.Left))
        assertEquals(second, press(model, Direction.Down))
        assertEquals(first, press(model, Direction.Up))
    }

    @Test
    fun theFolderStepsButtonsWalkWithAllFourDirections() = runTest {
        assertWalksThePair(model(), SetupControl.ChooseFolder, SetupControl.SetUpLater)
    }

    @Test
    fun aFailedLoadsButtonsWalkWithAllFourDirections() = runTest {
        source.foldersFailure = IllegalStateException("Room: disk I/O error")
        assertWalksThePair(model(), SetupControl.TryAgain, SetupControl.SetUpLater)
    }

    @Test
    fun aColumnOfControlsKeepsUpAndDownOnly() = runTest {
        val model = model()
        model.onPicked(PickResult.Picked("content://tree/roms"))
        runCurrent()
        assertEquals(listOf(SetupControl.FolderCard0, SetupControl.AddFolder, SetupControl.Continue), model.state.value.controls)
        assertEquals(SetupControl.Continue, model.state.value.focus)
        assertEquals(SetupControl.Continue, press(model, Direction.Left))
        assertEquals(SetupControl.AddFolder, press(model, Direction.Up))
        assertEquals(SetupControl.AddFolder, press(model, Direction.Right))
        assertEquals(SetupControl.AddFolder, press(model, Direction.Left))
    }
}
