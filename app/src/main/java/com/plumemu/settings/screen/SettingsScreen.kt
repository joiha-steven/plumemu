package com.plumemu.settings.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.plumemu.R
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SoundLevel
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreinput.TextEntry
import com.plumemu.coreinput.hint
import com.plumemu.coreui.components.EdgeFade
import com.plumemu.coreui.components.InsetFocusPillShape
import com.plumemu.coreui.components.SystemRowLook
import com.plumemu.coreui.components.TopBar
import com.plumemu.coreui.theme.LeftColumnDimens
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumLayout
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.SettingsThreeDimens
import com.plumemu.coreui.theme.SlabDimens
import com.plumemu.coreui.theme.WidthClass
import com.plumemu.game.isTrayUp
import com.plumemu.home.rememberBatteryCharging
import com.plumemu.home.rememberBatteryReading
import com.plumemu.home.rememberBatteryText
import com.plumemu.home.rememberClockText
import com.plumemu.home.rememberWifiConnected
import com.plumemu.sound.HearFeel
import com.plumemu.sound.LocalUiSounds
import com.plumemu.sound.Outcome
import com.plumemu.usage.TrackScreen
import com.plumemu.usage.screenIdOf
import kotlin.math.floor

/**
 * Settings: sections | options | panel on the
 * same sliding strip as the main screen. Collects [router]'s actions only while started; writes go
 * to [actions] and apply at once through the store (theme, router and hint letters follow it).
 */
