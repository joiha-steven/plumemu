package com.plumemu.setup

import com.plumemu.keyboard.PasteBoard
import com.plumemu.telemetry.ScreenId
import com.plumemu.usage.TrackError
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.plumemu.coreui.theme.MaskOpaque
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column as StripColumn
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreinput.TextEntry
import com.plumemu.coreui.components.keyboard.KeyboardEvent
import com.plumemu.coreui.components.keyboard.KeyboardPage
import com.plumemu.account.signInActionWord
import com.plumemu.home.TypingHintBar
import com.plumemu.home.typingHints
import com.plumemu.keyboard.KeyboardChoice
import com.plumemu.keyboard.aboveTray
import com.plumemu.coreinput.hint
import com.plumemu.coreinput.tapItem
import com.plumemu.coreui.components.BatteryStatus
import com.plumemu.coreui.components.Hint
import com.plumemu.coreui.components.HintBar
import com.plumemu.coreui.components.ScanPulseLine
import com.plumemu.coreui.components.WifiStatus
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.SlabDimens
import com.plumemu.coreui.theme.WidthClass
import com.plumemu.home.rememberBatteryCharging
import com.plumemu.home.rememberBatteryReading
import com.plumemu.home.rememberBatteryText
import com.plumemu.home.rememberClockText
import com.plumemu.home.rememberWifiConnected
import com.plumemu.settings.ButtonLetters
import com.plumemu.sound.HearFeel

/**
 * First-run setup as MainActivity shows it: the model's state, the router's actions while
 * started (gamepad and taps alike), and the folder picker when the model asks for it. [keyboard] is
 * Settings › Controls › Keyboard: the box art step's sign-in form types with it.
 */
@Composable
internal fun SetupScreen(
    model: SetupModel,
    input: InputRouter,
    letters: ButtonLetters,
    onOpenFolderPicker: () -> Unit,
    keyboard: KeyboardChoice = KeyboardChoice.Plumemu,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val openPicker by rememberUpdatedState(onOpenFolderPicker)
    val currentKeyboard by rememberUpdatedState(keyboard)
    HearFeel(model.feel)
    TrackError(ScreenId.Setup, state.message)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(model, input, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            input.actions.collect { action ->
                model.keyboardChoice = currentKeyboard
                if (model.onAction(action) == SetupEffect.OpenFolderPicker) openPicker()
                // At once, not a frame later: a B pressed just after the tray went down must go back, not erase.
                input.textEntry = model.textEntry
            }
        }
    }
    TellRouterAboutTyping(input) { state.textEntry }
    val send = remember(input) { { action: Action -> input.send(action) } }
    val clock = rememberClockText()
    val batteryReading = rememberBatteryReading()
    val battery = rememberBatteryText(batteryReading)
    val charging = rememberBatteryCharging(batteryReading)
    val wifi = rememberWifiConnected()
    SetupLayout(state, letters, send, clock = { clock.value }, battery = { battery.value }, wifi = { wifi.value }, onTrayKey = model::onTrayKey, charging = { charging.value })
}

/**
 * Keeps [input]'s [InputRouter.textEntry] on what the sign-in form's typing needs ([SetupUiState.textEntry]),
 * including changes no action made (a tap on a key, a field or a button, the form appearing or going);
 * none when setup leaves the composition (completed, or the Activity going), so the home screen
 * never gets an erase or a typed key from it.
 */
@Composable
private fun TellRouterAboutTyping(
    input: InputRouter,
    textEntry: () -> TextEntry,
) {
    LaunchedEffect(input) { snapshotFlow(textEntry).collect { input.textEntry = it } }
    DisposableEffect(input) { onDispose { input.textEntry = TextEntry.None } }
}

/**
 * The board's RP5-O1 (wide: title column 260 beside the content) and Nova-7 (narrow: stacked),
 * between setup's top bar and the hint bar. Stateless: screenshots and tests host it directly.
 */
