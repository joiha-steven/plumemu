package com.plumemu.coreui.testing

import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize

/**
 * Theme settings at Medium text, for the bounds and fit tests whose numbers are Medium's
 * (a row 40 high, a top bar 18). The app's default is Large, so a test that
 * leaves the size unset measures Large; these name Medium on purpose.
 */
object MediumText {
    val Default: PlumThemeSettings = PlumThemeSettings.Default.copy(textSize = TextSize.Medium)
    val Graphite: PlumThemeSettings = PlumThemeSettings.Graphite.copy(textSize = TextSize.Medium)
    val Paper: PlumThemeSettings = PlumThemeSettings.Paper.copy(textSize = TextSize.Medium)
}
