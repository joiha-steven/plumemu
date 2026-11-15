package com.plumemu.coreui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.theme.LeftColumnDimens
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme

/** How a system row looks. */
enum class SystemRowLook {
    /** The cursor on the system column: `sel` fill, accent dot, `strong` text 500, count `meta`. */
    Focused,

    /** Another entry: name `text2`, count `muted`. */
    Normal,

    /** The current system while games have focus: accent text 600, no fill, count accent. */
    Current,
}

/**
 * An entry of the system column, 34 high (the board's height): name with an ellipsis, [count]
 * at the right. With [showName] false (the 48 dp peek) the name keeps its place but is not
 * drawn, so only counts show and a long name never pokes in (docs/design/rules.md › The strip).
 */
@Composable
fun SystemRow(
    name: String,
    count: String,
    look: SystemRowLook,
    showName: Boolean,
    modifier: Modifier = Modifier,
    bleedStart: Dp,
    hasDot: Boolean = false,
    /** False: no count and none of its room (Settings' sections in three columns, whose labels need it); [endPadding] 0 there too. */
    showCount: Boolean = true,
    endPadding: Dp = PlumDimens.systemRowPadding,
    barInset: Dp = LeftColumnDimens.systemBarInset,
) = SystemRow(name, count, look, nameAlpha = if (showName) NameShown else NameHidden, modifier = modifier, bleedStart = bleedStart, hasDot = hasDot, showCount = showCount, endPadding = endPadding, barInset = barInset)

/**
 * The same row with the name drawn at [nameAlpha], read while drawing: the main screen fades the
 * names with the strip's position as it slides to the peek, without recomposing a row per frame.
 *
 * The row is [bleedStart] wider on the left (Home and Settings pass the page margin): the label
 * starts at [bleedStart] whether focused or not, the focus dot sits centred in that space, and the
 * selector fills the whole row with square left corners, as if it ran on under the screen edge.
 */
@Composable
fun SystemRow(
    name: String,
    count: String,
    look: SystemRowLook,
    nameAlpha: () -> Float,
    modifier: Modifier = Modifier,
    bleedStart: Dp,
    hasDot: Boolean = false,
    /** True: the count fades with the names (the letter rail takes its place in the peek). False: it stays, as counts and system codes do. */
    fadeCount: Boolean = false,
    showCount: Boolean = true,
    endPadding: Dp = PlumDimens.systemRowPadding,
    /** Read while drawing: true leaves the count undrawn (a card over the page would cut it); its room stays. */
    countHidden: () -> Boolean = NeverHidden,
    /** The accent bar's distance from the slab's left edge (the slab itself starts at [LeftColumnDimens.systemPillStart]). */
    barInset: Dp = LeftColumnDimens.systemBarInset,
) {
    BleedingSystemRow(name, count, look, nameAlpha, bleedStart, modifier, hasDot, fadeCount, showCount, endPadding, countHidden, barInset)
}

