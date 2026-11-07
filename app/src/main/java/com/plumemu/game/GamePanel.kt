package com.plumemu.game

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column as StripColumn
import com.plumemu.coreinput.tapItem
import androidx.compose.ui.layout.onGloballyPositioned
import com.plumemu.coreui.components.LocalRowTextMark
import com.plumemu.coreui.components.ButtonKind
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.components.LocalSlabOnCard
import com.plumemu.coreui.components.OptionRow
import com.plumemu.coreui.components.OptionRowLook
import com.plumemu.coreui.components.PlumButton
import com.plumemu.coreui.components.raisedCard
import com.plumemu.coreui.components.slabOverhang
import com.plumemu.coreui.theme.CardDimens
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumTypography
import com.plumemu.coreui.theme.SlabDimens
import com.plumemu.coreui.theme.TextSize
import com.plumemu.coreui.theme.WidthClass
import com.plumemu.coreui.theme.fadingIn
import com.plumemu.coreui.theme.rememberContentFade
import com.plumemu.coreui.theme.rememberListGlide
import com.plumemu.home.BarRunClips
import com.plumemu.home.KeepFocusedVisible
import com.plumemu.settings.screen.ChevronSide
import com.plumemu.settings.screen.ValueChevron

/**
 * The game panel: a raised card on the page's right margin, between the top bar's gap and the hint
 * bar's gap, drawn over the page with no scrim or fade (the page keeps its own look up to the
 * card's shadow). Width: [CardDimens.menuWidth] (or [CardDimens.pickerWidth] for the emulator
 * lists) on wide screens, `width − 48` on narrow ones. Everything inside is `panelInset` from the
 * card's edge on both sides. It slides in and out with the strip's 180 ms ease-out
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
    val isOpen = panel != null
    WarmGamePanel(isWide, modifier)
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
    // This box is the area between the top bar's gap and the hint bar's gap: the card sits on the page's outer margin at its right.
    val cardWidth = if (isWide) panelCardWidth(shown.screen, layout.textScale, layout.width) else layout.width - PlumDimens.stripMarginSide * 2
    val slide = with(LocalDensity.current) { (cardWidth + PlumDimens.stripMarginSide).toPx() }
    // Nothing dims or fades the page behind the card: the list and the grid keep their own look right up to its shadow.
    Box(modifier.fillMaxSize()) {
        if (isOpen) {
            Box(Modifier.fillMaxSize().pointerInput(onAction) { detectTapGestures(onTap = { onAction(Action.Back) }) }.testTag(GamePanelTags.OUTSIDE))
        }
        // A question (buttons only) and a message hug their content from the card's top edge (a long one scrolls within the height); every other panel fills the height.
        val hugs = shown.hugsContent
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(end = PlumDimens.stripMarginSide)
                .graphicsLayer { translationX = (1f - progress.value) * slide }
                .width(cardWidth)
                .then(if (hugs) Modifier else Modifier.fillMaxHeight())
                .testTag(GamePanelTags.CARD)
                // The panel's raised card (the board's game menu card): gradient fill, hairline edge, soft shadows. Drawn before
                // the clip below so a typing panel does not cut it; it reads colours only while drawing.
                .pageBehindCard(footGap = if (hugs) 0.dp else PlumDimens.stripMarginBottom)
                .raisedCard()
                // Everything inside stays within the card's rounded shape: the focused slab's shadow and glow end at the card's edge, not on the page beside it.
                // (This also keeps a field panel's header, over a keyboard tray, from reaching the top bar.)
                .clip(RoundedCornerShape(CardDimens.corner))
                // Taps on the panel's empty parts stay in the panel (they are not "outside").
                .pointerInput(Unit) { detectTapGestures { } }
                // Above Plumemu's keyboard tray the extra top padding gives way: the field's line below it needs that height.
                .padding(start = PlumDimens.panelInset, end = PlumDimens.panelInset, top = if ((shown.screen as? TypingScreen)?.typing?.isTrayUp == true) 0.dp else CardDimens.padTop),
        ) {
            // Sliding out, the rows are inert: a tap there would otherwise fall through to the
            // strip as a Details tap (narrow details stop: Play).
            // The panel is a raised card: its focused row's slab is the lighter one made for a card.
            CompositionLocalProvider(LocalSlabOnCard provides true) { PanelBody(shown, if (isOpen) onAction else IGNORE_ACTIONS, keyboardCovers) }
        }
    }
}

/**
 * The page behind the card is not dimmed or covered, so what lies under its rounded corners would show as a sliver (the cover's corner at the
 * top right): the gap between the card's box and its rounded shape is filled with the page colour, drawn before the card's shadows. So is the
 * [footGap] under a card that fills the height, where home's rows run on towards the hint bar and only the tops of their text would show.
 */
