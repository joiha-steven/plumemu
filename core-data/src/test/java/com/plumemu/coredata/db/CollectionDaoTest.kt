package com.plumemu.coredata.db

import com.plumemu.coredata.settings.SortGamesSetting
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CollectionDaoTest {
    private lateinit var db: PlumDatabase
    private lateinit var library: ScannedLibrary
    private val dao get() = db.collectionDao()

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        library = ScannedLibrary(db)
        library.pickFolder()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun names() = dao.observeCollections().first().map { it.name }

    private fun sql(statement: String) = db.openHelper.writableDatabase.execSQL(statement)

    @Test
    fun createTrimsAndPutsEachNewCollectionAtTheEnd() = runTest {
        val first = dao.create("  RPGs  ", now = 10)
        val second = dao.create("Hacks", now = 20)
        val rows = dao.observeCollections().first()
        assertEquals(listOf(first, second), rows.map { it.id })
        assertEquals(listOf("RPGs", "Hacks"), rows.map { it.name })
        assertEquals(listOf(0, 1), rows.map { it.position })
        assertEquals(listOf(0, 0), rows.map { it.gameCount })
    }

    @Test
    fun aNameAlreadyUsedGetsTheNextNumberIgnoringCase() = runTest {
        dao.create("RPGs")
        dao.create("rpgs")
        dao.create("RPGs")
        assertEquals(listOf("RPGs", "rpgs 2", "RPGs 3"), names())
    }

    @Test
    fun blankAndTooLongNamesAreRefusedAndANumberedNameStillFitsForty() = runTest {
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { dao.create("   ") } }
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { dao.create("x".repeat(41)) } }
        assertEquals(emptyList<String>(), names())
        val forty = "y".repeat(40)
        dao.create(forty)
        dao.create(forty)
        assertEquals(listOf(forty, "y".repeat(38) + " 2"), names())
        assertTrue(names().all { it.length <= CollectionDao.MAX_NAME_LENGTH })
        // Characters, not UTF-16 units: forty Vietnamese letters with marks fit.
        dao.create("\u1EC7".repeat(40))
        assertEquals("\u1EC7".repeat(40), names().last())
    }

    @Test
    fun renameFollowsTheSameRulesAndItsOwnNameIsNotTaken() = runTest {
        val rpgs = dao.create("RPGs")
        val hacks = dao.create("Hacks")
        assertEquals("rpgs", dao.rename(rpgs, " rpgs "))
        assertEquals("the typed case is kept", "RPGS 2", dao.rename(hacks, "RPGS"))
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { dao.rename(hacks, "") } }
        assertNull(dao.rename(999, "Gone"))
        assertEquals(listOf("rpgs", "RPGS 2"), names())
    }

    @Test
    fun deleteTakesItsMembershipsKeepsTheGamesAndClosesTheGap() = runTest {
        val ids = library.scanIn(game(library.folderId, "a.sfc"), game(library.folderId, "b.sfc"))
        val one = dao.create("One")
        val two = dao.create("Two")
        val three = dao.create("Three")
        dao.add(two, ids.getValue("a.sfc"))
        dao.add(three, ids.getValue("a.sfc"))
        dao.delete(two)
        val rows = dao.observeCollections().first()
        assertEquals(listOf(one, three), rows.map { it.id })
        assertEquals(listOf(0, 1), rows.map { it.position })
        assertEquals(listOf(three), dao.observeCollectionsOf(ids.getValue("a.sfc")).first())
        assertEquals(2, library.scan.gamesInFolder(library.folderId).size)
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM collection_game WHERE collection_id = $two").use {
            it.moveToFirst()
            assertEquals("CASCADE from collection", 0, it.getInt(0))
        }
    }

    @Test
    fun moveAndReorderKeepPositionsZeroToN() = runTest {
        val a = dao.create("A")
        val b = dao.create("B")
        val c = dao.create("C")
        dao.move(c, 0)
        assertEquals(listOf("C", "A", "B"), names())
        dao.move(c, 99)
        assertEquals(listOf("A", "B", "C"), names())
        dao.move(999, 0)
        assertEquals(listOf("A", "B", "C"), names())
        dao.reorder(listOf(b, 777, b))
        assertEquals("listed first, the rest keep their order", listOf("B", "A", "C"), names())
        dao.reorder(listOf(c, b, a))
        val rows = dao.observeCollections().first()
        assertEquals(listOf("C", "B", "A"), rows.map { it.name })
        assertEquals(listOf(0, 1, 2), rows.map { it.position })
    }

    @Test
    fun addAndRemoveAndAGameIsInACollectionOnce() = runTest {
        val ids = library.scanIn(keyedGame(library.folderId, "a.sfc", listOf("code:SNS-A", "crc:aaaa:1")), game(library.folderId, "b.sfc"))
        val rpgs = dao.create("RPGs")
        val a = ids.getValue("a.sfc")
        assertTrue(dao.add(rpgs, a, now = 5))
        assertTrue("adding again is a no-op", dao.add(rpgs, a, now = 6))
        assertEquals(listOf("a.sfc"), dao.observeCollectionGames(rpgs).first().map { it.title })
        db.openHelper.readableDatabase.query("SELECT content_key, path_key, added_at FROM collection_game").use {
            assertEquals(1, it.count)
            it.moveToFirst()
            assertEquals("the first fingerprint key, never code:", "crc:aaaa:1", it.getString(0))
            assertEquals("content://tree/roms\na.sfc", it.getString(1))
            assertEquals(5L, it.getLong(2))
        }
        assertFalse("a game that is gone", dao.add(rpgs, 999))
        assertFalse("a collection that is gone", dao.add(999, a))
        assertEquals(1, dao.observeCollections().first().single().gameCount)
        dao.remove(rpgs, a)
        assertEquals(emptyList<GameListRow>(), dao.observeCollectionGames(rpgs).first())
        assertEquals(0, dao.observeCollections().first().single().gameCount)
    }

    @Test
    fun aGameWithoutFingerprintKeysIsStoredByPathOnly() = runTest {
        val ids = library.scanIn(keyedGame(library.folderId, "a.sfc", listOf("code:SNS-A")))
        val rpgs = dao.create("RPGs")
        dao.add(rpgs, ids.getValue("a.sfc"))
        db.openHelper.readableDatabase.query("SELECT content_key, path_key FROM collection_game").use {
            it.moveToFirst()
            assertTrue(it.isNull(0))
            assertEquals("content://tree/roms\na.sfc", it.getString(1))
        }
    }

    @Test
    fun countsAndListsLeaveOutHiddenAndNotAGameAsFavoritesDo() = runTest {
        val ids = library.scanIn(
            game(library.folderId, "a.sfc"),
            game(library.folderId, "b.sfc"),
            game(library.folderId, "c.sfc"),
            game(library.folderId, "d.sfc", isPrimaryCopy = false),
        )
        val rpgs = dao.create("RPGs")
        for (id in ids.values) dao.add(rpgs, id)
        db.gameDao().setHidden(ids.getValue("b.sfc"), true)
        sql("UPDATE game SET is_not_game = 1 WHERE id = ${ids.getValue("c.sfc")}")
        assertEquals("a copy the person put in stays, as in Favorites", listOf("a.sfc", "d.sfc"), dao.observeCollectionGames(rpgs).first().map { it.title })
        assertEquals(2, dao.observeCollections().first().single().gameCount)
        db.gameDao().setHidden(ids.getValue("b.sfc"), false)
        assertEquals(3, dao.observeCollections().first().single().gameCount)
    }

    @Test
    fun observeCollectionsOfGivesTheTicks() = runTest {
        val ids = library.scanIn(game(library.folderId, "a.sfc"), game(library.folderId, "b.sfc"))
        val one = dao.create("One")
        val two = dao.create("Two")
        dao.create("Three")
        dao.add(two, ids.getValue("a.sfc"))
        dao.add(one, ids.getValue("a.sfc"))
        dao.add(one, ids.getValue("b.sfc"))
        assertEquals(listOf(one, two), dao.observeCollectionsOf(ids.getValue("a.sfc")).first())
        assertEquals(listOf(one), dao.observeCollectionsOf(ids.getValue("b.sfc")).first())
    }

    @Test
    fun everySortListsTheSameGamesInItsOrderAndMatchesFavorites() = runTest {
        val ids = library.scanIn(
            game(library.folderId, "b.sfc", title = "Bravo"),
            game(library.folderId, "a.sfc", title = "Alpha"),
            game(library.folderId, "c.sfc", title = "Charlie"),
            game(library.folderId, "z.sfc", title = "Zulu"),
        )
        val rpgs = dao.create("RPGs")
        for (doc in listOf("a.sfc", "b.sfc", "c.sfc")) {
            dao.add(rpgs, ids.getValue(doc))
            db.gameDao().setFavorite(ids.getValue(doc), true)
        }
        sql("INSERT INTO play (game_id, last_played_at, total_seconds) VALUES (${ids.getValue("c.sfc")}, 100, 60), (${ids.getValue("a.sfc")}, 200, 600)")
        assertEquals(listOf("Alpha", "Bravo", "Charlie"), dao.observeCollectionGames(rpgs, SortGamesSetting.Name).first().map { it.title })
        assertEquals(listOf("Alpha", "Charlie", "Bravo"), dao.observeCollectionGames(rpgs, SortGamesSetting.LastPlayed).first().map { it.title })
        assertEquals(listOf("Alpha", "Charlie", "Bravo"), dao.observeCollectionGames(rpgs, SortGamesSetting.MostPlayed).first().map { it.title })
        assertEquals("one scan batch reads by name", listOf("Alpha", "Bravo", "Charlie"), dao.observeCollectionGames(rpgs, SortGamesSetting.RecentlyAdded).first().map { it.title })
        for (sort in SortGamesSetting.entries) {
            assertEquals("same rows as Favorites for $sort", db.gameDao().observeFavorites(sort).first(), dao.observeCollectionGames(rpgs, sort).first())
        }
    }
}
