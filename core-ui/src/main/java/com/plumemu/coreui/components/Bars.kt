package com.plumemu.coreui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.ButtonColors
import com.plumemu.coreui.theme.ColorMath
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.Rgb
import com.plumemu.coreui.theme.toComposeColor

/*
 * The two bars around the strip (docs/design/rules.md › The strip): the top bar, 18 high, says
 * where you are and shows clock and battery; the hint bar, 22 high, shows the buttons that do
 * something here. Both span the screen inside the 24 dp side margins.
 */

/**
 * Top bar. [place] is the whole "where you are" text ("18 systems · 1,284 games"); its first
 * occurrence of [emphasis] ("18 systems") is drawn in `text2`, the rest in `muted`. [clock] and
 * [battery] are read only by their own texts, so a minute tick redraws the clock alone; the
 * battery is left out while unknown, and either is left out while switched off (null). [wifi] says
 * whether the Wi-Fi glyph shows, between the clock and the battery (the callers pass false with
 * the battery off); [wifiDescription] is its spoken name. [charging] says whether the bolt stands
 * before the percentage (the device is on its charger); [chargingDescription] is its spoken name.
 */
@Composable
fun TopBar(
    place: String,
    emphasis: String,
    clock: () -> String?,
    battery: () -> String?,
    modifier: Modifier = Modifier,
    wifi: () -> Boolean = { false },
    wifiDescription: String = "",
    charging: () -> Boolean = { false },
    chargingDescription: String = "",
    /** The place text's opacity, read while drawing: the home fades it to show a notice and back. */
    placeAlpha: () -> Float = { 1f },
) {
    val colors = PlumTheme.colors
    // The colour is read while drawing (`color` below): a moved theme colour redraws the bar and recomposes nothing.
    val style = PlumTheme.type.topBar
    val muted: () -> Color = { colors.muted }
    // The emphasised words are in `text2`, a colour that moves with a slider's preview, and a span's colour is a value
    // read in composition. So the place is drawn twice, one over the other: [text] with the emphasis made
    // transparent (in `muted`) and [emphasised] with everything else transparent (in `text2`); both colours are read while drawing.
    val text =
        remember(place, emphasis) { placeWithOnly(place, emphasis, keepEmphasis = false) }
    val emphasised =
        remember(place, emphasis) { if (emphasis.isEmpty() || !place.contains(emphasis)) null else placeWithOnly(place, emphasis, keepEmphasis = true) }
    val text2: () -> Color = { colors.text2 }
    Row(
        modifier = modifier.fillMaxWidth().height(PlumTheme.layout.topBarHeight).padding(horizontal = PlumDimens.stripMarginSide),
        horizontalArrangement = Arrangement.spacedBy(PlumDimens.topBarItemGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).graphicsLayer { alpha = placeAlpha() }) {
            BasicText(text = text, style = style, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Same text, same width, so the same line and the same cut; read once by the semantics (the one above).
            if (emphasised != null) BasicText(text = emphasised, style = style, color = text2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clearAndSetSemantics { })
        }
        StatusText(clock, style, muted)
        WifiStatus(wifi, style, wifiDescription, muted)
        BatteryStatus(battery, charging, style, chargingDescription, muted)
    }
}

/** [place] with the first [emphasis] kept (or, with [keepEmphasis] false, everything but it) and the rest transparent: a text colour is not read from here. */
private fun placeWithOnly(
    place: String,
    emphasis: String,
    keepEmphasis: Boolean,
): AnnotatedString {
    val start = if (emphasis.isEmpty()) -1 else place.indexOf(emphasis)
    if (start < 0) return AnnotatedString(place)
    val end = start + emphasis.length
    val hidden = SpanStyle(color = Color.Transparent)
    return buildAnnotatedString {
        append(place)
        if (keepEmphasis) {
            addStyle(hidden, 0, start)
            addStyle(hidden, end, place.length)
        } else {
            addStyle(hidden, start, end)
        }
    }
}

/** The Wi-Fi glyph while [connected] says so, read in its own scope like the texts beside it. */
@Composable
fun WifiStatus(
    connected: () -> Boolean,
    style: TextStyle,
    description: String,
    color: () -> Color = { style.color },
) {
    if (connected()) WifiGlyph(style, color, description, Modifier.testTag(TopBarTags.WIFI))
}

/**
 * The battery percentage read in its own scope like the texts beside it, left out while unknown (null), with the
 * charging bolt before it while [charging] says the device is on its charger; [chargingDescription] is the bolt's spoken name.
 */
