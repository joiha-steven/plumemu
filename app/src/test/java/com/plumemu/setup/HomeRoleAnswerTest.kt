package com.plumemu.setup

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The home screen question's Yes never seems to do nothing: Android 10+ answers a role request at once, showing
 * nothing, after two refusals, so the card waits for the answer and, if Plumemu is still not the home app, says so.
 */
class HomeRoleAnswerTest {
    private class Actions : HomeRoleActions {
        var canRequest = true
        var canOpenList = true
        var requests = 0
        var listOpened = 0
        var marked = 0
        var answer: ((Boolean) -> Unit)? = null

        override fun isHomeApp() = false

        override fun requestHomeRole(onAnswered: (Boolean) -> Unit): Boolean {
            requests++
            answer = onAnswered
            return canRequest
        }

        override fun openHomeSettings(): Boolean {
            listOpened++
            return canOpenList
        }

        override fun markAsked() {
            marked++
        }
    }

    private val actions = Actions()
    private val state = HomeRolePromptState(actions)

    @Test
    fun yesKeepsTheCardUntilAndroidAnswers() {
        state.onAction(Action.Enter)
        assertEquals(1, actions.marked)
        assertFalse("the card stays", state.isClosed)
        assertTrue(state.isWaiting)
        assertTrue(state.keepsShowing)
        state.onAction(Action.Back)
        state.onAction(Action.Enter)
        assertEquals("nothing is asked twice while it waits", 1, actions.requests)
        assertFalse(state.isClosed)
    }

    @Test
    fun becomingTheHomeAppClosesTheCard() {
        state.onAction(Action.Enter)
        actions.answer!!(true)
        assertTrue(state.isClosed)
    }

    @Test
    fun notBecomingTheHomeAppSaysSoAndOffersAndroidsList() {
        state.onAction(Action.Enter)
        actions.answer!!(false)
        assertFalse(state.isClosed)
        assertTrue(state.showsNotYet)
        assertFalse(state.isWaiting)
        assertEquals("the list button has the focus", 0, state.focus)
        state.onAction(Action.Enter)
        assertEquals(1, actions.listOpened)
        assertTrue(state.isClosed)
        assertEquals("asked once only", 1, actions.requests)
    }

    @Test
    fun okAndBackCloseTheNotYetCardWithoutOpeningAnything() {
        state.onAction(Action.Enter)
        actions.answer!!(false)
        state.onAction(Action.Move(Direction.Right))
        assertEquals(1, state.focus)
        state.onAction(Action.Enter)
        assertTrue(state.isClosed)
        assertEquals(0, actions.listOpened)
        val again = HomeRolePromptState(actions)
        again.onAction(Action.Enter)
        actions.answer!!(false)
        again.onAction(Action.Back)
        assertTrue(again.isClosed)
        assertEquals(0, actions.listOpened)
    }

    @Test
    fun touchOpensAndroidsListFromTheNotYetCard() {
        state.onAction(Action.TapItem(com.plumemu.coreinput.Column.Games, 0))
        actions.answer!!(false)
        state.onAction(Action.TapItem(com.plumemu.coreinput.Column.Games, 0))
        assertEquals(1, actions.listOpened)
        assertTrue(state.isClosed)
    }

    @Test
    fun aListThatCannotOpenFallsBackToTheUnavailableWords() {
        actions.canOpenList = false
        state.onAction(Action.Enter)
        actions.answer!!(false)
        state.onAction(Action.Enter)
        assertTrue(state.showsUnavailable)
        assertFalse(state.showsNotYet)
        assertFalse(state.isClosed)
    }

    @Test
    fun noScreenAtAllStillSaysSoAtOnce() {
        actions.canRequest = false
        state.onAction(Action.Enter)
        assertTrue(state.showsUnavailable)
        assertFalse(state.isWaiting)
    }

    @Test
    fun notNowClosesAndAsksNothing() {
        state.onAction(Action.Move(Direction.Right))
        state.onAction(Action.Enter)
        assertTrue(state.isClosed)
        assertEquals(1, actions.marked)
        assertEquals(0, actions.requests)
    }
}