@Composable
internal fun SettingsScreen(
    settings: PlumSettings,
    router: InputRouter,
    actions: SettingsActions,
    versionName: String,
    modifier: Modifier = Modifier,
    status: SettingsStatus = SettingsStatus(),
    content: SettingsContent = SettingsContent(),
    /** Told each new state of Statistics' figures (the second screen's card); no second read. */
    onStatistics: (StatisticsState) -> Unit = {},
    /** Opens on About › Updates' panel (the main screen's pill or START while an update waits), once, when that row exists. */
    opensOnUpdate: Boolean = false,
    /** Opens on Library › Files not added (the home's empty state), once, when the library's content has arrived. */
    opensOnFilesNotAdded: Boolean = false,
    /** Closes Settings and puts the main screen's cursor on a game in its system's list (Statistics' most-played rows). */
    showOnHome: (systemId: String, gameId: Long) -> Unit = { _, _ -> },
) {
    // Made with its content (as the SideEffect below sets it): the first composition draws the real rows, not empty ones.
    val model = remember(actions) { SettingsModel(actions).also { it.content = content.copy(hasNoButtonHints = !settings.controls.showButtonHints) } }
    SideEffect { model.showOnHome = showOnHome }
    val sounds = LocalUiSounds.current
    HearFeel(model.feel)
    TrackSettingsSection(model)
    DisposableEffect(model) { onDispose { model.onDispose() } }
    // Statistics' figures are read once per opening, after the first frame; content given with them (tests) keeps its own.
    val statistics = rememberStatistics(actions.statistics, content.isScanning, status.libraryReset)
    SideEffect { model.content = (if (content.statistics == StatisticsState.NotRead) content.copy(statistics = statistics) else content).copy(hasNoButtonHints = !settings.controls.showButtonHints) }
    val shownStatistics = if (content.statistics == StatisticsState.NotRead) statistics else content.statistics
    val currentOnStatistics by rememberUpdatedState(onStatistics)
    LaunchedEffect(shownStatistics) { currentOnStatistics(shownStatistics) }
    val geometry = settingsGeometry(PlumTheme.layout)
    val currentSettings by rememberUpdatedState(settings)
    val currentGeometry by rememberUpdatedState(geometry)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Once: the content arrives after the first frame, and the panel must not reopen after the person left it.
    var hasOpenedUpdate by rememberSaveable { mutableStateOf(false) }
    val hasUpdateRow = content.update.found != null
    LaunchedEffect(opensOnUpdate, hasUpdateRow) {
        if (opensOnUpdate && hasUpdateRow && !hasOpenedUpdate) hasOpenedUpdate = model.openUpdate(currentSettings, currentGeometry)
    }
    // Library's rows depend on the content (unknown folders sit before this one), so it waits for it: the folders arrive with it.
    var hasOpenedFilesNotAdded by rememberSaveable { mutableStateOf(false) }
    val hasLibraryContent = content.folders.isNotEmpty()
    LaunchedEffect(opensOnFilesNotAdded, hasLibraryContent) {
        if (opensOnFilesNotAdded && hasLibraryContent && !hasOpenedFilesNotAdded) hasOpenedFilesNotAdded = model.openFilesNotAdded(currentSettings, currentGeometry)
    }
    LaunchedEffect(router, model, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            router.actions.collect {
                model.onAction(it, currentSettings, currentGeometry)
                // At once, not a frame later: a B pressed just after the tray went down must go back, not erase.
                router.textEntry = model.textEntry
            }
        }
    }
    TellRouterAboutTyping(router, model)
    // Changing the Sounds level plays one confirm at the new volume (Off is silent); not when Settings just opened.
    var soundsWere by remember { mutableStateOf(settings.advanced.sounds) }
    LaunchedEffect(settings.advanced.sounds) {
        if (settings.advanced.sounds != SoundLevel.Off && settings.advanced.sounds != soundsWere) sounds.playFor(Outcome.Activated)
        soundsWere = settings.advanced.sounds
    }
    val touch =
        remember(model, router) {
            SettingsTouch(
                onSection = model::onTapSection,
                onOption = { model.onTapOption(it, currentSettings, currentGeometry) },
                onValue = { model.onTapValue(it, currentSettings, currentGeometry) },
                onPanelItem = { model.onTapPanelItem(it, currentSettings, currentGeometry) },
                onHexEntered = model::onHexEntered,
                onHexText = model::onHexTextChange,
                onHint = router::send,
                onSlider = { index, fraction -> model.onTouchSlider(index, fraction, currentSettings, currentGeometry) },
                onSliderEnd = model::onTouchSliderEnd,
            )
        }
    // Clock and Show battery hide them here too, so the switch shows its effect at once; nothing
    // ticks or listens while one is off.
    val clock = if (settings.homeScreen.showClock) rememberClockText() else null
    val batteryReading = if (settings.homeScreen.showBattery) rememberBatteryReading() else null
    val battery = batteryReading?.let { rememberBatteryText(it) }
    val charging = batteryReading?.let { rememberBatteryCharging(it) }
    val wifi = if (settings.homeScreen.showBattery) rememberWifiConnected() else null
    SettingsLayout(
        settings, model, touch, versionName, { clock?.value }, { battery?.value }, modifier, status, { wifi?.value == true },
        charging = { charging?.value == true },
    )
}

/**
 * Keeps [router]'s [InputRouter.textEntry] on what Settings' typing needs ([SettingsModel.textEntry]:
 * B erasing while a field's tray is up), including changes no action made (a tap on a key, on the
 * field or on a swatch); none when Settings leaves (B out of it, the Home button), so no other
 * screen ever gets an erase or a typed key.
 */
@Composable
private fun TellRouterAboutTyping(
    router: InputRouter,
    model: SettingsModel,
) {
    LaunchedEffect(router, model) { snapshotFlow { model.textEntry }.collect { router.textEntry = it } }
    DisposableEffect(router) { onDispose { router.textEntry = TextEntry.None } }
}

/** Every way a finger reaches Settings; hints send the same actions as the buttons. */
@Immutable
internal class SettingsTouch(
    val onSection: (SettingsSection) -> Unit,
    val onOption: (Int) -> Unit,
    val onValue: (Int) -> Unit,
    val onPanelItem: (Int) -> Unit,
    val onHexEntered: (String) -> Unit,
    val onHexText: (String) -> Unit,
    val onHint: (Action) -> Unit,
    /** A tap or drag on a colour slider's track: its panel index and where on the track (0..1). */
    val onSlider: (Int, Float) -> Unit = { _, _ -> },
    /** The finger lifted or was cancelled after [onSlider] calls (a tap too): the thumb settles. */
    val onSliderEnd: () -> Unit = {},
)

