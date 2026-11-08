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
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import com.plumemu.coreui.theme.ListGlide
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
import com.plumemu.coreui.components.systemBadgeTextStyle
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.components.plainLayer

/** The band's about text: at most this many lines. */
internal const val SystemInfoBandAboutMaxLines = 3

/**
 * The band at the top of a system's games list, the list's first item the tag (a raised chip), 14 dp right of it one facts line in `meta`
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
    /** True at the narrow details stop, where only 56 dp of the list shows: the badge and the line would show a cut piece, so they are not drawn (read while drawing). */
    isPeeking: () -> Boolean = { false },
    /** Not null: the list draws the hairline, under its focus pill ([drawsBandRule]); the band only reports where it lies. */
    rule: BandRule? = null,
) {
    val colors = PlumTheme.colors
    val facts = systemFactsOf(info)
    val lines = facts.candidates(stringResource(R.string.system_facts_separator))
    Column(modifier.fillMaxWidth().testTag(HomeTags.SYSTEM_BAND)) {
        BoxWithConstraints(Modifier.fillMaxWidth().plainLayer { alpha = if (isPeeking()) 0f else 1f }) {
            val room = min(maxWidth, visibleWidth)
            Row(verticalAlignment = Alignment.CenterVertically) {
                SystemBadge(info.abbr)
                val aboutText = info.about?.let { stringResource(it) }
                if (lines.isNotEmpty() || aboutText != null) {
                    Spacer(Modifier.width(PlumDimens.systemBandBadgeGap))
                    val measurer = rememberTextMeasurer()
                    val style = PlumTheme.type.meta
                    val density = LocalDensity.current
                    // The badge grows with its text; its real width is measured here, as it is laid out below.
                    val badge = rememberBadgeWidth(info.abbr, measurer)
                    val left = room - badge - PlumDimens.systemBandBadgeGap
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
                                // One line, not softWrap = false: that ignores the ellipsis and lets the clip cut the text mid-letter.
                                maxLines = 1,
                                // The last candidate has no shorter form ("Arcade games from many makers"): it ends in an ellipsis, never cut mid-letter by the clip.
                                overflow = TextOverflow.Ellipsis,
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
        val line = Modifier.fillMaxWidth().height(PlumDimens.systemBandRuleWidth)
        Spacer((if (rule == null) line.background(colors.sel) else line.onPlaced { rule.line = it }).testTag(HomeTags.SYSTEM_BAND_RULE))
    }
}

/**
 * Where the band's hairline lies, for the list that draws it. A row's slab overhangs its row and a gliding pill passes over the band's foot:
 * the list draws the pill behind its items, so a hairline drawn by the band would cross the slab. The list draws it behind the pill instead.
 */
@Stable
internal class BandRule {
    internal var line: LayoutCoordinates? = null
    internal var host: LayoutCoordinates? = null
}

/**
 * Draws [rule]'s hairline in [color] behind what follows (put it before the pill's modifier): where the band lies in [list] now, moved with the
 * glide, and only inside the window and below what [clearTop] px of it the list hides. Read while drawing: a scroll or glide redraws, nothing recomposes.
 */
internal fun Modifier.drawsBandRule(
    rule: BandRule,
    list: LazyListState,
    glide: ListGlide,
    isBand: (LazyListItemInfo) -> Boolean,
    clearTop: (height: Float) -> Float,
    color: () -> Color,
): Modifier =
    onPlaced { rule.host = it }.drawBehind {
        // Read so a scroll or a glide redraws this; the place itself comes from the coordinates.
        if (!BandRuleShown.isOn || list.layoutInfo.visibleItemsInfo.none(isBand)) return@drawBehind
        glide.offset
        val host = rule.host?.takeIf { it.isAttached } ?: return@drawBehind
        val line = rule.line?.takeIf { it.isAttached } ?: return@drawBehind
        val at = host.localPositionOf(line, Offset.Zero)
        val lineHeight = line.size.height.toFloat()
        if (at.y < clearTop(size.height) || at.y + lineHeight > size.height) return@drawBehind
        drawRect(color(), topLeft = at, size = Size(line.size.width.toFloat(), lineHeight))
    }

/** Off only in tests, which draw a frame with and without the hairline to see what of it shows past the slab (state: turning it redraws). */
internal object BandRuleShown {
    var isOn by mutableStateOf(true)
}

private fun fits(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    maxWidthPx: Int,
): Boolean = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width <= maxWidthPx

/** The badge's width: its text plus the padding on both sides (as `SystemBadge` lays out). */
@Composable
private fun rememberBadgeWidth(
    abbr: String,
    measurer: TextMeasurer,
): Dp {
    val style = systemBadgeTextStyle()
    val density = LocalDensity.current
    return remember(abbr, style, density) {
        val px = measurer.measure(abbr.uppercase(), style, maxLines = 1, softWrap = false).size.width
        with(density) { px.toDp() } + PlumDimens.systemBadgePaddingHorizontal * 2
    }
}
