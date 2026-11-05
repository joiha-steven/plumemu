package com.plumemu.coreui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.inset
import com.plumemu.coreui.theme.HintKeyDimens
import com.plumemu.coreui.theme.PlumLayout
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.Layout
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.ButtonColors
import com.plumemu.coreui.theme.ColorMath
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.FlatFace
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
 * occurrence of [emphasis] ("18 systems") is drawn bold and bright, the rest small and `muted` ([PlaceText]). [clock] and
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
    /** The update pill before the clock ("Update 0.1.1"), null for none; the place text gives way to it, the clock never does. */
    pill: String? = null,
    /** A tap on the pill. */
    onPill: () -> Unit = {},
    /** True while a keyboard tray is up: the bar keeps [PlumLayout.topBarHeightTyping] and the place name is drawn in the top bar's size. */
    typing: () -> Boolean = { false },
    /** Search's query field and count: given, the place text keeps its own width (at least [placeMinWidth]) and this fills the room after it. */
    middle: (@Composable RowScope.() -> Unit)? = null,
    placeMinWidth: Dp = 0.dp,
) {
    val colors = PlumTheme.colors
    val compact = typing()
    val barHeight = if (compact) PlumTheme.layout.topBarHeightTyping else PlumTheme.layout.topBarHeight
    // The colour is read while drawing (`color` below): a moved theme colour redraws the bar and recomposes nothing.
    val style = PlumTheme.type.topBar
    // The clock and status read at the bar's own step (13), one above the games list's year and tag.
    val status = style
    val ink: () -> Color = { colors.text2 }
    Row(
        modifier = modifier.fillMaxWidth().height(barHeight).padding(horizontal = PlumDimens.stripMarginSide),
        horizontalArrangement = Arrangement.spacedBy(PlumDimens.topBarItemGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaceText(place, emphasis, placeAlpha, if (middle == null) Modifier.weight(1f) else Modifier.widthIn(min = placeMinWidth), compact)
        middle?.invoke(this)
        if (pill != null) TopBarPill(pill, style, barHeight, onPill)
        StatusText(clock, status, ink)
        WifiStatus(wifi, status, wifiDescription, ink)
        BatteryStatus(battery, charging, status, chargingDescription, ink)
    }
}

/** The accent pill of the top bar: one line, as tall as the bar, never cut (the place text beside it shrinks first). */
@Composable
private fun TopBarPill(
    text: String,
    style: TextStyle,
    height: Dp,
    onTap: () -> Unit,
) {
    val colors = PlumTheme.colors
    Box(
        Modifier
            .testTag(TopBarTags.PILL)
            .height(height)
            .background(colors.accent, CircleShape)
            .semantics {
                role = Role.Button
                onClick {
                    onTap()
                    true
                }
            }
            .pointerInput(onTap) { detectTapGestures(onTap = { onTap() }) }
            .padding(horizontal = PlumDimens.topBarPillPadding),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = text, style = style.copy(color = colors.onAccent), maxLines = 1)
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
 * There is no battery drawing: a low level (not charging) shows on the number, in the danger colour.
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
    val colors = PlumTheme.colors
    val low = !charging() && isLowBattery(value)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (charging()) {
            ChargingGlyph(style, color, chargingDescription, Modifier.testTag(TopBarTags.CHARGING))
            Spacer(Modifier.width(PlumDimens.topBarChargingGap))
        }
        BasicText(text = value, style = style, color = if (low) ({ readableDanger(colors.danger, colors.bg, colors.strong) }) else color, maxLines = 1, modifier = Modifier.testTag(TopBarTags.BATTERY_TEXT))
    }
}

/** Test tag of a hint's dot ([Hint] with `hasDot`). */
const val HINT_DOT_TAG = "hint_dot"

/** Test tag of a hint's key cap (the round pill with "A", "START", "L1 R1"). */
const val HINT_KEY_CAP_TAG = "hint_key_cap"

