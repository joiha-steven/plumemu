package com.plumemu.coreui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected as semanticsSelected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.plumemu.coreui.theme.ChoiceDimens
import com.plumemu.coreui.theme.FlatFace
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme

/**
 * One of several (V2 board "One of several"): a row with a round mark in a [CollarSize.Key] collar on the left (chosen:
 * the accent with a check; not chosen: an empty sunken well), the [label] and an optional [supporting] line. Focus is the
 * row's [focusPill] and the mark's lit groove together. Off ([enabled] false) mutes the text and flattens the mark.
 * Taps come in through [modifier]; the row is a radio button to a screen reader.
 */
@Composable
fun PlumChoice(
    label: String,
    selected: Boolean,
    focused: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = PlumTheme.layout.rowHeight)
            .focusPill(focused && enabled)
            .semantics(mergeDescendants = true) {
                role = Role.RadioButton
                semanticsSelected = selected
                if (!enabled) disabled()
            }
            .padding(start = PlumDimens.slabTextStart, end = ChoiceDimens.choicePaddingX),
        horizontalArrangement = Arrangement.spacedBy(ChoiceDimens.choiceGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .collar(CollarSize.Key, focused = focused && enabled)
                .size(ChoiceDimens.choiceMark)
                .drawBehind {
                    when {
                        !enabled -> {
                            drawCssCap(colors.collar.seat.offCap, press = 1f, moveFace = false)
                            if (selected) drawCheckMark(colors.muted)
                        }
                        selected -> {
                            drawCssCap(colors.collar.seat.choiceDot(colors.accent))
                            drawCheckMark(colors.onAccentFace)
                        }
                        // Off: the groove's own colour, flat (`background: #0F1012`).
                        else -> drawCircle(colors.collar.seat.well)
                    }
                },
        )
        Column(verticalArrangement = Arrangement.spacedBy(ChoiceDimens.choiceLineGap)) {
            val labelColor =
                when {
                    !enabled -> colors.muted
                    focused || selected -> colors.strong
                    else -> colors.text2
                }
            BasicText(label, style = type.settingsOption.copy(color = labelColor), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (supporting != null) {
                BasicText(supporting, style = type.meta.copy(color = colors.muted), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

