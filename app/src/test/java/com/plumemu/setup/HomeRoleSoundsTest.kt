package com.plumemu.setup

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.sound.Outcome
import com.plumemu.sound.Reported
import org.junit.Assert.assertEquals
import org.junit.Test

/** What the home screen question reports: moves between answers, confirms, and the "unavailable" message. */
class HomeRoleSoundsTest {
    private class Actions(var canRequest: Boolean) : HomeRoleActions {
        override fun isHomeApp() = false

        override fun requestHomeRole() = canRequest

        override fun markAsked() = Unit
    }

    private val reported = Reported()

    private fun prompt(canRequest: Boolean = true) = HomeRolePromptState(Actions(canRequest)).also { reported.listenTo(it.feel) }

    private fun HomeRolePromptState.press(action: Action): List<Outcome> {
        onAction(action)
        return reported.drain()
    }

    @Test
    fun movesBetweenTheAnswersOnlyWhenTheyMove() {
        val state = prompt()
        assertEquals(emptyList<Outcome>(), state.press(Action.Move(Direction.Up)))
        assertEquals(listOf(Outcome.Moved), state.press(Action.Move(Direction.Down)))
        assertEquals(emptyList<Outcome>(), state.press(Action.Move(Direction.Down)))
    }

    @Test
    fun yesConfirmsAndNotNowViaBackGoesBack() {
        assertEquals(listOf(Outcome.Activated), prompt().press(Action.Enter))
        assertEquals(listOf(Outcome.WentBack), prompt().press(Action.Back))
    }

    @Test
    fun openingTheUnavailableMessageConfirmsAndClosingItGoesBack() {
        val state = prompt(canRequest = false)
        assertEquals(listOf(Outcome.Activated), state.press(Action.Enter))
        assertEquals(true, state.showsUnavailable)
        assertEquals(listOf(Outcome.WentBack), state.press(Action.Back))
    }
}
