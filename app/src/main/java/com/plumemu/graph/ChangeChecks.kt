package com.plumemu.graph

import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.library.scan.FolderChanges
import com.plumemu.library.scan.ScanStats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What a change check needs from [StartupWork]: its scan slot and a scan of some folders. */
internal interface CheckSlot {
    /** Takes the scan slot without showing it as busy; false while a scan runs, waits or is asked for. */
    fun claimForCheck(): Boolean

    /** Gives the slot back; a scan asked for meanwhile runs now. */
    suspend fun releaseAfterCheck()

    /**
     * Scans [folderIds] while holding the slot, shown as busy; the check found [unavailable]
     * unreachable. Each scanned folder's stats; null when the scan could not run (logged).
     */
    suspend fun scanFolders(folderIds: Set<Long>, unavailable: Set<Long>): Map<Long, ScanStats>?

    /** The folders the last check could not reach, for Settings › ROM folders. */
    fun showUnavailable(folderIds: Set<Long>)
}

/**
 * When the change check runs. Only on two events,
 * never on a timer of its own; the delays below are one-shot follow-ups of a mount.
 *
 * - [onFront]: Plumemu came back to the front; at most once per [FRONT_INTERVAL_MS], unless a check
 *   is owed. Nothing runs between [onStop] and the next [onFront] (a game is playing): a check that
 *   falls due then (a mount's, its retry, one waiting for the slot) is owed and runs on [onFront].
 *   A return while a scan holds the slot is owed too and runs when the slot is free.
 * - [onMount]: storage was mounted. Mounts closer than [MOUNT_INTERVAL_MS] to the last mount check
 *   join one check at the end of that interval. A mount that cannot be checked now (before the
 *   startup scan ended, or while a scan holds the slot) is remembered and checked once the slot is
 *   free ([onSlotFreed], [enable]). A folder unreachable right after a mount (the documents provider
 *   may not see the volume yet) gets one more check [MOUNT_RETRY_MS] later, then no more.
 * - A folder whose check-triggered scan changed nothing yet still reads as changed (a provider with
 *   no directory times, a subdirectory that always fails) is passed over by checks for
 *   [BACK_OFF_MS]; the startup scan and Settings' Rescan still read it.
 * - A box-art run is asked for when a check's scan added games, but not while a file it saw may
 *   still be copied ([ScanStats.filesStillWriting]): then it is owed to the next check's scan that
 *   sees no file being written (the directory is read again then).
 *
 * [clock] counts time asleep too (`SystemClock.elapsedRealtime`): a card swapped while the device
 * slept is checked on waking even within the interval measured before the sleep.
 */
