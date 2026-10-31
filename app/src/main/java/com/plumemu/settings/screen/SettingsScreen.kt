package com.plumemu.settings.screen

import androidx.compose.animation.core.Animatable
import com.plumemu.usage.TrackScreen
import com.plumemu.usage.screenIdOf
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
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
import com.plumemu.coreui.components.SystemRow
import com.plumemu.coreui.components.SystemRowLook
import com.plumemu.coreui.components.TopBar
import com.plumemu.coreui.components.InsetFocusPillShape
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumLayout
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.WidthClass
import com.plumemu.game.GameActions
import com.plumemu.game.GamePanelOverlay
import com.plumemu.game.OpenPanel
import com.plumemu.game.rememberGameMenuController
import com.plumemu.telemetry.LaunchFrom
import com.plumemu.home.rememberBatteryCharging
import com.plumemu.home.rememberBatteryReading
import com.plumemu.home.rememberBatteryText
import com.plumemu.home.rememberClockText
import com.plumemu.home.rememberWifiConnected
import com.plumemu.sound.LocalUiSounds
import com.plumemu.sound.HearFeel
import com.plumemu.sound.Outcome
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
    /** Plays a most-played game from Statistics (A or a tap on its row); its failures show as the main screen's do. */
    games: GameActions = GameActions.None,
) {
    val model = remember(actions) { SettingsModel(actions) }
    val menu = rememberGameMenuController(games)
    SideEffect { model.playGame = { menu.play(it, LaunchFrom.Statistics) } }
    val sounds = LocalUiSounds.current
    HearFeel(model.feel, menu.feel)
    TrackSettingsSection(model)
    DisposableEffect(model) { onDispose { model.onDispose() } }
    // Statistics' figures are read once per opening, after the first frame; content given with them (tests) keeps its own.
    val statistics = rememberStatistics(actions.statistics, content.isScanning, status.libraryReset)
    SideEffect { model.content = if (content.statistics == StatisticsState.NotRead) content.copy(statistics = statistics) else content }
    val shownStatistics = if (content.statistics == StatisticsState.NotRead) statistics else content.statistics
    val currentOnStatistics by rememberUpdatedState(onStatistics)
    LaunchedEffect(shownStatistics) { currentOnStatistics(shownStatistics) }
    val geometry = settingsGeometry(PlumTheme.layout)
    val currentSettings by rememberUpdatedState(settings)
    val currentGeometry by rememberUpdatedState(geometry)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(router, model, menu, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            router.actions.collect {
                // A launch message (file gone, no emulator…) is the game menu's panel: while it is open it takes every action.
                if (!menu.onAction(it)) model.onAction(it, currentSettings, currentGeometry)
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
        message = menu.panel, onMessageAction = router::send, charging = { charging?.value == true },
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
    if (layout.widthClass == WidthClass.Wide) PlumDimens.settingsPanelWidth else layout.settingsOptionsWidth

/**
 * Where the strip rests for [column] (strip coordinates, as on the board): sections at 0; options
 * with margin and sections slid fully away (−224); the narrow panel stop one options column and
 * gap further. The wide panel sits beside the options, so the strip stays.
 */
internal fun settingsStripOffset(
    column: SettingsColumn,
    layout: PlumLayout,
): Dp {
    val toOptions = PlumDimens.stripMarginSide + PlumDimens.settingsSectionsWidth
    return when {
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
    /** The game menu's panel while a launch message shows (Statistics' Play), drawn over the strip. */
    message: OpenPanel? = null,
    onMessageAction: (Action) -> Unit = {},
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
                .padding(top = PlumDimens.stripMarginTop, bottom = PlumDimens.stripMarginBottom)
                .testTag(SettingsTags.SCREEN),
        verticalArrangement = Arrangement.spacedBy(PlumDimens.barGap),
    ) {
        SettingsTopBar(model, clock, battery, wifi, charging)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            SettingsStrip(settings, model, touch, versionName, Modifier.fillMaxSize())
            // Plumemu's keyboard for the focused page's field, down to the screen's bottom; the hint bar is drawn on it.
            SettingsKeyboardTray(model, belowContent = settingsTrayBelowStrip())
            GamePanelOverlay(message, onMessageAction)
        }
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
        model.focusedOption?.let {
            // The account rows' labels carry a name or a whole sentence, an unknown folder's its own name: the crumb names the place only.
            when (it) {
                SettingsOption.ScraperAccount, SettingsOption.ScraperStatus -> stringResource(R.string.settings_account_title)
                SettingsOption.UnknownFolder -> stringResource(R.string.settings_unknown_folder_title)
                else -> optionLabel(it, model.content, model.optionIndex)
            }
        }
    val place =
        when {
            focus.column == SettingsColumn.Sections -> title
            focus.column == SettingsColumn.Panel && option != null ->
                stringResource(R.string.settings_crumb, stringResource(R.string.settings_crumb, title, section), option)
            else -> stringResource(R.string.settings_crumb, title, section)
        }
    TopBar(place = place, emphasis = title, clock = clock, battery = battery, wifi = wifi, wifiDescription = stringResource(R.string.wifi_connected),
        charging = charging, chargingDescription = stringResource(R.string.battery_charging),
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
    val density = LocalDensity.current
    val targetPx = with(density) { settingsStripOffset(column, layout).toPx() }
    val offset = remember { Animatable(targetPx) }
    val spec = PlumTheme.motion.slideSpec<Float>()
    val isMotionOn = PlumTheme.motion.isOn
    // Transform only: 180 ms ease-out, or a jump when motion is off.
    LaunchedEffect(targetPx, isMotionOn) { if (isMotionOn) offset.animateTo(targetPx, spec) else offset.snapTo(targetPx) }
    Box(modifier.clipToBounds().testTag(SettingsTags.STRIP)) {
        Row(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .wrapContentWidth(align = Alignment.Start, unbounded = true)
                    .graphicsLayer { translationX = offset.value }
                    // No start padding: the sections column starts at the screen edge and holds the margin itself.
                    .padding(end = PlumDimens.stripMarginSide),
            horizontalArrangement = Arrangement.spacedBy(PlumDimens.columnGap),
        ) {
            SectionsColumn(model, touch)
            OptionsColumn(settings, model, touch, versionName)
            val panelVisible = column == SettingsColumn.Panel || (column == SettingsColumn.Options && layout.widthClass == WidthClass.Wide)
            Box(Modifier.width(settingsPanelWidth(layout)).fillMaxHeight().testTag(SettingsTags.PANEL)) {
                if (panelVisible) PanelHost(settings, model, touch, versionName)
            }
        }
        // Content runs past the right edge only while the sections show.
        if (column == SettingsColumn.Sections) EdgeFade(Modifier.align(Alignment.CenterEnd))
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
    val option = model.focusedOption ?: return
    // Keyed by the row too: each system of Emulators is the same option with its own panel.
    FadeIn(option to model.optionIndex, Modifier.fillMaxSize()) { SettingsPanel(option, settings, model, touch, versionName) }
}

@Composable
private fun SectionsColumn(
    model: SettingsModel,
    touch: SettingsTouch,
) {
    // Scrolls to the focused section: at the larger text sizes the seven rows do not all fit the strip.
    KeepFocusedColumn(
        { model.focus.section.ordinal },
        SettingsSection.entries.size,
        0.dp,
        Modifier.width(PlumDimens.stripMarginSide + PlumDimens.settingsSectionsWidth).fillMaxHeight().testTag(SettingsTags.SECTIONS),
        fadeTag = SettingsTags.SECTIONS,
        // Only while the sections have the focus: a Current section (focus in the options) keeps its own quiet look.
        pillIndex = { if (model.focus.column == SettingsColumn.Sections) model.focus.section.ordinal else -1 },
        // The rows draw their pill from [PlumDimens.gameRowPadding] before the label (SystemRow's bleedStart); the gliding one does the same.
        pillShape = remember { InsetFocusPillShape((PlumDimens.stripMarginSide - PlumDimens.gameRowPadding).coerceAtLeast(0.dp)) },
    ) { place ->
        for (section in SettingsSection.entries) SectionRow(section, model, touch, place(section.ordinal))
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
    SystemRow(
        name = stringResource(section.title),
        count = "",
        look = look,
        showName = true,
        bleedStart = PlumDimens.stripMarginSide,
        // As high as an option row (not the systems' 40): the two columns' text lines then share a centre row by row, where the
        // 4 dp between 40 and 44 at Large put the options' first text line 2 dp lower. The outer height wins over SystemRow's own.
        modifier = place.height(PlumTheme.layout.rowHeight).tapTarget { touch.onSection(section) }.testTag(SettingsTags.section(section)),
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
        modifier = Modifier.width(if (isPreview) layout.settingsOptionsPreviewWidth else layout.settingsOptionsWidth).fillMaxHeight().testTag(SettingsTags.OPTIONS),
        verticalArrangement = Arrangement.spacedBy(PlumDimens.listRowGap),
    ) {
        val options = section.rowsIn(content)
        // The panel says it on wide screens and on a narrow panel stop; here only on a narrow options column.
        val notice by remember(model, layout) {
            derivedStateOf { model.work.notice?.takeIf { it.first == model.focusedOption && model.focus.column == SettingsColumn.Options && layout.widthClass == WidthClass.Narrow } }
        }
        notice?.let { NoticeLine(it.second, count = model.work.noticeCount) }
        if (content.loadFailed && (section == SettingsSection.Library || section == SettingsSection.Emulators)) {
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
        // Keyed by section: the column's scroll and measured rows belong to one section's rows. Kept across a
        // section change, the old spans stood for the new rows and the remembered row was left out of view.
        key(section) {
            KeepFocusedColumn(
                { model.optionIndex },
                options.size,
                PlumDimens.listRowGap,
                Modifier.weight(1f, fill = false).testTag(SettingsTags.OPTION_LIST),
                // Only while the options have the focus: with a panel focused the option row is Current, not focused.
                pillIndex = { if (model.focus.column == SettingsColumn.Options) model.optionIndex else -1 },
            ) { place ->
                // Statistics: one text line for every row, so the cover slot is kept in rows without a cover.
                val pageHasCovers = content.statisticRows.any { it is StatisticRow.Game && it.showsCover }
                options.forEachIndexed { index, option ->
                    val isSystem = option == SettingsOption.SystemEmulator
                    val unknown = if (option == SettingsOption.UnknownFolder) content.unknownFolderAtRow(index) else null
                    val statistic = if (option == SettingsOption.Statistic) content.statisticRows.getOrNull(index) else null
                    if (statistic is StatisticRow.Game && statistic.rank == 1) StatisticGroupHeading(stringResource(R.string.settings_statistics_most_played), pageHasCovers)
                    OptionRowAt(
                        model = model,
                        index = index,
                        option = option,
                        value = optionValue(option, settings, versionName, content, index, model.work.isDetecting(content)),
                        label = optionLabel(option, content, index),
                        touch = touch,
                        place = place(index),
                        isSystem = isSystem,
                        unknown = unknown,
                        statistic = statistic,
                        pageHasCovers = pageHasCovers,
                    )
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
    unknown: SettingsFolderRow? = null,
    statistic: StatisticRow? = null,
    pageHasCovers: Boolean = false,
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
        valueModifier = if (option.kind == OptionKind.Info && statistic !is StatisticRow.Game) Modifier else Modifier.tapTarget { touch.onValue(index) },
        label = label,
        tag = if (unknown != null) SettingsTags.unknownFolder(unknown.key) else if (isSystem) SettingsTags.system(index) else if (statistic != null) SettingsTags.statistic(index) else SettingsTags.option(option),
        valueTag = if (unknown != null) SettingsTags.unknownFolderValue(unknown.key) else if (isSystem) SettingsTags.systemValue(index) else if (statistic != null) SettingsTags.statisticValue(index) else SettingsTags.value(option),
        detail = statisticDetail(statistic),
        leading = (statistic as? StatisticRow.Game)?.takeIf { it.showsCover }?.let { game -> { StatisticCover(game.game.cover, index) } },
        labelLines = if (statistic is StatisticRow.Game || unknown != null) GAME_TITLE_LINES else 2,
        keepsLeadingRoom = statistic != null && pageHasCovers,
        verticalPadding = if (statistic != null) PlumDimens.factsRowGap else if (option == SettingsOption.ConfirmBottom) PlumDimens.settingsTwoLineRowPadding else 0.dp,
        headline = if (statistic is StatisticRow.Total) value.let { (it as? OptionValue.Text)?.text } else null,
        textTag = if (statistic != null) SettingsTags.statisticText(index) else null,
    )
}

/** A plain line about what the focused option just tried (errors are never silent). */
@Composable
internal fun NoticeLine(
    notice: SettingsNotice,
    modifier: Modifier = Modifier,
    count: Int = 0,
) {
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