@Composable
fun BatteryStatus(
    battery: () -> String?,
    charging: () -> Boolean,
    style: TextStyle,
    chargingDescription: String,
    color: () -> Color = { style.color },
) {
    val value = battery() ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(PlumDimens.topBarChargingGap), verticalAlignment = Alignment.CenterVertically) {
        if (charging()) ChargingGlyph(style, color, chargingDescription, Modifier.testTag(TopBarTags.CHARGING))
        BasicText(text = value, style = style, color = color, maxLines = 1)
    }
}

/** Test tag of a hint's dot ([Hint] with `hasDot`). */
const val HINT_DOT_TAG = "hint_dot"

/** Test tag of a hint's key cap (the round pill with "A", "START", "L1 R1"). */
const val HINT_KEY_CAP_TAG = "hint_key_cap"

/** Test tag of the top bar's Wi-Fi glyph. */
object TopBarTags {
    const val WIFI = "top_bar_wifi"

    /** The charging bolt before the battery percentage. */
    const val CHARGING = "top_bar_charging"
}

/** A status text read in its own scope: a new value recomposes this text only. */
@Composable
private fun StatusText(
    text: () -> String?,
    style: TextStyle,
    color: () -> Color = { style.color },
) {
    val value = text() ?: return
    BasicText(text = value, style = style, color = color, maxLines = 1)
}

/** The hint bar; put [Hint]s in [content], left to right. */
@Composable
fun HintBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(PlumTheme.layout.hintBarHeight).padding(horizontal = PlumDimens.stripMarginSide),
        horizontalArrangement = Arrangement.spacedBy(PlumDimens.hintGap),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * The hint bar for a row of hints that may not all fit (a narrow screen, a wide font, a long
 * language): each hint keeps its natural width, and when they do not fit the ones named in
 * [dropOrder] (indices into the hints, first dropped first) are left out until the rest fit, then
 * any others from the end. A left-out hint is neither drawn nor tappable; nothing is cut or
 * overlapped. Put [Hint]s in [content], left to right.
 */
@Composable
fun FittingHintBar(
    modifier: Modifier = Modifier,
    dropOrder: List<Int> = emptyList(),
    content: @Composable () -> Unit,
) {
    Layout(
        content = content,
        modifier = modifier.fillMaxWidth().height(PlumTheme.layout.hintBarHeight).padding(horizontal = PlumDimens.stripMarginSide),
    ) { measurables, constraints ->
        val gap = PlumDimens.hintGap.roundToPx()
        val placeables = measurables.map { it.measure(Constraints(maxHeight = constraints.maxHeight)) }
        val shown = BooleanArray(placeables.size) { true }
        fun width(): Int = placeables.indices.filter { shown[it] }.let { kept -> kept.sumOf { placeables[it].width } + gap * (kept.size - 1).coerceAtLeast(0) }
        val order = dropOrder.filter { it in placeables.indices } + placeables.indices.reversed()
        for (index in order) {
            if (width() <= constraints.maxWidth) break
            shown[index] = false
        }
        val height = constraints.maxHeight
        layout(constraints.maxWidth, height) {
            var x = 0
            placeables.forEachIndexed { index, placeable ->
                if (!shown[index]) return@forEachIndexed
                placeable.placeRelative(x, (height - placeable.height) / 2)
                x += placeable.width + gap
            }
        }
    }
}

/**
 * The hint bar of the grid (the board's RP5-3 and RP5-4): the focused game's [title] and its
 * [meta] ("1993 · Capcom") on the left from [startInset] (where the grid starts), the hints at
 * the right, 16 apart. Put [Hint]s in [hints], left to right.
 *
 * When they do not all fit (a narrow screen, a large text size, a long title), the hints named in
 * [dropBeforeMeta] go first, but only if that keeps the meta; then the meta goes, then the hints named in [dropOrder] (indices into the hints, first dropped first), so
 * the title is never cut while a hint could make room; a left-out hint is neither drawn nor
 * tappable. Only a title longer than the bar with one hint left is cut, with an ellipsis.
 */
