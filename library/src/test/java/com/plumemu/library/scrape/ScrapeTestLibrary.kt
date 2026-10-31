package com.plumemu.library.scrape

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.NameTrustNames
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.PlumMigrations
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScanBatch
import com.plumemu.coredata.db.ScanFinish
import com.plumemu.coredata.db.SystemEntity
import com.plumemu.library.scan.DocumentTree
import com.plumemu.scraper.GameDetails
import com.plumemu.scraper.GameInfo
import com.plumemu.scraper.GameMatch
import com.plumemu.scraper.GameTag
import com.plumemu.scraper.MatchedBy
import com.plumemu.scraper.MediaKind
import com.plumemu.scraper.MediaRef
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.TextByLanguage
import com.plumemu.scraper.TextByRegion
import com.plumemu.scraper.media.SavedMedia
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * A library database in a file (so a test can close and reopen it), games inserted as a named
 * scan leaves them, and a [LibraryScrapeSource] over it with a settable clock and system.
 */
internal class ScrapeTestLibrary(private val file: File, private val tree: DocumentTree? = null) {
    var db: PlumDatabase = open()
        private set
    var now = NOW
    var currentSystem: String? = null
    var preferred = "us"
    val folderId: Long = runBlocking {
        db.scanDao().insertFolder(RomFolderEntity(treeUri = TREE_URI, displayName = "ROMs", addedAt = 0))
    }

    fun source(): LibraryScrapeSource = LibraryScrapeSource(
        database = db,
        openTree = { tree ?: error("no tree in this test") },
        currentSystemId = { currentSystem },
        preferredRegion = { preferred },
        deviceLanguage = { "fr" },
        clock = { now },
    )

    private fun open(): PlumDatabase =
        Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java, file.absolutePath)
            .addMigrations(*PlumMigrations.ALL)
            .build()

    /** Closes the database and opens the same file again, like a new process. */
    fun reopen() {
        db.close()
        db = open()
    }

    fun close() = db.close()

    /** Inserts named games in one batch; returns their ids in order. */
    suspend fun add(vararg games: GameEntity): List<Long> {
        db.scanDao().applyBatch(ScanBatch(folderId, SYSTEMS, games.toList()))
        db.scanDao().finishScan(ScanFinish(folderId))
        val byDoc = db.scanDao().gamesInFolder(folderId).associateBy { it.documentId }
        return games.map { byDoc.getValue(it.documentId).id }
    }

    fun game(
        doc: String,
        fixKeys: String = "crc:${doc.hashCode().toUInt().toString(16)}:1024",
        title: String = doc.substringBeforeLast('.'),
        system: String = "snes",
        regions: String = "USA",
        trust: String = NameTrustNames.FILE_NAME,
        fingerprint: String? = null,
        favorite: Boolean = false,
    ) = GameEntity(
        folderId = folderId,
        documentId = doc,
        parentDocumentId = "root",
        relativePath = doc,
        fileName = doc,
        sizeBytes = 1024,
        modifiedAt = 7,
        title = title,
        sortName = title.lowercase(),
        regions = regions,
        discGroup = null,
        discCount = 1,
        systemId = system,
        isPrimaryCopy = true,
        isFavorite = favorite,
        nameTrust = trust,
        fingerprint = fingerprint,
        fixKeys = fixKeys,
        needsIdentify = false,
    )

    fun sql(statement: String, vararg args: Any) = db.openHelper.writableDatabase.execSQL(statement, args)

    fun played(gameId: Long, at: Long) = sql("INSERT INTO play (game_id, last_played_at, total_seconds) VALUES (?, ?, 60)", gameId, at)

    companion object {
        const val TREE_URI = "content://test/tree/roms"
        const val NOW = 1_800_000_000_000L
        const val DAY = 24L * 60 * 60 * 1000
        val SYSTEMS = listOf(
            SystemEntity("snes", "Super Nintendo"), SystemEntity("gba", "Game Boy Advance"), SystemEntity("psx", "PlayStation"),
            // Folders that hold another system's cartridges (scrape rules 3, `ScrapeRulesRepairTest`).
            SystemEntity("sgb", "Super Game Boy"), SystemEntity("snesmsu1", "SNES MSU-1"), SystemEntity("genesismsu", "Genesis MSU"), SystemEntity("megadrive", "Mega Drive"),
        )

        fun matched(title: String? = "Zork Quest", by: MatchedBy = MatchedBy.Hash, withSupport: Boolean = true) = ScrapeResult.Matched(
            match = GameMatch("1245", by),
            info = GameInfo(title, "A quest.", 1991, "Adventure", "Zork Dev", "Zork Soft", "1-2", 16),
            media = listOfNotNull(
                MediaRef(MediaKind.BoxFront, "us", "https://neoclone.screenscraper.fr/api2/mediaJeu.php?media=box-2D(us)"),
                MediaRef(MediaKind.Support, "us", "https://neoclone.screenscraper.fr/api2/mediaJeu.php?media=support-2D(us)").takeIf { withSupport },
                MediaRef(MediaKind.Screenshot, "wor", "https://neoclone.screenscraper.fr/api2/mediaJeu.php?media=ss"),
            ),
            details = GameDetails(
                names = listOf(TextByRegion("us", "Zork Quest"), TextByRegion("jp", "Zoku Kuesuto")),
                synopses = listOf(TextByLanguage("en", "A quest."), TextByLanguage("fr", "Une qu\u00eate.")),
                genres = listOf(
                    GameTag(id = "1", names = listOf(TextByLanguage("en", "Action"))),
                    GameTag(id = "2", main = true, names = listOf(TextByLanguage("fr", "Aventure"), TextByLanguage("en", "Adventure"))),
                ),
            ),
        )

        fun boxFront(size: String = "grid") = SavedMedia(MediaKind.BoxFront, size, "/media/1245/box-front-$size-240x336.webp", 234, 320)

        fun support(size: String = "grid") = SavedMedia(MediaKind.Support, size, "/media/1245/support-$size-240x336.webp", 240, 236)
    }
}
