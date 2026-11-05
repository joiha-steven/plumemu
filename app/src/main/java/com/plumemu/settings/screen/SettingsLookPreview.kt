package com.plumemu.settings.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import com.plumemu.R
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.SettingsPreviewDimens as P
import com.plumemu.coreui.theme.SettingsPreviewLook
import com.plumemu.coreui.theme.drawPreviewCover
import com.plumemu.coreui.theme.drawPreviewFrameLine
import com.plumemu.coreui.theme.drawPreviewSlab
import com.plumemu.coreui.theme.drawPreviewInset

/*
 * The look preview at the top of Settings' right column: a small mock screen (list lines, a focused row, a cover tile with a
 * ball) drawn from the theme the screen itself wears, so a ‹ › step, a slider drag or a swatch under the cursor shows in it
 * as it happens. Colours are read while drawing: a move redraws it and recomposes nothing.
 */

/** What the preview shows for an option. */
internal enum class PreviewKind {
    /** The whole look: colours, background, text and accent. */
    Look,

    /** The focused row's text is a sample in the type as set (size, weight, font). */
    Sample,

    /** The list lines fade out at the bottom while Fade list edges is on. */
    Fade,
}

/** The preview [option] gets, or null: the options of Appearance that change how the app looks and a still tile can show. */
internal fun previewKindOf(option: SettingsOption): PreviewKind? =
    when (option) {
        SettingsOption.Colors, SettingsOption.Background, SettingsOption.TextColor, SettingsOption.Accent -> PreviewKind.Look
        SettingsOption.TextSize, SettingsOption.TextWeight, SettingsOption.Font -> PreviewKind.Sample
        SettingsOption.EdgeFade -> PreviewKind.Fade
        else -> null
    }

/** The card. [fadeEdges] is the stored Fade list edges, read only by [PreviewKind.Fade]. */
@Composable
internal fun SettingsLookPreview(
    kind: PreviewKind,
    fadeEdges: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = PlumTheme.colors
    val shape = RoundedCornerShape(P.corner)
    val fades = kind == PreviewKind.Fade && fadeEdges
    Box(
        modifier
            .size(P.width, P.height)
            // The bottom line is outside the card's shape, so before the clip; the well and its inset shadow are inside it.
            .drawBehind { drawPreviewFrameLine(P.corner.toPx()) }
            .clip(shape)
            .drawBehind {
                drawRect(SettingsPreviewLook.well(colors.bg, colors.text))
                drawPreviewInset(P.corner.toPx(), colors.bg.luminance() >= LIGHT_FROM)
            }
            .drawWithContent {
                drawContent()
                if (fades) {
                    val well = SettingsPreviewLook.well(colors.bg, colors.text)
                    val from = size.height - P.fadeHeight.toPx()
                    drawRect(Brush.verticalGradient(listOf(well.copy(alpha = 0f), well), startY = from, endY = size.height), topLeft = Offset(0f, from), size = Size(size.width, size.height - from))
                }
            }
            .testTag(SettingsTags.LOOK_PREVIEW),
    ) {
        Line(0)
        Line(1)
        Column(Modifier.offset(P.slabLeft, P.slabTop)) {
            FocusedRow(sample = kind == PreviewKind.Sample)
            Spacer(Modifier.height(P.lineTops[2] - P.slabTop - P.slabHeight))
            Line(2, inFlow = true)
            Spacer(Modifier.height(P.lineTops[3] - P.lineTops[2] - P.lineHeight))
            Line(3, inFlow = true)
        }
        Tile()
    }
}

/** List line [index]: absolute in the card, or the next in the column under the focused row ([inFlow]). */
@Composable
private fun Line(
    index: Int,
    inFlow: Boolean = false,
) {
    val colors = PlumTheme.colors
    val place = if (inFlow) Modifier.padding(start = P.lineLeft - P.slabLeft) else Modifier.offset(P.lineLeft, P.lineTops[index])
    Box(
        place.size(P.lineWidths[index], P.lineHeight).drawBehind { drawRoundRect(SettingsPreviewLook.line(colors.text), cornerRadius = CornerRadius(size.height / 2)) },
    )
}

/** The focused row: the slab and the text bar, or the sample text in the type as set; the row grows to hold a larger line. */
@Composable
private fun FocusedRow(sample: Boolean) {
    val colors = PlumTheme.colors
    Box(
        Modifier
            // The board's width, wider where a sample line in a larger or wider type needs it (up to the card's room).
            .widthIn(min = P.slabWidth, max = P.width - P.slabLeft * 2)
            .heightIn(min = P.slabHeight)
            // The mini slab: the system's slab colours at the board's size (the real slab is never drawn as short as 20 dp).
            .drawBehind { drawPreviewSlab(colors) }
            .padding(horizontal = P.barLeft - P.slabLeft),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (sample) {
            BasicText(
                stringResource(R.string.settings_sample_game_2),
                style = PlumTheme.type.settingsPanelMeta,
                color = { colors.text },
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.padding(vertical = P.samplePad),
            )
        } else {
            Box(Modifier.size(P.barWidth, P.barHeight).drawBehind { drawRoundRect(colors.text, cornerRadius = CornerRadius(size.height / 2)) })
        }
    }
}

