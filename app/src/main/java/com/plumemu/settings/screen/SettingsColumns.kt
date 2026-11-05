package com.plumemu.settings.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.SettingsThreeDimens
import com.plumemu.coreui.theme.PlumLayout
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.SlabDimens

/** Margin and labels: 224 when the sections slide away, to the divider in three columns. */
internal fun sectionsColumnWidth(layout: PlumLayout): Dp =
    if (layout.isSettingsThreeColumns) SettingsThreeDimens.sectionsWidth else PlumDimens.stripMarginSide + PlumDimens.settingsSectionsWidth

/** The hairline on the sections column's right edge (three columns), as long as the strip less the list dividers' inset. */
@Composable
internal fun SectionsDivider(modifier: Modifier) {
    val colors = PlumTheme.colors
    Box(
        modifier
            .offset(x = SettingsThreeDimens.sectionsWidth - PlumDimens.listDividerWidth, y = PlumDimens.listDividerInset)
            .width(PlumDimens.listDividerWidth)
            .fillMaxHeight()
            .padding(bottom = PlumDimens.listDividerInset * 2)
            .drawBehind { drawRect(colors.text.copy(alpha = SECTIONS_DIVIDER_ALPHA)) }
            .testTag(SettingsTags.SECTIONS_DIVIDER),
    )
}

private const val SECTIONS_DIVIDER_ALPHA = 0.08f


/** Three columns: a page one step deeper than its option (forms, confirms, the own-colour page) takes the options' and the right column's room together. */
internal fun SettingsFocus.isWidePage(): Boolean = column == SettingsColumn.Panel && page in SettingsNavigation.DEEP_PAGES

/**
 * The right column's host, on the strip's right margin, always shown: [content] is the panel of the focused option, or with the sections
 * focused the panel of the section's remembered (first) row, so its help stands there as soon as a section is on. A wide page covers
 * the options column as well (the sections stay), and takes the touches that would reach it.
 */
@Composable
internal fun BoxScope.ThreeColumnPanel(
    model: SettingsModel,
    content: @Composable () -> Unit,
) {
    val layout = PlumTheme.layout
    val colors = PlumTheme.colors
    val isWide = model.focus.isWidePage()
    val width = if (isWide) layout.settingsOptionsWidth + SettingsThreeDimens.panelGap + SettingsThreeDimens.panelWidth else SettingsThreeDimens.panelWidth
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .padding(end = PlumDimens.stripMarginSide)
            .width(width)
            .fillMaxHeight()
            .clipPanel()
            .then(if (isWide) Modifier.drawBehind { drawRect(colors.bg) }.pointerInput(Unit) {} else Modifier)
            .testTag(SettingsTags.PANEL),
    ) {
        content()
    }
}

/**
 * The strip's clip: at the sides (it slides off screen), and [SlabDimens.listEndRoom] above and below, the room a list keeps for a focused
 * first or last row's slab and glow, so they are never cut flat by a nearer clip. Panels clip themselves at the bar gap ([clipPanel]).
 */
internal fun Modifier.clipStrip(): Modifier =
    graphicsLayer {
        clip = true
        shape = EdgeRoomClip(maxOf(PlumDimens.barGap, SlabDimens.listEndRoom).toPx(), SlabDimens.listEndRoom.toPx(), sides = true)
    }

/**
 * A panel's clip: nothing of it draws more than the bar gap above its box (a scrolled panel's content must not reach the clock), and its foot keeps
 * [SlabDimens.listEndRoom] below, for a card's shadow at the panel's bottom; no clip at the sides.
 */
internal fun Modifier.clipPanel(): Modifier =
    graphicsLayer {
        clip = true
        shape = EdgeRoomClip(PlumDimens.barGap.toPx(), SlabDimens.listEndRoom.toPx(), sides = false)
    }

private data class EdgeRoomClip(
    val above: Float,
    val below: Float,
    val sides: Boolean,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline =
        Outline.Rectangle(if (sides) Rect(0f, -above, size.width, size.height + below) else Rect(-SIDE_REACH, -above, size.width + SIDE_REACH, size.height + below))

    private companion object {
        const val SIDE_REACH = 100_000f
    }
}
