package com.plumemu.identify.pack

/**
 * Constants of name pack format v1, documented byte by byte in `identify/PACKS.md`. The Python
 * pack tool writes the same bytes; change both or neither.
 */
internal object PackFormat {
    const val FORMAT_VERSION = 1
    const val HEADER_SIZE = 36
    const val SECTION_ENTRY_SIZE = 12
    const val TRAILER_SIZE = 32
    const val SYSTEM_ID_BYTES = 16

    /** "None" for a string reference or a source index: all bits set. */
    const val NO_REF: Int = -1
    const val NO_SOURCE: Int = 0xFF

    /** A CRC entry whose content size was unknown or did not fit 32 bits; matches any size. */
    const val ANY_SIZE: Long = (1L shl 32) - 1

    const val TITLE_RECORD = 20
    const val RELEASE_RECORD = 20
    const val CRC_RECORD = 12
    const val CODE_RECORD = 12
    const val SOURCE_RECORD = 8
    const val ARCADE_RECORD = 24

    const val TAG_STRINGS = "STRS"
    const val TAG_SOURCES = "SRCS"
    const val TAG_TITLES = "TITL"
    const val TAG_RELEASES = "RELS"
    const val TAG_CRCS = "CRCS"
    const val TAG_CODES = "CODE"
    const val TAG_ARCADE = "ARCD"

    /** Largest string in the pool: its length is a u16. */
    const val MAX_STRING_BYTES = 0xFFFF

    val MAGIC: ByteArray = byteArrayOf(0x50, 0x4C, 0x4D, 0x50, 0x41, 0x43, 0x4B, 0x00) // "PLMPACK\0"

    fun requiredSections(kind: PackKind): List<String> = when (kind) {
        PackKind.CONSOLE -> listOf(TAG_STRINGS, TAG_SOURCES, TAG_TITLES, TAG_RELEASES, TAG_CRCS, TAG_CODES)
        PackKind.ARCADE -> listOf(TAG_STRINGS, TAG_SOURCES, TAG_ARCADE)
    }

    fun recordSize(tag: String): Int? = when (tag) {
        TAG_TITLES -> TITLE_RECORD
        TAG_RELEASES -> RELEASE_RECORD
        TAG_CRCS -> CRC_RECORD
        TAG_CODES -> CODE_RECORD
        TAG_ARCADE -> ARCADE_RECORD
        else -> null
    }
}

/** What a pack holds. The numbers are the `kind` byte of the header. */
enum class PackKind(internal val code: Int) {
    /** Releases of a console system: titles, regions, CRCs and product codes. */
    CONSOLE(1),

    /** Arcade sets by short name, with parents, clones, BIOS and device flags. */
    ARCADE(2),
    ;

    internal companion object {
        fun fromCode(code: Int): PackKind? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Region and language bits of a release. The lists are append-only: a bit keeps its meaning in
 * every pack ever written. Region names are spelled as No-Intro and Redump write them, the same
 * spelling `library`'s `Region.parseGroup` reads, so callers map them without a second enum here.
 */
object PackMasks {
    /** Bit n of a region mask is `REGIONS[n]`. The first 21 are the regions `library` knows. */
    val REGIONS: List<String> = listOf(
        "USA", "Europe", "Japan", "World", "Korea", "China", "Taiwan", "Hong Kong", "Asia", "Brazil",
        "Australia", "Canada", "UK", "France", "Germany", "Spain", "Italy", "Netherlands", "Sweden",
        "Scandinavia", "Russia",
        "Belgium", "Denmark", "Finland", "Greece", "India", "Latin America", "Mexico", "New Zealand",
        "Norway", "Poland", "Portugal",
    )

    /** Bit n of a language mask is `LANGUAGES[n]`, No-Intro's two-letter codes. */
    val LANGUAGES: List<String> = listOf(
        "En", "Ja", "Fr", "De", "Es", "It", "Nl", "Pt", "Sv", "No", "Da", "Fi", "Zh", "Ko", "Pl", "Ru",
        "El", "Ca", "Hu", "Cs", "Tr", "Ar", "He", "Hr", "Ro", "Sk", "Sl", "Uk", "Bg", "Id", "Th", "Vi",
    )

    /** The mask of [names] (case-insensitive) and the names that have no bit. */
    fun encode(names: Collection<String>, list: List<String>): Pair<Int, List<String>> {
        var mask = 0
        val unknown = ArrayList<String>()
        for (name in names) {
            val bit = list.indexOfFirst { it.equals(name.trim(), ignoreCase = true) }
            if (bit < 0) unknown += name else mask = mask or (1 shl bit)
        }
        return mask to unknown
    }

    /** The names of the bits set in [mask], in list order. */
    fun decode(mask: Int, list: List<String>): List<String> {
        if (mask == 0) return emptyList()
        val out = ArrayList<String>(Integer.bitCount(mask))
        for (bit in list.indices) if (mask and (1 shl bit) != 0) out += list[bit]
        return out
    }
}