/**
 * Preset swatches per row: what fits between the panel's text-column lines, 14 in on each side (4 in the wide 248, 10 in the narrow stop); Colors'
 * preview tiles per row: what fits on the row-text lines at the tile's least width for the text size.
 */
internal fun settingsGeometry(layout: PlumLayout): SettingsGeometry {
    val panel = settingsPanelWidth(layout)
    val inner = panel - PlumDimens.settingsPanelRowPadding * 2
    val columns = floor((inner + PlumDimens.settingsPresetSwatchGap) / (PlumDimens.settingsPresetSwatch + PlumDimens.settingsPresetSwatchGap)).toInt()
    val tileGap = PlumDimens.settingsColorTileGap
    val tiles = floor((colorTilesWidth(layout) + tileGap) / (PlumDimens.settingsColorTileMinWidth * layout.textScale + tileGap)).toInt()
    return SettingsGeometry(isNarrow = layout.widthClass == WidthClass.Narrow, presetColumns = columns.coerceAtLeast(1),
        defaultTextSize = TextSizeSetting.Default,
        colorSetColumns = tiles.coerceIn(1, PlumDimens.SETTINGS_COLOR_TILE_MAX_COLUMNS),
    )
}

/** Colors' tiles span the panel between its row-text lines (14 in on each side). */
internal fun colorTilesWidth(layout: PlumLayout): Dp = settingsPanelWidth(layout) - PlumDimens.settingsPanelRowPadding * 2

/** The panel: 248 beside the options on wide screens; the options' width as a further stop on narrow ones. */
internal fun settingsPanelWidth(layout: PlumLayout): Dp =
    when {
        layout.isSettingsThreeColumns -> SettingsThreeDimens.panelWidth
        layout.widthClass == WidthClass.Wide -> PlumDimens.settingsPanelWidth
        else -> layout.settingsOptionsWidth
    }

/**
 * Where the strip rests for [column] (strip coordinates, as on the board): sections at 0; options
 * with margin and sections slid fully away (−224); the narrow panel stop one options column and
 * gap further. The wide panel sits beside the options, so the strip stays; in three columns it never moves.
 */
internal fun settingsStripOffset(
    column: SettingsColumn,
    layout: PlumLayout,
): Dp {
    val toOptions = PlumDimens.stripMarginSide + PlumDimens.settingsSectionsWidth
    return when {
        // Three columns: the sections stay where they are.
        layout.isSettingsThreeColumns -> 0.dp
        column == SettingsColumn.Sections -> 0.dp
        column == SettingsColumn.Panel && layout.widthClass == WidthClass.Narrow -> -(toOptions + PlumDimens.columnGap + layout.settingsOptionsWidth)
        else -> -toOptions
    }
}

/** The screen for a given model: top bar, strip, hints. Tests and shots drive it directly. */
@Composable
internal fun SettingsLayout(
    settings: PlumSettings,
    model: SettingsModel,
    touch: SettingsTouch,
    versionName: String,
    clock: () -> String?,
    battery: () -> String?,
    modifier: Modifier = Modifier,
    status: SettingsStatus = SettingsStatus(),
    wifi: () -> Boolean = { false },
    /** Whether the charging bolt stands before the battery percentage; read only by the bolt. */
    charging: () -> Boolean = { false },
) = CompositionLocalProvider(LocalSettingsStatus provides status) {
    val colors = PlumTheme.colors
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .drawBehind { drawRect(colors.bg) }
                // No tap passes through Settings (the main screen leaves the composition while it shows).
                .pointerInput(Unit) {}
                .padding(top = PlumDimens.stripMarginTop, bottom = PlumDimens.footerBelow)
                .testTag(SettingsTags.SCREEN),
    ) {
        SettingsTopBar(model, clock, battery, wifi, charging)
        Spacer(Modifier.height(PlumDimens.barGap))
        Box(Modifier.fillMaxWidth().weight(1f)) {
            SettingsStrip(settings, model, touch, versionName, Modifier.fillMaxSize())
            // Plumemu's keyboard for the focused page's field, down to the screen's bottom; the hint bar is drawn on it.
            SettingsKeyboardTray(model, belowContent = settingsTrayBelowStrip())
        }
        Spacer(Modifier.height(PlumDimens.footerGap))
        SettingsHints(settings, model, touch.onHint)
    }
}