@Composable
private fun Modifier.pageBehindCard(footGap: Dp): Modifier {
    val colors = PlumTheme.colors
    return drawBehind {
        val corner = CardDimens.corner.toPx()
        val shape = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(corner))) }
        clipPath(shape, ClipOp.Difference) { drawRect(colors.bg, size = Size(size.width, size.height + footGap.toPx())) }
    }
}

/**
 * The card's width: the board's menu card, wider for the lists that carry a "Current" badge beside a long emulator name
 * ([CardDimens.pickerWidth], grown by the text size's [scale] so a name keeps its line, up to [PICKER_MAX_SHARE] of the screen).
 */
private fun panelCardWidth(
    screen: PanelScreen,
    scale: Float,
    screenWidth: Dp,
): Dp {
    val picker = (CardDimens.pickerWidth * scale).coerceAtMost(maxOf(CardDimens.pickerWidth, screenWidth * PICKER_MAX_SHARE))
    return when (screen) {
        is PanelScreen.PlayWith, is PanelScreen.Versions -> picker
        // The "choose an emulator" question lists emulator names too; a plain message or a yes/no question keeps the menu's width.
        is PanelScreen.Message -> if (screen.content.isButtonRow || screen.content.focusableRows.size < 3) CardDimens.menuWidth else picker
        else -> CardDimens.menuWidth
    }
}

private const val PICKER_MAX_SHARE = 0.6f

@Composable
private fun PanelBody(
    panel: OpenPanel,
    onAction: (Action) -> Unit,
    keyboardCovers: Dp,
) {
    val content = panel.content
    // A sub-screen (Play with, Info, Choose an emulator, Fix name) fades in over the one before.
    val fade = rememberContentFade(panel.screen::class)
    val screen = panel.screen
    val modifier = Modifier.fillMaxWidth().then(if (panel.hugsContent) Modifier else Modifier.fillMaxHeight()).testTag(GamePanelTags.PANEL).fadingIn(fade)
    if (screen is TypingScreen) {
        TypingPanelLayout(screen.typing, keyboardCovers, modifier, header = { PanelHeader(content) }) {
            key(screen) { TypingPanelBody(screen, onAction) }
        }
        return
    }
    // A list under the title keeps room for a row whole: a long body gives way (cut, never the rows), or a large text left a message no window to reach its rows in.
    val reserve = with(LocalDensity.current) { (PlumTheme.layout.rowHeight + PlumDimens.listFadeBand).roundToPx() }
    val keepsRoom = screen !is PanelScreen.Info && !content.isButtonRow && content.items.isNotEmpty()
    Column(modifier) {
        if (keepsRoom) {
            Column(
                Modifier.layout { measurable, constraints ->
                    val room = if (constraints.hasBoundedHeight) (constraints.maxHeight - reserve).coerceAtLeast(0) else constraints.maxHeight
                    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = room))
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }.clipToBounds(),
            ) { PanelHeader(content) }
        } else {
            PanelHeader(content)
        }
        if (screen is PanelScreen.Info) {
            // In the height Back leaves (a weighted child is measured last): the facts take all of it, Back sits in the card's foot, and the facts scroll only when they do not fit.
            InfoFacts(screen.facts, screen.scroll, Modifier.weight(1f))
            Spacer(Modifier.height(PlumDimens.panelBlockGap))
        }
        // A new list per screen: Play with opens at its top, not at the menu's scroll.
        if (content.isButtonRow) {
            // Weighted, so it is measured after the buttons and the bottom padding: when the largest text leaves the card short of height, this gap gives way, not the padding.
            Spacer(Modifier.weight(1f, fill = false).height(CardDimens.questionButtonsGap))
            key(screen) { PanelButtons(panel, content, onAction) }
            Spacer(Modifier.height(CardDimens.padBottom))
        } else {
            key(screen) { PanelRows(panel, content, onAction) }
        }
    }
}

