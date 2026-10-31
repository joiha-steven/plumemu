package com.plumemu.settings.screen

import androidx.annotation.StringRes
import com.plumemu.R
import com.plumemu.coredata.settings.BoxArtRegionSetting
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ConfirmButtonSetting
import com.plumemu.coredata.settings.FontSetting
import com.plumemu.coredata.settings.InGameSetting
import com.plumemu.coredata.settings.KeyboardSetting
import com.plumemu.coredata.settings.SwitchSystemWithSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.RecentSizeSetting
import com.plumemu.coredata.settings.SortGamesSetting
import com.plumemu.coredata.settings.SoundLevel
import com.plumemu.coredata.settings.SystemOrderSetting
import com.plumemu.coredata.settings.GridSizeSetting
import com.plumemu.coredata.settings.LeftEdgeSetting
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coredata.settings.TextWeightSetting
import com.plumemu.account.AccountStatus
import com.plumemu.coreui.theme.Rgb
import com.plumemu.secondscreen.SecondScreen
import com.plumemu.secondscreen.isSecondScreenOn
import com.plumemu.settings.colorSet
import com.plumemu.settings.colorsCycle
import com.plumemu.settings.withColors

/*
 * What Settings lists, in the alpha only what works: sections in
 * SPEC order, their options, how each option reacts and what its side panel holds. Hidden until
 * they work: artwork options; Switch system and Jump by letter (core-input has no setting for them); Start
 * on, Return here after playing (launching does not read it) and Show Wi-Fi; Sound.
 */

/**
 * A section of the left column, in SPEC order. Sound has nothing that works and is not listed.
 * [options] are the fixed rows; Emulators adds one row per system before them ([rowsIn]).
 */
internal enum class SettingsSection(
    @StringRes val title: Int,
    val options: List<SettingsOption>,
) {
    Library(
        R.string.settings_section_library,
        listOf(SettingsOption.RomFolders, SettingsOption.Rescan, SettingsOption.ScanOnStartup, SettingsOption.SortGames, SettingsOption.SystemOrder),
    ),
    Appearance(
        R.string.settings_section_appearance,
        listOf(SettingsOption.Colors, SettingsOption.Background, SettingsOption.TextColor, SettingsOption.Accent, SettingsOption.Font, SettingsOption.TextSize, SettingsOption.TextWeight, SettingsOption.LeftEdge),
    ),
    Emulators(R.string.settings_section_emulators, listOf(SettingsOption.DetectEmulators)),
    Controls(R.string.settings_section_controls, listOf(SettingsOption.HoldToScrollFaster, SettingsOption.PushRightToPlay, SettingsOption.ShowButtonHints, SettingsOption.SwitchSystemWith, SettingsOption.Keyboard)),
    HomeScreen(
        R.string.settings_section_home_screen,
        listOf(SettingsOption.UseAsHome, SettingsOption.ShowApps, SettingsOption.ShowClock, SettingsOption.ShowBattery, SettingsOption.SystemInfo),
    ),
    /** Read-only figures from the play history, the library and the stored box art ([statisticRowsOf]). */
    Statistics(R.string.settings_section_statistics, emptyList()),
    Advanced(
        R.string.settings_section_advanced,
        listOf(
            SettingsOption.ConfirmBottom,
            SettingsOption.Animations,
            SettingsOption.Sounds,
            SettingsOption.Vibration,
            SettingsOption.Language,
            SettingsOption.UsageStatistics,
            SettingsOption.Backup,
            SettingsOption.Restore,
            SettingsOption.ResetAll,
            SettingsOption.ResetLibrary,
            SettingsOption.ExportLogs,
        ),
    ),
    About(
        R.string.settings_section_about,
        listOf(SettingsOption.Version, SettingsOption.CheckForUpdates, SettingsOption.CheckUpdatesDaily, SettingsOption.Licenses, SettingsOption.Privacy, SettingsOption.ReportProblem),
    ),
    ;

    /**
     * The rows shown for [content]: in Emulators one [SettingsOption.SystemEmulator] per system with
     * games first; in Library Systems after System order (only while a system has games), the ScreenScraper rows ([accountRowsIn]), one
     * [SettingsOption.UnknownFolder] per folder of [SettingsContent.unknownFolders], then "Unidentified games (n)"
     * and "Hidden games (n)", each only while it has games, then Recent size and Clear Recent… last; in Appearance Grid size last, only while signed in to ScreenScraper (the grid
     * needs box art); in Home screen Second screen last, only while there is a second screen, and In
     * game after it only while the switch is on (Settings has no dimmed rows). A build from source has
     * no statistics server and no update manifest: none of the rows that need them is listed
     * ([SettingsContent.isInThisBuild]).
     */
    fun rowsIn(content: SettingsContent): List<SettingsOption> = listedIn(content).filter { content.isInThisBuild(it) }

    private fun listedIn(content: SettingsContent): List<SettingsOption> =
        when {
            this == Emulators -> List(content.systems.size) { SettingsOption.SystemEmulator } + options
            this == Statistics -> List(content.statisticRows.size) { SettingsOption.Statistic }
            this == Library ->
                options + listOfNotNull(SettingsOption.Systems.takeIf { content.librarySystems.isNotEmpty() }) + accountRowsIn(content) +
                    List(content.unknownFolders.size) { SettingsOption.UnknownFolder } +
                    listOfNotNull(
                        SettingsOption.UnidentifiedGames.takeIf { content.unidentifiedGames.isNotEmpty() },
                        SettingsOption.HiddenGames.takeIf { content.hiddenGames.isNotEmpty() },
                    ) +
                    // Recent last, after every row that shows only sometimes.
                    listOf(SettingsOption.RecentSize, SettingsOption.ClearRecent)
            // The grid exists only with box art, which needs the signed-in account (HomeContent.hasBoxArt).
            this == Appearance && content.account is AccountStatus.SignedIn -> options + SettingsOption.GridSize
            // Undo last restore right after Restore…, only while the way-back set exists.
            this == Advanced && content.wayBack != null -> options.flatMap { if (it == SettingsOption.Restore) listOf(it, SettingsOption.UndoRestore) else listOf(it) }
            // "Update available" right under Version, only while a check found one.
            this == About && content.update.found != null -> options.flatMap { if (it == SettingsOption.Version) listOf(it, SettingsOption.UpdateAvailable) else listOf(it) }
            this == HomeScreen && content.secondScreen != null ->
                options + listOfNotNull(SettingsOption.SecondScreen, SettingsOption.SecondScreenInGame.takeIf { content.isSecondScreenOn })
            else -> options
        }
}

