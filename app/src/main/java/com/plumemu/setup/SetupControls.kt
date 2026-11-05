package com.plumemu.setup

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.plumemu.coreui.components.StripeAnchor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import com.plumemu.coreui.components.drawFlatFace
import com.plumemu.coreui.components.KeyState
import com.plumemu.coreui.components.keyDrive
import com.plumemu.coreui.components.rememberKeyState
import com.plumemu.coreui.components.drawFocusRing
import com.plumemu.coreui.components.drawSunken
import com.plumemu.coreui.components.focusPill
import com.plumemu.coreui.components.ButtonKind
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.components.PlumButton
import com.plumemu.coreui.components.PlumSwitch
import com.plumemu.coreui.components.raisedCard
import com.plumemu.coreui.theme.CardDimens
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme

/*
 * Setup's building blocks: collar buttons, choices and folder cards, the emulator and statistics
 * rows, the switch and the step dots. Focus follows docs/design/rules.md › Focus and current: a
 * focused button lights its collar's groove; a focused choice or row is the slab.
 */

/**
 * Draws the focus ring around this item when [isFocused]: only drawn, never laid out, so a focus
 * move costs no layout. It reaches [PlumDimens.focusRingOutset] past the item; leave that unclipped.
 */
internal fun Modifier.focusRing(
    isFocused: Boolean,
    corner: Dp,
    ring: Color,
    halo: Color,
): Modifier =
    drawWithContent {
        drawContent()
        if (isFocused) drawFocusRing(Offset.Zero, size, corner, ring = ring, halo = halo)
    }

/** [focusRing] with colours read while drawing: a theme colour that moves (a slider's preview) redraws the ring and recomposes nothing. */
internal fun Modifier.focusRing(
    isFocused: Boolean,
    corner: Dp,
    ring: () -> Color,
    halo: () -> Color,
): Modifier =
    drawWithContent {
        drawContent()
        if (isFocused) drawFocusRing(Offset.Zero, size, corner, ring = ring(), halo = halo())
    }

/**
 * A scroll container clips to its bounds; this lets its content draw [by] past them on every side
 * (focus rings at the edges), and [bottom] past the bottom edge. Put it before `verticalScroll` and
 * pad the content by [by].
 */
internal fun Modifier.bleed(
    by: Dp,
    bottom: Dp = by,
    /** More room past the top, past each side and past the bottom (a soft shadow's reach); the padding of the content is the same. */
    top: Dp = 0.dp,
    sides: Dp = 0.dp,
    below: Dp = 0.dp,
): Modifier =
    layout { measurable, constraints ->
        val px = by.roundToPx()
        val side = px + sides.roundToPx()
        val up = px + top.roundToPx()
        val down = bottom.roundToPx() + below.roundToPx()
        val placeable = measurable.measure(constraints.offset(side * 2, up + down))
        layout(placeable.width - side * 2, placeable.height - up - down) { placeable.place(-side, -up) }
    }

/**
 * The setup button: a [PlumButton] (collar, the main one in the accent, the others as keys). Its bounds include the collar
 * ([CollarSize.Control]'s lip and groove), so the collar's outer edge lines up with the column and keeps the column's gap from the
 * part above; the cap inside is `setupButtonHeight` high. The shadow below and the focus halo reach past the bounds: leave that
 * unclipped. [maxLines] 2 lets a label wrap in a narrow column (Settings' 248 dp panel at the largest text sizes); the cap grows with it.
 */
@Composable
internal fun SetupButton(
    text: String,
    isPrimary: Boolean,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    /** How much of the collar's top hangs above the bounds instead of inside them (a form short of room, Plumemu's tray up); 0 keeps the collar inside. */
    hangAbove: Dp = 0.dp,
    /** The collar's size: the small one where the room is short. */
    collarSize: CollarSize = CollarSize.Control,
) {
    PlumButton(
        label = text,
        kind = if (isPrimary) ButtonKind.Primary else ButtonKind.Secondary,
        focused = isFocused,
        modifier = modifier.then(if (hangAbove > 0.dp) Modifier.hangAbove(hangAbove) else Modifier),
        maxLines = maxLines,
        collarSize = collarSize,
    )
}

/** Reports the part [by] shorter and draws it [by] higher: its top hangs over the gap above, the face stays where the rest of the layout puts it. */
private fun Modifier.hangAbove(by: Dp): Modifier =
    layout { measurable, constraints ->
        val up = by.roundToPx()
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height - up) { placeable.place(0, -up) }
    }

/**
 * How a choice or card looks: the focused choice is the slab, a folder's card a raised card ([Chosen]), a plain one an outline.
 * [Current] is the choice in force while the cursor is elsewhere (the box art source): the outline with accent text at weight
 * 600 and no fill, as every "current, not focused" item (docs/design/rules.md), so it is never mistaken for the slab.
 */
internal enum class ChoiceLook { Focused, Plain, Chosen, Current }

