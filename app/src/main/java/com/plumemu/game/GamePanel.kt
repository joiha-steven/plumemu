package com.plumemu.game

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column as StripColumn
import com.plumemu.coreinput.tapItem
import com.plumemu.coreui.components.EdgeFade
import com.plumemu.coreui.components.ListEdgeFade
import com.plumemu.coreui.components.OptionRow
import com.plumemu.coreui.components.OptionRowLook
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.WidthClass
import com.plumemu.coreui.theme.fadingIn
import com.plumemu.coreui.theme.rememberContentFade
import com.plumemu.coreui.theme.rememberListGlide
import com.plumemu.home.KeepFocusedVisible

/**
 * The game panel over the strip (built from the Settings side panel's parts,
 * design/rules.md › Settings): on wide screens a 248 column at the right, where the details column
 * would be, the list fading out before it; on narrow screens a further stop of the options width
 * (`width − 48`) over the whole strip. It slides in and out with the strip's 180 ms ease-out
 * (`graphicsLayer` only), and jumps when motion is off. While open, a tap outside it is Back.
 * [keyboardCovers]: how much of its bottom Plumemu's keyboard tray covers while a field types
 * with it ([PanelKeyboardTray]); the panel then ends at the tray's top, the field whole and what
 * is under it scrolling in the space left ([TypingPanelLayout]).
 */
@Composable
internal fun GamePanelOverlay(
    panel: OpenPanel?,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
    keyboardCovers: Dp = 0.dp,
) {
    val layout = PlumTheme.layout
    val isWide = layout.widthClass == WidthClass.Wide
    val panelWidth = if (isWide) PlumDimens.settingsPanelWidth else layout.settingsOptionsWidth
    val isOpen = panel != null
    // Kept while the panel slides out, so it leaves with its content. Not state: the overlay
    // recomposes whenever [panel] changes anyway.
    val lastShown = remember { arrayOfNulls<OpenPanel>(1) }
    if (panel != null) lastShown[0] = panel
    // Already open on the first frame (a screenshot, a restored screen): no slide.
    val progress = remember { Animatable(if (isOpen) 1f else 0f) }
    val spec = PlumTheme.motion.slideSpec<Float>()
    LaunchedEffect(isOpen) { progress.animateTo(if (isOpen) 1f else 0f, spec) }
    val isGone by remember { derivedStateOf { progress.value == 0f } }
    val shown = lastShown[0]
    if (shown == null || (!isOpen && isGone)) return
    val slide = with(LocalDensity.current) { (if (isWide) PlumDimens.columnGap + panelWidth + PlumDimens.stripMarginSide else layout.width).toPx() }
    Box(modifier.fillMaxSize()) {
        if (isOpen) {
            Box(Modifier.fillMaxSize().pointerInput(onAction) { detectTapGestures(onTap = { onAction(Action.Back) }) }.testTag(GamePanelTags.OUTSIDE))
        }
        Row(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .graphicsLayer { translationX = (1f - progress.value) * slide },
        ) {
            if (isWide) EdgeFade()
            Box(
                Modifier
                    .fillMaxHeight()
                    // Over a keyboard tray, a field panel's header may give way from its top: never over the top bar.
                    .then(if (shown.screen is TypingScreen) Modifier.clipToBounds() else Modifier)
                    .background(PlumTheme.colors.bg)
                    // Taps on the panel's empty parts stay in the panel (they are not "outside").
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(start = if (isWide) PlumDimens.columnGap else PlumDimens.stripMarginSide, end = PlumDimens.stripMarginSide),
            ) {
                // Sliding out, the rows are inert: a tap there would otherwise fall through to the
                // strip as a Details tap (narrow details stop: Play).
                PanelBody(shown, panelWidth, if (isOpen) onAction else IGNORE_ACTIONS, keyboardCovers)
            }
        }
    }
}

@Composable
private fun PanelBody(
    panel: OpenPanel,
    width: Dp,
    onAction: (Action) -> Unit,
    keyboardCovers: Dp,
) {
    val content = panel.content
    // A sub-screen (Play with, Info, Choose an emulator, Fix name) fades in over the one before.
    val fade = rememberContentFade(panel.screen::class)
    val screen = panel.screen
    val modifier = Modifier.width(width).fillMaxHeight().testTag(GamePanelTags.PANEL).fadingIn(fade)
    if (screen is TypingScreen) {
        TypingPanelLayout(screen.typing, keyboardCovers, modifier, header = { PanelHeader(content) }) {
            key(screen) { TypingPanelBody(screen, onAction) }
        }
        return
    }
    Column(modifier) {
        PanelHeader(content)
        if (screen is PanelScreen.Info) {
            // In the height Back leaves (a weighted child is measured last): Back stays on screen, the facts scroll.
            InfoFacts(screen.facts, screen.scroll, Modifier.weight(1f, fill = false))
            Spacer(Modifier.height(PlumDimens.panelBlockGap))
        }
        // A new list per screen: Play with opens at its top, not at the menu's scroll.
        key(screen) { PanelRows(panel, content, onAction) }
    }
}

/** The panel's title, its body lines and the gap after them. */
@Composable
private fun PanelHeader(content: PanelContent) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    BasicText(
        text = content.title.resolve(),
        style = type.systemNameCurrent.copy(color = colors.text),
        maxLines = TITLE_LINES,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = PlumDimens.gameRowPadding).testTag(GamePanelTags.TITLE),
    )
    content.body.forEach { line ->
        Spacer(Modifier.height(PlumDimens.titleLineGap))
        BasicText(
            text = line.resolve(),
            style = type.smallFact.copy(color = colors.muted),
            maxLines = BODY_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = PlumDimens.gameRowPadding),
        )
    }
    Spacer(Modifier.height(PlumDimens.panelBlockGap))
}

