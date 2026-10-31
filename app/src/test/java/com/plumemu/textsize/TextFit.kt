package com.plumemu.textsize

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
)

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
 * - overlapped by another text by more than a pixel in both directions.
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
        val mayEllipsize = (allow.isLargest && node.hasAncestorTagged(allow.ellipsisUnder)) || node.hasAncestorTagged(allow.cutToFit)
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
    for (a in texts.indices) {
        for (b in a + 1 until texts.size) {
            val x = texts[a].second.boundsInRoot
            val y = texts[b].second.boundsInRoot
            val overlapW = minOf(x.right, y.right) - maxOf(x.left, y.left)
            val overlapH = minOf(x.bottom, y.bottom) - maxOf(x.top, y.top)
            // A system count under the letter rail is faded out (games focused): the rail takes its place in the peek.
            val countUnderRail = (texts[a].second.hasAncestorTagged(SystemRowTags.COUNT) && texts[b].second.hasAncestorTagged(HomeTags.RAIL)) ||
                (texts[b].second.hasAncestorTagged(SystemRowTags.COUNT) && texts[a].second.hasAncestorTagged(HomeTags.RAIL))
            if (overlapW > 1f && overlapH > 1f && !countUnderRail) problems += "'${texts[a].first}' overlaps '${texts[b].first}' ($overlapW x $overlapH px)"
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

/** Why [node], the focused item, is not fully on screen; null when it is. */
internal fun notFullyShown(
    node: SemanticsNode,
    root: SemanticsNode,
): String? {
    val shown = node.boundsInRoot
    val screen = root.boundsInRoot
    return when {
        shown.height + 1f < node.size.height || shown.width + 1f < node.size.width -> "is cut (${shown.width} x ${shown.height} of ${node.size.width} x ${node.size.height} px)"
        shown.top < screen.top - 1f || shown.bottom > screen.bottom + 1f -> "is off the screen (${shown.top}..${shown.bottom} of ${screen.top}..${screen.bottom})"
        else -> null
    }
}

private const val LINE_LEADING_SLACK = 0.12f
private const val PEEK_SLIVER = 0.25f
