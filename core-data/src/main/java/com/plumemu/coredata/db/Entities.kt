package com.plumemu.coredata.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A system that has games (or an emulator choice). `name` is filled from the platform data. */
@Entity(tableName = "system")
data class SystemEntity(
    @PrimaryKey val id: String,
    val name: String,
)

/** A folder the person picked in Android's folder picker; Plumemu holds a lasting read grant on it. */
@Entity(tableName = "rom_folder", indices = [Index(value = ["tree_uri"], unique = true)])
data class RomFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "tree_uri") val treeUri: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "last_scan_finished_at") val lastScanFinishedAt: Long? = null,
)

/**
 * A directory inside a picked folder as it was at the last scan. A rescan asks the provider for the
 * directory's modified time and lists the directory again only when it differs. 0 means "list it
 * next time" (the provider gave no time, or the time was too close to the scan to trust).
 *
 * There is no child count: the provider reports one only by listing the directory, which is the
 * query a rescan exists to avoid.
 */
@Entity(
    tableName = "scanned_directory",
    primaryKeys = ["folder_id", "document_id"],
    foreignKeys = [
        ForeignKey(
            entity = RomFolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folder_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ScannedDirectoryEntity(
    @ColumnInfo(name = "folder_id") val folderId: Long,
    @ColumnInfo(name = "document_id") val documentId: String,
    /** Path below the picked folder, `""` for the folder itself, `snes/hacks` below it. */
    @ColumnInfo(name = "relative_path") val relativePath: String,
    @ColumnInfo(name = "last_modified") val lastModified: Long,
    /**
     * Schema 9: files directly in this directory that some system's extension claims (junk such as
     * `._*` left out), counted only when no folder name on the path names a system (and the person
     * chose none): what Settings › Library lists as "Unknown folder". 0 for every other directory.
     */
    @ColumnInfo(name = "unknown_files", defaultValue = "0") val unknownFiles: Int = 0,
)

/**
 * Schema 9: the person's answer for a folder whose name matches no system (Settings › Library ›
 * Unknown folder). [relativePath] is below the picked folder [folderId] (`Xbox 360`); [systemId]
 * is the system its files are scanned as, null for "Ignore this folder" (scanned as before, by
 * extension alone, and no longer listed). Goes with the picked folder (CASCADE).
 */
@Entity(
    tableName = "folder_system",
    primaryKeys = ["folder_id", "relative_path"],
    foreignKeys = [
        ForeignKey(
            entity = RomFolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folder_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class FolderSystemEntity(
    @ColumnInfo(name = "folder_id") val folderId: Long,
    @ColumnInfo(name = "relative_path") val relativePath: String,
    @ColumnInfo(name = "system_id") val systemId: String?,
    /** Epoch milliseconds of the choice. */
    @ColumnInfo(name = "chosen_at") val chosenAt: Long,
)

/**
 * Schema 13 (Skipped sub-folders): a folder inside picked folder [folderId] that the
 * person chose to skip in Settings › ROM folders. The walk does not read it or anything below it, so
 * its games leave the library like games whose files are gone; nothing on disk changes.
 * [relativePath] is below the picked folder, `/`-separated, without a leading or trailing slash, in
 * the case the folder had when it was picked; the walk compares it ignoring case
 * ([SkippedSubfolderDao.normalized]). Goes with the picked folder (CASCADE).
 */
@Entity(
    tableName = "skipped_subfolder",
    primaryKeys = ["folder_id", "relative_path"],
    foreignKeys = [
        ForeignKey(
            entity = RomFolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folder_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SkippedSubfolderEntity(
    @ColumnInfo(name = "folder_id") val folderId: Long,
    @ColumnInfo(name = "relative_path") val relativePath: String,
    /** Epoch milliseconds of the skip. */
    @ColumnInfo(name = "skipped_at") val skippedAt: Long,
)

/**
 * Schema 10: how many different days a game's box-art lookup ended on a server error or a timeout
 * ([ScrapedGameDao.recordFailure]). At [ScrapedGameDao.MAX_FAILED_DAYS] the game is no longer
 * offered by [ScrapedGameDao.pending] until Re-download all clears the row
 * ([ScrapedGameDao.forgetFailures]) or a result is stored for it. Keyed like [ScrapedGameEntity]
 * (the game's first fix key, `game:<id>` without one), so it needs no game row and survives renames.
 * A `game:<id>` key outlives its game here; the leftover row is a few bytes and is dropped by the sweep.
 */
@Entity(tableName = "scrape_failure")
data class ScrapeFailureEntity(
    @PrimaryKey @ColumnInfo(name = "scrape_key") val scrapeKey: String,
    /** Days with at least one failed lookup, counted once per [lastDay]. */
    @ColumnInfo(name = "failed_days") val failedDays: Int,
    /** The last day counted: epoch milliseconds divided by a day (UTC; only "a different day" matters). */
    @ColumnInfo(name = "last_day") val lastDay: Long,
)

/**
 * One game as shown in the library: a playable file after junk, BIOS and extra discs were set
 * aside. Scan columns are rewritten by rescans; `is_hidden`, `is_favorite` and `hash` belong to
 * the person or to later steps and are never touched by a rescan.
 *
 * Names (schema v2): `title`, `sort_name` and `regions` are what the lists show and sort
 * by; `file_title`, `file_sort_name` and `file_regions` are what the file name says, written by
 * every scan and compared by rescans and the rename matching. They are equal until the
 * identification pass (library `IdentifyPass`) or a person's fix ([NameFixEntity]) gives a better
 * name; `name_trust` says whose name is shown (a `com.plumemu.identify.NameTrust` name, see
 * [NameTrustNames]). A scan rewrites the shown name only while its trust is `FILE_NAME`.
 * `fingerprint` caches what the identifier read, valid while the file still has
 * `fingerprint_size` and `fingerprint_mtime`; a rescan of an unchanged file reads nothing.
 * `needs_identify` is set by the scan when a row is new or its file changed, cleared by the pass.
 *
 * `is_pending` marks a row inserted by a scan that has not finished: the scan may have been the
 * one that found a moved or renamed file, and the old row it should be matched with is deleted
 * only at the end of a scan. Every later scan that sees a pending row again offers it to the rename
 * matching; [ScanDao.finishScan] clears the mark.
 */
@Entity(
    tableName = "game",
    foreignKeys = [
        ForeignKey(
            entity = RomFolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folder_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SystemEntity::class,
            parentColumns = ["id"],
            childColumns = ["system_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["system_id", "sort_name"]),
        Index(value = ["folder_id", "document_id"], unique = true),
        Index(value = ["folder_id", "parent_document_id"]),
        Index(value = ["folder_id", "needs_identify"]),
    ],
)
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "folder_id") val folderId: Long,
    /** The file's document id; with the folder's tree URI it gives the document URI. */
    @ColumnInfo(name = "document_id") val documentId: String,
    @ColumnInfo(name = "parent_document_id") val parentDocumentId: String,
    @ColumnInfo(name = "relative_path") val relativePath: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "modified_at") val modifiedAt: Long,
    val title: String,
    @ColumnInfo(name = "sort_name") val sortName: String,
    /** Region codes in file order, comma-separated (`USA,EUR`); empty when the name has none. */
    val regions: String,
    /** Shared name of a multi-disc game's discs; null for single-file games. */
    @ColumnInfo(name = "disc_group") val discGroup: String?,
    @ColumnInfo(name = "disc_count") val discCount: Int,
    @ColumnInfo(name = "system_id") val systemId: String,
    /** False for a regional copy that is grouped under another copy in the preferred region. */
    @ColumnInfo(name = "is_primary_copy") val isPrimaryCopy: Boolean,
    @ColumnInfo(name = "is_hidden") val isHidden: Boolean = false,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    val hash: String? = null,
    @ColumnInfo(name = "is_pending", defaultValue = "0") val isPending: Boolean = false,
    /** The title the file name gives ([title] until something better is known). */
    @ColumnInfo(name = "file_title", defaultValue = "''") val fileTitle: String = title,
    @ColumnInfo(name = "file_sort_name", defaultValue = "''") val fileSortName: String = sortName,
    /** Region codes the file name carries, as [regions] was in schema v1. */
    @ColumnInfo(name = "file_regions", defaultValue = "''") val fileRegions: String = regions,
    /** Whose name [title] is: a `NameTrust` name ([NameTrustNames]). */
    @ColumnInfo(name = "name_trust", defaultValue = "'FILE_NAME'") val nameTrust: String = NameTrustNames.FILE_NAME,
    /** The identifier's `Method` name of the last identification; null before the first one. */
    @ColumnInfo(name = "id_method") val idMethod: String? = null,
    /** The identifier's `Confidence` name of the last identification; null before the first one. */
    @ColumnInfo(name = "id_confidence") val idConfidence: String? = null,
    /** The fingerprints the identifier read, encoded by library's `FingerprintCodec`; null when none were read. */
    val fingerprint: String? = null,
    /** File size and modified time [fingerprint] was read at; null when the file was never read. */
    @ColumnInfo(name = "fingerprint_size") val fingerprintSize: Long? = null,
    @ColumnInfo(name = "fingerprint_mtime") val fingerprintMtime: Long? = null,
    /**
     * Data version of the system's name pack this row was named with; null when the system had no
     * pack. A newer pack (an update, new DATs) sets `needs_identify` again for the system's rows.
     */
    @ColumnInfo(name = "pack_version") val packVersion: Long? = null,
    /** Keys a person's name fix is stored under ([NameFixEntity]), strongest first, space-separated. */
    @ColumnInfo(name = "fix_keys", defaultValue = "''") val fixKeys: String = "",
    /** An arcade BIOS or device set, or a person's "Not a game": kept, never listed. */
    @ColumnInfo(name = "is_not_game", defaultValue = "0") val isNotGame: Boolean = false,
    /** The identification pass still has to name this row (new, or its file changed). */
    @ColumnInfo(name = "needs_identify", defaultValue = "1") val needsIdentify: Boolean = true,
)

/** The `NameTrust` names stored in `game.name_trust` that SQL in this module compares with. */
object NameTrustNames {
    const val FILE_NAME = "FILE_NAME"
    const val IDENTIFIER_EXACT = "IDENTIFIER_EXACT"
    const val PERSON_FIX = "PERSON_FIX"
    const val SCRAPER_PERSON_CHOICE = "SCRAPER_PERSON_CHOICE"
}

/** What a person's name fix says (the Fix name screens). */
enum class NameFixKind {
    /** A name the person typed ("Use my own name"). */
    OWN_NAME,

    /** A title the person chose from the system's name pack ("Fix name..."). */
    CHOSEN_TITLE,

    /** "Not a game": the file is kept but never listed. */
    NOT_A_GAME,
}

/**
 * A person's name fix, stored once per key of the game (`crc:...`, `code:...`, `chd:...`,
 * `arcade:...`, from `Identification.fixKeys`), so it survives renames, moves and rescans: a
 * game is looked up by any of its keys. A file without any key falls back to `game:<id>`; only
 * that row has [gameId], so it goes with the game (CASCADE) and a reused id never inherits it.
 * Content keys outlive the game: the same file put back later gets its fix again.
 */
@Entity(
    tableName = "name_fix",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["game_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["game_id"])],
)
data class NameFixEntity(
    @PrimaryKey @ColumnInfo(name = "fix_key") val fixKey: String,
    val kind: NameFixKind,
    /** The name shown: typed or chosen; null for [NameFixKind.NOT_A_GAME]. */
    val title: String?,
    /** Where a chosen title came from (the pack's source name); null otherwise. */
    @ColumnInfo(name = "title_source") val titleSource: String?,
    /** Set only on the `game:<id>` fallback key. */
    @ColumnInfo(name = "game_id") val gameId: Long?,
    @ColumnInfo(name = "fixed_at") val fixedAt: Long,
) {
    companion object {
        /** The key of a game whose file gave no fingerprint key. */
        fun rowKey(gameId: Long): String = "$ROW_PREFIX$gameId"

        const val ROW_PREFIX = "game:"
    }
}

/** A downsized image or other media for a game (box art). */
@Entity(
    tableName = "media",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["game_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["game_id"])],
)
data class MediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "game_id") val gameId: Long,
    val kind: String,
    val path: String,
)

/** Play history of a game: when it was last played and for how long in total. */
@Entity(
    tableName = "play",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["game_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["last_played_at"])],
)
data class PlayEntity(
    @PrimaryKey @ColumnInfo(name = "game_id") val gameId: Long,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long,
    @ColumnInfo(name = "total_seconds") val totalSeconds: Long,
    /**
     * The person took the game out of Recent (game menu's Remove from Recent, or Settings' Clear
     * Recent). Only Recent reads it; play time, sessions and statistics ignore it. The
     * next recorded play sets it back to false (`PlayDao.recordSession`). A flag, not a time
     * compared with [lastPlayedAt], so a clock set back between plays cannot keep a game out.
     */
    @ColumnInfo(name = "recent_removed", defaultValue = "0") val recentRemoved: Boolean = false,
)

/**
 * One play session, written with the total in `PlayDao.addSession` (Settings ›
 * Statistics' "this week"). Times are wall-clock milliseconds; `started_at` is `ended_at` minus
 * the measured elapsed time in milliseconds, and `seconds` is that time's whole seconds (rounded
 * down), so the span may be up to a second longer than `seconds`. `system_id` is a copy of the game's at
 * the time (no foreign key): it is what the session was played as. Rows go with their game
 * (CASCADE, like `play`); a rescan that keeps another row moves them first (`ScanDao`). Kept 400
 * days (`PlayDao.SESSION_RETENTION_MS`); totals in `play` never depend on them. Sessions exist
 * only from schema 5 on: nothing older is back-filled.
 *
 * Battery use of the play (schema 8), all null
 * when nothing was measured (the in-game card was not up, Show battery off, a device that reports
 * no current, a session from before schema 8): `battery_seconds` is the time measured while
 * discharging (reads more than two minutes apart and charging do not count), `charge_start_uah` and
 * `charge_end_uah` the charge counter at the play's first and last read in microampere-hours (null
 * on a device without a counter), `battery_draw_ua` the mean draw over `battery_seconds` in
 * microamperes, positive, already corrected for the device's sign and unit and by the counter when
 * it disagreed. A game's usual pace is read from these (`PlayDao.usualDraw`).
 */
@Entity(
    tableName = "play_session",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["game_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["ended_at"]), Index(value = ["game_id"])],
)
data class PlaySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "game_id") val gameId: Long,
    @ColumnInfo(name = "system_id") val systemId: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long,
    val seconds: Long,
    @ColumnInfo(name = "battery_seconds") val batterySeconds: Long? = null,
    @ColumnInfo(name = "charge_start_uah") val chargeStartMicroampHours: Long? = null,
    @ColumnInfo(name = "charge_end_uah") val chargeEndMicroampHours: Long? = null,
    @ColumnInfo(name = "battery_draw_ua") val batteryDrawMicroamps: Long? = null,
)

/**
 * What one play measured of the battery, stored with its `play_session` row (see
 * [PlaySessionEntity]): [measuredSeconds] discharging, the charge counter at the first and last
 * read (null without a counter), the mean draw in microamperes (positive).
 */
data class SessionBattery(
    val measuredSeconds: Long,
    val chargeStartMicroampHours: Long?,
    val chargeEndMicroampHours: Long?,
    val drawMicroamps: Long,
)

/**
 * The emulator for a system (`game_id` null) or for one game, by emulator id: RetroArch cores share
 * one package, so a package could not say which core the person chose. `is_person_choice` marks a
 * choice the person made; automatic defaults never replace it.
 */
@Entity(
    tableName = "emulator_choice",
    foreignKeys = [
        ForeignKey(
            entity = SystemEntity::class,
            parentColumns = ["id"],
            childColumns = ["system_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["game_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["system_id", "game_id"], unique = true), Index(value = ["game_id"])],
)
data class EmulatorChoiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "system_id") val systemId: String,
    @ColumnInfo(name = "game_id") val gameId: Long?,
    /** The emulator's id in the platform data (RetroArch has one per core), not its package. */
    @ColumnInfo(name = "emulator_id") val emulatorId: String,
    @ColumnInfo(name = "is_person_choice") val isPersonChoice: Boolean,
)

/**
 * A shortcut another app pinned to Plumemu ("Add to home screen" while Plumemu is the home app, schema 6). Android keeps the pin for the launcher that accepted it, but only
 * answers while that launcher is the home app, so Plumemu keeps this record of its own: the game
 * stays listed when another home app is chosen. The game itself is an ordinary `game` row (its
 * system, title, favourite, hidden mark, play time), filed in the reserved folder
 * [PinnedShortcutDao.FOLDER_TREE_URI], which no scan, identification or box-art run reads.
 *
 * One record per shortcut and Android user (the unique index): pinning the same shortcut again
 * finds the existing game. The record goes with its game (CASCADE), as `play` does.
 * [packageName], [shortcutId] and [userSerial] are what `LauncherApps.startShortcut` needs (step
 * 2b); [isAvailable] is false while the app has disabled or removed the shortcut.
 */
@Entity(
    tableName = "pinned_shortcut",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["game_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["package_name", "shortcut_id", "user_serial"], unique = true)],
)
data class PinnedShortcutEntity(
    @PrimaryKey @ColumnInfo(name = "game_id") val gameId: Long,
    /** The app that published the shortcut (`ShortcutInfo.getPackage`). */
    @ColumnInfo(name = "package_name") val packageName: String,
    /** The publisher's id of the shortcut (GameNative `game_<id>`, DroidDeck `game:<id>`). */
    @ColumnInfo(name = "shortcut_id") val shortcutId: String,
    /** `UserManager.getSerialNumberForUser` of the shortcut's user (0 for the device's primary user). */
    @ColumnInfo(name = "user_serial") val userSerial: Long,
    /** The short label the app gave when it was pinned; the shown name is `game.title`. */
    val label: String,
    /** Epoch milliseconds of the accept. */
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "is_available", defaultValue = "1") val isAvailable: Boolean = true,
)

/**
 * An installed Android app listed as a game (schema 11): an Android game in the system
 * `android`, or a native port app in `ports`. The game itself is an ordinary `game` row (title,
 * system, favourite, hidden mark, play time) filed in the reserved folder
 * [AppGameDao.FOLDER_TREE_URI], which no scan, identification or box-art run reads.
 * [AppGameDao.sync] adds and removes these rows from the list of installed apps the app layer
 * marked as games or ports. One record per package (the unique index); it goes with its game
 * (CASCADE), as `play` does.
 */
@Entity(
    tableName = "app_game",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["game_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["package_name"], unique = true)],
)
data class AppGameEntity(
    @PrimaryKey @ColumnInfo(name = "game_id") val gameId: Long,
    /** The app's package; what the app layer launches. */
    @ColumnInfo(name = "package_name") val packageName: String,
    /** Epoch milliseconds of the sync that first listed it. */
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

/**
 * The person's own answer for one installed app (schema 11): "Show as game", "Show as
 * port" or "Not a game" ([AppKind]); it always wins over Android's game flag and the known-package
 * list. Not tied to a game row: it is kept while the app is uninstalled, and Reset library keeps it
 * (it is a setting, not something a scan found). [kind] is [AppKind.stored]; a value this build does
 * not know reads as no choice ([AppGameDao.choices]).
 */
@Entity(tableName = "app_kind_choice")
data class AppKindChoiceEntity(
    @PrimaryKey @ColumnInfo(name = "package_name") val packageName: String,
    val kind: String,
    /** Epoch milliseconds of the choice. */
    @ColumnInfo(name = "chosen_at") val chosenAt: Long,
)

/**
 * The game menu's Other versions (schema 7): the person chose to play the copy [gameId] (a
 * regional copy or an arcade set of the game a row stands for) instead of the row's own file.
 * Keyed by the CHOSEN copy's row; the row the person launches from finds it among its copies
 * (library's `copyToPlay`), and the latest [chosenAt] among them wins. The copy's row going (its
 * file removed) takes the choice with it, and the row plays its own file again.
 */
@Entity(
    tableName = "copy_choice",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["game_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CopyChoiceEntity(
    @PrimaryKey @ColumnInfo(name = "game_id") val gameId: Long,
    /** Epoch milliseconds of the choice. */
    @ColumnInfo(name = "chosen_at") val chosenAt: Long,
)