@Composable
internal fun SetupLayout(
    state: SetupUiState,
    letters: ButtonLetters,
    onAction: (Action) -> Unit,
    clock: () -> String,
    battery: () -> String?,
    modifier: Modifier = Modifier,
    wifi: () -> Boolean = { false },
    /** A tap on a key of Plumemu's tray over the sign-in form ([SetupModel.onTrayKey]). */
    onTrayKey: (KeyboardEvent) -> Unit = {},
    /** Whether the charging bolt stands before the battery percentage; read only by the bolt. */
    charging: () -> Boolean = { false },
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(top = PlumDimens.stripMarginTop, bottom = PlumDimens.footerBelow)
            .testTag(SetupTags.SCREEN),
    ) {
        SetupTopBar(stepIndex = state.stepDotIndex, stepCount = state.stepDots, clock, battery, wifi, charging, typing = { state.boxArt?.isTrayUp == true })
        Spacer(Modifier.height(PlumDimens.barGap))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Body(state, onAction)
            // Plumemu's keyboard for the sign-in form, down to the screen's bottom; the hint bar is drawn on it.
            SetupKeyboardTray(state.boxArt, onTrayKey, belowContent = setupTrayBelowBody())
        }
        // The scroll's own gap (bleed, last spacer, reveal) is measured from this body's bottom, so it follows.
        Spacer(Modifier.height(PlumDimens.footerGap))
        SetupHints(state, letters, onAction)
    }
}

/** The step's title and content between the bars; with the tray up the scroll ends at the tray's top. */
@Composable
private fun Body(
    state: SetupUiState,
    onAction: (Action) -> Unit,
) {
    val covers = setupKeyboardCovers()
    val trayCut = Modifier.aboveTray({ state.boxArt?.isTrayUp == true }, covers)
    Box(Modifier.fillMaxSize().padding(horizontal = PlumDimens.stripMarginSide)) {
        val title = stepTitle(state).let { if (state.boxArt?.isTrayUp == true) it.copy(description = null) else it }
        val content: @Composable ColumnScope.() -> Unit = {
            when {
                state.hasLoadFailed -> LoadFailedContent(state, onAction)
                state.step == SetupStep.Folders -> FolderStepContent(state, onAction)
                state.step == SetupStep.Confirm -> ConfirmStepContent(state, onAction)
                state.step == SetupStep.Look -> LookStepContent(state, onAction)
                state.step == SetupStep.SecondScreen -> SecondScreenStepContent(state, onAction)
                state.step == SetupStep.BoxArt -> BoxArtStepContent(state, onAction)
                else -> LibraryStepContent(state, onAction)
            }
        }
        // The sign-in form keeps the ring's reach only: its scroll ends at Plumemu's tray, which covers the bar gap.
        val hasForm = state.boxArt != null
        val bleedBottom = if (hasForm) PlumDimens.focusRingOutset else PlumDimens.footerGap
        // The form's scroll ends at Plumemu's tray, where its fields are placed by `aboveTray`: no resting on lines there.
        if (PlumTheme.layout.widthClass == WidthClass.Wide) WideBody(title, trayCut, bleedBottom, !hasForm, state, content) else NarrowBody(title, trayCut, bleedBottom, !hasForm, content)
    }
}

/** [status] is a quiet line of the step under the description (the games are still being named); null when there is none. */
private data class StepTitle(val title: String, val description: String?, val status: String? = null)

@Composable
private fun stepTitle(state: SetupUiState): StepTitle =
    if (state.hasLoadFailed) {
        StepTitle(stringResource(R.string.setup_load_failed_title), stringResource(R.string.setup_load_failed_body))
    } else {
        stepTitleOf(state)
    }

