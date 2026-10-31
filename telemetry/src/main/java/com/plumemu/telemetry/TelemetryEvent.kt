package com.plumemu.telemetry

import kotlinx.serialization.json.JsonPrimitive

/**
 * Every event of docs/conventions/telemetry.md, one type each, so a wrong field or value does not
 * compile. Counts that would say too much (games, search length, results) are passed as numbers
 * and turned into the convention's ranges here; nothing else is ever sent from them.
 *
 * `first_open`, `queue_overflow`, `stats_switched_on` and `stats_declined` are sent by the module
 * itself ([Telemetry]); the app never makes them.
 */
public sealed class TelemetryEvent(
    /** The event's name on the wire. */
    public val name: String,
) {
    /** The event's fields, exactly as sent. */
    internal abstract fun props(): Map<String, JsonPrimitive>

    /** The event's fields as the text sent, for the app's tests (a fake [TelemetrySink] reads them). */
    public fun wireFields(): Map<String, String> = props().mapValues { it.value.content }

    /** False when a field is not of its allowed shape: the event is dropped and logged, never sent. */
    internal open fun isValid(): Boolean = true

    /** Sent at once rather than with the next batch. */
    internal open val urgent: Boolean get() = false

    /** [coldStartMs] only on a cold start (process start to the first frame); null otherwise, and the field is left out. */
    public class AppOpen(
        public val coldStartMs: Long?,
        public val from: OpenFrom,
    ) : TelemetryEvent("app_open") {
        override fun props(): Map<String, JsonPrimitive> =
            buildMap {
                coldStartMs?.let { put("cold_start_ms", num(it)) }
                put("from", wire(from))
            }
    }

    /** The app went to the background; also sends what is queued. */
    public class AppBackground(
        public val secondsInApp: Long,
        public val screens: Int,
        public val input: InputUsed,
    ) : TelemetryEvent("app_background") {
        override val urgent: Boolean get() = true

        override fun props(): Map<String, JsonPrimitive> =
            mapOf("seconds_in_app" to num(secondsInApp), "screens" to num(screens), "input" to wire(input))
    }

    public class SetupStep(
        public val step: SetupStepId,
        public val seconds: Long,
        public val action: SetupAction,
    ) : TelemetryEvent("setup_step") {
        override fun props(): Map<String, JsonPrimitive> = mapOf("step" to wire(step), "seconds" to num(seconds), "action" to wire(action))
    }

    public class SetupCompleted(
        public val seconds: Long,
        public val artwork: Boolean,
        public val folders: Int,
        public val systemsFound: Int,
        public val emulatorsPicked: Int,
    ) : TelemetryEvent("setup_completed") {
        override fun props(): Map<String, JsonPrimitive> =
            mapOf(
                "seconds" to num(seconds),
                "artwork" to yesNo(artwork),
                "folders" to num(folders),
                "systems_found" to num(systemsFound),
                "emulators_picked" to num(emulatorsPicked),
            )
    }

    /** [games] is the exact count; only its range ([Buckets.games]) is sent. */
    public class LibraryScanned(
        public val trigger: ScanTrigger,
        public val games: Int,
        public val systems: Int,
        public val seconds: Long,
        public val result: ScanResult,
    ) : TelemetryEvent("library_scanned") {
        override fun props(): Map<String, JsonPrimitive> =
            mapOf(
                "trigger" to wire(trigger),
                "games_bucket" to JsonPrimitive(Buckets.games(games)),
                "systems" to num(systems),
                "seconds" to num(seconds),
                "result" to wire(result),
            )
    }

    public class ScreenOpened(
        public val screen: ScreenId,
    ) : TelemetryEvent("screen_opened") {
        override fun props(): Map<String, JsonPrimitive> = mapOf("screen" to wire(screen))
    }

    /** `view_changed`: list and details carry no size, grid always does. */
    public sealed class ViewChanged(
        public val system: SystemRef,
        private val view: String,
        private val gridSize: GridSize?,
    ) : TelemetryEvent("view_changed") {
        override fun props(): Map<String, JsonPrimitive> =
            buildMap {
                put("system_id", JsonPrimitive(system.id))
                put("view", JsonPrimitive(view))
                gridSize?.let { put("grid_size", wire(it)) }
            }

        public class ToList(system: SystemRef) : ViewChanged(system, "list", null)

        public class ToGrid(system: SystemRef, size: GridSize) : ViewChanged(system, "grid", size)

        public class ToDetails(system: SystemRef) : ViewChanged(system, "details", null)
    }

    /** Never the words: only the ranges of [queryLength] and [results] are sent. */
    public class SearchUsed(
        public val queryLength: Int,
        public val results: Int,
        public val openedResult: Boolean,
    ) : TelemetryEvent("search_used") {
        override fun props(): Map<String, JsonPrimitive> =
            mapOf(
                "query_length_bucket" to JsonPrimitive(Buckets.queryLength(queryLength)),
                "results_bucket" to JsonPrimitive(Buckets.results(results)),
                "opened_result" to yesNo(openedResult),
            )
    }

    public class FavoriteChanged(
        public val system: SystemRef,
        public val change: FavoriteChange,
    ) : TelemetryEvent("favorite_changed") {
        override fun props(): Map<String, JsonPrimitive> = mapOf("system_id" to JsonPrimitive(system.id), "change" to wire(change))
    }

    /** [emulator] is null when none was chosen or installed for the system; the field is left out then. */
    public class GameLaunched(
        public val system: SystemRef,
        public val emulator: EmulatorRef?,
        public val from: LaunchFrom,
        public val result: LaunchResult,
    ) : TelemetryEvent("game_launched") {
        override fun props(): Map<String, JsonPrimitive> =
            buildMap {
                put("system_id", JsonPrimitive(system.id))
                emulator?.let { put("emulator_id", JsonPrimitive(it.id)) }
                put("from", wire(from))
                put("result", wire(result))
            }
    }

    /** [minutes] is whole minutes; more than [MAX_SESSION_MINUTES] is sent as that. */
    public class GameSession(
        public val system: SystemRef,
        public val emulator: EmulatorRef,
        public val minutes: Int,
    ) : TelemetryEvent("game_session") {
        override fun props(): Map<String, JsonPrimitive> =
            mapOf(
                "system_id" to JsonPrimitive(system.id),
                "emulator_id" to JsonPrimitive(emulator.id),
                "minutes" to num(minutes.coerceIn(0, MAX_SESSION_MINUTES)),
            )
    }

    public class EmulatorPicked(
        public val system: SystemRef,
        public val emulator: EmulatorRef,
        public val where: PickPlace,
    ) : TelemetryEvent("emulator_picked") {
        override fun props(): Map<String, JsonPrimitive> =
            mapOf("system_id" to JsonPrimitive(system.id), "emulator_id" to JsonPrimitive(emulator.id), "where" to wire(where))
    }

    /**
     * `setting_changed`: the key and value are entries of the app's own enums (the option and its
     * choice), sent in snake case, so a colour value or free text cannot be passed. A switch sends
     * `on` / `off`.
     */
    public class SettingChanged private constructor(
        public val key: String,
        public val value: String,
    ) : TelemetryEvent("setting_changed") {
        public constructor(key: Enum<*>, value: Enum<*>) : this(key.snakeName(), value.snakeName())

        public constructor(key: Enum<*>, isOn: Boolean) : this(key.snakeName(), if (isOn) "on" else "off")

        override fun props(): Map<String, JsonPrimitive> = mapOf("key" to JsonPrimitive(key), "value" to JsonPrimitive(value))
    }

    public class ControllerConnected(
        public val kind: ControllerKind,
    ) : TelemetryEvent("controller_connected") {
        override fun props(): Map<String, JsonPrimitive> = mapOf("kind" to wire(kind))
    }

    public class ArtworkStatus(
        public val downloaded: Int,
        public val failed: Int,
        public val quotaHit: Int,
    ) : TelemetryEvent("artwork_status") {
        override fun props(): Map<String, JsonPrimitive> =
            mapOf("downloaded" to num(downloaded), "failed" to num(failed), "quota_hit" to num(quotaHit))
    }

    public class UpdateCheck(
        public val trigger: UpdateTrigger,
        public val result: UpdateCheckResult,
    ) : TelemetryEvent("update_check") {
        override fun props(): Map<String, JsonPrimitive> = mapOf("trigger" to wire(trigger), "result" to wire(result))
    }

    /** [fromVersion] is the version name the update replaced (`0.0.2-alpha`); any other text is dropped, not sent. */
    public class UpdateInstalled(
        public val fromVersion: String,
    ) : TelemetryEvent("update_installed") {
        override fun isValid(): Boolean = VERSION_NAME.matches(fromVersion)

        override fun props(): Map<String, JsonPrimitive> = mapOf("from_version" to JsonPrimitive(fromVersion))
    }

    public class BackupMade(
        public val kind: BackupKind,
        public val result: OkOrError,
    ) : TelemetryEvent("backup_made") {
        override fun props(): Map<String, JsonPrimitive> = mapOf("kind" to wire(kind), "result" to wire(result))
    }

    public class RestoreDone(
        public val kind: BackupKind,
        public val result: OkOrError,
    ) : TelemetryEvent("restore_done") {
        override fun props(): Map<String, JsonPrimitive> = mapOf("kind" to wire(kind), "result" to wire(result))
    }

    /** [code] is an entry of the app's error enum, never the message shown. */
    public class ErrorShown(
        public val where: ScreenId,
        public val code: Enum<*>,
    ) : TelemetryEvent("error_shown") {
        override fun props(): Map<String, JsonPrimitive> = mapOf("where" to wire(where), "code" to JsonPrimitive(code.snakeName()))
    }

    /** At the first launch, sent at once. */
    internal object FirstOpen : TelemetryEvent("first_open") {
        override val urgent: Boolean get() = true

        override fun props(): Map<String, JsonPrimitive> = emptyMap()
    }

    /** Events the full queue dropped since the last one was delivered. */
    internal class QueueOverflow(
        val dropped: Int,
    ) : TelemetryEvent("queue_overflow") {
        override fun props(): Map<String, JsonPrimitive> = mapOf("dropped" to num(dropped))
    }

    internal class StatsSwitchedOn(
        val where: SwitchPlace,
    ) : TelemetryEvent("stats_switched_on") {
        override fun props(): Map<String, JsonPrimitive> = mapOf("where" to wire(where))
    }

    public companion object {
        /** `game_session.minutes` is capped here (docs/conventions/telemetry.md). */
        public const val MAX_SESSION_MINUTES: Int = 600
    }
}

private fun wire(value: WireValue) = JsonPrimitive(value.wire)

private fun num(value: Number) = JsonPrimitive(value)

private fun yesNo(value: Boolean) = JsonPrimitive(if (value) "yes" else "no")