@Composable
private fun SettingsTopBar(
    model: SettingsModel,
    clock: () -> String?,
    battery: () -> String?,
    wifi: () -> Boolean,
    charging: () -> Boolean,
) {
    val focus = model.focus
    val title = stringResource(R.string.settings_title)
    val section = stringResource(focus.section.title)
    val option =
        model.panelOption?.let {
            // The account rows' labels carry a name or a whole sentence: the crumb names the place only.
            when (it) {
                SettingsOption.ScraperAccount, SettingsOption.ScraperStatus -> stringResource(R.string.settings_account_title)
                SettingsOption.RetroAchievementsAccount -> stringResource(R.string.settings_ra_title)
                else -> optionLabel(it, model.content, model.optionIndex).withoutDots()
            }
        }
    val place =
        when {
            // The sections are on screen in three columns: the bar names the screen only.
            focus.column == SettingsColumn.Sections || PlumTheme.layout.isSettingsThreeColumns -> title
            // This section's name is too long to share the narrow bar with a panel's, and the panel's name alone says where it is.
            focus.column == SettingsColumn.Panel && option != null && focus.section == SettingsSection.Accounts ->
                stringResource(R.string.settings_crumb, title, option)
            focus.column == SettingsColumn.Panel && option != null ->
                stringResource(R.string.settings_crumb, stringResource(R.string.settings_crumb, title, section), option)
            else -> stringResource(R.string.settings_crumb, title, section)
        }
    TopBar(place = place, emphasis = title, modifier = Modifier.testTag(SettingsTags.TOP_BAR), clock = clock, battery = battery, wifi = wifi, wifiDescription = stringResource(R.string.wifi_connected),
        charging = charging, chargingDescription = stringResource(R.string.battery_charging),
        typing = { model.ownKeyboardField?.typing?.isTrayUp == true },
    )
}

