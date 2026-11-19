package com.plumemu.coredata.db

/** A picked folder with games scanned in through [ScanDao] (`applyBatch` then `finishScan`), as library's scan writes them. */
internal class ScannedLibrary(val db: PlumDatabase, val treeUri: String = "content://tree/roms") {
    val scan get() = db.scanDao()
    var folderId = 0L
        private set

    suspend fun pickFolder(): Long {
        scan.insertFolder(RomFolderEntity(treeUri = treeUri, displayName = "ROMs", addedAt = 0))
        folderId = checkNotNull(scan.folderByTreeUri(treeUri)).id
        return folderId
    }

    /** Scans [games] in as one finished scan; returns their ids by document id. */
    suspend fun scanIn(vararg games: GameEntity, addedAt: Long? = null, systems: List<SystemEntity> = SYSTEMS): Map<String, Long> {
        scan.applyBatch(ScanBatch(folderId, systems = systems, newGames = games.toList(), addedAt = addedAt))
        scan.finishScan(ScanFinish(folderId))
        return scan.gamesInFolder(folderId).associate { it.documentId to it.id }
    }

    suspend fun idOf(documentId: String): Long = scan.gamesInFolder(folderId).single { it.documentId == documentId }.id

    companion object {
        val SYSTEMS = listOf(SystemEntity("snes", "Super Nintendo"), SystemEntity("psx", "PlayStation"))
    }
}

/** A game with fingerprint keys (`crc:` first after an optional `code:`), as identification leaves `fix_keys`. */
internal fun keyedGame(
    folderId: Long,
    documentId: String,
    fixKeys: List<String> = emptyList(),
    systemId: String = "snes",
    title: String = documentId,
    relativePath: String = documentId,
    regions: String = "",
    isPrimaryCopy: Boolean = true,
) = game(folderId, documentId, systemId = systemId, title = title, relativePath = relativePath, isPrimaryCopy = isPrimaryCopy)
    .copy(fixKeys = FixKeys.join(fixKeys), regions = regions, fileRegions = regions)
