package com.plumemu.coredata.db

import androidx.room.ColumnInfo
import androidx.room.Embedded

/** The last game played in a list (the second screen's "Last played <title> · yesterday"). */
data class LastPlay(
    val title: String,
    /** Epoch milliseconds. */
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long,
)

/**
 * A game in a group of copies holding a played game (Settings › Statistics, `PlayDao.playedCopies`):
 * where it is and what the regional grouping reads, whether the lists show it, and its play total.
 */
data class PlayedCopy(
    val id: Long,
    val title: String,
    @ColumnInfo(name = "sort_name") val sortName: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "folder_id") val folderId: Long,
    @ColumnInfo(name = "parent_document_id") val parentDocumentId: String,
    @ColumnInfo(name = "system_id") val systemId: String,
    @ColumnInfo(name = "file_sort_name") val fileSortName: String,
    @ColumnInfo(name = "is_primary_copy") val isPrimaryCopy: Boolean,
    @ColumnInfo(name = "is_hidden") val isHidden: Boolean,
    @ColumnInfo(name = "is_not_game") val isNotGame: Boolean,
    /** 0 for a copy never played. */
    @ColumnInfo(name = "total_seconds") val totalSeconds: Long,
    /** The system's stored name, for when the platform data has none. */
    @ColumnInfo(name = "system_name") val systemName: String,
    /** The key its ScreenScraper result is stored under ([ScrapedGameDao.SCRAPE_KEY]), for its cover. */
    @ColumnInfo(name = "scrape_key") val scrapeKey: String,
    /** Path below the picked folder, and what else the grouping of copies kept one game per folder reads (library `gameFolderCopyGroup`). */
    @ColumnInfo(name = "relative_path") val relativePath: String,
    @ColumnInfo(name = "disc_count") val discCount: Int,
    /** Rows in its directory, itself included, counted up to 2: 1 for a game folder's only row. */
    @ColumnInfo(name = "rows_beside") val rowsBeside: Int,
)

/**
 * One row of a game list: only what a list row draws. Immutable so a list screen can skip rows
 * that did not change. Kept small on purpose; a system can hold 10,000 of these.
 */
data class GameListRow(
    val id: Long,
    val title: String,
    /** Region codes, comma-separated (`USA,EUR`), shown in place of the year without box art. */
    val regions: String,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
    /** The game's system: Recent, Favorites and Search show its short name beside the title. */
    @ColumnInfo(name = "system_id") val systemId: String,
    /**
     * The key its ScreenScraper result is stored under ([ScrapedGameDao.SCRAPE_KEY]): the home
     * screen observes `scraped_game` for the rows it shows by this key. Computed from `game` alone,
     * so a stored result does not re-run the list queries.
     */
    @ColumnInfo(name = "scrape_key") val scrapeKey: String,
    /** The key the lists are ordered by (identify's `TitleCleaner.sortName`): the home screen's letter rail files a game under its first letter. */
    @ColumnInfo(name = "sort_name") val sortName: String,
    /** The file's name: the variant tags (Rev 1, T+Eng) that tell two rows of one title apart are read from it for display; never shown whole. */
    @ColumnInfo(name = "file_name") val fileName: String = "",
)

/** A hidden game as Settings › Library › Hidden games lists it: the title and its system. */
data class HiddenGameRow(
    val id: Long,
    val title: String,
    /** Region codes, comma-separated, as in [GameListRow]. */
    val regions: String,
    @ColumnInfo(name = "system_id") val systemId: String,
)

/** One picked folder's games and systems (folders with no games have no row). */
data class FolderGameCount(
    @ColumnInfo(name = "folder_id") val folderId: Long,
    @ColumnInfo(name = "game_count") val gameCount: Int,
    @ColumnInfo(name = "system_count") val systemCount: Int,
)

/** A system with the number of games it shows, for the strip of systems. */
data class SystemGameCount(
    val id: String,
    val name: String,
    @ColumnInfo(name = "game_count") val gameCount: Int,
)

