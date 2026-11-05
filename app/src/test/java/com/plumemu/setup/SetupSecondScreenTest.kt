package com.plumemu.setup

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
 * The second screen question: only with a screen nobody has
 * decided about, after the folders while the scan runs; its focus by the screen's kind; the answer
 * stored by A and by a tap; Back to the folders; skipped when the screen goes away.
 */
class SetupSecondScreenTest {
    private val source = FakeSetupSource()

    private fun TestScope.modelWithAFolder(on: FakeSetupSource = source): SetupModel {
        val model = SetupModel(backgroundScope, on) { }
        backgroundScope.launch { model.state.collect { } }
        runCurrent()
        model.onPicked(PickResult.Picked(TREE))
        runCurrent()
        return model
    }

    private fun TestScope.press(
        model: SetupModel,
        action: Action,
    ) {
        model.onAction(action)
        runCurrent()
    }

    /** Continue on the folders, the look step's tile, then the box art step's first choice: the next step. */
    private fun TestScope.passLookAndBoxArt(model: SetupModel) {
        repeat(3) { press(model, Action.Enter) }
    }

    @Test
    fun withoutASecondScreenBoxArtGoesStraightToTheScanAndFourDotsShow() = runTest {
        val model = modelWithAFolder()
        assertEquals("folders, look, box art, library", 4, model.state.value.stepDots)
        passLookAndBoxArt(model)
        assertEquals(SetupStep.Scan, model.state.value.step)
        assertEquals(4, model.state.value.stepDots)
        assertEquals("the scan shares the library's dot", 3, model.state.value.stepDotIndex)
        assertNull(source.secondScreenAnswer)
    }

    @Test
    fun aBuiltInScreenIsAskedAboutWithUseItFocusedAndAIsStoredAsOn() = runTest {
        source.secondScreenQuestion.value = ScreenKind.BuiltIn
        val model = modelWithAFolder()
        assertEquals("the dot counts on the folder step already", 5, model.state.value.stepDots)
        val scansBefore = source.scanRequests
        passLookAndBoxArt(model)

        val asked = model.state.value
        assertEquals(SetupStep.SecondScreen, asked.step)
        assertEquals(SetupControl.UseSecondScreen, asked.focus)
        assertEquals(listOf(SetupControl.UseSecondScreen, SetupControl.SkipSecondScreen), asked.controls)
        assertEquals(3, asked.stepDotIndex)
        assertEquals("the scan runs while the question shows", scansBefore + 1, source.scanRequests)
        assertTrue(asked.isScanning)

        press(model, Action.Enter) // Use it
        assertEquals(true, source.secondScreenAnswer)
        val after = model.state.value
        assertEquals(SetupStep.Scan, after.step)
        assertEquals("the answered step keeps its dot", 5, after.stepDots)
        assertEquals(4, after.stepDotIndex)

        source.endScan()
        runCurrent()
        assertEquals(SetupStep.Ready, model.state.value.step)
    }

    @Test
    fun anUnsureScreenStartsOnNotNowAndAIsStoredAsOff() = runTest {
        source.secondScreenQuestion.value = ScreenKind.Unsure
        val model = modelWithAFolder()
        passLookAndBoxArt(model)
        assertEquals(SetupControl.SkipSecondScreen, model.state.value.focus)
        press(model, Action.Enter)
        assertEquals(false, source.secondScreenAnswer)
        assertEquals(SetupStep.Scan, model.state.value.step)
    }

    @Test
    fun tapsAnswerEitherWay() = runTest {
        source.secondScreenQuestion.value = ScreenKind.Unsure
        val model = modelWithAFolder()
        press(model, Action.TapItem(Column.Games, 2)) // Continue (the folder card is 0, Add another folder 1)
        press(model, Action.Enter) // the look step's tile
        press(model, Action.TapItem(Column.Games, 1)) // Box art only
        assertEquals(SetupStep.SecondScreen, model.state.value.step)
        press(model, Action.TapItem(Column.Games, 0)) // Use it
        assertEquals("a tap on Use it, though Not now had the focus", true, source.secondScreenAnswer)

        val other = FakeSetupSource().apply { secondScreenQuestion.value = ScreenKind.BuiltIn }
        val second = modelWithAFolder(other)
        passLookAndBoxArt(second)
        press(second, Action.TapItem(Column.Games, 1)) // Not now
        assertEquals("a tap on Not now, though Use it had the focus", false, other.secondScreenAnswer)
    }

