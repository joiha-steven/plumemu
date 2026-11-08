package com.plumemu.coreui.testing

import com.plumemu.coreui.theme.PLACE_NAME_STEP_SP
import com.plumemu.coreui.theme.PlumThemeSettings
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plumemu.coreui.theme.TextSize
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.TypeRole

/**
 * Theme settings at Small text (factor 1: the type table as drawn), for the bounds and fit tests whose
 * numbers are the table's (a row 40 high, a top bar of one place-name line). The app's default is Large,
 * so a test that leaves the size unset measures Large; these name Small on purpose.
 */
object TableText {
    /** The top bar at Small in the default font: the line of the place name's 16 step, which is taller than the 18 dp base. */
    val TopBarHeight = (TypeRole.PlaceName.lineHeightSp(PlumFont.Jost, TextSize.Small) * PLACE_NAME_STEP_SP / TypeRole.PlaceName.sizeSp).dp

    /** Where the strip starts at Small: the top margin 14, the bar, the 10 gap. */
    val StripTop = 14.dp + TopBarHeight + 10.dp

    val Default: PlumThemeSettings = PlumThemeSettings.Default.copy(textSize = TextSize.Small)
    val Graphite: PlumThemeSettings = PlumThemeSettings.Graphite.copy(textSize = TextSize.Small)
    val Paper: PlumThemeSettings = PlumThemeSettings.Paper.copy(textSize = TextSize.Small)
}