/** The panel's title, its body lines and the gap after them. */
@Composable
private fun PanelHeader(content: PanelContent) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    // No accent dot: it carried no state, only decoration. The core question's title is the board's 20 / 600 up to Large (above it the card has no room for it), a menu's the 16 / 600 panel title.
    BasicText(
        text = content.title.resolve(),
        style = questionTitleStyle(content, type).copy(color = colors.text),
        maxLines = TITLE_LINES,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = PlumDimens.panelTitleStart, end = PlumDimens.panelTitleStart).testTag(GamePanelTags.TITLE),
    )
    // The question's body is the board's dialog body: text2 on a 1.47 em line, 6.67 under the title. Above Large the card has no
    // room for the looser line (a three-line body would push the buttons into the bottom padding), so it keeps the ramp's line and gap.
    val question = content.isButtonRow
    val roomy = question && type.textSize <= TextSize.Large
    val bodyStyle = type.smallFact.let { if (roomy) it.copy(lineHeight = it.fontSize * CardDimens.QUESTION_BODY_LEADING) else it }
    content.body.forEach { line ->
        Spacer(Modifier.height(if (roomy) CardDimens.questionBodyGap else PlumDimens.titleLineGap))
        BasicText(
            text = line.resolve(),
            style = bodyStyle.copy(color = if (question) colors.text2 else colors.muted),
            maxLines = BODY_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = PlumDimens.panelTitleStart, end = PlumDimens.panelTitleStart),
        )
    }
    // A question's gap to its buttons is the weighted one in PanelBody, so it can give way to the card's bottom padding.
    if (!content.isButtonRow) Spacer(Modifier.height(PlumDimens.panelBlockGap))
}

/** The question's title above Large keeps the panel title's size on a tighter line, so a three-line body and the buttons still fit under it with the card's bottom padding. */
private fun questionTitleStyle(
    content: PanelContent,
    type: PlumTypography,
): TextStyle =
    when {
        !content.isButtonRow -> type.systemNameCurrent
        type.textSize <= TextSize.Large -> type.messageTitle.copy(fontWeight = FontWeight.SemiBold)
        else -> type.systemNameCurrent.copy(lineHeight = type.systemNameCurrent.fontSize * QUESTION_TITLE_LEADING)
    }

private const val QUESTION_TITLE_LEADING = 1.05f

/** The core question's No and Yes side by side at the right, the first row (Yes) rightmost; the focused one is lit. */
@Composable
private fun PanelButtons(
    panel: OpenPanel,
    content: PanelContent,
    onAction: (Action) -> Unit,
) {
    val focusedRow = content.focusableRows.getOrNull(panel.focus)
    Row(
        Modifier.fillMaxWidth().padding(end = PlumDimens.panelTitleStart - CollarSize.Control.padding.calculateEndPadding(LayoutDirection.Ltr)),
        horizontalArrangement = Arrangement.spacedBy(CardDimens.questionButtonSpacing, Alignment.End),
    ) {
        val rows = content.items.mapNotNull { (it as? PanelItem.Row)?.row }
        rows.asReversed().forEach { row ->
            val focusIndex = content.focusableRows.indexOf(row)
            PlumButton(
                label = row.label.resolve(),
                kind = if (row == rows.first()) ButtonKind.Primary else ButtonKind.Secondary,
                focused = row == focusedRow,
                modifier = Modifier.tapItem(StripColumn.Details, focusIndex, onAction).testTag(GamePanelTags.row(row.key)),
            )
        }
    }
}


