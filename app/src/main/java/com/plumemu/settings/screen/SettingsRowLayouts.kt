package com.plumemu.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.SlabDimens

/**
 * The label and its value on a row. The label takes what it needs and the value the rest, wrapping only where both do not fit: the gap gives way first, down to [leastGap], then a label
 * of two lines where that leaves a value within its share whole, then the value, down to what it needs on two lines and at most its share of the row ([com.plumemu.coreui.theme.PlumLayout.settingsValueMaxWidth]). A row of two lines keeps [padding] above and
 * below them, so its neighbours' text stays as far from it as from any other row. [boldLabelWidth] is the label's width on a focused row: the room is worked out with it on every row,
 * so a value does not move when the cursor arrives and the label turns bold.
 */
@Composable
internal fun LabelBesideValue(
    gap: Dp,
    leastGap: Dp,
    padding: Dp,
    boldLabelWidth: () -> Int,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val rowHeight = PlumTheme.layout.rowHeight
    val share = PlumTheme.layout.settingsValueMaxWidth
    Layout(content, modifier) { (label, value), constraints ->
        val width = constraints.maxWidth
        val labelNeeds = maxOf(label.maxIntrinsicWidth(Constraints.Infinity), boldLabelWidth())
        val valueNeeds = value.maxIntrinsicWidth(Constraints.Infinity)
        val valueWidth =
            if (labelNeeds + leastGap.roundToPx() + valueNeeds <= width) {
                valueNeeds
            } else if (valueNeeds <= share.roundToPx() && (labelNeeds + label.minIntrinsicWidth(Constraints.Infinity)) / 2 + leastGap.roundToPx() + valueNeeds <= width) {
                valueNeeds
            } else {
                val twoLines = minOf((valueNeeds + value.minIntrinsicWidth(Constraints.Infinity)) / 2, share.roundToPx())
                maxOf(width - gap.roundToPx() - labelNeeds, twoLines).coerceAtMost(width)
            }
        val labelPlaced = label.measure(Constraints(maxWidth = (width - valueWidth - leastGap.roundToPx()).coerceAtLeast(0)))
        val valuePlaced = value.measure(Constraints(maxWidth = valueWidth))
        val tallest = maxOf(labelPlaced.height, valuePlaced.height)
        val padded = tallest + 2 * padding.roundToPx()
        val height = maxOf(if (padded > rowHeight.roundToPx()) padded else tallest, constraints.minHeight)
        layout(width, height) {
            labelPlaced.place(0, (height - labelPlaced.height) / 2)
            valuePlaced.place(width - valuePlaced.width, (height - valuePlaced.height) / 2)
        }
    }
}

/** Whether a value set under its label gives up the room of its left chevron, so its text starts on the label's edge. Written and read while measuring, never observed. */
internal class ValueLeadRoom {
    var isGone = false
}

/** The chevron's room before a choice: nothing where [room] says it is gone. */
internal fun Modifier.valueLead(room: ValueLeadRoom?): Modifier =
    if (room == null) {
        this
    } else {
        layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            if (room.isGone) layout(0, placeable.height) {} else layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
    }

/**
 * Its two children, a label on one line and a value: the value at the end when both fit beside each other whole, [gap] apart, else on its own line under the label with
 * [padding] above and below the two lines, clear of the focused row's edge.
 */
@Composable
internal fun LabelThenValue(
    gap: Dp,
    padding: Dp,
    leadRoom: ValueLeadRoom,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content, modifier) { (label, value), constraints ->
        val width = constraints.maxWidth
        val free = constraints.copy(minWidth = 0, minHeight = 0)
        val labelPlaced = label.measure(free)
        val room = width - labelPlaced.width - gap.roundToPx()
        leadRoom.isGone = false
        val fits = value.maxIntrinsicWidth(free.maxHeight) <= room
        leadRoom.isGone = !fits
        if (fits) {
            val valuePlaced = value.measure(free.copy(maxWidth = room))
            val height = maxOf(labelPlaced.height, valuePlaced.height, constraints.minHeight)
            layout(width, height) {
                labelPlaced.place(0, (height - labelPlaced.height) / 2)
                valuePlaced.place(width - valuePlaced.width, (height - valuePlaced.height) / 2)
            }
        } else {
            val valuePlaced = value.measure(free)
            val pad = padding.roundToPx()
            layout(width, labelPlaced.height + valuePlaced.height + 2 * pad) {
                labelPlaced.place(0, pad)
                valuePlaced.place(0, pad + labelPlaced.height)
            }
        }
    }
}

/** [title] over [rest]; the title gives way where the two overrun a panel [window] px tall and [rest] alone does not: it repeats the row beside it, and the rest then stands whole with no scroll. */
@Composable
internal fun TitleGivesWay(
    window: Int,
    gap: Dp,
    title: @Composable () -> Unit,
    rest: @Composable () -> Unit,
) {
    Layout(contents = listOf(title, rest)) { (titles, rests), constraints ->
        val free = constraints.copy(minWidth = 0, minHeight = 0)
        val titlePlaced = titles.single().measure(free)
        val restPlaced = rests.single().measure(free)
        val foot = (SlabDimens.listEndRoom - PanelFootGive).roundToPx()
        val titleRoom = titlePlaced.height + gap.roundToPx()
        val gives = titleRoom + restPlaced.height + foot > window && restPlaced.height + foot <= window
        layout(constraints.maxWidth, restPlaced.height + if (gives) 0 else titleRoom) {
            if (!gives) titlePlaced.place(0, 0)
            restPlaced.place(0, if (gives) 0 else titleRoom)
        }
    }
}
