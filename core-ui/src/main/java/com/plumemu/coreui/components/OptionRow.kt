package com.plumemu.coreui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme

/** How an option row looks (Settings options, side-panel choices such as the game menu). */
enum class OptionRowLook {
    /** The cursor: `sel` fill, 6 dp accent dot, `strong` text, weight 500, value `meta`. */
    Focused,

    /** Another row: label `text2`, value `muted`. */
    Normal,

    /** Cannot be chosen now: label and value `muted`; never focused. */
    Disabled,
}

/** A modifier put on every text of the option rows below it, for a list that needs to know where their lines sit; empty by default. */
val LocalRowTextMark = compositionLocalOf<Modifier> { Modifier }

/**
 * An option row, 40 high (taller only with [isBadgeBelow]), in the one focus language (the board's RP5-S1-settings options): [label]
 * at 16 sp on one line with an ellipsis, an optional [badge] ("Current") right after it, and
 * [value] (13 sp) at the right. [isBadgeBelow] puts the badge on a second line under the label, inside
 * the same row (fill and tap), so the label keeps the whole line. [modifier] carries the tap handling and the width.
 */
@Composable
fun OptionRow(
    label: String,
    look: OptionRowLook,
    modifier: Modifier = Modifier,
    value: String = "",
    badge: String? = null,
    isBadgeBelow: Boolean = false,
    /** Drawn last at the row's right, after the value (a chevron on a row that opens something); centred on the row. */
    trailing: (@Composable () -> Unit)? = null,
    /** Space between the slab's edge and the text on each side; a card's rows take the card title's. */
    sidePadding: Dp = PlumDimens.gameRowPadding,
    /** A small line under the label (a result's year) inside the same slab: [PlumDimens.noteGap] under it, the room [PlumDimens.noteBelowGap] between pairs. */
    note: String? = null,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val focused = look == OptionRowLook.Focused
    // At the larger sizes a badge beside the label would squeeze a long name, so it goes below it.
    val badgeBelow = isBadgeBelow || PlumTheme.layout.textScale > BADGE_BESIDE_MAX_SCALE
    val (labelColor, valueColor) =
        when (look) {
            OptionRowLook.Focused -> colors.strong to colors.meta
            OptionRowLook.Normal -> colors.text2 to colors.muted
            OptionRowLook.Disabled -> colors.muted to colors.muted
        }
    val row: @Composable () -> Unit = {
    Row(
        modifier =
            if (note != null) {
                Modifier.fillMaxWidth().padding(start = sidePadding, end = sidePadding, top = PlumDimens.noteInset)
            } else {
                modifier.fillMaxWidth().heightIn(min = PlumTheme.layout.rowHeight).focusPill(focused).padding(horizontal = sidePadding, vertical = PlumDimens.factsRowGap)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val labelLine: @Composable () -> Unit = {
            BasicText(
                text = label,
                style = (if (focused) type.settingsOptionFocused else type.settingsOption).copy(color = labelColor),
                // A long name (an emulator) wraps to up to three lines before it is cut.
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = (if (badgeBelow || (badge == null && value.isNotEmpty())) Modifier else Modifier.weight(1f, fill = false)).then(LocalRowTextMark.current),
            )
        }
        // A value with a badge goes under the label at the larger sizes; a value alone is placed by measure ([LabelAndValue]): beside the label when it fits.
        val valueBelow = value.isNotEmpty() && badge != null && PlumTheme.layout.textScale > BADGE_BESIDE_MAX_SCALE
        val valueStyle = type.meta.copy(color = valueColor)
        if (badge == null && value.isNotEmpty() && !valueBelow) {
            LabelAndValue(Modifier.weight(1f), label, value, labelLine, valueStyle, if (focused) type.settingsOptionFocused else type.settingsOption)
        } else {
            if ((badgeBelow && badge != null) || valueBelow) {
                Column(Modifier.weight(1f)) {
                    labelLine()
                    if (badge != null) {
                        Spacer(Modifier.height(PlumDimens.factsRowGap))
                        Badge(badge)
                    }
                    if (valueBelow) BasicText(text = value, style = valueStyle, maxLines = 2, modifier = LocalRowTextMark.current)
                }
            } else {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    labelLine()
                    if (badge != null) {
                        Spacer(Modifier.width(PlumDimens.badgeGap))
                        Badge(badge)
                    }
                }
            }
            if (value.isNotEmpty() && !valueBelow) {
                Spacer(Modifier.width(PlumDimens.rowTrailingGap))
                BasicText(text = value, style = valueStyle, maxLines = 1, modifier = LocalRowTextMark.current)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(PlumDimens.rowTrailingGap))
            trailing()
        }
    }
    }
    if (note == null) {
        row()
    } else {
        // Title and note are one slab, the same box whether or not the cursor is on it, so nothing moves with the focus.
        Column {
        Column(modifier.fillMaxWidth().focusPill(focused)) {
            row()
            BasicText(
                text = note,
                style = type.smallFact.copy(color = valueColor),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = sidePadding, end = sidePadding, top = PlumDimens.noteGap, bottom = PlumDimens.noteInset).testTag(NOTE_TAG),
            )
        }
        Spacer(Modifier.height(PlumDimens.noteBelowGap))
        }
    }
}