@Composable
private fun PanelRows(
    panel: OpenPanel,
    content: PanelContent,
    onAction: (Action) -> Unit,
) {
    val listState = rememberLazyListState()
    // A list whose rows all fit has nothing cut at its ends, so its first and last row's glow keeps its room; one that scrolls keeps the slab's overhang
    // only (its fades and the card's edge are laid out for that), and its focused row's shadow is still whole: the pill draws it outside the list's clip.
    // Info's one row (Back) sits under facts that scroll: its list is laid out as it is.
    val fits = !listState.canScrollBackward && !listState.canScrollForward && panel.screen !is PanelScreen.Info
    val glide = rememberListGlide(stride = PlumTheme.layout.rowHeight + PlumDimens.panelRowGap, glowRoom = fits, shadowPastEnds = true)
    val density = LocalDensity.current
    val slabOverhangDp = with(density) { slabOverhang(PlumTheme.layout.rowHeight.toPx(), density).toDp() }
    val focusedRow = content.focusableRows.getOrNull(panel.focus)
    val current by rememberUpdatedState(content.items.indexOfFirst { (it as? PanelItem.Row)?.row == focusedRow }.coerceAtLeast(0))
    // Rows fade into the card's edge and under the title with the screen's recipe (design/rules.md › Lists that scroll).
    val items = rememberUpdatedState(content.items)
    val pill = rememberPanelPill(listState, glide, content.items.indexOfFirst { (it as? PanelItem.Row)?.row == focusedRow && focusedRow != null })
    val focusedKey by rememberUpdatedState(focusedRow?.key)
    // Room under the last row, so that at the end of the list the row cut at the top never shows a sliver of its text (see [sliverAtTopPx]).
    var slackPx by remember(listState) { mutableIntStateOf(0) }
    val lead = with(LocalDensity.current) { CardDimens.listEdgeLead.toPx() }
    val bands = remember { HashMap<Any, RowTextBands>() }
    val clips =
        remember(listState, glide) {
            BarRunClips(
                listState,
                glide,
                topRoom = 0f,
                ramps = true,
                isHeading = { items.value.getOrNull(it) is PanelItem.Heading },
                hiddenAtEnd = { height -> listState.hiddenAtEndPx(height, lead, focusedKey, bands) },
            ) { current }
        }
    LaunchedEffect(listState) {
        snapshotFlow { bands.values.sumOf { it.seen } to listState.sliverAtTopPx(lead, bands) }.collect { (_, it) ->
            if (it > 0) {
                slackPx += it
                // The room takes effect with the next layout; then the rows move up by it.
                withFrameNanos { }
                listState.scrollBy(it.toFloat())
            }
        }
    }
    // The list's own clip reaches past its bounds by the slab's overhang, which would show the row after the last whole one below the card's edge:
    // nothing is drawn below the card (sideways and above, the slab and the fade keep their room). A list that ends inside the card (a short one, a
    // notice's buttons) has no row after the last: its last row's shadow and glow may reach their whole room, not be cut flat at the list's end.
    Box(Modifier.drawWithContent {
        val below = if (listState.canScrollForward) 0f else SlabDimens.listEndRoom.toPx()
        clipRect(left = -size.width, top = -size.height, right = 2f * size.width, bottom = size.height + below) { this@drawWithContent.drawContent() }
    }.ignoringHiddenEnd { height -> listState.hiddenAtEndPx(height, lead, focusedKey, bands) }) {
    pill.Provide {
    LazyColumn(
        state = listState,
        // The board's row column ends 15.33 dp inside the card (its slab, 412 px in a 440 px card, 23 px from the edge), not at the card's padding.
        modifier = Modifier.fillMaxWidth().testTag(GamePanelTags.ROWS).then(pill.modifier).then(glide.withFadeMask(clips, isOn = true, hidesCutRows = true)),
        // The card's bottom padding scrolls with the rows: the last one rests above it, and a focused row is kept clear of it. The slab of the
        // last row ends 15.33 dp (the board's padding and border) above the card's edge, so the padding counts the slab's overhang too.
        // Both paddings are whole pixels, and the room the glide adds is rounded once and apart from the rest: summed in dp and rounded together, a
        // fraction could add a pixel with motion on, moving Back and the facts' window by one (InfoGeometryMotionTest).
        contentPadding = with(density) {
            val room = glide.extra.roundToPx()
            PaddingValues(top = room.toDp(), bottom = (room + (SlabDimens.cardGap + slabOverhangDp).roundToPx() + slackPx).toDp())
        },
        verticalArrangement = Arrangement.spacedBy(PlumDimens.panelRowGap),
    ) {
        itemsIndexed(content.items, key = { index, item -> (item as? PanelItem.Row)?.row?.key ?: "heading_$index" }) { index, item ->
            when (item) {
                is PanelItem.Heading -> PanelHeading(item.text.resolve(), Modifier.testTag(GamePanelTags.heading(index)))
                is PanelItem.Row -> {
                    val focusIndex = content.focusableRows.indexOf(item.row)
                    val look =
                        when {
                            !item.row.isEnabled -> OptionRowLook.Disabled
                            item.row == focusedRow -> OptionRowLook.Focused
                            else -> OptionRowLook.Normal
                        }
                    PanelRowView(item.row, look, focusIndex, onAction, bands = bands.getOrPut(item.row.key) { RowTextBands() })
                }
            }
        }
    }
    }
    // Marks the edges the list continues past, for the tests and for accessibility tools reading the tree; the fade itself is the mask above.
    val window = { (listState.layoutInfo.viewportSize.height - 2 * with(density) { glide.extra.roundToPx() }).toFloat() }
    val continuesBelow by remember(clips) { derivedStateOf { clips.continuesPast(atTop = false, window()) } }
    val continuesAbove by remember(clips) { derivedStateOf { clips.continuesPast(atTop = true, window()) } }
    if (continuesBelow) Box(Modifier.align(Alignment.BottomCenter).testTag(GamePanelTags.FADE_BOTTOM))
    if (continuesAbove) Box(Modifier.align(Alignment.TopCenter).testTag(GamePanelTags.FADE_TOP))
    }
    KeepFocusedVisible(listState, glide, snapsToRows = true, keepsContextAtOpen = true) { current }
}

