package com.plumemu.battery

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScanBatch
import com.plumemu.coredata.db.SessionBattery
import com.plumemu.coredata.db.SystemEntity
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.launcher.play.battery.BatteryReader
import com.plumemu.launcher.play.battery.UsualPace
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A game's usual pace from its stored plays, over a device that is 6,254 mAh at 50%. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class LibraryBatteryPaceTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java).build()
    private lateinit var ids: Map<String, Long>

    private val reader =
        object : BatteryReader {
            override fun nowMs() = 0L

            override fun currentNow() = BatteryReader.UNKNOWN

            override fun chargeCounter() = 3_127_000L

            override fun level() = 50

            override fun isCharging() = false

            override fun chargeTimeRemainingMs() = BatteryReader.UNKNOWN
        }
    private val pace = LibraryBatteryPace(plays = { db.playDao() }, usualPace = UsualPace(reader))

    @Before
    fun setUp() =
        runBlocking {
            val folderId = db.scanDao().insertFolder(RomFolderEntity(treeUri = "content://t/tree/roms", displayName = "ROMs", addedAt = 0))
            db.scanDao().applyBatch(
                ScanBatch(folderId, systems = listOf(SystemEntity("snes", "snes")), newGames = listOf(game(folderId, "Measured"), game(folderId, "Played"), game(folderId, "Never"))),
            )
            ids = db.scanDao().gamesInFolder(folderId).associate { it.title to it.id }
            // 625.4 mA for 10 minutes of 6,254 mAh is 10% an hour.
            db.playDao().addSession(ids.getValue("Measured"), endedAt = 1_000_000, seconds = 600, battery = SessionBattery(600, 3_000_000, 2_900_000, 625_400))
            db.playDao().addSession(ids.getValue("Played"), endedAt = 1_000_000, seconds = 600)
        }

    @After
    fun tearDown() = db.close()

    private fun game(
        folderId: Long,
        title: String,
    ) = GameEntity(
        folderId = folderId, documentId = "root/$title", parentDocumentId = "root", relativePath = title, fileName = title, sizeBytes = 1, modifiedAt = 1,
        title = title, sortName = title.lowercase(), regions = "", discGroup = null, discCount = 1, systemId = "snes", isPrimaryCopy = true,
    )

    @Test
    fun aMeasuredGameHasItsUsualPaceInPercentAnHour() =
        runBlocking {
            assertEquals(10.0, pace.read(ids.getValue("Measured"))!!.percentPerHour!!, 0.01)
        }

    @Test
    fun aGamePlayedButNeverMeasuredHasNoPace() =
        runBlocking {
            assertNull(pace.read(ids.getValue("Played")))
            assertNull(pace.read(ids.getValue("Never")))
            assertNull("an id the library does not hold", pace.read(ids.values.max() + 1000))
        }

    @Test
    fun aClosedDatabaseThrowsToTheCaller() {
        db.close()
        val thrown = runCatching { runBlocking { pace.read(1) } }.exceptionOrNull()
        assertTrue("the read failed: $thrown", thrown is Exception)
    }
}
