package com.plumemu.coreui.components

import android.view.ViewConfiguration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The key cap the confirm button is printed with ("A", or "B" under Confirm with B), the name
 * [LocalHeldKeys] holds while it is down. The app provides it next to its button letters; the default is
 * the usual "A".
 */
val LocalConfirmCap = staticCompositionLocalOf { "A" }

/**
 * A finger must stay down this long, without moving past touch slop, before an item shows the press (as
 * `clickable` does inside scrolling containers): a finger that starts a scroll never flashes the row. A tap
 * that lifts sooner still shows the whole dip, since the minimum visible time applies.
 */
fun pressFeelTouchDelayMs(): Long = ViewConfiguration.getTapTimeout().toLong()

/**
 * How pressed an item looks, 0 (rest) to 1; only draw blocks read [progress]. The timing is the hints':
 * 60 ms in, 100 ms out, and the dip stays at least 100 ms. Made once per item, but no coroutine or state
 * observer is attached until the item is focused or touched.
 */
internal class PressDip {
    val progress = Animatable(0f)
    private var pressedAt = Long.MIN_VALUE / 2

    /** True while a finger holds the item pressed, so losing focus does not release a touch. */
    var touching = false

    suspend fun press(motionOn: Boolean) =
        coroutineScope {
            // Times come from the frame clock (not the system clock), so tests on a virtual clock agree.
            launch { pressedAt = withFrameMillis { it } }
            // A quick release must not cut the dip short: the way in always finishes.
            withContext(NonCancellable) {
                progress.animateTo(1f, if (motionOn) tween(PlumMotion.PRESS_IN_MS, easing = PlumMotion.EaseOut) else tween(0))
            }
        }

    suspend fun release(motionOn: Boolean) {
        if (progress.value == 0f && progress.targetValue == 0f) return
        // With animations off the fill changes at once: no minimum visible time, no frame to wait for.
        if (!motionOn) {
            progress.snapTo(0f)
            return
        }
        val visibleMs = withFrameMillis { it } - pressedAt
        if (visibleMs < PlumMotion.PRESS_MIN_VISIBLE_MS) delay(PlumMotion.PRESS_MIN_VISIBLE_MS - visibleMs)
        progress.animateTo(0f, tween(PlumMotion.PRESS_OUT_MS, easing = PlumMotion.EaseOut))
    }
}

/**
 * Presses [dip] while the confirm button [cap] is held and [focusKey] names a focused item (null: none), for a
 * press that began while that same item was focused: a button already down when the item took the focus (A
 * opened the screen, or the D-pad moved the focus while A was held) shows nothing until it is let go and
 * pressed again. Read in a coroutine; the caller starts it only for something focused.
 */
internal suspend fun driveConfirmPress(
    dip: PressDip,
    held: State<Set<String>>,
    cap: String,
    motionOn: () -> Boolean,
    focusKey: () -> Any?,
) {
    var armed = false // a press can start only once the button was seen up while this item was focused
    var armedFor: Any? = null
    try {
        snapshotFlow { focusKey() to (cap in held.value) }
            .distinctUntilChanged()
            .collectLatest { (key, isDown) ->
                if (key != armedFor) armed = false
                armedFor = key
                when {
                    key == null -> {
                        armed = false
                        if (!dip.touching) dip.release(motionOn())
                    }
                    !isDown -> {
                        armed = true
                        if (!dip.touching) dip.release(motionOn())
                    }
                    armed -> dip.press(motionOn())
                    // Held since before this item had the focus (the mark moved under a held button): at rest.
                    else -> if (!dip.touching) dip.progress.snapTo(0f)
                }
            }
    } finally {
        // The item left the focus (or the screen) mid-press: put it back at once.
        if (!dip.touching) withContext(NonCancellable) { dip.progress.snapTo(0f) }
    }
}

