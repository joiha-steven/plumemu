package com.plumemu.coreui.components.keyboard

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumLayout
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.TypeRole
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * The words the keyboard needs from the app's strings (core-ui has no string resources): the
 * space key's label and what a screen reader says for the three icon keys.
 */
@Immutable
data class KeyboardLabels(
    val space: String,
    val shift: String,
    val delete: String,
    val hide: String,
)

/**
 * Plumemu's own on-screen keyboard (docs/design/rules.md › Keyboard): four rows of keys,
 * centred, on a tray that runs from the screen's left edge to its right edge, from 8 dp above the keys down
 * to the bottom of what the caller gives it: [PlumLayout.keyboardTrayHeight] on a screen, the tray
 * running on under the keys to the screen's bottom with the caller's hint bar on it
 * ([PlumLayout.keyboardHeight] is the keys' part).
 *
 * [state] holds the focus, page and shift ([rememberKeyboardState] with the same [spec]); the
 * caller moves and presses through it. A tap on a key focuses it and presses it at once, and
 * every key's touch area is its key plus half of each gap around it (at least 40 dp). Events go to
 * [onEvent]; page and shift keys change [state] and send nothing.
 *
 * Nothing runs while it is idle: no animation, no caret, no frame callbacks. A focus
 * move recomposes the two keys whose focus changed and nothing else.
 *
 * The focused key's ring reaches 2 dp past it: under the last row that is 2 dp below this
 * composable, so the caller must not clip it there.
 */