@Composable
private fun stepTitleOf(state: SetupUiState): StepTitle =
    when (state.step) {
        SetupStep.Folders -> StepTitle(stringResource(R.string.setup_folder_title), stringResource(R.string.setup_folder_desc))
        SetupStep.Confirm ->
            StepTitle(stringResource(R.string.setup_confirm_title), stringResource(if (state.confirmAnswer == null) R.string.setup_confirm_desc else R.string.setup_confirm_again_desc))
        SetupStep.Look -> StepTitle(stringResource(R.string.setup_look_title), stringResource(R.string.setup_look_desc))
        SetupStep.SecondScreen -> StepTitle(stringResource(R.string.setup_second_screen_title), stringResource(R.string.setup_second_screen_desc))
        SetupStep.BoxArt ->
            if (state.boxArt == null) {
                StepTitle(stringResource(R.string.setup_box_art_question_title), stringResource(R.string.setup_box_art_question_desc))
            } else {
                StepTitle(stringResource(R.string.setup_box_art_title), stringResource(R.string.setup_box_art_desc))
            }
        SetupStep.Scan ->
            if (state.hasScanFailed) {
                StepTitle(stringResource(R.string.setup_scan_failed_title), stringResource(R.string.setup_scan_failed_body))
            } else {
                StepTitle(stringResource(R.string.setup_scan_title), stringResource(R.string.setup_scan_desc))
            }
        SetupStep.Ready -> readyTitleOf(state).let { StepTitle(it.title, it.description, it.status) }
    }

/** Title column 260 wide, 48 before the content; the content scrolls on its own if it runs long. */
@Composable
private fun WideBody(
    title: StepTitle,
    trayCut: Modifier,
    bleedBottom: Dp,
    restsOnCards: Boolean,
    state: SetupUiState,
    content: @Composable ColumnScope.() -> Unit,
) {
    val snaps = remember { LineSnaps() }.takeIf { restsOnCards }
    Row(Modifier.fillMaxSize().padding(top = PlumDimens.setupTopPaddingWide)) {
        TitleBlock(title, PlumDimens.setupTitleGapWide, Modifier.width(PlumDimens.setupTitleColumnWidth), counts = state.takeIf { it.countsInTitle })
        Spacer(Modifier.width(PlumDimens.setupMainGapWide))
        ScrollingColumn(Modifier.weight(1f).then(trayCut), bleedBottom, snaps) {
            val column = Modifier.then(if (snaps == null) Modifier else with(snaps) { Modifier.anchoring() })
            CompositionLocalProvider(LocalLineSnaps provides snaps) {
                Column(column, verticalArrangement = Arrangement.spacedBy(PlumDimens.setupContentGap), content = content)
            }
        }
    }
}

/** Title over the content, 14 apart; the whole column scrolls when it runs long (several folders). */
@Composable
private fun NarrowBody(
    title: StepTitle,
    trayCut: Modifier,
    bleedBottom: Dp,
    restsOnLines: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val snaps = remember { LineSnaps() }.takeIf { restsOnLines }
    ScrollingColumn(trayCut.fillMaxSize(), bleedBottom, snaps) {
        if (snaps != null) with(snaps) { Spacer(Modifier.height(PlumDimens.setupTopPaddingNarrow).anchoring()) } else Spacer(Modifier.height(PlumDimens.setupTopPaddingNarrow))
        TitleBlock(title, PlumDimens.setupTitleGapNarrow, Modifier.fillMaxWidth(), snaps)
        Spacer(Modifier.height(PlumDimens.setupMainGapNarrow))
        CompositionLocalProvider(LocalLineSnaps provides snaps) {
            Column(verticalArrangement = Arrangement.spacedBy(PlumDimens.setupContentGap), content = content)
        }
    }
}

