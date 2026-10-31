package com.plumemu.coreui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import com.plumemu.coreui.theme.LocalGlideClock
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.Settle

/**
 * The focus mark of a home grid (the ring), travelling from the cell it leaves to the cell it lands on in both axes, the
 * PS Vita's way ([GlidingPill] does the same for a list's pill, on Y only): over [PlumMotion.GLIDE_MS] on
 * [PlumMotion.Decelerate], the list glide's own tokens. A grid draws the ring itself (over its clip), so this does not draw: it
 * answers, while the grid draws, how far from its cell the ring is now ([offset]). Nothing recomposes per frame.
 *
 * The caller passes the focused cell's laid-out place, without the list glide's translation: the ring travels on its own
 * path from where it was drawn to where the cell now lies, over the same time and curve as the rows' glide, so the two stay
 * in step. (Passing the translated place made the ring jump a row when the grid cut its glide short in the frame after the
 * move, which it does at the last whole row, where no room is left to glide in: the lag was fixed against a target that
 * then moved.) A move starts the lag where the ring was drawn last frame, so a move during a move never snaps back. A key held (the next move comes within [PlumMotion.GLIDE_MS]
 * of the last) settles linearly at the speed of the repeats, like the list glide, so the ring stays with the moving focus.
 * With animations off the offset is always zero: the ring jumps. [moved] arms it, [offset] answers.
 */
@Stable
class FocusTravel internal constructor(
    /** Settles 1 to 0: how much of the glide is left. */
    private val settle: Settle,
    private val nowNanos: () -> Long,
    private val isOn: () -> Boolean,
) {
    private var lastIndex = NONE
    private var lastDrawn = Offset.Unspecified
    private var lastMoveAt = Long.MIN_VALUE
    private var lag = Offset.Zero

    /** The share of the glide the last frame had left: the lag is spent in proportion to it. */
    private var owed = 0f

    /** The cell the focus moved to ([moved]) and the ring has not been drawn on yet. */
    private var armed = NONE

    /**
     * The focus moved to cell [index]: start the glide's clock now, in the frame of the move (not at the first draw, which a
     * frame that draws nothing would postpone). Call it from the code that follows the focus, not while drawing. A move
     * within [PlumMotion.GLIDE_MS] of the last (a held key) glides linearly over the time since it, so the ring keeps
     * the repeats' speed; any other move glides on [PlumMotion.Decelerate].
     */
    fun moved(index: Int) {
        if (!isOn() || index < 0) return
        val now = nowNanos()
        val since = if (lastMoveAt == Long.MIN_VALUE) Long.MAX_VALUE else now - lastMoveAt
        lastMoveAt = now
        val glideNanos = PlumMotion.GLIDE_MS * NANOS_PER_MS
        if (since !in 0 until glideNanos) {
            settle.start(1f, glideNanos, PlumMotion.Decelerate)
        } else {
            settle.start(1f, since.coerceAtLeast(PlumMotion.GLIDE_HELD_MIN_MS * NANOS_PER_MS), LinearEasing)
        }
        armed = index
    }

    /**
     * Where the ring is drawn relative to its cell now. Call it while drawing, once per frame, for the focused cell [index]
     * whose laid-out top-left (no glide translation) is [cell]. The lag never exceeds [capX] and [capY] (a page jump must
     * not carry the ring across the screen).
     */
    fun offset(
        index: Int,
        cell: Offset,
        capX: Float,
        capY: Float,
    ): Offset {
        if (!isOn()) {
            reset()
            return Offset.Zero
        }
        if (index != lastIndex) {
            val from = lastDrawn
            lastIndex = index
            if (armed == index && from != Offset.Unspecified) {
                val wanted = from - cell
                val share = settle.value
                lag = Offset(wanted.x.coerceIn(-capX, capX), wanted.y.coerceIn(-capY, capY)) * share
                owed = if (lag == Offset.Zero) 0f else share
                if (owed == 0f) settle.stop()
            } else {
                // The ring appears in place: the grid opened, or came back from a panel.
                settle.stop()
                lag = Offset.Zero
                owed = 0f
            }
            armed = NONE
        } else if (owed > 0f) {
            // Each frame the lag is what is left between where the ring was drawn and where its cell is now, spent in
            // proportion to the glide: a cell that moves meanwhile (the grid lays the focused row out a frame after the
            // move, or cuts its glide short) never makes the ring jump, and a cell at rest gives the plain eased path.
            val share = settle.value
            lag = if (share <= 0f) Offset.Zero else (lastDrawn - cell) * (share / owed).coerceAtMost(1f)
            owed = share
        }
        lastDrawn = cell + lag
        return lag
    }

    /** No cell is focused (the grid lost the focus, a panel opened, the focused cell is off screen): the next one appears in place. */
    fun reset() {
        lastIndex = NONE
        lastDrawn = Offset.Unspecified
        lag = Offset.Zero
        owed = 0f
        armed = NONE
        if (settle.isSettling) settle.stop()
    }

    private companion object {
        const val NONE = -1
        const val NANOS_PER_MS = 1_000_000L
    }
}

/** A [FocusTravel] for one grid; it is a no-op with animations off. */
@Composable
fun rememberFocusTravel(): FocusTravel {
    val scope = rememberCoroutineScope()
    val clock = rememberUpdatedState(LocalGlideClock.current)
    val isOn = rememberUpdatedState(PlumTheme.motion.isOn)
    return remember(scope) { FocusTravel(Settle(scope), { clock.value() }, { isOn.value }) }
}
