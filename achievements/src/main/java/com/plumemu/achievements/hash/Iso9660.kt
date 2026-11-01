// Kotlin rewrite of rcheevos' ISO 9660 file lookup (`rc_cd_find_file_sector` in src/hash/rc_hash_disc.c). rcheevos: MIT licence.
package com.plumemu.achievements.hash

import java.io.IOException

/** Where a file sits on the disc: its first [sector] (never 0) and its [size] in bytes. */
internal class DiscFileLocation(val sector: Long, val size: Long)

/**
 * Finds files on an ISO 9660 disc the way rc_hash does: the root directory only (read from its
 * first sector on), directories walked by name, names compared without case, a `;1` version
 * ignored. Paths use `\` between folders, as rc_hash's do.
 */
internal object Iso9660 {
    private const val SECTOR = 2048

    /** One scan over a directory's records. */
    private class Scan(val track: DiscTrack, var sectorIndex: Long, var remaining: Long, val blockSize: Int) {
        val sector = ByteArray(SECTOR)
        var readPtr = -1
        var entry = 0 // offset of the current record in [sector]

        val nameLength: Int get() = u8(entry + 32)
        fun u8(at: Int): Int = if (at in 0 until SECTOR) sector[at].toInt() and 0xFF else 0
        fun fileSector(): Long = (u8(entry + 2) or (u8(entry + 3) shl 8) or (u8(entry + 4) shl 16)).toLong()
        fun fileSize(): Long =
            (u8(entry + 10) or (u8(entry + 11) shl 8) or (u8(entry + 12) shl 16)).toLong() or (u8(entry + 13).toLong() shl 24)
        fun isDirectory(): Boolean = (u8(entry + 25) and 2) != 0

        /** `rc_cd_find_next_file`: moves to the next record, into the next sector of the directory when it has one. */
        @Throws(IOException::class)
        fun next(): Boolean {
            if (readPtr < 0) {
                if (track.readSector(sectorIndex, sector, SECTOR) == 0) return false
                readPtr = 0
            }
            while (true) {
                if (readPtr >= SECTOR - 34 || u8(readPtr) == 0) {
                    if (remaining > blockSize) {
                        remaining -= blockSize
                        if (track.readSector(++sectorIndex, sector, SECTOR) != 0) {
                            readPtr = 0
                            continue
                        }
                    }
                    return false
                }
                entry = readPtr
                readPtr += u8(readPtr)
                if (readPtr > SECTOR) continue // a record that runs past the sector is skipped
                return true
            }
        }

        fun nameMatches(path: String, from: Int, length: Int): Boolean {
            for (i in 0 until length) {
                if (!sameLetter(u8(entry + 33 + i), path[from + i].code)) return false
            }
            return true
        }
    }

    private fun sameLetter(a: Int, b: Int): Boolean = lower(a) == lower(b)
    private fun lower(c: Int): Int = if (c in 'A'.code..'Z'.code) c + 32 else c

    /** `rc_cd_init_file_info`: the volume descriptor sits 16 sectors into the track and says where the root directory is. */
    @Throws(IOException::class)
    private fun start(track: DiscTrack): Scan? {
        val volume = ByteArray(256)
        if (track.readSector(track.firstSector + 16L, volume, 256) == 0) return null
        if (volume[0].toInt() != 1 || String(volume, 1, 5, Charsets.ISO_8859_1) != "CD001") return null
        fun u8(at: Int) = volume[at].toInt() and 0xFF
        val blockSize = u8(128) or (u8(129) shl 8)
        val rootSector = (u8(158) or (u8(159) shl 8) or (u8(160) shl 16)).toLong()
        val rootSize = (u8(166) or (u8(167) shl 8) or (u8(168) shl 16)).toLong() or (u8(169).toLong() shl 24)
        return Scan(track, rootSector, rootSize, blockSize)
    }

    /** The file at [path] (`FOLDER\NAME.EXT`), or null when the disc has no such file or is not ISO 9660. */
    @Throws(IOException::class)
    fun find(track: DiscTrack, path: String): DiscFileLocation? {
        val scan = start(track) ?: return null
        var from = if (path.startsWith("\\")) 1 else 0
        while (true) {
            val slash = path.indexOf('\\', from)
            if (slash < 0) break
            val length = slash - from
            var found = false
            while (scan.next()) {
                // Folders have no version suffix, so the name must match whole.
                if (scan.nameLength == length && scan.nameMatches(path, from, length)) {
                    scan.sectorIndex = scan.fileSector()
                    scan.readPtr = -1
                    found = true
                    break
                }
            }
            if (!found) return null
            from = slash + 1
        }
        val length = path.length - from
        while (scan.next()) {
            val versioned = scan.u8(scan.entry + 33 + length) == ';'.code
            if ((scan.nameLength == length || versioned) && scan.nameMatches(path, from, length)) {
                return DiscFileLocation(scan.fileSector(), scan.fileSize())
            }
        }
        return null
    }

    /** A file found by [list]: its path from the root (`\` between folders, no version suffix), first sector and size. */
    class Entry(val path: String, val sector: Long, val size: Long)

    private const val MAX_LISTED_FILES = 64
    private const val MAX_LISTED_PATH_BYTES = 2048
    private const val MAX_DEPTH = 32
    private const val MAX_FOLDERS = 4096

    /**
     * `rc_cd_get_files`: every file whose name [accept] takes, from the root down, at most 64 of them
     * (rc_hash's limit), sorted by path. Folders are entered after the files of their parent.
     */
    @Throws(IOException::class)
    fun list(track: DiscTrack, accept: (String) -> Boolean): List<Entry> {
        val root = start(track) ?: return emptyList()
        val found = ArrayList<Entry>()
        var pathBytes = 0
        var folders = 0

        fun walk(scan: Scan, path: String, depth: Int) {
            class Folder(val name: String, val sector: Long, val size: Long)
            val subfolders = ArrayList<Folder>()
            while (scan.next()) {
                val name = String(scan.sector, scan.entry + 33, minOf(scan.nameLength, SECTOR - scan.entry - 33).coerceAtLeast(0), Charsets.ISO_8859_1)
                if (scan.isDirectory()) {
                    if (subfolders.size < 64 && scan.nameLength < 32 && scan.u8(scan.entry + 33) >= ' '.code) {
                        subfolders += Folder(name, scan.fileSector(), scan.fileSize())
                    }
                } else {
                    val semicolon = name.lastIndexOf(';')
                    val bare = if (semicolon > 0 && name.substring(semicolon + 1).all { it in '0'..'9' }) name.substring(0, semicolon) else name
                    val full = if (path.isEmpty()) bare else "$path\\$bare"
                    if (accept(bare) && pathBytes + path.length + bare.length + 2 < MAX_LISTED_PATH_BYTES) {
                        found += Entry(full, scan.fileSector(), scan.fileSize())
                        pathBytes += full.length + 1
                        if (found.size == MAX_LISTED_FILES) return
                    }
                }
            }
            if (depth >= MAX_DEPTH) return
            for (folder in subfolders) {
                if (found.size == MAX_LISTED_FILES || ++folders > MAX_FOLDERS) return
                val inner = Scan(track, folder.sector, folder.size, scan.blockSize)
                walk(inner, if (path.isEmpty()) folder.name else "$path\\${folder.name}", depth + 1)
            }
        }
        walk(root, "", 0)
        return found.sortedBy { it.path }
    }
}
