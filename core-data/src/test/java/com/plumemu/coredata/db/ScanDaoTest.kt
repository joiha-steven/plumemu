package com.plumemu.coredata.db

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScanDaoTest {
    private lateinit var db: PlumDatabase
    private val dao get() = db.scanDao()
    private var folderId = 0L

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        folderId = dao.insertFolder(RomFolderEntity(treeUri = "content://tree/roms", displayName = "ROMs", addedAt = 0))
    }

    @After
    fun tearDown() = db.close()

    private fun batch(vararg games: GameEntity) =
        ScanBatch(folderId, systems = listOf(SystemEntity("snes", "snes")), newGames = games.toList())

    @Test
    fun sameTreeUriIsStoredOnce() = runTest {
        val again = dao.insertFolder(RomFolderEntity(treeUri = "content://tree/roms", displayName = "ROMs", addedAt = 5))
        assertEquals(-1L, again)
        assertEquals(1, dao.folders().size)
    }

    @Test
    fun insertingTheSameFileTwiceKeepsOneRow() = runTest {
        dao.applyBatch(batch(game(folderId, "a.sfc")))
        dao.applyBatch(batch(game(folderId, "a.sfc")))
        assertEquals(1, dao.gamesInFolder(folderId).size)
    }

    @Test
    fun rescanUpdateKeepsFavouriteAndHidden() = runTest {
        dao.applyBatch(batch(game(folderId, "a.sfc", title = "Old")))
        val id = dao.gamesInFolder(folderId).single().id
        db.gameDao().setFavorite(id, true)
        db.gameDao().setHidden(id, true)

        val stored = dao.gamesInDirectory(folderId, "root").single()
        dao.applyBatch(ScanBatch(folderId, changedGames = listOf(stored.copy(fields = stored.fields.copy(title = "New")))))

        val after = dao.gamesInFolder(folderId).single()
        assertEquals("New", after.title)
        assertTrue(after.isFavorite)
        assertTrue(after.isHidden)
    }

    @Test
    fun subtreeQueriesTakeOnlyTheirOwnRowsEvenWithLikeCharactersInNames() = runTest {
        dao.applyBatch(
            batch(
                game(folderId, "1", relativePath = "snes_%/a.sfc"),
                game(folderId, "2", relativePath = "snes_%/sub/b.sfc"),
                game(folderId, "3", relativePath = "snes_%x/c.sfc"),
                game(folderId, "4", relativePath = "snesA%/d.sfc"),
            ),
        )
        assertEquals(setOf("1", "2"), dao.gamesInSubtree(folderId, "snes_%").map { it.fields.documentId }.toSet())
        dao.finishScan(
            ScanFinish(
                folderId,
                directories = listOf("snes_%", "snes_%/sub", "snes_%x", "snesA%").map { ScannedDirectoryEntity(folderId, it, it, 1) },
            ),
        )
        dao.finishScan(ScanFinish(folderId, removedDirectoryPaths = listOf("snes_%")))
        assertEquals(setOf("snes_%x", "snesA%"), dao.directories(folderId).map { it.relativePath }.toSet())
    }

    @Test
    fun removingMoreIdsThanSqliteAllowsInOneStatementWorks() = runTest {
        val games = (1..1500).map { game(folderId, "g$it") }
        dao.applyBatch(batch(*games.toTypedArray()))
        val ids = dao.gamesInFolder(folderId).map { it.id }
        dao.finishScan(ScanFinish(folderId, removedGameIds = ids))
        assertEquals(0, dao.gamesInFolder(folderId).size)
    }

    @Test
    fun applyBatchReturnsOnlyRowsReallyInserted() = runTest {
        assertEquals(2, dao.applyBatch(batch(game(folderId, "a.sfc"), game(folderId, "b.sfc"))).size)
        val again = dao.applyBatch(batch(game(folderId, "a.sfc"), game(folderId, "c.sfc")))
        assertEquals(listOf("c.sfc"), again.map { it.fields.documentId })
    }

    @Test
    fun ignoredInsertOfAKnownDocumentMovesTheStoredRowAndKeepsItsMarks() = runTest {
        dao.applyBatch(batch(game(folderId, "id-7", parent = "snes", relativePath = "snes/Game.sfc")))
        val id = dao.gamesInFolder(folderId).single().id
        db.gameDao().setFavorite(id, true)
        dao.applyBatch(batch(game(folderId, "id-7", parent = "hacks", relativePath = "snes/hacks/Game.sfc")))
        val after = dao.gamesInFolder(folderId).single()
        assertEquals(id, after.id)
        assertEquals("snes/hacks/Game.sfc", after.relativePath)
        assertTrue(after.isFavorite)
    }

    @Test
    fun aMoveKeepsTheOldRowWithItsMarksPlayAndEmulatorAndDropsTheNewRow() = runTest {
        dao.applyBatch(batch(game(folderId, "old", relativePath = "PS1/a.sfc")))
        val old = dao.gamesInFolder(folderId).single()
        db.gameDao().setFavorite(old.id, true)
        db.gameDao().setHidden(old.id, true)
        db.playDao().addSession(old.id, endedAt = 10, seconds = 60)
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO emulator_choice (system_id, game_id, emulator_id, is_person_choice) VALUES ('snes', ${old.id}, 'pkg', 1)",
        )
        val inserted = dao.applyBatch(batch(game(folderId, "new", relativePath = "psx/a.sfc"))).single()

        dao.finishScan(ScanFinish(folderId, moves = listOf(GameMove(old.id, inserted))))

        val after = dao.gamesInFolder(folderId).single()
        assertEquals(old.id, after.id)
        assertEquals("new", after.documentId)
        assertEquals("psx/a.sfc", after.relativePath)
        assertTrue(after.isFavorite && after.isHidden)
        assertEquals(60L, db.playDao().play(old.id)?.totalSeconds)
        assertEquals("pkg", db.emulatorChoiceDao().gameChoice(old.id)?.emulatorId)
    }

    @Test
    fun aMoveMergesWhatThePersonDidToTheNewRowDuringTheScan() = runTest {
        dao.applyBatch(batch(game(folderId, "old-a", relativePath = "PS1/a.sfc"), game(folderId, "old-b", relativePath = "PS1/b.sfc")))
        val oldA = dao.gamesInFolder(folderId).single { it.documentId == "old-a" }
        val oldB = dao.gamesInFolder(folderId).single { it.documentId == "old-b" }
        db.gameDao().setHidden(oldA.id, true)
        db.playDao().addSession(oldA.id, endedAt = 10, seconds = 60)
        setGameEmulator(oldB.id, "pkg-old")
        val inserted = dao.applyBatch(batch(game(folderId, "new-a", relativePath = "psx/a.sfc"), game(folderId, "new-b", relativePath = "psx/b.sfc")))
        val newA = inserted.single { it.fields.documentId == "new-a" }
        val newB = inserted.single { it.fields.documentId == "new-b" }
        // The new rows were on the list screens since their batch: the person used them.
        db.gameDao().setFavorite(newA.id, true)
        db.playDao().addSession(newA.id, endedAt = 20, seconds = 30)
        setGameEmulator(newA.id, "pkg-new-a")
        db.playDao().addSession(newB.id, endedAt = 5, seconds = 15)
        setGameEmulator(newB.id, "pkg-new-b")

        dao.finishScan(ScanFinish(folderId, moves = listOf(GameMove(oldA.id, newA), GameMove(oldB.id, newB))))

        val a = db.gameDao().game(oldA.id) ?: error("kept row A was deleted")
        assertTrue("favourite from the new row", a.isFavorite)
        assertTrue("hidden from the old row", a.isHidden)
        assertEquals(PlayEntity(oldA.id, lastPlayedAt = 20, totalSeconds = 90), db.playDao().play(oldA.id))
        assertEquals("pkg-new-a", db.emulatorChoiceDao().gameChoice(oldA.id)?.emulatorId)
        assertEquals(PlayEntity(oldB.id, lastPlayedAt = 5, totalSeconds = 15), db.playDao().play(oldB.id))
        assertEquals("the old row's own choice stays", "pkg-old", db.emulatorChoiceDao().gameChoice(oldB.id)?.emulatorId)
        assertEquals(2, dao.gamesInFolder(folderId).size)
    }

    @Test
    fun aFoldJoinsFavouritePlayAndEmulatorToTheKeptRowButNotTheHiddenMark() = runTest {
        dao.applyBatch(batch(game(folderId, "disc-1", relativePath = "PS1/Game - Disc 1.chd"), game(folderId, "disc-2", relativePath = "PS1/Game - Disc 2.chd")))
        val first = dao.gamesInFolder(folderId).single { it.documentId == "disc-1" }
        val second = dao.gamesInFolder(folderId).single { it.documentId == "disc-2" }
        db.playDao().addSession(first.id, endedAt = 10, seconds = 60)
        // The person had marked the extra entry of an older scan: favourite, hidden, played, own emulator.
        db.gameDao().setFavorite(second.id, true)
        db.gameDao().setHidden(second.id, true)
        db.playDao().addSession(second.id, endedAt = 30, seconds = 20)
        setGameEmulator(second.id, "pkg-disc-2")

        dao.finishScan(ScanFinish(folderId, folds = listOf(GameFold(keptGameId = first.id, foldedGameId = second.id))))

        val kept = dao.gamesInFolder(folderId).single()
        assertEquals(first.id, kept.id)
        assertEquals("disc-1", kept.documentId)
        assertTrue("favourite joins", kept.isFavorite)
        assertEquals("a hidden extra disc entry does not hide the game", false, kept.isHidden)
        assertEquals(PlayEntity(first.id, lastPlayedAt = 30, totalSeconds = 80), db.playDao().play(first.id))
        assertEquals("pkg-disc-2", db.emulatorChoiceDao().gameChoice(first.id)?.emulatorId)
    }

    private fun sql(statement: String) = db.openHelper.writableDatabase.execSQL(statement)

    private fun rowFixKinds(gameId: Long): List<String> =
        db.openHelper.readableDatabase.query("SELECT kind FROM name_fix WHERE fix_key = 'game:$gameId'").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }

    private fun notAGame(gameId: Long) {
        sql("INSERT INTO name_fix (fix_key, kind, title, title_source, game_id, fixed_at) VALUES ('game:$gameId', 'NOT_A_GAME', NULL, NULL, $gameId, 1)")
        sql("UPDATE game SET is_not_game = 1 WHERE id = $gameId")
    }

    private suspend fun twoDiscRows(prefix: String): Pair<GameEntity, GameEntity> {
        dao.applyBatch(batch(game(folderId, "$prefix-1", relativePath = "PS1/$prefix - Disc 1.chd"), game(folderId, "$prefix-2", relativePath = "PS1/$prefix - Disc 2.chd")))
        val rows = dao.gamesInFolder(folderId)
        return rows.single { it.documentId == "$prefix-1" } to rows.single { it.documentId == "$prefix-2" }
    }

    private suspend fun twoStoredRows(prefix: String): Pair<StoredGame, StoredGame> {
        val rows = dao.applyBatch(batch(game(folderId, "$prefix-1", relativePath = "PS1/$prefix (Disc 1).chd"), game(folderId, "$prefix-2", relativePath = "PS1/$prefix.m3u")))
        return rows.single { it.fields.documentId == "$prefix-1" } to rows.single { it.fields.documentId == "$prefix-2" }
    }

    @Test
    fun aFoldedEntryMarkedNotAGameNeverMakesTheGameNotAGame() = runTest {
        val (first, second) = twoDiscRows("game")
        notAGame(second.id)
        dao.finishScan(ScanFinish(folderId, folds = listOf(GameFold(first.id, second.id))))
        val kept = dao.gamesInFolder(folderId).single()
        assertEquals(false, kept.isNotGame)
        assertEquals(emptyList<String>(), rowFixKinds(first.id))
        assertEquals("the folded row's fix went with it", emptyList<String>(), rowFixKinds(second.id))
    }

    @Test
    fun aFoldedEntrysOwnNameStillJoinsTheGame() = runTest {
        val (first, second) = twoDiscRows("game")
        sql("INSERT INTO name_fix (fix_key, kind, title, title_source, game_id, fixed_at) VALUES ('game:${second.id}', 'OWN_NAME', 'My Game', NULL, ${second.id}, 1)")
        dao.finishScan(ScanFinish(folderId, folds = listOf(GameFold(first.id, second.id))))
        assertEquals(listOf("OWN_NAME"), rowFixKinds(first.id))
    }

    @Test
    fun theKeptRowsNotAGameGoesWhenTheFoldedRowWasAGameAndTheRowIsNamedAgain() = runTest {
        val (first, second) = twoDiscRows("game")
        notAGame(first.id)
        sql("UPDATE game SET needs_identify = 0")
        dao.finishScan(ScanFinish(folderId, folds = listOf(GameFold(first.id, second.id))))
        assertEquals(emptyList<String>(), rowFixKinds(first.id))
        assertTrue("named again, so is_not_game follows", dao.gamesInFolder(folderId).single().needsIdentify)
    }

    @Test
    fun aFoldedGameIsHiddenOnlyWhenEveryRowOfItWasHidden() = runTest {
        val (visibleKept, visibleFolded) = twoDiscRows("a")
        db.gameDao().setHidden(visibleKept.id, true) // e.g. FF7.m3u hidden as a duplicate of the visible discs
        val (hiddenKept, hiddenFolded) = twoDiscRows("b")
        db.gameDao().setHidden(hiddenKept.id, true)
        db.gameDao().setHidden(hiddenFolded.id, true)
        dao.finishScan(ScanFinish(folderId, folds = listOf(GameFold(visibleKept.id, visibleFolded.id), GameFold(hiddenKept.id, hiddenFolded.id))))
        assertEquals(false, db.gameDao().game(visibleKept.id)?.isHidden)
        assertEquals(true, db.gameDao().game(hiddenKept.id)?.isHidden)
    }

    @Test
    fun aFoldedRowMarkedNotAGameCountsAsHiddenSoAHiddenGameStaysHidden() = runTest {
        val (kept, folded) = twoDiscRows("game")
        db.gameDao().setHidden(kept.id, true)
        notAGame(folded.id)
        dao.finishScan(ScanFinish(folderId, folds = listOf(GameFold(kept.id, folded.id))))
        assertEquals(true, db.gameDao().game(kept.id)?.isHidden)
    }

    @Test
    fun theKeptRowsContentKeyNotAGameGoesOnlyWhenAListedGameFoldsIn() = runTest {
        val (listedKept, listedFolded) = twoDiscRows("a")
        val (hiddenKept, hiddenFolded) = twoDiscRows("b")
        for ((row, key) in listOf(listedKept to "chd:aaa", hiddenKept to "chd:bbb")) {
            sql("UPDATE game SET fix_keys = '$key', is_not_game = 1, needs_identify = 0 WHERE id = ${row.id}")
            sql("INSERT INTO name_fix (fix_key, kind, title, title_source, game_id, fixed_at) VALUES ('$key', 'NOT_A_GAME', NULL, NULL, NULL, 1)")
        }
        db.gameDao().setHidden(hiddenFolded.id, true)
        dao.finishScan(ScanFinish(folderId, folds = listOf(GameFold(listedKept.id, listedFolded.id), GameFold(hiddenKept.id, hiddenFolded.id))))
        val kinds = db.openHelper.readableDatabase.query("SELECT fix_key FROM name_fix ORDER BY fix_key").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
        assertEquals("a listed game folded in: its Not a game goes; a hidden one: it stays", listOf("chd:bbb"), kinds)
        assertEquals(true, db.gameDao().game(listedKept.id)?.needsIdentify)
    }

    @Test
    fun aMoveOntoANewLaunchRowKeepsTheOldHiddenMarkAndLeavesTheNewRowsNotAGame() = runTest {
        val (visibleOld, markedNew) = twoStoredRows("a")
        db.gameDao().setHidden(markedNew.id, true) // the m3u hidden as a duplicate of the visible discs
        notAGame(markedNew.id)
        db.gameDao().setFavorite(markedNew.id, true)
        val (hiddenOld, freshNew) = twoStoredRows("b")
        db.gameDao().setHidden(hiddenOld.id, true)
        dao.finishScan(
            ScanFinish(
                folderId,
                moves = listOf(
                    GameMove(visibleOld.id, markedNew, ontoNewLaunchRow = true),
                    GameMove(hiddenOld.id, freshNew, ontoNewLaunchRow = true),
                ),
            ),
        )
        assertEquals(false, db.gameDao().game(visibleOld.id)?.isHidden)
        assertEquals("favourite joins", true, db.gameDao().game(visibleOld.id)?.isFavorite)
        assertEquals(emptyList<String>(), rowFixKinds(visibleOld.id))
        assertEquals("a hidden game stays hidden when a fresh row takes over", true, db.gameDao().game(hiddenOld.id)?.isHidden)
        assertEquals(2, dao.gamesInFolder(folderId).size)
    }

    /** Each session of [gameId] as (seconds, ended at), oldest first. */
    private suspend fun sessionsOf(gameId: Long): List<Pair<Long, Long>> = db.playDao().sessions(gameId).map { it.seconds to it.endedAt }

    private fun sessionCount(): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM play_session").use { c ->
            c.moveToFirst()
            c.getInt(0)
        }

    @Test
    fun aMoveTakesTheNewRowsSessionsToTheKeptRow() = runTest {
        dao.applyBatch(batch(game(folderId, "old-a", relativePath = "PS1/a.sfc"), game(folderId, "old-b", relativePath = "PS1/b.sfc")))
        val oldA = dao.gamesInFolder(folderId).single { it.documentId == "old-a" }
        val oldB = dao.gamesInFolder(folderId).single { it.documentId == "old-b" }
        db.playDao().addSession(oldA.id, endedAt = 100_000, seconds = 60)
        val inserted = dao.applyBatch(batch(game(folderId, "new-a", relativePath = "psx/a.sfc"), game(folderId, "new-b", relativePath = "psx/b.sfc")))
        val newA = inserted.single { it.fields.documentId == "new-a" }
        val newB = inserted.single { it.fields.documentId == "new-b" }
        db.playDao().addSession(newA.id, endedAt = 200_000, seconds = 30)
        // The kept row B was never played: its play row moves, and its sessions with it.
        db.playDao().addSession(newB.id, endedAt = 300_000, seconds = 15)

        dao.finishScan(ScanFinish(folderId, moves = listOf(GameMove(oldA.id, newA), GameMove(oldB.id, newB))))

        assertEquals(listOf(60L to 100_000L, 30L to 200_000L), sessionsOf(oldA.id))
        assertEquals(listOf(15L to 300_000L), sessionsOf(oldB.id))
        assertEquals("nothing dropped by the delete", 3, sessionCount())
        assertEquals(db.playDao().play(oldA.id)?.totalSeconds, sessionsOf(oldA.id).sumOf { it.first })
    }

    @Test
    fun aFoldTakesTheFoldedRowsSessionsToTheKeptRow() = runTest {
        val (first, second) = twoDiscRows("game")
        db.playDao().addSession(first.id, endedAt = 100_000, seconds = 60)
        db.playDao().addSession(second.id, endedAt = 200_000, seconds = 20)
        db.playDao().addSession(second.id, endedAt = 300_000, seconds = 25)

        dao.finishScan(ScanFinish(folderId, folds = listOf(GameFold(keptGameId = first.id, foldedGameId = second.id))))

        assertEquals(listOf(60L to 100_000L, 20L to 200_000L, 25L to 300_000L), sessionsOf(first.id))
        assertEquals(3, sessionCount())
        assertEquals(105L, db.playDao().play(first.id)?.totalSeconds)
    }

    @Test
    fun aMoveOntoANewLaunchRowTakesItsSessionsToTheOldRow() = runTest {
        val (old, newLaunchRow) = twoStoredRows("game")
        db.playDao().addSession(old.id, endedAt = 100_000, seconds = 40)
        db.playDao().addSession(newLaunchRow.id, endedAt = 200_000, seconds = 50)

        dao.finishScan(ScanFinish(folderId, moves = listOf(GameMove(old.id, newLaunchRow, ontoNewLaunchRow = true))))

        assertEquals(listOf(40L to 100_000L, 50L to 200_000L), sessionsOf(old.id))
        assertEquals(2, sessionCount())
        assertEquals(90L, db.playDao().play(old.id)?.totalSeconds)
    }

    private fun setGameEmulator(gameId: Long, emulatorId: String) = db.openHelper.writableDatabase.execSQL(
        "INSERT INTO emulator_choice (system_id, game_id, emulator_id, is_person_choice) VALUES ('snes', $gameId, '$emulatorId', 1)",
    )

    @Test
    fun deletingAFolderRemovesItsGamesDirectoriesAndPlay() = runTest {
        dao.applyBatch(batch(game(folderId, "a.sfc")))
        dao.finishScan(ScanFinish(folderId, directories = listOf(ScannedDirectoryEntity(folderId, "root", "", 1))))
        val id = dao.gamesInFolder(folderId).single().id
        db.playDao().addSession(id, endedAt = 10, seconds = 60)
        dao.deleteFolder(folderId)
        assertEquals(0, dao.gamesInFolder(folderId).size)
        assertEquals(0, dao.directories(folderId).size)
        assertEquals(null, db.playDao().play(id))
    }

    @Test
    fun forgettingTheLibraryKeepsFoldersAndSystemChoicesAndDropsGamesWithTheirPersonData() = runTest {
        dao.applyBatch(batch(game(folderId, "a.sfc"), game(folderId, "b.sfc")))
        dao.finishScan(ScanFinish(folderId, directories = listOf(ScannedDirectoryEntity(folderId, "root", "", 1))))
        dao.markScanFinished(folderId, finishedAt = 99)
        val (a, b) = dao.gamesInFolder(folderId)
        db.gameDao().setFavorite(a.id, true)
        db.playDao().addSession(a.id, endedAt = 10, seconds = 60)
        setGameEmulator(b.id, "pkg-game")
        db.emulatorChoiceDao().setPersonSystemChoice("snes", "pkg-system")

        dao.forgetLibrary()

        val folder = dao.folders().single()
        assertEquals("the picked folder stays, with its grant's URI", "content://tree/roms", folder.treeUri)
        assertEquals("the next scan is a full one", null, folder.lastScanFinishedAt)
        assertEquals(0, dao.gamesInFolder(folderId).size)
        assertEquals(0, dao.directories(folderId).size)
        assertEquals(null, db.playDao().play(a.id))
        assertEquals(null, db.emulatorChoiceDao().gameChoice(b.id))
        assertEquals("the system's emulator is a setting, not library data", "pkg-system", db.emulatorChoiceDao().systemChoice("snes")?.emulatorId)
    }

    @Test
    fun forgettingAnEmptyLibraryTwiceIsHarmless() = runTest {
        dao.forgetLibrary()
        dao.forgetLibrary()
        assertEquals(1, dao.folders().size)
    }
}
