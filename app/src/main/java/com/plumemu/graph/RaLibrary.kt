package com.plumemu.graph

import android.net.Uri
import android.provider.DocumentsContract
import com.plumemu.achievements.hash.RaRomSource
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.RaUnmatchedGame
import com.plumemu.launcher.intent.DocumentPaths
import com.plumemu.library.scan.DocumentTree
import com.plumemu.library.scan.TreeResult
import com.plumemu.library.storage.StorageRoots
import java.io.File
import java.io.IOException

/** A library game as achievements see it: its system and title; its file only when asked ([file]). */
internal class RaLibraryGame(
    val id: Long,
    val systemId: String,
    val title: String,
    /** The file's own title (from its name), the second name a name match tries. */
    val fileTitle: String = title,
    /** The file to hash, read now (blocking: call on the IO dispatcher). Throws [IOException] when it cannot be reached. */
    val file: () -> RaLibraryFile,
)

/** A ROM file and its modified time (epoch milliseconds), the hash cache's key with [RaRomSource.key] and its size. */
internal class RaLibraryFile(val source: RaRomSource, val modifiedAt: Long)

/** The library games achievements read: one by id, and the unmatched ones of some systems. */
internal interface RaLibrary {
    suspend fun game(gameId: Long): RaLibraryGame?

    suspend fun unmatched(systemIds: List<String>): List<RaUnmatchedGame>
}

/**
 * [RaLibrary] over the library database. A game's file is read as a plain file when its picked
 * folder has a real path Plumemu can read, else through the documents provider ([openTree]).
 */
internal class DatabaseRaLibrary(
    private val database: () -> PlumDatabase,
    private val openTree: (treeUri: String) -> DocumentTree,
    private val roots: StorageRoots,
) : RaLibrary {
    override suspend fun game(gameId: Long): RaLibraryGame? {
        val games = database().gameDao()
        val game = games.game(gameId) ?: return null
        val treeUri = games.launchTarget(gameId)?.treeUri ?: return null
        return RaLibraryGame(game.id, game.systemId, game.title, game.fileTitle) {
            raLibraryFileOf(treeUri, game.documentId, game.parentDocumentId, game.fileName, roots, openTree)
        }
    }

    override suspend fun unmatched(systemIds: List<String>): List<RaUnmatchedGame> = database().raDao().unmatchedGames(systemIds)
}

/**
 * The file of a library game: by its real path when there is one and it can be read, else through
 * the documents provider, whose answer gives the exact size. Blocking.
 */
internal fun raLibraryFileOf(
    treeUri: String,
    documentId: String,
    parentDocumentId: String,
    fileName: String,
    roots: StorageRoots,
    openTree: (String) -> DocumentTree,
): RaLibraryFile {
    val tree = Uri.parse(treeUri)
    val path = DocumentPaths.realPath(tree.authority, documentId, roots)
    val plain = path?.let(::File)?.takeIf { it.isFile && it.canRead() }
    if (plain != null) {
        val source = FileRaRomSource(plain)
        return RaLibraryFile(source, source.lastModifiedMillis)
    }
    val documents = openTree(treeUri)
    val entry = when (val stat = documents.stat(documentId)) {
        is TreeResult.Found -> stat.value
        TreeResult.Missing -> throw IOException("the folder is gone")
        is TreeResult.PermissionDenied -> throw IOException("folder access revoked (${stat.cause.javaClass.simpleName})")
        is TreeResult.Failed -> throw IOException("the provider could not read it (${stat.cause.javaClass.simpleName})")
    }
    val key = path ?: DocumentsContract.buildDocumentUriUsingTree(tree, documentId).toString()
    val source = DocumentRaRomSource(documents, documentId, parentDocumentId, key, entry.name.ifEmpty { fileName }, entry.sizeBytes)
    return RaLibraryFile(source, entry.lastModified)
}