/** How an option reacts to A, a tap and left / right. */
internal enum class OptionKind {
    /** ‹ value ›: left / right step through the values, A and a tap go to the next one (wrapping). */
    Choice,

    /** A switch: A and a tap flip it. */
    Switch,

    /** A colour dot and its name: A, right or a tap open the colour panel. */
    Swatch,

    /** "»": A, right or a tap open its panel (a confirm step, the licences). */
    Opens,

    /** A value only (Version) or a page to read (Privacy); on narrow screens A opens its panel to read. */
    Info,

    /** Does something at once (Rescan library, Detect new emulators, Use as home screen); A and a tap run it. */
    Action,
}

/** One row of the options column. */
internal enum class SettingsOption(
    @StringRes val label: Int,
    val kind: OptionKind,
) {
    /**
     * ‹ Plumemu › … ‹ Cream ›, then ‹ Custom › while there is a custom look: a ready-made
     * background, text and accent. The value is derived from those three ([colorSet]), never stored.
     * Left / right step through the sets like every ‹ › row; A and a tap open the preview panel
     * instead of stepping ([PanelPage.ColorSets]).
     */
    Colors(R.string.settings_colors, OptionKind.Choice),
    Background(R.string.settings_background, OptionKind.Swatch),
    TextColor(R.string.settings_text_color, OptionKind.Swatch),
    Accent(R.string.settings_accent, OptionKind.Swatch),
    Font(R.string.settings_font, OptionKind.Choice),
    TextSize(R.string.settings_text_size, OptionKind.Choice),

    /** ‹ Thin › / ‹ Medium › / ‹ Bold ›: how heavy every text role is drawn; not picked is Medium. */
    TextWeight(R.string.settings_text_weight, OptionKind.Choice),

    /** ‹ Game counts › / ‹ System codes › / ‹ Letters ›: what the home screen's peek shows while a games list is focused; not picked is Game counts. */
    LeftEdge(R.string.settings_left_edge, OptionKind.Choice),

    /** ‹ Small › / ‹ Medium › / ‹ Large ›: the main screen's grid, listed only while it has box art (the grid exists only then). */
    GridSize(R.string.settings_grid_size, OptionKind.Choice),
    ConfirmBottom(R.string.settings_confirm_bottom, OptionKind.Switch),
    Animations(R.string.settings_animations, OptionKind.Switch),
    /** ‹ Off › / ‹ Quiet › / ‹ Loud ›: the interface sounds' volume ([SoundLevel]). */
    Sounds(R.string.settings_sounds, OptionKind.Choice),

    /** A light haptic tick on every move and a click on confirm (on by default). */
    Vibration(R.string.settings_vibration, OptionKind.Switch),
    UsageStatistics(R.string.settings_usage_statistics, OptionKind.Switch),
    /** "Back up…": its panel chooses Settings or Everything, then Android's save picker. */
    Backup(R.string.settings_backup, OptionKind.Opens),

    /** "Restore…": its panel opens Android's open picker, checks the file, then asks before replacing. */
    Restore(R.string.settings_restore, OptionKind.Opens),

    /** "Undo last restore", listed only while the way-back set exists; its value is the space the set keeps. */
    UndoRestore(R.string.settings_undo_restore, OptionKind.Opens),
    ResetAll(R.string.settings_reset_all, OptionKind.Opens),
    ResetLibrary(R.string.settings_reset_library, OptionKind.Opens),

    /** Shares the app's log file with Android's share sheet. */
    ExportLogs(R.string.settings_export_logs, OptionKind.Action),
    Version(R.string.settings_version, OptionKind.Info),

    /** Listed under Version while a check found a newer release; its panel has the notes and Update. */
    UpdateAvailable(R.string.settings_update_available, OptionKind.Opens),

    /** Checks now, whatever the switch and the last check say; its value is the last check's result and time. */
    CheckForUpdates(R.string.settings_check_updates, OptionKind.Action),
    CheckUpdatesDaily(R.string.settings_check_updates_daily, OptionKind.Switch),
    Licenses(R.string.settings_licenses, OptionKind.Opens),
    Privacy(R.string.settings_privacy, OptionKind.Info),

    /** About › Report a problem: its panel is the report form ([SettingsProblemReport]). */
    ReportProblem(R.string.settings_report_problem, OptionKind.Opens),

    /**
     * "English »": A, right or a tap open the language list ([PanelPage.Language]); left and right never change the
     * language (one slip landed in a language the person could not read).
     */
    Language(R.string.settings_language, OptionKind.Opens),
    RomFolders(R.string.settings_rom_folders, OptionKind.Opens),
    Rescan(R.string.settings_rescan, OptionKind.Action),
    ScanOnStartup(R.string.settings_scan_on_startup, OptionKind.Switch),
    SortGames(R.string.settings_sort_games, OptionKind.Choice),

    /** ‹ 10 / 20 / 50 ›: how many games Recent keeps. */
    RecentSize(R.string.settings_recent_size, OptionKind.Choice),

    /** "»": Clear Recent…'s confirm step. */
    ClearRecent(R.string.settings_clear_recent, OptionKind.Opens),

    /** ‹ A–Z › / ‹ Maker & year › / ‹ Manual ›: how the main screen's system column is arranged. */
    SystemOrder(R.string.settings_system_order, OptionKind.Choice),

    /** "16 shown »": every system with games, to arrange (A picks one up) and hide (Y). Listed while a system has games. */
    Systems(R.string.settings_systems, OptionKind.Opens),

    /**
     * One per folder in [SettingsContent.unknownFolders] ("Unknown folder: Xbox 360", later "Xbox 360 · Xbox"); the row's
     * folder is the one at its place among these rows ([SettingsContent.unknownFolderAtRow]).
     */
    UnknownFolder(R.string.settings_unknown_folder_title, OptionKind.Opens),

    /** Listed only while games are named from their file alone; A or a tap on one opens Fix name…. */
    UnidentifiedGames(R.string.settings_unidentified_games_title, OptionKind.Opens),

    /** Listed only while games are hidden; its panel lists them, A or a tap shows one again. */
    HiddenGames(R.string.settings_hidden_games_title, OptionKind.Opens),

    /** One per system with games (its name is the label); the row's index is the system's. */
    SystemEmulator(R.string.settings_section_emulators, OptionKind.Opens),
    DetectEmulators(R.string.settings_detect_emulators, OptionKind.Action),
    HoldToScrollFaster(R.string.settings_hold_to_scroll, OptionKind.Switch),
    PushRightToPlay(R.string.settings_push_right, OptionKind.Switch),
    ShowButtonHints(R.string.settings_show_hints, OptionKind.Switch),

    /** ‹ L2 / R2 › / ‹ L1 / R1 ›: which shoulder pair switches system on the main screen. */
    SwitchSystemWith(R.string.settings_switch_system_with, OptionKind.Choice),

    /** ‹ Plumemu › / ‹ Android ›: which on-screen keyboard text fields type with. */
    Keyboard(R.string.settings_keyboard, OptionKind.Choice),
    UseAsHome(R.string.settings_use_as_home, OptionKind.Action),
    ShowApps(R.string.settings_show_apps, OptionKind.Switch),
    ShowClock(R.string.settings_clock, OptionKind.Switch),
    ShowBattery(R.string.settings_show_battery, OptionKind.Switch),
    SystemInfo(R.string.settings_system_info, OptionKind.Switch),

    /** Listed only while there is a second screen; shows [isSecondScreenOn], A or a tap stores the other value. */
    SecondScreen(R.string.settings_second_screen, OptionKind.Switch),

    /** Listed after [SecondScreen] while the switch is on; hidden while it is off (Settings has no dimmed rows). */
    SecondScreenInGame(R.string.settings_second_screen_in_game, OptionKind.Choice),

    /**
     * "ScreenScraper", signed in or out (the label stays put): its value is "Sign in »" while signed
     * out, else the box art's short state or the name (`optionValue`); its panel signs in, or shows
     * the status and the box art actions.
     */
    ScraperAccount(R.string.settings_account_title, OptionKind.Opens),

    /** "Box art: not available in this build" ([boxArtLineOf] is its label); the only row in a build without credentials. */
    ScraperStatus(R.string.settings_account_title, OptionKind.Info),

    /** Whose box to show when the game's own region has none; listed only in a build that fetches box art. */
    BoxArtRegion(R.string.settings_box_art_region, OptionKind.Choice),

    /** One figure of Statistics; the row's index is its [StatisticRow] in [SettingsContent.statisticRows]. */
    Statistic(R.string.settings_section_statistics, OptionKind.Info),
}

