package com.plumemu.home

import android.util.Log
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.plumemu.coreinput.Column as StripColumn
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.theme.PlumTheme
import kotlin.math.abs

/**
 * The system peek's A to Z rail while the focus is in a games list: the
 * letters of the focused list, the focused game's letter in the accent. It fades in as the strip slides
 * to the peek and out as the column opens (the names and counts take its place), keeping its letters while it fades. Not focusable; the
 * letters are equal slots top to bottom ([HomeTags.railLetter]) so a touch can find one by its position.
 *
 * The focus is read only here, in derived states: a move inside a letter recomposes nothing, a move to
 * another letter recomposes the two letters involved.
 */
@Composable
internal fun LetterRail(
    content: HomeContent,
    state: StripState,
    modifier: Modifier = Modifier,
) {
    // While the systems are focused the rail keeps the letters it had and fades out with the column (alpha below);
    // it reads only the column then, so scrolling the systems rebuilds nothing. The holders are written in the
    // derivations on purpose: the last games-side value is what the fading rail still shows.
    val held = remember(state, content) { HeldRail() }
    val letters by remember(state, content) {
        derivedStateOf {
            if (state.focus.column == StripColumn.Systems) {
                held.letters
            } else {
                content.entries.getOrNull(state.focus.systemIndex)?.railLetters.orEmpty().also { held.letters = it }
            }
        }
    }
    if (letters.isEmpty()) return
    val focusedLetter = remember(state, content) {
        derivedStateOf {
            if (state.focus.column == StripColumn.Systems) {
                held.focused
            } else {
                val entry = content.entries.getOrNull(state.focus.systemIndex)
                val row = state.focus.row
                (if (entry == null || row < entry.railStart) null else entry.rows.getOrNull(row)?.let(LetterKey::of)).also { held.focused = it }
            }
        }
    }
    // Only while a games list is focused does the rail take touches; with the systems focused (the rail is fading
    // out) the peek's touches go to the system rows as before.
    val touchable by remember(state) { derivedStateOf { state.focus.column != StripColumn.Systems } }
    BoxWithConstraints(modifier = modifier.testTag(HomeTags.RAIL).clipToBounds().graphicsLayer { alpha = 1f - state.systemNamesAlpha }) {
        val capacity = railCapacity(maxHeight, PlumTheme.layout.railLetterMinHeight)
        val shown by remember(letters, capacity) { derivedStateOf { railWindow(letters, focusedLetter.value, capacity) } }
        val shownNow by rememberUpdatedState(shown)
        val contentNow by rememberUpdatedState(content)
        val jump by rememberUpdatedState<(Char) -> Unit> { letter ->
            val entry = contentNow.entries.getOrNull(state.focus.systemIndex)
            val row = entry?.let { letterStartRow(it.rows, letter, it.railStart) }
            if (row != null) state.focusRow(row) else Log.d(TAG, "Letter rail: no row for $letter")
        }
        val touch =
            if (touchable) {
                Modifier.railTouch(state, { shownNow }) { jump(it) }
            } else {
                Modifier
            }
        Column(Modifier.fillMaxSize().then(touch)) {
            for (letter in shown) key(letter) { RailLetter(letter, focusedLetter, Modifier.weight(1f)) }
        }
    }
}

/**
 * Touch on the rail: a tap on a letter, or a vertical drag along the rail, calls [jump] with the letter under the
 * finger (equal slots over the height, the ends clamped); a drag calls it only when the letter changes. A horizontal
 * move is left to the strip's own drag. The slots are the drawn slots, so no touch area is smaller than what is drawn.
 */
private fun Modifier.railTouch(
    key: Any,
    letters: () -> List<Char>,
    jump: (Char) -> Unit,
): Modifier =
    pointerInput(key) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // The window is frozen at touch-down: the drawn rail re-centres on each jump, and the finger must keep
            // meaning the letter that was under it when it landed.
            val shown = letters()
            fun letterAt(y: Float): Char? {
                if (shown.isEmpty()) return null
                return shown[(y / size.height * shown.size).toInt().coerceIn(0, shown.lastIndex)]
            }
            var dragging = false
            var last: Char? = null
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (change.isConsumed) return@awaitEachGesture
                if (!change.pressed) {
                    if (!dragging) {
                        letterAt(down.position.y)?.let(jump)
                        change.consume()
                    }
                    return@awaitEachGesture
                }
                val dy = change.position.y - down.position.y
                val dx = change.position.x - down.position.x
                if (!dragging && abs(dy) > viewConfiguration.touchSlop && abs(dy) > abs(dx)) dragging = true
                if (dragging) {
                    change.consume()
                    val letter = letterAt(change.position.y)
                    if (letter != null && letter != last) {
                        last = letter
                        jump(letter)
                    }
                }
            }
        }
    }

/** The games-side rail the fading rail still shows after the focus went to the systems. */
private class HeldRail {
    var letters: List<Char> = emptyList()
    var focused: Char? = null
}

/** How many letters fit in [height] at [minHeight] each (at least one). */
internal fun railCapacity(
    height: Dp,
    minHeight: Dp,
): Int = (height / minHeight).toInt().coerceAtLeast(1)

@Composable
private fun RailLetter(
    letter: Char,
    focusedLetter: State<Char?>,
    modifier: Modifier,
) {
    val isFocused by remember(letter) { derivedStateOf { focusedLetter.value == letter } }
    val colors = PlumTheme.colors
    val style = if (isFocused) PlumTheme.type.smallFactCurrent.copy(color = colors.accent) else PlumTheme.type.meta.copy(color = colors.muted)
    Box(modifier.fillMaxSize().testTag(HomeTags.railLetter(letter)), contentAlignment = Alignment.Center) {
        // The slot may be a little shorter than the line: the capital is centred and the line may overhang.
        BasicText(letter.toString(), Modifier.wrapContentHeight(Alignment.CenterVertically, unbounded = true), style = style, maxLines = 1)
    }
}