    @Test
    fun leftRightUpAndDownWalkTheTwoButtons() = runTest {
        source.secondScreenQuestion.value = ScreenKind.BuiltIn
        val model = modelWithAFolder()
        passLookAndBoxArt(model)
        press(model, Action.Move(Direction.Right))
        assertEquals(SetupControl.SkipSecondScreen, model.state.value.focus)
        press(model, Action.Move(Direction.Right))
        assertEquals("stops at the end", SetupControl.SkipSecondScreen, model.state.value.focus)
        press(model, Action.Move(Direction.Left))
        assertEquals(SetupControl.UseSecondScreen, model.state.value.focus)
        press(model, Action.Move(Direction.Down))
        assertEquals(SetupControl.SkipSecondScreen, model.state.value.focus)
        press(model, Action.Move(Direction.Up))
        assertEquals(SetupControl.UseSecondScreen, model.state.value.focus)
        assertNull("moving stores nothing", source.secondScreenAnswer)
    }

    @Test
    fun aScanThatEndsWhileTheQuestionShowsWaitsForTheAnswerThenTheLibraryShows() = runTest {
        source.secondScreenQuestion.value = ScreenKind.BuiltIn
        val model = modelWithAFolder()
        passLookAndBoxArt(model)
        source.endScan()
        runCurrent()
        assertEquals("the question stays until answered", SetupStep.SecondScreen, model.state.value.step)
        press(model, Action.Enter)
        assertEquals(SetupStep.Ready, model.state.value.step)
        assertEquals(SetupControl.StartPlaying, model.state.value.focus)
    }

    @Test
    fun backGoesToBoxArtLeavesTheScanRunningAndContinuingAsksAgain() = runTest {
        source.secondScreenQuestion.value = ScreenKind.BuiltIn
        val model = modelWithAFolder()
        passLookAndBoxArt(model)
        press(model, Action.Back)
        val back = model.state.value
        assertEquals(SetupStep.BoxArt, back.step)
        assertEquals(SetupControl.BoxArtLibretro, back.focus)
        assertTrue("the scan goes on", source.scan.value.isBusy)
        assertNull(source.secondScreenAnswer)

        source.endScan()
        runCurrent()
        assertEquals("a scan that ends meanwhile does not jump past the step", SetupStep.BoxArt, model.state.value.step)
        press(model, Action.Enter)
        assertEquals(SetupStep.SecondScreen, model.state.value.step)
    }

    @Test
    fun theScreenGoingAwayWhileAskedSkipsToTheScanAndItsDot() = runTest {
        source.secondScreenQuestion.value = ScreenKind.Unsure
        val model = modelWithAFolder()
        passLookAndBoxArt(model)
        assertEquals(SetupStep.SecondScreen, model.state.value.step)

        source.secondScreenQuestion.value = null
        runCurrent()
        val skipped = model.state.value
        assertEquals(SetupStep.Scan, skipped.step)
        assertEquals("no step, no dot", 4, skipped.stepDots)
        assertNull("nothing stored", source.secondScreenAnswer)

        // It stays skipped if the screen comes back during the scan, and so does its dot.
        source.secondScreenQuestion.value = ScreenKind.Unsure
        runCurrent()
        assertEquals(SetupStep.Scan, model.state.value.step)
        assertEquals("the dot does not come back without the question", 4, model.state.value.stepDots)
        source.endScan()
        runCurrent()
        assertEquals(SetupStep.Ready, model.state.value.step)
    }

    @Test
    fun anAnswerThatCannotBeStoredSaysSoAndSetupGoesOn() = runTest {
        source.secondScreenQuestion.value = ScreenKind.BuiltIn
        val model = modelWithAFolder()
        passLookAndBoxArt(model)
        source.saveFailure = IllegalStateException("disk")
        press(model, Action.Enter)
        assertEquals(SetupMessage.SaveFailed, model.state.value.message)
        source.saveFailure = null
        source.endScan()
        runCurrent()
        assertEquals(SetupStep.Ready, model.state.value.step)
    }

    private companion object {
        const val TREE = "content://com.android.externalstorage.documents/tree/primary%3AROMs"
    }
}
