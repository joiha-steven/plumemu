package com.plumemu.coreui.testing

import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize

/**
 * Theme settings at Medium text for the second screen's tests: its text keeps its own factors
 * ([TextSize.secondScreenFactor]), where Medium is 1.0, the table as drawn there.
 */
object SecondScreenText {
    val Default: PlumThemeSettings = PlumThemeSettings.Default.copy(textSize = TextSize.Medium)
    val Graphite: PlumThemeSettings = PlumThemeSettings.Graphite.copy(textSize = TextSize.Medium)
    val Paper: PlumThemeSettings = PlumThemeSettings.Paper.copy(textSize = TextSize.Medium)
}
