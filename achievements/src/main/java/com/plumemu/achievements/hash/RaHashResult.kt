package com.plumemu.achievements.hash

import java.io.IOException
import java.io.InputStream

/**
 * A ROM file as the hasher reads it. Same shape as the scraper's `RomSource`, so `app` can adapt
 * one to the other without this module depending on the scraper.
 */
public interface RaRomSource {
    /**
     * The file path, or the document URI string. Arcade hashing reads its parent folder name
     * (`.../nes/game.zip`), so give a real path when there is one.
     */
    public val key: String
    public val fileName: String

    /** The exact size in bytes; 0 means an empty file. */
    public val sizeBytes: Long

    /** Opens the ROM for reading; the hasher closes the stream. May be called several times. */
    @Throws(IOException::class)
    public fun open(): InputStream

    /**
     * The file called [name] in the same folder as this one, or null when there is none. Disc images
     * need it: a `.cue` names its `.bin` files and an `.m3u` names its discs. [name] is a relative
     * path with `/` separators (a cue may say `Tracks/track01.bin`); an implementation over plain
     * paths resolves it against the parent folder, one over document trees walks the folders, and
     * both should fall back to a case-insensitive match because cue sheets often get the case wrong.
     * The hasher never calls this with an absolute path, a drive letter or a `..` segment; those
     * names are refused before the call, so an implementation need not guard against them.
     */
    public fun sibling(name: String): RaRomSource? = null
}

/** What hashing one ROM gave. */
public sealed interface RaHashResult {
    /** [md5]: 32 lowercase hex digits, the hash RetroAchievements lists the game under. */
    public data class Hashed(val md5: String) : RaHashResult

    /** The file is readable but cannot be given a hash; [reason] says why, [detail] is for the log. */
    public data class CannotHash(
        val reason: RaCannotHashReason,
        val detail: String? = null,
    ) : RaHashResult

    /** Reading the file failed. */
    public data class Unreadable(val cause: IOException) : RaHashResult
}

public enum class RaCannotHashReason {
    /** RetroAchievements has no hash rule for this console id. */
    UnsupportedFormat,

    /** A compressed container (7z, rar...) that Android has no decoder for. */
    NeedsDecoder,

    /** A rule RetroAchievements has that this code does not have yet (some disc systems, 3DS, MS-DOS). */
    NotYetSupported,

    /** The bytes are not a ROM of the console (bad magic, header cut short). */
    NotARom,

    /** A zero-byte file. */
    Empty,

    /** A `.zip` that cannot be opened. */
    BadArchive,

    /** A `.zip` with no entry that looks like a ROM. */
    NoRomInArchive,

    /** A `.cue` or `.m3u` names a file that is not next to it. */
    MissingFile,

    /** A disc image whose container is damaged (bad `.cso` header or block). */
    BadImage,
}