/** Label at [bleedStart]; the focus pill starts at [LeftColumnDimens.systemPillStart] and its accent bar [barInset] in (Home and Settings). */
@Composable
private fun BleedingSystemRow(
    name: String,
    count: String,
    look: SystemRowLook,
    nameAlpha: () -> Float,
    bleedStart: Dp,
    modifier: Modifier,
    hasDot: Boolean,
    fadeCount: Boolean,
    showCount: Boolean,
    endPadding: Dp,
    countHidden: () -> Boolean,
    barInset: Dp,
) {
    val colors = PlumTheme.colors
    val focused = look == SystemRowLook.Focused
    // The pill starts [systemPillStart] from the screen edge and the label never moves with focus. It is drawn by focusPill so it dips with the row;
    // the empty PILL box only keeps the tag for the layout test.
    val inset = LeftColumnDimens.systemPillStart
    // The press (dip and finger) is on the whole row, the touch target; only the drawn pill starts at [inset].
    // As a game row: the tap area is the stride (row + gap) and the drawn row, pill and content, is the game row's height.
    Box(modifier = modifier.fillMaxWidth().height(PlumTheme.layout.systemRowHeight)) {
        Box(modifier = Modifier.fillMaxWidth().height(PlumTheme.layout.gameRowHeight).focusPill(focused, shape = remember(inset, barInset) { InsetFocusPillShape(inset, barInset) })) {
            if (focused) Box(Modifier.fillMaxSize().padding(start = inset).testTag(SystemRowTags.PILL))
            // The row is as tall as a game row plus its gap: the content takes the game row's height and shares the title's baseline
            // (name and count on one LastBaseline, as the title and year in GameRow), so a count sits on the baseline of the game beside it.
            Row(
                modifier = Modifier.fillMaxWidth().height(PlumTheme.layout.gameRowHeight).padding(start = bleedStart, end = endPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.weight(1f).fadedBy(nameAlpha).alignBy(LastBaseline), verticalAlignment = Alignment.CenterVertically) {
                        SystemName(name, look)
                    }
                    if (hasDot) {
                        // Something new here (games added by a change check): the hint bar's accent dot, gone with the name in the peek.
                        Spacer(Modifier.width(PlumDimens.hintKeyGap))
                        Box(Modifier.alignBy { it.measuredHeight }.fadedBy(nameAlpha).size(PlumDimens.hintDot).drawBehind { drawCircle(colors.accent) }.testTag(SystemRowTags.NEW_DOT))
                    }
                    // Faded with the names only while the letter rail takes the peek (Settings › Home screen › Left edge).
                    if (showCount) SystemCount(count, look, nameAlpha, fadeCount, countHidden, Modifier.alignBy(LastBaseline))
                }
            }
        }
    }
}

@Composable
private fun SystemName(
    name: String,
    look: SystemRowLook,
) {
    val type = PlumTheme.type
    val colors = PlumTheme.colors
    val style =
        when (look) {
            SystemRowLook.Focused -> type.systemNameFocused
            SystemRowLook.Normal -> type.systemName
            SystemRowLook.Current -> type.systemNameCurrent
        }
    // The colour is read while drawing, so a moved theme colour redraws the name without recomposing it.
    val color: () -> Color =
        when (look) {
            SystemRowLook.Focused -> { { colors.strong } }
            SystemRowLook.Normal -> { { colors.text2 } }
            SystemRowLook.Current -> { { colors.accent } }
        }
    EllipsisText(text = name, style = style, color = color, maxLines = 1, modifier = Modifier.testTag(SystemRowTags.LABEL))
}

@Composable
private fun SystemCount(
    count: String,
    look: SystemRowLook,
    nameAlpha: () -> Float,
    fade: Boolean,
    hidden: () -> Boolean,
    modifier: Modifier,
) {
    val colors = PlumTheme.colors
    val color: () -> Color =
        when (look) {
            SystemRowLook.Focused -> { { colors.meta } }
            SystemRowLook.Normal -> { { colors.muted.copy(alpha = colors.muted.alpha * LeftColumnDimens.RESTING_COUNT_ALPHA) } }
            SystemRowLook.Current -> { { colors.accent } }
        }
    Spacer(Modifier.width(PlumDimens.rowTrailingGap))
    BasicText(text = count, modifier = modifier.then(if (fade) Modifier.fadedBy(nameAlpha) else Modifier).hiddenWhile(hidden).testTag(SystemRowTags.COUNT), style = PlumTheme.type.systemRowCount, color = color, maxLines = 1)
}

/** Draws at [alpha], read while drawing; no offscreen buffer per row while the names fade (dot and name do not overlap). */
private fun Modifier.fadedBy(alpha: () -> Float): Modifier =
    plainLayer {
        this.alpha = alpha()
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }

/** Test tags inside a [SystemRow], for the layout test (read from the unmerged tree). */
object SystemRowTags {
    const val PILL = "system_row_pill"
    const val LABEL = "system_row_label"

    /** The count (or system code) at the row's end; faded out with the names only when the letter rail covers its place. Baseline tests find the column's text by this tag, whatever it shows. */
    const val COUNT = "system_row_count"

    /** The dot of a system with new games ([SystemRow] `hasDot`). */
    const val NEW_DOT = "system_row_new_dot"
}

private val NameShown: () -> Float = { 1f }
private val NameHidden: () -> Float = { 0f }

