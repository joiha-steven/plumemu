package com.plumemu.identify.fingerprint.disc

import com.plumemu.identify.fingerprint.ascii
import com.plumemu.identify.fingerprint.startsWith
import com.plumemu.identify.fingerprint.u32le
import com.plumemu.identify.fingerprint.u8
import java.io.IOException
import java.util.Locale

/**
 * Just enough ISO 9660 (ECMA-119) to read a few small files by name: the Primary Volume
 * Descriptor at sector 16 (type 1, "CD001" at 1, root directory record at 156), directory
 * records (length at 0, extent LBA little-endian at 2, data length at 10, flags at 25 with bit 1
 * a directory, name length at 32, name at 33; a record never crosses a sector, a 0 length byte
 * pads to the next sector). Names compare case-insensitively without the ";1" version.
 *
 * Everything is capped: a directory is read up to its size and [MAX_DIRECTORY_BYTES], a file up to the
 * caller's limit, extents past the image end read as empty, and parsing always moves forward.
 */
internal class Iso9660 private constructor(private val image: SectorImage, private val root: Entry) {

    /** One directory record. */
    data class Entry(val name: String, val lba: Long, val size: Long, val isDirectory: Boolean)

    /**
     * The entry at [path] ("SYSTEM.CNF", "PSP_GAME/PARAM.SFO"), following directories from the
     * root; null when missing.
     */
    @Throws(IOException::class)
    fun find(path: String): Entry? {
        var current = root
        for (part in path.split('/')) {
            if (!current.isDirectory) return null
            current = listing(current).find(part.uppercase(Locale.ROOT)) ?: return null
        }
        return current
    }

    /** The first [limit] bytes of [entry]'s data (fewer when the file or the image is shorter). */
    @Throws(IOException::class)
    fun readHead(entry: Entry, limit: Int): ByteArray =
        image.read(entry.lba, minOf(entry.size, limit.toLong()).toInt())

    /** Directories being read, by extent: several names are looked up in the root. */
    private val listings = HashMap<Long, Listing>()

    private fun listing(directory: Entry): Listing = listings.getOrPut(directory.lba) { Listing(directory) }

    /**
     * One directory, read a sector at a time only until the name looked for turns up, up to its
     * declared size or [MAX_DIRECTORY_BYTES]: a root with hundreds of files costs only the
     * sectors in front of the one wanted.
     */
    private inner class Listing(private val directory: Entry) {
        private val entries = ArrayList<Entry>()
        private val sectors = (minOf(directory.size, MAX_DIRECTORY_BYTES.toLong()) + SectorImage.SECTOR - 1) / SectorImage.SECTOR
        private var nextSector = 0L

        @Throws(IOException::class)
        fun find(name: String): Entry? {
            entries.firstOrNull { it.name == name }?.let { return it }
            while (nextSector < sectors) {
                val bytes = image.read(directory.lba + nextSector++, SectorImage.SECTOR)
                val before = entries.size
                parseSector(bytes)
                entries.subList(before, entries.size).firstOrNull { it.name == name }?.let { return it }
                if (bytes.size < SectorImage.SECTOR) break
            }
            return null
        }

        private fun parseSector(bytes: ByteArray) {
            var at = 0
            while (at < bytes.size) {
                val length = bytes.u8(at)
                // A 0 length pads the rest of the sector; records never cross sectors.
                if (length == 0 || length < RECORD_MIN || at + length > bytes.size) return
                parseRecord(bytes, at, length)?.let { entries += it }
                at += length
            }
        }
    }

    companion object {
        /** A directory is read up to this much; the 64 KB read cap of a disc stops it first. */
        const val MAX_DIRECTORY_BYTES = 64 * 1024
        private const val PVD_SECTOR = 16L
        private const val ROOT_RECORD = 156
        private const val RECORD_MIN = 34
        private val CD001 = ascii("CD001")

        /** The volume of [image], or null when sector 16 is not a Primary Volume Descriptor. */
        @Throws(IOException::class)
        fun open(image: SectorImage): Iso9660? {
            val pvd = image.read(PVD_SECTOR, ROOT_RECORD + RECORD_MIN)
            if (pvd.size < ROOT_RECORD + RECORD_MIN || pvd.u8(0) != 1 || !pvd.startsWith(CD001, 1)) return null
            val root = parseRecord(pvd, ROOT_RECORD, RECORD_MIN) ?: return null
            return Iso9660(image, root.copy(isDirectory = true))
        }

        private fun parseRecord(bytes: ByteArray, at: Int, length: Int): Entry? {
            val nameLength = bytes.u8(at + 32)
            if (33 + nameLength > length) return null
            val raw = String(bytes, at + 33, nameLength, Charsets.ISO_8859_1)
            val name = raw.substringBefore(';').trimEnd('.').uppercase(Locale.ROOT)
            val isDirectory = bytes.u8(at + 25) and 0x02 != 0
            return Entry(name, bytes.u32le(at + 2), bytes.u32le(at + 10), isDirectory)
        }
    }
}