/**
 * Whether A on this row opens a page or a sign-in ("Open" in the hint bar) rather than changing a
 * value: every "»" row except a system's emulator, whose panel is a choice like Colors' and the
 * colour dots' ("Change").
 */
internal val SettingsOption.opensPage: Boolean
    get() = kind == OptionKind.Opens && this != SettingsOption.SystemEmulator

/**
 * What A does on [option]'s row, for the hint bar: read-only rows (Version, Privacy, Statistics,
 * the box art status) do nothing, on a narrow screen too, where "› More" (the Right key) opens
 * their explanation and A does not; an action "Select"; a page "Open"; anything else "Change".
 */
@StringRes
internal fun rowConfirmHint(option: SettingsOption): Int? =
    when {
        option.kind == OptionKind.Info -> null
        option.kind == OptionKind.Action -> R.string.settings_hint_select
        option.opensPage -> R.string.settings_hint_open
        else -> R.string.settings_hint_change
    }

/** The panel pages the focus can enter; every other option's panel is only read. */
internal enum class PanelPage {
    /** Appearance › Colors: a preview tile per set (and Custom), A uses one ([colorSetsPanelEntries]). */
    ColorSets,
    BackgroundPresets,
    BackgroundCustom,
    AccentPresets,
    AccentCustom,
    TextPresets,
    TextCustom,

