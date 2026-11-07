package com.plumemu.textsize

import com.plumemu.settings.screen.SettingsTags
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import com.plumemu.coreui.components.SystemRowTags
import com.plumemu.home.HomeTags

/** What a screen's check may let through; everything else is a problem. */
internal class FitAllowance(
    /**
     * Tags of lists that scroll: a text inside one may be cut at the top or bottom of the list (the
     * list continues), and nowhere else.
     */
    val scrollTags: Set<String> = emptySet(),
    /** Tag of a region whose texts may be ellipsized, at the largest size only (Info's values). */
    val ellipsisUnder: String? = null,
    val isLargest: Boolean = false,
    /** Tags of the home strip lists: a text may run out of the right edge or be a sliver under the left one. */
    val stripTags: Set<String> = emptySet(),
    /**
     * Tag of a text cut to the height it is given by design, at every size: the details' synopsis
     * takes the lines left in the column and ends its last whole line with an ellipsis
     * (`HomeDetails.Synopsis`). It may be ellipsized; it is still checked for clipping, overlap and
     * a line's height.
     */
    val cutToFit: String? = null,
    /**
     * Tags of a typed text (`CaretLine`) that slides left to keep the caret in view when it is wider than its room:
     * cut at its sides by design, as a long name always is. A name that merely does not fit beside a field's label
     * (Fix box art's "Name" at the large text sizes) is the same thing.
     */
    val slidingText: Set<String> = emptySet(),
    /** Tags of texts that are one line with an ellipsis by design, at every size (the details' subtitle and fact lines, the emulator's name, the facts table's values). */
    val oneLine: Set<String> = emptySet(),
    /** Exact texts that may end in an ellipsis wherever they are: one named case of a long title at a size and font where no mechanism leaves it room. */
    val ellipsisTexts: Set<String> = emptySet(),
    /**
     * Tags of the bars the home lists and grids run under (design/rules.md › Lists that scroll): a text in a [scrollTags] list may overlap a text in one of these
     * bars, as a row going under the bar does. Two texts of the lists, or two of the bars, may not.
     */
    val barTags: Set<String> = emptySet(),
)

/** Game titles the home list cuts at Largest in Serif and Mono: the list's room is about 3 dp short of the whole title there, with the year alone and nothing left to give up. */
// Rows now show under both bars, so more of the fixture is on screen: its Zelda and its filler titles.
internal val LONG_TITLES = setOf("Donkey Kong Country 2", "Zelda: A Link to the Past") + (9..99).map { "Super Nintendo title $it" }

/** The details column's texts that hold one line and end in an ellipsis rather than grow: a late value must not move anything. */
internal val DETAILS_ONE_LINE = setOf(HomeTags.DETAILS_SUBTITLE, HomeTags.DETAILS_FACT_LINE, HomeTags.DETAILS_EMULATOR, HomeTags.DETAILS_FACT_VALUE)

private fun SemanticsNode.hasAncestorTagged(tag: String?): Boolean =
    tag != null && generateSequence(this) { it.parent }.any { SemanticsProperties.TestTag in it.config && it.config[SemanticsProperties.TestTag] == tag }

private fun SemanticsNode.isUnder(tags: Set<String>): Boolean = tags.any { hasAncestorTagged(it) }

/**
 * What is wrong with the text under [root] (unmerged tree), as readable lines; empty when every text
 * fits. A text is a problem when it is:
 * - ellipsized, or wider than the node it was given (single line);
 * - clipped sideways by a parent, unless it is in a home strip list ([FitAllowance.stripTags]) and runs out of the
 *   screen's right edge (the strip continuing under its fade) or is a system name left as a sliver under the left edge
 *   (hidden in the peek);
 * - clipped above or below, unless it sits in a list that scrolls ([FitAllowance.scrollTags]);
 * - shorter than one line, everywhere (a node's size does not depend on the scroll position), by more than the
 *   12% a font's line box keeps as leading around its glyphs: the bars are 18 and 22 dp for Plum Sans, and Serif
 *   and Mono lines are taller;
 * - overlapped by another text by more than a pixel in both directions, unless one is a row of a scrolling list and the other is in a bar the list
 *   runs under ([FitAllowance.barTags]).
 * A text with nothing drawn (a row scrolled out of view) is not on screen and is skipped.
 */
