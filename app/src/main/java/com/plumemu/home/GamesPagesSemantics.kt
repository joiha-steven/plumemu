package com.plumemu.home

import androidx.compose.ui.Modifier
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.invalidateSemantics
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.testTag

/*
 * What the semantics tree shows of the games pages (GamesPages.kt): a page kept beside the shown
 * one is built and laid out but must not be found by tests or accessibility. Both nodes follow
 * the shown page while building the semantics, without recomposing.
 */

/**
 * Takes the rows of a page out of the merged semantics tree while it is not [shown] (read while
 * building the semantics, without recomposing): a page kept beside the shown one is built and laid
 * out, and `onNodeWithText(title)` must find the shown page's row only. The page itself stays, as
 * [HomeTags.GAMES_PAGE]. Compose keeps cleared nodes in the unmerged tree, so the rows also drop
 * their tags there ([gameRowTag]); a title searched with `useUnmergedTree` can still be a kept page's.
 */
internal fun Modifier.rowsInSemanticsOnlyWhen(
    shown: () -> Boolean,
    tag: String = HomeTags.GAMES_PAGE,
): Modifier = this then PageSemanticsElement(shown, tag)

private data class PageSemanticsElement(
    val shown: () -> Boolean,
    val tag: String,
) : ModifierNodeElement<PageSemanticsNode>() {
    override fun create() = PageSemanticsNode(shown, tag)

    override fun update(node: PageSemanticsNode) {
        node.shown = shown
        node.follow()
    }
}

private class PageSemanticsNode(
    var shown: () -> Boolean,
    val tag: String,
) : Modifier.Node(),
    SemanticsModifierNode,
    ObserverModifierNode {
    private var isShown = false

    override val shouldClearDescendantSemantics: Boolean get() = !isShown

    override fun onAttach() = follow()

    override fun onObservedReadsChanged() = follow()

    fun follow() {
        observeReads { isShown = shown() }
        invalidateSemantics()
    }

    override fun SemanticsPropertyReceiver.applySemantics() {
        testTag = tag
    }
}

/**
 * [HomeTags.game] for row [index], only while its page is shown ([tagged], read while building
 * the semantics): a kept page's rows are still in the unmerged semantics tree
 * ([rowsInSemanticsOnlyWhen] clears only the merged one), and a test finding `game(i)` in either
 * tree must find one row. Follows [tagged] without recomposing the row.
 */
internal fun Modifier.gameRowTag(
    index: Int,
    tagged: () -> Boolean,
): Modifier = tagWhen(HomeTags.game(index), tagged)

/** [tag] on this node only while [tagged] says so (read while building the semantics, without recomposing): what a page kept beside the shown one must not show. */
internal fun Modifier.tagWhen(
    tag: String,
    tagged: () -> Boolean,
): Modifier = this then TagWhenElement(tag, tagged)

private data class TagWhenElement(
    val tag: String,
    val tagged: () -> Boolean,
) : ModifierNodeElement<TagWhenNode>() {
    override fun create() = TagWhenNode(tag, tagged)

    override fun update(node: TagWhenNode) {
        node.tag = tag
        node.tagged = tagged
        node.follow()
    }
}

private class TagWhenNode(
    var tag: String,
    var tagged: () -> Boolean,
) : Modifier.Node(),
    SemanticsModifierNode,
    ObserverModifierNode {
    private var isTagged = false

    override fun onAttach() = follow()

    override fun onObservedReadsChanged() = follow()

    fun follow() {
        observeReads { isTagged = tagged() }
        invalidateSemantics()
    }

    override fun SemanticsPropertyReceiver.applySemantics() {
        if (isTagged) testTag = tag
    }
}

/** Whether the grid page being composed is the shown one (read while building the semantics); a grid not in a page of [GridPages] counts as shown. */
internal val LocalGridPageShown = androidx.compose.runtime.staticCompositionLocalOf<() -> Boolean> { { true } }

/** [tag] for a grid's node, only while its page is shown ([LocalGridPageShown]): a grid built ahead is in the unmerged tree and must not carry the tags tests and screen readers find. */
@androidx.compose.runtime.Composable
internal fun Modifier.gridTag(tag: String): Modifier = tagWhen(tag, LocalGridPageShown.current)