    /** Cancel and Reset, for Reset all settings and Reset library. */
    Confirm,

    /** Advanced › Language: System default, then each language in its own name; A applies ([languagePanelIndex], [languagePanelTag]). */
    Language,

    /** Advanced › Back up…: Settings and Everything, or Cancel while a backup runs ([backupPanelItems]). */
    Backup,

    /** Advanced › Restore…: Choose file…, Cancel while reading, Restore and Cancel at the confirm ([restorePanelItems]). */
    Restore,

    /** Advanced › Undo last restore: Undo the restore and Forget the copy ([undoPanelItems]). */
    UndoRestore,

    /** About › Update available: Update, or the step's own buttons ([updatePanelItems]); none while it installs. */
    Update,
    Licenses,

    /** One licence's text; up and down scroll it. */
    LicenseText,

    /** Nothing to focus: the explanation of an option, opened as a stop on narrow screens. */
    ReadOnly,

    /** Library › ROM folders: each folder, then "+ Add folder". */
    Folders,

    /** A folder's page ([folderPageItems]): Skip a sub-folder…, its skipped sub-folders (A reads one again), Remove this folder…; about [SettingsFocus.folderId]. */
    Folder,

    /** Skip a sub-folder…: the folder's sub-folders read from disk ([pickableSubfolders]); A skips one. */
    SkipSubfolder,

