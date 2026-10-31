package com.plumemu.coredata.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

/**
 * The library database. Bump [version] only with a migration in [PlumMigrations] and the new
 * schema JSON under `core-data/schemas/`; never with destructive migration, the library holds the
 * person's favourites and play history.
 */
@Database(
    entities = [
        SystemEntity::class,
        RomFolderEntity::class,
        ScannedDirectoryEntity::class,
        GameEntity::class,
        MediaEntity::class,
        PlayEntity::class,
        PlaySessionEntity::class,
        EmulatorChoiceEntity::class,
        NameFixEntity::class,
        ScrapedGameEntity::class,
        PinnedShortcutEntity::class,
        CopyChoiceEntity::class,
        FolderSystemEntity::class,
        ScrapeFailureEntity::class,
        AppGameEntity::class,
        AppKindChoiceEntity::class,
        SkippedSubfolderEntity::class,
    ],
    version = PlumDatabase.SCHEMA_VERSION,
    exportSchema = true,
)
abstract class PlumDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao

    abstract fun gameDao(): GameDao

    abstract fun playDao(): PlayDao

    abstract fun emulatorChoiceDao(): EmulatorChoiceDao

    abstract fun identifyDao(): IdentifyDao

    abstract fun scrapedGameDao(): ScrapedGameDao

    abstract fun pinnedShortcutDao(): PinnedShortcutDao

    abstract fun copyChoiceDao(): CopyChoiceDao

    abstract fun folderSystemDao(): FolderSystemDao

    abstract fun appGameDao(): AppGameDao

    abstract fun skippedSubfolderDao(): SkippedSubfolderDao

    companion object {
        const val FILE_NAME = "plumemu.db"

        /** The schema version above; a restore refuses a database newer than this. */
        const val SCHEMA_VERSION = 13

        /**
         * Opens the app's database file; call once and keep the instance for the process. With a
         * [gate], every open of the file passes through it first (Settings › Advanced › Restore…
         * holds the file shut while it swaps it, and puts an interrupted swap right before the
         * first open:).
         */
        fun open(
            context: Context,
            gate: DatabaseGate? = null,
            /** The app's file; tests give a path of their own. */
            name: String = FILE_NAME,
        ): PlumDatabase =
            Room.databaseBuilder(context.applicationContext, PlumDatabase::class.java, name)
                .addMigrations(*PlumMigrations.ALL)
                .apply { if (gate != null) openHelperFactory(gate.around(FrameworkSQLiteOpenHelperFactory())) }
                .build()
    }
}
