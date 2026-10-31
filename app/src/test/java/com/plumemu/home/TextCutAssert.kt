package com.plumemu.home

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertTrue

/**
 * Fails when any placed text under [root] is drawn cut: its natural (intrinsic) width is wider than
 * the node it was given. `didOverflowWidth` is not used because the text layout from
 * `GetTextLayoutResult` is made without the screen's width. Returns how many
 * texts were checked, so a test can assert it looked at something.
 */
fun assertNoTextCut(
    root: SemanticsNode,
    what: String,
): Int {
    var seen = 0

    fun visit(node: SemanticsNode) {
        if (node.layoutInfo.isPlaced && SemanticsProperties.Text in node.config) {
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            // The text's natural width against the width it was given on screen.
            val natural = layouts.maxOf { it.multiParagraph.intrinsics.maxIntrinsicWidth }
            val given = node.boundsInRoot.width
            assertTrue("$what: '${node.config[SemanticsProperties.Text].joinToString()}' is cut ($natural > $given px)", natural <= given + 1f)
            seen++
        }
        node.children.forEach(::visit)
    }
    visit(root)
    return seen
}