/**
 * A vertical scroll that leaves room for focus rings at its edges (they would be clipped), and
 * [bleedBottom] under it: the bar gap down to the hint bar, so the Ready step's Start playing (up to
 * 4 past the content at 768 x 432) needs no scroll and its ring is whole from the first frame.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScrollingColumn(
    modifier: Modifier,
    bleedBottom: Dp,
    snaps: LineSnaps? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    val extra = remember { mutableIntStateOf(0) }
    // What the content measures without the extra room, and the scroll's own height: from these (not from maxValue, which
    // already holds the extra) the room is worked out, so it cannot chase itself.
    val sizes = remember { mutableStateOf(IntSize.Zero) }
    val viewport = remember { mutableIntStateOf(0) }
    val ring = with(LocalDensity.current) { PlumDimens.focusRingOutset.roundToPx() }
    // The scroll lives on across steps: a step without snaps (box art) must not keep the room a step with them added.
    if (snaps == null) LaunchedEffect(Unit) { extra.intValue = 0 }
    if (snaps != null) {
        // The scroll's end is where a focused last button sits the bar gap above the hints; when that is half way through a
        // line of the title, a little more room under the content moves the end to the next line (what is added is up to the distance to the next rest: a line of the title, or a whole card).
        LaunchedEffect(snaps) {
            snapshotFlow { Triple(sizes.value.height, viewport.intValue, snaps.version) }.collect { (content, area, _) ->
                val base = content + ring * 2 - area
                extra.intValue = if (base > 0) snaps.restAtOrPast(base) - base else 0
            }
        }
        // The first reveal runs before the extra room is laid out and stops at the old end; the end then moves to a line, and
        // a scroll that rested at the old end follows it.
        LaunchedEffect(scroll) {
            var before = scroll.maxValue
            snapshotFlow { scroll.maxValue to extra.intValue }.collect { (max, added) ->
                // Only a growth the added room explains (not a tray coming up), and not from 0: a step that has just appeared is at its top.
                if (before > 0 && max > before && max - before <= added && scroll.value == before) scroll.scrollTo(max)
                before = max
            }
        }
    }
    val density = LocalDensity.current
    val baseReveal = remember(scroll, snaps) { if (snaps == null) InstantReveal else LineSnapReveal(scroll, snaps) }
    // The scroll's frame reaches `room` past the window on every side, the content padded by the same room: nothing moves and the scroll
    // range is the same, but a focused slab's shadow and glow fade out instead of ending flat at the clip. Where the scroll is in the
    // middle that extra frame is cut back (the rows beyond would show under the hint bar): only an end that rests keeps its room.
    val room = SlabDimens.listEndRoom
    val roomPx = with(density) { room.toPx() }
    val fadePx = with(density) { PlumDimens.listFadeBand.toPx() }
    // A reveal measures the window as it was without the room.
    val reveal = remember(baseReveal, roomPx) { WithoutRoom(baseReveal) { roomPx } }
    CompositionLocalProvider(LocalBringIntoViewSpec provides reveal) {
        Column(
            modifier
                .bleed(PlumDimens.focusRingOutset, bottom = bleedBottom)
                .clipsLayoutToWindow(reach = room)
                .bleed(0.dp, bottom = 0.dp, top = room, sides = room, below = room)
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    // Read at draw time: a scroll never recomposes for its position.
                    // At rest on a line the ring's reach above the window is cut too: it would keep the bottom of the line above (faint specks).
                    val onLine = scroll.value > 0 && snaps?.isRest(scroll.value, ring) == true
                    val cutTop = if (onLine) roomPx + ring else if (scroll.value > 0) roomPx else 0f
                    val cutBottom = if (scroll.value < scroll.maxValue) roomPx else 0f
                    clipRect(top = cutTop, bottom = size.height - cutBottom) { this@drawWithContent.drawContent() }
                    // A cut edge fades out (the rows and their shadows are erased towards it) instead of ending flat.
                    if (cutTop > 0f && !onLine) drawFade(cutTop, cutTop + fadePx, fromTop = true)
                    if (cutBottom > 0f) drawFade(size.height - cutBottom - fadePx, size.height - cutBottom, fromTop = false)
                }
                .testTag(SetupTags.SCROLL)
                .onSizeChanged { viewport.intValue = it.height - (roomPx * 2).toInt() } // the room is outside the window the extra room is worked out from
                .verticalScroll(scroll)
                .padding(PlumDimens.focusRingOutset + room),
        ) {
            Column(Modifier.onSizeChanged { sizes.value = it }) {
                content()
                // Always: scrolled to its end, the last button sits the bar gap above the hint bar, not the ring's reach (4.5).
                // Only when the content ran long it used to be there; a Ready step that fitted by a few dp kept its Start playing
                // 5.6 above the bar, and one that came after a shorter step kept the shorter step's range of 0.
                Spacer(Modifier.height(PlumDimens.footerGap - PlumDimens.focusRingOutset))
            }
            Spacer(Modifier.height(with(density) { extra.intValue.toDp() }))
        }
    }
}

/**
 * Brings a focused control into view at once. An animated reveal (about a quarter of a second after a step
 * appears) makes the scroll eat a finger that lands in it: the first tap on Start playing did nothing
 * (`SetupReadyTapTest`).
 */
