package com.plumemu.usage

import com.plumemu.coredata.backup.BackupKind
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.FontSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coredata.settings.LeftEdgeSetting
import com.plumemu.coredata.settings.TextWeightSetting
import com.plumemu.coreinput.Column
import com.plumemu.graph.BackupState
import com.plumemu.graph.RestoreResult
import com.plumemu.home.EntryKind
import com.plumemu.launcher.EmulatorOffer
import com.plumemu.launcher.FailureReason
import com.plumemu.launcher.FileProblem
import com.plumemu.launcher.LaunchOutcome
import com.plumemu.launcher.choice.ChoiceSource
import com.plumemu.settings.screen.SettingsSection
import com.plumemu.telemetry.ControllerKind
import com.plumemu.telemetry.LaunchResult
import com.plumemu.telemetry.ScreenId
import com.plumemu.update.CheckFailure
import com.plumemu.update.CheckOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The app's states as the convention's fixed values (docs/conventions/telemetry.md). */
class UsageEventsTest {
    @Test
    fun aColourOfThePersonsOwnIsSentAsCustomNeverItsValue() {
        val before = PlumSettings()
        val after = before.copy(appearance = before.appearance.copy(background = ColorSetting.Custom(0x123456), font = FontSetting.Mono))
        val events = settingChanges(before, after)
        assertEquals(
            setOf(mapOf("key" to "background", "value" to "custom"), mapOf("key" to "font", "value" to "mono"), mapOf("key" to "colors", "value" to "custom")),
            events.map { it.wireFields() }.toSet(),
        )
        assertNoFreeText(events)
    }

    @Test
    fun presetsSwitchesAndTextSizeBackToNotPicked() {
        val before = PlumSettings().let { it.copy(appearance = it.appearance.copy(textSize = TextSizeSetting.Medium)) }
        val after =
            before.copy(
                appearance = before.appearance.copy(background = ColorSetting.Preset("Paper"), textSize = null),
                homeScreen = before.homeScreen.copy(showClock = false),
            )
        val fields = settingChanges(before, after).map { it.wireFields() }
        assertEquals(true, mapOf("key" to "background", "value" to "paper") in fields)
        assertEquals(true, mapOf("key" to "text_size", "value" to "large") in fields)
        assertEquals(true, mapOf("key" to "show_clock", "value" to "off") in fields)
    }

    @Test
    fun textWeightNotPickedCountsAsMedium() {
        val before = PlumSettings().let { it.copy(appearance = it.appearance.copy(textWeight = null)) }
        assertEquals(emptyList<Any>(), settingChanges(before, before.copy(appearance = before.appearance.copy(textWeight = TextWeightSetting.Medium))))
        val fields = settingChanges(before, before.copy(appearance = before.appearance.copy(textWeight = TextWeightSetting.Bold))).map { it.wireFields() }
        assertEquals(listOf(mapOf("key" to "text_weight", "value" to "bold")), fields)
    }

    @Test
    fun textSizeNotPickedCountsAsLarge() {
        val before = PlumSettings().let { it.copy(appearance = it.appearance.copy(textSize = null)) }
        assertEquals(emptyList<Any>(), settingChanges(before, before.copy(appearance = before.appearance.copy(textSize = TextSizeSetting.Large))))
        val fields = settingChanges(before, before.copy(appearance = before.appearance.copy(textSize = TextSizeSetting.Huge))).map { it.wireFields() }
        assertEquals(listOf(mapOf("key" to "text_size", "value" to "huge")), fields)
        assertEquals(emptyList<Any>(), settingChanges(before.copy(appearance = before.appearance.copy(textSize = TextSizeSetting.Large)), before))
    }

    @Test
    fun leftEdgeNotPickedCountsAsGameCounts() {
        val before = PlumSettings().let { it.copy(appearance = it.appearance.copy(leftEdge = null)) }
        assertEquals(emptyList<Any>(), settingChanges(before, before.copy(appearance = before.appearance.copy(leftEdge = LeftEdgeSetting.Counts))))
        val fields = settingChanges(before, before.copy(appearance = before.appearance.copy(leftEdge = LeftEdgeSetting.Codes))).map { it.wireFields() }
        assertEquals(listOf(mapOf("key" to "left_edge", "value" to "codes")), fields)
    }

    @Test
    fun theStatisticsSwitchAndUnchangedSettingsSendNothing() {
        val before = PlumSettings()
        assertEquals(emptyList<Any>(), settingChanges(before, before))
        assertEquals(emptyList<Any>(), settingChanges(before, before.copy(advanced = before.advanced.copy(usageStatistics = false))))
    }

    @Test
    fun everySettingsSectionIsItsScreen() {
        assertEquals(
            SettingsSection.entries.map { "settings_" + it.name.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase() },
            SettingsSection.entries.map { screenIdOf(it).wire },
        )
    }

