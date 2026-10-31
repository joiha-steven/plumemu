package com.plumemu.telemetry

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Each event's name and fields as docs/conventions/telemetry.md lists them. */
class TelemetryEventTest {
    private enum class SettingsOptionLike { TextSize, ShowClock }

    private enum class TextSizeLike { Large }

    private enum class ErrorCode { FOLDER_LOST }

    private fun props(event: TelemetryEvent) = event.props().mapValues { (it.value as JsonPrimitive).content }

    @Test
    fun everyEventHasTheConventionsNameAndFields() {
        val cases: List<Pair<TelemetryEvent, Pair<String, Map<String, String>>>> =
            listOf(
                TelemetryEvent.AppOpen(420, OpenFrom.HomeKey) to ("app_open" to mapOf("cold_start_ms" to "420", "from" to "home_key")),
                TelemetryEvent.AppBackground(95, 4, InputUsed.Both) to
                    ("app_background" to mapOf("seconds_in_app" to "95", "screens" to "4", "input" to "both")),
                TelemetryEvent.SetupStep(SetupStepId.Folders, 31, SetupAction.Skip) to
                    ("setup_step" to mapOf("step" to "folders", "seconds" to "31", "action" to "skip")),
                TelemetryEvent.SetupCompleted(200, artwork = false, folders = 2, systemsFound = 9, emulatorsPicked = 5) to
                    (
                        "setup_completed" to
                            mapOf("seconds" to "200", "artwork" to "no", "folders" to "2", "systems_found" to "9", "emulators_picked" to "5")
                    ),
                TelemetryEvent.LibraryScanned(ScanTrigger.FolderAdded, 12_000, 30, 75, ScanResult.FolderLost) to
                    (
                        "library_scanned" to
                            mapOf("trigger" to "folder_added", "games_bucket" to "10k+", "systems" to "30", "seconds" to "75", "result" to "folder_lost")
                    ),
                TelemetryEvent.ScreenOpened(ScreenId.GameMenu) to ("screen_opened" to mapOf("screen" to "game_menu")),
                TelemetryEvent.ViewChanged.ToList(SystemRef("snes")) to ("view_changed" to mapOf("system_id" to "snes", "view" to "list")),
                TelemetryEvent.ViewChanged.ToGrid(SystemRef("snes"), GridSize.Medium) to
                    ("view_changed" to mapOf("system_id" to "snes", "view" to "grid", "grid_size" to "medium")),
                TelemetryEvent.ViewChanged.ToDetails(SystemRef("psx")) to ("view_changed" to mapOf("system_id" to "psx", "view" to "details")),
                TelemetryEvent.SearchUsed(queryLength = 4, results = 0, openedResult = true) to
                    ("search_used" to mapOf("query_length_bucket" to "3–5", "results_bucket" to "0", "opened_result" to "yes")),
                TelemetryEvent.FavoriteChanged(SystemRef("gba"), FavoriteChange.Removed) to ("favorite_changed" to mapOf("system_id" to "gba", "change" to "removed")),
                TelemetryEvent.GameLaunched(SystemRef("psx"), EmulatorRef("duckstation"), LaunchFrom.SecondScreen, LaunchResult.EmulatorMissing) to
                    (
                        "game_launched" to
                            mapOf("system_id" to "psx", "emulator_id" to "duckstation", "from" to "second_screen", "result" to "emulator_missing")
                    ),
                TelemetryEvent.GameSession(SystemRef("psx"), EmulatorRef("duckstation"), 900) to
                    ("game_session" to mapOf("system_id" to "psx", "emulator_id" to "duckstation", "minutes" to "600")),
                TelemetryEvent.EmulatorPicked(SystemRef("n64"), EmulatorRef("mupen64plus"), PickPlace.PerGame) to
                    ("emulator_picked" to mapOf("system_id" to "n64", "emulator_id" to "mupen64plus", "where" to "per_game")),
                TelemetryEvent.SettingChanged(SettingsOptionLike.TextSize, TextSizeLike.Large) to
                    ("setting_changed" to mapOf("key" to "text_size", "value" to "large")),
                TelemetryEvent.SettingChanged(SettingsOptionLike.ShowClock, isOn = false) to
                    ("setting_changed" to mapOf("key" to "show_clock", "value" to "off")),
                TelemetryEvent.ControllerConnected(ControllerKind.PlayStation) to ("controller_connected" to mapOf("kind" to "playstation")),
                TelemetryEvent.ArtworkStatus(120, 3, 1) to ("artwork_status" to mapOf("downloaded" to "120", "failed" to "3", "quota_hit" to "1")),
                TelemetryEvent.UpdateCheck(UpdateTrigger.Daily, UpdateCheckResult.Available) to
                    ("update_check" to mapOf("trigger" to "daily", "result" to "available")),
                TelemetryEvent.UpdateInstalled("0.0.2-alpha") to ("update_installed" to mapOf("from_version" to "0.0.2-alpha")),
                TelemetryEvent.BackupMade(BackupKind.Everything, OkOrError.Ok) to ("backup_made" to mapOf("kind" to "everything", "result" to "ok")),
                TelemetryEvent.RestoreDone(BackupKind.Settings, OkOrError.Error) to ("restore_done" to mapOf("kind" to "settings", "result" to "error")),
                TelemetryEvent.ErrorShown(ScreenId.SettingsLibrary, ErrorCode.FOLDER_LOST) to
                    ("error_shown" to mapOf("where" to "settings_library", "code" to "folder_lost")),
                TelemetryEvent.FirstOpen to ("first_open" to emptyMap()),
                TelemetryEvent.QueueOverflow(100) to ("queue_overflow" to mapOf("dropped" to "100")),
                TelemetryEvent.StatsSwitchedOn(SwitchPlace.Settings) to ("stats_switched_on" to mapOf("where" to "settings")),
            )
        for ((event, expected) in cases) {
            assertEquals(expected.first, event.name)
            assertEquals(expected.first, expected.second, props(event))
        }
    }