/** Shows the press for a finger on the item, after [pressFeelTouchDelayMs] and only if it has not moved past touch slop. */
private fun Modifier.touchPress(
    dip: PressDip,
    scope: CoroutineScope,
    motionOn: () -> Boolean,
    onPress: () -> Unit = {},
): Modifier =
    pointerInput(dip) {
        val delayMs = pressFeelTouchDelayMs()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val slop = viewConfiguration.touchSlop
            // Ends by Up, Moved or Cancelled while waiting; null means the delay passed with the finger still.
            var outcome: String? = null
            withTimeoutOrNull(delayMs) {
                while (outcome == null) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                    outcome =
                        when {
                            change == null -> "cancelled"
                            change.changedToUp() -> "up"
                            (change.position - down.position).getDistance() > slop -> "moved"
                            else -> null
                        }
                }
            }
            when (outcome) {
                "moved", "cancelled" -> return@awaitEachGesture
                "up" -> {
                    // A quick tap: the whole dip, then let go.
                    onPress()
                    dip.touching = true
                    scope.launch {
                        // The button's driver can interrupt this press: `touching` must clear anyway, or the
                        // item stays dipped.
                        try {
                            dip.press(motionOn())
                        } finally {
                            dip.touching = false
                        }
                        dip.release(motionOn())
                    }
                    return@awaitEachGesture
                }
            }
            // Still down after the delay: pressed until it lifts, or moves past slop (a scroll or a drag began).
            onPress()
            dip.touching = true
            val pressing: Job = scope.launch { dip.press(motionOn()) }
            try {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                    if (change == null || change.changedToUp() || (change.position - down.position).getDistance() > slop) break
                }
            } finally {
                scope.launch {
                    pressing.join()
                    dip.touching = false
                    dip.release(motionOn())
                }
            }
        }
    }

/**
 * The press look of a focused row, tile or button (design/rules.md > Shapes and motion > Pressed): while the
 * confirm button is held the item scales to [PlumMotion.PRESS_SCALE] about its centre and its [fill] moves one step
 * toward the text colour; while a finger is down on it the same happens, focused or not. The fill is drawn
 * here (pass the item's focus fill, or null for none, and its [shape]) so the step can be mixed in: put this
 * where the item's `background` was, and outside a focus ring that must dip with the item. Only [focused] items
 * answer the button, and only for a press that began while they were focused. [answersButton] false keeps the
 * fill but ignores the button (a field row while Plumemu's keyboard is up: the press belongs to the key); a
 * finger still presses it.
 * With animations off the fill changes and the item does not shrink.
 *
 * An item that is not focused and not touched does no work on a key event: no coroutine, no read of the held
 * keys. The scale is a draw transform, so bounds and text baselines do not change.
 */
fun Modifier.pressFeel(
    focused: Boolean,
    fill: Color?,
    shape: Shape,
    answersButton: Boolean = true,
): Modifier = pressFeel(focused, { fill }, shape, answersButton)

/**
 * [pressFeel] with a fill that is read while drawing: pass `{ colors.sel2 }` (a theme colour) and a colour that moves
 * (a slider's preview) redraws the item without recomposing it. A fill that is null at draw time draws none.
 */
fun Modifier.pressFeel(
    focused: Boolean,
    fill: () -> Color?,
    shape: Shape,
    answersButton: Boolean = true,
): Modifier = pressFeelWith(focused, { fill() }, shape, answersButton, null)

/**
 * [pressFeel] with the fill and the optional ring chosen from the theme's colours, read while drawing. One composed block
 * (not a second one around it), so a row that wears it adds nothing to the recomposition count of a focus move.
 */
internal fun Modifier.pressFeelWith(
    focused: Boolean,
    fill: (PlumComposeColors) -> Color?,
    shape: Shape,
    answersButton: Boolean,
    ring: ((PlumComposeColors) -> Color?)?,
    /** A focused row of a home list: the list draws the pill ([rememberGlidingPill]), so this row draws no fill or ring and hands over its press dip. */
    listDrawsPill: Boolean = false,
): Modifier =
    composed {
        val dip = remember { PressDip() }
        val list = if (listDrawsPill && focused) LocalGlidingPill.current else null
        if (list != null) {
            SideEffect { list.press = dip }
            DisposableEffect(list, dip) {
                onDispose {
                    // No longer the focused row: the list has no row to draw its pill on until the next one reports.
                    if (list.press === dip) {
                        list.press = null
                        list.rowBox = null
                    }
                }
            }
        }
        val scope = rememberCoroutineScope()
        val motionOn by rememberUpdatedState(PlumTheme.motion.isOn)
        if (focused && answersButton) {
            val held = LocalHeldKeys.current
            val cap = LocalConfirmCap.current
            LaunchedEffect(held, cap, dip) { driveConfirmPress(dip, held, cap, { motionOn }) { Unit } }
        }
        // The colour set itself, read in the draw block below: a moved colour redraws this and recomposes nothing.
        val colors = PlumTheme.colors
        this
            .touchPress(dip, scope, motionOn = { motionOn })
            .then(
                if (list != null && list.measuresRows) {
                    Modifier.onPlaced {
                        val content = list.content
                        val top = if (content != null && content.isAttached) content.localPositionOf(it, Offset.Zero).y else it.positionInParent().y
                        list.rowBox = RowBox(top, it.size.height.toFloat(), it)
                    }
                } else {
                    Modifier
                },
            )
            .drawWithContent {
                val p = dip.progress.value
                val fill = if (list != null) null else fill(colors)
                if (p == 0f) {
                    if (fill != null) drawOutline(shape.createOutline(size, layoutDirection, this), fill)
                    if (fill != null && ring != null) ring(colors)?.let { drawRingInside(shape.createOutline(size, layoutDirection, this), it) }
                    drawContent()
                } else {
                    val scale = if (motionOn) 1f - (1f - PlumMotion.PRESS_SCALE) * p else 1f
                    withTransform({ scale(scale, scale, center) }) {
                        if (fill != null) drawOutline(shape.createOutline(size, layoutDirection, this), lerp(fill, colors.text, PlumMotion.PRESS_FILL_MIX * p))
                        if (fill != null && ring != null) ring(colors)?.let { drawRingInside(shape.createOutline(size, layoutDirection, this), it) }
                        this@drawWithContent.drawContent()
                    }
                }
            }
    }