/** Test tag of the top bar's Wi-Fi glyph. */
object TopBarTags {
    const val WIFI = "top_bar_wifi"

    /** The update pill before the clock. */
    const val PILL = "top_bar_pill"

    /** The charging bolt before the battery percentage. */
    const val CHARGING = "top_bar_charging"

    /** The battery percentage. */
    const val BATTERY_TEXT = "top_bar_battery_text"
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
        horizontalArrangement = HintGapArrangement,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** The gap between [count] hints of [sum] px in [room] px: the board's, shrunk toward the tight gap only as far as it takes to fit. */
internal fun hintGapFor(
    sum: Int,
    count: Int,
    room: Int,
    board: Int,
    tight: Int,
): Int = if (count < 2) board else ((room - sum) / (count - 1)).coerceIn(tight, board)

/** Hints from the start, [hintGapFor] apart. */
private object HintGapArrangement : Arrangement.Horizontal {
    override fun Density.arrange(
        totalSize: Int,
        sizes: IntArray,
        layoutDirection: LayoutDirection,
        outPositions: IntArray,
    ) {
        val gap = hintGapFor(sizes.sum(), sizes.size, totalSize, PlumDimens.hintGapBoard.roundToPx(), PlumDimens.hintGap.roundToPx())
        var x = 0
        val rtl = layoutDirection == LayoutDirection.Rtl
        sizes.forEachIndexed { index, size ->
            outPositions[index] = if (rtl) totalSize - x - size else x
            x += size + gap
        }
    }
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
        val boardGap = PlumDimens.hintGapBoard.roundToPx()
        val placeables = measurables.map { it.measure(Constraints(maxHeight = constraints.maxHeight)) }
        val shown = BooleanArray(placeables.size) { true }
        fun width(): Int = placeables.indices.filter { shown[it] }.let { kept -> kept.sumOf { placeables[it].width } + gap * (kept.size - 1).coerceAtLeast(0) }
        val order = dropOrder.filter { it in placeables.indices } + placeables.indices.reversed()
        for (index in order) {
            if (width() <= constraints.maxWidth) break
            shown[index] = false
        }
        val height = constraints.maxHeight
        val kept = placeables.indices.filter { shown[it] }
        val spread = hintGapFor(kept.sumOf { placeables[it].width }, kept.size, constraints.maxWidth, boardGap, gap)
        layout(constraints.maxWidth, height) {
            var x = 0
            placeables.forEachIndexed { index, placeable ->
                if (!shown[index]) return@forEachIndexed
                placeable.placeRelative(x, (height - placeable.height) / 2)
                x += placeable.width + spread
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
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val boardLook = remember(title, meta, colors, type, density) { InfoLook(type.infoLineTitle.copy(color = colors.text), title, meta, colors.infoMeta, type.infoLineMeta, measurer, density) }
    val fallbackLook = remember(title, meta, colors, type, density) { InfoLook(type.gridFocusTitle.copy(color = colors.text), title, meta, colors.infoMeta, type.smallFact, measurer, density) }
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
        val widths = hintPlaceables.map { it.width }
        fun plan(look: InfoLook) = planInfoLine(look, meta.isNotEmpty(), widths, gap, constraints.maxWidth, dropOrder, dropBeforeMeta, measurer)
        // One rule: the title takes the board's size, or steps down so that no hint key is dropped. A key named in dropBeforeMeta
        // gives way to the meta by design, so losing only those does not step the title down unless the step would keep them all.
        val board = plan(boardLook)
        val chosen = if (board.lossless) board else plan(fallbackLook).let { if (it.lossless || !board.acceptable) it else board }
        val shown = chosen.shown
        fun hintsWidth(): Int = hintPlaceables.indices.filter { shown[it] }.let { kept -> kept.sumOf { hintPlaceables[it].width } + gap * kept.size }
        val titleWidth = (constraints.maxWidth - hintsWidth()).coerceAtLeast(0)
        val titlePlaceable =
            subcompose(TITLE_SLOT) {
                BasicText(text = chosen.text, style = chosen.style, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }.single().measure(Constraints(maxWidth = titleWidth)) // its line may be taller than the bar (Serif 23); the bar keeps its height
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

/** The title's style with its text parts: [meta] after it, in [muted] at [metaStyle]. */
private class InfoLook(
    val style: TextStyle,
    val title: String,
    val meta: String,
    val muted: Color,
    val metaStyle: TextStyle,
    measurer: TextMeasurer,
    density: Density,
) {
    val withMeta: AnnotatedString = focusTitle(title, meta, muted, metaStyle, gapLetterSpacing(style, measurer, density))
    val titleOnly: AnnotatedString = AnnotatedString(title)
}

/** What a [InfoLook] shows on one bar: the [text], which hints stay, and whether every hint stayed. */
private class InfoPlan(val style: TextStyle, val text: AnnotatedString, val shown: BooleanArray, val lossless: Boolean, val acceptable: Boolean)

/** Which hints and which text stay at [look] in [maxWidth]; see [TitledHintBar] for the order things leave in. */
private fun planInfoLine(
    look: InfoLook,
    hasMeta: Boolean,
    hintWidths: List<Int>,
    gap: Int,
    maxWidth: Int,
    dropOrder: List<Int>,
    dropBeforeMeta: List<Int>,
    measurer: TextMeasurer,
): InfoPlan {
    val shown = BooleanArray(hintWidths.size) { true }
    fun hintsWidth(): Int = hintWidths.indices.filter { shown[it] }.let { kept -> kept.sumOf { hintWidths[it] } + gap * kept.size }
    fun fits(text: AnnotatedString): Boolean = measurer.measure(text, look.style, maxLines = 1).size.width + hintsWidth() <= maxWidth
    if (hasMeta && !fits(look.withMeta)) {
        // The meta matters more than these hints: leave them out when that is what lets the meta stay.
        val before = dropBeforeMeta.filter { it in hintWidths.indices && it != 0 }
        before.forEach { shown[it] = false }
        if (!fits(look.withMeta)) before.forEach { shown[it] = true }
    }
    val withMeta = hasMeta && fits(look.withMeta)
    if (!withMeta) {
        // Keep the first hint (A) whatever happens: the bar always says what A does.
        for (index in dropOrder.filter { it in hintWidths.indices && it != 0 }) {
            if (fits(look.titleOnly)) break
            shown[index] = false
        }
    }
    val text = if (withMeta) look.withMeta else look.titleOnly
    return InfoPlan(look.style, text, shown, shown.all { it }, shown.indices.all { shown[it] || it in dropBeforeMeta })
}

private const val HINTS_SLOT = "hints"
private const val TITLE_SLOT = "title"

/** Extra spacing that makes one space in [style] as wide as the board's gap between the title and the text after it. */
private fun gapLetterSpacing(
    style: TextStyle,
    measurer: TextMeasurer,
    density: Density,
): TextUnit =
    with(density) {
        val space = measurer.measure(" ", style, maxLines = 1).size.width
        (PlumDimens.infoLineGap.toPx() - space).coerceAtLeast(0f).toSp()
    }

/** "Mega Man X  1993 · Capcom": the title in `text`, a gap, then the meta in [muted] at [small] (no dot between title and meta, as the board). */
private fun focusTitle(
    title: String,
    meta: String,
    muted: Color,
    small: TextStyle,
    gap: TextUnit,
): AnnotatedString =
    buildAnnotatedString {
        append(title)
        if (meta.isNotEmpty()) {
            withStyle(SpanStyle(letterSpacing = gap)) { append(" ") }
            withStyle(SpanStyle(color = muted, fontSize = small.fontSize, fontWeight = small.fontWeight)) { append(meta) }
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
    /** The confirm key wears the accent (board: Hint keys); a ready-made button set's own colour still wins. */
    accent: Boolean = false,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val buttons = PlumTheme.buttons
    // Without a ready-made button set the cap's colours are theme colours, read while drawing (a colour slider's
    // preview moves them without recomposing the hint); with one, the look is read here, as that set seldom changes.
    val setLook = if (buttons?.forKeyCap(keyCap) != null) keyCapLook(keyCap, buttons, colors) else null
    val press = rememberHintPress(keyCap)
    Row(
        modifier = modifier.height(PlumTheme.layout.hintBarHeight).trackTouch(press),
        horizontalArrangement = Arrangement.spacedBy(PlumDimens.hintKeyGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    // The key's collar (CollarSize.Hint) is drawn outside the cap's bounds, so the bar keeps its height:
                    // sideways it has room in the layout, up and down it reaches past the bar like the focus ring.
                    .padding(horizontal = HintKeyDimens.collarSide)
                    .drawBehind {
                        val l = CollarSize.Hint.lip.toPx() + CollarSize.Hint.groove.toPx()
                        val b = CollarSize.Hint.lipBottom.toPx() + CollarSize.Hint.grooveBottom.toPx()
                        inset(-l, -l, -l, -b) {
                            drawCollar(colors.collar, CollarSize.Hint, cornerRadius = size.height.minus(l + b) / 2, glow = colors.partGlow)
                        }
                    }
                    .testTag(HINT_KEY_CAP_TAG) // outside the layer: its bounds stay the resting cap's
                    // Pressed (design/rules.md › Shapes and motion › Pressed): only the cap dips, and
                    // `press.progress` is read in the layer and draw blocks, never in composition.
                    .graphicsLayer { translationY = colors.collar.seat.hintCap.travel.toPx() * press.progress.value }
                    .height(PlumTheme.layout.keyCapHeight)
                    .defaultMinSize(minWidth = PlumDimens.keyCapMinWidth)
                    // The board's hint CAP (ACCENT CAP for confirm or a button set's colour). Pressed: 1.33 dp down in the capDown face, the letter kept.
                    .drawBehind {
                        val seat = colors.collar.seat
                        val fill = setLook?.fill
                        val cap = if (fill != null) seat.hintAccent(fill) else if (accent) seat.hintAccent(colors.accent) else seat.hintCap
                        drawCssCap(cap, press = press.progress.value, moveFace = false)
                    }
                    // One glyph (A, B) keeps the board's 22.67 round key; only a word (START, L1 R1) takes the 8.67 padding.
                    .padding(horizontal = if (keyCap.length > 1) HintKeyDimens.capPadding else 0.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = keyCap,
                style = type.keyCap,
                maxLines = 1,
                color = { setLook?.letter ?: if (accent) colors.onAccentFace else colors.text },
            )
        }
        BasicText(text = label, style = type.hintLabel, color = { colors.hintLabel }, maxLines = 1)
        if (hasDot) Box(Modifier.size(PlumDimens.hintDot).drawBehind { drawCircle(colors.accent) }.testTag(HINT_DOT_TAG))
    }
}

/** How a key cap in a button set is drawn: its flat [fill] (the printed letter's colour) and its [letter]. Caps without a set use the theme's `key` face and `text`. */
data class KeyCapLook(
    val fill: Color?,
    val letter: Color,
)

/**
 * [keyCap]'s look. While [buttons] are in use (the set the person last picked) an A, B, X or Y cap's flat face is its
 * printed letter's colour and its letter white or dark as [com.plumemu.coreui.theme.ButtonColor] says. Every other cap,
 * and every cap without [buttons], has no fill of its own: the theme's `key` face with `text`. A fill close to the
 * background (a yellow B on Banana) still stands off by its edge and shadow.
 */
fun keyCapLook(
    keyCap: String,
    buttons: ButtonColors?,
    colors: PlumComposeColors,
): KeyCapLook {
    val glyph = buttons?.forKeyCap(keyCap) ?: return KeyCapLook(fill = null, letter = colors.text)
    return KeyCapLook(fill = glyph.fill.toComposeColor(), letter = glyph.letter.toComposeColor())
}