@Composable
fun TitledHintBar(
    title: String,
    meta: String,
    startInset: Dp,
    modifier: Modifier = Modifier,
    dropOrder: List<Int> = emptyList(),
    dropBeforeMeta: List<Int> = emptyList(),
    hints: @Composable () -> Unit,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val style = type.gridFocusTitle.copy(color = colors.text)
    val small = type.smallFact
    val withMeta = remember(title, meta, colors.muted, small) { focusTitle(title, meta, colors.muted, small) }
    val titleOnly = remember(title) { AnnotatedString(title) }
    val measurer = rememberTextMeasurer()
    SubcomposeLayout(
        modifier =
            modifier
                .fillMaxWidth()
                .height(PlumTheme.layout.hintBarHeight)
                .padding(start = startInset, end = PlumDimens.stripMarginSide),
    ) { constraints ->
        val gap = PlumDimens.gridHintGap.roundToPx()
        val height = constraints.maxHeight
        val hintPlaceables = subcompose(HINTS_SLOT, hints).map { it.measure(Constraints(maxHeight = height)) }
        val shown = BooleanArray(hintPlaceables.size) { true }
        fun hintsWidth(): Int = hintPlaceables.indices.filter { shown[it] }.let { kept -> kept.sumOf { hintPlaceables[it].width } + gap * kept.size }
        fun fits(text: AnnotatedString): Boolean = measurer.measure(text, style, maxLines = 1).size.width + hintsWidth() <= constraints.maxWidth
        if (meta.isNotEmpty() && !fits(withMeta)) {
            // The meta matters more than these hints: leave them out when that is what lets the meta stay.
            val before = dropBeforeMeta.filter { it in hintPlaceables.indices && it != 0 }
            before.forEach { shown[it] = false }
            if (!fits(withMeta)) before.forEach { shown[it] = true }
        }
        val text = if (meta.isNotEmpty() && fits(withMeta)) withMeta else titleOnly
        if (text === titleOnly) {
            // Keep the first hint (A) whatever happens: the bar always says what A does.
            for (index in dropOrder.filter { it in hintPlaceables.indices && it != 0 }) {
                if (fits(titleOnly)) break
                shown[index] = false
            }
        }
        val titleWidth = (constraints.maxWidth - hintsWidth()).coerceAtLeast(0)
        val titlePlaceable =
            subcompose(TITLE_SLOT) {
                BasicText(text = text, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }.single().measure(Constraints(maxWidth = titleWidth, maxHeight = height))
        layout(constraints.maxWidth, height) {
            titlePlaceable.placeRelative(0, (height - titlePlaceable.height) / 2)
            var x = constraints.maxWidth - hintsWidth() + gap
            hintPlaceables.forEachIndexed { index, placeable ->
                if (!shown[index]) return@forEachIndexed
                placeable.placeRelative(x, (height - placeable.height) / 2)
                x += placeable.width + gap
            }
        }
    }
}

private const val HINTS_SLOT = "hints"
private const val TITLE_SLOT = "title"

/** "Mega Man X · 1993 · Capcom": the title 13 / 600 in `text`, the meta after it 13 in [muted]. */
private fun focusTitle(
    title: String,
    meta: String,
    muted: Color,
    small: TextStyle,
): AnnotatedString =
    buildAnnotatedString {
        append(title)
        if (meta.isNotEmpty()) {
            withStyle(SpanStyle(color = muted, fontSize = small.fontSize, fontWeight = small.fontWeight)) {
                append(" · ")
                append(meta)
            }
        }
    }

/**
 * One hint: a round key cap ([keyCap], e.g. "A" or "L1 R1") and its [label]. Every hint is
 * tappable: pass the touch handling in [modifier] (core-input's `Modifier.hint`).
 * [hasDot] adds the 6 dp accent dot after the label: something new behind this hint (an update
 * found, for START Settings), with no toast or pop-up over the screen.
 *
 * The cap looks pressed while its button is held ([LocalHeldKeys], matched by the cap's text through
 * [keyCapNames]: "A", "L1 R1", "START", "↑↓") and while a finger is down on the hint. So every hint
 * lights without being told its control; a new cap text only has to use the button's name.
 */
@Composable
fun Hint(
    keyCap: String,
    label: String,
    modifier: Modifier = Modifier,
    hasDot: Boolean = false,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val buttons = PlumTheme.buttons
    // Without a ready-made button set the cap's colours are theme colours, read while drawing (a colour slider's
    // preview moves them without recomposing the hint); with one, the look is read here, as that set seldom changes.
    val setLook = if (buttons?.forKeyCap(keyCap) != null) keyCapLook(keyCap, buttons, colors) else null
    val capShape = RoundedCornerShape(PlumTheme.layout.keyCapHeight / 2)
    val press = rememberHintPress(keyCap)
    val scales = PlumTheme.motion.isOn
    Row(
        modifier = modifier.height(PlumTheme.layout.hintBarHeight).trackTouch(press),
        horizontalArrangement = Arrangement.spacedBy(PlumDimens.hintKeyGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .testTag(HINT_KEY_CAP_TAG) // outside the layer: its bounds stay the resting cap's
                    // Pressed (design/rules.md › Shapes and motion › Pressed): only the cap dips, and
                    // `press.progress` is read in the layer and draw blocks, never in composition.
                    .graphicsLayer {
                        val scale = if (scales) 1f - (1f - PlumMotion.HINT_PRESS_SCALE) * press.progress.value else 1f
                        scaleX = scale
                        scaleY = scale
                    }
                    .height(PlumTheme.layout.keyCapHeight)
                    .defaultMinSize(minWidth = PlumDimens.keyCapMinWidth)
                    .then(if (setLook?.fill != null) Modifier.background(setLook.fill, capShape) else Modifier)
                    // Pressed: the ring turns to the text colour too. It is drawn after the border, over it.
                    .drawWithContent {
                        drawContent()
                        val p = press.progress.value
                        if (p > 0f) {
                            val w = PlumDimens.keyCapBorder.toPx()
                            drawRoundRect(
                                colors.text.copy(alpha = p),
                                topLeft = Offset(w / 2, w / 2),
                                size = Size(size.width - w, size.height - w),
                                cornerRadius = CornerRadius((size.height - w) / 2),
                                style = Stroke(w),
                            )
                        }
                    }
                    .then(
                        if (setLook != null) {
                            Modifier.border(PlumDimens.keyCapBorder, setLook.edge, capShape)
                        } else {
                            // The same ring `border` draws, with the colour read while drawing.
                            Modifier.drawWithContent {
                                drawContent()
                                val w = kotlin.math.ceil(PlumDimens.keyCapBorder.toPx())
                                drawRoundRect(
                                    colors.chip,
                                    topLeft = Offset(w / 2, w / 2),
                                    size = Size(size.width - w, size.height - w),
                                    cornerRadius = CornerRadius((size.height - w) / 2),
                                    style = Stroke(w),
                                )
                            }
                        },
                    )
                    .drawBehind {
                        val p = press.progress.value
                        if (p > 0f) {
                            drawRoundRect(colors.text.copy(alpha = p), cornerRadius = CornerRadius(size.height / 2))
                        }
                    }
                    // The board's border-box: 1.5 border + 5 padding on each side (text + 13).
                    // Compose draws the border inside the box, so the padding covers both.
                    .padding(horizontal = PlumDimens.keyCapPadding + PlumDimens.keyCapBorder),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = keyCap,
                style = type.keyCap,
                maxLines = 1,
                color = { lerp(setLook?.letter ?: colors.text, colors.bg, press.progress.value) },
            )
        }
        BasicText(text = label, style = type.hintLabel, color = { colors.muted }, maxLines = 1)
        if (hasDot) Box(Modifier.size(PlumDimens.hintDot).drawBehind { drawCircle(colors.accent) }.testTag(HINT_DOT_TAG))
    }
}

