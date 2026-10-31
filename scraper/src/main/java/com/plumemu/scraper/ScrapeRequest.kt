package com.plumemu.scraper

import java.io.InputStream

/**
 * One game to look up on ScreenScraper.
 *
 * @property systemId Plumemu's system id, e.g. "snes"; mapped by [SystemIds].
 * @property title the clean title from the library; used for the name search.
 * @property regions region preference, most wanted first, e.g. ["us", "wor", "eu", "jp"].
 * @property languages language preference, most wanted first, e.g. ["en"].
 * @property hints what the library already knows about the file (so the ROM is never read a second time).
 *   When given, the scraper never opens [rom]: it sends these values instead of its own hashes.
 * @property fresh the person asked for this game again (Download missing, Re-download): the
 *   scraper skips its 30-day answer cache (and still stores the new answer), and the run
 *   downloads the pictures again, each replacing the old file only once the new one is saved.
 *   One more API call than a cached request; the ROM hash cache is still used.
 * @property hack the file is tagged `[Hack]`: when ScreenScraper has no box art for it, the scraper tries the
 *   parent game's (the title before its subtitle). Derived from the stored file name, not stored.
 * @property steamAppId the Steam app id of a pinned DroidDeck shortcut (`game:<id>`), when the library knows it: the Steam art
 *   fallback uses it before reading a `.steam` file or searching Steam's store by name.
 */
public data class ScrapeRequest(
    val rom: RomSource,
    val systemId: String,
    val title: String,
    val regions: List<String>,
    val languages: List<String>,
    val hints: RomHints? = null,
    val fresh: Boolean = false,
    val hack: Boolean = false,
    val steamAppId: Long? = null,
)

/**
 * Facts about a ROM found by the library's identifier, so the scraper does not read the file again.
 * Any of them may be missing; a blank value counts as missing.
 *
 * @property crc32 the CRC32 as hex (the inner file's for a zip), sent as `crc`.
 * @property serial a disc serial, sent as `serialnum`.
 * @property sizeBytes the size to send as `romtaille`; null sends [RomSource.sizeBytes].
 * @property serialSystemId the Plumemu system whose format [serial] was read from (identify's
 *   `CodeKind.nativeSystemId`: `gb` for a `.gbc` in a `gba/` folder, `snes` for an `.sfc` in
 *   `snesmsu1/`); an answer on that system counts too. Null: only the asked system counts.
 */
public data class RomHints(
    val crc32: String? = null,
    val serial: String? = null,
    val sizeBytes: Long? = null,
    val serialSystemId: String? = null,
)

/**
 * A ROM the scraper can read. The library supplies one per game: a plain file, or (later) a
 * document URI. [key], [sizeBytes] and [lastModifiedMillis] key the hash cache, so an unchanged
 * file is never read twice.
 */
public interface RomSource {
    /** A stable identity: the file path or the document URI string. */
    public val key: String
    public val fileName: String
    public val sizeBytes: Long
    public val lastModifiedMillis: Long

    /** Opens the ROM for reading; the caller closes the stream. */
    public fun open(): InputStream
}