@Composable
private fun SettingsStrip(
    settings: PlumSettings,
    model: SettingsModel,
    touch: SettingsTouch,
    versionName: String,
    modifier: Modifier,
) {
    SideEffect { SettingsRecomposeProbe.strips++ }
    val layout = PlumTheme.layout
    // Only the column is read here: a move inside a column must not recompose the strip (sections,
    // every option row and the panel); each row reads the focus for its own look.
    val column by remember(model) { derivedStateOf { model.focus.column } }
    val covered by remember(model, layout) { derivedStateOf { layout.isSettingsThreeColumns && model.focus.isWidePage() } }
    val density = LocalDensity.current
    val targetPx = with(density) { settingsStripOffset(column, layout).toPx() }
    val offset = remember { Animatable(targetPx) }
    val spec = PlumTheme.motion.slideSpec<Float>()
    val isMotionOn = PlumTheme.motion.isOn
    // Transform only: 180 ms ease-out, or a jump when motion is off.
    LaunchedEffect(targetPx, isMotionOn) { if (isMotionOn) offset.animateTo(targetPx, spec) else offset.snapTo(targetPx) }
    Box(modifier.clipStrip().testTag(SettingsTags.STRIP)) {
        Row(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .wrapContentWidth(align = Alignment.Start, unbounded = true)
                    .graphicsLayer { translationX = offset.value }
                    // No start padding: the sections column starts at the screen edge and holds the margin itself.
                    .padding(end = PlumDimens.stripMarginSide),
            horizontalArrangement = Arrangement.spacedBy(if (layout.isSettingsThreeColumns) 0.dp else PlumDimens.columnGap),
        ) {
            // Settings' opening composes each column in a frame of its own ([SettingsOpening]); nothing is drawn meanwhile.
            val stage = LocalSettingsOpeningStage.current
            if (stage < SettingsOpening.SECTIONS) Spacer(Modifier.width(sectionsColumnWidth(layout))) else SectionsColumn(model, touch)
            if (layout.isSettingsThreeColumns) Spacer(Modifier.width(SettingsThreeDimens.optionsGap))
            // A wide page covers the options: they are not composed then, so no text of theirs lies under the page.
            if (covered) Spacer(Modifier.width(layout.settingsOptionsWidth))
            else if (stage < SettingsOpening.OPTIONS) Spacer(Modifier.width(layout.settingsOptionsPreviewWidth)) else OptionsColumn(settings, model, touch, versionName)
            if (layout.isSettingsThreeColumns) Spacer(Modifier.width(SettingsThreeDimens.panelGap))
            if (layout.isSettingsThreeColumns) {
                Spacer(Modifier.width(settingsPanelWidth(layout)))
            } else {
                val panelVisible = column == SettingsColumn.Panel || (column == SettingsColumn.Options && layout.widthClass == WidthClass.Wide)
                Box(Modifier.width(settingsPanelWidth(layout)).fillMaxHeight().clipPanel().testTag(SettingsTags.PANEL)) {
                    if (panelVisible) PanelHost(settings, model, touch, versionName)
                }
            }
        }
        if (layout.isSettingsThreeColumns) ThreeColumnPanel(model) { PanelHost(settings, model, touch, versionName) }
        if (layout.isSettingsThreeColumns) SectionsDivider(Modifier.align(Alignment.TopStart))
        // Content runs past the right edge only while the sections show (never in three columns: everything is on screen).
        else if (column == SettingsColumn.Sections) EdgeFade(Modifier.align(Alignment.CenterEnd))
    }
}

/** The side panel of the focused option; its content fades in when another option takes the focus (no pop). */
@Composable
private fun PanelHost(
    settings: PlumSettings,
    model: SettingsModel,
    touch: SettingsTouch,
    versionName: String,
) {
    val option = model.panelOption ?: return
    // Keyed by the row too: each system of Emulators is the same option with its own panel.
    val textStart = if (PlumTheme.layout.isSettingsThreeColumns && !model.focus.isWidePage()) 0.dp else PlumDimens.gameRowPadding
    CompositionLocalProvider(LocalPanelTextStart provides textStart) {
        FadeIn(option to model.optionIndex, Modifier.fillMaxSize()) { PanelWithLookPreview(option, settings, model, touch, versionName) }
    }
}

@Composable
private fun SectionsColumn(
    model: SettingsModel,
    touch: SettingsTouch,
) {
    // Scrolls to the focused section (the rows do not all fit the strip). Second screen and Emulators come and go.
    val sections by remember(model) { derivedStateOf { SettingsSection.shownIn(model.content) } }
    KeepFocusedColumn(
        { sections.indexOf(model.focus.section) },
        sections.size,
        0.dp,
        Modifier.width(sectionsColumnWidth(PlumTheme.layout)).fillMaxHeight().testTag(SettingsTags.SECTIONS),
        fadeTag = SettingsTags.SECTIONS,
        endRoom = SlabDimens.listEndRoom,
        // Only while the sections have the focus: a Current section (focus in the options) keeps its own quiet look.
        pillIndex = { if (model.focus.column == SettingsColumn.Sections) sections.indexOf(model.focus.section) else -1 },
        // The rows draw their pill from [PlumDimens.gameRowPadding] before the label (SystemRow's bleedStart); the gliding one does the same.
        pillShape = remember { InsetFocusPillShape(LeftColumnDimens.systemPillStart, SlabDimens.barInset) },
    ) { place ->
        sections.forEachIndexed { index, section -> SectionRow(section, model, touch, place(index)) }
    }
}