/** A choice ("+ Add another folder") or a chosen folder's card: label, a small line under it. */
@Composable
internal fun SetupChoice(
    label: String,
    sub: String,
    look: ChoiceLook,
    modifier: Modifier = Modifier,
    subColor: Color = PlumTheme.colors.muted,
    /** Lines the small line may take; the box art choices carry a long one. */
    subMaxLines: Int = 2,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val shape = RoundedCornerShape(CardDimens.corner)
    // The accent stripe stands beside the title, not beside the middle of a long line under it.
    val anchor = remember { StripeAnchor() }
    val rest =
        when (look) {
            ChoiceLook.Plain, ChoiceLook.Current -> Modifier.border(PlumDimens.setupChoiceRing, colors.sel, shape)
            ChoiceLook.Chosen -> Modifier.raisedCard()
            ChoiceLook.Focused -> Modifier
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = PlumTheme.layout.setupChoiceMinHeight)
                .then(rest)
                .focusPill(look == ChoiceLook.Focused, shape = shape, stripe = anchor)
                .padding(start = PlumDimens.slabTextStart, end = PlumDimens.setupChoicePaddingHorizontal, top = PlumDimens.setupChoicePaddingVertical, bottom = PlumDimens.setupChoicePaddingVertical),
        horizontalArrangement = Arrangement.spacedBy(PlumDimens.setupChoiceDotGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(PlumDimens.setupChoiceLineGap)) {
            val labelStyle =
                when (look) {
                    ChoiceLook.Focused -> type.setupChoiceFocused.copy(color = colors.strong)
                    ChoiceLook.Current -> type.setupChoiceCurrent.copy(color = colors.accent)
                    else -> type.setupChoice.copy(color = colors.text2)
                }
            BasicText(text = label, style = labelStyle, maxLines = 2, overflow = TextOverflow.Ellipsis, onTextLayout = anchor::measured, modifier = anchor.title())
            BasicText(text = sub, style = type.smallFact.copy(color = subColor), maxLines = subMaxLines, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The emulators, box art and usage statistics rows: a raised card, title, line, trailing item, text at the slab start. Focused,
 * it wears the row focus language (docs/design/rules.md › Focus and current): the slab,
 * `strong` title at weight 500.
 */
@Composable
internal fun SetupInfoRow(
    title: String,
    body: String?,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val anchor = remember { StripeAnchor() }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (isFocused) Modifier else Modifier.raisedCard())
                .focusPill(isFocused, shape = RoundedCornerShape(CardDimens.corner), stripe = if (body != null) anchor else null)
                .padding(start = PlumDimens.slabTextStart, end = PlumDimens.setupRowPaddingHorizontal, top = PlumDimens.setupRowPaddingVertical, bottom = PlumDimens.setupRowPaddingVertical),
        horizontalArrangement = Arrangement.spacedBy(PlumDimens.setupRowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(PlumDimens.setupChoiceDotGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(PlumDimens.setupRowLineGap)) {
                val titleStyle = if (isFocused) type.setupRowTitleFocused.copy(color = colors.strong) else type.setupRowTitle.copy(color = colors.text)
                BasicText(text = title, style = titleStyle, maxLines = 2, overflow = TextOverflow.Ellipsis, onTextLayout = if (body != null) anchor::measured else null, modifier = if (body != null) anchor.title() else Modifier)
                if (body != null) BasicText(text = body, style = type.setupNote.copy(color = colors.muted), maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing()
    }
}

/**
 * The collar switch ([PlumSwitch]): on is the accent track, off the plain one, the knob a cap face. It always sits in a row or card,
 * and the row's slab is the focus, so the switch never lights its own groove: [isFocused] only lets the confirm button press it
 * through [key], the press state a row feeds (a finger on the row, or the confirm button).
 */
@Composable
internal fun SetupSwitch(
    isOn: Boolean,
    stateText: String,
    modifier: Modifier = Modifier,
    isFocused: Boolean = false,
    /** The press state; a row that wants a tap anywhere on it to press the switch passes its own and feeds it. */
    key: KeyState = rememberKeyState(),
) {
    PlumSwitch(
        on = isOn,
        focused = false,
        modifier = modifier.semantics { stateDescription = stateText }.keyDrive(key, isFocused),
    )
}

/** The step dots on the top bar: the current one 16 wide, done and current in accent. */
@Composable
internal fun StepDots(
    current: Int,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val colors = PlumTheme.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(PlumDimens.setupDotGap), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until count) {
            val width = if (i == current) PlumDimens.setupDotCurrentWidth else PlumDimens.setupDot
            Box(
                Modifier
                    .width(width)
                    .height(PlumDimens.setupDot)
                    .background(if (i <= current) colors.accent else colors.sel, RoundedCornerShape(PlumDimens.setupDot / 2))
                    .testTag(SetupTags.DOT),
            )
        }
    }
}

/** The scan's bar: sunken `sel` track, accent part for [fraction] (0–1). Static: nothing animates while the scan runs. */
@Composable
internal fun SetupProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val colors = PlumTheme.colors
    Box(modifier.fillMaxWidth().height(PlumDimens.setupProgressHeight).drawBehind { drawSunken(colors.sel, colors.inset) }) {
        if (fraction > 0f) {
            Box(Modifier.fillMaxWidth(fraction.coerceAtMost(1f)).height(PlumDimens.setupProgressHeight).drawBehind { drawSunken(colors.accent, colors.inset) })
        }
    }
}

/** Clips what is laid out to this box (semantics and text checks read that) but what is drawn only to [reach] past it. */
internal fun Modifier.clipsLayoutToWindow(reach: Dp): Modifier =
    graphicsLayer {
        clip = true
        shape = PastWindowShape(reach)
    }

/** A rectangle [reach] past the box on every side: the room a soft shadow draws in. */
private class PastWindowShape(
    private val reach: Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val out = with(density) { reach.toPx() }
        return Outline.Rectangle(Rect(-out, -out, size.width + out, size.height + out))
    }
}
