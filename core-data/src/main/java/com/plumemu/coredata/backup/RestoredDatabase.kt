package com.plumemu.coredata.backup

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.PlumMigrations
import com.plumemu.coredata.db.ScrapedPictures
import java.io.File
import java.io.IOException

/**
 * Makes an extracted backup database ready to be swapped in,
 * before anything on the device changes:
 *
 * 1. Room opens it with the app's migrations, so an older schema is migrated (and validated against
 *    the entities) here, where a failure changes nothing, not at the first query after the swap.
 * 2. The box-art paths are absolute and start with the backing-up device's folder
 *    ([BackupManifest.mediaRoot]): `scraped_game.pictures` (decoded and encoded again with
 *    [ScrapedPictures], so the layout `BoxFrontShapes` reads in SQL stays) and `media.path` are
 *    rewritten to this device's folder.
 * 3. It is left one file in rollback-journal mode, with no `-wal` or `-shm` that could be separated
 *    from it by the swap; Room turns the WAL on again when the app opens it.
 *
 * Never on the main thread. Throws when the file cannot be prepared; the caller then refuses it.
 */
object RestoredDatabase {
    class Prepared(
        val gameCount: Int,
        /** `scraped_game` rows whose pictures were moved to this device's folder. */
        val picturesRewritten: Int,
        /** `media` rows whose path was moved to this device's folder. */
        val mediaRowsRewritten: Int,
    )

    fun prepare(
        context: Context,
        file: File,
        fromRoot: String?,
        toRoot: String,
    ): Prepared {
        val room =
            Room.databaseBuilder(context.applicationContext, PlumDatabase::class.java, file.absolutePath)
                .addMigrations(*PlumMigrations.ALL)
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .build()
        val prepared =
            try {
                // Opening runs the migrations and Room's schema validation.
                val db = room.openHelper.writableDatabase
                var pictures = 0
                var mediaRows = 0
                db.beginTransaction()
                try {
                    if (fromRoot != null && fromRoot.trimEnd('/') != toRoot.trimEnd('/')) {
                        val from = fromRoot.trimEnd('/') + "/"
                        val to = toRoot.trimEnd('/') + "/"
                        pictures = rewritePictures(db, from, to)
                        mediaRows = rewriteMediaPaths(db, from, to)
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                val check = db.query("PRAGMA quick_check").use { if (it.moveToFirst()) it.getString(0) else null }
                if (check != "ok") throw IOException("the restored database did not pass quick_check after its migration")
                val games = db.query("SELECT COUNT(*) FROM game").use { if (it.moveToFirst()) it.getInt(0) else 0 }
                Prepared(games, pictures, mediaRows)
            } finally {
                room.close()
            }
        oneFile(file)
        return prepared
    }

    private fun rewritePictures(
        db: SupportSQLiteDatabase,
        from: String,
        to: String,
    ): Int {
        val changed = ArrayList<Pair<String, String>>()
        db.query("SELECT scrape_key, pictures FROM scraped_game WHERE pictures != ''").use { c ->
            while (c.moveToNext()) {
                val stored = c.getString(1)
                val pictures = ScrapedPictures.decode(stored)
                if (pictures.none { it.path.startsWith(from) }) continue
                val moved = pictures.map { if (it.path.startsWith(from)) it.copy(path = to + it.path.removePrefix(from)) else it }
                changed += c.getString(0) to ScrapedPictures.encode(moved)
            }
        }
        for ((key, pictures) in changed) {
            db.update("scraped_game", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply { put("pictures", pictures) }, "scrape_key = ?", arrayOf(key))
        }
        return changed.size
    }

    /** `substr`, not `LIKE`: a folder name may hold `%` or `_`; `LIKE` treats them as wildcards. */
    private fun rewriteMediaPaths(
        db: SupportSQLiteDatabase,
        from: String,
        to: String,
    ): Int {
        db.execSQL("UPDATE media SET path = ? || substr(path, ?) WHERE substr(path, 1, ?) = ?", arrayOf<Any>(to, from.length + 1, from.length, from))
        return db.query("SELECT changes()").use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    /** After Room closed the file: no hot journal, no WAL; the empty side files TRUNCATE leaves go. */
    private fun oneFile(file: File) {
        for (side in listOf("-journal", "-wal", "-shm")) {
            val f = File(file.path + side)
            if (!f.exists()) continue
            if (side != "-shm" && f.length() > 0) throw IOException("the restored database kept a non-empty $side")
            if (!f.delete()) throw IOException("the restored database's $side could not be removed")
        }
    }
}