    /** Cancel and Remove for the folder in [SettingsFocus.folderId]. */
    RemoveFolder,

    /** A system's emulators: "Automatic", then each installed one. */
    Emulators,

    /** Library › Hidden games: each hidden game; A or a tap shows it again. */
    HiddenGames,

    /** Library › Unidentified games: each game; A or a tap opens Fix name… for it. */
    UnidentifiedGames,

    /** Fix name… for one unidentified game ([SettingsNameFix.open]); its field takes the actions. */
    FixName,

    /** Library › ScreenScraper while signed out: the sign-in form ([SettingsSignIn.form]); it takes the actions. */
    ScraperSignIn,

    /** About › Report a problem: the form ([SettingsProblemReport.form]); it takes the actions. */
    ProblemReport,

    /**
     * Library › ScreenScraper: Sign in again and Sign out after a refusal; otherwise the box art
     * actions ([accountPageItems]) and Sign out.
     */
    ScraperAccount,

    /** Library › ScreenScraper › Choose systems…: each system (A checks it), then the two actions ([SettingsFocus.chosenSystems]). */
    ScraperSystems,

    /** Re-download's confirm: Re-download, then Cancel (focused first); about [SettingsFocus.redownloadSystems]. */
    ScraperConfirm,

    /** Sign out's confirm: Sign out, then Cancel (focused first). */
    ScraperSignOutConfirm,

    /** Clear box art's confirm: Clear box art, then Cancel (focused first). */
    ScraperClearConfirm,

    /** Library › Systems: every system with games in the column's order; A carries one, Y hides it ([SettingsSystems]). */
    Systems,

    /** Library › an unknown folder: Ignore this folder, then every system; A applies ([SettingsFocus.folderKey]). */
    UnknownFolder,
}

/** Value of a ‹ › option, for the row and for stepping. */
internal fun SettingsOption.choiceIndex(
    settings: PlumSettings,
    defaultTextSize: TextSizeSetting,
): Int =
    when (this) {
        SettingsOption.Colors -> settings.appearance.let { it.colorsCycle().indexOf(it.colorSet()) }
        SettingsOption.Font -> settings.appearance.font.ordinal
        SettingsOption.TextSize -> (settings.appearance.textSize ?: defaultTextSize).ordinal
        SettingsOption.TextWeight -> (settings.appearance.textWeight ?: TextWeightSetting.Default).ordinal
        SettingsOption.LeftEdge -> (settings.appearance.leftEdge ?: LeftEdgeSetting.Default).ordinal
        SettingsOption.GridSize -> settings.appearance.gridSize.ordinal
        SettingsOption.SortGames -> settings.library.sortGames.ordinal
        SettingsOption.RecentSize -> settings.library.recentSize.ordinal
        SettingsOption.Keyboard -> settings.controls.keyboard.ordinal
        SettingsOption.SwitchSystemWith -> settings.controls.switchSystemWith.ordinal
        SettingsOption.SystemOrder -> settings.library.systemOrder.ordinal
        SettingsOption.SecondScreenInGame -> settings.secondScreen.inGame.ordinal
        SettingsOption.BoxArtRegion -> settings.library.boxArtRegion.ordinal
        SettingsOption.Sounds -> settings.advanced.sounds.ordinal
        else -> 0
    }

