package com.plumemu.coreui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme

/** The badge's test tag (bounds tests measure it; brief 3's card too). */
const val SYSTEM_BADGE_TAG = "system_badge"

/** The badge's text: the `systemTag` role (bold), tracked [PlumDimens.SYSTEM_BADGE_TRACKING] em, in `strong`. */
@Composable
fun systemBadgeTextStyle(): TextStyle = PlumTheme.type.systemTag.copy(color = PlumTheme.colors.strong, letterSpacing = PlumDimens.SYSTEM_BADGE_TRACKING.em, textAlign = TextAlign.Center)

/**
 * A system's tag: its short name ([abbr]: "SNES", "PS1") in tracked capitals on the V2 board's RAISED chip
 * (a radial-gradient surface with its inset lights, ring and drop, all derived from the theme: `SeatLook.raisedChip`).
 * [PlumDimens.systemBadgeHeight] tall (grown with the text size), wider with a longer name or larger text,
 * never cut: one line, no ellipsis. Theme colours only, so it suits every colour set.
 */
@Composable
fun SystemBadge(
    abbr: String,
    modifier: Modifier = Modifier,
) {
    val face = PlumTheme.colors.collar.seat.raisedChip
    val corner = PlumDimens.systemBadgeCorner
    Box(
        modifier =
            modifier
                .defaultMinSize(minHeight = PlumDimens.systemBadgeHeight * PlumTheme.layout.textScale)
                .testTag(SYSTEM_BADGE_TAG)
                .drawBehind {
                    val fill = CssImages.chipBrush(face.stops[0], face.stops[1], size)
                    drawCssFace(face, Offset.Zero, size, corner.toPx(), fill = fill)
                }.padding(horizontal = PlumDimens.systemBadgePaddingHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = abbr.uppercase(),
            style = systemBadgeTextStyle(),
            maxLines = 1,
            softWrap = false,
        )
    }
}
