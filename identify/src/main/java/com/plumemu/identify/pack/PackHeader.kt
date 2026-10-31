package com.plumemu.identify.pack

import java.nio.ByteBuffer

/**
 * The header and section table of a pack, checked against the file length. Cheap: reads the
 * first bytes and the section table only, never the records.
 */
internal sealed class PackHeader {
    class Parsed(
        val kind: PackKind,
        val dataVersion: Long,
        val systemId: String,
        val sections: Map<String, IntRange>,
    ) : PackHeader()

    class Invalid(val reason: String) : PackHeader()

    companion object {
        /** [buffer] must be little-endian and hold the whole file. */
        fun parse(buffer: ByteBuffer): PackHeader {
            val length = buffer.limit()
            if (length < PackFormat.HEADER_SIZE + PackFormat.TRAILER_SIZE) return Invalid("too short for a pack ($length bytes)")
            if (PackFormat.MAGIC.indices.any { buffer.get(it) != PackFormat.MAGIC[it] }) return Invalid("not a name pack (bad magic)")
            val version = buffer.getShort(8).toInt() and 0xFFFF
            if (version != PackFormat.FORMAT_VERSION) return Invalid("format version $version is not supported (only 1)")
            val kind = PackKind.fromCode(buffer.get(10).toInt() and 0xFF) ?: return Invalid("unknown pack kind ${buffer.get(10)}")
            val dataVersion = buffer.getInt(12).toLong() and PackWriter.UINT_MAX
            val systemId = readSystemId(buffer) ?: return Invalid("system id is not printable ASCII")
            val sectionCount = buffer.getShort(32).toInt() and 0xFFFF
            val tableEnd = PackFormat.HEADER_SIZE + sectionCount * PackFormat.SECTION_ENTRY_SIZE
            val dataEnd = length - PackFormat.TRAILER_SIZE
            if (tableEnd > dataEnd) return Invalid("section table runs past the end ($sectionCount sections)")
            val sections = LinkedHashMap<String, IntRange>()
            for (i in 0 until sectionCount) {
                val at = PackFormat.HEADER_SIZE + i * PackFormat.SECTION_ENTRY_SIZE
                val tag = String(ByteArray(4) { buffer.get(at + it) }, Charsets.ISO_8859_1)
                val offset = buffer.getInt(at + 4).toLong() and PackWriter.UINT_MAX
                val size = buffer.getInt(at + 8).toLong() and PackWriter.UINT_MAX
                if (offset < tableEnd || offset + size > dataEnd) return Invalid("section $tag lies outside the pack")
                if (tag in sections) return Invalid("section $tag appears twice")
                val record = PackFormat.recordSize(tag)
                if (record != null && size % record != 0L) return Invalid("section $tag is not a whole number of records")
                sections[tag] = offset.toInt() until (offset + size).toInt()
            }
            val missing = PackFormat.requiredSections(kind).filter { it !in sections }
            if (missing.isNotEmpty()) return Invalid("missing sections $missing")
            val sources = sections.getValue(PackFormat.TAG_SOURCES)
            if (sources.last - sources.first + 1 < 4) return Invalid("section SRCS is too short")
            return Parsed(kind, dataVersion, systemId, sections)
        }

        private fun readSystemId(buffer: ByteBuffer): String? {
            val bytes = ByteArray(PackFormat.SYSTEM_ID_BYTES) { buffer.get(16 + it) }
            val end = bytes.indexOf(0).let { if (it < 0) bytes.size else it }
            if (end == 0) return null
            if ((0 until end).any { bytes[it] !in 0x21..0x7E }) return null
            if ((end until bytes.size).any { bytes[it].toInt() != 0 }) return null
            return String(bytes, 0, end, Charsets.US_ASCII)
        }
    }
}
