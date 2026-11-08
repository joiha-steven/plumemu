package com.plumemu.setup

import com.plumemu.account.FakeScraperAccounts
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.secondscreen.ScreenKind
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Two review findings: a long press obeys the first-tap rule of a folder card, and the Scan step always has its wait. */
class SetupReviewFaultsTest {
    private val source = FakeSetupSource()

    private fun TestScope.model(): SetupModel {
        val model = SetupModel(backgroundScope, source, FakeScraperAccounts()) { }
        backgroundScope.launch { model.state.collect { } }
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
    fun aLongPressOnAFolderCardThatIsNotFocusedOnlyFocusesIt() = runTest {
        val model = model()
        model.onPicked(PickResult.Picked("content://x/tree/primary%3AROMs"))
        runCurrent()
        model.onPicked(PickResult.Picked("content://x/tree/primary%3AGames"))
        runCurrent()
        val folders = model.state.value.folders.size
        val cards = model.state.value.controls.filter { it.folderIndex != null }
        assertEquals(2, cards.size)
        val other = cards.first { it != model.state.value.focus }
        press(model, Action.LongPressItem(Column.Games, model.state.value.controls.indexOf(other)))
        assertEquals("nothing was removed", folders, model.state.value.folders.size)
        assertEquals("the card is focused", other, model.state.value.focus)
    }

    @Test
    fun theScanStepShownWhenTheSecondScreenGoesAwayStillGetsItsWait() = runTest {
        val model = model()
        model.onPicked(PickResult.Picked("content://x/tree/primary%3AROMs"))
        runCurrent()
        // Continue, the look tile, Yes, the default source: the scan.
        press(model, Action.Enter, Action.Enter, Action.Move(Direction.Down), Action.Enter, Action.Enter)
        assertEquals(SetupStep.Scan, model.state.value.step)
        // Back to the box art question: the wait stops. A second screen appears; the question is answered again.
        press(model, Action.Back)
        assertEquals(SetupStep.BoxArt, model.state.value.step)
        source.secondScreenQuestion.value = ScreenKind.BuiltIn
        runCurrent()
        press(model, Action.Enter, Action.Enter)
        assertEquals(SetupStep.SecondScreen, model.state.value.step)
        // It is unplugged while asked about: the screen shows the scan, and the library must still come.
        source.secondScreenQuestion.value = null
        runCurrent()
        assertEquals(SetupStep.Scan, model.state.value.step)
        source.endScan()
        runCurrent()
        assertEquals(SetupStep.Ready, model.state.value.step)
    }
}