internal fun SettingsOption.choiceCount(settings: PlumSettings): Int =
    when (this) {
        // Custom is a value only while there is a custom look to be on or go back to.
        SettingsOption.Colors -> settings.appearance.colorsCycle().size
        SettingsOption.Font -> FontSetting.entries.size
        SettingsOption.TextSize -> TextSizeSetting.entries.size
        SettingsOption.TextWeight -> TextWeightSetting.entries.size
        SettingsOption.LeftEdge -> LeftEdgeSetting.entries.size
        SettingsOption.GridSize -> GridSizeSetting.entries.size
        SettingsOption.SortGames -> SortGamesSetting.entries.size
        SettingsOption.RecentSize -> RecentSizeSetting.entries.size
        SettingsOption.Keyboard -> KeyboardSetting.entries.size
        SettingsOption.SwitchSystemWith -> SwitchSystemWithSetting.entries.size
        SettingsOption.SystemOrder -> SystemOrderSetting.entries.size
        SettingsOption.SecondScreenInGame -> InGameSetting.entries.size
        SettingsOption.BoxArtRegion -> BoxArtRegionSetting.entries.size
        SettingsOption.Sounds -> SoundLevel.entries.size
        else -> 1
    }

/** [settings] with this ‹ › option at value [index]. */
internal fun SettingsOption.withChoice(
    settings: PlumSettings,
    index: Int,
): PlumSettings =
    when (this) {
        SettingsOption.SortGames -> settings.copy(library = settings.library.copy(sortGames = SortGamesSetting.entries[index]))
        SettingsOption.RecentSize -> settings.copy(library = settings.library.copy(recentSize = RecentSizeSetting.entries[index]))
        SettingsOption.Keyboard -> settings.copy(controls = settings.controls.copy(keyboard = KeyboardSetting.entries[index]))
        SettingsOption.SwitchSystemWith -> settings.copy(controls = settings.controls.copy(switchSystemWith = SwitchSystemWithSetting.entries[index]))
        // The manual list stays when the order moves away: coming back to Manual finds it.
        SettingsOption.SystemOrder -> settings.copy(library = settings.library.copy(systemOrder = SystemOrderSetting.entries[index]))
        SettingsOption.SecondScreenInGame -> settings.copy(secondScreen = settings.secondScreen.copy(inGame = InGameSetting.entries[index]))
        SettingsOption.BoxArtRegion -> settings.copy(library = settings.library.copy(boxArtRegion = BoxArtRegionSetting.entries[index]))
        SettingsOption.Sounds -> settings.copy(advanced = settings.advanced.copy(sounds = SoundLevel.entries[index]))
        else -> withAppearanceChoice(settings, index)
    }

private fun SettingsOption.withAppearanceChoice(
    settings: PlumSettings,
    index: Int,
): PlumSettings =
    settings.withAppearance {
        when (this@withAppearanceChoice) {
            SettingsOption.Colors -> it.withColors(it.colorsCycle()[index])
            SettingsOption.Font -> it.copy(font = FontSetting.entries[index])
            SettingsOption.TextSize -> it.copy(textSize = TextSizeSetting.entries[index])
            SettingsOption.TextWeight -> it.copy(textWeight = TextWeightSetting.entries[index])
            SettingsOption.LeftEdge -> it.copy(leftEdge = LeftEdgeSetting.entries[index])
            SettingsOption.GridSize -> it.copy(gridSize = GridSizeSetting.entries[index])
            else -> it
        }
    }

internal fun SettingsOption.switchValue(settings: PlumSettings): Boolean =
    when (this) {
        SettingsOption.ConfirmBottom -> settings.controls.confirmButton == ConfirmButtonSetting.Bottom
        SettingsOption.Animations -> settings.advanced.animations
        SettingsOption.Vibration -> settings.advanced.vibration
        SettingsOption.UsageStatistics -> settings.advanced.usageStatistics
        SettingsOption.CheckUpdatesDaily -> settings.updates.checkDaily
        SettingsOption.ScanOnStartup -> settings.library.scanOnStartup
        SettingsOption.HoldToScrollFaster -> settings.controls.holdToScrollFaster
        SettingsOption.PushRightToPlay -> settings.controls.pushRightToPlay
        SettingsOption.ShowButtonHints -> settings.controls.showButtonHints
        SettingsOption.ShowApps -> settings.homeScreen.showApps
        SettingsOption.ShowClock -> settings.homeScreen.showClock
        SettingsOption.ShowBattery -> settings.homeScreen.showBattery
        SettingsOption.SystemInfo -> settings.homeScreen.isSystemInfoShown
        else -> false
    }

