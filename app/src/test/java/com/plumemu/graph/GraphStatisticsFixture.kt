package com.plumemu.graph

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.plumemu.content.LibraryHomeContentTest
import com.plumemu.coredata.db.NewPinnedShortcut
import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScanBatch
import com.plumemu.coredata.db.SystemEntity
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.ArtPicture
import com.plumemu.home.CoverImages
import com.plumemu.home.DetailsExtras
import com.plumemu.home.GameArt
import com.plumemu.home.GameDetails
import com.plumemu.home.HomeArtSource
import com.plumemu.settings.screen.PlayStatistics
import com.plumemu.settings.screen.StatisticsState
import com.plumemu.settings.screen.TopSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The in-memory library, play history and reader Settings › Statistics' read tests share. */
internal abstract class GraphStatisticsFixture {
    protected val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java).build()
    protected val logged = mutableListOf<String>()
    protected var signedIn = true
    protected val asked = mutableListOf<List<String>>()

    /** The home screen's results: a saved box front for every key except those of games named "No art". */
    protected val art =
        object : HomeArtSource {
            override fun observe(keys: List<String>): Flow<Map<String, GameArt>> {
                asked += keys
                return flowOf(keys.filter { it !in keysWithoutArt }.associateWith { GameArt(ArtPicture("/art/$it.png", 300, 420), null, GameDetails(null, "", "", "", "")) })
            }

            override suspend fun extras(key: String): DetailsExtras = DetailsExtras.None

            override val images: CoverImages = CoverImages.None
        }
    protected val keysWithoutArt = mutableSetOf<String>()

    protected var clock = 0L

    protected fun reader(
        catalog: Boolean = true,
        arcadeGames: ArcadeGames = ArcadeGames.None,
    ) =
        GraphStatistics(
            plays = { db.playDao() },
            scraped = { db.scrapedGameDao() },
            catalog = { if (catalog) LibraryHomeContentTest.catalog else null },
            hasBoxArt = { signedIn },
            art = art,
            io = Dispatchers.IO,
            log = { logged += it },
            now = { clock },
            zone = { zone },
            locale = { locale },
            arcadeSystems = listOf("arcade"),
            arcadeGames = arcadeGames,
        )

    protected var zone = TimeZone.getTimeZone("UTC")
    protected var locale = Locale.US

    @After
    fun tearDown() = db.close()

    /** [count] games, the system of each picked by [systemOf]; returns their ids in order. */
    protected suspend fun library(
        count: Int,
        systemOf: (Int) -> String = { "snes" },
    ): List<Long> {
        val folderId = db.scanDao().insertFolder(RomFolderEntity(treeUri = "content://t/tree/roms", displayName = "ROMs", addedAt = 0))
        db.scanDao().applyBatch(
            ScanBatch(
                folderId,
                systems = listOf(SystemEntity("snes", "Super Famicom (stored)"), SystemEntity("gba", "gba"), SystemEntity("md", "md")),
                newGames = List(count) { game(folderId, systemOf(it), "Game %05d".format(it)) },
            ),
        )
        return db.scanDao().gamesInFolder(folderId).sortedBy { it.title }.map { it.id }
    }

    /** A saved box front for every game whose position passes [has]. */
    protected fun boxFronts(
        ids: List<Long>,
        has: (Int) -> Boolean,
    ) {
        val sql = db.openHelper.writableDatabase
        ids.forEachIndexed { at, id ->
            if (has(at)) {
                sql.execSQL(
                    "INSERT INTO scraped_game (scrape_key, game_id, outcome, scraped_at, title, pictures, has_box_front) VALUES (?, ?, 'MATCHED', 1, 't', '', 1)",
                    arrayOf<Any>("game:$id", id),
                )
            }
        }
    }

    protected fun game(
        folderId: Long,
        system: String,
        title: String,
    ) = GameEntity(
        folderId = folderId,
        documentId = "root/$system/$title",
        parentDocumentId = "root/$system",
        relativePath = "$system/$title",
        fileName = title,
        sizeBytes = 1,
        modifiedAt = 1,
        title = title,
        sortName = title.lowercase(),
        regions = "",
        discGroup = null,
        discCount = 1,
        systemId = system,
        isPrimaryCopy = true,
    )


    protected fun at(
        day: String,
        zoneId: String = "UTC",
    ): Long = java.time.ZonedDateTime.parse("${day}[$zoneId]").toInstant().toEpochMilli()

    protected companion object {
        const val DAY = 86_400_000L
        const val HOUR = 3_600_000L
    }
}
