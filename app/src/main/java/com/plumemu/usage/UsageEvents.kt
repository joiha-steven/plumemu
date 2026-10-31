package com.plumemu.usage

import com.plumemu.coreinput.Column
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.ConfirmButtonSetting
import com.plumemu.coredata.settings.LeftEdgeSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coredata.settings.TextWeightSetting
import com.plumemu.coreui.theme.AccentPreset
import com.plumemu.coreui.theme.BackgroundPreset
import com.plumemu.coreui.theme.TextMode
import com.plumemu.home.EntryKind
import com.plumemu.home.HomeEntry
import com.plumemu.launcher.LaunchOutcome
import com.plumemu.settings.colorSet
import com.plumemu.settings.screen.SettingsOption
import com.plumemu.settings.screen.SettingsSection
import com.plumemu.telemetry.LaunchFrom
import com.plumemu.telemetry.LaunchResult
import com.plumemu.telemetry.ScreenId
import com.plumemu.telemetry.TelemetryEvent
import com.plumemu.telemetry.OkOrError
import com.plumemu.telemetry.BackupKind as TelemetryBackupKind
import com.plumemu.coredata.backup.BackupKind
import com.plumemu.graph.BackupState
import com.plumemu.graph.RestoreResult
import com.plumemu.telemetry.UpdateCheckResult
import com.plumemu.telemetry.UpdateTrigger
import com.plumemu.update.CheckOutcome

/*
 * The app's own states turned into the fixed values of docs/conventions/telemetry.md. Pure, so
 * each mapping is tested without a device; nothing here reads a title, a file name or a colour value.
 */

/** Settings' section as its screen (`settings_<section>`). */
internal fun screenIdOf(section: SettingsSection): ScreenId =
    when (section) {
        SettingsSection.Library -> ScreenId.SettingsLibrary
        SettingsSection.Appearance -> ScreenId.SettingsAppearance
        SettingsSection.Emulators -> ScreenId.SettingsEmulators
        SettingsSection.Controls -> ScreenId.SettingsControls
        SettingsSection.HomeScreen -> ScreenId.SettingsHomeScreen
        SettingsSection.Statistics -> ScreenId.SettingsStatistics
        SettingsSection.Advanced -> ScreenId.SettingsAdvanced
        SettingsSection.About -> ScreenId.SettingsAbout
    }

/**
 * The main screen's screen: the game panel over everything, then Search, then the strip (the
 * system column is `home`, a game column is its entry's kind, the details column `details`).
 * [entryKind] is the focused entry's; null when the library shows no entry.
 */
internal fun homeScreenIdOf(
    column: Column,
    entryKind: EntryKind?,
    isSearchOpen: Boolean,
    isPanelOpen: Boolean,
): ScreenId =
    when {
        isPanelOpen -> ScreenId.GameMenu
        isSearchOpen -> ScreenId.Search
        column == Column.Systems || entryKind == null -> ScreenId.Home
        column == Column.Details -> ScreenId.Details
        else ->
            when (entryKind) {
                EntryKind.Recent -> ScreenId.Recent
                EntryKind.Favorites -> ScreenId.Favorites
                EntryKind.Apps -> ScreenId.Apps
                EntryKind.System -> ScreenId.System
            }
    }

/** `game_launched.from` for Play on a row of [entry]: Recent and Favorites by name, else the system's view. */
internal fun launchFromOf(
    entry: HomeEntry,
    isGrid: Boolean,
): LaunchFrom =
    when {
        entry.kind == EntryKind.Recent -> LaunchFrom.Recent
        entry.kind == EntryKind.Favorites -> LaunchFrom.Favorites
        isGrid -> LaunchFrom.Grid
        else -> LaunchFrom.List
    }

