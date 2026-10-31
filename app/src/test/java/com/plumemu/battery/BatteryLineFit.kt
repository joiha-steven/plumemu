package com.plumemu.battery

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Constraints

/**
 * A wording of the picking line a fit test checks: the battery at [level] percent (the glyph, and the
 * charge there is now), a game whose usual pace is [percentPerHour].
 */
internal data class BatteryLineSample(
    val label: String,
    val level: Int,
    val percentPerHour: Double,
) {
    /** The time on this charge at the usual pace, as `UsualPace` gives it for a battery whose counter is [level] percent full. */
    val minutesOnCharge: Double get() = level / percentPerHour * MINUTES_PER_HOUR

    val pace: BatteryPace get() = BatteryPace(minutesOnCharge, percentPerHour)
}

/**
 * The wordings the fit tests loop through at every text size in every font: the shortest common one, the
 * common long one, the longest a real game gives (14 hours, and a two-digit pace with "About"), and the
 * longest the line can say (three-digit hours, exact and with "About").
 */
internal val BATTERY_LINE_SAMPLES =
    listOf(
        // "2 h on this charge · 30% an hour"
        BatteryLineSample("short", level = 60, percentPerHour = 30.0),
        // "About 2 h 45 min on this charge · 30% an hour"
        BatteryLineSample("common long", level = 83, percentPerHour = 30.0),
        // "About 14 h 15 min on this charge · 7% an hour"
        BatteryLineSample("longest", level = 100, percentPerHour = 7.0),
        // "312 h 30 min on this charge · under 1% an hour" (exact: no "About"): no game draws so little, but the line says it.
        BatteryLineSample("under 1%", level = 100, percentPerHour = 0.32),
        // "About 10 h 30 min on this charge · 10% an hour": 625 minutes rounded up, 9.6% rounded to 10.
        BatteryLineSample("longest two-digit pace", level = 100, percentPerHour = 9.6),
        // "About 312 h 15 min on this charge · under 1% an hour": the longest the line can say (18,732 minutes, rounded to 18,735).
        BatteryLineSample("longest under 1%", level = 100, percentPerHour = 0.3203),
    )

/**
 * Why the line's text [node] (unmerged) does not show all its words, or null when it does: its natural
 * height at the width it was given, measured again here with its own style, is taller than the box it
 * got. That catches a line cut with "…" at its last allowed line (two lines shown of three) as well as a
 * single line cut at its end. The layout the semantics hand out is only used for its input (text,
 * style, density): it is not made at the screen's width.
 */
internal fun batteryLineCut(node: SemanticsNode): String? {
    val layouts = mutableListOf<TextLayoutResult>()
    node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
    val input = layouts.single().layoutInput
    val natural = TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection).measure(input.text, input.style, constraints = Constraints(maxWidth = node.size.width))
    val text = node.config[SemanticsProperties.Text].joinToString("")
    return if (natural.size.height > node.size.height + 1) "'$text' needs ${natural.lineCount} lines (${natural.size.height} px) in ${node.size.height} px" else null
}

private const val MINUTES_PER_HOUR = 60.0