@Composable
private fun SectionRow(
    section: SettingsSection,
    model: SettingsModel,
    touch: SettingsTouch,
    place: Modifier,
) {
    SideEffect { SettingsRecomposeProbe.sectionRows++ }
    val look by remember(model, section) {
        derivedStateOf {
            val focus = model.focus
            when {
                section != focus.section -> SystemRowLook.Normal
                focus.column == SettingsColumn.Sections -> SystemRowLook.Focused
                else -> SystemRowLook.Current
            }
        }
    }
    // Three columns: the open section is told by an accent dot where the focus bar stands (the board's current, not focused); every label starts alike.
    val three = PlumTheme.layout.isSettingsThreeColumns
    val colors = PlumTheme.colors
    val marked = three && look == SystemRowLook.Current
    SectionLabelRow(
        name = stringResource(section.title),
        look = look,
        endPadding = if (three) 0.dp else PlumDimens.systemRowPadding + PlumDimens.rowTrailingGap,
        // As high as an option row (not the systems' 40): the two columns' text lines then share a centre row by row, where the
        // 4 dp between 40 and 44 at Large put the options' first text line 2 dp lower.
        modifier = place.heightIn(min = PlumTheme.layout.rowHeight).tapTarget { touch.onSection(section) }.testTag(SettingsTags.section(section))
            .drawBehind {
                if (marked) drawCircle(colors.accent, PlumDimens.hintDot.toPx() / 2, Offset(LeftColumnDimens.sectionBarCentre.toPx(), size.height / 2))
            },
    )
}

@Composable
private fun OptionsColumn(
    settings: PlumSettings,
    model: SettingsModel,
    touch: SettingsTouch,
    versionName: String,
) {
    val layout = PlumTheme.layout
    val section by remember(model) { derivedStateOf { model.focus.section } }
    // With the sections focused the column is a preview that must end inside the margin (narrow, 640 and up); it widens when an option takes the focus.
    val isPreview by remember(model) { derivedStateOf { model.focus.column == SettingsColumn.Sections } }
    val content = model.content
    Column(
        modifier =
            Modifier
                .width(
                    when {
                        // Three columns: Statistics has no right column, so its page (and its preview) take that room too.
                        section == SettingsSection.Statistics && layout.isSettingsThreeColumns -> statisticsPageWidth(layout)
                        isPreview -> layout.settingsOptionsPreviewWidth
                        // Statistics has no side panel: once entered, its page takes the panel's room too.
                        section == SettingsSection.Statistics -> statisticsPageWidth(layout)
                        else -> layout.settingsOptionsWidth
                    },
                ).fillMaxHeight()
                .testTag(SettingsTags.OPTIONS),
        verticalArrangement = Arrangement.spacedBy(PlumDimens.listRowGap),
    ) {
        val options = section.rowsIn(content)
        // The panel says it on wide screens and on a narrow panel stop; here only on a narrow options column.
        val notice by remember(model, layout) {
            derivedStateOf { model.work.notice?.takeIf { it.first == model.focusedOption && model.focus.column == SettingsColumn.Options && layout.widthClass == WidthClass.Narrow } }
        }
        notice?.let { NoticeLine(it.second, count = model.work.noticeCount) }
        if (content.loadFailed && section == SettingsSection.Library) {
            BasicText(
                stringResource(R.string.settings_library_unreadable),
                style = PlumTheme.type.panelBody.copy(color = PlumTheme.colors.text),
                modifier = Modifier.padding(horizontal = PlumDimens.gameRowPadding).testTag(SettingsTags.LOAD_FAILED),
            )
        }
        if (LocalSettingsStatus.current.saveFailed) {
            // The rows show what is stored; this says the last change did not make it.
            BasicText(
                stringResource(R.string.settings_save_failed),
                style = PlumTheme.type.panelBody.copy(color = PlumTheme.colors.text),
                modifier = Modifier.padding(horizontal = PlumDimens.gameRowPadding).testTag(SettingsTags.SAVE_FAILED),
            )
        }
        if (section == SettingsSection.Statistics) {
            StatisticsPage(model, touch, isPreview, settings, content.hasUsageAccess, Modifier.fillMaxWidth().weight(1f))
            return@Column
        }
        // Keyed by section: the column's scroll and measured rows belong to one section's rows. Kept across a
        // section change, the old spans stood for the new rows and the remembered row was left out of view.
        // The weight is on a box around the column: the column's edge fades share a box with it ([KeepFocusedColumn]).
        key(section) {
            Box(Modifier.weight(1f, fill = false)) {
                KeepFocusedColumn(
                    { model.optionIndex },
                    options.size,
                    PlumDimens.listRowGap,
                    Modifier.testTag(SettingsTags.OPTION_LIST),
                    fadeTag = SettingsTags.OPTION_LIST,
                    endRoom = SlabDimens.listEndRoom,
                    // Only while the options have the focus: with a panel focused the option row is Current, not focused.
                    pillIndex = { if (model.focus.column == SettingsColumn.Options) model.optionIndex else -1 },
                ) { place ->
                    options.forEachIndexed { index, option ->
                        val isSystem = option == SettingsOption.SystemEmulator
                        OptionRowAt(
                            model = model,
                            index = index,
                            option = option,
                            value = optionValue(option, settings, versionName, content, index),
                            label = optionLabel(option, content, index),
                            touch = touch,
                            place = place(index),
                            isSystem = isSystem,
                        )
                    }
                }
            }
        }
    }
}

