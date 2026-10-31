package com.plumemu.graph

import android.util.Log
import com.plumemu.coredata.db.GameIdentificationRow
import com.plumemu.coredata.db.NameFixEntity
import com.plumemu.coredata.db.NameFixKind
import com.plumemu.coredata.db.NameTrustNames
import com.plumemu.game.ArcadeSetFacts
import com.plumemu.game.NameCertainty
import com.plumemu.game.NameFacts
import com.plumemu.game.NameOrigin
import com.plumemu.game.RenameBoxArtNotice
import com.plumemu.game.TitleChoice
import com.plumemu.game.TitleFinder
import com.plumemu.game.TitleList
import com.plumemu.identify.Confidence
import com.plumemu.identify.Method
import com.plumemu.identify.arcade.ArcadeLookup
import com.plumemu.identify.arcade.ArcadeSystems
import com.plumemu.identify.arcade.ArcadeTitles
import com.plumemu.identify.name.TitleCleaner
import com.plumemu.identify.pack.CorruptPackException
import com.plumemu.identify.pack.NamePack
import com.plumemu.identify.pack.PackKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A person's name fixes for the game menu and Settings › Library: the name lists Fix
 * name… searches, Info's "Name from", and the writes. One per process ([AppGraph.nameFixes]), so
 * the list of the last system searched is kept for the next Fix name… and Back to automatic's
 * naming outlives the screen that asked (it runs in [AppGraph.appScope]). Everything runs on [io].
 * Lines carry game ids, system ids and exception classes, never names.
 */
