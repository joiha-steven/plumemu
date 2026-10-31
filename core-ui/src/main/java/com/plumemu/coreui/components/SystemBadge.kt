package com.plumemu.coreui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme

/** The badge's test tag (bounds tests measure it; brief 3's card too). */
const val SYSTEM_BADGE_TAG = "system_badge"

/**
 * A system's badge: its short name ([abbr]: "SNES", "PS1", at most 5 characters) in a rounded
 * outline of the `chip` colour with a small accent dot at the top left
 * (our own text badge, no maker logos). At least 72 x 48 dp, wider with a longer name or
 * larger text, never cut: one line, no ellipsis. Theme colours only, so it suits every colour set.
 */
@Composable
fun SystemBadge(
    abbr: String,
    modifier: Modifier = Modifier,
) {
    val colors = PlumTheme.colors
    val shape = RoundedCornerShape(PlumDimens.cornerRow)
    Box(
        modifier =
            modifier
                .defaultMinSize(PlumDimens.systemBadgeMinWidth, PlumDimens.systemBadgeMinHeight)
                .border(PlumDimens.systemBadgeRing, colors.chip, shape)
                .testTag(SYSTEM_BADGE_TAG)
                .padding(horizontal = PlumDimens.systemBadgePaddingHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(PlumDimens.systemBadgeDotInset - PlumDimens.systemBadgePaddingHorizontal, PlumDimens.systemBadgeDotInset)
                .size(PlumDimens.systemBadgeDot)
                .background(colors.accent, CircleShape),
        )
        BasicText(
            text = abbr,
            style = PlumTheme.type.systemNameCurrent.copy(color = colors.strong, textAlign = TextAlign.Center),
            maxLines = 1,
            softWrap = false,
        )
    }
}