/** `game_launched.result` of a launch's outcome. */
internal fun launchResultOf(outcome: LaunchOutcome): LaunchResult =
    when (outcome) {
        is LaunchOutcome.Started -> LaunchResult.Ok
        is LaunchOutcome.EmulatorMissing, is LaunchOutcome.NoEmulatorForSystem, is LaunchOutcome.NeedsFilePath -> LaunchResult.EmulatorMissing
        is LaunchOutcome.EmulatorNotChosen -> LaunchResult.EmulatorNotChosen
        is LaunchOutcome.FileMissing, is LaunchOutcome.BadTitleIdFile, is LaunchOutcome.BadSteamAppIdFile -> LaunchResult.FileMissing
        is LaunchOutcome.Failed, is LaunchOutcome.ShortcutRefused, is LaunchOutcome.AppGameRefused -> LaunchResult.Error
    }

/** The emulator a launch's outcome names (started, missing, or unable to take the folder); null when none was chosen or installed. */
internal fun launchEmulatorIdOf(outcome: LaunchOutcome): String? =
    when (outcome) {
        is LaunchOutcome.Started -> outcome.emulatorId
        is LaunchOutcome.EmulatorMissing -> outcome.emulator.emulatorId
        is LaunchOutcome.NeedsFilePath -> outcome.emulator.emulatorId
        else -> null
    }

/** A value of a setting that is not one of the app's enums: a colour of the person's own, or the text size left to the screen. */
internal enum class SettingWord { Custom, BySystem }

/**
 * `setting_changed` for every fixed choice that differs between [before] and [after], keyed by
 * its Settings option. Colours are sent as their preset's name or `custom`, never a value; the
 * statistics switch is left out (it sends its own `stats_switched_on` or the one decline), and
 * so are lists (folders, system order, hidden systems, list or grid per system).
 */
internal fun settingChanges(
    before: PlumSettings,
    after: PlumSettings,
): List<TelemetryEvent.SettingChanged> =
    buildList {
        fun choice(
            option: SettingsOption,
            old: Enum<*>,
            new: Enum<*>,
        ) {
            if (old != new) add(TelemetryEvent.SettingChanged(option, new))
        }

        fun switch(
            option: SettingsOption,
            old: Boolean,
            new: Boolean,
        ) {
            if (old != new) add(TelemetryEvent.SettingChanged(option, new))
        }
        val a = before.appearance
        val b = after.appearance
        choice(SettingsOption.Colors, a.colorSet() ?: SettingWord.Custom, b.colorSet() ?: SettingWord.Custom)
        choice(SettingsOption.Background, backgroundWord(a.background), backgroundWord(b.background))
        choice(SettingsOption.TextColor, textWord(a.textColor), textWord(b.textColor))
        choice(SettingsOption.Accent, accentWord(a.accent), accentWord(b.accent))
        choice(SettingsOption.Font, a.font, b.font)
        choice(SettingsOption.TextSize, a.textSize ?: TextSizeSetting.Default, b.textSize ?: TextSizeSetting.Default)
        choice(SettingsOption.TextWeight, a.textWeight ?: TextWeightSetting.Default, b.textWeight ?: TextWeightSetting.Default)
        choice(SettingsOption.LeftEdge, a.leftEdge ?: LeftEdgeSetting.Default, b.leftEdge ?: LeftEdgeSetting.Default)
        choice(SettingsOption.GridSize, a.gridSize, b.gridSize)
        val c = before.controls
        val d = after.controls
        switch(SettingsOption.ConfirmBottom, c.confirmButton == ConfirmButtonSetting.Bottom, d.confirmButton == ConfirmButtonSetting.Bottom)
        switch(SettingsOption.HoldToScrollFaster, c.holdToScrollFaster, d.holdToScrollFaster)
        switch(SettingsOption.ShowButtonHints, c.showButtonHints, d.showButtonHints)
        choice(SettingsOption.Keyboard, c.keyboard, d.keyboard)
        choice(SettingsOption.SwitchSystemWith, c.switchSystemWith, d.switchSystemWith)
        switch(SettingsOption.PushRightToPlay, c.pushRightToPlay, d.pushRightToPlay)
        val l = before.library
        val m = after.library
        switch(SettingsOption.ScanOnStartup, l.scanOnStartup, m.scanOnStartup)
        choice(SettingsOption.SortGames, l.sortGames, m.sortGames)
        choice(SettingsOption.BoxArtRegion, l.boxArtRegion, m.boxArtRegion)
        choice(SettingsOption.SystemOrder, l.systemOrder, m.systemOrder)
        val h = before.homeScreen
        val i = after.homeScreen
        switch(SettingsOption.ShowApps, h.showApps, i.showApps)
        switch(SettingsOption.ShowClock, h.showClock, i.showClock)
        switch(SettingsOption.ShowBattery, h.showBattery, i.showBattery)
        if (before.secondScreen.enabled != after.secondScreen.enabled && after.secondScreen.enabled != null) {
            add(TelemetryEvent.SettingChanged(SettingsOption.SecondScreen, after.secondScreen.enabled == true))
        }
        choice(SettingsOption.SecondScreenInGame, before.secondScreen.inGame, after.secondScreen.inGame)
        switch(SettingsOption.Animations, before.advanced.animations, after.advanced.animations)
        choice(SettingsOption.Sounds, before.advanced.sounds, after.advanced.sounds)
        switch(SettingsOption.Vibration, before.advanced.vibration, after.advanced.vibration)
        switch(SettingsOption.CheckUpdatesDaily, before.updates.checkDaily, after.updates.checkDaily)
    }

