package com.plumemu.coreui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.theme.ButtonDimens
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumTheme

/** How a [PlumButton] looks (docs/design/rules.md › Buttons): the main one in the accent, a key-like one, a bare one, a risky one. */
enum class ButtonKind { Primary, Secondary, Quiet, Danger }

/**
 * A pill button in a [CollarSize.Control] collar (the V2 look). [kind]: Primary has an accent cap with text on accent; Secondary a
 * `collarCap` cap with text colour; Danger the same with the danger colour; Quiet shows only its text until it is [focused] or
 * pressed, then the collar and cap appear. Focus lights the groove in the accent with the halo (Secondary and Danger text turns
 * strong / semi-bold); a press sinks the cap into its edge; [enabled] false draws the off face with muted text (no fade), and
 * the button swallows touches, so a `clickable` in [modifier] never fires. The cap is `PlumTheme.layout.setupButtonHeight` high
 * (it grows with text size) and the collar is added outside it; [maxLines] 2 lets the label wrap and the cap grow.
 * [state] is the press and lift state; pass one only to hold a press (a test). The caller passes taps through [modifier] and decides `focused`; core-ui does not read input.
 * Reaches the collar's shadow below its bounds and the halo around it: leave that unclipped.
 */
@Composable
fun PlumButton(
    label: String,
    kind: ButtonKind,
    focused: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxLines: Int = 1,
    state: KeyState = rememberKeyState(),
    /** The collar's size: [CollarSize.Control] for a button; [CollarSize.Key] where the room is short (a form over the tray). */
    collarSize: CollarSize = CollarSize.Control,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val capHeight = PlumTheme.layout.setupButtonHeight
    val quiet = kind == ButtonKind.Quiet
    val key = state
    val lit = focused && enabled
    // The groove's light and the halo grow in FOCUS_GROW_MS, like Modifier.collar(focused).
    val focus = remember { Animatable(if (lit) 1f else 0f) }
    val motionOn = PlumTheme.motion.isOn
    LaunchedEffect(lit, motionOn) {
        focus.animateTo(if (lit) 1f else 0f, if (motionOn) tween(PlumMotion.FOCUS_GROW_MS, easing = PlumMotion.EaseOut) else snap())
    }
    Box(
        modifier =
            modifier
                .drawBehind {
                    // A quiet button has no collar at rest; it appears with focus or a press.
                    if (!quiet || focus.value > 0f || key.press > 0f) {
                        val partHeight = size.height - collarSize.padding.let { (it.calculateTopPadding() + it.calculateBottomPadding()).toPx() }
                        drawCollar(colors.collar, collarSize, partHeight / 2, focus.value, accent = colors.accent, glow = colors.partGlow)
                    }
                }
                .padding(collarSize.padding)
                .semantics {
                    role = Role.Button
                    if (!enabled) disabled()
                }
                .then(if (maxLines > 1) Modifier.heightIn(min = capHeight) else Modifier.height(capHeight))
                .then(if (enabled) Modifier.keyDrive(key, focused) else Modifier)
                .drawWithContent {
                    val shown = if (quiet) enabled && (focus.value > 0f || key.press > 0f) else true
                    val press = if (enabled) key.press else 1f
                    val seat = colors.collar.seat
                    val cap =
                        when {
                            !enabled -> seat.offCap
                            kind == ButtonKind.Primary -> seat.buttonAccent(colors.accent)
                            else -> seat.buttonCap
                        }
                    // The board's CAP (ACCENT CAP for the main one) layer by layer; pressed and off sit 2 dp down in the capDown / capOff face.
                    if (shown) drawCssCap(cap, press = press)
                    translate(top = cap.travel.toPx() * press) { this@drawWithContent.drawContent() }
                }
                // Inside the caller's clickable: a child that consumes the touch keeps the parent's tap from firing.
                .then(if (enabled) Modifier else Modifier.pointerInput(Unit) { swallowTouches() })
                .padding(horizontal = ButtonDimens.padding, vertical = if (maxLines > 1) ButtonDimens.wrapPadding else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        val bright = focused && enabled && kind != ButtonKind.Primary
        val base = if (kind == ButtonKind.Primary || bright) type.setupButton else type.setupChoiceFocused
        val style = if (maxLines > 1) base.copy(textAlign = TextAlign.Center) else base
        BasicText(
            text = label,
            style = style,
            color = {
                when {
                    !enabled -> colors.collar.offText
                    kind == ButtonKind.Primary -> colors.onAccentFace
                    kind == ButtonKind.Danger -> colors.danger
                    bright -> colors.strong
                    quiet -> colors.text2
                    else -> colors.text
                }
            },
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.swallowTouches() {
    awaitPointerEventScope {
        while (true) awaitPointerEvent().changes.forEach { it.consume() }
    }
}