internal fun textProblems(
    root: SemanticsNode,
    allow: FitAllowance,
): List<String> {
    val texts = mutableListOf<Triple<String, SemanticsNode, TextLayoutResult>>()

    fun visit(node: SemanticsNode) {
        if (node.layoutInfo.isPlaced && SemanticsProperties.Text in node.config) {
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val layout = layouts.firstOrNull()
            val shown = node.boundsInRoot
            if (layout != null && shown.width > 0f && shown.height > 0f) texts += Triple(node.config[SemanticsProperties.Text].joinToString(), node, layout)
        }
        node.children.forEach(::visit)
    }
    visit(root)

    val problems = mutableListOf<String>()
    val screenRight = root.boundsInRoot.right
    for ((text, node, layout) in texts) {
        val shown = node.boundsInRoot
        val mayEllipsize = (allow.isLargest && node.hasAncestorTagged(allow.ellipsisUnder)) || node.hasAncestorTagged(allow.cutToFit) || node.isUnder(allow.oneLine) || text in allow.ellipsisTexts
        val inScrollingList = node.isUnder(allow.scrollTags)
        val inStrip = node.isUnder(allow.stripTags)
        val slides = node.isUnder(allow.slidingText)
        if (!mayEllipsize && (0 until layout.lineCount).any { layout.isLineEllipsized(it) }) problems += "'$text' is ellipsized"
        val natural = layout.multiParagraph.intrinsics.maxIntrinsicWidth
        if (!mayEllipsize && layout.lineCount == 1 && natural > node.size.width + 1f) problems += "'$text' is wider than its node ($natural > ${node.size.width} px)"
        // Under the left edge, a text with only a sliver in view is a system name hidden in the peek by design.
        val isHiddenInPeek = inStrip && shown.left <= root.boundsInRoot.left + 1f && shown.width < PEEK_SLIVER * node.size.width
        val isClippedSideways = shown.width + 1f < node.size.width && (!inStrip || shown.right < screenRight - 1f) && !isHiddenInPeek && !slides
        val isClippedAbove = shown.height + 1f < node.size.height
        if (isClippedSideways || (isClippedAbove && !inScrollingList)) {
            problems += "'$text' is clipped by a parent (${shown.width} x ${shown.height} of ${node.size.width} x ${node.size.height} px)"
        }
        val firstLine = layout.getLineBottom(0) - layout.getLineTop(0)
        if (node.size.height + 1f < firstLine * (1f - LINE_LEADING_SLACK)) problems += "'$text' is shorter than one line (${node.size.height} < $firstLine px)"
    }
    // A panel list erases the room past an edge it continues over ([PanelListFades]), and past an edge it does not no row lies: its text draws only inside the list.
    val panelList = generateSequence(listOf(root)) { level -> level.flatMap { it.children }.takeIf { it.isNotEmpty() } }.flatten()
        .firstOrNull { SemanticsProperties.TestTag in it.config && it.config[SemanticsProperties.TestTag] == SettingsTags.PANEL_LIST }
    fun drawn(node: SemanticsNode) = if (panelList != null && node.hasAncestorTagged(SettingsTags.PANEL_LIST)) node.boundsInRoot.intersect(panelList.boundsInRoot) else node.boundsInRoot
    for (a in texts.indices) {
        for (b in a + 1 until texts.size) {
            val x = drawn(texts[a].second)
            val y = drawn(texts[b].second)
            val overlapW = minOf(x.right, y.right) - maxOf(x.left, y.left)
            val overlapH = minOf(x.bottom, y.bottom) - maxOf(x.top, y.top)
            // A system count under the letter rail is faded out (games focused): the rail takes its place in the peek.
            val countUnderRail = (texts[a].second.hasAncestorTagged(SystemRowTags.COUNT) && texts[b].second.hasAncestorTagged(HomeTags.RAIL)) ||
                (texts[b].second.hasAncestorTagged(SystemRowTags.COUNT) && texts[a].second.hasAncestorTagged(HomeTags.RAIL))
            val nodeA = texts[a].second
            val nodeB = texts[b].second
            val rowUnderBar = (nodeA.isUnder(allow.scrollTags) && nodeB.isUnder(allow.barTags)) || (nodeB.isUnder(allow.scrollTags) && nodeA.isUnder(allow.barTags))
            if (overlapW > 1f && overlapH > 1f && !countUnderRail && !rowUnderBar) problems += "'${texts[a].first}' overlaps '${texts[b].first}' ($overlapW x $overlapH px)"
        }
    }
    return problems
}

/** The first node under [root] with [tag], or null. */
internal fun findTagged(
    root: SemanticsNode,
    tag: String,
): SemanticsNode? {
    if (SemanticsProperties.TestTag in root.config && root.config[SemanticsProperties.TestTag] == tag) return root
    return root.children.firstNotNullOfOrNull { findTagged(it, tag) }
}

/**
 * Why [node], the focused item, is not fully on screen; null when it is. [trailingGapPx] is a gap the node carries
 * under its drawn part (a game row's node holds the gap after it); the last row of a list may leave that gap outside.
 */
internal fun notFullyShown(
    node: SemanticsNode,
    root: SemanticsNode,
    trailingGapPx: Float = 0f,
): String? {
    val shown = node.boundsInRoot
    val screen = root.boundsInRoot
    return when {
        shown.height + 1f < node.size.height - trailingGapPx || shown.width + 1f < node.size.width -> "is cut (${shown.width} x ${shown.height} of ${node.size.width} x ${node.size.height} px)"
        shown.top < screen.top - 1f || shown.bottom > screen.bottom + 1f -> "is off the screen (${shown.top}..${shown.bottom} of ${screen.top}..${screen.bottom})"
        else -> null
    }
}

private const val LINE_LEADING_SLACK = 0.12f
private const val PEEK_SLIVER = 0.25f

/**
 * Why [cell] of the facts [box] is not on show; null when it is placed and lies inside the box. A cell the box has no room for is
 * measured but not placed, so a node that exists (even with a width) proves nothing: its place is checked.
 */
internal fun notShownInBox(
    cell: SemanticsNode,
    box: SemanticsNode,
): String? {
    val c = cell.boundsInRoot
    val b = box.boundsInRoot
    return when {
        !cell.layoutInfo.isPlaced -> "is not placed"
        c.width <= 0f || c.height <= 0f -> "has no size"
        c.left < b.left - 1f || c.top < b.top - 1f || c.right > b.right + 1f || c.bottom > b.bottom + 1f -> "lies outside the box ($c in $b)"
        else -> null
    }
}
