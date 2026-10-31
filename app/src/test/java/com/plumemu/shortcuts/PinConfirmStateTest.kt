package com.plumemu.shortcuts

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The confirm screen's keys and taps: A adds, B cancels, one add at a time, messages close. */
@OptIn(ExperimentalCoroutinesApi::class)
class PinConfirmStateTest {
    private val scope = TestScope(UnconfinedTestDispatcher())
    private val request = FakePinRequest(gameNativeShortcut())
    private val ask = PinOffer.Ask(request, gameNativeShortcut(), "Broforce", "steam", "Steam")
    private val adds = AtomicInteger()
    private var offer: PinOffer = ask
    private var answer = CompletableDeferred<PinAddOutcome>()

    private val state =
        PinConfirmState(
            object : PinConfirmActions {
                override suspend fun offer(): PinOffer = offer

                override suspend fun add(offer: PinOffer.Ask): PinAddOutcome {
                    adds.incrementAndGet()
                    return answer.await()
                }
            },
            scope,
        )

    @Test
    fun itAsksAboutTheGameAndItsList() {
        state.start()
        assertEquals(PinScreen.Asking("Broforce", "Steam"), state.screen)
        assertEquals(0, state.focus)
    }

    @Test
    fun aAddsAndClosesWhenStored() {
        state.start()
        state.onAction(Action.Enter)
        assertEquals(PinScreen.Asking("Broforce", "Steam", isAdding = true), state.screen)
        answer.complete(PinAddOutcome.Added(7, isNew = true))
        scope.runCurrent()
        assertEquals(1, adds.get())
        assertTrue(state.isClosed)
    }

    @Test
    fun pressesWhileAddingAreIgnoredSoOnlyOneAddRuns() {
        state.start()
        state.onAction(Action.Enter)
        state.onAction(Action.Enter)
        state.onAction(Action.TapItem(Column.Games, 0))
        state.onAction(Action.Back)
        assertEquals(1, adds.get())
        assertFalse("B while adding does not drop the add", state.isClosed)
    }

    @Test
    fun bCancelsWithoutAdding() {
        state.start()
        state.onAction(Action.Back)
        assertTrue(state.isClosed)
        assertEquals(0, adds.get())
        assertEquals(0, request.acceptCalls.get())
    }

    @Test
    fun rightThenACancels() {
        state.start()
        state.onAction(Action.Move(Direction.Right))
        assertEquals(1, state.focus)
        state.onAction(Action.Move(Direction.Right))
        assertEquals("stops at Cancel", 1, state.focus)
        state.onAction(Action.Enter)
        assertTrue(state.isClosed)
        assertEquals(0, adds.get())
    }

    @Test
    fun aTapOnAddAdds() {
        state.start()
        state.onAction(Action.TapItem(Column.Games, 0))
        assertEquals(1, adds.get())
    }

    @Test
    fun aFailedAddShowsItsMessageAndOkCloses() {
        state.start()
        state.onAction(Action.Enter)
        answer.complete(PinAddOutcome.NotSaved)
        scope.runCurrent()
        assertEquals(PinScreen.Message(PinMessage.NotSaved, "Broforce"), state.screen)
        assertFalse(state.isClosed)
        state.onAction(Action.Enter)
        assertTrue(state.isClosed)
    }

    @Test
    fun aWidgetShowsTheRefusalAndBCloses() {
        offer = PinOffer.Refused(PinRefusal.Widget)
        state.start()
        assertEquals(PinScreen.Message(PinMessage.Widget), state.screen)
        state.onAction(Action.Move(Direction.Right))
        assertEquals("one button only", 0, state.focus)
        state.onAction(Action.Back)
        assertTrue(state.isClosed)
        assertEquals(0, adds.get())
    }

    @Test
    fun beforeTheRequestIsReadOnlyBLeaves() {
        state.onAction(Action.Enter)
        assertFalse(state.isClosed)
        assertEquals(PinScreen.Reading, state.screen)
        state.onAction(Action.Back)
        assertTrue(state.isClosed)
        assertEquals(0, adds.get())
    }
}
