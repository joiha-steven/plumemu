package com.plumemu.coreui.components.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.text.TextLayoutResult
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import androidx.compose.ui.unit.dp

internal const val KEYBOARD_TAG = "keyboard"

internal val TEST_LABELS = KeyboardLabels(space = "Space", shift = "Shift", delete = "Delete", hide = "Hide keyboard")

/** Every key cell of the page shown. */
internal val isKeyCell = SemanticsMatcher("a key cell") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("keyboard_key_") == true }

/**
 * The keyboard on a screen-sized background, at the bottom. With [trayToBottom] the tray runs on
 * under the keys as far as Search's will (`keyboardTrayHeight`: the 6 gap, the hint bar and the
 * bottom margin), as a caller gives it; without, the keyboard has exactly its own height and the screen's background
 * shows under it (where its last row's cells reach 2 dp past it).
 */
@Composable
internal fun KeyboardOnScreen(
    state: KeyboardState,
    spec: KeyboardSpec,
    trayToBottom: Boolean = true,
    onEvent: (KeyboardEvent) -> Unit = {},
) {
    val layout = PlumTheme.layout
    Box(Modifier.fillMaxSize().background(PlumTheme.colors.bg)) {
        val below = layout.keyboardTrayHeight - layout.keyboardHeight
        PlumKeyboard(
            state = state,
            spec = spec,
            labels = TEST_LABELS,
            onEvent = onEvent,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = if (trayToBottom) 0.dp else below)
                    .testTag(KEYBOARD_TAG)
                    .height(layout.keyboardHeight + if (trayToBottom) below else 0.dp),
        )
    }
}

/** The id of the key that shows [label] ("SHIFT", "DEL", "HIDE", "SPACE", "ACTION" for the others). */
internal fun KeyboardModel.idOf(label: String): KeyId {
    rows.forEachIndexed { r, row ->
        row.forEachIndexed { i, slot ->
            val shown =
                when (val key = slot.key) {
                    is KeyboardKey.Character -> key.char.toString()
                    is KeyboardKey.Page -> key.label
                    KeyboardKey.Shift -> "SHIFT"
                    KeyboardKey.Delete -> "DEL"
                    KeyboardKey.Hide -> "HIDE"
                    KeyboardKey.Space -> "SPACE"
                    KeyboardKey.Action -> "ACTION"
                }
            if (shown == label) return KeyId(r, i)
        }
    }
    throw AssertionError("no key '$label' on $page")
}

/**
 * What is wrong with the key labels under [root] (unmerged tree), as readable lines: a label
 * ellipsized, wider than the node it was given, clipped by a parent, taller than its key, or
 * outside its key (the cell less half a gap on each side). Also returns how many labels it read.
 */
internal fun labelProblems(
    root: SemanticsNode,
    halfGapPx: Float,
): Pair<List<String>, Int> {
    val problems = mutableListOf<String>()
    var seen = 0

    fun cellOf(node: SemanticsNode): SemanticsNode? =
        generateSequence(node) { it.parent }.firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("keyboard_key_") == true }

    fun visit(node: SemanticsNode) {
        if (node.layoutInfo.isPlaced && SemanticsProperties.Text in node.config) {
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val layout = layouts.first()
            val text = node.config[SemanticsProperties.Text].joinToString()
            seen++
            if ((0 until layout.lineCount).any { layout.isLineEllipsized(it) }) problems += "'$text' is ellipsized"
            val natural = layout.multiParagraph.intrinsics.maxIntrinsicWidth
            if (natural > node.size.width + 1f) problems += "'$text' is wider than its node ($natural > ${node.size.width} px)"
            val shown = node.boundsInRoot
            if (shown.width + 1f < node.size.width || shown.height + 1f < node.size.height) problems += "'$text' is clipped ($shown of ${node.size})"
            val cell = cellOf(node)?.boundsInRoot
            if (cell == null) {
                problems += "'$text' is not in a key"
            } else {
                val inKey =
                    shown.left >= cell.left + halfGapPx - 1f && shown.right <= cell.right - halfGapPx + 1f &&
                        shown.top >= cell.top + halfGapPx - 1f && shown.bottom <= cell.bottom - halfGapPx + 1f
                if (!inKey) problems += "'$text' ($shown) runs out of its key ($cell less $halfGapPx px)"
            }
        }
        node.children.forEach(::visit)
    }
    visit(root)
    return problems to seen
}
