package com.plumemu.identify.pack

import com.plumemu.identify.pack.PackFormat.NO_REF
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Locale

/**
 * Writes name packs in format v1 (`identify/PACKS.md`). Used by tests and measurements; the
 * Python pack tool must produce the same bytes for the same input.
 *
 * Collect with [ConsolePackBuilder] or [ArcadePackBuilder], then call `build()`. Names outside
 * the region and language lists ([PackMasks]) are dropped and reported in `warnings`.
 */
object PackWriter {
    internal fun assemble(kind: PackKind, systemId: String, dataVersion: Long, sections: List<Pair<String, ByteArray>>): ByteArray {
        val idBytes = systemId.toByteArray(Charsets.US_ASCII)
        require(idBytes.isNotEmpty() && idBytes.size <= PackFormat.SYSTEM_ID_BYTES && systemId.all { it.code in 0x21..0x7E }) {
            "system id must be 1-16 printable ASCII characters"
        }
        require(dataVersion in 0..UINT_MAX) { "data version must fit 32 bits" }
        val out = LittleEndianBuffer()
        out.bytes(PackFormat.MAGIC)
        out.u16(PackFormat.FORMAT_VERSION)
        out.u8(kind.code)
        out.u8(0)
        out.u32(dataVersion)
        out.bytes(idBytes)
        out.bytes(ByteArray(PackFormat.SYSTEM_ID_BYTES - idBytes.size))
        out.u16(sections.size)
        out.u16(0)
        var offset = PackFormat.HEADER_SIZE + sections.size * PackFormat.SECTION_ENTRY_SIZE
        for ((tag, body) in sections) {
            offset = align4(offset)
            out.bytes(tag.toByteArray(Charsets.US_ASCII))
            out.u32(offset.toLong())
            out.u32(body.size.toLong())
            offset += body.size
        }
        for ((_, body) in sections) {
            out.padTo4()
            out.bytes(body)
        }
        out.padTo4()
        val withoutTrailer = out.toByteArray()
        return withoutTrailer + MessageDigest.getInstance("SHA-256").digest(withoutTrailer)
    }

    internal fun align4(value: Int): Int = (value + 3) and 3.inv()

    internal const val UINT_MAX: Long = (1L shl 32) - 1
}

/** One source a pack was built from: "No-Intro" with its DAT date, "MAME" with its version. */
data class PackSource(val name: String, val version: String)

/** Builds a console pack. Not thread-safe; one builder per pack. */
class ConsolePackBuilder(private val systemId: String, private val dataVersion: Long) {
    private class TitleIn(val name: String, val year: Int?, val publisher: String?)
    private class ReleaseIn(
        val title: Int,
        val regionMask: Int,
        val languageMask: Int,
        val variant: String?,
        val source: Int,
        val crcs: List<Pair<Long, Long?>>,
        val codes: List<Pair<String, Int>>,
    )

    private val sources = ArrayList<PackSource>()
    private val titles = ArrayList<TitleIn>()
    private val releases = ArrayList<ReleaseIn>()

    /** Problems the writer worked around (dropped regions, ...), for the build tool to print. */
    val warnings: MutableList<String> = ArrayList()

    /** Adds a source and returns its index for [release]. */
    fun source(name: String, version: String): Int {
        require(sources.size < PackFormat.NO_SOURCE) { "at most 255 sources" }
        sources += PackSource(name, version)
        return sources.size - 1
    }

    /** Adds a title and returns its handle for [release]. [year] 0 or null is unknown. */
    fun title(name: String, year: Int? = null, publisher: String? = null): Int {
        require(name.isNotBlank()) { "a title needs a name" }
        require(year == null || year in 0..0xFFFF) { "year must fit 16 bits" }
        titles += TitleIn(name, year?.takeIf { it > 0 }, publisher)
        return titles.size - 1
    }

    /**
     * Adds a release of [title]. [crcs] are (CRC-32, content size or null when unknown);
     * [codes] are (product code, code kind byte from PACKS.md, 0 when unspecified).
     */
    fun release(
        title: Int,
        regions: List<String> = emptyList(),
        languages: List<String> = emptyList(),
        variant: String? = null,
        source: Int? = null,
        crcs: List<Pair<Long, Long?>> = emptyList(),
        codes: List<Pair<String, Int>> = emptyList(),
    ) {
        require(title in titles.indices) { "unknown title handle" }
        require(source == null || source in sources.indices) { "unknown source index" }
        val (regionMask, unknownRegions) = PackMasks.encode(regions, PackMasks.REGIONS)
        val (languageMask, unknownLanguages) = PackMasks.encode(languages, PackMasks.LANGUAGES)
        if (unknownRegions.isNotEmpty()) warnings += "${titles[title].name}: regions dropped: $unknownRegions"
        if (unknownLanguages.isNotEmpty()) warnings += "${titles[title].name}: languages dropped: $unknownLanguages"
        crcs.forEach { (crc, _) -> require(crc in 0..PackWriter.UINT_MAX) { "CRC must be an unsigned 32-bit value" } }
        codes.forEach { (_, kind) -> require(kind in 0..0xFF) { "code kind must fit a byte" } }
        releases += ReleaseIn(title, regionMask, languageMask, variant, source ?: PackFormat.NO_SOURCE, crcs, codes)
    }

