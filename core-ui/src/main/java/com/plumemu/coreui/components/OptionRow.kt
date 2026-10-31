package com.plumemu.coreui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
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
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = PlumTheme.layout.rowHeight)
                .focusPill(focused)
                .padding(horizontal = PlumDimens.gameRowPadding, vertical = PlumDimens.factsRowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val labelLine: @Composable () -> Unit = {
            BasicText(
                text = label,
                style = (if (focused) type.settingsOptionFocused else type.settingsOption).copy(color = labelColor),
                // A long name (an emulator) wraps to up to three lines before it is cut.
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = if (badgeBelow) Modifier else Modifier.weight(1f, fill = false),
            )
        }
        // At the larger sizes the value goes under the label too, so a long label keeps the row's width.
        val valueBelow = value.isNotEmpty() && PlumTheme.layout.textScale > BADGE_BESIDE_MAX_SCALE
        if ((badgeBelow && badge != null) || valueBelow) {
            Column(Modifier.weight(1f)) {
                labelLine()
                if (badge != null) {
                    Spacer(Modifier.height(PlumDimens.factsRowGap))
                    Badge(badge)
                }
                if (valueBelow) BasicText(text = value, style = type.meta.copy(color = valueColor), maxLines = 2)
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
            BasicText(text = value, style = type.meta.copy(color = valueColor), maxLines = 1)
        }
    }
}

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
                .background(colors.accent, RoundedCornerShape(PlumDimens.cornerBadge))
                .padding(horizontal = PlumDimens.badgePaddingHorizontal, vertical = PlumDimens.badgePaddingVertical),
    )
}

private const val BADGE_BESIDE_MAX_SCALE = 1.2f
