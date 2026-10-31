package com.plumemu.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.min
import com.plumemu.R
import com.plumemu.coreui.components.SystemBadge
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme

/** The band's about text: at most this many lines. */
internal const val SystemInfoBandAboutMaxLines = 3

/**
 * The band at the top of a system's games list, the list's first item the badge, 14 dp right of it one facts line in `meta`
 * ("Nintendo · Home console · 1990–2003", never wrapped: the kind goes first when it does not fit,
 * then the maker), and 8 dp under it a hairline in `sel`. A system with no facts shows the badge
 * alone. Its about text, if it has one, goes under the facts line in the same style, three lines at most, whole or left out (never cut). [visibleWidth] is how much of the list's width is on screen beside the systems column
 * (the part past it slides off screen; the line may reach the screen's right margin, where the 32 dp fade has only begun).
 */
@Composable
internal fun SystemInfoBand(
    info: SystemInfo,
    visibleWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = PlumTheme.colors
    val facts = systemFactsOf(info)
    val lines = facts.candidates(stringResource(R.string.system_facts_separator))
    Column(modifier.fillMaxWidth().testTag(HomeTags.SYSTEM_BAND)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val room = min(maxWidth, visibleWidth)
            Row(verticalAlignment = Alignment.CenterVertically) {
                SystemBadge(info.abbr)
                val aboutText = info.about?.let { stringResource(it) }
                if (lines.isNotEmpty() || aboutText != null) {
                    Spacer(Modifier.width(PlumDimens.systemBandBadgeGap))
                    val measurer = rememberTextMeasurer()
                    val style = PlumTheme.type.meta
                    val density = LocalDensity.current
                    val badgeWidth = PlumDimens.systemBadgeMinWidth
                    // The badge grows with its text; its real width is measured here, as it is laid out below.
                    val badge = rememberBadgeWidth(info.abbr, measurer)
                    val left = room - maxOf(badge, badgeWidth) - PlumDimens.systemBandBadgeGap
                    // Whole or not at all: an about text that needs more than three lines at this width is left out, never cut.
                    val about =
                        aboutText?.takeIf {
                            remember(it, left, style, density) {
                                measurer.measure(it, style, constraints = Constraints(maxWidth = with(density) { left.roundToPx() })).lineCount <= SystemInfoBandAboutMaxLines
                            }
                        }
                    Column(Modifier.width(left)) {
                        if (lines.isNotEmpty()) {
                            val text = remember(lines, left, style, density) { firstThatFits(lines) { fits(measurer, it, style, with(density) { left.roundToPx() }) } }
                            BasicText(
                                text = text,
                                style = style.copy(color = colors.text2),
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.testTag(HomeTags.SYSTEM_BAND_FACTS),
                            )
                        }
                        if (about != null) {
                            if (lines.isNotEmpty()) Spacer(Modifier.height(PlumDimens.factsRowGap))
                            BasicText(
                                text = about,
                                style = style.copy(color = colors.text2),
                                maxLines = SystemInfoBandAboutMaxLines,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag(HomeTags.SYSTEM_BAND_ABOUT),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(PlumDimens.systemBandRuleGap))
        Spacer(Modifier.fillMaxWidth().height(PlumDimens.systemBandRuleWidth).background(colors.sel).testTag(HomeTags.SYSTEM_BAND_RULE))
    }
}

private fun fits(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    maxWidthPx: Int,
): Boolean = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width <= maxWidthPx

/** The badge's width: its text plus the padding on both sides, at least 72 (as `SystemBadge` lays out). */
@Composable
private fun rememberBadgeWidth(
    abbr: String,
    measurer: TextMeasurer,
): Dp {
    val style = PlumTheme.type.systemNameCurrent
    val density = LocalDensity.current
    return remember(abbr, style, density) {
        val px = measurer.measure(abbr, style, maxLines = 1, softWrap = false).size.width
        with(density) { px.toDp() } + PlumDimens.systemBadgePaddingHorizontal * 2
    }
}