    /** The pack bytes, trailer included. */
    fun build(): ByteArray {
        val pool = StringPool()
        // Canonical order (PACKS.md): UTF-8 bytes of the lower-cased name, unsigned; ties keep input order.
        val sortKeys = titles.map { it.name.lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8) }
        val titleOrder = titles.indices.sortedWith { a, b -> compareUnsigned(sortKeys[a], sortKeys[b]).takeIf { it != 0 } ?: (a - b) }
        val newIndex = IntArray(titles.size).also { map -> titleOrder.forEachIndexed { new, old -> map[old] = new } }
        val releaseOrder = releases.indices.sortedWith(compareBy({ newIndex[releases[it].title] }, { it }))
        val firstRelease = IntArray(titles.size) { -1 }
        val releaseCount = IntArray(titles.size)
        releaseOrder.forEachIndexed { position, old ->
            val t = newIndex[releases[old].title]
            if (firstRelease[t] < 0) firstRelease[t] = position
            releaseCount[t]++
        }
        val titleBytes = LittleEndianBuffer()
        titleOrder.forEachIndexed { new, old ->
            val t = titles[old]
            titleBytes.i32(pool.ref(t.name))
            titleBytes.i32(t.publisher?.let(pool::ref) ?: NO_REF)
            titleBytes.u16(t.year ?: 0)
            titleBytes.u16(0)
            titleBytes.u32((if (firstRelease[new] < 0) 0 else firstRelease[new]).toLong())
            titleBytes.u32(releaseCount[new].toLong())
        }
        val releaseBytes = LittleEndianBuffer()
        val crcRows = ArrayList<LongArray>()
        val codeRows = ArrayList<Triple<ByteArray, Int, Int>>()
        releaseOrder.forEachIndexed { position, old ->
            val r = releases[old]
            releaseBytes.u32(newIndex[r.title].toLong())
            releaseBytes.i32(r.regionMask)
            releaseBytes.i32(r.languageMask)
            releaseBytes.i32(r.variant?.let(pool::ref) ?: NO_REF)
            releaseBytes.u8(r.source)
            releaseBytes.bytes(ByteArray(3))
            r.crcs.forEach { (crc, size) ->
                val stored = if (size == null || size !in 0 until PackFormat.ANY_SIZE) PackFormat.ANY_SIZE else size
                crcRows += longArrayOf(crc, stored, position.toLong())
            }
            r.codes.forEach { (code, kind) -> codeRows += Triple(code.toByteArray(Charsets.UTF_8), kind, position) }
        }
        val crcBytes = crcSection(crcRows)
        val codeBytes = codeSection(codeRows, pool)
        val sourceBytes = sourcesSection(sources, pool)
        return PackWriter.assemble(
            PackKind.CONSOLE,
            systemId,
            dataVersion,
            listOf(
                PackFormat.TAG_STRINGS to pool.bytes(),
                PackFormat.TAG_SOURCES to sourceBytes,
                PackFormat.TAG_TITLES to titleBytes.toByteArray(),
                PackFormat.TAG_RELEASES to releaseBytes.toByteArray(),
                PackFormat.TAG_CRCS to crcBytes.toByteArray(),
                PackFormat.TAG_CODES to codeBytes.toByteArray(),
            ),
        )
    }
}

/** Arcade flag bits of an `ARCD` record (PACKS.md). */
object ArcadeFlags {
    const val BIOS = 1
    const val DEVICE = 2
    const val MECHANICAL = 4
    const val CLONE = 8
    const val NOT_A_GAME = 16
}

/** Builds an arcade pack. Not thread-safe; one builder per pack. */
class ArcadePackBuilder(private val systemId: String, private val dataVersion: Long) {
    private class SetIn(val shortName: String, val title: String, val maker: String?, val year: Int?, val flags: Int, val parent: String?, val source: Int)

    private val sources = ArrayList<PackSource>()
    private val sets = LinkedHashMap<String, SetIn>()

    /** Problems the writer worked around (a parent that is not in the pack, ...). */
    val warnings: MutableList<String> = ArrayList()

    fun source(name: String, version: String): Int {
        require(sources.size < PackFormat.NO_SOURCE) { "at most 255 sources" }
        sources += PackSource(name, version)
        return sources.size - 1
    }

