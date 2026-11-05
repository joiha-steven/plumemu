package com.plumemu.setup

import com.plumemu.coredata.settings.ConfirmButtonSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.secondscreen.ScreenKind
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * First-run Setup faults of 0.1.2, at the model: the confirm step is two plain rows, Back from the last
 * steps returns to the question before them (the scan runs on), Back to the look step opens on the tile
 * picked, and a tap on a folder card first only focuses it.
 */
class SetupFirstRunFaultsTest {
    private val source = FakeSetupSource()

    private fun TestScope.modelWithAFolder(): SetupModel {
        val model = SetupModel(backgroundScope, source) { }
        backgroundScope.launch { model.state.collect { } }
        runCurrent()
        model.onPicked(PickResult.Picked(TREE))
        runCurrent()
        return model
    }

    private fun TestScope.press(
        model: SetupModel,
        vararg actions: Action,
    ) {
        for (action in actions) {
            model.onAction(action)
            runCurrent()
        }
    }

    private fun TestScope.tap(
        model: SetupModel,
        index: Int,
    ) = press(model, Action.TapItem(Column.Games, index))

    private fun TestScope.toConfirm(): SetupModel {
        source.hasGamepad.value = true
        val model = modelWithAFolder()
        press(model, Action.Enter)
        assertEquals(SetupStep.Confirm, model.state.value.step)
        return model
    }

    // 1. The confirm step: two rows, no key read as an answer.

    @Test
    fun theConfirmStepsFirstVisitIsTwoRowsFocusedOnUseA() = runTest {
        val model = toConfirm()
        assertEquals(listOf(SetupControl.ConfirmKeep, SetupControl.ConfirmUseB), model.state.value.controls)
        assertEquals(SetupControl.ConfirmKeep, model.state.value.focus)
    }

    @Test
    fun useAStoresAAndGoesOn() = runTest {
        val model = toConfirm()
        press(model, Action.Enter)
        assertEquals(ConfirmButtonSetting.Right, source.confirmButton)
        assertEquals(SetupStep.Look, model.state.value.step)
    }

    @Test
    fun moveDownThenAStoresBAndGoesOn() = runTest {
        val model = toConfirm()
        press(model, Action.Move(Direction.Down))
        assertEquals(SetupControl.ConfirmUseB, model.state.value.focus)
        assertNull("moving stores nothing", source.confirmButton)
        press(model, Action.Enter)
        assertEquals(ConfirmButtonSetting.Bottom, source.confirmButton)
        assertEquals(SetupStep.Look, model.state.value.step)
    }

    @Test
    fun aTapOnUseBStoresBAndGoesOn() = runTest {
        val model = toConfirm()
        tap(model, 1)
        assertEquals(ConfirmButtonSetting.Bottom, source.confirmButton)
        assertEquals(SetupStep.Look, model.state.value.step)
    }

    @Test
    fun bGoesBackToTheFoldersAndStoresNothing() = runTest {
        val model = toConfirm()
        press(model, Action.Back)
        assertEquals(SetupStep.Folders, model.state.value.step)
        assertNull(source.confirmButton)
    }

    // 4. Back from Scan and Ready: the question just before.

    private fun TestScope.toTheScan(askSecondScreen: Boolean): SetupModel {
        if (askSecondScreen) source.secondScreenQuestion.value = ScreenKind.BuiltIn
        val model = modelWithAFolder()
        press(model, Action.Enter) // Continue
        press(model, Action.Enter) // a look
        tap(model, 1) // Box art only
        return model
    }

    @Test
    fun backFromTheScanGoesToBoxArtAndTheScanRunsOn() = runTest {
        val model = toTheScan(askSecondScreen = false)
        assertEquals(SetupStep.Scan, model.state.value.step)
        press(model, Action.Back)
        assertEquals(SetupStep.BoxArt, model.state.value.step)
        source.endScan()
        runCurrent()
        assertEquals("a scan that ends does not pull the person off the question", SetupStep.BoxArt, model.state.value.step)
        tap(model, 1)
        assertEquals("answered again: the library, the scan being over", SetupStep.Ready, model.state.value.step)
    }

