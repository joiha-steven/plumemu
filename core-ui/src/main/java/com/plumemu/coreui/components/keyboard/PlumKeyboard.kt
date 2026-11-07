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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
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
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.components.collar
import com.plumemu.coreui.components.drawCssCap
import com.plumemu.coreui.theme.CssCap
import com.plumemu.coreui.theme.KeyboardDimens
import com.plumemu.coreui.theme.FlatFace
import com.plumemu.coreui.theme.KeyboardCollarDimens
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumLayout
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.TypeRole
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * The words the keyboard needs from the app's strings (core-ui has no string resources): the
 * space key's label and what a screen reader says for the four icon keys.
 */
@Immutable
data class KeyboardLabels(
    val space: String,
    val shift: String,
    val delete: String,
    val hide: String,
    val paste: String = "",
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
    val hasPaste = state.hasPaste
    val rows = KeyboardLayout.rows(page, state.hasShift, hasPaste)
    val keyHeight = layout.keyboardKeyHeight
    val totalHeight = layout.keyboardHeight
    Layout(
        content = {
            rows.forEachIndexed { r, row ->
                row.forEachIndexed { i, slot ->
                    key(page, hasPaste, r, i) { KeyCell(state, KeyId(r, i), slot, spec.actionLabel, labels, onTap) }
                }
            }
        },
        modifier = modifier.fillMaxWidth().drawBehind { drawTray(colors) },
    ) { measurables, constraints ->
        val gap = KeyboardDimens.keyboardKeyGap.toPx()
        val screenWidth = constraints.maxWidth.toDp()
        val pitch = PlumLayout.keyboardColumnWidth(screenWidth).toPx() + gap
        val rowPitch = keyHeight.toPx() + gap
        // Cells tile the keys and their gaps: each starts half a gap before its key.
        val left = (constraints.maxWidth - PlumLayout.keyboardBlockWidth(screenWidth).toPx()) / 2 - gap / 2
        val top = KeyboardDimens.keyboardTopPadding.toPx() - gap / 2
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
    val halfGap = KeyboardDimens.keyboardKeyGap / 2
    var pressed by remember { mutableStateOf(false) }
    val capCorner = KeyboardCollarDimens.capCorner
    Box(
        modifier =
            Modifier
                .testTag(keyboardKeyTag(id))
                .pointerInput(id, onTap) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            try {
                                tryAwaitRelease()
                            } finally {
                                pressed = false
                            }
                        },
                        onTap = { onTap(id) },
                    )
                }.semantics(mergeDescendants = true) {
                    role = Role.Button
                    if (description != null) contentDescription = description
                    onClick {
                        onTap(id)
                        true
                    }
                }.padding(halfGap)
                // The collar sits inside the key's 44 x 36, so the key's size does not change (KeyboardFitTest).
                .drawBehind { drawWellLight(capCorner.toPx()) }
                .collar(CollarSize.Well, focused = focused, radius = capCorner)
                .drawBehind {
                    // `pressed` is read here, while drawing: a press redraws this key and recomposes nothing.
                    drawCssCap(look.face, corner = capCorner.toPx(), press = if (pressed) 1f else 0f)
                    if (focused) drawFocusedKeyRing(colors.onAccent, capCorner.toPx())
                },
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
        KeyboardKey.Paste -> KeyIcon.Paste
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
        KeyIcon.Paste -> labels.paste
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
        KeyboardKey.Shift, KeyboardKey.Delete, KeyboardKey.Hide, KeyboardKey.Paste -> ""
    }

/** A key's cap face, ink and type role. */
private data class KeyLook(
    val face: CssCap,
    val ink: Color,
    val role: TypeRole,
)

/**
 * The board's look (the Search board): every key a cap in its collar; character keys on the cap
 * colour (`text`, 400), keys with a word or icon a touch lighter (`text2`, 400); the action key an accent cap with
 * its word in `onAccent`, 600; shift in the accent while it is on. The focused key, whichever it is, is the strongest
 * thing on the keyboard: the accent cap, its label in `onAccentFace`, 600, its collar's groove lit, and the inner ring
 * ([drawFocusedKeyRing]) the action key at rest does not have.
 */
