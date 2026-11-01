package com.plumemu.coredata.db

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings › Statistics' reads of the play history, on an in-memory database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayStatisticsDaoTest {
    private lateinit var db: PlumDatabase
    private val dao get() = db.playDao()

    @Before
    fun setUp() {
        db = inMemoryDatabase()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insert(vararg games: GameEntity): Map<String, Long> {
        val folderId = db.scanDao().insertFolder(RomFolderEntity(treeUri = "t", displayName = "ROMs", addedAt = 0))
        db.scanDao().applyBatch(
            ScanBatch(
                folderId,
                listOf(
                    SystemEntity("snes", "Super Nintendo"), SystemEntity("md", "Mega Drive"), SystemEntity("gba", "Game Boy Advance"),
                    SystemEntity("arcade", "Arcade"),
                ),
                games.map { it.copy(folderId = folderId) },
            ),
        )
        return db.scanDao().gamesInFolder(folderId).associate { it.documentId to it.id }
    }

    private suspend fun play(
        ids: Map<String, Long>,
        key: String,
        seconds: Long,
    ) = dao.addSession(ids.getValue(key), endedAt = 1_000, seconds = seconds)

    @Test
    fun nothingPlayedReadsAsZeroAndNoCopies() =
        runTest {
            insert(game(0, "metroid"), game(0, "sonic", systemId = "md"))
            assertEquals(0L, dao.totalSeconds())
            assertEquals(emptyList<PlayedCopy>(), dao.playedCopies())
        }

    @Test
    fun theTotalCountsEveryGameStillInTheLibraryHiddenOnesToo() =
        runTest {
            val ids = insert(game(0, "metroid"), game(0, "hidden"), game(0, "bios").copy(isNotGame = true))
            db.gameDao().setHidden(ids.getValue("hidden"), true)
            play(ids, "metroid", 600)
            play(ids, "metroid", 60)
            play(ids, "hidden", 100)
            play(ids, "bios", 5)
            assertEquals(765L, dao.totalSeconds())
        }

    @Test
    fun theCopiesOfAPlayedGameComeWithItPlayedOrNotAndNothingElse() =
        runTest {
            val ids =
                insert(
                    game(0, "zelda-usa", title = "Zelda", parent = "root/snes").copy(fileName = "Zelda (USA).sfc", isPrimaryCopy = false),
                    game(0, "zelda-eur", title = "Zelda", parent = "root/snes").copy(fileName = "Zelda (Europe).sfc"),
                    // Same name, another directory: grouped apart by the cataloguer.
                    game(0, "zelda-other", title = "Zelda", parent = "root/other"),
                    game(0, "metroid", title = "Super Metroid", parent = "root/snes"),
                    game(0, "sonic", systemId = "md", title = "Sonic"),
                )
            play(ids, "zelda-usa", 600)
            play(ids, "sonic", 90)
            val copies = dao.playedCopies().sortedBy { it.id }
            assertEquals(listOf("zelda-usa", "zelda-eur", "sonic").map { ids.getValue(it) }.sorted(), copies.map { it.id })
            val eur = copies.single { it.id == ids.getValue("zelda-eur") }
            assertEquals("never played itself", 0L, eur.totalSeconds)
            assertEquals(true, eur.isPrimaryCopy)
            assertEquals("Zelda (Europe).sfc", eur.fileName)
            assertEquals("zelda", eur.fileSortName)
            assertEquals("Super Nintendo", eur.systemName)
            assertEquals("game:${ids.getValue("zelda-eur")}", eur.scrapeKey)
            assertEquals(600L, copies.single { it.id == ids.getValue("zelda-usa") }.totalSeconds)
        }

    @Test
    fun aPlayedGameAloneInItsFolderComesWithTheGamesAloneInOtherFoldersOfItsName() =
        runTest {
            val ids =
                insert(
                    game(0, "zelda-usa", title = "Zelda", parent = "root/snes/Zelda (USA)"),
                    game(0, "zelda-eur", title = "Zelda", parent = "root/snes/Zelda (Europe)").copy(isPrimaryCopy = false),
                    // A folder of two games is no game folder: library's grouping leaves its rows to their directory.
                    game(0, "zelda-mixed", title = "Zelda", parent = "root/snes/Mixed"),
                    game(0, "metroid-mixed", title = "Super Metroid", parent = "root/snes/Mixed"),
                )
            play(ids, "zelda-eur", 600)
            val copies = dao.playedCopies().sortedBy { it.id }
            assertEquals(listOf("zelda-usa", "zelda-eur").map { ids.getValue(it) }.sorted(), copies.map { it.id })
            assertEquals(listOf(1, 1), copies.map { it.rowsBeside })
        }

    @Test
    fun shownRowsOfAPlayedDirectoryComeByIdAndFileNameAndAreReadInFullById() =
        runTest {
            fun arcade(id: String, parent: String = "root/arcade") =
                game(0, id, systemId = "arcade", title = "Cadillacs and Dinosaurs", parent = parent).copy(fileName = "$id.zip", fileSortName = id)
            val ids =
                insert(
                    arcade("dinou"),
                    arcade("dino").copy(isPrimaryCopy = false),
                    arcade("pang"),
                    // Another directory: not played there.
                    arcade("dinoj", parent = "root/arcade2"),
                    game(0, "zelda-usa", title = "Zelda", parent = "root/arcade"),
                )
            play(ids, "dino", 300)
            assertEquals("by file sort name, as before", listOf(ids.getValue("dino")), dao.playedCopies().map { it.id })
            val played = dao.playedCopies().single()
            val siblings = dao.shownRowsIn(played.folderId, played.parentDocumentId, "arcade")
            assertEquals("shown rows of the directory and system only", listOf("dinou", "pang").map { ids.getValue(it) }.sorted(), siblings.map { it.id }.sorted())
            assertEquals("dinou.zip", siblings.single { it.id == ids.getValue("dinou") }.fileName)
            val read = dao.playedCopiesOf(listOf(ids.getValue("dinou"), ids.getValue("dino")))
            assertEquals(dao.playedCopies().single(), read.single { it.id == ids.getValue("dino") })
            assertEquals(0L, read.single { it.id == ids.getValue("dinou") }.totalSeconds)
        }

    @Test
    fun hiddenAndNotAGameCopiesSayWhatTheyAre() =
        runTest {
            val ids = insert(game(0, "hidden"), game(0, "bios").copy(isNotGame = true))
            db.gameDao().setHidden(ids.getValue("hidden"), true)
            play(ids, "hidden", 100)
            play(ids, "bios", 100)
            val copies = dao.playedCopies().associateBy { it.id }
            assertEquals(true, copies.getValue(ids.getValue("hidden")).isHidden)
            assertEquals(true, copies.getValue(ids.getValue("bios")).isNotGame)
        }

    /** The week's sessions in one read: those that overlap the range, by the same rule as `secondsPlayedBetween`. */
    @Test
    fun sessionsBetweenListsTheSessionsThatTouchTheRange() =
        runTest {
            val id = insert(game(0, "metroid")).getValue("metroid")
            dao.addSession(id, endedAt = 5_000, seconds = 2, startedAt = 3_000)
            dao.addSession(id, endedAt = 12_000, seconds = 4, startedAt = 8_000)
            dao.addSession(id, endedAt = 30_000, seconds = 1, startedAt = 29_000)
            dao.addSession(id, endedAt = 9_500, seconds = 0, startedAt = 9_500)
            assertEquals(
                "the one across the start, the zero-length one inside, not the one before or after",
                listOf(SessionSpan(8_000, 12_000, 4), SessionSpan(9_500, 9_500, 0)),
                dao.sessionsBetween(9_000, 20_000).sortedBy { it.startedAt },
            )
        }
}