@OptIn(ExperimentalFoundationApi::class)
private val InstantReveal =
    object : BringIntoViewSpec {
        override val scrollAnimationSpec: AnimationSpec<Float> = snap()
    }

@Composable
private fun TitleBlock(
    title: StepTitle,
    gap: androidx.compose.ui.unit.Dp,
    modifier: Modifier,
    snaps: LineSnaps? = null,
    /** The wide Ready step's counts, which sit under the description so the scroll never hides them. */
    counts: SetupUiState? = null,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        BasicText(
            text = title.title,
            style = type.setupTitle(PlumTheme.layout.widthClass).copy(color = colors.text),
            modifier = Modifier.testTag(SetupTags.TITLE).then(if (snaps == null) Modifier else with(snaps) { Modifier.textPart(0) }),
            onTextLayout = { snaps?.layoutOf(0, it) },
        )
        // Null while the tray is up over the sign-in form: the paragraph is read before typing, and the tray would cut it mid-line.
        if (title.description != null) {
            BasicText(
                text = title.description,
                style = type.description.copy(color = colors.muted),
                modifier = Modifier.testTag(SetupTags.DESCRIPTION).then(if (snaps == null) Modifier else with(snaps) { Modifier.textPart(1) }),
                onTextLayout = { snaps?.layoutOf(1, it) },
            )
        }
        if (title.status != null) {
            ScanPulseLine(
                text = title.status,
                style = type.smallFact.copy(color = colors.muted),
                dotModifier = Modifier.testTag(SetupTags.NAMING_PULSE),
                textModifier = Modifier.testTag(SetupTags.NAMING_LINE),
            )
        }
        if (counts != null) LibraryCounts(counts)
    }
}

/**
 * "Setup" and the step dots (folder, the second screen on a device with one, library: the steps
 * the alpha has), clock and battery on the right.
 */
@Composable
private fun SetupTopBar(
    stepIndex: Int,
    stepCount: Int,
    clock: () -> String,
    battery: () -> String?,
    wifi: () -> Boolean,
    charging: () -> Boolean,
    typing: () -> Boolean,
) {
    val colors = PlumTheme.colors
    val style = PlumTheme.type.topBar.copy(color = colors.muted)
    Row(
        Modifier.fillMaxWidth().height(if (typing()) PlumTheme.layout.topBarHeightTyping else PlumTheme.layout.topBarHeight).padding(horizontal = PlumDimens.stripMarginSide),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(PlumDimens.setupDotsGap), verticalAlignment = Alignment.CenterVertically) {
            BasicText(text = stringResource(R.string.setup_top_bar), style = style.copy(color = colors.text2), maxLines = 1)
            StepDots(current = stepIndex, count = stepCount)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(PlumDimens.topBarItemGap), verticalAlignment = Alignment.CenterVertically) {
            BasicText(text = clock(), style = style, maxLines = 1)
            WifiStatus(wifi, style, stringResource(R.string.wifi_connected))
            BatteryStatus(battery, charging, style, stringResource(R.string.battery_charging))
        }
    }
}