@Composable
internal fun PanelRowView(
    row: PanelRow,
    look: OptionRowLook,
    focusIndex: Int,
    onAction: (Action) -> Unit,
    /** Drawn above the row inside the same lazy item (a store's status line), so the row's place in the item is its own. */
    lead: (@Composable () -> Unit)? = null,
    /** Fills with where the row's texts sit (the menu list needs it to keep a cut row from showing a sliver of a text). */
    bands: RowTextBands? = null,
) {
    val tap =
        remember(focusIndex, onAction) {
            if (focusIndex >= 0) Modifier.tapItem(StripColumn.Details, focusIndex, onAction) else Modifier
        }
    val chevron: (@Composable () -> Unit)? =
        if (row.command.opensSomething) {
            {
                val colors = PlumTheme.colors
                ValueChevron(ChevronSide.Right, PlumTheme.type.meta, { colors.muted }, tag = GamePanelTags.chevron(row.key))
            }
        } else {
            null
        }
    CompositionLocalProvider(LocalRowTextMark provides (bands?.mark ?: Modifier)) {
    Column(if (bands != null) Modifier.onGloballyPositioned { bands.item = it } else Modifier) {
        lead?.invoke()
        OptionRow(
            label = row.label.resolve(),
            look = look,
            value = row.value?.resolve().orEmpty(),
            // Beside the name; the picker's card is wide enough for the longest name and its badge on one line (the row puts it below only at the largest text sizes).
            badge = if (row.isCurrent) stringResource(R.string.game_current) else null,
            trailing = chevron,
            sidePadding = PlumDimens.panelTitleStart,
            note = row.note?.resolve(),
            modifier = tap.testTag(GamePanelTags.row(row.key)),
        )
    }
    }
}

/** "Not installed": divides a list, never focused. */
@Composable
private fun PanelHeading(
    text: String,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        style = PlumTheme.type.smallFact.copy(color = PlumTheme.colors.muted),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(start = PlumDimens.panelTitleStart, end = PlumDimens.panelTitleStart, top = PlumDimens.panelBlockGap, bottom = PlumDimens.factsRowGap),
    )
}

/** Test tags of the game panel. */
internal object GamePanelTags {
    const val PANEL = "game_panel"

    /** The raised card: the panel's content and its inset on both sides. */
    const val CARD = "game_panel_card"

    fun chevron(key: String) = "game_panel_chevron_$key"
    const val TITLE = "game_panel_title"
    const val ROWS = "game_panel_rows"
    const val FACTS = "game_panel_facts"

    /** Info's facts and "Name from", which scroll when the panel is too short for them. */
    const val FACTS_SCROLL = "game_panel_facts_scroll"
    /** Over an edge the list continues past: Info's facts fade there, the rows' list draws its chevron (the tag means "continues"). */
    const val FADE_BOTTOM = "game_panel_fade_bottom"
    const val FADE_TOP = "game_panel_fade_top"
    const val OUTSIDE = "game_panel_outside"

    const val NAME_FROM = "game_panel_name_from"

    /** Info's description (ScreenScraper's synopsis) and its blocks (0 the label). */
    const val DESCRIPTION = "game_panel_description"

    fun row(key: String) = "game_panel_row_$key"

    /** The heading at item [index] of the panel's list ("Not installed"). */
    fun heading(index: Int) = "game_panel_heading_$index"

    /** Info's fact row [index] (System first). */
    fun fact(index: Int) = "game_panel_fact_$index"

    /** Line [index] of Info's "Name from" (0 its label). */
    fun nameFromLine(index: Int) = "game_panel_name_from_$index"

    fun descriptionBlock(index: Int) = "game_panel_description_$index"

    /** Info's RetroAchievements section, its one quiet line, its count lines and each badge; [achievement] n is the n-th row of the list (0 first). */
    const val ACHIEVEMENTS = "game_panel_achievements"
    const val ACHIEVEMENTS_LINE = "game_panel_achievements_line"
    const val ACHIEVEMENTS_SUMMARY = "game_panel_achievements_summary"

    /** The quiet "Matched by name" line under the summary of a game found by its name. */
    const val ACHIEVEMENTS_BY_NAME = "game_panel_achievements_by_name"
    const val ACHIEVEMENT_BADGE = "game_panel_achievement_badge"

    fun achievement(index: Int) = "game_panel_achievement_$index"
}

private val IGNORE_ACTIONS: (Action) -> Unit = {}

private const val TITLE_LINES = 3
private const val BODY_LINES = 6
