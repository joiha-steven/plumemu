package com.plumemu.home

private const val NBSP = ' '

/** One system and its count as one unbreakable word: a line never ends between "GB" and "1" (nor inside "Mega Drive"). */
internal fun systemItem(
    name: String,
    count: String,
): String = "${name.replace(' ', NBSP)}$NBSP$count"

/**
 * The systems line: the items joined by [separator], whose break is the only place a line may end (its space before the dot is a
 * non-breaking one, so the dot ends a line, never starts one). [fits] says whether a text fits the room; when the whole line does
 * not, trailing whole items are dropped and an ellipsis follows the last one kept (at least one item stays).
 */
internal fun systemsLineThatFits(
    items: List<String>,
    separator: String,
    fits: (String) -> Boolean,
): String {
    val glue = "$NBSP${separator.trim()} "
    for (kept in items.size downTo 1) {
        val text = items.take(kept).joinToString(glue) + if (kept < items.size) "…" else ""
        if (kept == 1 || fits(text)) return text
    }
    return ""
}
