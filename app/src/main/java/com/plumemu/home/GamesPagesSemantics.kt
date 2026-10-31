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
internal fun Modifier.rowsInSemanticsOnlyWhen(shown: () -> Boolean): Modifier = this then PageSemanticsElement(shown)

private data class PageSemanticsElement(
    val shown: () -> Boolean,
) : ModifierNodeElement<PageSemanticsNode>() {
    override fun create() = PageSemanticsNode(shown)

    override fun update(node: PageSemanticsNode) {
        node.shown = shown
        node.follow()
    }
}

private class PageSemanticsNode(
    var shown: () -> Boolean,
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
        testTag = HomeTags.GAMES_PAGE
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
): Modifier = this then GameRowTagElement(index, tagged)

private data class GameRowTagElement(
    val index: Int,
    val tagged: () -> Boolean,
) : ModifierNodeElement<GameRowTagNode>() {
    override fun create() = GameRowTagNode(index, tagged)

    override fun update(node: GameRowTagNode) {
        node.index = index
        node.tagged = tagged
        node.follow()
    }
}

private class GameRowTagNode(
    var index: Int,
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
        if (isTagged) testTag = HomeTags.game(index)
    }
}
