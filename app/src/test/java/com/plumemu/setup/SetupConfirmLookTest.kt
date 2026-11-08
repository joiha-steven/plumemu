package com.plumemu.setup

import com.plumemu.coredata.settings.BoxArtSourceSetting
import com.plumemu.coredata.settings.ConfirmButtonSetting
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.theme.ColorSet
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Setup's "Which button confirms?" and "Pick a look" steps, and the order of every step: no pad skips the
 * first; its two rows (Use A, Use B) set Confirm (SetupFirstRunFaultsTest has the rows' own rules); the look is preselected for the device; Back walks
 * all the way to the folders; the library step no longer has the push-right option.
 */
class SetupConfirmLookTest {
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

    @Test
    fun withoutAPadThereIsNoConfirmStepAndNoDotForIt() = runTest {
        val model = modelWithAFolder()
        assertEquals("folders, look, box art, library", 4, model.state.value.stepDots)
        press(model, Action.Enter)
        assertEquals(SetupStep.Look, model.state.value.step)
        assertNull(source.confirmButton)
    }

    @Test
    fun withAPadContinueOpensTheConfirmStepWithItsOwnDot() = runTest {
        source.hasGamepad.value = true
        val model = modelWithAFolder()
        assertEquals(5, model.state.value.stepDots)
        press(model, Action.Enter)
        val state = model.state.value
        assertEquals(SetupStep.Confirm, state.step)
        assertEquals(1, state.stepDotIndex)
        assertEquals(listOf(SetupControl.ConfirmKeep, SetupControl.ConfirmUseB), state.controls)
        assertEquals(SetupControl.ConfirmKeep, state.focus)
    }

    @Test
    fun aTapOrEnterOnUseAStoresAAndMovesOn() = runTest {
        source.hasGamepad.value = true
        val tapped = modelWithAFolder()
        press(tapped, Action.Enter, Action.TapItem(Column.Games, 0))
        assertEquals(ConfirmButtonSetting.Right, source.confirmButton)
        assertEquals(SetupStep.Look, tapped.state.value.step)

        val entered = modelWithAFolder()
        press(entered, Action.Enter, Action.Enter)
        assertEquals(SetupStep.Look, entered.state.value.step)
    }

    @Test
    fun aConfirmThatCannotBeStoredSaysSoAndStays() = runTest {
        source.hasGamepad.value = true
        val model = modelWithAFolder()
        press(model, Action.Enter)
        source.saveFailure = IllegalStateException("disk")
        press(model, Action.Move(Direction.Down), Action.Enter)
        assertEquals(SetupStep.Confirm, model.state.value.step)
        assertEquals(SetupMessage.SaveFailed, model.state.value.message)
    }

    @Test
    fun theLookStepOpensOnThePlainDefaultWhenTheDeviceSuggestsNothing() = runTest {
        val model = modelWithAFolder()
        press(model, Action.Enter)
        val state = model.state.value
        assertEquals(SetupStep.Look, state.step)
        assertEquals(SetupControl.Look0, state.focus)
        assertEquals(SetupLooks.sets.size, state.controls.size)
        press(model, Action.Enter)
        assertEquals(ColorSet.Plumemu, source.appliedLook?.first)
        assertNull("no suggestion: the text size is left alone", source.appliedLook?.second)
        assertEquals("not OLED: the set's own ink page", false, source.appliedBlackPage)
        assertEquals(SetupStep.BoxArt, model.state.value.step)
    }

    @Test
    fun anOledThorLikeDevicePreselectsNearBlack() = runTest {
        source.deviceFacts = DeviceFacts(manufacturer = "AYN", model = "Thor", widthDp = 768f, heightDp = 432f)
        val model = modelWithAFolder()
        press(model, Action.Enter)
        val index = SetupLooks.sets.indexOf(ColorSet.Plumemu)
        assertEquals(SetupControl.lookTile(index), model.state.value.focus)
        press(model, Action.Enter)
        assertEquals(ColorSet.Plumemu, source.appliedLook?.first)
        assertNull("a 16:9 screen keeps the text size", source.appliedLook?.second)
        assertEquals("OLED: true black under the default set's accent", true, source.appliedBlackPage)
    }

    @Test
    fun aSmallFourThreeDeviceGetsALargerTextSize() = runTest {
        source.deviceFacts = DeviceFacts(manufacturer = "Moorechip", model = "Retroid Pocket Nova", widthDp = 576f, heightDp = 432f)
        val model = modelWithAFolder()
        press(model, Action.Enter, Action.Enter)
        assertEquals(TextSizeSetting.Larger, source.appliedLook?.second)
    }

    @Test
    fun aTapOnAnotherTileAppliesThatSet() = runTest {
        val model = modelWithAFolder()
        press(model, Action.Enter, Action.TapItem(Column.Games, 3))
        assertEquals(SetupLooks.sets[3], source.appliedLook?.first)
        assertEquals(SetupStep.BoxArt, model.state.value.step)
    }

    @Test
    fun aSecondAnswerWhileTheFirstIsBeingStoredIsIgnored() = runTest {
        source.hasGamepad.value = true
        val model = modelWithAFolder()
        press(model, Action.Enter)
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        source.writeGate = gate
        press(model, Action.Enter)
        press(model, Action.Move(Direction.Down), Action.Enter)
        gate.complete(Unit)
        runCurrent()
        assertEquals("only the first answer counts", ConfirmButtonSetting.Right, source.confirmButton)
        assertEquals(SetupStep.Look, model.state.value.step)
    }

    @Test
    fun aSecondBoxArtChoiceWhileTheFirstIsBeingStoredIsIgnored() = runTest {
        val model = modelWithAFolder()
        press(model, Action.Enter, Action.Enter)
        assertEquals(SetupStep.BoxArt, model.state.value.step)
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        source.writeGate = gate
        press(model, Action.TapItem(Column.Games, 1)) // Yes: libretro (no ScreenScraper keys)
        press(model, Action.TapItem(Column.Games, 0)) // No, too late
        gate.complete(Unit)
        runCurrent()
        assertEquals(BoxArtSourceSetting.LibretroOnly, source.boxArtSource.value)
        assertEquals(SetupStep.Scan, model.state.value.step)
    }

    @Test
    fun theLookStepOpensOnTheCurrentSetWhenTheDeviceSuggestsNothing() = runTest {
        source.currentColorSet.value = SetupLooks.sets[4]
        val model = modelWithAFolder()
        press(model, Action.Enter)
        assertEquals(SetupControl.Look4, model.state.value.focus)
    }

    @Test
    fun anOwnColourLookOpensOnTheFirstTile() = runTest {
        source.currentColorSet.value = null
        val model = modelWithAFolder()
        press(model, Action.Enter)
        assertEquals(SetupControl.Look0, model.state.value.focus)
    }

    @Test
    fun theTilesWalkInAGridAndStopAtItsEdges() = runTest {
        val model = modelWithAFolder()
        press(model, Action.Enter)
        press(model, Action.Move(Direction.Left))
        assertEquals("nothing left of the first", SetupControl.Look0, model.state.value.focus)
        press(model, Action.Move(Direction.Right))
        assertEquals(SetupControl.Look1, model.state.value.focus)
        press(model, Action.Move(Direction.Down))
        assertEquals(SetupControl.Look4, model.state.value.focus)
        press(model, Action.Move(Direction.Down))
        assertEquals("nothing below the last row", SetupControl.Look4, model.state.value.focus)
        press(model, Action.Move(Direction.Up))
        assertEquals(SetupControl.Look1, model.state.value.focus)
        assertNull("moving stores nothing", source.appliedLook)
    }

    @Test
    fun backWalksEveryStepToTheFolders() = runTest {
        source.hasGamepad.value = true
        source.secondScreenQuestion.value = com.plumemu.secondscreen.ScreenKind.BuiltIn
        val model = modelWithAFolder()
        press(model, Action.Enter, Action.Enter) // Continue; Use A
        press(model, Action.Enter, Action.Move(Direction.Down), Action.Enter) // look tile; Plumemu's own covers (the fakes have no ScreenScraper keys)
        assertEquals(SetupStep.SecondScreen, model.state.value.step)
        val seen = mutableListOf<SetupStep>()
        repeat(4) {
            press(model, Action.Back)
            seen += model.state.value.step
        }
        assertEquals(listOf(SetupStep.BoxArt, SetupStep.Look, SetupStep.Confirm, SetupStep.Folders), seen)
    }

    @Test
    fun theLibraryStepHasOnlyTheStatisticsRowAndStartPlaying() = runTest {
        val model = modelWithAFolder()
        press(model, Action.Enter, Action.Enter, Action.Move(Direction.Down), Action.Enter)
        source.endScan()
        runCurrent()
        assertEquals(SetupStep.Ready, model.state.value.step)
        assertEquals(listOf(SetupControl.UsageStatistics, SetupControl.StartPlaying), controlsOf(SetupStep.Ready, folderCount = 1, hasScanFailed = false))
        assertEquals(BoxArtSourceSetting.LibretroOnly, source.boxArtSource.value)
    }

    private companion object {
        const val TREE = "content://com.android.externalstorage.documents/tree/primary%3AROMs"
    }
}