/** [settings] with this switch flipped. */
internal fun SettingsOption.toggled(settings: PlumSettings): PlumSettings =
    when (this) {
        SettingsOption.ConfirmBottom -> {
            val next = if (settings.controls.confirmButton == ConfirmButtonSetting.Bottom) ConfirmButtonSetting.Right else ConfirmButtonSetting.Bottom
            settings.copy(controls = settings.controls.copy(confirmButton = next))
        }
        SettingsOption.Animations -> settings.copy(advanced = settings.advanced.copy(animations = !settings.advanced.animations))
        SettingsOption.Vibration -> settings.copy(advanced = settings.advanced.copy(vibration = !settings.advanced.vibration))
        SettingsOption.UsageStatistics -> settings.copy(advanced = settings.advanced.copy(usageStatistics = !settings.advanced.usageStatistics))
        SettingsOption.CheckUpdatesDaily -> settings.copy(updates = settings.updates.copy(checkDaily = !settings.updates.checkDaily))
        SettingsOption.ScanOnStartup -> settings.copy(library = settings.library.copy(scanOnStartup = !settings.library.scanOnStartup))
        SettingsOption.HoldToScrollFaster ->
            settings.copy(controls = settings.controls.copy(holdToScrollFaster = !settings.controls.holdToScrollFaster))
        SettingsOption.PushRightToPlay -> settings.copy(controls = settings.controls.copy(pushRightToPlay = !settings.controls.pushRightToPlay))
        SettingsOption.ShowButtonHints -> settings.copy(controls = settings.controls.copy(showButtonHints = !settings.controls.showButtonHints))
        SettingsOption.ShowApps -> settings.copy(homeScreen = settings.homeScreen.copy(showApps = !settings.homeScreen.showApps))
        SettingsOption.ShowClock -> settings.copy(homeScreen = settings.homeScreen.copy(showClock = !settings.homeScreen.showClock))
        SettingsOption.ShowBattery -> settings.copy(homeScreen = settings.homeScreen.copy(showBattery = !settings.homeScreen.showBattery))
        SettingsOption.SystemInfo -> settings.copy(homeScreen = settings.homeScreen.copy(showSystemInfo = !settings.homeScreen.isSystemInfoShown))
        else -> settings
    }

/**
 * [settings] with the Second screen switch showing the other value: the stored choice, or (never
 * decided) the default for [screen]'s kind, flipped and stored, so the person's choice holds from then on.
 */
internal fun PlumSettings.withSecondScreenFlipped(screen: SecondScreen?): PlumSettings =
    copy(secondScreen = secondScreen.copy(enabled = !isSecondScreenOn(secondScreen, screen)))

internal inline fun PlumSettings.withAppearance(change: (AppearanceSettings) -> AppearanceSettings): PlumSettings =
    copy(appearance = change(appearance))

/**
 * Reset all settings (the only reset): every setting back to its default; the setup
 * flags are kept, so first-run setup and the home screen question do not come back. The library
 * lives in the database and is not touched.
 */
// Buttons None, not undecided: the defaults have none, and "undecided" would be settled from the colours only at the next start.
internal fun PlumSettings.resetKeepingFlags(): PlumSettings = PlumSettings(appearance = AppearanceSettings(buttonSet = AppearanceSettings.BUTTONS_NONE), flags = flags)

/** "#1a2b3c", "1A2B3C" → the colour; anything else → null. */
internal fun parseHexColor(text: String): Rgb? = Rgb.fromHexOrNull("#" + text.trim().removePrefix("#").uppercase())

/** The name of Left edge's value [index] (`LeftEdgeSetting.entries` order). */
internal fun leftEdgeNameRes(index: Int): Int =
    when (LeftEdgeSetting.entries[index]) {
        LeftEdgeSetting.Counts -> R.string.settings_left_edge_counts
        LeftEdgeSetting.Codes -> R.string.settings_left_edge_codes
        LeftEdgeSetting.Letters -> R.string.settings_left_edge_letters
    }