/** A [PlumDimens.focusPillRing]-wide ring of [color] just inside the edge of [outline] (a rounded rectangle; any other outline gets a stroke twice as wide centred on its edge). */
internal fun DrawScope.drawRingInside(
    outline: Outline,
    color: Color,
) {
    val width = PlumDimens.focusPillRing.toPx()
    if (outline is Outline.Rounded) {
        val box = outline.roundRect
        val radius = (box.topLeftCornerRadius.x - width / 2).coerceAtLeast(0f)
        drawRoundRect(
            color = color,
            topLeft = Offset(box.left + width / 2, box.top + width / 2),
            size = Size(box.width - width, box.height - width),
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(width),
        )
    } else {
        drawOutline(outline, color, style = Stroke(width * 2))
    }
}

/**
 * For a screen that draws its own focus mark (the home grid's cover): a function giving the scale of the
 * focused item right now, 1 at rest and [PlumMotion.PRESS_SCALE] fully pressed by the confirm button (always 1
 * with animations off). [focusKey] names the item the mark is on (its index, say), null when nothing there has the
 * focus; a press that began before that item had it does not show. Call the result only in draw and layer blocks.
 */
@Composable
fun rememberConfirmPressScale(focusKey: () -> Any?): () -> Float {
    val held = LocalHeldKeys.current
    val cap = LocalConfirmCap.current
    val dip = remember { PressDip() }
    val motionOn by rememberUpdatedState(PlumTheme.motion.isOn)
    val currentFocusKey by rememberUpdatedState(focusKey)
    LaunchedEffect(held, cap, dip) { driveConfirmPress(dip, held, cap, { motionOn }) { currentFocusKey() } }
    return { if (motionOn) 1f - (1f - PlumMotion.PRESS_SCALE) * dip.progress.value else 1f }
}

/**
 * The finger's press for a grid of covers or tiles, where the focus mark is drawn by the grid and one finger
 * touches one item at a time: a single dip shared by the grid, with the index of the item it belongs to.
 * Per item, put [modifier] on the item and read [scaleOf] in its draw block; the grid's ring pass reads
 * [scaleOf] of the focused index too, so a focused item dips with its ring. Always 1 with animations off.
 */
class TouchPressScale internal constructor(
    private val dip: PressDip,
    private val scope: CoroutineScope,
    private val motionOn: () -> Boolean,
) {
    private var touched = -1

    /** The scale of item [index] right now: 1 at rest or when the finger is elsewhere, [PlumMotion.PRESS_SCALE] fully pressed. Read in draw blocks only. */
    fun scaleOf(index: Int): Float {
        // Always read, even for an item the finger is not on: that is what redraws it when the finger arrives.
        val progress = dip.progress.value
        return if (index == touched && motionOn()) 1f - (1f - PlumMotion.PRESS_SCALE) * progress else 1f
    }

    /** Detects the finger on item [index]. Does not consume anything. The dip belongs to the item only once the touch counts as a press. */
    fun modifier(index: Int): Modifier = Modifier.touchPress(dip, scope, motionOn = { motionOn() }, onPress = { touched = index })
}

@Composable
fun rememberTouchPressScale(): TouchPressScale {
    val dip = remember { PressDip() }
    val scope = rememberCoroutineScope()
    val motionOn by rememberUpdatedState(PlumTheme.motion.isOn)
    return remember(dip, scope) { TouchPressScale(dip, scope) { motionOn } }
}