@Composable
private fun PanelRows(
    panel: OpenPanel,
    content: PanelContent,
    onAction: (Action) -> Unit,
) {
    val listState = rememberLazyListState()
    val glide = rememberListGlide(stride = PlumTheme.layout.rowHeight + PlumDimens.listRowGap)
    val focusedRow = content.focusableRows.getOrNull(panel.focus)
    val pill = rememberPanelPill(listState, glide, content.items.indexOfFirst { (it as? PanelItem.Row)?.row == focusedRow && focusedRow != null })
    Box {
    pill.Provide {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().testTag(GamePanelTags.ROWS).then(pill.modifier).then(glide.modifier),
        contentPadding = PaddingValues(vertical = glide.extra),
        verticalArrangement = Arrangement.spacedBy(PlumDimens.listRowGap),
    ) {
        itemsIndexed(content.items, key = { index, item -> (item as? PanelItem.Row)?.row?.key ?: "heading_$index" }) { _, item ->
            when (item) {
                is PanelItem.Heading -> PanelHeading(item.text.resolve())
                is PanelItem.Row -> {
                    val focusIndex = content.focusableRows.indexOf(item.row)
                    val look =
                        when {
                            !item.row.isEnabled -> OptionRowLook.Disabled
                            item.row == focusedRow -> OptionRowLook.Focused
                            else -> OptionRowLook.Normal
                        }
                    PanelRowView(item.row, look, focusIndex, onAction)
                }
            }
        }
    }
    }
    // A list that does not fit shows it scrolls: the row cut at an edge fades out (design/rules.md > Lists that scroll).
    if (listState.canScrollForward) ListEdgeFade(atBottom = true, Modifier.align(Alignment.BottomCenter).testTag(GamePanelTags.FADE_BOTTOM))
    if (listState.canScrollBackward) ListEdgeFade(atBottom = false, Modifier.align(Alignment.TopCenter).testTag(GamePanelTags.FADE_TOP))
    }
    val current by rememberUpdatedState(content.items.indexOfFirst { (it as? PanelItem.Row)?.row == focusedRow }.coerceAtLeast(0))
    KeepFocusedVisible(listState, glide) { current }
}

@Composable
internal fun PanelRowView(
    row: PanelRow,
    look: OptionRowLook,
    focusIndex: Int,
    onAction: (Action) -> Unit,
    /** Drawn above the row inside the same lazy item (a store's status line), so the row's place in the item is its own. */
    lead: (@Composable () -> Unit)? = null,
) {
    val tap =
        remember(focusIndex, onAction) {
            if (focusIndex >= 0) Modifier.tapItem(StripColumn.Details, focusIndex, onAction) else Modifier
        }
    Column {
        lead?.invoke()
        OptionRow(
            label = row.label.resolve(),
            look = look,
            value = row.value?.resolve().orEmpty(),
            // The badge sits on a second line of the same row: beside the name it cut "RetroArch (Beetle PSX)" at 768.
            badge = if (row.isCurrent) stringResource(R.string.game_current) else null,
            isBadgeBelow = true,
            modifier = tap.testTag(GamePanelTags.row(row.key)),
        )
        row.note?.let { note ->
            BasicText(
                text = note.resolve(),
                style = PlumTheme.type.smallFact.copy(color = PlumTheme.colors.muted),
                maxLines = NOTE_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = PlumDimens.gameRowPadding, vertical = PlumDimens.factsRowGap),
            )
        }
    }
}

/** "Not installed": divides a list, never focused. */
@Composable
private fun PanelHeading(text: String) {
    BasicText(
        text = text,
        style = PlumTheme.type.smallFact.copy(color = PlumTheme.colors.muted),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = PlumDimens.gameRowPadding, end = PlumDimens.gameRowPadding, top = PlumDimens.panelBlockGap, bottom = PlumDimens.factsRowGap),
    )
}

/** Test tags of the game panel. */
internal object GamePanelTags {
    const val PANEL = "game_panel"
    const val TITLE = "game_panel_title"
    const val ROWS = "game_panel_rows"
    const val FACTS = "game_panel_facts"

    /** Info's facts and "Name from", which scroll when the panel is too short for them. */
    const val FACTS_SCROLL = "game_panel_facts_scroll"
    /** The fades over the rows' and the facts' edges, present only while the list continues past that edge. */
    const val FADE_BOTTOM = "game_panel_fade_bottom"
    const val FADE_TOP = "game_panel_fade_top"
    const val OUTSIDE = "game_panel_outside"
    const val NAME_FROM = "game_panel_name_from"

    /** Info's description (ScreenScraper's synopsis) and its blocks (0 the label). */
    const val DESCRIPTION = "game_panel_description"

    fun row(key: String) = "game_panel_row_$key"

    /** Info's fact row [index] (System first). */
    fun fact(index: Int) = "game_panel_fact_$index"

    /** Line [index] of Info's "Name from" (0 its label). */
    fun nameFromLine(index: Int) = "game_panel_name_from_$index"

    fun descriptionBlock(index: Int) = "game_panel_description_$index"
}

private val IGNORE_ACTIONS: (Action) -> Unit = {}

private const val TITLE_LINES = 3
private const val BODY_LINES = 6
// 4 since the type ramp puts notes at 13: Play with's download note takes 4 lines at Larger on 837.
private const val NOTE_LINES = 4