/** How a key cap is drawn: its [fill] (null: none, the usual ring on the background), its ring ([edge]) and its [letter]. */
data class KeyCapLook(
    val fill: Color?,
    val edge: Color,
    val letter: Color,
)

/**
 * [keyCap]'s look. While [buttons] are in use (the set the person last picked) an
 * A, B, X or Y cap is filled with its printed letter's colour, its ring the same colour (so the cap
 * keeps its size and shape) and the letter white or dark as [com.plumemu.coreui.theme.ButtonColor]
 * says. A fill under [MIN_FILL_CONTRAST] against the background (a yellow B on Banana) gets the
 * `chip` ring instead (the token that always reaches 3:1 on the background, the usual key ring),
 * so the cap never melts into it. Every other cap, and every cap without [buttons], is the usual
 * `chip` ring with `text`.
 */
fun keyCapLook(
    keyCap: String,
    buttons: ButtonColors?,
    colors: PlumComposeColors,
): KeyCapLook {
    val glyph = buttons?.forKeyCap(keyCap) ?: return KeyCapLook(fill = null, edge = colors.chip, letter = colors.text)
    val fill = glyph.fill.toComposeColor()
    val background = Rgb(colors.bg.toArgb() and 0xFFFFFF)
    val melts = ColorMath.contrast(glyph.fill, background) < MIN_FILL_CONTRAST
    return KeyCapLook(fill = fill, edge = if (melts) colors.chip else fill, letter = glyph.letter.toComposeColor())
}

/** A key cap's fill needs this much contrast with the background, or it gets a ring (WCAG's bar for graphics). */
const val MIN_FILL_CONTRAST = 3.0