    @Test
    fun theMainScreensScreens() {
        assertEquals(ScreenId.Home, homeScreenIdOf(Column.Systems, EntryKind.System, isSearchOpen = false, isPanelOpen = false))
        assertEquals(ScreenId.System, homeScreenIdOf(Column.Games, EntryKind.System, false, false))
        assertEquals(ScreenId.Favorites, homeScreenIdOf(Column.Games, EntryKind.Favorites, false, false))
        assertEquals(ScreenId.Recent, homeScreenIdOf(Column.Games, EntryKind.Recent, false, false))
        assertEquals(ScreenId.Apps, homeScreenIdOf(Column.Games, EntryKind.Apps, false, false))
        assertEquals(ScreenId.Details, homeScreenIdOf(Column.Details, EntryKind.System, false, false))
        assertEquals(ScreenId.Search, homeScreenIdOf(Column.Games, EntryKind.System, isSearchOpen = true, isPanelOpen = false))
        assertEquals(ScreenId.GameMenu, homeScreenIdOf(Column.Games, EntryKind.System, isSearchOpen = true, isPanelOpen = true))
        assertEquals("an empty library", ScreenId.Home, homeScreenIdOf(Column.Games, null, false, false))
    }

    @Test
    fun launchOutcomesAsResultsAndEmulators() {
        val offer = EmulatorOffer("duckstation", "DuckStation", "com.github.stenzek.duckstation")
        assertEquals(LaunchResult.Ok, launchResultOf(LaunchOutcome.Started(1, "duckstation", "pkg", ChoiceSource.PersonForGame, null)))
        assertEquals("duckstation", launchEmulatorIdOf(LaunchOutcome.Started(1, "duckstation", "pkg", ChoiceSource.PersonForGame, null)))
        assertEquals(LaunchResult.EmulatorMissing, launchResultOf(LaunchOutcome.EmulatorMissing(1, offer, ChoiceSource.PersonForGame)))
        assertEquals("duckstation", launchEmulatorIdOf(LaunchOutcome.EmulatorMissing(1, offer, ChoiceSource.PersonForGame)))
        assertEquals(LaunchResult.EmulatorMissing, launchResultOf(LaunchOutcome.NoEmulatorForSystem(1, "psx", emptyList())))
        assertEquals(LaunchResult.EmulatorNotChosen, launchResultOf(LaunchOutcome.EmulatorNotChosen(1, "psx", "PlayStation", listOf(offer), null)))
        assertNull(launchEmulatorIdOf(LaunchOutcome.EmulatorNotChosen(1, "psx", "PlayStation", listOf(offer), null)))
        assertEquals(LaunchResult.FileMissing, launchResultOf(LaunchOutcome.FileMissing(1, "Crash (USA).cue", FileProblem.Gone)))
        assertEquals(LaunchResult.Error, launchResultOf(LaunchOutcome.Failed(1, FailureReason.StartFailed)))
    }

    @Test
    fun updateChecks() {
        assertEquals(mapOf("trigger" to "daily", "result" to "available"), updateCheckEventOf(false, CheckOutcome.Found).wireFields())
        assertEquals(mapOf("trigger" to "manual", "result" to "none"), updateCheckEventOf(true, CheckOutcome.NeedsNewerAndroid).wireFields())
        assertEquals("error", updateCheckEventOf(true, CheckOutcome.Failed(CheckFailure.Offline)).wireFields()["result"])
    }

    @Test
    fun backupsAndRestores() {
        assertEquals(mapOf("kind" to "everything", "result" to "ok"), backupEventOf(BackupKind.Everything, BackupState.Done(BackupKind.Everything, "x.zip", 10, 0))?.wireFields())
        assertNull("a cancel is not a backup made", backupEventOf(BackupKind.Settings, BackupState.Cancelled(deleted = true)))
        assertEquals(mapOf("kind" to "settings", "result" to "error"), restoreEventOf(RestoreResult.Failed(BackupKind.Settings, isUndo = false, isPutBack = true))?.wireFields())
        assertEquals("ok", restoreEventOf(RestoreResult.Done(BackupKind.Everything, 0, isUndo = false))?.wireFields()?.get("result"))
        assertNull("an Undo is no restore", restoreEventOf(RestoreResult.Done(BackupKind.Everything, 0, isUndo = true)))
    }

    @Test
    fun controllerKinds() {
        assertEquals(ControllerKind.BuiltIn, controllerKindOf(0x045E, isExternal = false))
        assertEquals(ControllerKind.Xbox, controllerKindOf(0x045E, isExternal = true))
        assertEquals(ControllerKind.PlayStation, controllerKindOf(0x054C, isExternal = true))
        assertEquals(ControllerKind.Nintendo, controllerKindOf(0x057E, isExternal = true))
        assertEquals(ControllerKind.Other, controllerKindOf(0x2DC8, isExternal = true))
    }
}
