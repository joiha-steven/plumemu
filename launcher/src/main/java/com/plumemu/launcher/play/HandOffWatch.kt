package com.plumemu.launcher.play

import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Settles, between a caller that waits for a start with a time limit and [PlayTimeTracker.handOff],
 * whether the start is given up or handed over, never both: the caller puts one in the start's
 * coroutine context; [handOff] [claim]s it right before the hand-off, the caller [giveUp]s it when
 * its limit is reached. Whoever comes first wins: a given-up start hands nothing over (its note is
 * taken back), and a caller that finds the hand-off begun waits for its real result.
 */
class HandOffWatch : AbstractCoroutineContextElement(Key) {
    private val state = AtomicInteger(WAITING)

    /** The hand-off begins; false when the caller gave up first. */
    fun claim(): Boolean = state.compareAndSet(WAITING, HANDING_OFF) || state.get() == HANDING_OFF

    /** The caller stops waiting; false when the hand-off already began (then wait for its result). */
    fun giveUp(): Boolean = state.compareAndSet(WAITING, GIVEN_UP) || state.get() == GIVEN_UP

    companion object Key : CoroutineContext.Key<HandOffWatch> {
        private const val WAITING = 0
        private const val HANDING_OFF = 1
        private const val GIVEN_UP = 2
    }
}
