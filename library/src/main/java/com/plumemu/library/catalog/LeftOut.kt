package com.plumemu.library.catalog

import com.plumemu.library.scan.DocumentEntry

/**
 * Why a file the scan saw is not in the library. Stored by [code] (never rename one: it is in the
 * database). Files that are part of a game without a row of their own (a bin, a later disc, a
 * regional copy shown under another) are in the library and get no reason.
 */
enum class LeftOutReason(val code: String) {
    /** A BIOS or firmware file (`scph1001.bin`, `gba_bios.bin`). */
    BIOS("bios"),

    /** An emulator's save, save state or clock file. */
    SAVE("save"),

    /** Hidden files, notes, documents, pictures, videos, checksums. */
    JUNK("junk"),

    /** A file no emulator of the folder's system opens; with no system, a file no system opens at all. */
    UNMATCHED("unmatched"),

    /** A file several systems open, in a folder that names no system (the picked folder itself, an unknown or ignored folder). */
    NO_SYSTEM("no_system"),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }

        /** The reason stored as [code], or null for a code this build does not know. */
        fun of(code: String): LeftOutReason? = byCode[code]
    }
}

/** A file of one directory the catalogue left out, with its reason and the system of its folder (null when it names none). */
data class LeftOutFile(
    val entry: DocumentEntry,
    val reason: LeftOutReason,
    val systemId: String?,
)