/**
 * The columns a scan writes, compared with what a rescan finds to skip unchanged rows. [title],
 * [sortName] and [regions] are what the file name gives (`file_title`, `file_sort_name`,
 * `file_regions`), not the shown name, which identification may have improved.
 */
data class GameScanFields(
    @ColumnInfo(name = "document_id") val documentId: String,
    @ColumnInfo(name = "parent_document_id") val parentDocumentId: String,
    @ColumnInfo(name = "relative_path") val relativePath: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "modified_at") val modifiedAt: Long,
    @ColumnInfo(name = "file_title") val title: String,
    @ColumnInfo(name = "file_sort_name") val sortName: String,
    @ColumnInfo(name = "file_regions") val regions: String,
    @ColumnInfo(name = "disc_group") val discGroup: String?,
    @ColumnInfo(name = "disc_count") val discCount: Int,
    @ColumnInfo(name = "system_id") val systemId: String,
    @ColumnInfo(name = "is_primary_copy") val isPrimaryCopy: Boolean,
)

/**
 * A row of a directory that may be one game's folder, as library's grouping of copies across
 * sibling game folders (`GameFolderCopies`) compares them: the scan's names, not the shown ones.
 */
data class GameFolderRow(
    val id: Long,
    @ColumnInfo(name = "parent_document_id") val parentDocumentId: String,
    @ColumnInfo(name = "relative_path") val relativePath: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "file_sort_name") val fileSortName: String,
    @ColumnInfo(name = "file_regions") val fileRegions: String,
    @ColumnInfo(name = "system_id") val systemId: String,
    @ColumnInfo(name = "is_not_game") val isNotGame: Boolean,
    @ColumnInfo(name = "is_primary_copy") val isPrimaryCopy: Boolean,
    @ColumnInfo(name = "disc_count") val discCount: Int,
)

/** A game already in the database, as a rescan sees it. */
data class StoredGame(
    val id: Long,
    @Embedded val fields: GameScanFields,
)

/** A game as launching sees it: the document URI is built from [treeUri] and [documentId]. */
data class GameLaunchTarget(
    val id: Long,
    @ColumnInfo(name = "system_id") val systemId: String,
    @ColumnInfo(name = "document_id") val documentId: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "tree_uri") val treeUri: String,
)

/**
 * Which rows library's content pass reads whole ([IdentifyDao.contentReadPage]). This module does
 * not know the layout of `game.fingerprint` (library's `FingerprintCodec`), so the caller passes
 * the substrings that tell a row apart: the pass stays correct when the codec's layout changes,
 * as long as it passes its own marks.
 */
data class ContentReadFilter(
    /** Systems whose loose files a whole read gives a content CRC. */
    val looseSystems: Collection<String>,
    /** Systems whose zip entries may hide a header the directory CRC includes. */
    val zippedSystems: Collection<String>,
    /** Largest file read, in bytes. */
    val maxBytes: Long,
    /** In the fingerprint of a loose file. */
    val looseMark: String,
    /** In the fingerprint of a zip. */
    val zipMark: String,
    /** In a fingerprint holding a loose file's whole-file CRC (a read after a fix): not read again. */
    val wholeFileCrcMark: String,
    /** In a fingerprint holding a zip entry's header-less CRC: not read again. */
    val zipContentCrcMark: String,
    /** In a fingerprint the pass is done with (read, or given up on): not read again. */
    val doneMark: String,
)

/** Where [IdentifyDao.contentReadPage] goes on from: the last row of the page before. */
data class ContentReadCursor(val group: Int = -1, val sizeBytes: Long = -1, val id: Long = -1)

/** A row the content pass reads whole: the file, its cached fingerprint, and its place in the order. */
data class GameToReadContent(
    @Embedded val game: GameToIdentify,
    @ColumnInfo(name = "folder_id") val folderId: Long,
    /** 0 played, 1 favourite, 2 the rest. */
    @ColumnInfo(name = "read_group") val readGroup: Int,
)

