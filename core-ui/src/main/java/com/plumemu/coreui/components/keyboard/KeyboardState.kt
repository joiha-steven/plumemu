package com.plumemu.coreui.components.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * A [KeyboardModel] Compose can watch, for [PlumKeyboard]. The rules are the model's; this only
 * keeps the current one. Readers see [page], [shift] and [focus] through derived states, so a
 * focus move recomposes only the two keys whose focus changed, and a shift change only
 * the keys whose label or look depends on it.
 *
 * The caller drives it: [move] and [press] from its gamepad actions, [toggleShift] and
 * [togglePage] from the buttons it gives those (SELECT and Y); taps come through
 * [PlumKeyboard] itself.
 */
@Stable
class KeyboardState(
    start: KeyboardModel,
) {
    var model: KeyboardModel by mutableStateOf(start)
        private set

    val hasShift: Boolean = start.hasShift

    val page: KeyboardPage by derivedStateOf { model.page }
    val shift: ShiftMode by derivedStateOf { model.shift }
    val focus: KeyId by derivedStateOf { model.focus }

    /** Moves the focus; see [KeyboardModel.move]. On [KeyboardMove.LeftAbove] or [KeyboardMove.LeftBelow] the caller decides. */
    fun move(
        direction: KeyboardDirection,
        isRepeat: Boolean = false,
    ): KeyboardMove {
        val (next, result) = model.move(direction, isRepeat)
        model = next
        return result
    }

    /** Presses the focused key (A); null for a page or shift key, which act here. */
    fun press(): KeyboardEvent? = apply(model.press())

    /** Focuses [key] and presses it: a tap. */
    fun tap(key: KeyId): KeyboardEvent? = apply(model.tap(key))

    fun toggleShift() {
        model = model.toggleShift()
    }

    fun togglePage() {
        model = model.togglePage()
    }

    /** Whether [key] has the focus, as a state of its own: it changes only when that answer does. */
    internal fun isFocused(key: KeyId): State<Boolean> = derivedStateOf { focus == key }

    private fun apply(result: Pair<KeyboardModel, KeyboardEvent?>): KeyboardEvent? {
        model = result.first
        return result.second
    }
}

/** A [KeyboardState] for [spec] (its start page, shift or not), kept while those stay the same. */
@Composable
fun rememberKeyboardState(spec: KeyboardSpec): KeyboardState =
    remember(spec.hasShift, spec.startPage) { KeyboardState(spec.startModel()) }
