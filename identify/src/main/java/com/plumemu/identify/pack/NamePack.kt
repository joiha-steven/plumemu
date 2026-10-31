package com.plumemu.identify.pack

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.Locale

/**
 * One name pack (format v1, `identify/PACKS.md`), read in place from a memory-mapped file: no
 * entry is loaded or copied, a lookup touches only the pages its binary search visits, and no
 * object is created except the results.
 *
 * Opening checks the header and that every section lies inside the file (cheap). The SHA-256
 * trailer is checked only by [verifyChecksum], which [PackStore] calls once when it installs a
 * pack. A reference that points outside its section (a pack damaged after it was installed)
 * makes the lookup throw [CorruptPackException].
 *
 * Thread-safe: every read is an absolute get on a buffer nobody moves. The file handle is closed
 * as soon as the file is mapped; the mapping lives as long as this object.
 */
class NamePack private constructor(
    private val buffer: ByteBuffer,
    val kind: PackKind,
    /** The pack's data version, e.g. 20261021; a higher one replaces a lower one. */
    val dataVersion: Long,
    /** The system the pack names, e.g. "megadrive" or "arcade". */
    val systemId: String,
    private val sections: Map<String, IntRange>,
) {
    private val length = buffer.limit()
    private val pool = sections.getValue(PackFormat.TAG_STRINGS)

    /** Number of titles (console packs) or sets (arcade packs). */
    val titleCount: Int = recordCount(if (kind == PackKind.ARCADE) PackFormat.TAG_ARCADE else PackFormat.TAG_TITLES)

    /** Number of releases; 0 in an arcade pack. */
    val releaseCount: Int = recordCount(PackFormat.TAG_RELEASES)

    /** The SHA-256 the trailer records, lower-case hex. */
    val checksum: String = (length - PackFormat.TRAILER_SIZE until length).joinToString("") {
        String.format(Locale.ROOT, "%02x", buffer.get(it).toInt() and 0xFF)
    }

    /** Recomputes the SHA-256 of everything before the trailer and compares it with [checksum]. */
    fun verifyChecksum(): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        val view = buffer.duplicate()
        view.position(0)
        view.limit(length - PackFormat.TRAILER_SIZE)
        digest.update(view)
        val actual = digest.digest()
        return actual.indices.all { actual[it] == buffer.get(length - PackFormat.TRAILER_SIZE + it) }
    }

    /** The sources the pack was built from, in the order releases refer to them. */
    fun sources(): List<PackSource> {
        val section = sections.getValue(PackFormat.TAG_SOURCES)
        val count = u32(section.first)
        if (count > (section.last - section.first + 1 - 4) / PackFormat.SOURCE_RECORD) corrupt("source count")
        return (0 until count.toInt()).map {
            val at = section.first + 4 + it * PackFormat.SOURCE_RECORD
            PackSource(string(buffer.getInt(at)), string(buffer.getInt(at + 4)))
        }
    }

    // ---- console lookups ----

    /**
     * Releases whose content has CRC-32 [crc] (unsigned). With [size], only entries of that
     * content size or of unknown size; without it, every entry with the CRC.
     */
    fun byCrc(crc: Long, size: Long? = null): List<PackRelease> {
        if (kind != PackKind.CONSOLE) return emptyList()
        val section = section(PackFormat.TAG_CRCS)
        val count = recordCount(PackFormat.TAG_CRCS)
        var low = 0
        var high = count
        while (low < high) {
            val mid = (low + high) ushr 1
            if (u32(section.first + mid * PackFormat.CRC_RECORD) < crc) low = mid + 1 else high = mid
        }
        val out = ArrayList<PackRelease>(1)
        var i = low
        while (i < count) {
            val at = section.first + i * PackFormat.CRC_RECORD
            if (u32(at) != crc) break
            val stored = u32(at + 4)
            if (size == null || stored == size || stored == PackFormat.ANY_SIZE) out += release(u32(at + 8).toInt())
            i++
        }
        return out
    }

    /** Releases whose product code is exactly [code] (as `Fingerprints.productCode` gives it). */
    fun byCode(code: String): List<PackRelease> {
        if (kind != PackKind.CONSOLE) return emptyList()
        val key = code.toByteArray(Charsets.UTF_8)
        val section = section(PackFormat.TAG_CODES)
        val count = recordCount(PackFormat.TAG_CODES)
        val first = lowerBound(key, count) { section.first + it * PackFormat.CODE_RECORD }
        val out = ArrayList<PackRelease>(1)
        var i = first
        while (i < count) {
            val at = section.first + i * PackFormat.CODE_RECORD
            if (compareWithString(key, buffer.getInt(at)) != 0) break
            out += release(u32(at + 8).toInt())
            i++
        }
        return out
    }

    /** The name of title [index]; the cheap call the name matcher uses. */
    fun titleName(index: Int): String = string(buffer.getInt(titleAt(index)))

    /** Title [index] with its year, publisher and the regions of all its releases. */
    fun title(index: Int): PackTitle {
        val at = titleAt(index)
        val first = u32(at + 12).toInt()
        val releases = u32(at + 16).toInt()
        if (releases < 0 || first < 0 || first.toLong() + releases > releaseCount) corrupt("title $index releases")
        var regions = 0
        for (r in first until first + releases) regions = regions or buffer.getInt(releaseAt(r) + 4)
        return PackTitle(
            index = index,
            name = string(buffer.getInt(at)),
            year = (buffer.getShort(at + 8).toInt() and 0xFFFF).takeIf { it > 0 },
            publisher = optionalString(buffer.getInt(at + 4)),
            releaseCount = releases,
            regions = PackMasks.decode(regions, PackMasks.REGIONS),
        )
    }

    /** The releases of title [index], in pack order. */
    fun releasesOf(index: Int): List<PackRelease> {
        val at = titleAt(index)
        val first = u32(at + 12).toInt()
        val releases = u32(at + 16).toInt()
        if (releases < 0 || first < 0 || first.toLong() + releases > releaseCount) corrupt("title $index releases")
        return (first until first + releases).map(::release)
    }

    /** Every title in pack order, read one at a time (for the fix-name search screen). */
    fun allTitles(): Sequence<PackTitle> = (0 until titleCount).asSequence().map(::title)

    /** Release [index] with its title's name, year and publisher. */
    fun release(index: Int): PackRelease {
        val at = releaseAt(index)
        val titleIndex = u32(at).toInt()
        val titleAt = titleAt(titleIndex)
        val sourceIndex = buffer.get(at + 16).toInt() and 0xFF
        return PackRelease(
            index = index,
            titleIndex = titleIndex,
            title = string(buffer.getInt(titleAt)),
            regions = PackMasks.decode(buffer.getInt(at + 4), PackMasks.REGIONS),
            languages = PackMasks.decode(buffer.getInt(at + 8), PackMasks.LANGUAGES),
            variant = optionalString(buffer.getInt(at + 12)),
            year = (buffer.getShort(titleAt + 8).toInt() and 0xFFFF).takeIf { it > 0 },
            publisher = optionalString(buffer.getInt(titleAt + 4)),
            source = source(sourceIndex),
        )
    }

    // ---- arcade lookups ----

    /** The set named [shortName] (case-insensitive), or null. Only in an arcade pack. */
    fun byShortName(shortName: String): ArcadeEntry? {
        if (kind != PackKind.ARCADE) return null
        val key = shortName.lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8)
        val section = section(PackFormat.TAG_ARCADE)
        val i = lowerBound(key, titleCount) { section.first + it * PackFormat.ARCADE_RECORD }
        if (i >= titleCount || compareWithString(key, buffer.getInt(section.first + i * PackFormat.ARCADE_RECORD)) != 0) return null
        return arcadeEntry(i)
    }

    /**
     * The short names of the sets whose parent is one of [parents] (short names, any case), by
     * parent short name in lower case: the reverse of [ArcadeEntry.parentShortName], which the pack
     * has no index for, so it is one pass over the records' parent links (no string read for the
     * others). Parents the pack does not list are left out. Only in an arcade pack.
     */
    fun arcadeClonesOf(parents: Set<String>): Map<String, List<String>> {
        if (kind != PackKind.ARCADE || parents.isEmpty()) return emptyMap()
        val section = section(PackFormat.TAG_ARCADE)
        val parentOfIndex = HashMap<Int, String>()
        for (name in parents) byShortName(name)?.let { parentOfIndex[it.index] = it.shortName.lowercase(Locale.ROOT) }
        val clones = HashMap<String, MutableList<String>>()
        for (i in 0 until titleCount) {
            val at = section.first + i * PackFormat.ARCADE_RECORD
            val parent = parentOfIndex[buffer.getInt(at + 16)] ?: continue
            clones.getOrPut(parent) { ArrayList() } += string(buffer.getInt(at))
        }
        return clones
    }

    /** Arcade set [index], in short-name order. */
    fun arcadeEntry(index: Int): ArcadeEntry {
        val section = section(PackFormat.TAG_ARCADE)
        if (index !in 0 until titleCount) corrupt("arcade index $index")
        val at = section.first + index * PackFormat.ARCADE_RECORD
        val parent = buffer.getInt(at + 16)
        if (parent != -1 && parent !in 0 until titleCount) corrupt("arcade parent of $index")
        return ArcadeEntry(
            index = index,
            shortName = string(buffer.getInt(at)),
            title = string(buffer.getInt(at + 4)),
            maker = optionalString(buffer.getInt(at + 8)),
            year = (buffer.getShort(at + 12).toInt() and 0xFFFF).takeIf { it > 0 },
            flags = buffer.getShort(at + 14).toInt() and 0xFFFF,
            parentShortName = if (parent == -1) null else string(buffer.getInt(section.first + parent * PackFormat.ARCADE_RECORD)),
            source = source(buffer.get(at + 20).toInt() and 0xFF),
        )
    }

    // ---- internals ----

    private fun section(tag: String): IntRange = sections[tag] ?: corrupt("no $tag section in a ${kind.name} pack")

    private fun recordCount(tag: String): Int {
        val range = sections[tag] ?: return 0
        val record = PackFormat.recordSize(tag) ?: return 0
        return (range.last - range.first + 1) / record
    }

    private fun titleAt(index: Int): Int {
        if (index !in 0 until titleCount || kind != PackKind.CONSOLE) corrupt("title index $index")
        return section(PackFormat.TAG_TITLES).first + index * PackFormat.TITLE_RECORD
    }

    private fun releaseAt(index: Int): Int {
        if (index !in 0 until releaseCount) corrupt("release index $index")
        return section(PackFormat.TAG_RELEASES).first + index * PackFormat.RELEASE_RECORD
    }

    private fun source(index: Int): PackSource? {
        if (index == PackFormat.NO_SOURCE) return null
        val section = sections.getValue(PackFormat.TAG_SOURCES)
        if (index >= u32(section.first)) corrupt("source index $index")
        val at = section.first + 4 + index * PackFormat.SOURCE_RECORD
        if (at + PackFormat.SOURCE_RECORD > section.last + 1) corrupt("source index $index")
        return PackSource(string(buffer.getInt(at)), string(buffer.getInt(at + 4)))
    }

    /** First record index whose string (the u32 ref at the record start) is not below [key]. */
    private inline fun lowerBound(key: ByteArray, count: Int, recordAt: (Int) -> Int): Int {
        var low = 0
        var high = count
        while (low < high) {
            val mid = (low + high) ushr 1
            if (compareWithString(key, buffer.getInt(recordAt(mid))) > 0) low = mid + 1 else high = mid
        }
        return low
    }

    /** [key] compared, unsigned byte by byte, with the pool string at [ref]. */
    private fun compareWithString(key: ByteArray, ref: Int): Int {
        val start = stringStart(ref)
        val size = buffer.getShort(start - 2).toInt() and 0xFFFF
        val n = minOf(key.size, size)
        for (i in 0 until n) {
            val d = (key[i].toInt() and 0xFF) - (buffer.get(start + i).toInt() and 0xFF)
            if (d != 0) return d
        }
        return key.size - size
    }

    private fun optionalString(ref: Int): String? = if (ref == PackFormat.NO_REF) null else string(ref)

    private fun string(ref: Int): String {
        val start = stringStart(ref)
        val size = buffer.getShort(start - 2).toInt() and 0xFFFF
        val bytes = ByteArray(size)
        for (i in 0 until size) bytes[i] = buffer.get(start + i)
        return String(bytes, Charsets.UTF_8)
    }

    /** Offset of the first byte of the string at [ref]; checks it lies inside the pool. */
    private fun stringStart(ref: Int): Int {
        val poolSize = pool.last - pool.first + 1
        if (ref < 0 || ref.toLong() + 2 > poolSize) corrupt("string reference $ref")
        val size = buffer.getShort(pool.first + ref).toInt() and 0xFFFF
        if (ref.toLong() + 2 + size > poolSize) corrupt("string at $ref runs past the pool")
        return pool.first + ref + 2
    }

    private fun u32(at: Int): Long = buffer.getInt(at).toLong() and PackWriter.UINT_MAX

    private fun corrupt(what: String): Nothing = throw CorruptPackException("pack $systemId: bad $what")

    companion object {
        /**
         * Maps [file] and checks its header and section table. Returns [PackOpenResult.Rejected]
         * with a plain reason for a file that is not a valid v1 pack.
         *
         * @throws IOException when the file cannot be read.
         */
        @Throws(IOException::class)
        fun open(file: File): PackOpenResult {
            val buffer = FileInputStream(file).use { stream ->
                val channel = stream.channel
                val size = channel.size()
                if (size > Int.MAX_VALUE) return PackOpenResult.Rejected("file is larger than 2 GB")
                channel.map(FileChannel.MapMode.READ_ONLY, 0, size)
            }
            return parse(buffer)
        }

        /** Same checks as [open], on bytes in memory (tests, and packs not yet written to a file). */
        fun fromBytes(bytes: ByteArray): PackOpenResult = parse(ByteBuffer.wrap(bytes))

        private fun parse(raw: ByteBuffer): PackOpenResult {
            val buffer = raw.order(ByteOrder.LITTLE_ENDIAN)
            val header = PackHeader.parse(buffer)
            return when (header) {
                is PackHeader.Parsed -> PackOpenResult.Opened(
                    NamePack(buffer, header.kind, header.dataVersion, header.systemId, header.sections),
                )
                is PackHeader.Invalid -> PackOpenResult.Rejected(header.reason)
            }
        }
    }
}

