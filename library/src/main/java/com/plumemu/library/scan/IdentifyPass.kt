package com.plumemu.library.scan

import android.util.Log
import androidx.room.withTransaction
import com.plumemu.coredata.db.ArcadeCopyRow
import com.plumemu.coredata.db.GameToIdentify
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.identify.IdentifyRequest
import com.plumemu.identify.Identification
import com.plumemu.identify.Identifier
import com.plumemu.identify.LaunchFileSystems
import com.plumemu.identify.arcade.ArcadeSystems
import com.plumemu.identify.arcade.ArcadeTitles
import com.plumemu.identify.fingerprint.FingerprintReader
import com.plumemu.identify.fingerprint.Fingerprints
import com.plumemu.identify.name.Region
import com.plumemu.identify.name.TitleCleaner
import com.plumemu.identify.pack.PackStore
import com.plumemu.identify.source.FileSource
import com.plumemu.library.catalog.RegionPreference
import com.plumemu.library.naming.ArcadeCopies
import com.plumemu.library.naming.ArcadeTitleClashes
import com.plumemu.library.naming.FingerprintCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * The name packs and the identifier, one of each per process (the app graph builds them lazily):
 * two stores on one directory would each map their own copy of the packs.
 */
class GameNaming(val packs: PackStore, val identifier: Identifier)

/** What one folder's identification pass did, for the log and the timing test. */
data class NamingStats(
    val gamesNamed: Int,
    /** Files opened and read (QUICK: a few KB each). */
    val filesRead: Int,
    /** Games named from the fingerprints cached by an earlier pass, without opening the file. */
    val fromCache: Int,
    /** Files that could not be read this time (I/O error, provider failure): named next scan. */
    val notRead: Int,
    /** Arcade BIOS and device sets taken off the lists. */
    val hidden: Int,
    /** Games of systems whose pack ships but could not be installed: left for a later scan. */
    val waitingForPack: Int = 0,
)

/**
 * The second pass of a scan: names the rows of one folder that are new or whose file
 * changed (`needs_identify`), right after the walk listed them under their file names, so the
 * library shows at once and the names arrive in batches. Measured on the 10,000-file library:
 * opening every file through the documents provider
 * costs more than the whole walk, so it is not done inside the walk.
 *
 * Per game: cached fingerprints when the file still has the size and time they were read at
 * (nothing is opened), otherwise the file is opened and read at QUICK depth; arcade systems are
 * named from the short name alone, launch-file systems (`steam`, [LaunchFileSystems]) from the file name. A file that cannot be read keeps its row and its name and is
 * tried again by the next scan. Results are written per page in one transaction that re-reads
 * the stored trust and the person's fixes, so a fix made while the pass runs is never undone.
 * Arcade sets of one game in one directory are one row at the end of every pass (`groupArcadeCopies`,
 * [ArcadeCopies]): the person's hidden and favourite marks and play time change which copy shows,
 * and nothing tells the pass they changed, so it looks again each time (the arcade pack by short
 * name only, no file opened). Arcade games show short titles; then shown sets of one system in
 * this folder that would show the same one get their full descriptions instead
 * (`separateArcadeTitles`), when this pass named an arcade row, the walk removed games or the
 * grouping changed.
 *
 * Files of a page are opened [readers] at a time: on an SD card each open costs a
 * seek of 4-8 ms, which overlaps when several are in flight. The page's answers are kept in page
 * order and written in one transaction as before, so the names, the writes and the progress
 * ("N of M", once per page) are the same as one at a time; only the order of log lines differs.
 * A worker checks for cancellation before each file. A failing file is logged and counted and the
 * others go on; access revoked or the folder gone stops every worker and drops the page, as the
 * single reader did. Counters are atomic; the pack states and [SiblingFiles] lock.
 *
 * Logs carry folder and game ids and exception classes, never names.
 */
