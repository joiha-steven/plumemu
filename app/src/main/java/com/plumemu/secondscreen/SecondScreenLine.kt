package com.plumemu.secondscreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTypography
import com.plumemu.coreui.theme.TypeRole

/**
 * Jost's own line (1.445 em) is too tall for the second screen's cards, which are packed to the
 * margins: inside a card they are set at this height instead. Only Jost; other fonts keep their own.
 */
internal const val CARD_JOST_LINE_EM = 1.25f

/** Set by [SecondScreenCard]: whether the text below it is in a second-screen card. */
internal val LocalCardLine = staticCompositionLocalOf { false }

/**
 * [role]'s style in a second-screen card: Jost on [CARD_JOST_LINE_EM]. The ramp's default
 * [LineHeightStyle.Mode.Fixed] never makes a line shorter than the font's own, so the line is
 * set in [LineHeightStyle.Mode.Tight] with both ends trimmed: one line is exactly the line high.
 */
@Composable
@ReadOnlyComposable
internal fun PlumTypography.card(role: TypeRole): TextStyle = get(role).forCard(font, LocalCardLine.current)

internal fun TextStyle.forCard(
    font: PlumFont,
    inCard: Boolean,
): TextStyle =
    if (inCard && font == PlumFont.Jost) {
        copy(
            lineHeight = fontSize * CARD_JOST_LINE_EM,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both, LineHeightStyle.Mode.Tight),
        )
    } else {
        this
    }
