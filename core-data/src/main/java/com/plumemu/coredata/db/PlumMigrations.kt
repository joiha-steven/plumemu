package com.plumemu.coredata.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every schema migration, oldest first. Adding one: bump `PlumDatabase.version`, build (KSP
 * writes `schemas/<n>.json`), add the migration here and a test in `PlumMigrationTest` that
 * opens version n-1 filled with the person's data and migrates.
 */
object PlumMigrations {
    /**
     * Version 2 (game identification). Only adds: every v1 column, row and table stays
     * as it was, so favourites, hidden marks, play time and emulator choices are untouched.
     *
     * - `game` gets the file-name columns (`file_title`, `file_sort_name`, `file_regions`, copied
     *   from the v1 name, which was the file name's), the trust of the shown name (`FILE_NAME`),
     *   the identification columns (empty) and `needs_identify` = 1, so the next scan names every
     *   game once.
     * - `name_fix` is new and empty.
     *
     * Room runs a migration in one transaction, so a failure leaves version 1 whole. Each step
     * also checks what is already there, so running it on a half-migrated file (copied by hand,
     * restored from a backup) does not fail on a column that exists.
     */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val existing = columnsOf(db, "game")
            for ((name, definition) in GAME_COLUMNS_V2) {
                if (name !in existing) db.execSQL("ALTER TABLE `game` ADD COLUMN `$name` $definition")
            }
            // v1 rows were named from the file only; rows a v2 scan already wrote are left alone.
            db.execSQL(
                "UPDATE `game` SET `file_title` = `title`, `file_sort_name` = `sort_name`, `file_regions` = `regions` " +
                    "WHERE `file_title` = '' AND `file_sort_name` = '' AND `file_regions` = ''",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_game_folder_id_needs_identify` ON `game` (`folder_id`, `needs_identify`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `name_fix` (`fix_key` TEXT NOT NULL, `kind` TEXT NOT NULL, `title` TEXT, " +
                    "`title_source` TEXT, `game_id` INTEGER, `fixed_at` INTEGER NOT NULL, PRIMARY KEY(`fix_key`), " +
                    "FOREIGN KEY(`game_id`) REFERENCES `game`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_name_fix_game_id` ON `name_fix` (`game_id`)")
        }
    }

    /**
     * Version 3 (ScreenScraper results). Only adds the empty `scraped_game` table and
     * its index: no existing row or column changes, so every game, name, fix, favourite and play
     * time stays as it was. `IF NOT EXISTS` lets it run again on a file it already migrated.
     * Going back means a build with schema 3 or later: there is no 3 to 2: a downgrade needs a build that knows the newer schema.
     */
    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `scraped_game` (`scrape_key` TEXT NOT NULL, `game_id` INTEGER, " +
                    "`outcome` TEXT NOT NULL, `screenscraper_id` TEXT, `matched_by` TEXT, " +
                    "`is_person_choice` INTEGER NOT NULL DEFAULT 0, `scraped_at` INTEGER NOT NULL, `details_json` TEXT, " +
                    "`title` TEXT, `year` INTEGER, `publisher` TEXT, `players` TEXT, `genre` TEXT, " +
                    "`pictures` TEXT NOT NULL DEFAULT '', `has_box_front` INTEGER NOT NULL DEFAULT 0, " +
                    "`support_missing` INTEGER NOT NULL DEFAULT 0, `support_attempts` INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(`scrape_key`), " +
                    "FOREIGN KEY(`game_id`) REFERENCES `game`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_scraped_game_game_id` ON `scraped_game` (`game_id`)")
        }
    }

    /**
     * Version 4 (Settings' Download missing and Re-download). Only adds
     * `scraped_game.ask_again`, 0 on every stored row: each result stays as it was and is not
     * asked again until the person asks. Checks the column first, so it runs again on a file that
     * already has it. Going back means a build with schema 4 or later (no 4 to 3).
     */
    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if ("ask_again" !in columnsOf(db, "scraped_game")) {
                db.execSQL("ALTER TABLE `scraped_game` ADD COLUMN `ask_again` INTEGER NOT NULL DEFAULT 0")
            }
        }
    }

    /**
     * Version 5 (Settings › Statistics' "this week"). Only adds the empty
     * `play_session` table and its two indexes, as `schemas/.../5.json` has them: no existing row,
     * column or table changes, so every total, last play, favourite and name stays as it was.
     * Sessions are recorded from the update on; nothing older is made up from the totals.
     * `IF NOT EXISTS` lets it run again on a file it already migrated. Going back means a build
     * with schema 5 or later (no 5 to 4).
     */
    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `play_session` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`game_id` INTEGER NOT NULL, `system_id` TEXT NOT NULL, `started_at` INTEGER NOT NULL, " +
                    "`ended_at` INTEGER NOT NULL, `seconds` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`game_id`) REFERENCES `game`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_play_session_ended_at` ON `play_session` (`ended_at`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_play_session_game_id` ON `play_session` (`game_id`)")
        }
    }

    /**
     * Version 6 (pinned shortcuts). Only adds the empty `pinned_shortcut` table and
     * its unique index, as `schemas/.../6.json` has them: no existing row, column or table changes,
     * so every game, favourite, hidden mark, play total, session, choice, fix and box-art result
     * stays as it was. The reserved folder and the shortcut games are written later, at the first
     * accepted pin, never here. `IF NOT EXISTS` lets it run again on a file it already migrated.
     * Going back means a build with schema 6 or later (no 6 to 5).
     */
    val MIGRATION_5_6: Migration = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `pinned_shortcut` (`game_id` INTEGER NOT NULL, `package_name` TEXT NOT NULL, " +
                    "`shortcut_id` TEXT NOT NULL, `user_serial` INTEGER NOT NULL, `label` TEXT NOT NULL, " +
                    "`added_at` INTEGER NOT NULL, `is_available` INTEGER NOT NULL DEFAULT 1, PRIMARY KEY(`game_id`), " +
                    "FOREIGN KEY(`game_id`) REFERENCES `game`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_pinned_shortcut_package_name_shortcut_id_user_serial` " +
                    "ON `pinned_shortcut` (`package_name`, `shortcut_id`, `user_serial`)",
            )
        }
    }

    /**
     * Version 7 (the game menu's Other versions). Only adds the empty `copy_choice` table, as
     * `schemas/.../7.json` has it: no existing row, column or table changes, so every game,
     * favourite, hidden mark, play total, session, emulator choice, fix, box-art result and
     * shortcut stays as it was, and every row plays its own file as before until the person
     * chooses another copy. `IF NOT EXISTS` lets it run again on a file it already migrated.
     * Going back means a build with schema 7 or later (no 7 to 6).
     */
    val MIGRATION_6_7: Migration = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `copy_choice` (`game_id` INTEGER NOT NULL, `chosen_at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`game_id`), FOREIGN KEY(`game_id`) REFERENCES `game`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
        }
    }

    /**
     * Version 8 (battery use per play). Only adds four nullable columns to
     * `play_session` (`battery_seconds`, `charge_start_uah`, `charge_end_uah`, `battery_draw_ua`),
     * as `schemas/.../8.json` has them: every existing session keeps its values and reads as "not
     * measured" (null), and no other row, column or table changes. A column already there is left
     * alone, so it runs again on a file it already migrated. Going back means a build with schema 8
     * or later (no 8 to 7).
     */
    val MIGRATION_7_8: Migration = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val existing = columnsOf(db, "play_session")
            for (name in SESSION_BATTERY_COLUMNS_V8) {
                if (name !in existing) db.execSQL("ALTER TABLE `play_session` ADD COLUMN `$name` INTEGER")
            }
        }
    }

    /**
     * Version 9 (folders whose name matches no system, audit follow-up wave 2 item 2). Adds the
     * empty `folder_system` table (the person's system for such a folder) and the column
     * `scanned_directory.unknown_files` with 0 in every existing row, as `schemas/.../9.json` has
     * them. No existing value changes: every game, favourite, hidden mark, play total, session,
     * choice, fix, box-art result and directory time stays as it was. The new column fills in as
     * directories are listed again (library's `FOLDER_MATCH_VERSION` 7 lists every folder once).
     * A column or table already there is left alone, so it runs again on a file it already
     * migrated. Going back means a build with schema 9 or later (no 9 to 8).
     */
    val MIGRATION_8_9: Migration = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if ("unknown_files" !in columnsOf(db, "scanned_directory")) {
                db.execSQL("ALTER TABLE `scanned_directory` ADD COLUMN `unknown_files` INTEGER NOT NULL DEFAULT 0")
            }
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `folder_system` (`folder_id` INTEGER NOT NULL, `relative_path` TEXT NOT NULL, " +
                    "`system_id` TEXT, `chosen_at` INTEGER NOT NULL, PRIMARY KEY(`folder_id`, `relative_path`), " +
                    "FOREIGN KEY(`folder_id`) REFERENCES `rom_folder`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
        }
    }

    /**
     * Version 10 (box art's retry cap, audit item 21). Adds the empty `scrape_failure` table (days a
     * game's lookup failed on a server error or timeout). No existing table or value changes. A
     * table already there is left alone, so it runs again on a file it already migrated. Going back
     * means a build with schema 10 or later (no 10 to 9).
     */
    val MIGRATION_9_10: Migration = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `scrape_failure` (`scrape_key` TEXT NOT NULL, `failed_days` INTEGER NOT NULL, " +
                    "`last_day` INTEGER NOT NULL, PRIMARY KEY(`scrape_key`))",
            )
        }
    }

    /**
     * Version 11 (installed apps as games). Adds the empty `app_game` table (an installed
     * app listed as a game, its `game` row in the reserved folder `AppGameDao.FOLDER_TREE_URI`) and
     * the empty `app_kind_choice` table (the person's answer per package). No existing table or value
     * changes. Tables already there are left alone, so it runs again on a file it already migrated.
     * Going back means a build with schema 11 or later (no 11 to 10).
     */
    val MIGRATION_10_11: Migration = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `app_game` (`game_id` INTEGER NOT NULL, `package_name` TEXT NOT NULL, " +
                    "`added_at` INTEGER NOT NULL, PRIMARY KEY(`game_id`), " +
                    "FOREIGN KEY(`game_id`) REFERENCES `game`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_app_game_package_name` ON `app_game` (`package_name`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `app_kind_choice` (`package_name` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                    "`chosen_at` INTEGER NOT NULL, PRIMARY KEY(`package_name`))",
            )
        }
    }

    /**
     * Version 12 (Remove from Recent and Clear Recent). Adds `play.recent_removed`, 0 on
     * every row, so every played game stays in Recent as before; no other column or value changes.
     * The column is added only when missing, so it runs again on a file it already migrated. Going
     * back means a build with schema 12 or later (no 12 to 11).
     */
    val MIGRATION_11_12: Migration = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if ("recent_removed" !in columnsOf(db, "play")) {
                db.execSQL("ALTER TABLE `play` ADD COLUMN `recent_removed` INTEGER NOT NULL DEFAULT 0")
            }
        }
    }

    /**
     * Version 13 (Skipped sub-folders). Adds the empty `skipped_subfolder` table (the
     * sub-folders a person chose to skip in Settings › ROM folders). No existing table or value
     * changes, so every game, mark and play time stays. A table already there is left alone, so it
     * runs again on a file it already migrated. Going back means a build with schema 13 or later
     * (no 13 to 12).
     */
    val MIGRATION_12_13: Migration = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `skipped_subfolder` (`folder_id` INTEGER NOT NULL, `relative_path` TEXT NOT NULL, " +
                    "`skipped_at` INTEGER NOT NULL, PRIMARY KEY(`folder_id`, `relative_path`), " +
                    "FOREIGN KEY(`folder_id`) REFERENCES `rom_folder`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
        }
    }

    /**
     * Version 14 (RetroAchievements). Adds two empty tables: `ra_match` (each library game's
     * RetroAchievements game and the hash read to find it) and `ra_progress` (the signed-in
     * person's progress per RetroAchievements game), with `ra_match`'s index on `ra_game_id`. No
     * existing table or value changes, so every game, mark and play time stays. Tables and the index
     * already there are left alone, so it runs again on a file it already migrated. Going back means
     * a build with schema 14 or later (no 14 to 13).
     */
    val MIGRATION_13_14: Migration = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `ra_match` (`game_id` INTEGER NOT NULL, `status` TEXT NOT NULL, " +
                    "`ra_game_id` INTEGER, `md5` TEXT, `cannot_hash_reason` TEXT, `file_key` TEXT NOT NULL, " +
                    "`size_bytes` INTEGER NOT NULL, `modified_at` INTEGER NOT NULL, `checked_at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`game_id`), " +
                    "FOREIGN KEY(`game_id`) REFERENCES `game`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ra_match_ra_game_id` ON `ra_match` (`ra_game_id`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `ra_progress` (`ra_game_id` INTEGER NOT NULL, " +
                    "`achievements_total` INTEGER NOT NULL, `awarded_softcore` INTEGER NOT NULL, " +
                    "`awarded_hardcore` INTEGER NOT NULL, `highest_award` TEXT, `updated_at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`ra_game_id`))",
            )
        }
    }

    /**
     * Version 15 (Settings › Library › Files not added). Adds the empty `left_out_file` table and its
     * index. No existing table or value changes, so every game, mark and play time stays; the next
     * scan that lists a directory fills its rows (unchanged directories stay empty until then). Runs
     * again on a file it already migrated (IF NOT EXISTS). Going back means a build with schema 15 or
     * later (no 15 to 14).
     */
    val MIGRATION_14_15: Migration = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `left_out_file` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`folder_id` INTEGER NOT NULL, `directory_path` TEXT NOT NULL, `relative_path` TEXT, `reason` TEXT NOT NULL, " +
                    "`system_id` TEXT, `more` INTEGER NOT NULL DEFAULT 0, " +
                    "FOREIGN KEY(`folder_id`) REFERENCES `rom_folder`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_left_out_file_folder_id_directory_path` ON `left_out_file` (`folder_id`, `directory_path`)",
            )
            // The table starts empty and a rescan skips unchanged directories: the first scan after
            // the upgrade lists each directory once so its left-out files are stored. Unchanged
            // files keep their rows and names (updateScanFields only flags changed fields).
            db.execSQL("UPDATE scanned_directory SET last_modified = 0")
        }
    }

    /**
     * Version 16 (the Rating sort): `scraped_game` gets `rating`, back-filled from each row's
     * `details_json` with [rating] (SQLite on Android 6 has no JSON functions). Only adds: no
     * other value changes. A row without details, without a rating or whose details do not read
     * keeps null; one bad row never fails the migration. Pages of [RATING_PAGE] rows by key keep
     * each cursor window small (a row's details can be large). Runs again safely: the column is
     * added only when missing and only null ratings are read.
     */
    fun migration15To16(rating: DetailsRating): Migration = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if ("rating" !in columnsOf(db, "scraped_game")) db.execSQL("ALTER TABLE `scraped_game` ADD COLUMN `rating` INTEGER")
            backfillRatings(db, rating)
        }
    }

    /**
     * Version 17 (the second screen): `game.added_at`, `ra_progress.points_earned` and
     * `points_total`, all nullable and left null on existing rows (no time or points is known for
     * them; `modified_at` is the file's own time, not when it entered the library), and the empty
     * `ra_achievement` table. Only adds: every existing value stays. Runs again safely (each column
     * only when missing, the table IF NOT EXISTS). Going back means a build with schema 17 or later.
     */
    val MIGRATION_16_17: Migration = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if ("added_at" !in columnsOf(db, "game")) db.execSQL("ALTER TABLE `game` ADD COLUMN `added_at` INTEGER")
            val progress = columnsOf(db, "ra_progress")
            if ("points_earned" !in progress) db.execSQL("ALTER TABLE `ra_progress` ADD COLUMN `points_earned` INTEGER")
            if ("points_total" !in progress) db.execSQL("ALTER TABLE `ra_progress` ADD COLUMN `points_total` INTEGER")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `ra_achievement` (`ra_game_id` INTEGER NOT NULL, `achievement_id` INTEGER NOT NULL, " +
                    "`title` TEXT NOT NULL, `badge_name` TEXT, `points` INTEGER NOT NULL, `earned_at` INTEGER, " +
                    "`earned_hardcore_at` INTEGER, PRIMARY KEY(`ra_game_id`, `achievement_id`))",
            )
        }
    }

    /**
     * Version 18 (a grid packs at the stored shapes): `game.art_aspect`, nullable. Only adds a column: no row is
     * read or written, so the time does not grow with the library. Null on existing rows until [ArtAspects.sync] reads their
     * pictures. Runs again safely. Going back means a build with schema 18 or later.
     */
    val MIGRATION_17_18: Migration = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if ("art_aspect" !in columnsOf(db, "game")) db.execSQL("ALTER TABLE `game` ADD COLUMN `art_aspect` REAL")
        }
    }

    /** The highest rating kept (ScreenScraper's are out of 20, the scraper's `GameDetails.MAX_RATING`). */
    private const val MAX_RATING = 20

    private const val RATING_PAGE = 100

    private fun backfillRatings(db: SupportSQLiteDatabase, rating: DetailsRating) {
        var after = ""
        while (true) {
            val page = db.query(
                // The LIKE only skips rows whose text cannot hold a rating, so they are not decoded.
                "SELECT scrape_key, details_json FROM scraped_game WHERE scrape_key > ? AND details_json IS NOT NULL " +
                    "AND details_json LIKE '%\"rating\"%' AND rating IS NULL ORDER BY scrape_key LIMIT $RATING_PAGE",
                arrayOf<Any?>(after),
            ).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1)) }
            }
            if (page.isEmpty()) return
            for ((key, json) in page) {
                val value = try {
                    rating.of(json)?.takeIf { it in 1..MAX_RATING }
                } catch (e: Throwable) {
                    // Details that do not read keep no rating; the row and the migration stay. Any
                    // Throwable (a StackOverflowError on deeply nested text too): rethrown, it would
                    // roll the migration back and the database would fail on every open. Nothing
                    // here is a coroutine, so there is no cancellation to pass on.
                    null
                }
                if (value != null) db.execSQL("UPDATE scraped_game SET rating = ? WHERE scrape_key = ?", arrayOf<Any?>(value, key))
            }
            after = page.last().first
        }
    }

    /**
     * Every migration, oldest first. [rating] reads a stored details JSON for [migration15To16]:
     * the app passes the scraper's reader.
     */
    fun all(rating: DetailsRating): Array<Migration> =
        arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
            MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15,
            migration15To16(rating), MIGRATION_16_17, MIGRATION_17_18,
        )

    private val SESSION_BATTERY_COLUMNS_V8 = listOf("battery_seconds", "charge_start_uah", "charge_end_uah", "battery_draw_ua")

    /** Column definitions exactly as Room's schema 2 has them (`schemas/.../2.json`). */
    private val GAME_COLUMNS_V2: List<Pair<String, String>> = listOf(
        "file_title" to "TEXT NOT NULL DEFAULT ''",
        "file_sort_name" to "TEXT NOT NULL DEFAULT ''",
        "file_regions" to "TEXT NOT NULL DEFAULT ''",
        "name_trust" to "TEXT NOT NULL DEFAULT 'FILE_NAME'",
        "id_method" to "TEXT",
        "id_confidence" to "TEXT",
        "fingerprint" to "TEXT",
        "fingerprint_size" to "INTEGER",
        "fingerprint_mtime" to "INTEGER",
        "pack_version" to "INTEGER",
        "fix_keys" to "TEXT NOT NULL DEFAULT ''",
        "is_not_game" to "INTEGER NOT NULL DEFAULT 0",
        "needs_identify" to "INTEGER NOT NULL DEFAULT 1",
    )

    private fun columnsOf(db: SupportSQLiteDatabase, table: String): Set<String> =
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            buildSet { while (cursor.moveToNext()) add(cursor.getString(nameIndex)) }
        }
}
