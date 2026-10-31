package com.plumemu.content

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScanBatch
import com.plumemu.coredata.db.SystemEntity
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.game.EmulatorToStart
import com.plumemu.home.GamePlay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

/** The details column's play facts from an in-memory play history: played, never played, the emulator, a closed database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class LibraryPlayFactsTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java).build()

    /** Today at noon, so a few calendar days back stay whole days in any time zone. */
    private val now = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0) }.timeInMillis
    private val snes9x = EmulatorToStart("Snes9x", isInstalled = true)
    private val asked = mutableListOf<Long>()
    private val facts = LibraryPlayFacts(plays = { db.playDao() }, games = { db.gameDao() }, emulatorOf = { asked += it; snes9x }, now = { now })
    private lateinit var ids: Map<String, Long>

    private fun daysBack(days: Int): Long = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, -days) }.timeInMillis

    @Before
    fun setUp() =
        runBlocking {
            val folderId = db.scanDao().insertFolder(RomFolderEntity(treeUri = "content://t/tree/roms", displayName = "ROMs", addedAt = 0))
            db.scanDao().applyBatch(
                ScanBatch(folderId, systems = listOf(SystemEntity("snes", "snes")), newGames = listOf(game(folderId, "Super Metroid"), game(folderId, "Zelda"))),
            )
            ids = db.scanDao().gamesInFolder(folderId).associate { it.title to it.id }
            db.playDao().addSession(ids.getValue("Super Metroid"), endedAt = daysBack(2), seconds = 252 * 60L + 59)
        }

    @After
    fun tearDown() = db.close()

    private fun game(
        folderId: Long,
        title: String,
    ) = GameEntity(
        folderId = folderId,
        documentId = "root/snes/$title",
        parentDocumentId = "root/snes",
        relativePath = "snes/$title",
        fileName = title,
        sizeBytes = 1,
        modifiedAt = 1,
        title = title,
        sortName = title.lowercase(),
        regions = "",
        discGroup = null,
        discCount = 1,
        systemId = "snes",
        isPrimaryCopy = true,
    )

    @Test
    fun aPlayedGameGetsItsDaysWholeMinutesAndTheEmulatorPlayWouldStart() =
        runBlocking {
            val metroid = ids.getValue("Super Metroid")
            assertEquals(GamePlay(lastPlayedDaysAgo = 2, playTimeMinutes = 252, emulator = snes9x), facts.read(metroid))
            assertEquals("the emulator asked for this game", listOf(metroid), asked)
        }

    @Test
    fun theUsualBatteryPaceComesWithThePlayFacts() =
        runBlocking {
            val withPace = LibraryPlayFacts(plays = { db.playDao() }, games = { db.gameDao() }, emulatorOf = { snes9x }, paceOf = { com.plumemu.battery.BatteryPace(minutesOnCharge = 120.0, percentPerHour = 30.0) }, now = { now })
            assertEquals(com.plumemu.battery.BatteryPace(minutesOnCharge = 120.0, percentPerHour = 30.0), withPace.read(ids.getValue("Super Metroid"))?.batteryPace)
            assertEquals("none by default", null, facts.read(ids.getValue("Super Metroid"))?.batteryPace)
        }

    @Test
    fun aGameNeverPlayedReadsAsNeverPlayedWithNoTime() =
        runBlocking {
            assertEquals(GamePlay(lastPlayedDaysAgo = null, playTimeMinutes = 0, emulator = snes9x), facts.read(ids.getValue("Zelda")))
        }

    @Test
    fun noEmulatorInstalledIsNullForTheScreenToWordAsNoneInstalled() =
        runBlocking {
            val none = LibraryPlayFacts(plays = { db.playDao() }, games = { db.gameDao() }, emulatorOf = { null }, now = { now })
            assertEquals(null, none.read(ids.getValue("Zelda"))?.emulator)
        }

    /** A row left from before a rescan names a game the library no longer has: no table, not "Never / 0 min". */
    @Test
    fun aGameTheLibraryNoLongerHasReadsAsNull() =
        runBlocking {
            val gone = ids.values.max() + 1000
            assertEquals(null, facts.read(gone))
            assertEquals("no emulator asked for it", emptyList<Long>(), asked)
        }

    /** The screen catches it, logs it and shows the column without the table (HomeTextDetailsTest). */
    @Test
    fun aClosedDatabaseThrowsToTheScreen() {
        db.close()
        val thrown = runCatching { runBlocking { facts.read(1) } }.exceptionOrNull()
        assertTrue("the read failed: $thrown", thrown is Exception)
    }
}