/** Test tag of an option row's note line. */
const val NOTE_TAG = "option_row_note"

/** A small label such as "Current" or "Default": accent fill, `onAccent` text, 11 sp bold (design/rules.md › Colour). */
@Composable
fun Badge(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = PlumTheme.colors
    BasicText(
        text = text,
        style = PlumTheme.type.badge.copy(color = colors.onAccent),
        maxLines = 1,
        modifier =
            modifier
                .then(LocalRowTextMark.current)
                .background(colors.accent, RoundedCornerShape(PlumDimens.cornerBadge))
                .padding(horizontal = PlumDimens.badgePaddingHorizontal, vertical = PlumDimens.badgePaddingVertical),
    )
}

/** The text scale up to which a badge sits beside its label: Larger (1.3); Huge and Largest put it below. */
private const val BADGE_BESIDE_MAX_SCALE = 1.3f

/**
 * A label and the value after it on one line: the label keeps its natural width (it wraps only when it alone does not fit) and the value
 * takes the rest at the right. When what is left is under [VALUE_MIN_SHARE] of the value's own width, the value goes under the label instead
 * (a label never gives up its line, and a value is never cut: the text-size checks flag an ellipsis).
 */
@Composable
private fun LabelAndValue(
    modifier: Modifier,
    label: String,
    value: String,
    labelLine: @Composable () -> Unit,
    valueStyle: TextStyle,
    labelStyle: TextStyle,
) {
    val measurer = rememberTextMeasurer()
    val gap = PlumDimens.rowTrailingGap
    BoxWithConstraints(modifier) {
        val gapPx = with(LocalDensity.current) { gap.roundToPx() }
        val room = constraints.maxWidth
        val labelWidth = measurer.measure(label, labelStyle, maxLines = 1, softWrap = false).size.width
        val valueWidth = measurer.measure(value, valueStyle, maxLines = 1, softWrap = false).size.width
        val left = room - labelWidth - gapPx
        // The label wraps while alone: only then is there no line to keep.
        val labelFits = labelWidth <= room
        if (labelFits && left >= valueWidth * VALUE_MIN_SHARE) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                labelLine()
                Box(Modifier.weight(1f).padding(start = gap), contentAlignment = Alignment.CenterEnd) {
                    BasicText(text = value, style = valueStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = LocalRowTextMark.current)
                }
            }
        } else {
            Column {
                labelLine()
                BasicText(text = value, style = valueStyle, maxLines = 2, modifier = LocalRowTextMark.current)
            }
        }
    }
}

/** The share of the value's own width that must be left beside the label for the value to stay on its line. */
private const val VALUE_MIN_SHARE = 1f