private fun keyLook(
    key: KeyboardKey,
    focused: Boolean,
    shift: ShiftMode,
    colors: PlumComposeColors,
): KeyLook {
    val seat = colors.collar.seat
    // The accent cap's label is white unless under 3:1 (`onAccentFace`).
    val accentCap = seat.keyAccent(colors.accent)
    if (key is KeyboardKey.Character) {
        return if (focused) {
            KeyLook(accentCap, colors.onAccentFace, TypeRole.KeyboardKeyFocused)
        } else {
            KeyLook(seat.keyCap, colors.text, TypeRole.KeyboardKey)
        }
    }
    // The action is the field's primary button.
    if (key == KeyboardKey.Action || focused) return KeyLook(accentCap, colors.onAccentFace, TypeRole.KeyboardWordStrong)
    val ink = if (key == KeyboardKey.Shift && shift != ShiftMode.Off) colors.accent else colors.text2
    return KeyLook(seat.wordCap, ink, TypeRole.KeyboardWord)
}

/** The hairline of light just under the well: the key's outline moved down 1 px, drawn first. */
private fun DrawScope.drawWellLight(corner: Float) {
    val groove = KeyboardCollarDimens.WELL_GROOVE.toPx()
    drawRoundRect(
        KeyboardCollarDimens.wellLight,
        topLeft = Offset(0f, 1f),
        size = size,
        cornerRadius = CornerRadius(corner + groove),
    )
}

/**
 * The focused key: its accent cap hides the accent groove, so a ring in `onAccent` is drawn inside the cap,
 * [KeyboardDimens.keyboardActionRingInset] in from its edge. It tells the focused key from the action key at rest.
 */
private fun DrawScope.drawFocusedKeyRing(
    ring: Color,
    corner: Float,
) {
    val width = KeyboardDimens.keyboardFocusRing.toPx()
    val inset = KeyboardDimens.keyboardActionRingInset.toPx() + width / 2
    drawRoundRect(
        color = ring,
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2, size.height - inset * 2),
        cornerRadius = CornerRadius((corner - inset).coerceAtLeast(0f)),
        style = Stroke(width),
    )
}

/**
 * The tray: its fill, the 1 dp hairline along its top, and above it the faint upward shadow of
 * the board's `box-shadow: 0 -6px 12px -4px` (offset up 6, blur 12, pulled in 4): its edge sits
 * 2 dp above the tray and fades over the blur, drawn as a gradient (no blur filter: see FocusRing).
 */
private fun DrawScope.drawTray(colors: PlumComposeColors) {
    val sigma = KeyboardDimens.keyboardShadowBlur.toPx() / 2
    val edge = -(KeyboardDimens.keyboardShadowOffset - KeyboardDimens.keyboardShadowSpread).toPx()
    val top = edge - KeyboardDimens.keyboardShadowBlur.toPx()
    val stops =
        Array(SHADOW_STOPS + 1) { i ->
            val fraction = i.toFloat() / SHADOW_STOPS
            val y = top + fraction * -top
            // Share of the shadow at y: the normal curve's integral past its edge, logistic form (as FocusRing).
            val share = 1f / (1f + exp(-LOGISTIC_SCALE * (y - edge) / sigma))
            fraction to colors.trayShadow.copy(alpha = colors.trayShadow.alpha * share)
        }
    drawRect(Brush.verticalGradient(*stops, startY = top, endY = 0f), Offset(0f, top), Size(size.width, -top))
    // The board's tray: lighter at the top, darker toward the bottom.
    drawRect(
        Brush.verticalGradient(
            0f to KeyboardCollarDimens.trayTop(colors.tray),
            KeyboardCollarDimens.TRAY_MID to colors.tray,
            1f to KeyboardCollarDimens.trayBottom(colors.tray),
            startY = 0f,
            endY = size.height,
        ),
    )
    drawRect(colors.trayEdge, size = Size(size.width, KeyboardDimens.keyboardEdge.toPx()))
}

private const val SHADOW_STOPS = 6

/** The logistic curve that tracks the normal distribution's integral (1.702, Bowling et al.). */
private const val LOGISTIC_SCALE = 1.702f