internal class ChangeChecks(
    private val scope: CoroutineScope,
    private val settings: suspend () -> PlumSettings?,
    /** Null when the check failed (logged by the caller). */
    private val changedFolders: suspend (Set<Long>?) -> FolderChanges?,
    private val scanEnded: suspend () -> Unit,
    private val slot: CheckSlot,
    private val clock: () -> Long,
    private val info: (String) -> Unit,
    private val log: (String) -> Unit,
    /** Told what a check that scanned found (one entry per scanned folder); the home's "+24 games" notice. */
    private val onFound: (Collection<ScanStats>) -> Unit = {},
) {
    /** Why a check runs, strongest first: a check owed for a mount may still retry once. */
    private enum class Reason(val label: String) {
        Mount("storage mounted"),
        MountRetry("retry after a mount"),
        Front("back in front"),
    }

    private val lock = Any()
    private var isEnabled = false
    private var isOffForGood = false

    /** Between the Activity's `onStart` and `onStop`: nothing runs while a game plays. */
    private var isInFront = false
    private var lastFrontCheckAt: Long? = null
    private var lastMountCheckAt: Long? = null

    /** A mount check is due and has not asked the folders yet: later mounts join it. */
    private var isMountDue = false

    /** A check that could not run (stopped, or the slot was held); it runs at the next chance in front. */
    private var owed: Reason? = null

    /** Mount checks and retries waiting for their moment; cancelled, and owed, on [onStop]. */
    private val delayed = HashMap<Job, Reason>()
    private var boxArtOwed = false
    private val backedOffUntil = HashMap<Long, Long>()

    /** The startup scan ended: checks may run; one owed meanwhile runs now if Plumemu is in front. */
    fun enable() {
        synchronized(lock) {
            if (isOffForGood) return
            isEnabled = true
        }
        runOwedIfFree()
    }

    /** A restore replaced the library in this process: no checks (the person scans when they choose). */
    fun turnOffAfterRestore() =
        synchronized(lock) {
            isOffForGood = true
            owed = null
        }

    /** The Activity started before the first frame: not a return yet, but checks owed may run from now. */
    fun markInFront() = synchronized(lock) { isInFront = true }

    /** The Activity stopped (a game, another app, the screen off): what is due waits for the next [onFront]. */
    fun onStop() {
        val cancelled = synchronized(lock) {
            isInFront = false
            isMountDue = false
            delayed.toMap().also { delayed.clear() }
        }
        for ((job, reason) in cancelled) {
            job.cancel()
            owe(reason)
        }
        if (cancelled.isNotEmpty()) info("Change check: ${cancelled.size} due checks wait until Plumemu is back in front")
    }

    fun onFront(): Job? {
        val now = clock()
        val reason = synchronized(lock) {
            isInFront = true
            if (!isEnabled) {
                if (!isOffForGood) info("Change check (${Reason.Front.label}) skipped: the startup scan has not ended, it reads the folders")
                return null
            }
            // A check owed while stopped runs now, whatever the interval says.
            val due = owed.also { owed = null }
            val last = lastFrontCheckAt
            if (due == null && last != null && now - last < FRONT_INTERVAL_MS) {
                info("Change check (${Reason.Front.label}) skipped: the last one was ${(now - last) / 1_000} s ago")
                return null
            }
            lastFrontCheckAt = now
            due ?: Reason.Front
        }
        return scope.launch { runCheck(reason) }
    }

    fun onMount(): Job? {
        val now = clock()
        val wait: Long
        synchronized(lock) {
            if (isOffForGood) return null
            if (isMountDue) {
                info("Change check (${Reason.Mount.label}): joins the mount check already due")
                return null
            }
            val last = lastMountCheckAt
            wait = if (last == null) 0 else maxOf(0, MOUNT_INTERVAL_MS - (now - last))
            isMountDue = true
            lastMountCheckAt = now + wait
        }
        if (wait > 0) info("Change check (${Reason.Mount.label}): in ${wait / 1_000.0} s, mounts close together are checked once")
        // From the moment it runs a new mount gets a check of its own: this one may have asked the folders already.
        return later(wait, Reason.Mount) { isMountDue = false }
    }

    /** The scan slot is free and no scan is asked for: a check owed while it was held runs now, in front only. */
    fun onSlotFreed() = runOwedIfFree()

    private fun runOwedIfFree() {
        val reason = synchronized(lock) {
            if (!isEnabled || !isInFront) return
            owed.also { owed = null } ?: return
        }
        scope.launch { runCheck(reason) }
    }

    private fun owe(reason: Reason) =
        synchronized(lock) {
            if (isOffForGood) return
            owed = owed?.let { if (it.ordinal <= reason.ordinal) it else reason } ?: reason
        }

    /** Runs [reason]'s check after [wait] ms unless [onStop] comes first; [atStart] runs under the lock. */
    private fun later(wait: Long, reason: Reason, atStart: () -> Unit = {}): Job {
        lateinit var job: Job
        job =
            scope.launch(start = CoroutineStart.LAZY) {
                if (wait > 0) delay(wait)
                synchronized(lock) {
                    delayed.remove(job)
                    atStart()
                }
                runCheck(reason)
            }
        synchronized(lock) { delayed[job] = reason }
        job.start()
        return job
    }

    private suspend fun runCheck(reason: Reason) {
        val outcome = synchronized(lock) {
            when {
                isOffForGood -> return
                !isInFront -> "waits until Plumemu is back in front"
                !isEnabled || !slot.claimForCheck() -> "waits: the startup scan or another scan holds the slot; it runs when the slot is free"
                else -> null
            }
        }
        if (outcome == null) {
            checkHoldingSlot(reason)
        } else {
            owe(reason)
            info("Change check (${reason.label}) $outcome")
        }
    }

    private suspend fun checkHoldingSlot(reason: Reason) {
        try {
            check(reason)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Class only: providers put document URIs in their messages.
            log("Startup change check failed (${e.javaClass.simpleName})")
        } finally {
            slot.releaseAfterCheck()
        }
    }

    private suspend fun check(reason: Reason) {
        val s = settings() ?: return
        // Find new games automatically off: the person scans when they choose (Settings › Rescan library).
        if (!s.flags.setupCompleted || !s.library.scanOnStartup) return
        val changes = changedFolders(null) ?: return
        slot.showUnavailable(changes.unavailable.keys)
        if (changes.unavailable.isNotEmpty()) afterUnavailable(reason, changes.unavailable.keys)
        val now = clock()
        val changed = synchronized(lock) {
            backedOffUntil.values.removeAll { it <= now }
            changes.changedFolderIds - backedOffUntil.keys
        }
        if (changed.isEmpty()) return
        info("Change check (${reason.label}): scanning changed folders ${changed.sorted()}")
        val stats = slot.scanFolders(changed, changes.unavailable.keys) ?: return
        // A folder with no records yet is on its first full scan: that is setup or a new folder, not news.
        onFound(stats.filterKeys { it !in changes.neverScannedFolderIds }.values)
        askForBoxArt(stats.values)
        backOffFoldersThatStayChanged(stats)
    }

    private fun afterUnavailable(reason: Reason, folderIds: Set<Long>) {
        when (reason) {
            Reason.Mount -> {
                info("Change check: folders ${folderIds.sorted()} unreachable right after a mount; one more check in ${MOUNT_RETRY_MS / 1_000} s")
                later(MOUNT_RETRY_MS, Reason.MountRetry)
            }
            Reason.MountRetry ->
                info("Change check: folders ${folderIds.sorted()} still unreachable after a mount; checked again when Plumemu comes back to the front")
            Reason.Front -> Unit
        }
    }

    private suspend fun askForBoxArt(stats: Collection<ScanStats>) {
        val stillWriting = stats.sumOf { it.filesStillWriting }
        val newOrChanged = stats.sumOf { it.gamesAdded + it.gamesUpdated }
        val ask = synchronized(lock) {
            if (stillWriting > 0) {
                if (newOrChanged > 0) boxArtOwed = true
                false
            } else {
                (stats.sumOf { it.gamesAdded } > 0 || boxArtOwed).also { boxArtOwed = false }
            }
        }
        if (stillWriting > 0) info("Change check: $stillWriting files may still be copied; box art waits for the next check")
        if (ask) {
            info("Change check: asking for box art (games added or owed)")
            scanEnded()
        }
    }

    private suspend fun backOffFoldersThatStayChanged(stats: Map<Long, ScanStats>) {
        val idle = stats.filterValues { it.gamesAdded + it.gamesUpdated + it.gamesRemoved == 0 && it.filesStillWriting == 0 }.keys
        if (idle.isEmpty()) return
        val stuck = changedFolders(idle)?.changedFolderIds.orEmpty()
        if (stuck.isEmpty()) return
        val until = clock() + BACK_OFF_MS
        synchronized(lock) { stuck.forEach { backedOffUntil[it] = until } }
        info(
            "Change check: folders ${stuck.sorted()} still read as changed after a scan that changed nothing " +
                "(no directory times, or a directory that cannot be read); checks pass them over for ${BACK_OFF_MS / 60_000} min",
        )
    }

    companion object {
        /** At most one check per this long when Plumemu comes back to the front (it does after every game). */
        const val FRONT_INTERVAL_MS = 30_000L

        /** Mounts closer together than this are checked once. */
        const val MOUNT_INTERVAL_MS = 5_000L

        /** How long after a mount a folder that was unreachable is checked once more. */
        const val MOUNT_RETRY_MS = 5_000L

        /** How long checks pass over a folder that reads as changed after a scan that changed nothing. */
        const val BACK_OFF_MS = 10 * 60_000L
    }
}
