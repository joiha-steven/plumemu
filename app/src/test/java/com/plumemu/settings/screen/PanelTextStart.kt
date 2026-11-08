package com.plumemu.settings.screen

import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.SettingsColumns
import com.plumemu.coreui.theme.TextSize

/**
 * Where a panel's title and free text start from the panel's edge in a test that names its size last in [at] ("768x432 Large", or "768x432 Sans Large" where it sweeps the fonts):
 * on the edge itself in three columns ([wide] and the size fits, the board), else the row-text line.
 */
internal fun panelTextStartAt(
    at: String,
    wide: Boolean,
): Float {
    val size = TextSize.entries.firstOrNull { at.endsWith(" ${it.name}") } ?: return PlumDimens.gameRowPadding.value
    // A test that sweeps the fonts names it before the size ("768x432 Sans Large"); the others use Jost.
    val font = listOf(PlumFont.Jost).firstOrNull { at.endsWith(" ${it.name} ${size.name}") } ?: PlumFont.Jost
    return if (wide && SettingsColumns.fits(size, font)) 0f else PlumDimens.gameRowPadding.value
}
