package com.plumemu.identify

import com.plumemu.identify.fingerprint.FingerprintReader
import com.plumemu.identify.fingerprint.Fingerprints
import com.plumemu.identify.source.RandomAccessSource
import com.plumemu.identify.source.SiblingOpener

/**
 * One file to identify.
 *
 * @property systemId Plumemu system id (`platforms.json`), e.g. "megadrive" or "mame".
 * @property fileName the file's name as the person sees it; its extension picks readers, its
 *   base name is the arcade short name and the last fallback title.
 * @property cleanedTitle the title `library`'s `TitleCleaner` made from the file name, or null.
 * @property regionsFromName regions the file name carries, spelled as in
 *   [com.plumemu.identify.pack.PackMasks.REGIONS] ("USA", "Europe", ...; compared ignoring
 *   case); used to pick between releases that share a CRC or a code.
 * @property source the file's bytes; null when only the name is known (nothing is read).
 * @property siblings opens the files a `.cue`, `.gdi` or `.m3u` names; without it those sheets
 *   give only a note.
 * @property depth how much of the file to read ([FingerprintReader.Depth]).
 * @property known fingerprints the scan cached from an earlier read; when given, nothing is read.
 */
data class IdentifyRequest(
    val systemId: String,
    val fileName: String,
    val cleanedTitle: String?,
    val regionsFromName: List<String> = emptyList(),
    val source: RandomAccessSource? = null,
    val siblings: SiblingOpener? = null,
    val depth: FingerprintReader.Depth = FingerprintReader.Depth.QUICK,
    val known: Fingerprints? = null,
)

/** How sure the name is (exact, likely, unknown). */
enum class Confidence {
    /** A fingerprint, a product code, a canonical header title or an arcade short name. */
    EXACT,

    /** A name match against the system's titles. */
    LIKELY,

    /** Only the cleaned file name. */
    UNKNOWN,
}

/** Which layer gave the name, for the game's Info screen. */
enum class Method { ARCADE_NAME, CRC, PRODUCT_CODE, HEADER_TITLE, NAME_MATCH, FILE_NAME }

/**
 * What ScreenScraper needs so it does not read the file again.
 *
 * @property crc32 CRC-32 of the ROM content (a zip's entry, or the loose file without its header).
 * @property size length of the bytes [crc32] covers.
 * @property fileSize length of the file as found (the archive, for a zip).
 * @property serial the cartridge code or disc serial.
 * @property romFileName the file name to send as the ROM name.
 * @property canonicalTitle the identified title, null when only the file name was used.
 */
data class ScrapeHints(
    val crc32: Long?,
    val size: Long?,
    val fileSize: Long?,
    val serial: String?,
    val romFileName: String,
    val canonicalTitle: String?,
    val systemId: String,
)

/**
 * The name the identifier gives one file.
 *
 * @property regions region names of the matched release, or the ones from the file name when no
 *   release was matched; for an arcade set, the ones its description names (none for a set the
 *   pack does not list).
 * @property variant "Rev 1", "Beta", ... of the matched release.
 * @property publisher the publisher, or the maker of an arcade set.
 * @property hide true for arcade BIOS, device and other non-game sets.
 * @property parent the parent short name of an arcade clone.
 * @property fingerprints what was read (or passed in [IdentifyRequest.known]); the scan caches it.
 * @property fixKeys keys a person's name fix is stored under, strongest first.
 * @property packVersion data version of the name pack this answer was looked up in; null when no
 *   pack was used (the system has none, or its pack failed a lookup in this call). A caller that
 *   expected a pack should not store a null-version answer as final.
 * @property fullTitle an arcade set's full description ("Zork Fighters (Japan 961201)") when
 *   [title] is its short title ([com.plumemu.identify.arcade.ArcadeTitles]); null otherwise.
 *   library uses it to tell two sets of one folder apart; it is not stored (the app's Info looks
 *   the set up in the pack again by its short name). Also given as [ScrapeHints.canonicalTitle]
 *   (nothing reads that yet).
 */
data class Identification(
    val title: String,
    val regions: List<String>,
    val variant: String?,
    val year: Int?,
    val publisher: String?,
    val confidence: Confidence,
    val method: Method,
    val hide: Boolean,
    val parent: String?,
    val fingerprints: Fingerprints?,
    val fixKeys: List<String>,
    val scrapeHints: ScrapeHints,
    val packVersion: Long? = null,
    val fullTitle: String? = null,
)

/**
 * Whose name wins when several are known for one game, weakest
 * first: a ScreenScraper exact match upgrades a likely or unknown name, never an exact identifier
 * match; the game the person picked on ScreenScraper ("Fix box art...") names it over
 * every automatic name; and nothing overrides the person's own fix (Fix name..., Use my own name).
 *
 * Stored by name in `game.name_trust`, so a value is never renamed; a build that does not know a
 * stored name reads it as [FILE_NAME].
 */
enum class NameTrust {
    FILE_NAME,
    SCRAPER_LIKELY,
    IDENTIFIER_LIKELY,
    SCRAPER_EXACT,
    IDENTIFIER_EXACT,
    SCRAPER_PERSON_CHOICE,
    PERSON_FIX,
    ;

    companion object {
        /** The trust of an identifier result. */
        fun of(identification: Identification): NameTrust = when (identification.confidence) {
            Confidence.EXACT -> IDENTIFIER_EXACT
            Confidence.LIKELY -> IDENTIFIER_LIKELY
            Confidence.UNKNOWN -> FILE_NAME
        }

        /** The stronger of [current] and [candidate]; on a tie [current] stays, so names do not flip. */
        fun winner(current: NameTrust, candidate: NameTrust): NameTrust = if (candidate > current) candidate else current
    }
}