/** What the content pass re-reads inside its write transaction ([IdentifyDao.contentReadState]). */
data class ContentReadState(
    val id: Long,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "modified_at") val modifiedAt: Long,
    @ColumnInfo(name = "file_name") val fileName: String,
    val fingerprint: String?,
    @ColumnInfo(name = "needs_identify") val needsIdentify: Boolean,
    @ColumnInfo(name = "fix_keys") val fixKeys: String,
    val title: String,
)

/** A game the identification pass names: its file, and the fingerprints cached by the last pass. */
data class GameToIdentify(
    val id: Long,
    @ColumnInfo(name = "document_id") val documentId: String,
    @ColumnInfo(name = "parent_document_id") val parentDocumentId: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "modified_at") val modifiedAt: Long,
    @ColumnInfo(name = "system_id") val systemId: String,
    @ColumnInfo(name = "file_title") val fileTitle: String,
    @ColumnInfo(name = "file_regions") val fileRegions: String,
    val fingerprint: String?,
    @ColumnInfo(name = "fingerprint_size") val fingerprintSize: Long?,
    @ColumnInfo(name = "fingerprint_mtime") val fingerprintMtime: Long?,
)

/**
 * An arcade game that shows the name the identifier gave from its short name, and
 * whether it is the copy its group shows (library's `ArcadeCopies`, arcade versions step 2).
 */
data class ArcadeShownGame(
    val id: Long,
    @ColumnInfo(name = "system_id") val systemId: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    val title: String,
    @ColumnInfo(name = "is_primary_copy") val isPrimaryCopy: Boolean = true,
)

/** A shown row in the directory of a played arcade set (Settings › Statistics, `PlayDao.shownRowsIn`): its id and file name. */
data class PlayedSibling(
    val id: Long,
    @ColumnInfo(name = "file_name") val fileName: String,
)

/**
 * An arcade row as library's copy grouping reads it (`ArcadeCopies`, arcade versions step 2):
 * where it is, and the person's marks that make it the copy its group shows.
 */
data class ArcadeCopyRow(
    val id: Long,
    @ColumnInfo(name = "system_id") val systemId: String,
    @ColumnInfo(name = "parent_document_id") val parentDocumentId: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "name_trust") val nameTrust: String,
    @ColumnInfo(name = "is_hidden") val isHidden: Boolean,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
    @ColumnInfo(name = "is_primary_copy") val isPrimaryCopy: Boolean,
    @ColumnInfo(name = "total_seconds") val totalSeconds: Long,
    /** A ScreenScraper game the person picked for it (its title, or a pick stored under its key). */
    @ColumnInfo(name = "has_scraper_choice") val hasScraperChoice: Boolean = false,
    /** An emulator the person chose for this game. */
    @ColumnInfo(name = "has_emulator_choice") val hasEmulatorChoice: Boolean = false,
)

/** Whose name a game shows now, read inside the pass's write transaction. */
data class GameNameState(
    val id: Long,
    @ColumnInfo(name = "name_trust") val nameTrust: String,
    @ColumnInfo(name = "fix_keys") val fixKeys: String,
)

/** What the game's Info screen shows under "How it was identified". */
data class GameIdentificationRow(
    val id: Long,
    @ColumnInfo(name = "system_id") val systemId: String,
    val title: String,
    @ColumnInfo(name = "file_title") val fileTitle: String,
    @ColumnInfo(name = "name_trust") val nameTrust: String,
    @ColumnInfo(name = "id_method") val idMethod: String?,
    @ColumnInfo(name = "id_confidence") val idConfidence: String?,
    val fingerprint: String?,
    @ColumnInfo(name = "fix_keys") val fixKeys: String,
    @ColumnInfo(name = "is_not_game") val isNotGame: Boolean,
)