/** One option row; reads the focus for its own look, so a move recomposes only the rows whose look changes. */
@Composable
private fun OptionRowAt(
    model: SettingsModel,
    index: Int,
    option: SettingsOption,
    value: OptionValue,
    label: String,
    touch: SettingsTouch,
    place: Modifier,
    isSystem: Boolean,
) {
    SideEffect { SettingsRecomposeProbe.optionRows++ }
    val look by remember(model, index) {
        derivedStateOf {
            val focus = model.focus
            when {
                focus.column == SettingsColumn.Sections -> OptionLook.Preview
                index != model.optionIndex -> OptionLook.Normal
                focus.column == SettingsColumn.Panel -> OptionLook.Current
                else -> OptionLook.Focused
            }
        }
    }
    OptionRow(
        option = option,
        value = value,
        look = look,
        modifier = place.tapTarget { touch.onOption(index) },
        // A read-only row's value is no button (TalkBack would announce one that does nothing): a tap on it reaches the row's own target, which focuses.
        valueModifier = if (option.kind == OptionKind.Info) Modifier else Modifier.tapTarget { touch.onValue(index) },
        label = label,
        tag = if (isSystem) SettingsTags.system(index) else SettingsTags.option(option),
        valueTag = if (isSystem) SettingsTags.systemValue(index) else SettingsTags.value(option),
        verticalPadding = if (option == SettingsOption.ConfirmBottom) PlumDimens.settingsTwoLineRowPadding else 0.dp,
    )
}

/** A plain line about what the focused option just tried (errors are never silent). */
@Composable
internal fun NoticeLine(notice: SettingsNotice, modifier: Modifier = Modifier, count: Int = 0) {
    BasicText(
        notice.lineText(count),
        style = PlumTheme.type.panelBody.copy(color = PlumTheme.colors.text),
        modifier = modifier.onRowTextLine().testTag(SettingsTags.NOTICE),
    )
}

/** The section the person settles on is a screen opened (`screen_opened`, settings_<section>); its own scope, so a section change recomposes only this. */
@Composable
private fun TrackSettingsSection(model: SettingsModel) {
    val section by remember(model) { derivedStateOf { model.focus.section } }
    TrackScreen(screenIdOf(section))
}
