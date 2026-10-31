package com.plumemu.library.storage

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

/**
 * The one reader of Android's own storage provider (`ExternalStorageProvider`) ids, used by setup's
 * folder names, the scan's deleted-folder proof, the launcher's paths and its SD-card check.
 * Its document ids are `<root>:<path below the root>`:
 * - `primary`: the device's own storage of this user;
 * - `home`: the "Documents" shortcut, the `Documents` folder of that same storage;
 * - a volume's file-system id: `1A2B-3C4D` (FAT, exFAT), `0123456789ABCDEF` (NTFS), a full UUID
 *   (ext4, f2fs): an SD card or a USB drive Android mounted as a volume.
 * Pure string work, so it runs in plain JVM tests; where a root really is on the device is
 * [StorageRoots]' job.
 */
object ExternalStorageIds {
    const val AUTHORITY = "com.android.externalstorage.documents"

    const val PRIMARY_ROOT = "primary"
    const val HOME_ROOT = "home"

    /** The folder below internal storage that the `home` root stands for (`Environment.DIRECTORY_DOCUMENTS`). */
    const val HOME_FOLDER = "Documents"

    // Hex groups of 4 or more, joined by dashes: covers every file-system id Android mounts under
    // `/storage/<id>`, and refuses names (`secondary`) and anything with a path in it.
    private val VOLUME_ID = Regex("^[0-9A-Fa-f]{4,}(-[0-9A-Fa-f]{4,})*$")

    /**
     * [documentId] read, or null when it is not a well-formed id of this provider. A `.` or `..`
     * component is refused: the provider never makes one, and it would point a stored row elsewhere.
     */
    fun parse(documentId: String): StorageDocument? {
        val colon = documentId.indexOf(':')
        if (colon <= 0) return null
        val root = when (val id = documentId.substring(0, colon)) {
            PRIMARY_ROOT -> StorageRoot.Internal
            HOME_ROOT -> StorageRoot.Home
            else -> if (VOLUME_ID.matches(id)) StorageRoot.Volume(id) else return null
        }
        val path = documentId.substring(colon + 1).split('/').filter { it.isNotEmpty() }
        if (path.any { it == "." || it == ".." }) return null
        return StorageDocument(root, path)
    }

    /**
     * The tree's document id of a tree URI of this provider (`content://<AUTHORITY>/tree/<id>`, or a
     * document URI under it, `.../tree/<id>/document/<id>`), percent-decoded; null for another
     * provider or a malformed URI. A literal `+` stays a `+`: it is no space in a URI path.
     */
    fun treeDocumentId(uri: String): String? {
        val parsed = try {
            URI(uri)
        } catch (e: URISyntaxException) {
            return null
        }
        if (parsed.scheme != "content" || parsed.rawAuthority != AUTHORITY) return null
        val segments = parsed.rawPath?.split('/')?.filter { it.isNotEmpty() } ?: return null
        val shapeOk = segments.size == 2 || (segments.size == 4 && segments[2] == "document")
        if (!shapeOk || segments[0] != "tree") return null
        return try {
            URLDecoder.decode(segments[1].replace("+", "%2B"), Charsets.UTF_8.name())
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** [parse] of [treeDocumentId]. */
    fun treeDocument(uri: String): StorageDocument? = treeDocumentId(uri)?.let(::parse)
}

/** Which storage a document of [ExternalStorageIds] is on. */
sealed interface StorageRoot {
    /** The device's own storage of this user (`primary`). */
    data object Internal : StorageRoot

    /** The "Documents" shortcut (`home`): [ExternalStorageIds.HOME_FOLDER] on internal storage. */
    data object Home : StorageRoot

    /** A removable volume by its file-system id. */
    data class Volume(val id: String) : StorageRoot
}

/** A document of [ExternalStorageIds]: its root and the path components below it (never `.` or `..`). */
data class StorageDocument(val root: StorageRoot, val path: List<String>) {
    /** The path below internal storage for [StorageRoot.Internal] and [StorageRoot.Home]; null on a volume. */
    val internalPath: List<String>?
        get() = when (root) {
            StorageRoot.Internal -> path
            StorageRoot.Home -> listOf(ExternalStorageIds.HOME_FOLDER) + path
            is StorageRoot.Volume -> null
        }

    /** The real path on this device, from [roots]; null when the root has none. */
    fun realPath(roots: StorageRoots): String? {
        val base = when (root) {
            StorageRoot.Internal, StorageRoot.Home -> roots.internal()
            is StorageRoot.Volume -> roots.volume(root.id)
        } ?: return null
        val below = if (root is StorageRoot.Volume) path else internalPath.orEmpty()
        return (listOf(base.path.trimEnd('/')) + below).joinToString("/")
    }
}