    @Test
    fun backFromReadyGoesToBoxArtWhenNoSecondScreenWasAsked() = runTest {
        val model = toTheScan(askSecondScreen = false)
        source.endScan()
        runCurrent()
        assertEquals(SetupStep.Ready, model.state.value.step)
        press(model, Action.Back)
        assertEquals(SetupStep.BoxArt, model.state.value.step)
    }

    @Test
    fun backFromReadyGoesToTheSecondScreenQuestionWhenItWasShown() = runTest {
        val model = toTheScan(askSecondScreen = true)
        assertEquals(SetupStep.SecondScreen, model.state.value.step)
        press(model, Action.Enter) // Use it
        source.endScan()
        runCurrent()
        assertEquals(SetupStep.Ready, model.state.value.step)
        press(model, Action.Back)
        assertEquals(SetupStep.SecondScreen, model.state.value.step)
        assertEquals(listOf(SetupControl.UseSecondScreen, SetupControl.SkipSecondScreen), model.state.value.controls)
        press(model, Action.Move(Direction.Right), Action.Enter) // Not now this time
        assertEquals(false, source.secondScreenAnswer)
        assertEquals(SetupStep.Ready, model.state.value.step)
    }

    @Test
    fun backFromAScanThatIsStillRunningGoesToTheSecondScreenQuestionToo() = runTest {
        val model = toTheScan(askSecondScreen = true)
        press(model, Action.Enter) // Use it; the scan is still running
        assertEquals(SetupStep.Scan, model.state.value.step)
        press(model, Action.Back)
        assertEquals(SetupStep.SecondScreen, model.state.value.step)
        press(model, Action.Enter)
        assertEquals(SetupStep.Scan, model.state.value.step)
        source.endScan()
        runCurrent()
        assertEquals(SetupStep.Ready, model.state.value.step)
    }

    @Test
    fun backFromReadyAfterSetUpLaterStaysOnTheFolders() = runTest {
        val model = SetupModel(backgroundScope, source) { }
        backgroundScope.launch { model.state.collect { } }
        runCurrent()
        press(model, Action.Move(Direction.Down), Action.Enter) // Set up later
        assertEquals(SetupStep.Ready, model.state.value.step)
        press(model, Action.Back)
        assertEquals(SetupStep.Folders, model.state.value.step)
    }

    // 6. The look step opens on the tile picked once a look was answered.

    @Test
    fun backToTheLookStepOpensOnThePickedTileNotTheSuggestion() = runTest {
        val model = modelWithAFolder()
        press(model, Action.Enter)
        assertEquals("first visit: the suggestion (none: the first tile)", SetupControl.Look0, model.state.value.focus)
        tap(model, 3)
        assertEquals(SetupStep.BoxArt, model.state.value.step)
        press(model, Action.Back)
        assertEquals(SetupStep.Look, model.state.value.step)
        assertEquals(SetupControl.Look3, model.state.value.focus)
    }

    @Test
    fun theLookStepsFirstVisitStillOpensOnTheDevicesSuggestion() = runTest {
        source.deviceFacts = DeviceFacts(manufacturer = "AYN", model = "Thor", widthDp = 768f, heightDp = 432f)
        val model = modelWithAFolder()
        press(model, Action.Enter)
        assertEquals(SetupControl.lookTile(SetupLooks.sets.indexOf(com.plumemu.coreui.theme.ColorSet.Plumemu)), model.state.value.focus)
    }

    // 11. A tap on a folder card: the first only focuses it.

    @Test
    fun aFirstTapOnAFolderCardOnlyFocusesItAndASecondRemovesIt() = runTest {
        val model = modelWithAFolder()
        assertEquals(SetupControl.Continue, model.state.value.focus)
        tap(model, 0)
        assertEquals(SetupControl.FolderCard0, model.state.value.focus)
        assertTrue("nothing removed yet", source.removed.isEmpty())
        assertEquals(1, model.state.value.folders.size)
        tap(model, 0)
        assertEquals(listOf(1L), source.removed.toList())
    }

    @Test
    fun aOnAFocusedFolderCardStillRemovesIt() = runTest {
        val model = modelWithAFolder()
        press(model, Action.Move(Direction.Up), Action.Move(Direction.Up), Action.Enter)
        assertEquals(1, source.removed.size)
    }

    private companion object {
        const val TREE = "content://com.android.externalstorage.documents/tree/primary%3AROMs"
    }
}
