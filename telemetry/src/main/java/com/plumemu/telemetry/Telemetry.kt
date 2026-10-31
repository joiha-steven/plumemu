package com.plumemu.telemetry

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** What the app hands events and the statistics switch to; tests of the app use a fake. */
public interface TelemetrySink {
    /** Queues [event] while statistics are on; does nothing while they are off. Never blocks, never throws. */
    public fun track(event: TelemetryEvent)

    /**
     * The statistics switch moved ([where]: setup's Ready step or Settings). Off deletes the queue
     * and sends `stats_declined` once per install, ever; then nothing. On again resumes with the
     * same install id and sends `stats_switched_on`. Setting the value it already has does nothing.
     */
    public fun setEnabled(
        on: Boolean,
        where: SwitchPlace,
    )
}

/** How a send ended, for the worker. */
public enum class SendOutcome {
    /** Everything that could go went (or was dropped as the API says). */
    Done,

    /** The server asked to wait, or there was no network: WorkManager tries again with back-off. */
    Retry,
}

/**
 * Usage statistics (docs/conventions/telemetry.md): one per
 * process, made by the Application with [create] and started with [start].
 *
 * - The first launch makes the install id and sends `first_open` at once.
 * - [track] queues events on the device ([EventQueue]); [TelemetryScheduler] sends them in batches
 *   of at most 200 within 5 minutes, at once when the app goes to the background.
 * - [setEnabled] follows the statistics switch.
 *
 * Every change of state goes through one queue of commands, handled in order by one coroutine, so
 * a `track` made just before a switch-off never lands after it. Sends are one at a time
 * ([sendPending]). Nothing here throws at the app; a failure is one log line.
 */
