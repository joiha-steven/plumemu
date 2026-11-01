package com.plumemu.keyboard

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import com.plumemu.coreui.theme.PlumTheme
import kotlin.math.roundToInt

/**
 * A field typed with Plumemu's keyboard: [text] in [style], or the muted [placeholder] when empty,
 * and a static accent caret at [caret] while [showsCaret] (it never blinks, so nothing
 * runs while the person reads). Text wider than its room slides left so the caret stays in view.
 * No text field: nothing here can bring Android's keyboard up. A tap gives [onTapAt] the tapped
 * character's offset (a caret position). [caret] and [showsCaret] are read while drawing and laying
 * out: a caret move redraws, it does not recompose. Search's query and the game panel's fields.
 *
 * A long press does what a tap does and then, while the clipboard holds text and the field takes a
 * paste ([onPaste]), offers Paste in Android's own floating text toolbar, as Android's fields do.
 */
@Composable
internal fun CaretLine(
    text: String,
    caret: () -> Int,
    showsCaret: () -> Boolean,
    placeholder: String,
    style: TextStyle,
    onTapAt: (Int) -> Unit,
    modifier: Modifier,
    textTag: String,
    placeholderTag: String,
    /** False: the line shows the start of the text, not slid to the caret (a name that is not being typed). Read while laying out. */
    followsCaret: () -> Boolean = { true },
    /** Inserts the clipboard at the caret; null: a long press offers nothing. */
    onPaste: (() -> Unit)? = null,
) {
    val colors = PlumTheme.colors
    val caretWidth = PlumTheme.layout.caretWidth
    val line = remember { CaretLineLayout() }
    // Read when the tap lands, not when the gesture started listening: the text changes under it.
    val currentText by rememberUpdatedState(text)
    val caretMark =
        Modifier.drawWithContent {
            drawContent()
            if (showsCaret()) {
                val laidOut = line.text ?: return@drawWithContent
                val at = if (text.isEmpty()) 0 else caret().coerceIn(0, text.length)
                val x = if (text.isEmpty()) 0f else laidOut.getHorizontalPosition(at, usePrimaryDirection = true)
                val width = caretWidth.toPx()
                drawRect(colors.accent, Offset((x - width / 2).coerceAtLeast(0f), laidOut.getLineTop(0)), Size(width, laidOut.getLineBottom(0) - laidOut.getLineTop(0)))
            }
        }
    val toolbar = LocalTextToolbar.current
    val currentOnPaste by rememberUpdatedState(onPaste)
    // The menu goes with the field, with any edit (it was for the caret where the finger was) and as soon as
    // typing leaves this line (the cursor moved to another field): it pastes only into the line pressed.
    DisposableEffect(toolbar) { onDispose { line.hideMenu() } }
    LaunchedEffect(text) { line.hideMenu() }
    LaunchedEffect(toolbar) { snapshotFlow { showsCaret() }.collect { if (!it) line.hideMenu() } }
    Box(
        modifier
            .clipToBounds()
            .onPlaced { line.coordinates = it }
            .pointerInput(onTapAt) {
                detectTapGestures(
                    onTap = { at ->
                        line.hideMenu()
                        onTapAt(line.offsetAt(at.x, currentText))
                    },
                    onLongPress = { at ->
                        onTapAt(line.offsetAt(at.x, currentText))
                        currentOnPaste?.let { paste -> line.offerPaste(toolbar, at) { if (showsCaret()) paste() } }
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        if (text.isEmpty()) {
            BasicText(
                placeholder,
                style = style.copy(color = colors.muted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = {
                    line.text = it
                    line.shift = 0
                },
                modifier = caretMark.testTag(placeholderTag),
            )
        } else {
            BasicText(
                text,
                style = style,
                maxLines = 1,
                softWrap = false,
                onTextLayout = { line.text = it },
                modifier =
                    Modifier
                        .layout { measurable, constraints ->
                            // Laid out at its full width, then slid left just enough to show the caret.
                            val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
                            val caretX = line.text?.getHorizontalPosition(caret().coerceIn(0, text.length), usePrimaryDirection = true) ?: 0f
                            val shift = if (followsCaret()) (caretX + caretWidth.toPx() - constraints.maxWidth).coerceAtLeast(0f).roundToInt() else 0
                            line.shift = shift
                            layout(minOf(placeable.width, constraints.maxWidth), placeable.height) { placeable.place(-shift, 0) }
                        }.then(caretMark)
                        .testTag(textTag),
            )
        }
    }
}

/** What the line last laid out, for the caret and for taps (plain, not state: nothing draws from a change of it). */
private class CaretLineLayout {
    var text: TextLayoutResult? = null
    var shift: Int = 0

    /** Where the line is, for the paste menu's place. */
    var coordinates: LayoutCoordinates? = null

    /** Android's floating menu with Paste only, over the line at [at], while the clipboard holds text. */
    fun offerPaste(
        toolbar: TextToolbar,
        at: Offset,
        paste: () -> Unit,
    ) {
        val coordinates = coordinates?.takeIf { it.isAttached } ?: return
        if (!PasteBoard.hasText) return
        val point = coordinates.localToRoot(at)
        val bounds = coordinates.boundsInRoot()
        toolbar.showMenu(
            rect = Rect(point.x, bounds.top, point.x, bounds.bottom),
            onCopyRequested = null,
            onPasteRequested = {
                hideMenu()
                paste()
            },
            onCutRequested = null,
            onSelectAllRequested = null,
        )
        PasteBoard.menuOpened(toolbar, owner = this)
    }

    /** Closes the paste menu if this line opened it (another line's is not this line's to close). */
    fun hideMenu() {
        if (PasteBoard.ownsMenu(this)) PasteBoard.closeMenu()
    }

    /** The character offset nearest [x] (from the line's start), as a caret position in [text]. */
    fun offsetAt(
        x: Float,
        text: String,
    ): Int {
        if (text.isEmpty()) return 0
        val layout = this.text ?: return text.length
        return layout.getOffsetForPosition(Offset(x + shift, (layout.getLineTop(0) + layout.getLineBottom(0)) / 2)).coerceIn(0, text.length)
    }
}