internal class GraphNameFixes(
    private val graph: AppGraph,
    private val io: CoroutineDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = { Log.w(TAG, it) },
    /** Box art after a rename, one per process with this; null looks nothing up. */
    private val renameBoxArt: GraphRenameBoxArt? = null,
    /** The rename's whole-file read for the file's content key ([contentKeyNow]); a seam for tests. */
    private val contentKey: suspend (gameId: Long) -> Unit = { gameId ->
        val scanner = graph.scanner()
        if (scanner == null) log("content key of game $gameId: no scanner (platform data unavailable)") else scanner.addContentKey(gameId)
    },
    /** After a chosen title or own name, once [contentKey] ended: the box-art lookup ([GraphRenameBoxArt]). */
    private val afterRename: suspend (gameId: Long) -> Unit = { gameId -> renameBoxArt?.afterRename(gameId) },
) {
    /**
     * The last list built and the pack it was built from: arcade's holds tens of thousands of titles,
     * so only one is kept. Keyed by the pack itself, so a pack replaced by an update gives new titles.
     */
    @Volatile
    private var lastList: Pair<NamePack, TitleFinder>? = null

    /** Fix name…: [systemId]'s titles, ready to search; built once per system and kept until another. */
    suspend fun titleList(
        systemId: String,
        systemName: String,
    ): TitleList =
        withContext(io) {
            val packs = graph.naming.packs
            val pack = packs.packFor(systemId)
            lastList?.takeIf { it.first === pack }?.let { return@withContext TitleList.Ready(it.second) }
            if (pack == null) {
                return@withContext if (packs.hasBundled(systemId)) TitleList.Unavailable else TitleList.NoList(systemName)
            }
            val choices = try {
                choicesOf(pack)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                // A damaged pack (CorruptPackException): the store reinstalls it next time.
                log("titles: pack of $systemId unreadable (${e.javaClass.simpleName})")
                packs.reportBroken(pack, e)
                return@withContext TitleList.Unavailable
            }
            val finder = TitleFinder(choices)
            lastList = pack to finder
            TitleList.Ready(finder)
        }

    /** Info › Name from; null when the game is gone. */
    suspend fun nameFacts(gameId: Long): NameFacts? =
        withContext(io) {
            val dao = graph.database.identifyDao()
            val row = dao.identificationOf(gameId) ?: return@withContext null
            val fix = dao.fixOf(gameId)
            factsOf(row, fix)
        }

    /** Info › Name from's source line: the sources of the game's system pack (opens the pack). */
    suspend fun packSource(gameId: Long): String? =
        withContext(io) {
            val systemId = graph.database.identifyDao().identificationOf(gameId)?.systemId ?: return@withContext null
            sourcesOf(systemId)
        }

    /**
     * Info › Arcade set: the set [gameId]'s file holds, by its short name in the arcade pack
     * (opens the pack); null for a game of another system, a set the pack does not list (a
     * MAME 2003-Plus name shows the record of its alias), or a pack that cannot be read.
     */
    suspend fun arcadeSet(gameId: Long): ArcadeSetFacts? =
        withContext(io) {
            val game = graph.database.gameDao().game(gameId) ?: return@withContext null
            if (!ArcadeSystems.isArcade(game.systemId)) return@withContext null
            val packs = graph.naming.packs
            var pack: NamePack? = null
            try {
                pack = packs.packFor(ArcadeSystems.PACK_ID)?.takeIf { it.kind == PackKind.ARCADE }
                pack?.let { ArcadeLookup(it).find(game.fileName) }?.let { ArcadeSetFacts(it.shortName, ArcadeTitles.details(it.title)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                // A damaged pack (CorruptPackException): the store reinstalls it next time.
                log("info: arcade set of game $gameId unreadable (${e.javaClass.simpleName})")
                val broken = pack
                if (e is CorruptPackException && broken != null) packs.reportBroken(broken, e)
                null
            }
        }

    /** Stores the chosen title; the answer says why box art cannot be looked up by it, or null ([renamed]). */
    suspend fun setChosenTitle(
        gameId: Long,
        choice: TitleChoice,
    ): RenameBoxArtNotice? {
        setFix(gameId, NameFixKind.CHOSEN_TITLE, choice.name, choice.source)
        return renamed(gameId)
    }

    /** Stores the person's own name; the answer as [setChosenTitle]'s. */
    suspend fun setOwnName(
        gameId: Long,
        name: String,
    ): RenameBoxArtNotice? {
        require(name.isNotBlank()) { "an own name must not be blank" }
        setFix(gameId, NameFixKind.OWN_NAME, name.trim(), null)
        // An installed app listed as a game has no file to read a content key from and no box art.
        if (withContext(io) { graph.database.appGameDao().isAppGameRow(gameId) }) return null
        return renamed(gameId)
    }

    suspend fun markNotAGame(gameId: Long) {
        setFix(gameId, NameFixKind.NOT_A_GAME, null, null)
        contentKeyNow(gameId, lookUpBoxArt = false)
    }

    /**
     * Deletes the person's fix under every key, shows the file name, then names the game again at
     * once off the main thread ([namingNow]): from its cached fingerprints when the file is
     * unchanged, else by reading it; a file that cannot be read keeps the file name until the next
     * scan. Other copies sharing a key are named in the same pass. Throws when the game is gone.
     */
    suspend fun backToAutomatic(gameId: Long): Job {
        val done = withContext(io) { graph.database.identifyDao().backToAutomatic(gameId) }
        check(done) { "game $gameId is not in the library" }
        return namingNow()
    }

    /** One naming pass over every folder's waiting rows, in the process scope; failures are logged. */
    fun namingNow(): Job =
        graph.appScope.launch {
            try {
                val scanner = graph.scanner() ?: return@launch log("naming now: no scanner (platform data unavailable)")
                scanner.nameWaiting().collect { }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("naming now failed (${e.javaClass.simpleName}); the next scan names the games")
            }
        }

    private suspend fun setFix(
        gameId: Long,
        kind: NameFixKind,
        title: String?,
        source: String?,
    ) {
        val stored = withContext(io) {
            graph.database.identifyDao().setFix(gameId, kind, title, title?.let(TitleCleaner::sortName), source, clock())
        }
        check(stored) { "game $gameId is not in the library" }
    }

    /**
     * After a new name: why box art cannot be looked up by it now, to tell the person (a quick
     * local check, before the content key read; a failed check says nothing), then the content key
     * and the lookup in the process scope.
     */
    private suspend fun renamed(gameId: Long): RenameBoxArtNotice? {
        val notice =
            try {
                renameBoxArt?.noticeFor(gameId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("box art after rename: game $gameId notice failed (${e.javaClass.simpleName})")
                null
            }
        contentKeyNow(gameId, lookUpBoxArt = true)
        return notice
    }

    /**
     * After a fix: one whole-file read of the game's file for its content key, in the process
     * scope, so it neither holds up the panel nor stops when the screen goes. Until it lands (or
     * when the file cannot be read) the fix lives on the row key, which already shows it.
     * After a new name ([lookUpBoxArt]), then the box-art lookup by that name ([afterRename]), in
     * the same job: the game's key is final by then.
     */
    private fun contentKeyNow(
        gameId: Long,
        lookUpBoxArt: Boolean,
    ): Job =
        graph.appScope.launch {
            try {
                contentKey(gameId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("content key of game $gameId failed (${e.javaClass.simpleName}); the fix stays on its row")
            }
            if (!lookUpBoxArt) return@launch
            try {
                afterRename(gameId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("box art after rename: game $gameId failed (${e.javaClass.simpleName})")
            }
        }

    private fun sourcesOf(systemId: String): String? =
        try {
            graph.naming.packs.packFor(systemId)?.sources()?.map { it.name }?.distinct()?.joinToString(", ")?.ifEmpty { null }
        } catch (e: IllegalStateException) {
            log("info: sources of $systemId unreadable (${e.javaClass.simpleName})")
            null
        }

    private companion object {
        const val TAG = "PlumNames"
    }
}

/** Every title of [pack] as Fix name… lists it; arcade lists sets, without BIOS, device and mechanical ones. */
internal fun choicesOf(pack: NamePack): List<TitleChoice> =
    if (pack.kind == PackKind.ARCADE) {
        (0 until pack.titleCount).asSequence().map(pack::arcadeEntry)
            .filterNot { it.isBios || it.isDevice || it.isMechanical }
            .map { TitleChoice(it.title, "", it.year, it.source?.name) }
            .toList()
    } else {
        pack.allTitles().map { title ->
            TitleChoice(title.name, title.regions.joinToString(", "), title.year, pack.releasesOf(title.index).firstOrNull()?.source?.name)
        }.toList()
    }

/**
 * Info's naming facts from the stored row: a person's fix first, then the shown name's trust (a
 * file name after Back to automatic, until the pass runs), then the identifier's layer. A name
 * that came from a pack is marked [NameFacts.sourceInPack]; its source is read when Info opens.
 */
internal fun factsOf(
    row: GameIdentificationRow,
    fix: NameFixEntity?,
): NameFacts {
    when (fix?.kind) {
        NameFixKind.OWN_NAME -> return NameFacts(NameOrigin.OwnName, null, null, hasFix = true)
        NameFixKind.CHOSEN_TITLE -> return NameFacts(NameOrigin.ChosenTitle, null, fix.titleSource, hasFix = true)
        NameFixKind.NOT_A_GAME, null -> Unit
    }
    val hasFix = fix != null
    val method = Method.entries.firstOrNull { it.name == row.idMethod }
    if (method == null || row.nameTrust == NameTrustNames.FILE_NAME || row.nameTrust == NameTrustNames.PERSON_FIX) {
        val origin = if (method == null) NameOrigin.NotReadYet else NameOrigin.FileName
        return NameFacts(origin, NameCertainty.Unknown, null, hasFix)
    }
    val origin = when (method) {
        Method.ARCADE_NAME -> NameOrigin.ArcadeName
        Method.CRC -> NameOrigin.FileContents
        Method.PRODUCT_CODE -> NameOrigin.GameCode
        Method.HEADER_TITLE -> NameOrigin.TitleInFile
        Method.NAME_MATCH -> NameOrigin.NameMatch
        Method.FILE_NAME -> NameOrigin.FileName
    }
    val certainty = when (Confidence.entries.firstOrNull { it.name == row.idConfidence }) {
        Confidence.EXACT -> NameCertainty.Sure
        Confidence.LIKELY -> NameCertainty.Likely
        Confidence.UNKNOWN, null -> NameCertainty.Unknown
    }
    val fromPack = origin != NameOrigin.TitleInFile && origin != NameOrigin.FileName
    return NameFacts(origin, certainty, null, hasFix, sourceInPack = fromPack)
}
