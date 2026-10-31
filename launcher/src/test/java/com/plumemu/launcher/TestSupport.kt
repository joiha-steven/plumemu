package com.plumemu.launcher

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScanBatch
import com.plumemu.coredata.db.SystemEntity
import com.plumemu.library.storage.StorageRoots
import com.plumemu.platforms.PlatformCatalog
import com.plumemu.platforms.PlatformLoadResult
import com.plumemu.platforms.PlatformLoader
import java.io.File

internal object TestCatalogs {
    val shipped: PlatformCatalog by lazy { load(File("../platforms/src/main/assets/platforms.json").readText(), "shipped") }

    /**
     * One system, `snes`, whose list starts with a legal-risk emulator, then two RetroArch cores
     * sharing a package (64-bit build first, 32-bit variant), then a standalone emulator taking a
     * URI; `usb` has only a path emulator.
     */
    val small: PlatformCatalog by lazy { load(SMALL, "small") }

    private fun load(text: String, source: String): PlatformCatalog =
        when (val result = PlatformLoader.load(text, source)) {
            is PlatformLoadResult.Loaded -> result.catalog
            is PlatformLoadResult.Failed -> error(result.failure.describe())
        }

    private val SMALL = """
        {"formatVersion":2,
         "systems":[
          {"id":"snes","name":"Super Nintendo","shortName":"SNES","displayName":"Super Nintendo","order":0,"folders":["snes"],"extensions":["sfc"],
           "cover":"landscape","tier":"tested","emulators":["risky","ra-snes9x","ra-bsnes","snes9x"]},
          {"id":"usb","name":"Path Only","shortName":"Path","displayName":"Path Only","order":1,"folders":["usb"],"extensions":["bin"],
           "cover":"portrait","tier":"untested","emulators":["pathemu"]}],
         "emulators":[
          {"id":"pathemu","name":"Path Emu","package":"com.path.emu","activity":"com.path.emu.Main",
           "game":{"mode":"path","target":"data"},"action":"android.intent.action.VIEW"},
          {"id":"ra-bsnes","name":"RetroArch: bsnes","package":"com.ra","activity":"com.ra.Retro","bits":64,
           "variants":[{"package":"com.ra32","activity":"com.ra.Retro","bits":32}],"core":"bsnes_libretro_android.so",
           "game":{"mode":"path","target":"extra","key":"ROM"},"extras":{"LIBRETRO":"/data/data/{package}/cores/{core}"}},
          {"id":"ra-snes9x","name":"RetroArch: Snes9x","package":"com.ra","activity":"com.ra.Retro","bits":64,
           "variants":[{"package":"com.ra32","activity":"com.ra.Retro","bits":32}],"core":"snes9x_libretro_android.so",
           "game":{"mode":"path","target":"extra","key":"ROM"},"extras":{"LIBRETRO":"/data/data/{package}/cores/{core}"}},
          {"id":"risky","name":"Risky","package":"com.risky","activity":"com.risky.Main",
           "game":{"mode":"uri","target":"data"},"legalRisk":true},
          {"id":"snes9x","name":"Snes9x EX+","package":"com.snes9x","activity":"com.snes9x.Main",
           "game":{"mode":"uri","target":"extra","key":"uri"},"action":"android.intent.action.VIEW"}]}
    """.trimIndent()
}

internal class RecordingLog : LauncherLog {
    val lines = mutableListOf<String>()

    override fun info(message: String) {
        lines += "I $message"
    }

    override fun warn(message: String) {
        lines += "W $message"
    }
}

internal fun inMemoryDatabase(): PlumDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java)
        .allowMainThreadQueries()
        .build()

internal const val INTERNAL_TREE = "content://com.android.externalstorage.documents/tree/primary%3AROMs"
/** The primary user's storage roots: `/storage/emulated/0` and `/storage/<id>`. */
internal val OWNER_STORAGE = object : StorageRoots {
    override fun internal() = File("/storage/emulated/0")

    override fun volume(id: String) = File("/storage", id)
}

internal const val USB_TREE = "content://com.example.usbprovider/tree/root"

/** Adds one game in a folder picked at [treeUri] and returns its id. */
internal suspend fun PlumDatabase.addGame(
    systemId: String,
    documentId: String,
    treeUri: String = INTERNAL_TREE,
    fileName: String = documentId.substringAfterLast('/'),
): Long {
    val folderId = scanDao().folderByTreeUri(treeUri)?.id
        ?: scanDao().insertFolder(RomFolderEntity(treeUri = treeUri, displayName = "ROMs", addedAt = 0))
    val game = GameEntity(
        folderId = folderId, documentId = documentId, parentDocumentId = "parent", relativePath = documentId,
        fileName = fileName, sizeBytes = 1, modifiedAt = 1, title = "Game", sortName = "game", regions = "",
        discGroup = null, discCount = 1, systemId = systemId, isPrimaryCopy = true,
    )
    scanDao().applyBatch(ScanBatch(folderId, listOf(SystemEntity(systemId, systemId)), listOf(game)))
    return scanDao().gamesInFolder(folderId).single { it.documentId == documentId }.id
}

internal object AndroidPackageLookupFactory {
    fun create(): com.plumemu.launcher.detect.PackageLookup =
        com.plumemu.launcher.detect.AndroidPackageLookup(
            ApplicationProvider.getApplicationContext<android.content.Context>().packageManager,
        )
}
