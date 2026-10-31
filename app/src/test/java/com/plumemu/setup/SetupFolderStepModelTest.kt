package com.plumemu.setup

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The folder step's two ways out besides Continue: Set up later (no folder), and removing a folder that was picked by mistake. */
class SetupFolderStepModelTest {
    private val source = FakeSetupSource()
    private val logLines = CopyOnWriteArrayList<String>()

    private fun TestScope.model(): SetupModel {
        val model = SetupModel(backgroundScope, source) { logLines += it }
        backgroundScope.launch { model.state.collect { } }
        runCurrent()
        return model
    }

    private fun TestScope.pick(
        model: SetupModel,
        tree: String,
    ) {
        model.onPicked(PickResult.Picked(tree))
        runCurrent()
    }

    @Test
    fun withNoFolderTheStepOffersChooseAndSetUpLaterAndOpensOnChoose() = runTest {
        val model = model()
        assertEquals(listOf(SetupControl.ChooseFolder, SetupControl.SetUpLater), model.state.value.controls)
        assertEquals(SetupControl.ChooseFolder, model.state.value.focus)
    }

    @Test
    fun setUpLaterByGamepadGoesToTheLastStepWithNoFolder() = runTest {
        val model = model()
        model.onAction(Action.Move(Direction.Down))
        runCurrent()
        assertEquals(SetupControl.SetUpLater, model.state.value.focus)
        assertNull("it needs no picker", model.onAction(Action.Enter))
        runCurrent()
        val state = model.state.value
        assertEquals(SetupStep.Ready, state.step)
        assertTrue(state.folders.isEmpty())
        assertEquals(SetupControl.StartPlaying, state.focus)
        assertEquals("no scan for a library with no folder", 0, source.scanRequests)
        model.onAction(Action.Enter)
        runCurrent()
        assertTrue("Start playing ends setup with no folder", source.setupCompleted)
    }

    @Test
    fun setUpLaterByTouch() = runTest {
        val model = model()
        model.onAction(Action.TapItem(Column.Games, 1))
        runCurrent()
        assertEquals(SetupStep.Ready, model.state.value.step)
    }

    @Test
    fun backFromTheLastStepReturnsToTheFolderStepOnChoose() = runTest {
        val model = model()
        model.onAction(Action.TapItem(Column.Games, 1))
        runCurrent()
        model.onAction(Action.Back)
        runCurrent()
        assertEquals(SetupStep.Folders, model.state.value.step)
        assertEquals(SetupControl.ChooseFolder, model.state.value.focus)
    }

    @Test
    fun aRemovalWaitingForABusyScanShowsRemovingIgnoresASecondAAndBlocksContinue() = runTest {
        val model = model()
        pick(model, TREE_A)
        source.removeGate = kotlinx.coroutines.CompletableDeferred()
        model.onAction(Action.Move(Direction.Up))
        runCurrent()
        model.onAction(Action.Move(Direction.Up))
        runCurrent()
        model.onAction(Action.Enter)
        runCurrent()
        assertTrue("shown as removing at once", model.state.value.folders.single().isRemoving)
        model.onAction(Action.Enter)
        runCurrent()
        assertEquals("a second A on it does nothing", 1, source.removeCalls)
        val scansBefore = source.scanRequests
        model.onAction(Action.TapItem(Column.Games, 2)) // Continue
        runCurrent()
        assertEquals("Continue waits for the removal", scansBefore, source.scanRequests)
        assertEquals(SetupStep.Folders, model.state.value.step)
        source.removeGate!!.complete(Unit)
        runCurrent()
        assertEquals(listOf(1L), source.removed.toList())
        assertTrue(model.state.value.folders.isEmpty())
    }

    @Test
    fun aWronglyPickedFolderIsRemovedByAAndFocusGoesBackToChoose() = runTest {
        val model = model()
        pick(model, TREE_A)
        model.onAction(Action.Move(Direction.Up))
        runCurrent()
        model.onAction(Action.Move(Direction.Up))
        runCurrent()
        assertEquals(SetupControl.FolderCard0, model.state.value.focus)
        model.onAction(Action.Enter)
        runCurrent()
        assertEquals(listOf(1L), source.removed.toList())
        val state = model.state.value
        assertTrue(state.folders.isEmpty())
        assertEquals(listOf(SetupControl.ChooseFolder, SetupControl.SetUpLater), state.controls)
        assertEquals(SetupControl.ChooseFolder, state.focus)
        assertNull(state.message)
    }

    @Test
    fun aTapOnTheSecondCardRemovesThatFolderOnlyAndFocusLandsOnContinue() = runTest {
        val model = model()
        pick(model, TREE_A)
        pick(model, TREE_B)
        assertEquals(
            listOf(SetupControl.FolderCard0, SetupControl.FolderCard1, SetupControl.AddFolder, SetupControl.Continue),
            model.state.value.controls,
        )
        model.onAction(Action.TapItem(Column.Games, 1))
        runCurrent()
        assertEquals(listOf(2L), source.removed.toList())
        assertEquals(listOf(1L), model.state.value.folders.map { it.id })
        assertEquals(SetupControl.Continue, model.state.value.focus)
    }

    @Test
    fun aFolderThatCannotBeRemovedSaysSoAndStays() = runTest {
        val model = model()
        pick(model, TREE_A)
        source.removeAnswer = { false }
        model.onAction(Action.TapItem(Column.Games, 0))
        runCurrent()
        assertEquals(SetupMessage.FolderCouldNotRemove, model.state.value.message)
        assertEquals(1, model.state.value.folders.size)
    }

    @Test
    fun aFailureWhileRemovingIsLoggedByClassAndShown() = runTest {
        val model = model()
        pick(model, TREE_A)
        source.removeAnswer = { throw IllegalStateException("content://secret/path") }
        model.onAction(Action.TapItem(Column.Games, 0))
        runCurrent()
        assertEquals(SetupMessage.FolderCouldNotRemove, model.state.value.message)
        assertTrue(logLines.any { it.contains("IllegalStateException") })
        assertFalse("no URI in the log", logLines.any { it.contains("content://") })
    }

    private companion object {
        const val TREE_A = "content://com.android.externalstorage.documents/tree/primary%3AROMs"
        const val TREE_B = "content://com.android.externalstorage.documents/tree/1234-5678%3AGames"
    }
}