internal class IdentifyPass(
    private val folder: RomFolderEntity,
    private val tree: DocumentTree,
    private val database: PlumDatabase,
    private val naming: GameNaming,
    /** The walk before this pass removed games: an arcade clash may be gone. */
    private val gamesRemoved: Boolean = false,
    /** The person's region order: which arcade copy of a game shows ([ArcadeCopies]). */
    private val preferredRegions: List<Region> = RegionPreference.DEFAULT,
    /** Files opened at the same time ([NAMING_READERS] in the app); 1 reads in the calling coroutine, one after another. */
    private val readers: Int = NAMING_READERS,
    /** Where the [readers] run: their opens and reads block. */
    private val readerDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val emit: suspend (ScanEvent) -> Unit,
) {
    private class Named(val game: GameToIdentify, val identification: Identification, val fromCache: Boolean = false)

    /** A system's name pack as this pass sees it. */
    private sealed interface PackState {
        data class Ready(val version: Long) : PackState

        /** The system has no pack (none shipped, none installed): names come from the file. */
        data object None : PackState

        /** A pack ships but could not be installed: its games wait for a later scan. */
        data object Unavailable : PackState
    }

    private val packStates = HashMap<String, PackState>()

    private class FolderGone(val reason: String) : Exception()

    private val siblings = SiblingFiles(tree)
    // Written by the page's writer (one coroutine at a time).
    private var named = 0
    private var hidden = 0

    // Counted by the readers, several at once.
    private val read = AtomicInteger()
    private val fromCache = AtomicInteger()
    private val notRead = AtomicInteger()
    private val waitingForPack = AtomicInteger()

    /** Time spent opening files and reading their headers, summed over the readers. */
    private val openAndReadNanos = AtomicLong()

    /** This pass wrote the name of at least one arcade row: its folder's clashes are looked at again. */
    private var arcadeNamed = false

    suspend fun run(): NamingStats {
        val dao = database.identifyDao()
        // Before anything is named: a row left out here that the pack no longer knows afterwards has left the grouping.
        val leftOutBefore = dao.arcadeLeftOut(folder.id).toHashSet()
        renameForNewPacks()
        val total = dao.countToIdentify(folder.id)
        if (total > 0) {
            val started = System.nanoTime()
            val handled = nameGames(total)
            ScanPhaseLog.log(
                folder.id,
                ScanPhaseLog.NAMING,
                System.nanoTime() - started,
                handled,
                "${read.get()} opened, open and read ${ScanPhaseLog.millis(openAndReadNanos.get())} ms summed over $readers at a time, " +
                    "${fromCache.get()} from cache",
            )
        }
        val regrouped = groupArcadeCopies(leftOutBefore)
        // Only when an arcade row was named, a file deleted or the shown copies changed: a rescan
        // with nothing changed looks no description up.
        if (arcadeNamed || gamesRemoved || regrouped) separateArcadeTitles()
        return stats()
    }

    /** @return the rows looked at (named, not read, waiting for a pack), for the phase log. */
    private suspend fun nameGames(total: Int): Int {
        val dao = database.identifyDao()
        for (systemId in dao.systemsToIdentify(folder.id)) {
            if (packStateOf(systemId) == PackState.Unavailable) {
                Log.w(LOG_TAG, "Folder ${folder.id}: name pack of $systemId unavailable; its games are named by a later scan")
            }
            if (LaunchFileSystems.isLaunchFile(systemId)) {
                Log.i(LOG_TAG, "Folder ${folder.id}: games of $systemId are named from their file names, never opened (${LaunchFileSystems.REASON})")
            }
        }
        var afterId = 0L
        var handled = 0
        try {
            while (true) {
                val page = dao.gamesToIdentify(folder.id, afterId, PAGE_SIZE)
                if (page.isEmpty()) break
                val results = identifyPage(page)
                write(results)
                handled += page.size
                afterId = page.last().id
                emit(ScanEvent.Named(folder.id, named, total))
            }
        } catch (e: FolderGone) {
            Log.w(LOG_TAG, "Folder ${folder.id}: naming stopped (${e.reason}); the next scan names the rest")
        }
        return handled
    }

    /**
     * The answers for [page], in page order. [readers] workers take the next game in turn; each
     * answer goes into its game's slot, so the order does not depend on which file read first.
     * [FolderGone] from one worker cancels the others (the scope fails) and reaches [nameGames].
     */
    private suspend fun identifyPage(page: List<GameToIdentify>): List<Named> {
        if (readers <= 1 || page.size <= 1) {
            return page.mapNotNull { game ->
                currentCoroutineContext().ensureActive()
                identifyOne(game)
            }
        }
        val answers = arrayOfNulls<Named>(page.size)
        val packFailed = BooleanArray(page.size)
        val next = AtomicInteger()
        coroutineScope {
            repeat(minOf(readers, page.size)) {
                launch(readerDispatcher) {
                    while (true) {
                        val index = next.getAndIncrement()
                        if (index >= page.size) break
                        currentCoroutineContext().ensureActive()
                        answers[index] = identifyOne(page[index]) { packFailed[index] = true }
                    }
                }
            }
        }
        // coroutineScope returned: every worker finished, and their writes to the slots are visible here.
        return keepBeforePackFailures(page, answers, packFailed)
    }

    /**
     * One at a time, a pack that fails a lookup is reinstalled or given up before the next game of
     * its system is looked at, so that game and every later one wait for a later scan. Read in
     * parallel, later games may have been answered by the failing pack before it was reported; they
     * wait too, as one reader would have left them (the pack's games are named by a later scan).
     */
    private fun keepBeforePackFailures(page: List<GameToIdentify>, answers: Array<Named?>, packFailed: BooleanArray): List<Named> {
        val firstFailure = HashMap<String, Int>()
        for (index in page.indices) if (packFailed[index]) firstFailure.getOrPut(page[index].systemId) { index }
        return answers.withIndex().mapNotNull { (index, answer) ->
            val failedAt = answer?.let { firstFailure[it.game.systemId] }
            if (failedAt != null && index > failedAt) {
                waitingForPack.incrementAndGet()
                null
            } else {
                answer
            }
        }
    }

    /**
     * Decides which arcade copy of each game shows ([ArcadeCopies]) over every arcade row of this
     * folder that is not "not a game", and writes `is_primary_copy` where it changed (nothing
     * else: marks stay on their rows). Sets are looked up in the arcade pack by short name, the
     * parent's too; no file is opened. Left as it is when the pack is not ready or a lookup fails:
     * not knowing a set is not knowing it is a copy. A mark the person sets while this runs
     * counts at the next scan. A row of [leftOutBefore] (left out by the grouping before this pass)
     * whose set the pack no longer knows has left the grouping: it is shown again at once (it would
     * otherwise stay hidden, its directory unchanged), and its directory is listed again by the
     * next scan, so the catalogue's regional-copy rule decides it from then on.
     *
     * @return true when a row's `is_primary_copy` changed.
     */
    private suspend fun groupArcadeCopies(leftOutBefore: Set<Long>): Boolean {
        val dao = database.identifyDao()
        val rows = dao.arcadeCopies(folder.id, ArcadeSystems.IDS.toList())
        if (rows.isEmpty()) return false
        if (rows.any { packStateOf(it.systemId) !is PackState.Ready }) {
            Log.w(LOG_TAG, "Folder ${folder.id}: arcade pack unavailable; copies not grouped")
            return false
        }
        val looked = HashMap<String, Identification>()
        val released = ArrayList<ArcadeCopyRow>()
        val copies = try {
            rows.mapNotNull { row ->
                val identify = { shortName: String ->
                    looked.getOrPut(shortName) { naming.identifier.identify(IdentifyRequest(row.systemId, shortName, cleanedTitle = null)) }
                }
                // A set the pack does not know, or a BIOS or device set the pass has not marked yet,
                // is not the arcade grouping's: it keeps what the catalogue gave it.
                val found = ArcadeCopies.find(ArcadeSystems.shortNameOf(row.fileName), identify) as? ArcadeCopies.Found.Known
                if (found == null) {
                    if (row.id in leftOutBefore && !row.isPrimaryCopy) released += row
                    return@mapNotNull null
                }
                ArcadeCopies.copyOf(row, found.set)
            }
        } catch (e: ArcadeCopies.NotLookedUp) {
            Log.w(LOG_TAG, "Folder ${folder.id}: an arcade set was not looked up; copies not grouped")
            return false
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            Log.w(LOG_TAG, "Folder ${folder.id}: arcade copies not grouped (${e.javaClass.simpleName})")
            return false
        }
        val shown = ArcadeCopies.shownCopies(copies, preferredRegions)
        val stored = rows.associate { it.id to it.isPrimaryCopy }
        val toShow = shown.filter { (id, show) -> show && stored[id] != true }.keys.toList() + released.map { it.id }
        val toLeaveOut = shown.filter { (id, show) -> !show && stored[id] != false }.keys.toList()
        if (toShow.isEmpty() && toLeaveOut.isEmpty()) return false
        var changed = 0
        database.withTransaction {
            changed += dao.setPrimaryCopy(toShow, true)
            changed += dao.setPrimaryCopy(toLeaveOut, false)
            if (released.isNotEmpty()) database.scanDao().listAgain(folder.id, released.map { it.parentDocumentId }.distinct())
        }
        if (released.isNotEmpty()) Log.i(LOG_TAG, "Folder ${folder.id}: ${released.size} rows left the arcade grouping; shown, directories listed again next scan")
        Log.i(LOG_TAG, "Folder ${folder.id}: $changed arcade rows changed which copy of their game shows")
        return changed > 0
    }

    /**
     * Shown arcade sets of one system in this folder that would show the same short title show their
     * full descriptions, and the short title again once the clash is gone ([ArcadeTitleClashes]).
     * Only rows showing the identifier's arcade name are read; each change is written only while
     * the row still shows the title read here, so a fix made meanwhile is never overwritten. Reads
     * no file: the descriptions come from the arcade pack. Runs only when this pass named an
     * arcade row, the walk removed games or [groupArcadeCopies] changed which copies show, so a
     * rescan with nothing changed looks no description up. Copies the lists leave out show their
     * short title.
     */
    private suspend fun separateArcadeTitles() {
        val dao = database.identifyDao()
        val shown = dao.arcadeShownGames(folder.id)
        if (shown.count { it.isPrimaryCopy } < 2 && shown.all { ArcadeTitles.shortTitle(it.title) == it.title }) return
        val changes = ArcadeTitleClashes.changes(shown) { game ->
            if (packStateOf(game.systemId) !is PackState.Ready) return@changes null
            try {
                naming.identifier.identify(IdentifyRequest(game.systemId, game.fileName, cleanedTitle = null)).fullTitle
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                Log.w(LOG_TAG, "Folder ${folder.id}: game ${game.id} arcade description not looked up (${e.javaClass.simpleName})")
                null
            }
        }
        if (changes.isEmpty()) return
        val shownTitles = shown.associate { it.id to it.title }
        var changed = 0
        database.withTransaction {
            for ((id, title) in changes) {
                changed += dao.setArcadeTitle(id, shownTitles.getValue(id), title, TitleCleaner.sortName(title))
            }
        }
        Log.i(LOG_TAG, "Folder ${folder.id}: $changed arcade titles changed to tell sets apart or back to short")
    }

    private fun stats() = NamingStats(named, read.get(), fromCache.get(), notRead.get(), hidden, waitingForPack.get())

    /**
     * A system whose pack is newer than the one its rows were named with (a data update, the
     * user's DATs) gets those rows named again; their fingerprints are cached, so nothing is read.
     */
    private suspend fun renameForNewPacks() {
        val dao = database.identifyDao()
        for ((systemId, versions) in dao.packVersions(folder.id).groupBy({ it.systemId }, { it.packVersion })) {
            val state = packStateOf(systemId) as? PackState.Ready ?: continue
            if (versions.any { it != state.version }) {
                val marked = dao.markForNewPack(folder.id, systemId, state.version)
                Log.i(LOG_TAG, "Folder ${folder.id}: $marked games of $systemId to name again with pack version ${state.version}")
            }
        }
    }

    /** Installs the pack on first use (blocking, worker thread; the other readers wait); remembered for this pass. */
    private fun packStateOf(systemId: String): PackState = synchronized(packStates) {
        packStates.getOrPut(systemId) { packStateLookup(systemId) }
    }

    private fun packStateLookup(systemId: String): PackState {
        val packSystem = if (ArcadeSystems.isArcade(systemId)) ArcadeSystems.PACK_ID else systemId
        val pack = naming.packs.packFor(packSystem)
        return when {
            pack != null -> PackState.Ready(pack.dataVersion)
            naming.packs.hasBundled(packSystem) -> PackState.Unavailable
            else -> PackState.None
        }
    }

    /** [onPackFailed]: called when the pack answered with a failed lookup (the game then waits for a pack). */
    private fun identifyOne(game: GameToIdentify, onPackFailed: () -> Unit = {}): Named? {
        val packVersion = when (val pack = packStateOf(game.systemId)) {
            is PackState.Ready -> pack.version
            PackState.None -> null
            PackState.Unavailable -> {
                // Named from the file only, it would be stamped done and never get its real name.
                waitingForPack.incrementAndGet()
                return null
            }
        }
        val cached = cachedFingerprints(game)
        val request = IdentifyRequest(
            systemId = game.systemId,
            fileName = game.fileName,
            cleanedTitle = game.fileTitle,
            regionsFromName = game.fileRegions.split(',').mapNotNull { Region.fromCode(it.trim()) }.map { it.noIntroName },
            depth = FingerprintReader.Depth.QUICK,
            known = cached,
        )
        return try {
            // Arcade sets are named from the short name, launch files (.steam) from the file name: neither is opened.
            val named = if (cached != null || ArcadeSystems.isArcade(game.systemId) || LaunchFileSystems.isLaunchFile(game.systemId)) {
                if (cached != null) fromCache.incrementAndGet()
                Named(game, naming.identifier.identify(request), fromCache = cached != null)
            } else {
                readAndIdentify(game, request)
            }
            when {
                named == null -> null
                // The pack installed but failed its lookups (damaged on disk): the answer used no
                // pack, so stamping it named would keep the file name forever.
                packVersion != null && named.identification.packVersion == null -> {
                    waitingForPack.incrementAndGet()
                    onPackFailed()
                    null
                }
                else -> named
            }
        } catch (e: IOException) {
            notReadThisTime(game, e.javaClass.simpleName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            // Not expected (the identifier turns bad bytes into notes); one file must not stop the scan.
            notReadThisTime(game, e.javaClass.simpleName)
        }
    }

    private fun readAndIdentify(game: GameToIdentify, request: IdentifyRequest): Named? {
        val started = System.nanoTime()
        try {
            return openAndIdentify(game, request)
        } finally {
            openAndReadNanos.addAndGet(System.nanoTime() - started)
        }
    }

    private fun openAndIdentify(game: GameToIdentify, request: IdentifyRequest): Named? {
        val opened = when (val result = tree.open(game.documentId)) {
            is TreeResult.Found -> result.value
            is TreeResult.PermissionDenied -> throw FolderGone("access revoked")
            is TreeResult.Failed -> return notReadThisTime(game, result.cause.javaClass.simpleName)
            TreeResult.Missing -> return notReadThisTime(game, "missing")
        }
        read.incrementAndGet()
        return opened.use { file ->
            FileSource(file.channel).use { source ->
                val siblingOpener = siblings.openerFor(game.parentDocumentId)
                Named(game, naming.identifier.identify(request.copy(source = source, siblings = siblingOpener)))
            }
        }
    }

    /** Logs the failure and, when the picked folder itself stopped answering, ends the pass. */
    private fun notReadThisTime(game: GameToIdentify, cause: String): Named? {
        notRead.incrementAndGet()
        Log.w(LOG_TAG, "Folder ${folder.id}: game ${game.id} not read this time ($cause)")
        when (val root = tree.stat(tree.rootDocumentId)) {
            is TreeResult.Found -> Unit
            is TreeResult.PermissionDenied -> throw FolderGone("access revoked")
            is TreeResult.Failed -> throw FolderGone("folder unreadable, ${root.cause.javaClass.simpleName}")
            TreeResult.Missing -> throw FolderGone("folder missing")
        }
        return null
    }

    private fun cachedFingerprints(game: GameToIdentify): Fingerprints? {
        val stored = game.fingerprint
        return if (stored != null && !FingerprintCodec.isCurrent(stored, game.systemId)) {
            Log.i(LOG_TAG, "Folder ${folder.id}: game ${game.id} cached by an older ${game.systemId} reader; reading again")
            null
        } else if (stored != null && game.fingerprintSize == game.sizeBytes && game.fingerprintMtime == game.modifiedAt) {
            FingerprintCodec.decode(stored).also {
                if (it == null) Log.w(LOG_TAG, "Folder ${folder.id}: game ${game.id} cached fingerprints unreadable; reading again")
            }
        } else {
            null
        }
    }

    private suspend fun write(results: List<Named>) {
        if (results.isEmpty()) return
        val files = results.map { NamedFile(it.game, it.identification, fingerprintToStore(it)) }
        database.withTransaction {
            for (row in NameWrite.write(database, files)) {
                named++
                if (ArcadeSystems.isArcade(row.file.game.systemId)) arcadeNamed = true
                val shown = row.shown
                if (shown != null && shown.isNotGame && row.fix == null) hidden++
            }
        }
    }

    /**
     * The value stored as `game.fingerprint`: the cached one as it is when the answer came from it
     * unchanged, so the content pass's stamp ([ContentPass]) survives a naming from the cache.
     */
    private fun fingerprintToStore(named: Named): String? {
        val fingerprints = named.identification.fingerprints ?: return null
        val stored = named.game.fingerprint
        if (named.fromCache && stored != null && FingerprintCodec.decode(stored) == fingerprints.copy(notes = emptyList())) return stored
        return FingerprintCodec.encode(fingerprints, named.game.systemId)
    }

    private companion object {
        /** Rows read, identified and written per transaction; the lists update after each page. */
        const val PAGE_SIZE = 200
    }
}

/**
 * Files the naming pass opens at the same time. Measured on the Retroid Pocket Nova's SD card with a
 * cold cache, 1,865 games: 1 reader 25.3 s, 2 about 12 s, 3 about 8 s, 4 7.2 s, 6 5.0 s.
 * 4 takes most of the gain while keeping few threads busy on a 2 GB Android 6 device.
 */
internal const val NAMING_READERS = 4