/** One system of a folder and a pack version its rows were named with (null: no pack, or not named yet). */
data class SystemPackVersion(
    @ColumnInfo(name = "system_id") val systemId: String,
    @ColumnInfo(name = "pack_version") val packVersion: Long?,
)

/**
 * A game the ScreenScraper run may look up ([ScrapedGameDao.pending]): where its file is, what
 * the identifier cached ([fingerprint], library's `FingerprintCodec`) and the key its result is
 * stored under ([scrapeKey], [ScrapedGameDao.SCRAPE_KEY]).
 */
data class GameToScrape(
    val id: Long,
    @ColumnInfo(name = "tree_uri") val treeUri: String,
    @ColumnInfo(name = "document_id") val documentId: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "modified_at") val modifiedAt: Long,
    @ColumnInfo(name = "system_id") val systemId: String,
    val title: String,
    /** Region codes of the shown name, comma-separated (`USA,EUR`), as `game.regions`. */
    val regions: String,
    val fingerprint: String?,
    @ColumnInfo(name = "scrape_key") val scrapeKey: String,
)

/** A game as [ScrapedGameDao.target] reads it before a result is written to it. */
data class ScrapeTarget(
    val id: Long,
    @ColumnInfo(name = "name_trust") val nameTrust: String,
    @ColumnInfo(name = "fix_keys") val fixKeys: String,
    @ColumnInfo(name = "needs_identify") val needsIdentify: Boolean,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "modified_at") val modifiedAt: Long,
)

/** A stored match's title and how it was found ([ScrapedGameDao.matchedTitles]). */
data class ScrapedTitle(
    @ColumnInfo(name = "scrape_key") val scrapeKey: String,
    val title: String,
    @ColumnInfo(name = "matched_by") val matchedBy: ScrapeMatchedBy?,
    /** The person picked this game ("Fix box art..."): its title names the game over every automatic name. */
    @ColumnInfo(name = "is_person_choice") val isPersonChoice: Boolean = false,
)

/**
 * One game sharing a stored hash match whose lookup sent a serial (`ScrapedGameDao.hashMatchesAskedWithSerial`):
 * the scrape-rules repair reads the answer's system out of [detailsJson] (scraper's
 * text; this module never parses it) and the serial's format out of [fixKeys]' `code:<kind>:` line.
 */
data class SerialHashMatch(
    @ColumnInfo(name = "scrape_key") val scrapeKey: String,
    /** The system of a game the row stands for (Plumemu's id). */
    @ColumnInfo(name = "system_id") val systemId: String,
    /** That game's `fix_keys`; no `code:` line means its lookup sent no serial. */
    @ColumnInfo(name = "fix_keys") val fixKeys: String,
    @ColumnInfo(name = "details_json") val detailsJson: String,
)

/**
 * One game sharing a stored hash match, for scrape rules 4 (`ScrapedGameDao.hashMatchGamesWithoutCode`):
 * as [SerialHashMatch] without the answer (read by key, [StoredDetails]), with the file name whose
 * extension tells the cartridge format.
 */
data class HashMatchGame(
    @ColumnInfo(name = "scrape_key") val scrapeKey: String,
    @ColumnInfo(name = "system_id") val systemId: String,
    @ColumnInfo(name = "fix_keys") val fixKeys: String,
    @ColumnInfo(name = "file_name") val fileName: String,
)

/** A stored answer by key (`ScrapedGameDao.detailsOf`). */
data class StoredDetails(
    @ColumnInfo(name = "scrape_key") val scrapeKey: String,
    @ColumnInfo(name = "details_json") val detailsJson: String,
)

/** A game with no stored file-name title (`ScrapedGameDao.gamesWithoutFileTitle`): what a repair needs to write one. */
data class GameFileName(
    val id: Long,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "system_id") val systemId: String,
    /** The shown title now. */
    val title: String,
)
