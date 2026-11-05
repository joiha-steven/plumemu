package com.plumemu.coreui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.plumemu.coreui.theme.ColorMath
import com.plumemu.coreui.theme.Rgb
import com.plumemu.coreui.theme.toComposeColor

/** At or below this percentage, not charging, the top bar's battery number is in the danger colour. */
const val LOW_BATTERY_PERCENT = 15

/** The percentage a battery text such as "82%" says; null when it has no digits. */
fun batteryPercent(text: String): Int? = text.filter { it.isDigit() }.toIntOrNull()

/** Whether a battery text such as "12%" is low ([LOW_BATTERY_PERCENT] or less). */
fun isLowBattery(text: String): Boolean = batteryPercent(text)?.let { it <= LOW_BATTERY_PERCENT } ?: false

/** The contrast small text needs against its background. */
private const val TEXT_CONTRAST = 4.5

/**
 * [danger] as the ink of a small text on [background]: unchanged where it already reads at 4.5:1 (every dark theme), else moved toward
 * [toward] (the theme's strong text) just far enough (the light themes' danger is a little under).
 */
fun readableDanger(
    danger: Color,
    background: Color,
    toward: Color,
): Color {
    val from = Rgb(danger.toArgb() and 0xFFFFFF)
    val on = Rgb(background.toArgb() and 0xFFFFFF)
    if (ColorMath.contrast(from, on) >= TEXT_CONTRAST) return danger
    return ColorMath.reach(from, Rgb(toward.toArgb() and 0xFFFFFF), on, TEXT_CONTRAST).toComposeColor()
}
