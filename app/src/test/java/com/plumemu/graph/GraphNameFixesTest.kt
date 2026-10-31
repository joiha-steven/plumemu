package com.plumemu.graph

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.FixKeys
import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.NameFixKind
import com.plumemu.coredata.db.NameTrustNames
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScanBatch
import com.plumemu.coredata.db.SystemEntity
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coredata.db.NameFixEntity
import com.plumemu.game.TitleChoice
import com.plumemu.game.TitleList
import com.plumemu.identify.pack.ConsolePackBuilder
import com.plumemu.identify.pack.InstallResult
import com.plumemu.identify.pack.NamePack
import com.plumemu.identify.pack.PackOpenResult
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Name fixes on the real graph (real database file): a chosen title is stored under
 * every key with its source and shown at once; Not a game takes the game off every list and
 * Settings › Library › Hidden games brings it back by Back to automatic; Unidentified games lists
 * games named from their file only. Logs carry no names.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GraphNameFixesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val logged = CopyOnWriteArrayList<String>()
    private val graph = AppGraph(context, log = { logged += it })
    private val fixes = GraphNameFixes(graph, Dispatchers.IO, clock = { 7 }, log = { logged += it })
    private val names = GraphSettingsNames(graph, Dispatchers.IO, main = Dispatchers.Unconfined, log = { logged += it })

    @After
    fun tearDown() {
        graph.database.close()
        context.deleteDatabase("plumemu.db")
    }

    @Test
    fun aChosenTitleIsStoredUnderEveryKeyWithItsSourceAndShownAtOnce() = runBlocking {
        val id = identifiedGame(listOf("crc:0000abcd:1024", "code:SNES:ZZZE"))
        fixes.setChosenTitle(id, TitleChoice("Zork Quest Deluxe", "USA", 1994, "Test DAT"))
        val stored = graph.database.identifyDao().fixesByKeys(listOf("crc:0000abcd:1024", "code:SNES:ZZZE", NameFixEntity.rowKey(id)))
        assertEquals("the file's content key and its row, not the shared code", listOf("crc:0000abcd:1024", NameFixEntity.rowKey(id)), stored.map { it.fixKey }.sorted())
        assertTrue(stored.all { it.kind == NameFixKind.CHOSEN_TITLE && it.title == "Zork Quest Deluxe" && it.titleSource == "Test DAT" && it.fixedAt == 7L })
        val row = graph.database.gameDao().game(id)!!
        assertEquals("Zork Quest Deluxe", row.title)
        assertEquals(NameTrustNames.PERSON_FIX, row.nameTrust)
        val facts = fixes.nameFacts(id)!!
        assertTrue("Back to automatic is offered", facts.hasFix)
        assertEquals("Test DAT", facts.source)
    }

    @Test
    fun aPackReplacedByAnUpdateGivesItsNewTitles() = runBlocking {
        val packs = graph.naming.packs
        fun install(version: Long, title: String) {
            val bytes = ConsolePackBuilder("snes", version).run {
                release(title(title))
                build()
            }
            val checksum = (NamePack.fromBytes(bytes) as PackOpenResult.Opened).pack.checksum
            val gz = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()
            assertTrue(packs.install("snes", ByteArrayInputStream(gz), checksum) is InstallResult.Installed)
        }
        install(20_990_101, "Zork Alpha")
        val first = fixes.titleList("snes", "SNES") as TitleList.Ready
        assertEquals(listOf("Zork Alpha"), first.finder.find("zork").map { it.name })
        assertTrue("the same pack reuses its list", (fixes.titleList("snes", "SNES") as TitleList.Ready).finder === first.finder)
        install(20_990_102, "Zork Beta")
        val second = fixes.titleList("snes", "SNES") as TitleList.Ready
        assertEquals(listOf("Zork Beta"), second.finder.find("zork").map { it.name })
    }

    @Test
    fun notAGameLeavesEveryListAndHiddenGamesBringsItBack() = runBlocking {
        val id = identifiedGame(listOf("crc:0000abcd:1024"))
        val games = graph.database.gameDao()
        games.setFavorite(id, true)
        graph.database.playDao().addSession(id, endedAt = 5, seconds = 30)
        assertTrue(listed(id))

        fixes.markNotAGame(id)
        assertFalse("off the system list, Favorites and Recent (search reads these lists)", listed(id))
        val hidden = names.hiddenGames().first()
        assertEquals(listOf(id), hidden.map { it.id })
        assertTrue("its reason is shown", hidden.single().isNotAGame)
        assertEquals(emptyList<Long>(), games.observeHiddenGames().first().map { it.id })

        val done = CompletableDeferred<Boolean>()
        names.unhideGame(id) { done.complete(it) }
        assertTrue(withTimeout(TIMEOUT_MS) { done.await() })
        assertTrue("back on every list", listed(id))
        assertEquals(emptyList<Long>(), names.hiddenGames().first().map { it.id })
        assertEquals("the fix is gone under every key", emptyList<Any>(), graph.database.identifyDao().fixesByKeys(listOf("crc:0000abcd:1024")))
        assertTrue(logged.none { "Zork" in it || ".sfc" in it })
    }

    @Test
    fun aHiddenGameIsUnhiddenNotRenamed() = runBlocking {
        val id = identifiedGame(listOf("crc:0000abcd:1024"))
        graph.database.gameDao().setHidden(id, true)
        assertEquals(listOf(false), names.hiddenGames().first().map { it.isNotAGame })
        val done = CompletableDeferred<Boolean>()
        names.unhideGame(id) { done.complete(it) }
        assertTrue(withTimeout(TIMEOUT_MS) { done.await() })
        assertTrue(graph.database.gameDao().observeSystemGames("snes").first().any { it.id == id })
        assertEquals("Zork Quest", graph.database.gameDao().game(id)!!.title)
    }

    @Test
    fun unidentifiedListsGamesNamedFromTheirFileAndAnOwnNameTakesOneOut() = runBlocking {
        val id = identifiedGame(emptyList(), trust = NameTrustNames.FILE_NAME, method = "FILE_NAME")
        assertEquals(listOf(id), names.unidentifiedGames().first().map { it.id })
        val done = CompletableDeferred<Boolean>()
        names.setOwnName(id, "My Zork") { stored, _ -> done.complete(stored) }
        assertTrue(withTimeout(TIMEOUT_MS) { done.await() })
        assertEquals(emptyList<Long>(), names.unidentifiedGames().first().map { it.id })
        assertEquals("My Zork", graph.database.gameDao().game(id)!!.title)
        assertEquals("a game with no key keeps its fix under its row", NameFixKind.OWN_NAME, graph.database.identifyDao().fixOf(id)?.kind)
    }

    @Test
    fun aSteamGameIsNamedByDesignAndNeverUnidentified() = runBlocking {
        val snes = identifiedGame(emptyList(), trust = NameTrustNames.FILE_NAME, method = "FILE_NAME")
        identifiedGame(emptyList(), trust = NameTrustNames.FILE_NAME, method = "FILE_NAME", systemId = "steam")
        assertEquals("listed and counted: the snes game only", listOf(snes), names.unidentifiedGames().first().map { it.id })
    }

    @Test
    fun writesOnAGameThatIsGoneFailAndAreAnsweredFalse() = runBlocking {
        val done = CompletableDeferred<Boolean>()
        names.setChosenTitle(404, TitleChoice("Zork", "", null, null)) { stored, _ -> done.complete(stored) }
        assertFalse(withTimeout(TIMEOUT_MS) { done.await() })
        assertTrue(logged.any { it.contains("fix name of game 404 failed (IllegalStateException)") })
    }

    @Test
    fun aTitleListForAGameThatIsGoneSaysTheGameIsGoneNotThatTheListFailed() = runBlocking {
        val done = CompletableDeferred<TitleList?>()
        names.titleList(404) { done.complete(it) }
        assertEquals(TitleList.GameGone, withTimeout(TIMEOUT_MS) { done.await() })
    }

    @Test
    fun aNewNameLooksUpBoxArtOnceTheContentKeyLanded() = runBlocking {
        // A loose cartridge: no fingerprint key, so the fix starts on the row key.
        val id = identifiedGame(emptyList())
        val order = CopyOnWriteArrayList<String>()
        val keyAtLookup = CompletableDeferred<String>()
        val withSeams =
            GraphNameFixes(
                graph,
                Dispatchers.IO,
                clock = { 7 },
                log = { logged += it },
                contentKey = { gameId ->
                    order += "content key"
                    graph.database.identifyDao().addContentKeys(gameId, 1, 1, "", listOf(CONTENT_KEY))
                },
                afterRename = { gameId ->
                    order += "box art"
                    keyAtLookup.complete(FixKeys.forGame(gameId, graph.database.gameDao().game(gameId)!!.fixKeys).first())
                },
            )

        withSeams.setOwnName(id, "Zork Quest Mine")

        assertEquals("the lookup sees the final key", CONTENT_KEY, withTimeout(TIMEOUT_MS) { keyAtLookup.await() })
        assertEquals(listOf("content key", "box art"), order.toList())
    }

    @Test
    fun aChosenTitleLooksUpBoxArtAndNotAGameDoesNot() = runBlocking {
        val chosen = identifiedGame(listOf(CONTENT_KEY))
        val lookups = CopyOnWriteArrayList<Long>()
        val withSeams = GraphNameFixes(graph, Dispatchers.IO, clock = { 7 }, log = { logged += it }, contentKey = {}, afterRename = { lookups += it })

        afterWorkOf { withSeams.setChosenTitle(chosen, TitleChoice("Zork Quest Deluxe", "USA", 1994, "Test DAT")) }
        afterWorkOf { withSeams.markNotAGame(chosen) }

        assertEquals("one lookup, for the chosen title only", listOf(chosen), lookups.toList())
    }

    /** Runs [write], then waits for the work it launched in the process scope. */
    private suspend fun afterWorkOf(write: suspend () -> Unit) {
        val scope = graph.appScope.coroutineContext.job
        val before = scope.children.toSet()
        write()
        val launched = scope.children.toList() - before
        withTimeout(TIMEOUT_MS) { launched.joinAll() }
    }

    private suspend fun listed(id: Long): Boolean {
        val games = graph.database.gameDao()
        return games.observeSystemGames("snes").first().any { it.id == id } &&
            games.observeFavorites().first().any { it.id == id } &&
            games.observeRecent(10).first().any { it.id == id }
    }

    private suspend fun identifiedGame(
        keys: List<String>,
        trust: String = "IDENTIFIER_EXACT",
        method: String = "CRC",
        systemId: String = "snes",
    ): Long {
        val scanDao = graph.database.scanDao()
        val folder = scanDao.insertFolder(RomFolderEntity(treeUri = "content://test/tree/$systemId", displayName = systemId, addedAt = 1))
        scanDao.applyBatch(ScanBatch(folder, systems = listOf(SystemEntity(systemId, systemId)), newGames = listOf(game(folder).copy(systemId = systemId))))
        val id = scanDao.gamesInFolder(folder).single().id
        val dao = graph.database.identifyDao()
        assertEquals(1, dao.recordIdentification(id, 1, 1, "zq.sfc", null, null, null, FixKeys.join(keys), method, "EXACT", packVersion = 1))
        dao.setShownName(id, if (trust == NameTrustNames.FILE_NAME) "zq" else "Zork Quest", "zork quest", "", trust, isNotGame = false)
        return id
    }

    private fun game(folderId: Long) =
        GameEntity(
            folderId = folderId,
            documentId = "zq",
            parentDocumentId = "root",
            relativePath = "snes/zq.sfc",
            fileName = "zq.sfc",
            sizeBytes = 1,
            modifiedAt = 1,
            title = "zq",
            sortName = "zq",
            regions = "",
            discGroup = null,
            discCount = 1,
            systemId = "snes",
            isPrimaryCopy = true,
        )

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val CONTENT_KEY = "crc:0000abcd:1024"
    }
}