    /**
     * Adds a set. [shortName] is stored lower-case; [flags] combines [ArcadeFlags]; [parent] is
     * the parent's short name for a clone. A clone flag is set when [parent] is given.
     */
    fun set(shortName: String, title: String, maker: String? = null, year: Int? = null, flags: Int = 0, parent: String? = null, source: Int? = null) {
        val key = shortName.lowercase(Locale.ROOT)
        require(key.isNotBlank()) { "a set needs a short name" }
        require(year == null || year in 0..0xFFFF) { "year must fit 16 bits" }
        require(source == null || source in sources.indices) { "unknown source index" }
        require(key !in sets) { "duplicate short name" }
        val withClone = if (parent != null) flags or ArcadeFlags.CLONE else flags
        sets[key] = SetIn(key, title, maker, year?.takeIf { it > 0 }, withClone, parent?.lowercase(Locale.ROOT), source ?: PackFormat.NO_SOURCE)
    }

    fun build(): ByteArray {
        val pool = StringPool()
        val ordered = sets.values.sortedWith { a, b -> compareUnsigned(a.shortName.toByteArray(Charsets.UTF_8), b.shortName.toByteArray(Charsets.UTF_8)) }
        val position = HashMap<String, Int>().also { map -> ordered.forEachIndexed { i, s -> map[s.shortName] = i } }
        val body = LittleEndianBuffer()
        for (s in ordered) {
            val parentIndex = s.parent?.let { name ->
                val found = position[name]
                if (found == null) warnings += "${s.shortName}: parent $name not in pack"
                found
            } ?: -1
            body.i32(pool.ref(s.shortName))
            body.i32(pool.ref(s.title))
            body.i32(s.maker?.let(pool::ref) ?: NO_REF)
            body.u16(s.year ?: 0)
            body.u16(s.flags)
            body.i32(parentIndex)
            body.u8(s.source)
            body.bytes(ByteArray(3))
        }
        val sourceBytes = sourcesSection(sources, pool)
        return PackWriter.assemble(
            PackKind.ARCADE,
            systemId,
            dataVersion,
            listOf(
                PackFormat.TAG_STRINGS to pool.bytes(),
                PackFormat.TAG_SOURCES to sourceBytes,
                PackFormat.TAG_ARCADE to body.toByteArray(),
            ),
        )
    }
}

/** Rows (CRC, size, release) sorted as unsigned numbers. */
private fun crcSection(rows: MutableList<LongArray>): LittleEndianBuffer {
    rows.sortWith(compareBy({ it[0] }, { it[1] }, { it[2] }))
    val out = LittleEndianBuffer()
    rows.forEach { out.u32(it[0]); out.u32(it[1]); out.u32(it[2]) }
    return out
}

/** Rows (code bytes, kind, release) sorted by unsigned bytes, then kind, then release. */
private fun codeSection(rows: MutableList<Triple<ByteArray, Int, Int>>, pool: StringPool): LittleEndianBuffer {
    rows.sortWith { a, b -> compareUnsigned(a.first, b.first).takeIf { it != 0 } ?: compareValuesBy(a, b, { it.second }, { it.third }) }
    val out = LittleEndianBuffer()
    rows.forEach { (code, kind, release) ->
        out.i32(pool.ref(String(code, Charsets.UTF_8)))
        out.u8(kind)
        out.bytes(ByteArray(3))
        out.u32(release.toLong())
    }
    return out
}

private fun sourcesSection(sources: List<PackSource>, pool: StringPool): ByteArray {
    val out = LittleEndianBuffer()
    out.u32(sources.size.toLong())
    sources.forEach { out.i32(pool.ref(it.name)); out.i32(pool.ref(it.version)) }
    return out.toByteArray()
}

/** Byte-wise unsigned comparison: the order binary searches in [NamePack] rely on. */
internal fun compareUnsigned(a: ByteArray, b: ByteArray): Int {
    val n = minOf(a.size, b.size)
    for (i in 0 until n) {
        val d = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
        if (d != 0) return d
    }
    return a.size - b.size
}

/** Strings stored once each: u16 length, then UTF-8 bytes; a reference is the offset of the length. */
private class StringPool {
    private val out = LittleEndianBuffer()
    private val offsets = HashMap<String, Int>()

    fun ref(text: String): Int = offsets.getOrPut(text) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size <= PackFormat.MAX_STRING_BYTES) { "string longer than 65535 bytes" }
        val offset = out.size
        out.u16(bytes.size)
        out.bytes(bytes)
        offset
    }

    fun bytes(): ByteArray = out.toByteArray()
}

internal class LittleEndianBuffer {
    private val out = ByteArrayOutputStream()
    val size: Int get() = out.size()

    fun u8(value: Int) = out.write(value and 0xFF)

    fun u16(value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
    }

    fun i32(value: Int) {
        u16(value and 0xFFFF)
        u16(value ushr 16)
    }

    fun u32(value: Long) = i32(value.toInt())

    fun bytes(value: ByteArray) = out.write(value, 0, value.size)

    fun padTo4() {
        while (out.size() % 4 != 0) out.write(0)
    }

    fun toByteArray(): ByteArray = out.toByteArray()
}
