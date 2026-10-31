package com.plumemu.library.scan

import android.util.Log
import com.plumemu.coredata.db.FixKeys
import com.plumemu.coredata.db.GameToIdentify
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.identify.IdentifyRequest
import com.plumemu.identify.Identification
import com.plumemu.identify.arcade.ArcadeSystems
import com.plumemu.identify.fingerprint.FingerprintReader
import com.plumemu.identify.name.Region
import com.plumemu.identify.source.FileSource
import com.plumemu.library.naming.FingerprintCodec
import java.io.IOException
import kotlinx.coroutines.CancellationException

/** What [LibraryScanner.addContentKey] did. */
enum class ContentKeyOutcome {
    /** The fix is now stored under the file's content key too. */
    ADDED,

    /** The row already had a fingerprint key (a zip, a disc, an arcade set): nothing was read. */
    ALREADY_KEYED,

    /** The file was read but gave no content key (over the whole-file size limit, empty). */
    NO_KEY_FOUND,

    /** The file could not be opened or read; the fix stays on the row key. Logged. */
    NOT_READ,

    /** The game is gone, its file changed, or its fix was undone while it was read: nothing written. */
    STALE,
}

/**
 * One whole-file read after a person's fix: a loose cartridge read at QUICK depth has
 * only a product code, which revisions share, so its fix sits on its row key. Reading the file at
 * [FingerprintReader.Depth.FULL] gives its whole-file `crc:` key, under which the fix then follows
 * the file's content. The read runs without the scan lock; [writeLocked] runs the write under it,
 * so a running pass never writes its QUICK keys over the new one. Logs carry folder and game ids
 * and exception classes, never names.
 */
internal class WholeFileRead(
    private val database: PlumDatabase,
    private val permissions: FolderPermissions,
    private val openTree: (RomFolderEntity) -> DocumentTree,
    private val naming: GameNaming,
) {
    suspend fun run(
        gameId: Long,
        writeLocked: suspend (suspend () -> Boolean) -> Boolean,
    ): ContentKeyOutcome {
        val dao = database.identifyDao()
        val keys = dao.contentKeysOf(gameId) ?: return ContentKeyOutcome.STALE
        if (keys.isNotEmpty()) return ContentKeyOutcome.ALREADY_KEYED
        val game = dao.gameToRead(gameId) ?: return ContentKeyOutcome.STALE
        // An arcade set is named from its short name; a set without a key has a name no key can hold.
        if (ArcadeSystems.isArcade(game.systemId)) return ContentKeyOutcome.NO_KEY_FOUND
        val folderId = dao.folderOf(gameId) ?: return ContentKeyOutcome.STALE
        val folder = database.scanDao().folders().firstOrNull { it.id == folderId } ?: return ContentKeyOutcome.STALE
        val identification = try {
            if (!permissions.isHeld(folder.treeUri)) throw WholeFileNotRead("access no longer held", folderGone = true)
            readWhole(naming, openTree(folder), game)
        } catch (e: WholeFileNotRead) {
            return notRead(folderId, gameId, e.reason)
        } catch (e: IOException) {
            return notRead(folderId, gameId, e.javaClass.simpleName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            // Not expected (the identifier turns bad bytes into notes); the fix must stay usable.
            return notRead(folderId, gameId, e.javaClass.simpleName)
        }
        val fingerprints = identification.fingerprints
        if (fingerprints == null || FixKeys.fixTargets(identification.fixKeys).isEmpty()) {
            Log.i(LOG_TAG, "Folder $folderId: game $gameId whole-file read gave no content key; the fix stays on its row")
            return ContentKeyOutcome.NO_KEY_FOUND
        }
        // The file changed after the walk listed it: the next scan reads it again.
        if (fingerprints.size != game.sizeBytes) return ContentKeyOutcome.STALE
        val written = writeLocked {
            dao.addContentKeys(gameId, game.sizeBytes, game.modifiedAt, FingerprintCodec.encode(fingerprints, game.systemId), identification.fixKeys)
        }
        return if (written) ContentKeyOutcome.ADDED else ContentKeyOutcome.STALE
    }

    private fun notRead(
        folderId: Long,
        gameId: Long,
        cause: String,
    ): ContentKeyOutcome {
        Log.w(LOG_TAG, "Folder $folderId: game $gameId whole-file read failed ($cause); the fix stays on its row")
        return ContentKeyOutcome.NOT_READ
    }

    companion object {
        /**
         * [game]'s file opened in [tree] and identified at [FingerprintReader.Depth.FULL] (chunked
         * reads, the caller's thread); [WholeFileNotRead] when it cannot be opened. Shared with [ContentPass].
         */
        fun readWhole(
            naming: GameNaming,
            tree: DocumentTree,
            game: GameToIdentify,
        ): Identification {
            val opened = when (val result = tree.open(game.documentId)) {
                is TreeResult.Found -> result.value
                is TreeResult.PermissionDenied -> throw WholeFileNotRead("access revoked", folderGone = true)
                is TreeResult.Failed -> throw WholeFileNotRead(result.cause.javaClass.simpleName)
                TreeResult.Missing -> throw WholeFileNotRead("missing")
            }
            val request = IdentifyRequest(
                systemId = game.systemId,
                fileName = game.fileName,
                cleanedTitle = game.fileTitle,
                regionsFromName = game.fileRegions.split(',').mapNotNull { Region.fromCode(it.trim()) }.map { it.noIntroName },
                depth = FingerprintReader.Depth.FULL,
            )
            return opened.use { file ->
                FileSource(file.channel).use { source ->
                    naming.identifier.identify(request.copy(source = source, siblings = SiblingFiles(tree).openerFor(game.parentDocumentId)))
                }
            }
        }
    }
}

/**
 * A file [WholeFileRead.readWhole] could not open; [reason] is for the log (no names). [folderGone]:
 * the picked folder's access is gone, so no other file of it opens either.
 */
internal class WholeFileNotRead(val reason: String, val folderGone: Boolean = false) : Exception()