/** The cover tile and the ball on its corner: the board's gradients, drop shadow and glow ([drawPreviewCover]). */
@Composable
private fun Tile() {
    val colors = PlumTheme.colors
    Box(Modifier.fillMaxSize().drawBehind { drawPreviewCover(colors.accent, colors.bg.luminance() >= LIGHT_FROM) })
}

/** The preview as a part of a panel's own layout ([PanelHeaderYields]): at its size and place whatever width the column gives it. */
@Composable
internal fun LookPreviewPart(
    kind: PreviewKind,
    fadeEdges: Boolean,
) {
    Box(Modifier.wrapContentWidth(Alignment.Start).padding(top = P.top)) { SettingsLookPreview(kind, fadeEdges) }
}

/** [option]'s panel with the look preview where it has one and it fits (see [PanelWithPreview]); the colour pages place it in their own layout. */
@Composable
internal fun PanelWithLookPreview(
    option: SettingsOption,
    settings: PlumSettings,
    model: SettingsModel,
    touch: SettingsTouch,
    versionName: String,
) {
    val kind = previewKindOf(option).takeIf { !option.hostsItsPreview }
    PanelWithPreview(kind, settings.appearance.edgeFade, allowed = !model.focus.isWidePage(), bodyYields = kind == PreviewKind.Sample || kind == PreviewKind.Fade) {
        SettingsPanel(option, settings, model, touch, versionName)
    }
}

/** The options whose panel places the preview itself, in its own layout (the colour presets pages). */
internal val SettingsOption.hostsItsPreview: Boolean
    get() = this == SettingsOption.Background || this == SettingsOption.TextColor || this == SettingsOption.Accent

/**
 * A panel with the preview above it where the person can see it whole: only when the panel's own height and the preview
 * together fit the column (measured, never decided by device), so swatches, tiles and text keep priority over the card and
 * nothing is cut or scrolled for it. [kind] null, or [allowed] false (a page that covers the column), shows the panel alone.
 */
@Composable
internal fun PanelWithPreview(
    kind: PreviewKind?,
    fadeEdges: Boolean,
    allowed: Boolean,
    bodyYields: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (kind == null) {
        content()
        return
    }
    SubcomposeLayout(Modifier.fillMaxSize()) { constraints ->
        val top = P.top.roundToPx()
        val cardHeight = P.height.roundToPx()
        val gap = P.gapBelow.roundToPx()
        val above = top + cardHeight + gap
        // Intrinsics, not a measure at infinite height: the panels scroll, and a scroll refuses infinite room.
        fun natural(nodes: List<androidx.compose.ui.layout.Measurable>) = nodes.maxOfOrNull { it.maxIntrinsicHeight(constraints.maxWidth) } ?: 0
        // A description never wins over the card: the card shows when the rest of the panel fits under it, and the description gets what is left, in whole lines.
        val withoutBody = if (bodyYields) natural(subcompose(DropSlot) { CompositionLocalProvider(LocalBodyRoom provides BodyRoom.Dropped, content = content) }) else 0
        val plain = if (bodyYields) emptyList() else subcompose(PanelSlot, content)
        val shown = allowed && (if (bodyYields) above + withoutBody else top + natural(plain) + cardHeight + gap) <= constraints.maxHeight
        val room = BodyRoom.Height((constraints.maxHeight - above - withoutBody - PlumDimens.settingsPanelListGap.roundToPx()).coerceAtLeast(0))
        val panel = if (bodyYields) subcompose(RoomSlot) { if (shown) CompositionLocalProvider(LocalBodyRoom provides room, content = content) else content() } else plain
        val cards = if (shown) subcompose(CardSlot) { SettingsLookPreview(kind, fadeEdges) } else emptyList()
        val panelAbove = if (shown) above else 0
        val panelPlaced = panel.map { it.measure(Constraints(maxWidth = constraints.maxWidth, minHeight = 0, maxHeight = (constraints.maxHeight - panelAbove).coerceAtLeast(0))) }
        val cardPlaced = cards.map { it.measure(Constraints()) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            cardPlaced.forEach { it.place(0, top) }
            panelPlaced.forEach { it.place(0, panelAbove) }
        }
    }
}

private const val PanelSlot = "panel"
private const val CardSlot = "card"
private const val DropSlot = "drop"
private const val RoomSlot = "room"

/** The background's luminance from which a theme is light (the card's shadows are half as heavy). */
private const val LIGHT_FROM = 0.5f