public class Telemetry internal constructor(
    dir: File,
    /** The `common` object; read once, on the first request. */
    readCommon: () -> JsonObject,
    /** Null when this build has no endpoint it may send to: events are kept, nothing goes. */
    private val sender: Sender?,
    private val scheduler: SendScheduler,
    private val scope: CoroutineScope,
    private val log: (String) -> Unit,
    /**
     * The statistics switch as the app stores it (`advanced.usageStatistics`), read once at start
     * before anything is queued or sent: an install switched off in an earlier version, or whose
     * stored switch differs from the module's, sends nothing it should not.
     * Null: the person has chosen nothing yet (setup not finished, switch never stored); treated as off, no decline.
     */
    private val storedSwitch: suspend () -> Boolean?,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = System::currentTimeMillis,
    /** Android's records of how earlier processes ended (ANR, native crash); empty before Android 11. */
    readExits: () -> List<ExitRecord> = { emptyList() },
    /** The first line of every crash stack ([CrashReports.header]): the version a retrace needs. */
    private val crashHeader: String = "Plumemu",
    /** The log text a problem report attaches (blocking); null when it cannot be read. */
    readLog: () -> String? = { null },
) : TelemetrySink {
    private val store = StateStore(File(dir, StateStore.FILE_NAME), scope)
    internal val queue = EventQueue(File(dir, "queue"), log)

    /** Crash reports waiting for the next send ([CrashCapture] writes them as the app dies). */
    internal val crashes = CrashStore(CrashStore.dirIn(dir), log)
    private val exits = ExitReasons(File(CrashStore.dirIn(dir), ExitReasons.MARKER_NAME), readExits, log)
    private val common by lazy(readCommon)

    private sealed interface Command {
        class Record(val event: TelemetryEvent, val id: String, val time: String) : Command

        class Switch(val on: Boolean, val where: SwitchPlace) : Command

        /** Completed once every command sent before it was handled. */
        class Barrier(val done: CompletableDeferred<Unit>) : Command
    }

    private val inbox = Channel<Command>(Channel.UNLIMITED)
    private val started = AtomicBoolean(false)
    private val loaded = CompletableDeferred<Unit>()
    private val stateLock = Mutex()
    private val sendLock = Mutex()

    /** Problem reports from Settings › About › Report a problem; they go whatever the statistics switch says. */
    public val problems: ProblemReports =
        ProblemReports(
            dir = ProblemReports.dirIn(dir),
            sender = sender,
            scheduler = scheduler,
            ready = {
                start()
                loaded.await()
            },
            installIdIfOn = { state.installId?.takeIf { state.enabled && !unreadable } },
            statisticsOn = { state.enabled },
            common = { common },
            readLog = readLog,
            newId = newId,
            clock = clock,
            log = log,
        )

    /** Written under [stateLock] only, after the store took it. */
    @Volatile
    private var state = TelemetryState(installId = null, enabled = true, declineDecided = false, declinePending = null, firstOpenSent = false)

    /** What [track] checks without waiting: the switch as last set, or as stored once read. */
    @Volatile
    private var enabledHint = true

    /** Set when the state or the stored switch could not be read: this process sends and switches nothing. */
    @Volatile
    private var unreadable = false

    /** When [SendScheduler.sendSoon] was last asked; it is not asked again while that send waits. */
    @Volatile
    private var soonAskedAt = 0L

    /** A send was asked for at once while one may be running (whose KEEP request WorkManager then drops). */
    private val nowAskedDuringSend = AtomicBoolean(false)

    /** Reads the stored state and starts handling events and the switch. Safe to call again. */
    public fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch { run() }
    }

    override fun track(event: TelemetryEvent) {
        // Kept for a crash report even while statistics are off: it stays in memory, and an off
        // install's reports are deleted unsent.
        if (event is TelemetryEvent.ScreenOpened) CrashCapture.screen = event.screen.wire
        if (!enabledHint) return
        inbox.trySend(Command.Record(event, newId(), isoTime(clock())))
    }

    override fun setEnabled(
        on: Boolean,
        where: SwitchPlace,
    ) {
        enabledHint = on
        inbox.trySend(Command.Switch(on, where))
    }

    /** This install's id once read (null before [start] has read it, and never sent while statistics are off). */
    public suspend fun installId(): String? {
        start()
        loaded.await()
        return state.installId
    }

    /** Statistics on, as stored (for crash reports). */
    public suspend fun isEnabled(): Boolean {
        start()
        loaded.await()
        return state.enabled
    }

    /**
     * Sends the pending decline, then the queue in batches, until all went or the server or the
     * network says wait. One send at a time; a second caller waits and finds less to do.
     */
    public suspend fun sendPending(): SendOutcome {
        start()
        loaded.await()
        // What was tracked before this call (the app_background that asked for it) goes in this send.
        val handled = CompletableDeferred<Unit>()
        inbox.trySend(Command.Barrier(handled))
        handled.await()
        val outcome =
            sendLock.withLock {
                withContext(Dispatchers.IO) {
                    // WorkManager keeps a running "now" send and drops a new request for one: what was
                    // asked for during this run (a decline, app_background) is sent by this run.
                    var rounds = 0
                    var result: SendOutcome
                    do {
                        nowAskedDuringSend.set(false)
                        result = sendAll()
                    } while (result == SendOutcome.Done && nowAskedDuringSend.get() && ++rounds < MAX_SEND_REPEATS)
                    // Reports the person sent go too, whatever the events did (and whatever the switch says).
                    val reports = problems.sendPending()
                    if (result == SendOutcome.Retry || reports == SendOutcome.Retry) SendOutcome.Retry else SendOutcome.Done
                }
            }
        // The "later" send that was asked for has run: the next event asks again.
        soonAskedAt = 0L
        return outcome
    }

    private suspend fun run() {
        val firstLaunch =
            try {
                load().also { first -> followStoredSwitch(first) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                log("Statistics state could not be read (${e.javaClass.simpleName}); nothing sent this time")
                offForThisProcess()
                false
            } finally {
                // Before anything waiting for the state goes on: a send then finds the crashes ready.
                takeCrashes()
                loaded.complete(Unit)
            }
        if (firstLaunch && state.enabled) {
            // Only an install with statistics on is counted at its first launch; an opt-in install is counted at its first switch-on.
            change { it.copy(firstOpenSent = true) }
            record(TelemetryEvent.FirstOpen, newId(), isoTime(clock()))
        } else {
            val now = state
            when {
                now.declinePending != null -> scheduler.sendNow()
                now.enabled && queue.size() > 0 -> askSoon()
            }
        }
        for (command in inbox) {
            try {
                when (command) {
                    is Command.Record -> record(command.event, command.id, command.time)
                    is Command.Switch -> if (command.on) switchOn(command.where) else switchOff(command.where)
                    is Command.Barrier -> command.done.complete(Unit)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                log("Statistics: ${command.javaClass.simpleName} failed (${e.javaClass.simpleName})")
            }
        }
    }

    /** Reads the stored state; true at the first launch, when the install id is made. */
    private suspend fun load(): Boolean =
        stateLock.withLock {
            var read = store.read()
            val first = read.installId == null
            if (first) {
                read = read.copy(installId = newId())
                store.write(read)
            }
            state = read
            enabledHint = read.enabled
            // A switch-off whose queue deletion did not finish (the process died between the two), or a
            // crash written while off: neither is ever sent.
            if (!read.enabled) {
                queue.clear()
                crashes.clear()
            }
            first
        }

    /**
     * Makes the module's switch equal to the app's stored one before anything is queued or sent.
     * On a first run with the stored switch off (the default), nothing is sent, not
     * even a decline: the person never said yes. The decline is sent only when a person who had it on
     * switches it off.
     * Unreadable: nothing is sent by this process (the app's follower moves it later).
     */
    private suspend fun followStoredSwitch(firstLaunch: Boolean) {
        val stored =
            try {
                storedSwitch()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                log("Statistics: the stored switch could not be read (${e.javaClass.simpleName}); nothing sent this time")
                offForThisProcess()
                return
            }
        when {
            // Nothing chosen (setup never finished): no decline for a "no" nobody said.
            stored == null -> if (state.enabled) startOff()
            stored == state.enabled -> Unit
            stored -> switchOn(SwitchPlace.Settings)
            firstLaunch -> startOff()
            else -> switchOff(SwitchPlace.Settings)
        }
        if (firstLaunch && stored != true) log("Statistics: off from the first launch; nothing sent")
    }

    /**
     * At start, before any send: Android's new ANR and native-crash records join the crash files,
     * at most [CrashStore.MAX_KEPT] are kept, and a send is asked for at once when any waits. Off:
     * the files go unsent and the records are only marked as seen. Unreadable state: left alone.
     */
    private fun takeCrashes() {
        if (unreadable) return
        try {
            val on = state.enabled
            val installId = state.installId.orEmpty()
            // The same exit always gets the same id, so a report written again after a failure is stored once.
            val taken = exits.since(crashHeader, { exitId(installId, it) }, report = on)
            taken.items.forEach { (time, item) -> crashes.write(item, time, item.getValue("id").jsonPrimitive.content) }
            // Only once every report is in its file: a failed write leaves them to the next start.
            taken.seenUpTo?.let(exits::markSeen)
            if (!on) {
                crashes.clear()
                return
            }
            crashes.prune()
            if (!crashes.isEmpty()) askNow()
        } catch (e: Exception) {
            log("Crash reports not prepared (${e.javaClass.simpleName})")
        }
    }

    private suspend fun offForThisProcess() {
        // A later switch would write a state that was never read (dropping the install id and the
        // decline flag, so the one decline could be sent twice): ignore switches until a restart.
        unreadable = true
        enabledHint = false
        stateLock.withLock { state = state.copy(enabled = false) }
    }

    private fun record(
        event: TelemetryEvent,
        id: String,
        time: String,
    ) {
        if (!state.enabled) return
        if (!event.isValid()) {
            log("Statistics: ${event.name} dropped (a field is not of the allowed shape)")
            return
        }
        queue.append(eventJson(event, id, time), newId) { isoTime(clock()) }
        if (event.urgent) askNow()
        askSoon()
    }

    private fun askNow() {
        nowAskedDuringSend.set(true)
        scheduler.sendNow()
    }

    private fun askSoon() {
        val now = clock()
        if (soonAskedAt != 0L && now - soonAskedAt < TelemetryScheduler.BATCH_DELAY_MILLIS) return
        soonAskedAt = now
        scheduler.sendSoon()
    }

    private suspend fun switchOff(where: SwitchPlace) {
        if (unreadable) return
        val next =
            stateLock.withLock {
                val now = state
                if (!now.enabled) return
                // one decline per install, ever; switching off again sends nothing.
                val next =
                    now.copy(
                        enabled = false,
                        declineDecided = true,
                        declinePending = if (now.declineDecided) now.declinePending else where,
                    )
                store.write(next)
                state = next
                next
            }
        queue.clear()
        crashes.clear()
        if (next.declinePending != null) askNow()
    }

    private suspend fun change(edit: (TelemetryState) -> TelemetryState) =
        stateLock.withLock {
            state = edit(state).also { store.write(it) }
        }

    /** Off with no decline decided (first launch, or nothing chosen): only a later on-then-off sends one. */
    private suspend fun startOff() {
        if (unreadable) return
        change { it.copy(enabled = false) }
        queue.clear()
        crashes.clear()
    }

    private suspend fun switchOn(where: SwitchPlace) {
        if (unreadable) return
        var firstOpenOwed = false
        stateLock.withLock {
            val now = state
            if (now.enabled) return
            val next = now.copy(enabled = true, firstOpenSent = true)
            store.write(next)
            state = next
            firstOpenOwed = !now.firstOpenSent
        }
        // The dashboard counts new installs by first_open: an opt-in install is counted when it first switches on.
        if (firstOpenOwed) record(TelemetryEvent.FirstOpen, newId(), isoTime(clock()))
        record(TelemetryEvent.StatsSwitchedOn(where), newId(), isoTime(clock()))
    }

    /**
     * The pending decline, then batches, each POST made while holding [stateLock]: a switch-off
     * waits for a POST in flight and no batch starts after it (its queue is deleted first). A
     * decline that became pending during the run goes in the same run. Bounded, so a queue file
     * that cannot be changed (a read-only folder) cannot make it loop.
     */
    private suspend fun sendAll(): SendOutcome {
        val sender = sender ?: return SendOutcome.Done
        val overflowSent = HashSet<String>()
        // The crash cap of the day (429) or a server error: crashes wait, events still go.
        var crashesWait = false
        repeat(MAX_ROUNDS) {
            val done =
                stateLock.withLock {
                    val now = state
                    val decline = now.declinePending
                    if (decline != null) {
                        val body =
                            buildJsonObject {
                                put("v", JsonPrimitive(API_VERSION))
                                put("where", JsonPrimitive(decline.wire))
                                put("common", common)
                            }
                        if (sender.post(Sender.DECLINED, body) == Answer.Retry) return SendOutcome.Retry
                        val next = now.copy(declinePending = null)
                        store.write(next)
                        state = next
                        return@withLock false
                    }
                    val installId = now.installId
                    if (!now.enabled || installId == null) return@withLock true
                    if (!crashesWait) {
                        when (sendCrashBatch(sender, crashes, installId, common, log)) {
                            CrashBatch.Sent -> return@withLock false
                            CrashBatch.Wait -> crashesWait = true
                            CrashBatch.Nothing -> Unit
                        }
                    }
                    sendBatch(sender, installId, overflowSent) ?: return SendOutcome.Retry
                }
            if (done) return if (crashesWait) SendOutcome.Retry else SendOutcome.Done
        }
        log("Statistics: send stopped after $MAX_ROUNDS requests; the rest goes next time")
        return SendOutcome.Done
    }

    /** One batch; true when there was nothing to send, null when the server or network says wait. Under [stateLock]. */
    private fun sendBatch(
        sender: Sender,
        installId: String,
        overflowSent: MutableSet<String>,
    ): Boolean? {
        // An overflow count already sent in this run whose file could not be updated waits for the next run.
        val overflow = queue.overflow()?.takeIf { it.id !in overflowSent }
        val entries = queue.peek(MAX_BATCH_EVENTS - (if (overflow != null) 1 else 0), MAX_BATCH_EVENT_BYTES)
        if (entries.isEmpty() && overflow == null) return true
        val events =
            buildList {
                overflow?.let { add(eventJson(TelemetryEvent.QueueOverflow(it.dropped), it.id, it.time)) }
                entries.forEach { add(it.json) }
            }
        val body =
            buildJsonObject {
                put("v", JsonPrimitive(API_VERSION))
                put("install_id", JsonPrimitive(installId))
                put("common", common)
                put("events", JsonArray(events))
            }
        if (sender.post(Sender.EVENTS, body) == Answer.Retry) return null
        queue.remove(entries.mapTo(HashSet()) { it.id })
        overflow?.let {
            overflowSent += it.id
            if (!queue.overflowDelivered(it, newId) { isoTime(clock()) }) log("Statistics: the queue's overflow count could not be updated")
        }
        return false
    }

    public companion object {
        public const val LOG_TAG: String = "Telemetry"

        /** The module's folder under `noBackupFilesDir`. */
        internal const val DIR_NAME = "telemetry"

        internal const val API_VERSION = 1

        /** API limit: 200 events and 64 KB a request; the rest of the body is well under 4 KB. */
        internal const val MAX_BATCH_EVENTS = 200
        internal const val MAX_BATCH_EVENT_BYTES = 60_000

        /** Requests in one send at most: a full queue is 10 batches; the rest goes next time. */
        internal const val MAX_ROUNDS = 30

        /** Sends again in the same run when asked to during it, at most this often. */
        internal const val MAX_SEND_REPEATS = 3

        /**
         * The process's telemetry, sending to [config]'s endpoint. Its files are in the app's
         * no-backup folder (`telemetry/`). Call [start] after. [storedSwitch] reads the app's
         * stored statistics switch; it is awaited at start, on a background thread.
         */
        public fun create(
            context: Context,
            config: TelemetryConfig,
            storedSwitch: suspend () -> Boolean?,
        ): Telemetry {
            val app = context.applicationContext
            // Every line also lands in the log file Export logs shares.
            val log: (String) -> Unit = { AppLog.w(LOG_TAG, it) }
            val base = config.apiBase()
            if (base == null) log("Statistics: this build may not send to its endpoint; nothing will be sent")
            // Built on the first request, on a background thread: building it loads the trust store,
            // which must not happen on the main thread at startup.
            val client = {
                OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .callTimeout(60, TimeUnit.SECONDS)
                    // A redirect answer is dropped like any other 3xx/4xx, never followed (HTTPS only).
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build()
            }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> log("Statistics failed (${e.javaClass.simpleName})") })
            return Telemetry(
                dir = File(app.noBackupFilesDir, DIR_NAME),
                readCommon = { commonOrMinimal(app, config, log) },
                sender = base?.let { Sender(client, it, log) },
                scheduler = TelemetryScheduler(app, log),
                scope = scope,
                log = log,
                storedSwitch = storedSwitch,
                readExits = ExitReasons.reader(app, log),
                crashHeader = CrashReports.header(config),
                readLog = { ProblemReports.snapshotText(app, CrashReports.header(config), log) },
            )
        }

        private fun commonOrMinimal(
            app: Context,
            config: TelemetryConfig,
            log: (String) -> Unit,
        ): JsonObject =
            try {
                CommonFields.read(app, config)
            } catch (e: RuntimeException) {
                log("Statistics: device details could not be read (${e.javaClass.simpleName})")
                buildJsonObject {
                    put("app_version", JsonPrimitive(config.appVersion))
                    put("version_code", JsonPrimitive(config.versionCode))
                    put("build_type", JsonPrimitive(config.buildType.wire))
                }
            }

        /** A name-based UUID of the install and the exit: the same exit, the same crash id. */
        internal fun exitId(
            installId: String,
            record: ExitRecord,
        ): String = UUID.nameUUIDFromBytes("$installId:${record.kind.wire}:${record.timestamp}".toByteArray(Charsets.UTF_8)).toString()

        internal fun eventJson(
            event: TelemetryEvent,
            id: String,
            time: String,
        ): JsonObject =
            buildJsonObject {
                put("id", JsonPrimitive(id))
                put("name", JsonPrimitive(event.name))
                put("time", JsonPrimitive(time))
                put("props", JsonObject(event.props()))
            }

        /** UTC ISO-8601 with milliseconds (for example `YYYY-MM-DDThh:mm:ss.sssZ`); java.time needs API 26. */
        internal fun isoTime(millis: Long): String =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(Date(millis))
    }
}
