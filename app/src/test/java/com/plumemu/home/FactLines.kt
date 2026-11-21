package com.plumemu.home

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult

/** The facts of the details block in the order they are read, by their English labels. */
internal val FACT_ORDER = listOf("Genre", "Rating", "Played", "Last played", "Emulator", "Battery")

/** One fact line as laid out: its label's box and its value's box ([value] is null for the rating, which is spoken as one node). */
internal class FactLine(val name: String, val label: Rect, val value: Rect?, val valueLines: Int)

/**
 * Reads the fact lines under [root] for [labels] (in reading order); a label that is not placed (dropped, or no value) gives no line.
 * A value is the text to the right of its label on the same line; the rating's texts are hidden, so it is found by its spoken
 * "Rating, 8.5/10" and has no value box.
 */
internal fun readFactLines(root: SemanticsNode, labels: List<String> = FACT_ORDER): List<FactLine> {
    fun all(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::all)
    val nodes = all(root)
    val texts = nodes.filter { SemanticsProperties.Text in it.config && it.boundsInRoot.width > 0f }
    fun text(n: SemanticsNode) = n.config[SemanticsProperties.Text].joinToString("")
    fun spoken(n: SemanticsNode) = if (SemanticsProperties.ContentDescription in n.config) n.config[SemanticsProperties.ContentDescription].joinToString("") else ""
    fun lineCount(n: SemanticsNode): Int {
        val layouts = mutableListOf<TextLayoutResult>()
        n.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.firstOrNull()?.lineCount ?: 1
    }
    return labels.mapNotNull { name ->
        val label = texts.firstOrNull { text(it) == name }
        if (label == null) {
            val row = nodes.firstOrNull { spoken(it).startsWith("$name,") && it.boundsInRoot.width > 0f } ?: return@mapNotNull null
            return@mapNotNull FactLine(name, row.boundsInRoot, null, 1)
        }
        val box = label.boundsInRoot
        val value = texts.filter { text(it) !in labels && it.boundsInRoot.left >= box.right - 1f && it.boundsInRoot.center.y in box.top..box.bottom }.minByOrNull { it.boundsInRoot.left }
        FactLine(name, box, value?.boundsInRoot, value?.let(::lineCount) ?: 1)
    }
}

/**
 * The block's contract, as problems (empty when it holds): one fact to a line in reading order, top to bottom, no overlap; every label at the
 * box's left edge; every value at one x, after the labels; the lines at one pitch (within 1 px); every fact wholly inside [box]; every value on one line.
 */
internal fun factLineProblems(box: Rect, lines: List<FactLine>): List<String> {
    val problems = mutableListOf<String>()
    val names = lines.map { it.name }
    lines.zipWithNext().forEach { (a, b) ->
        if (b.label.top < a.label.bottom - 1f) problems += "${b.name} (${b.label.top}) is not below ${a.name} (${a.label.bottom})"
    }
    lines.forEach {
        if (kotlin.math.abs(it.label.left - box.left) > 1f) problems += "$names: ${it.name} starts at ${it.label.left}, not at the box's left ${box.left}"
        val whole = it.value?.let { v -> Rect(it.label.left, minOf(it.label.top, v.top), v.right, maxOf(it.label.bottom, v.bottom)) } ?: it.label
        if (whole.top < box.top - 1f || whole.bottom > box.bottom + 1f || whole.right > box.right + 1f) problems += "${it.name} $whole is not inside the box $box"
        if (it.valueLines != 1) problems += "${it.name}'s value takes ${it.valueLines} lines"
    }
    val valueLefts = lines.mapNotNull { it.value?.left }
    if (valueLefts.isNotEmpty() && valueLefts.max() - valueLefts.min() > 1f) problems += "the values start at different x: ${lines.map { it.name to it.value?.left }}"
    lines.forEach { if (it.value != null && it.value.left < it.label.right) problems += "${it.name}'s value starts inside its label" }
    val pitches = lines.zipWithNext { a, b -> b.label.top - a.label.top }
    if (pitches.isNotEmpty() && pitches.max() - pitches.min() > 1f) problems += "the lines are not evenly spaced: pitches $pitches"
    return problems
}