    @Test
    fun countsAreSentAsNumbersAndChoicesAsText() {
        val props = TelemetryEvent.AppOpen(420, OpenFrom.Icon).props()
        assertEquals(false, props.getValue("cold_start_ms").isString)
        assertEquals(true, props.getValue("from").isString)
    }

    @Test
    fun theSettingsSectionsAreScreensOfTheirOwn() {
        assertEquals(
            listOf("library", "appearance", "emulators", "controls", "home_screen", "statistics", "advanced", "about"),
            ScreenId.entries.map { it.wire }.filter { it.startsWith("settings_") }.map { it.removePrefix("settings_") },
        )
    }

    @Test
    fun aWarmOpenLeavesOutTheColdStart() {
        assertEquals(mapOf("from" to "other"), props(TelemetryEvent.AppOpen(null, OpenFrom.Other)))
    }

    @Test
    fun aLaunchWithNoEmulatorLeavesOutTheEmulator() {
        assertEquals(
            mapOf("system_id" to "psx", "from" to "list", "result" to "emulator_not_chosen"),
            props(TelemetryEvent.GameLaunched(SystemRef("psx"), null, LaunchFrom.List, LaunchResult.EmulatorNotChosen)),
        )
    }

    @Test
    fun wireFieldsAreThePropsAsText() {
        val event = TelemetryEvent.SetupStep(SetupStepId.SecondScreen, 4, SetupAction.Next)
        assertEquals(mapOf("step" to "second_screen", "seconds" to "4", "action" to "next"), event.wireFields())
    }

    @Test
    fun aNegativeSessionIsSentAsZeroMinutes() {
        assertEquals("0", props(TelemetryEvent.GameSession(SystemRef("psx"), EmulatorRef("duckstation"), -3))["minutes"])
    }
}