/** A background as a preset's name, or [SettingWord.Custom] for a colour of the person's own (never its value). */
private fun backgroundWord(setting: ColorSetting): Enum<*> =
    (setting as? ColorSetting.Preset)?.let { preset -> BackgroundPreset.entries.firstOrNull { it.name == preset.name } } ?: SettingWord.Custom

/** The text as Light or Dark, or [SettingWord.Custom] for a colour of the person's own (never its value). */
private fun textWord(setting: TextColorSetting): Enum<*> =
    when (setting) {
        TextColorSetting.Light -> TextMode.Light
        TextColorSetting.Dark -> TextMode.Dark
        is TextColorSetting.Custom -> SettingWord.Custom
    }

private fun accentWord(setting: ColorSetting): Enum<*> =
    if (setting is ColorSetting.BySystem) SettingWord.BySystem else (setting as? ColorSetting.Preset)?.let { preset -> AccentPreset.entries.firstOrNull { it.name == preset.name } } ?: SettingWord.Custom

/** `update_check` of a check that ended: found a newer release, none for this device, or failed. */
internal fun updateCheckEventOf(
    manual: Boolean,
    outcome: CheckOutcome,
): TelemetryEvent.UpdateCheck =
    TelemetryEvent.UpdateCheck(
        trigger = if (manual) UpdateTrigger.Manual else UpdateTrigger.Daily,
        result =
            when (outcome) {
                CheckOutcome.Found -> UpdateCheckResult.Available
                CheckOutcome.UpToDate, CheckOutcome.NeedsNewerAndroid -> UpdateCheckResult.None
                is CheckOutcome.Failed -> UpdateCheckResult.Error
            },
    )

/** `backup_made` of a backup that ended; a cancel by the person or an interrupted one sends nothing. */
internal fun backupEventOf(
    kind: BackupKind,
    end: BackupState,
): TelemetryEvent.BackupMade? =
    when (end) {
        is BackupState.Done -> TelemetryEvent.BackupMade(backupKindOf(kind), OkOrError.Ok)
        is BackupState.Failed -> TelemetryEvent.BackupMade(backupKindOf(kind), OkOrError.Error)
        else -> null
    }

/** `restore_done` of a restore that ended; an Undo or an interrupted one sends nothing. */
internal fun restoreEventOf(result: RestoreResult): TelemetryEvent.RestoreDone? =
    when (result) {
        is RestoreResult.Done -> if (result.isUndo) null else TelemetryEvent.RestoreDone(backupKindOf(result.kind), OkOrError.Ok)
        is RestoreResult.Failed -> if (result.isUndo) null else TelemetryEvent.RestoreDone(backupKindOf(result.kind), OkOrError.Error)
        is RestoreResult.Interrupted -> null
    }

private fun backupKindOf(kind: BackupKind): TelemetryBackupKind =
    when (kind) {
        BackupKind.Settings -> TelemetryBackupKind.Settings
        BackupKind.Everything -> TelemetryBackupKind.Everything
    }