@Composable
fun PlumKeyboard(
    state: KeyboardState,
    spec: KeyboardSpec,
    labels: KeyboardLabels,
    onEvent: (KeyboardEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(state.hasShift == spec.hasShift) { "the state was made for hasShift = ${state.hasShift}, the spec says ${spec.hasShift}" }
    val colors = PlumTheme.colors
    val layout = PlumTheme.layout
    val currentOnEvent by rememberUpdatedState(onEvent)
    val onTap: (KeyId) -> Unit = remember(state) { { id -> state.tap(id)?.let { currentOnEvent(it) } } }
    val page = state.page
    val rows = KeyboardLayout.rows(page, state.hasShift)
    val keyHeight = layout.keyboardKeyHeight
    val totalHeight = layout.keyboardHeight
    Layout(
        content = {
            rows.forEachIndexed { r, row ->
                row.forEachIndexed { i, slot ->
                    key(page, r, i) { KeyCell(state, KeyId(r, i), slot, spec.actionLabel, labels, onTap) }
                }
            }
        },
        modifier = modifier.fillMaxWidth().drawBehind { drawTray(colors) },
    ) { measurables, constraints ->
        val gap = PlumDimens.keyboardKeyGap.toPx()
        val screenWidth = constraints.maxWidth.toDp()
        val pitch = PlumLayout.keyboardColumnWidth(screenWidth).toPx() + gap
        val rowPitch = keyHeight.toPx() + gap
        // Cells tile the keys and their gaps: each starts half a gap before its key.
        val left = (constraints.maxWidth - PlumLayout.keyboardBlockWidth(screenWidth).toPx()) / 2 - gap / 2
        val top = PlumDimens.keyboardTopPadding.toPx() - gap / 2
        val cells = rows.flatMapIndexed { r, row -> row.map { slot -> r to slot } }
        val placed =
            measurables.zip(cells).map { (measurable, cell) ->
                val (r, slot) = cell
                val x0 = (left + slot.start * pitch).roundToInt()
                val x1 = (left + (slot.start + slot.span) * pitch).roundToInt()
                val y0 = (top + r * rowPitch).roundToInt()
                val y1 = (top + (r + 1) * rowPitch).roundToInt()
                Triple(measurable.measure(Constraints.fixed(x1 - x0, y1 - y0)), x0, y0)
            }
        val height = maxOf(constraints.minHeight, totalHeight.roundToPx())
        layout(constraints.maxWidth, height) {
            placed.forEach { (placeable, x, y) -> placeable.place(x, y) }
        }
    }
}

/** The test tag of the key at [id] on the page shown: its whole touch area. */
fun keyboardKeyTag(id: KeyId): String = "keyboard_key_${id.row}_${id.index}"

/** One key with its share of the gaps around it: the touch area. Recomposes when its own focus, or its shift look, changes. */
@Composable
private fun KeyCell(
    state: KeyboardState,
    id: KeyId,
    slot: KeySlot,
    actionLabel: String,
    labels: KeyboardLabels,
    onTap: (KeyId) -> Unit,
) {
    val focused by remember(state, id) { state.isFocused(id) }
    val key = slot.key
    val followsShift = key == KeyboardKey.Shift || (key is KeyboardKey.Character && key.char.isLetter())
    val shift = if (followsShift) state.shift else ShiftMode.Off
    val colors = PlumTheme.colors
    val look = keyLook(key, focused, shift, colors)
    val icon = iconOf(key)
    val description = icon?.let { spokenName(it, labels) }
    val halfGap = PlumDimens.keyboardKeyGap / 2
    Box(
        modifier =
            Modifier
                .testTag(keyboardKeyTag(id))
                .pointerInput(id, onTap) { detectTapGestures(onTap = { onTap(id) }) }
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    if (description != null) contentDescription = description
                    onClick {
                        onTap(id)
                        true
                    }
                }.drawBehind {
                    drawKey(look.fill, if (focused) colors.accent else null, if (focused && key == KeyboardKey.Action) colors.onAccent else null)
                }
                .padding(halfGap),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Spacer(Modifier.size(PlumTheme.layout.keyboardIconSize).drawBehind { drawKeyIcon(icon, look.ink, shift) })
        } else {
            BasicText(
                text = labelOf(key, shift, actionLabel, labels),
                style = PlumTheme.type[look.role].copy(color = look.ink),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun iconOf(key: KeyboardKey): KeyIcon? =
    when (key) {
        KeyboardKey.Shift -> KeyIcon.Shift
        KeyboardKey.Delete -> KeyIcon.Delete
        KeyboardKey.Hide -> KeyIcon.Hide
        else -> null
    }

/** What a screen reader says for an icon key. */
private fun spokenName(
    icon: KeyIcon,
    labels: KeyboardLabels,
): String =
    when (icon) {
        KeyIcon.Shift -> labels.shift
        KeyIcon.Delete -> labels.delete
        KeyIcon.Hide -> labels.hide
    }

/** The word or character a key without an icon shows; a letter in upper case while shift is on. */
private fun labelOf(
    key: KeyboardKey,
    shift: ShiftMode,
    actionLabel: String,
    labels: KeyboardLabels,
): String =
    when (key) {
        is KeyboardKey.Character -> (if (shift != ShiftMode.Off) key.char.uppercaseChar() else key.char).toString()
        is KeyboardKey.Page -> key.label
        KeyboardKey.Space -> labels.space
        KeyboardKey.Action -> actionLabel
        KeyboardKey.Shift, KeyboardKey.Delete, KeyboardKey.Hide -> ""
    }

/** A key's fill (null: none), ink and type role. */
private data class KeyLook(
    val fill: Color?,
    val ink: Color,
    val role: TypeRole,
)

/**
 * The board's look: character keys plain (`text`, 400); keys with a word or icon on a `trayKey`
 * fill (`text2`, 400); the focused key on `trayKey` in `strong`, 600, with the accent ring; the
 * action key an accent button with its word in `onAccent`, 600 (focused: the accent ring around it
 * and an `onAccent` ring inside it);
 * shift in the accent while it is on.
 */
private fun keyLook(
    key: KeyboardKey,
    focused: Boolean,
    shift: ShiftMode,
    colors: PlumComposeColors,
): KeyLook {
    if (key is KeyboardKey.Character) {
        return if (focused) {
            KeyLook(colors.trayKey, colors.strong, TypeRole.KeyboardKeyFocused)
        } else {
            KeyLook(null, colors.text, TypeRole.KeyboardKey)
        }
    }
    // The action is the field's primary button: accent fill, onAccent word (accent text on trayKey was
    // under 4.5:1, 3.42 with the default red on Graphite). Focused, it keeps the fill and gets the ring
    // outside it plus an onAccent ring inside it (drawKey), since an accent ring on accent does not show.
    if (key == KeyboardKey.Action) return KeyLook(colors.accent, colors.onAccent, TypeRole.KeyboardWordStrong)
    val ink =
        when {
            key == KeyboardKey.Shift && shift != ShiftMode.Off -> colors.accent
            focused -> colors.strong
            else -> colors.text2
        }
    return KeyLook(colors.trayKey, ink, if (focused) TypeRole.KeyboardWordStrong else TypeRole.KeyboardWord)
}

/**
 * The key inside its cell (half a gap in on every side), its fill and, when focused, the ring just
 * outside it. [innerRing] is for the action key, whose accent fill hides the accent ring: a second
 * ring inside the fill, 3 dp in from its edge, so the focus shows without growing into the gaps.
 */
private fun DrawScope.drawKey(
    fill: Color?,
    ring: Color?,
    innerRing: Color?,
) {
    val inset = PlumDimens.keyboardKeyGap.toPx() / 2
    val corner = PlumDimens.cornerRow.toPx()
    val keySize = Size(size.width - inset * 2, size.height - inset * 2)
    if (fill != null) drawRoundRect(fill, Offset(inset, inset), keySize, CornerRadius(corner))
    if (ring != null) {
        val width = PlumDimens.keyboardFocusRing.toPx()
        val out = width / 2
        drawRoundRect(
            color = ring,
            topLeft = Offset(inset - out, inset - out),
            size = Size(keySize.width + out * 2, keySize.height + out * 2),
            cornerRadius = CornerRadius(corner + out),
            style = Stroke(width),
        )
    }
    if (innerRing != null) {
        val width = PlumDimens.keyboardFocusRing.toPx()
        val innerInset = inset + PlumDimens.keyboardActionRingInset.toPx() + width / 2
        drawRoundRect(
            color = innerRing,
            topLeft = Offset(innerInset, innerInset),
            size = Size(size.width - innerInset * 2, size.height - innerInset * 2),
            cornerRadius = CornerRadius((corner - (innerInset - inset)).coerceAtLeast(0f)),
            style = Stroke(width),
        )
    }
}

/**
 * The tray: its fill, the 1 dp hairline along its top, and above it the faint upward shadow of
 * the board's `box-shadow: 0 -6px 12px -4px` (offset up 6, blur 12, pulled in 4): its edge sits
 * 2 dp above the tray and fades over the blur, drawn as a gradient (no blur filter: see FocusRing).
 */
private fun DrawScope.drawTray(colors: PlumComposeColors) {
    val sigma = PlumDimens.keyboardShadowBlur.toPx() / 2
    val edge = -(PlumDimens.keyboardShadowOffset - PlumDimens.keyboardShadowSpread).toPx()
    val top = edge - PlumDimens.keyboardShadowBlur.toPx()
    val stops =
        Array(SHADOW_STOPS + 1) { i ->
            val fraction = i.toFloat() / SHADOW_STOPS
            val y = top + fraction * -top
            // Share of the shadow at y: the normal curve's integral past its edge, logistic form (as FocusRing).
            val share = 1f / (1f + exp(-LOGISTIC_SCALE * (y - edge) / sigma))
            fraction to colors.trayShadow.copy(alpha = colors.trayShadow.alpha * share)
        }
    drawRect(Brush.verticalGradient(*stops, startY = top, endY = 0f), Offset(0f, top), Size(size.width, -top))
    drawRect(colors.tray)
    drawRect(colors.trayEdge, size = Size(size.width, PlumDimens.keyboardEdge.toPx()))
}

private const val SHADOW_STOPS = 6

/** The logistic curve that tracks the normal distribution's integral (1.702, Bowling et al.). */
private const val LOGISTIC_SCALE = 1.702f
