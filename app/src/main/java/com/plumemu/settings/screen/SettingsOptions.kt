package com.plumemu.settings.screen

import androidx.annotation.StringRes
import com.plumemu.R
import com.plumemu.coredata.settings.BoxArtRegionSetting
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ConfirmButtonSetting
import com.plumemu.coredata.settings.InGameSetting
import com.plumemu.coredata.settings.KeyboardSetting
import com.plumemu.coredata.settings.SwitchSystemWithSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.game.SortChoices
import com.plumemu.coredata.settings.SoundLevel
import com.plumemu.coredata.settings.SystemOrderSetting
import com.plumemu.coredata.settings.GridSizeSetting
import com.plumemu.coredata.settings.LeftEdgeSetting
import com.plumemu.coredata.settings.TextSizeSetting
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
 * [options] are the fixed rows; Emulators has none, only one row per system ([rowsIn]). Second
 * screen is listed only on a device with one, Emulators only while a system has games ([shownIn]).
 */
internal enum class SettingsSection(
    @StringRes val title: Int,
    val options: List<SettingsOption>,
) {
    Library(
        R.string.settings_section_library,
        listOf(SettingsOption.RomFolders, SettingsOption.Rescan, SettingsOption.SortGames),
    ),
    Appearance(
        R.string.settings_section_appearance,
        listOf(SettingsOption.Language, SettingsOption.Colors, SettingsOption.Accent, SettingsOption.TextSize),
    ),
    HomeScreen(
        R.string.settings_section_home_screen,
        listOf(SettingsOption.UseAsHome, SettingsOption.ShowApps, SettingsOption.ClockAndBattery, SettingsOption.SystemInfo, SettingsOption.LeftEdge),
    ),
    /** Listed only while there is a second screen; In game only while it is in use (Settings has no dimmed rows). */
    SecondScreen(R.string.settings_section_second_screen, listOf(SettingsOption.SecondScreen)),
    Emulators(R.string.settings_section_emulators, emptyList()),
    Controls(
        R.string.settings_section_controls,
        listOf(
            SettingsOption.ConfirmBottom,
            SettingsOption.SwitchSystemWith,
            SettingsOption.PushRightToPlay,
            SettingsOption.ShowButtonHints,
            SettingsOption.Keyboard,
            SettingsOption.Sounds,
            SettingsOption.Vibration,
        ),
    ),
    /** Read-only figures from the play history, the library and the stored box art, on one page ([StatisticsPage]). */
    Statistics(R.string.settings_section_statistics, emptyList()),
    /** ScreenScraper (box art, with its region inside its panel) and RetroAchievements: the person's accounts with outside services ([accountRowsIn]). */
    Accounts(R.string.settings_section_accounts, listOf(SettingsOption.RetroAchievementsAccount)),
    Advanced(
        R.string.settings_section_advanced,
        listOf(
            SettingsOption.Animations,
            SettingsOption.BackupAndRestore,
            SettingsOption.Reset,
            SettingsOption.ExportLogs,
        ),
    ),
    About(
        R.string.settings_section_about,
        listOf(
            SettingsOption.Version,
            SettingsOption.Updates,
            SettingsOption.UsageStatistics,
            SettingsOption.Licenses,
            SettingsOption.Privacy,
            SettingsOption.ReportProblem,
        ),
    ),
    ;

    /**
     * The rows shown for [content]: in Emulators one [SettingsOption.SystemEmulator] per system with
     * games; in Controls Vibration only on a device with a vibrator; in Home screen Grid size only while Show button hints is off; in Library "Folder could not be read" after Rescan library (only while a scan could not read a sub-folder), Systems after Sort games (only while a system has games), Files not added, then "Unidentified games (n)"
     * and "Hidden games (n)", each only while it has games, then Clear Recent… last; in Second screen In
     * game after the switch only while it is on (Settings has no dimmed rows); nothing in a section that is not [shownIn]. A build from source has
     * no statistics server and no update manifest: none of the rows that need them is listed, and Export logs,
     * which Report a problem's form holds elsewhere, is listed only there ([SettingsContent.isInThisBuild]).
     */
    fun rowsIn(content: SettingsContent): List<SettingsOption> = listedIn(content).filter { content.isInThisBuild(it) }

    private fun listedIn(content: SettingsContent): List<SettingsOption> =
        when {
            this == Emulators -> List(content.systems.size) { SettingsOption.SystemEmulator }
            this == Controls && !content.hasVibrator -> options - SettingsOption.Vibration
            this == HomeScreen && content.hasNoButtonHints -> options + SettingsOption.GridSize
            this == Statistics -> statisticsStopsIn(content)
            // ScreenScraper (its status line only in a build without credentials), then RetroAchievements.
            this == Accounts -> accountRowsIn(content) + options
            this == Library ->
                listOf(SettingsOption.RomFolders, SettingsOption.Rescan) +
                    // Right under Rescan library, whose Scan again it offers: only while a scan could not read a sub-folder.
                    listOfNotNull(SettingsOption.UnreadableFolders.takeIf { unreadableFolders(content).isNotEmpty() }) +
                    (options - SettingsOption.RomFolders - SettingsOption.Rescan) +
                    listOfNotNull(SettingsOption.Systems.takeIf { content.librarySystems.isNotEmpty() }) +
                    SettingsOption.FilesNotAdded +
                    listOfNotNull(
                        SettingsOption.UnidentifiedGames.takeIf { content.unidentifiedGames.isNotEmpty() },
                        SettingsOption.HiddenGames.takeIf { content.hiddenGames.isNotEmpty() },
                    ) +
                    // Recent last, after every row that shows only sometimes.
                    SettingsOption.ClearRecent
            this == SettingsSection.SecondScreen ->
                if (content.secondScreen == null) emptyList() else options + listOfNotNull(SettingsOption.SecondScreenInGame.takeIf { content.isSecondScreenOn })
            else -> options
        }

    companion object {
        /** The sections of the left column for [content], in order: Second screen only on a device that has one, Emulators only while it has rows. */
        fun shownIn(content: SettingsContent): List<SettingsSection> =
            entries.filter {
                when (it) {
                    SettingsSection.SecondScreen -> content.secondScreen != null
                    Emulators -> content.systems.isNotEmpty()
                    else -> true
                }
            }
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

    /** Does something at once (Rescan library, Use as home screen); A and a tap run it. */
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

    /** A row of Colors' panel, not of the list ([COLORS_BACKGROUND_ITEM]). */
    Background(R.string.settings_background, OptionKind.Swatch),

    /** A row of Colors' panel, not of the list ([COLORS_TEXT_ITEM]). */
    TextColor(R.string.settings_text_color, OptionKind.Swatch),
    Accent(R.string.settings_accent, OptionKind.Swatch),
    TextSize(R.string.settings_text_size, OptionKind.Choice),

    /** ‹ Thin › / ‹ Medium › / ‹ Bold ›: how heavy every text role is drawn; not picked is Medium. */

    /** ‹ Game counts › / ‹ System codes › / ‹ Letters ›: what the home screen's peek shows while a games list is focused; not picked is Game counts. */
    LeftEdge(R.string.settings_left_edge, OptionKind.Choice),

    /** ‹ Small › / ‹ Medium › / ‹ Large ›: touch's way to the grid's size, listed only while Show button hints is off, when the tappable Size hint is gone. */
    GridSize(R.string.settings_grid_size, OptionKind.Choice),

    ConfirmBottom(R.string.settings_confirm_bottom, OptionKind.Switch),
    Animations(R.string.settings_animations, OptionKind.Switch),
    /** ‹ Off › / ‹ Quiet › / ‹ Loud ›: the interface sounds' volume ([SoundLevel]). */
    Sounds(R.string.settings_sounds, OptionKind.Choice),

    /** A light haptic tick on every move and a click on confirm (on by default). */
    Vibration(R.string.settings_vibration, OptionKind.Switch),
    UsageStatistics(R.string.settings_usage_statistics, OptionKind.Switch),

    /** Advanced's row for the three below: its panel lists them; its value is [backupAndRestoreValue]. */
    BackupAndRestore(R.string.settings_backup_and_restore, OptionKind.Opens),

    /** Never a row: an item of Back up and restore's panel. Its page chooses Settings or Everything, then Android's save picker. */
    Backup(R.string.settings_backup, OptionKind.Opens),

    /** Never a row: an item of Back up and restore's panel. Its page opens Android's open picker, checks the file, then asks before replacing. */
    Restore(R.string.settings_restore, OptionKind.Opens),

    /** Never a row: an item of Back up and restore's panel while the way-back set exists; its word is the space the set keeps. */
    UndoRestore(R.string.settings_undo_restore, OptionKind.Opens),

    /** Advanced's row for the two below: its panel lists them, each with what it does. */
    Reset(R.string.settings_reset_row, OptionKind.Opens),

    /** Never a row: an item of Reset…'s panel, with its own confirm step. */
    ResetAll(R.string.settings_reset_all, OptionKind.Opens),

    /** Never a row: an item of Reset…'s panel, with its own confirm step. */
    ResetLibrary(R.string.settings_reset_library, OptionKind.Opens),

    /** Shares the app's log file with Android's share sheet. Listed only in a build with no Report a problem, whose form does this otherwise ([SettingsContent.isInThisBuild]). */
    ExportLogs(R.string.settings_export_logs, OptionKind.Action),
    Version(R.string.settings_version, OptionKind.Info),

    /** One row for everything about updates: its value is the update found or the last check, its panel ([UpdatePanel]) has the update, Check now and the daily switch. */
    Updates(R.string.settings_updates, OptionKind.Opens),

    /** Never a row: the switch of Updates' panel; telemetry reports it by this name. */
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
    SortGames(R.string.settings_sort_games, OptionKind.Choice),

    /** "»": Clear Recent…'s confirm step. */
    ClearRecent(R.string.settings_clear_recent, OptionKind.Opens),

    /** ‹ A–Z › / ‹ Maker & year › / ‹ Manual ›: how the main screen's system column is arranged. Never a row: the first item of the Systems panel. */
    SystemOrder(R.string.settings_system_order, OptionKind.Choice),

    /**
     * "psx »" or "2 folders »": listed only while the last scan of a folder could not list some sub-folders ([unreadableFolders]);
     * its panel says so and offers Scan again and Skip this folder ([PanelPage.UnreadableFolders]).
     */
    UnreadableFolders(R.string.settings_unreadable_row, OptionKind.Opens),

    /** "16 shown »": System order, then every system with games, to arrange (A picks one up) and hide (Y). Listed while a system has games. */
    Systems(R.string.settings_systems, OptionKind.Opens),

    /** "12 »" or "2 folders to sort »": the files the scans left out by reason and the unknown folders ([PanelPage.FilesNotAdded]); always listed. */
    FilesNotAdded(R.string.settings_files_not_added_title, OptionKind.Opens),

    /** Listed only while games are named from their file alone; A or a tap on one opens Fix name…. */
    UnidentifiedGames(R.string.settings_unidentified_games_title, OptionKind.Opens),

    /** Listed only while games are hidden; its panel lists them, A or a tap shows one again. */
    HiddenGames(R.string.settings_hidden_games_title, OptionKind.Opens),

    /** One per system with games (its name is the label); the row's index is the system's. */
    SystemEmulator(R.string.settings_section_emulators, OptionKind.Opens),
    PushRightToPlay(R.string.settings_push_right, OptionKind.Switch),
    ShowButtonHints(R.string.settings_show_hints, OptionKind.Switch),

    /** ‹ L2 / R2 › / ‹ L1 / R1 ›: which shoulder pair switches system on the main screen. */
    SwitchSystemWith(R.string.settings_switch_system_with, OptionKind.Choice),

    /** ‹ Plumemu › / ‹ Android ›: which on-screen keyboard text fields type with. */
    Keyboard(R.string.settings_keyboard, OptionKind.Choice),
    UseAsHome(R.string.settings_use_as_home, OptionKind.Action),
    ShowApps(R.string.settings_show_apps, OptionKind.Switch),

    /** One switch for the two stored keys: on while either is, and a flip writes both ([toggled]). */
    ClockAndBattery(R.string.settings_clock_battery, OptionKind.Switch),
    SystemInfo(R.string.settings_system_info, OptionKind.Switch),

    /** Listed only while there is a second screen; shows [isSecondScreenOn], A or a tap stores the other value. */
    SecondScreen(R.string.settings_second_screen, OptionKind.Switch),

    /** Listed after [SecondScreen] while the switch is on; hidden while it is off (Settings has no dimmed rows). */
    SecondScreenInGame(R.string.settings_second_screen_in_game, OptionKind.Choice),

    /**
     * "Box art »" with the source in force as its value: its panel ([PanelPage.BoxArtSource]) lists the three
     * choices (ScreenScraper, Default source, Plumemu's own covers), each with one line on what it brings; A applies.
     */
    BoxArtSource(R.string.settings_box_art_source, OptionKind.Opens),

    /**
     * "ScreenScraper", signed in or out (the label stays put): its value is "Sign in »" while signed
     * out, else the box art's short state or the name (`optionValue`); its panel signs in, or shows
     * the status and the box art actions.
     */
    ScraperAccount(R.string.settings_account_title, OptionKind.Opens),

    /** "Box art: not available in this build" ([boxArtLineOf] is its label); the only row in a build without credentials. */
    ScraperStatus(R.string.settings_account_title, OptionKind.Info),

    /**
     * "RetroAchievements", signed in or out (the label stays put): its value is "Sign in »" while signed
     * out, else the name; its panel signs in, or shows who is signed in and Sign out.
     */
    RetroAchievementsAccount(R.string.settings_ra_title, OptionKind.Opens),

    /** Whose box to show when the game's own region has none. Never a row: an item of the ScreenScraper panel ([AccountPageItem.BoxArtRegion]) steps through its choices. */
    BoxArtRegion(R.string.settings_box_art_region, OptionKind.Choice),

    /** A cursor stop of Statistics: a most-played game by its place, or the page itself ([statisticStopsOf]). */
    Statistic(R.string.settings_section_statistics, OptionKind.Info),

    /** Statistics' last stop: count only the time a game was on the screen; needs Android's Usage access. */
    ExactPlayTime(R.string.settings_exact_play_time, OptionKind.Switch),
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

    /** Cancel and Clear, for Clear Recent…. */
    Confirm,

    /** Advanced › Reset…: Reset all settings and Reset library, each with what it does ([ResetPanelItem]). */
    Reset,

    /** Reset all settings' and Reset library's confirm: Cancel (focused first) and Reset. */
    ResetAllConfirm, ResetLibraryConfirm,

    /** Advanced › Back up and restore: Back up…, Restore…, and Undo restore while the way-back set exists ([backupRestoreItems]). */
    BackupRestore,

    /** Appearance › Language: System default, then each language in its own name; A applies ([languagePanelIndex], [languagePanelTag]). */
    Language,

    /** Back up and restore › Back up…: Settings and Everything, or Cancel while a backup runs ([backupPanelItems]). */
    Backup,

    /** Back up and restore › Restore…: Choose file…, Cancel while reading, Restore and Cancel at the confirm ([restorePanelItems]). */
    Restore,

    /** Back up and restore › Undo restore: Undo the restore and Forget the copy ([undoPanelItems]). */
    UndoRestore,

    /** About › Updates: the update's own buttons while one is found ([updatePanelItems]), Check now and the daily switch ([updatesPanelItems]). */
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

    /** Accounts › ScreenScraper while signed out: the sign-in form ([SettingsSignIn.form]); it takes the actions. */
    ScraperSignIn,

    /** About › Report a problem: the form ([SettingsProblemReport.form]); it takes the actions. */
    ProblemReport,

    /**
     * Accounts › ScreenScraper: Sign in again and Sign out after a refusal; otherwise the box art
     * actions ([accountPageItems]) and Sign out.
     */
    ScraperAccount,

    /** Accounts › Box art: the choices of [boxArtSourceChoices], each with its line, then the work that needs no account ([boxArtPanelEntries]); on a choice A applies (ScreenScraper while signed out opens its sign-in form first). */
    BoxArtSource,

    /** Accounts › RetroAchievements while signed in: who is signed in, Sign in again (after a refusal) and Sign out ([raPageItems]). */
    RaAccount,

    /** Accounts › ScreenScraper › Choose systems…: each system (A checks it), then the two actions ([SettingsFocus.chosenSystems]). */
    ScraperSystems,

    /** Re-download's confirm: Re-download, then Cancel (focused first); about [SettingsFocus.redownloadSystems]. */
    ScraperConfirm,

    /** Sign out's confirm, ScreenScraper's and RetroAchievements': Sign out, then Cancel (focused first). */
    ScraperSignOutConfirm, RaSignOutConfirm,

    /** Clear box art's confirm: Clear box art, then Cancel (focused first). */
    ScraperClearConfirm,

    /** Library › Systems: System order ([SYSTEM_ORDER_ITEM]), then every system with games in the column's order; A carries one, Y hides it ([SettingsSystems]). */
    Systems,

    /** Library › Files not added › an unknown folder: Ignore this folder, then every system; A applies ([SettingsFocus.folderKey]). */
    UnknownFolder,

    /** Library › Files not added: every line of [SettingsContent.notAdded]; A on a folder's line opens that folder's page. */
    FilesNotAdded,

    /** Library › Folder could not be read: Scan again, then each unreadable sub-folder; A skips it ([unreadablePanelItems]). */
    UnreadableFolders,
}

/**
 * Value of a ‹ › option, for the row and for stepping. [sorts]: which of Year and Rating Sort games lists
 * (the default lists every sort: the row's name is read by position in all of them; stepping passes the library's answer).
 */
internal fun SettingsOption.choiceIndex(
    settings: PlumSettings,
    defaultTextSize: TextSizeSetting,
    sorts: SortChoices.Values = SortChoices.Values.All,
): Int =
    when (this) {
        SettingsOption.Colors -> settings.appearance.let { it.colorsCycle().indexOf(it.colorSet()) }
        SettingsOption.TextSize -> (settings.appearance.textSize ?: defaultTextSize).ordinal
        SettingsOption.LeftEdge -> (settings.appearance.leftEdge ?: LeftEdgeSetting.Default).ordinal
        SettingsOption.GridSize -> settings.appearance.gridSize.ordinal
        SettingsOption.SortGames -> SortChoices(settings.library.sortGames, sorts).options.indexOf(settings.library.sortGames)
        SettingsOption.Keyboard -> settings.controls.keyboard.ordinal
        SettingsOption.SwitchSystemWith -> settings.controls.switchSystemWith.ordinal
        SettingsOption.SystemOrder -> settings.library.systemOrder.ordinal
        SettingsOption.SecondScreenInGame -> settings.secondScreen.inGame.ordinal
        SettingsOption.BoxArtRegion -> settings.library.boxArtRegion.ordinal
        SettingsOption.Sounds -> settings.advanced.sounds.ordinal
        else -> 0
    }

internal fun SettingsOption.choiceCount(settings: PlumSettings, sorts: SortChoices.Values = SortChoices.Values.All): Int =
    when (this) {
        // Custom is a value only while there is a custom look to be on or go back to.
        SettingsOption.Colors -> settings.appearance.colorsCycle().size
        SettingsOption.TextSize -> TextSizeSetting.entries.size
        SettingsOption.LeftEdge -> LeftEdgeSetting.entries.size
        SettingsOption.GridSize -> GridSizeSetting.entries.size
        SettingsOption.SortGames -> SortChoices(settings.library.sortGames, sorts).options.size
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
    sorts: SortChoices.Values = SortChoices.Values.All,
): PlumSettings =
    when (this) {
        SettingsOption.SortGames -> settings.copy(library = settings.library.copy(sortGames = SortChoices(settings.library.sortGames, sorts).options[index]))
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
            SettingsOption.TextSize -> it.copy(textSize = TextSizeSetting.entries[index])
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
        SettingsOption.ExactPlayTime -> settings.advanced.exactPlayTime
        SettingsOption.CheckUpdatesDaily -> settings.updates.checkDaily
        SettingsOption.PushRightToPlay -> settings.controls.pushRightToPlay
        SettingsOption.ShowButtonHints -> settings.controls.showButtonHints
        SettingsOption.ShowApps -> settings.homeScreen.showApps
        SettingsOption.ClockAndBattery -> settings.homeScreen.showClock || settings.homeScreen.showBattery
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
        SettingsOption.ExactPlayTime -> settings.copy(advanced = settings.advanced.copy(exactPlayTime = !settings.advanced.exactPlayTime))
        SettingsOption.CheckUpdatesDaily -> settings.copy(updates = settings.updates.copy(checkDaily = !settings.updates.checkDaily))
        SettingsOption.PushRightToPlay -> settings.copy(controls = settings.controls.copy(pushRightToPlay = !settings.controls.pushRightToPlay))
        SettingsOption.ShowButtonHints -> settings.copy(controls = settings.controls.copy(showButtonHints = !settings.controls.showButtonHints))
        SettingsOption.ShowApps -> settings.copy(homeScreen = settings.homeScreen.copy(showApps = !settings.homeScreen.showApps))
        SettingsOption.ClockAndBattery -> {
            val next = !switchValue(settings)
            settings.copy(homeScreen = settings.homeScreen.copy(showClock = next, showBattery = next))
        }
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