/** The hints of each step; letters from [ButtonLetters]; a tap does what the button does. */
@Composable
private fun SetupHints(
    state: SetupUiState,
    letters: ButtonLetters,
    onAction: (Action) -> Unit,
) {
    val form = state.boxArt
    // While the tray is up: the typing hints of every field (A Type, B Delete, X Space, START Next / Sign in, …).
    if (form != null && form.isTrayUp) {
        val keys = form.keysOf(form.focus).keys
        val onLetters = keys.page == KeyboardPage.Letters
        val actionWord = signInActionWord(form)
        val canPaste = PasteBoard.hasText
        val hints = remember(onLetters, actionWord, letters, canPaste) { typingHints(onLetters, actionWord, hasShift = true, letters, canPaste) }
        TypingHintBar(hints, onAction, Modifier.testTag(SetupTags.TYPING_HINTS))
        return
    }
    val confirmLabel =
        if (form != null) boxArtConfirmHint(form) else when (state.focus) {
            SetupControl.ChooseFolder -> R.string.setup_hint_choose_folder
            SetupControl.SetUpLater -> R.string.setup_set_up_later
            SetupControl.FolderCard0, SetupControl.FolderCard1, SetupControl.FolderCard2, SetupControl.FolderCard3,
            SetupControl.FolderCard4, SetupControl.FolderCard5, SetupControl.FolderCard6, SetupControl.FolderCard7,
            -> R.string.setup_hint_remove
            SetupControl.AddFolder -> R.string.setup_hint_add_folder
            SetupControl.Continue -> R.string.setup_hint_continue
            SetupControl.UseSecondScreen, SetupControl.SkipSecondScreen, SetupControl.UseBoxArt, SetupControl.BoxArtLibretro, SetupControl.BoxArtOwnCovers,
            SetupControl.Look0, SetupControl.Look1, SetupControl.Look2, SetupControl.Look3, SetupControl.Look4, SetupControl.Look5,
            -> R.string.setup_hint_select
            SetupControl.ConfirmKeep, SetupControl.ConfirmUseB -> R.string.setup_hint_select
            SetupControl.TryAgain -> R.string.setup_hint_try_again
            SetupControl.UsageStatistics -> R.string.setup_hint_switch
            SetupControl.StartPlaying -> R.string.setup_hint_start_playing
            null -> null
        }
    // Every step after the folders goes back with B (Scan and Ready to the last question asked); the failed load has no step to go back from.
    val showsBack = state.step != SetupStep.Folders && !state.hasLoadFailed
    val backCap = stringResource(letters.back)
    HintBar(Modifier.testTag(SetupTags.HINTS)) {
        if (confirmLabel != null) {
            Hint(
                keyCap = stringResource(letters.confirm),
                label = stringResource(confirmLabel),
                modifier = Modifier.hint(Action.Enter, onAction).testTag(SetupTags.HINT_CONFIRM),
            )
        }
        if (showsBack) {
            Hint(
                keyCap = backCap,
                label = stringResource(R.string.setup_hint_back),
                modifier = Modifier.hint(Action.Back, onAction).testTag(SetupTags.HINT_BACK),
            )
        }
    }
}

/**
 * Taps on setup's controls go through the router like a key: `TapItem` with the control's place
 * in [SetupUiState.controls] (setup has one column; core-input's Games stands for it). The
 * focused control asks to be scrolled into view.
 */
@Composable
internal fun Modifier.setupControl(
    state: SetupUiState,
    control: SetupControl,
    onAction: (Action) -> Unit,
): Modifier {
    val index = state.controls.indexOf(control)
    return this
        // With its focus ring: on 576 x 432 the Ready step's rows at 13 push Start
        // playing to the scroll's edge, where the ring was cut by the hint bar's gap.
        // Down to the bar gap, not only the ring: the focused last button keeps the gap above the hint bar (10), not 5.5.
        .bringIntoViewWhen(state.focus == control, above = state.aboveLastButton, ring = PlumDimens.focusRingOutset, below = PlumDimens.footerGap - PlumDimens.focusRingOutset)
        .then(if (index >= 0) Modifier.tapItem(StripColumn.Games, index, onAction) else Modifier)
        .testTag(SetupTags.control(control))
}

/** Erases what is drawn from [from] to [to] (px), fully at the cut end, to nothing at the other: the fade of a scroll's cut edge. */
private fun DrawScope.drawFade(
    from: Float,
    to: Float,
    fromTop: Boolean,
) {
    val clear = MaskOpaque.copy(alpha = 0f)
    drawRect(
        Brush.verticalGradient(if (fromTop) listOf(MaskOpaque, clear) else listOf(clear, MaskOpaque), startY = from, endY = to),
        topLeft = Offset(0f, from),
        size = Size(size.width, to - from),
        blendMode = BlendMode.DstOut,
    )
}
