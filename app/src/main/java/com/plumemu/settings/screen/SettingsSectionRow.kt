package com.plumemu.settings.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.plumemu.coreui.components.EllipsisText
import com.plumemu.coreui.components.InsetFocusPillShape
import com.plumemu.coreui.components.SystemRowLook
import com.plumemu.coreui.components.SystemRowTags
import com.plumemu.coreui.components.focusPill
import com.plumemu.coreui.theme.LeftColumnDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.SlabDimens

/** Lines a section's label may take: at the largest sizes "Box art & accounts" does not fit the column, and a section's name is never cut. Two fit the row's height. */
private const val SECTION_LABEL_LINES = 2

/**
 * A section of Settings, the game row's recipe as home's system rows ([com.plumemu.coreui.components.SystemRow]): the slab from [LeftColumnDimens.systemPillStart] and the label
 * after the accent bar, but a long label wraps instead of being cut. The caller's [modifier] sets the row's least height; a label of two lines keeps the room of one above and below,
 * so the rhythm of the text holds.
 */
@Composable
internal fun SectionLabelRow(
    name: String,
    look: SystemRowLook,
    endPadding: Dp,
    modifier: Modifier,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val focused = look == SystemRowLook.Focused
    val inset = LeftColumnDimens.systemPillStart
    // The press is on the whole row, the touch target; only the drawn pill starts at [inset].
    Box(modifier.fillMaxWidth().focusPill(focused, shape = remember(inset) { InsetFocusPillShape(inset, SlabDimens.barInset) })) {
        // The row grows with its label: its height is known only once the label is measured.
        if (focused) Box(Modifier.matchParentSize().padding(start = inset).testTag(SystemRowTags.PILL))
        val style =
            when (look) {
                SystemRowLook.Focused -> type.systemNameFocused
                SystemRowLook.Normal -> type.systemName
                SystemRowLook.Current -> type.systemNameCurrent
            }
        // The colour is read while drawing, so a moved theme colour redraws the name without recomposing it.
        val color: () -> Color =
            when (look) {
                SystemRowLook.Focused -> { { colors.strong } }
                SystemRowLook.Normal -> { { colors.text2 } }
                SystemRowLook.Current -> { { colors.accent } }
            }
        // Whole px, rounded down: a label of one line never makes its row taller than the row's height.
        val rowHeight = PlumTheme.layout.rowHeight
        val room = with(LocalDensity.current) { ((rowHeight.toPx() - style.lineHeight.toPx()) / 2).toInt().coerceAtLeast(0).toDp() }
        EllipsisText(
            text = name,
            style = style,
            color = color,
            maxLines = SECTION_LABEL_LINES,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = LeftColumnDimens.sectionLabelStart, end = endPadding, top = room, bottom = room).testTag(SystemRowTags.LABEL),
        )
    }
}
