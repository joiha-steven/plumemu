package com.plumemu.coredata.db

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Battery use per play (schema 8): stored with the session row, and a game's usual
 * pace, on an in-memory database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionBatteryDaoTest {
    private lateinit var db: PlumDatabase
    private val dao get() = db.playDao()

    @Before
    fun setUp() {
        db = inMemoryDatabase()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insertGames(vararg documentIds: String): List<Long> {
        val folderId = db.scanDao().insertFolder(RomFolderEntity(treeUri = "t", displayName = "ROMs", addedAt = 0))
        db.scanDao().applyBatch(ScanBatch(folderId, listOf(SystemEntity("snes", "Super Nintendo")), documentIds.map { game(folderId, it) }))
        val byDocument = db.scanDao().gamesInFolder(folderId).associate { it.documentId to it.id }
        return documentIds.map { byDocument.getValue(it) }
    }

    /** One play of [gameId] ending at [endedAt] that measured [seconds] at [draw] microamperes. */
    private suspend fun play(gameId: Long, endedAt: Long, seconds: Long, draw: Long) =
        dao.addSession(gameId, endedAt = endedAt, seconds = seconds + 60, battery = SessionBattery(seconds, 4_000_000, 3_900_000, draw))

    @Test
    fun aPlayKeepsWhatItMeasuredInItsSessionRow() =
        runTest {
            val id = insertGames("metroid").single()
            dao.addSession(id, endedAt = 10_000, seconds = 900, battery = SessionBattery(840, 2_060_000, 1_970_000, 385_000))
            val row = dao.sessions(id).single()
            assertEquals(840L, row.batterySeconds)
            assertEquals(2_060_000L, row.chargeStartMicroampHours)
            assertEquals(1_970_000L, row.chargeEndMicroampHours)
            assertEquals(385_000L, row.batteryDrawMicroamps)
            assertEquals("the total is unchanged by the battery figures", 900L, dao.play(id)!!.totalSeconds)
        }

    @Test
    fun aPlayWithoutMeasurementStoresNullsAndADeviceWithoutACounterNoCharge() =
        runTest {
            val id = insertGames("metroid").single()
            dao.addSession(id, endedAt = 10_000, seconds = 900)
            dao.addSession(id, endedAt = 20_000, seconds = 900, battery = SessionBattery(600, null, null, 400_000))
            val (unmeasured, noCounter) = dao.sessions(id)
            assertNull(unmeasured.batterySeconds)
            assertNull(unmeasured.batteryDrawMicroamps)
            assertNull(unmeasured.chargeStartMicroampHours)
            assertEquals(600L, noCounter.batterySeconds)
            assertNull(noCounter.chargeStartMicroampHours)
            assertNull(noCounter.chargeEndMicroampHours)
            assertEquals("a play without a counter still has a pace", 400_000L, dao.usualDraw(id))
        }

    @Test
    fun noUsualPaceForAGameNeverMeasured() =
        runTest {
            val id = insertGames("metroid").single()
            assertNull("never played", dao.usualDraw(id))
            dao.addSession(id, endedAt = 10_000, seconds = 900)
            assertNull("played, never measured", dao.usualDraw(id))
        }

    @Test
    fun theUsualPaceWeighsEachPlayByItsMeasuredLength() =
        runTest {
            val id = insertGames("metroid").single()
            play(id, endedAt = 1_000, seconds = 600, draw = 300_000)
            play(id, endedAt = 2_000, seconds = 1_800, draw = 500_000)
            // (600 x 300 + 1800 x 500) / 2400 = 450 mA, not the plain mean 400.
            assertEquals(450_000L, dao.usualDraw(id))
        }

    @Test
    fun playsUnderThreeMeasuredMinutesDoNotCountNorTakeAPlaceAmongTheLastFive() =
        runTest {
            val id = insertGames("metroid").single()
            play(id, endedAt = 1_000, seconds = 600, draw = 300_000)
            play(id, endedAt = 2_000, seconds = 179, draw = 900_000)
            assertEquals("under three minutes is left out", 300_000L, dao.usualDraw(id))
            play(id, endedAt = 3_000, seconds = 180, draw = 600_000)
            // Exactly three minutes counts: (600 x 300 + 180 x 600) / 780.
            assertEquals((600L * 300_000 + 180L * 600_000) / 780, dao.usualDraw(id))
            repeat(4) { play(id, endedAt = 4_000L + it, seconds = 170, draw = 900_000) }
            assertEquals("short plays do not push measured ones out", (600L * 300_000 + 180L * 600_000) / 780, dao.usualDraw(id))
        }

    @Test
    fun onlyTheLastFiveMeasuredPlaysCountNewestRecordedFirst() =
        runTest {
            val id = insertGames("metroid").single()
            play(id, endedAt = 1_000, seconds = 600, draw = 900_000)
            for (i in 1..4) play(id, endedAt = 2_000L + i, seconds = 600, draw = 400_000)
            // Two plays ending at the same moment: the later recorded is the newer.
            play(id, endedAt = 9_000, seconds = 600, draw = 400_000)
            play(id, endedAt = 9_000, seconds = 600, draw = 400_000)
            assertEquals("the oldest (900 mA) fell out of the last five", 400_000L, dao.usualDraw(id))
        }

    @Test
    fun aPlayRecordedWithTheClockAYearAheadFallsOutOfTheLastFiveLikeAnyOther() =
        runTest {
            val id = insertGames("metroid").single()
            // The device's clock was set a year ahead for this play, then put right.
            play(id, endedAt = 1_800_000_000_000, seconds = 600, draw = 900_000)
            for (i in 1..5) play(id, endedAt = 1_760_000_000_000 + i, seconds = 600, draw = 400_000)
            assertEquals("the five recorded after it are the last five", 400_000L, dao.usualDraw(id))
        }

    @Test
    fun aGameSeesOnlyItsOwnPlays() =
        runTest {
            val (metroid, zelda) = insertGames("metroid", "zelda")
            play(metroid, endedAt = 1_000, seconds = 600, draw = 300_000)
            play(zelda, endedAt = 2_000, seconds = 600, draw = 700_000)
            assertEquals(300_000L, dao.usualDraw(metroid))
            assertEquals(700_000L, dao.usualDraw(zelda))
        }

    @Test
    fun movingSessionsOntoAKeptRowCarriesTheirBatteryFigures() =
        runTest {
            val (old, kept) = insertGames("old", "kept")
            play(old, endedAt = 1_000, seconds = 600, draw = 350_000)
            // The statement of ScanDao.moveSessions (protected), run before a merged or folded row is deleted.
            db.openHelper.writableDatabase.execSQL("UPDATE play_session SET game_id = $kept WHERE game_id = $old")
            assertEquals(350_000L, dao.usualDraw(kept))
            assertNull(dao.usualDraw(old))
        }
}