/** What [NamePack.open] found. */
sealed class PackOpenResult {
    data class Opened(val pack: NamePack) : PackOpenResult()

    /** Not a valid v1 pack; [reason] is plain English for the log (no file names in it). */
    data class Rejected(val reason: String) : PackOpenResult()
}

/** A pack whose bytes contradict its own structure (damaged after it was installed). */
class CorruptPackException(message: String) : IllegalStateException(message)

/** A title of a console pack. */
data class PackTitle(
    val index: Int,
    val name: String,
    val year: Int?,
    val publisher: String?,
    val releaseCount: Int,
    /** Region names (as in [PackMasks.REGIONS]) of all its releases. */
    val regions: List<String>,
)

/** One release of a title: a region, a revision, a language set. */
data class PackRelease(
    val index: Int,
    val titleIndex: Int,
    val title: String,
    val regions: List<String>,
    val languages: List<String>,
    /** "Rev 1", "Beta", ...; null for the plain release. */
    val variant: String?,
    val year: Int?,
    val publisher: String?,
    /** Where the name came from; null when the pack did not say. */
    val source: PackSource?,
)

/** One arcade set. [flags] combines [ArcadeFlags]. */
data class ArcadeEntry(
    val index: Int,
    val shortName: String,
    val title: String,
    val maker: String?,
    val year: Int?,
    val flags: Int,
    /** The parent set's short name when this set is a clone. */
    val parentShortName: String?,
    val source: PackSource?,
) {
    val isBios: Boolean get() = flags and ArcadeFlags.BIOS != 0
    val isDevice: Boolean get() = flags and ArcadeFlags.DEVICE != 0
    val isMechanical: Boolean get() = flags and ArcadeFlags.MECHANICAL != 0
    val isClone: Boolean get() = flags and ArcadeFlags.CLONE != 0
    val isNotAGame: Boolean get() = flags and ArcadeFlags.NOT_A_GAME != 0
}
