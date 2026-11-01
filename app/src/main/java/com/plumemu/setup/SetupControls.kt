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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.offset
import com.plumemu.coreui.components.drawFlatFace
import com.plumemu.coreui.components.flatKey
import com.plumemu.coreui.components.KeyState
import com.plumemu.coreui.components.keyDrive
import com.plumemu.coreui.components.rememberKeyState
import com.plumemu.coreui.components.drawFocusRing
import com.plumemu.coreui.components.drawSunken
import com.plumemu.coreui.components.focusPill
import com.plumemu.coreui.components.pressFeel
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme

/*
 * Setup's building blocks, drawn as the board's RP5-O1 draws them: filled and outlined buttons,
 * choices and folder cards, the emulator and statistics rows, the switch and the step dots.
 * Focus follows docs/design/rules.md › Focus and current: a focused button gets the 2.5 dp accent
 * ring inside the soft focus halo; a focused choice or row gets the `sel` fill.
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
): Modifier =
    layout { measurable, constraints ->
        val px = by.roundToPx()
        val below = bottom.roundToPx()
        val placeable = measurable.measure(constraints.offset(px * 2, px + below))
        layout(placeable.width - px * 2, placeable.height - px - below) { placeable.place(-px, -px) }
    }

/**
 * Pill button with a flat face, 40 high: the main one in the accent, the others as keys; the touch area grows to Android's minimum by itself.
 * [maxLines] 2 lets a label wrap in a narrow column (Settings' 248 dp panel at the largest text
 * sizes); two lines still fit the height, which grows with the text size.
 */
@Composable
internal fun SetupButton(
    text: String,
    isPrimary: Boolean,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val pill = PlumTheme.layout.setupButtonHeight / 2
    // A flat face that stands off the background (design/rules.md › Controls): the main button in the accent, the others as keys.
    // It goes down into its edge while pressed, sits higher while focused or hovered, and stays put when animations are off.
    val key = rememberKeyState()
    Box(
        modifier =
            modifier
                // Outside the face, so the ring stays where the button rests.
                .focusRing(isFocused, pill, colors.accent, colors.focusGlow)
                .semantics { role = Role.Button }
                .then(if (maxLines > 1) Modifier.heightIn(min = PlumTheme.layout.setupButtonHeight) else Modifier.height(PlumTheme.layout.setupButtonHeight))
                .flatKey(key, isFocused, { if (isPrimary) it.accentFace() else it.keyFace }, pill, colors)
                .padding(horizontal = PlumDimens.setupButtonPadding, vertical = if (maxLines > 1) PlumDimens.titleLineGap else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        val style = if (isPrimary) type.setupButton.copy(color = colors.onAccent) else type.setupButtonQuiet.copy(color = colors.text2)
        BasicText(text = text, style = if (maxLines > 1) style.copy(textAlign = TextAlign.Center) else style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

/** How a choice or card looks: the focused choice is filled, a chosen folder is a quiet filled card. */
internal enum class ChoiceLook { Focused, Plain, Chosen }

/** A choice ("+ Add another folder") or a chosen folder's card: label, a small line under it. */
@Composable
internal fun SetupChoice(
    label: String,
    sub: String,
    look: ChoiceLook,
    modifier: Modifier = Modifier,
    subColor: Color = PlumTheme.colors.muted,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val shape = RoundedCornerShape(PlumDimens.cornerSetupChoice)
    val fill = if (look == ChoiceLook.Plain) Modifier.border(PlumDimens.setupChoiceRing, colors.sel, shape) else Modifier
    val restFill = if (look == ChoiceLook.Chosen) colors.sel2 else null
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = PlumTheme.layout.setupChoiceMinHeight)
                .focusPill(look == ChoiceLook.Focused, rest = { restFill }, shape = shape)
                .then(fill)
                .padding(horizontal = PlumDimens.setupChoicePaddingHorizontal, vertical = PlumDimens.setupChoicePaddingVertical),
        horizontalArrangement = Arrangement.spacedBy(PlumDimens.setupChoiceDotGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(PlumDimens.setupChoiceLineGap)) {
            val labelStyle =
                if (look == ChoiceLook.Focused) type.setupChoiceFocused.copy(color = colors.strong) else type.setupChoice.copy(color = colors.text2)
            BasicText(text = label, style = labelStyle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            BasicText(text = sub, style = type.smallFact.copy(color = subColor), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The emulators and usage statistics rows: `sel2` panel, title, line, trailing item. One that can take the focus
 * ([isFocusable]), focused, wears the row focus language (docs/design/rules.md › Focus and current): the accent pill
 * with its ring, `strong` title at weight 500.
 */
@Composable
internal fun SetupInfoRow(
    title: String,
    body: String?,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    isFocusable: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .focusPill(isFocused, rest = { colors.sel2 }, shape = RoundedCornerShape(PlumDimens.cornerSetupChoice))
                .padding(horizontal = PlumDimens.setupRowPaddingHorizontal, vertical = PlumDimens.setupRowPaddingVertical),
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
                BasicText(text = title, style = titleStyle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (body != null) BasicText(text = body, style = type.setupNote.copy(color = colors.muted), maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing()
    }
}

/**
 * On: sunken accent track, flat `onAccent` knob at the end. Off: sunken `chip` track, pale knob at the start. The knob
 * slides between the ends and squeezes wider while the switch is pressed (a finger, or the confirm button while
 * [isFocused]); with animations off it jumps and the squeeze is instant.
 */
@Composable
internal fun SetupSwitch(
    isOn: Boolean,
    stateText: String,
    modifier: Modifier = Modifier,
    isFocused: Boolean = false,
    /** The knob's state; a row that wants a tap anywhere on it to widen the knob passes its own and feeds it. */
    key: KeyState = rememberKeyState(),
) {
    val colors = PlumTheme.colors
    val motion = PlumTheme.motion
    // The knob slides by transform only, or jumps when motion is off.
    val travel by animateFloatAsState(
        targetValue = if (isOn) 1f else 0f,
        animationSpec = motion.slideSpec(),
        label = "switch knob",
    )
    Box(
        modifier =
            modifier
                .semantics { stateDescription = stateText }
                .size(PlumDimens.switchWidth, PlumDimens.switchHeight)
                .keyDrive(key, isFocused)
                .drawBehind { drawSunken(if (isOn) colors.accent else colors.chip, colors.inset) },
        contentAlignment = Alignment.CenterStart,
    ) {
        val knobInset = (PlumDimens.switchHeight - PlumDimens.switchKnob) / 2
        Box(
            Modifier
                .padding(start = knobInset)
                .graphicsLayer { translationX = travel * (PlumDimens.switchWidth - PlumDimens.switchHeight).toPx() }
                .size(PlumDimens.switchKnob)
                .drawBehind {
                    // Squeezed: wider by a quarter of the knob toward the middle of the track, the face down into its edge.
                    val grow = size.width * PlumDimens.SWITCH_SQUEEZE * key.press
                    val atEnd = travel > 0.5f
                    inset(if (atEnd) -grow else 0f, 0f, if (atEnd) 0f else -grow, 0f) {
                        drawFlatFace(if (isOn) colors.knobOnFace() else colors.knobFace, press = key.press, raise = 0f)
                    }
                },
        )
    }
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
