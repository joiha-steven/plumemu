package com.plumemu.coreinput

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.plumemu.coreinput.strip.StripState

/*
 * Touch turned into the same actions as buttons. Pass `router::send` as
 * onAction so taps reach screens through `InputRouter.actions` like any key. Vertical swipes and
 * flings are the lists' own scrolling, not actions.
 */

/**
 * A row, cover or column item: a tap sends [Action.TapItem] (the focus state focuses it, or plays
 * it when already focused), a long press sends [Action.LongPressItem] (on a game its menu, on the
 * system column Settings). Also reachable by accessibility services through the same semantics.
 */
fun Modifier.tapItem(
    column: Column,
    index: Int,
    onAction: (Action) -> Unit,
): Modifier =
    semantics {
        onClick {
            onAction(Action.TapItem(column, index))
            true
        }
        onLongClick {
            onAction(Action.LongPressItem(column, index))
            true
        }
    }.pointerInput(column, index, onAction) {
        detectTapGestures(
            onTap = { onAction(Action.TapItem(column, index)) },
            onLongPress = { onAction(Action.LongPressItem(column, index)) },
        )
    }

/** A bottom-bar hint: a tap does what its button does ("A Play" sends [Action.Play]). */
fun Modifier.hint(
    action: Action,
    onAction: (Action) -> Unit,
): Modifier =
    semantics {
        role = Role.Button
        onClick {
            onAction(action)
            true
        }
    }.pointerInput(action, onAction) {
        detectTapGestures(onTap = { onAction(action) })
    }

/**
 * Put on the strip's container: a horizontal drag moves the strip with the finger and, on
 * release, settles on the nearest stop (or the next one after a fling), focusing that column.
 */
fun Modifier.stripDrag(state: StripState): Modifier =
    draggable(
        state = state.draggableState,
        orientation = Orientation.Horizontal,
        onDragStarted = { state.stopAnimation() },
        onDragStopped = { velocity -> state.settle(velocity) },
    )
