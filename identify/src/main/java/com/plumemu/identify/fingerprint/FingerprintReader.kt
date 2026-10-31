package com.plumemu.identify.fingerprint

import com.plumemu.identify.fingerprint.cart.CartridgeHeader
import com.plumemu.identify.fingerprint.cart.CartridgeReader
import com.plumemu.identify.fingerprint.cart.CartridgeReaders
import com.plumemu.identify.fingerprint.cart.ContentLayout
import com.plumemu.identify.fingerprint.cart.Nintendo64Reader
import com.plumemu.identify.fingerprint.disc.DiscReader
import com.plumemu.identify.fingerprint.nx.SwitchReader
import com.plumemu.identify.source.RandomAccessSource
import com.plumemu.identify.source.SiblingOpener
import com.plumemu.identify.source.readFully
import java.io.IOException

/**
 * Reads the cheap fingerprints of one file (layer 2 of identification): what container it is, the CRC a zip lists
 * for its ROM, the code and title a cartridge header carries, the serial and title a disc image
 * carries (`disc/DiscReader`), a Switch or 3DS title id (`nx/SwitchReader`, `cart/N3dsReader`),
 * and with [Depth.FULL] the CRC of a small loose file.
 *
 * Holds no state beyond its settings, so one instance serves any number of threads. It does
 * blocking I/O: call it from a background dispatcher, never the main thread.
 *
 * @param wholeFileCrcLimit largest loose file, in bytes, whose CRC [Depth.FULL] computes.
 */
class FingerprintReader(
    private val wholeFileCrcLimit: Long = DEFAULT_WHOLE_FILE_CRC_LIMIT,
) {
    /** How much of a file to read. */
    enum class Depth {
        /** Container, zip central directory and cartridge header only: a few KB per file. */
        QUICK,

        /**
         * QUICK plus the CRC of a loose file up to the size limit, read from end to end, and the
         * header-less CRC of a zipped ROM that may carry a header, unpacked once.
         */
        FULL,
    }

    /**
     * Fingerprints of [source], named [fileName] (its extension; for Switch files also a title id
     * written in the name), believed to be a
     * game of [systemId] (a Plumemu system id from `platforms.json`, or null when unknown).
     *
     * - A zip gives its chosen entry's CRC and size from the central directory; at [Depth.QUICK]
     *   nothing is unpacked, so the header of a zipped ROM is not read and
     *   [Fingerprints.productCode] stays null. That is the trade-off for reading a few KB instead
     *   of the whole archive: the CRC identifies it instead. A zipped ROM that carries a header
     *   (iNES, SNES copier, SMD) has a directory CRC that includes it; a note says so when the
     *   entry's length suggests one. At [Depth.FULL] an entry whose format may carry a header or
     *   a byte order, up to the size limit, is unpacked once for the header-less CRC
     *   ([ZipEntryContent]); the directory CRC stays in [Fingerprints.archiveCrc32].
     * - A 7z gives its chosen file's CRC and size from the archive header (decoded when it is
     *   LZMA- or LZMA2-compressed, as 7-Zip writes it); file data is never decompressed.
     * - A disc image or disc container (CHD, CSO, PBP, WBFS, RVZ, WIA, GCZ by signature; cue
     *   sheet, `.gdi`, `.m3u`, `.iso`, `.gcm` by extension; `.bin` and `.img` when [systemId] is a
     *   disc system or unknown) gets its serial and title from the first few KB, at most 64 KB,
     *   the same at both depths: a disc never gets a whole-file CRC. A CHD also gives its own
     *   SHA-1. A `.iso`/`.bin`/`.img` with no disc structure is read as a loose file.
     * - A loose file gets its cartridge header read (the readers [systemId] and the extension
     *   pick), and with [Depth.FULL] its whole-file CRC when it is at most the size limit.
     * - A Switch file (system `switch`, or `.nsp`/`.nsz`/`.xci`/`.xcz` with no system) gets its
     *   title id from an NSP's file table (ticket and content meta names, at most 64 KB) or from
     *   the file name; the base id is [Fingerprints.productCode] ([CodeKind.SWITCH]).
     *
     * @param siblings opens the files a cue sheet, `.gdi` or `.m3u` names, relative to its folder;
     *   without it those lists give only a note.
     * Bad or unexpected bytes never throw: what could not be read becomes a note and the rest is
     * returned.
     *
     * @throws IOException when [source] itself fails to read.
     */
    @Throws(IOException::class)
    fun read(
        source: RandomAccessSource,
        fileName: String,
        systemId: String?,
        depth: Depth,
        siblings: SiblingOpener? = null,
    ): Fingerprints {
        val start = source.readFully(0, SIGNATURE_BYTES)
        val extension = extensionOf(fileName)
        val container = when {
            start.startsWith(SEVEN_ZIP_SIGNATURE) -> Container.SEVEN_ZIP
            start.startsWith(ZIP_LOCAL_SIGNATURE) || start.startsWith(ZIP_EMPTY_SIGNATURE) || extension == "zip" ->
                Container.ZIP
            else -> DiscReader.containerOf(start, extension, systemId) ?: Container.NONE
        }
        DiscReader.skipNote(extension, systemId)?.let { note ->
            if (container != Container.ZIP && container != Container.SEVEN_ZIP && container != Container.CHD) {
                return Fingerprints(size = source.size, container = container, notes = listOf(note))
            }
        }
        return try {
            when (container) {
                Container.SEVEN_ZIP ->
                    readArchive(source, SevenZipDirectory().read(source), Container.SEVEN_ZIP, systemId, depth)
                Container.ZIP -> readArchive(source, ZipDirectory().read(source), Container.ZIP, systemId, depth)
                Container.NONE ->
                    if (SwitchReader.claims(systemId, extension)) {
                        readSwitch(source, fileName, extension, systemId, depth)
                    } else {
                        readLoose(source, extension, systemId, depth)
                    }
                else -> readDisc(source, container, extension, systemId, depth, siblings)
            }
        } catch (e: IOException) {
            throw e
        } catch (e: RuntimeException) {
            // A reader met bytes it did not expect (a bug or a pathological file): keep going
            // with what is known, and say what happened so the next reader can find it.
            Fingerprints(
                size = source.size,
                container = container,
                notes = listOf("read failed: ${e.javaClass.simpleName}: ${e.message}"),
            )
        }
    }

    /**
     * A zip or 7z: the chosen entry's CRC and size as the archive's directory or header lists
     * them; at [Depth.FULL], for a zip entry whose format may carry a header, the header-less
     * CRC of the unpacked entry ([ZipEntryContent]).
     */
    @Throws(IOException::class)
    private fun readArchive(
        source: RandomAccessSource,
        listing: ZipListing,
        container: Container,
        systemId: String?,
        depth: Depth,
    ): Fingerprints {
        val isZip = container == Container.ZIP
        val label = if (isZip) "zip" else "7z"
        val notes = listing.notes.toMutableList()
        val entry = pickRomEntry(listing.entries)
        if (entry == null) {
            if (listing.entries.isNotEmpty()) notes += "$label: no ROM entry (only directories or text and image files)"
            return Fingerprints(
                size = source.size,
                container = container,
                entryCount = countFiles(listing.entries),
                notes = notes,
            )
        }
        val crc = entry.crc32.takeIf { it != CRC_NOT_LISTED }
        if (crc == null) notes += "$label: the header lists no CRC for ${entry.name}"
        val readers = CartridgeReaders.candidates(systemId, extensionOf(entry.name))
        val content = if (isZip && depth == Depth.FULL && crc != null && readers.any { it.storedFormMayDiffer }) {
            ZipEntryContent.read(source, entry, readers, wholeFileCrcLimit, notes)
        } else {
            null
        }
        val base = Fingerprints(
            size = source.size,
            container = container,
            innerName = entry.name,
            entryCount = countFiles(listing.entries),
            contentSize = entry.uncompressedSize,
            crc32 = crc,
            crcSource = if (crc == null) null else if (isZip) CrcSource.ZIP_DIRECTORY else CrcSource.SEVEN_ZIP_HEADER,
            archiveCrc32 = crc,
        )
        if (content != null && content.storedFormIsContent) return base.copy(notes = notes + content.notes)
        if (content != null) {
            return base.copy(
                contentSize = content.contentSize,
                crc32 = content.crc32,
                crcSource = CrcSource.ZIP_ENTRY_CONTENT,
                headerBytesSkipped = content.headerBytesSkipped,
                notes = notes + content.notes,
            )
        }
        val storedNotes = storedFormNotes(entry, readers)
        if (storedNotes.isNotEmpty() && depth == Depth.FULL && !isZip) {
            notes += "7z: file data is never unpacked, so the header-less CRC is not computed"
        }
        return base.copy(notes = notes + storedNotes)
    }

    /**
     * Notes for an archive entry whose CRC is of the stored form: a length that suggests a
     * header, a byte-swapped N64 extension. Not given once [ZipEntryContent] has opened the
     * entry: it then knows, and only a failed unpacking keeps these doubts.
     */
    private fun storedFormNotes(entry: ZipEntryInfo, readers: List<CartridgeReader>): List<String> {
        val notes = mutableListOf<String>()
        Nintendo64Reader.zipNote(entry.name)?.let { notes += it }
        readers.firstNotNullOfOrNull { reader ->
            reader.likelyHeaderSize(entry.uncompressedSize).takeIf { it > 0 }?.let { reader.name to it }
        }?.let { (name, header) ->
            notes += "$name: entry length suggests a $header-byte header; the directory CRC includes it"
        }
        return notes
    }

    /** A disc: the same few KB at both depths, never a whole-disc CRC. */
    @Throws(IOException::class)
    private fun readDisc(
        source: RandomAccessSource,
        container: Container,
        extension: String,
        systemId: String?,
        depth: Depth,
        siblings: SiblingOpener?,
    ): Fingerprints {
        val discNotes = mutableListOf<String>()
        DiscReader().read(source, container, systemId, siblings, discNotes)?.let { return it }
        // A .iso/.bin/.img with no disc structure: read it as any other loose file. The disc
        // note matters only when the system says it should have been a disc.
        val loose = readLoose(source, extension, systemId, depth)
        return if (systemId == null) loose else loose.copy(notes = discNotes + loose.notes)
    }

    /**
     * A Switch file: its title id from the NSP's file table or the file name ([SwitchReader]),
     * the base id as the product code; at [Depth.FULL] a small file (an `.nro`) still gets its
     * whole-file CRC.
     */
    @Throws(IOException::class)
    private fun readSwitch(
        source: RandomAccessSource,
        fileName: String,
        extension: String,
        systemId: String?,
        depth: Depth,
    ): Fingerprints {
        val ids = SwitchReader.read(source, fileName, extension)
        val loose = readLoose(source, extension, systemId, depth)
        return loose.copy(
            container = ids.container,
            productCode = ids.baseId,
            codeKind = ids.codeKind,
            titleId = ids.titleId,
            titleKind = ids.kind,
            notes = ids.notes + loose.notes,
        )
    }

    @Throws(IOException::class)
    private fun readLoose(source: RandomAccessSource, extension: String, systemId: String?, depth: Depth): Fingerprints {
        val notes = mutableListOf<String>()
        val candidates = CartridgeReaders.candidates(systemId, extension)
        val header: CartridgeHeader? = CartridgeReaders.detect(candidates, source)
        if (header != null) {
            notes += header.notes
        } else if (candidates.isNotEmpty()) {
            notes += "no valid ${candidates.joinToString("/") { it.name }} header"
        }
        val skip = header?.headerBytesSkipped ?: 0
        val layout = header?.layout ?: ContentLayout.PLAIN
        var crc: Long? = null
        if (depth == Depth.FULL) {
            if (source.size <= wholeFileCrcLimit) {
                crc = wholeFileCrc(source, skip, layout)
            } else {
                notes += "whole-file CRC skipped: ${source.size} bytes is over the $wholeFileCrcLimit-byte limit"
            }
        }
        return Fingerprints(
            size = source.size,
            container = Container.NONE,
            contentSize = if (crc != null) (source.size - skip).coerceAtLeast(0) else null,
            crc32 = crc,
            crcSource = if (crc != null) CrcSource.WHOLE_FILE else null,
            headerBytesSkipped = skip,
            productCode = header?.productCode,
            codeKind = header?.codeKind,
            internalTitle = header?.title,
            titleIsCanonical = header?.titleIsCanonical ?: false,
            notes = notes,
            titleId = header?.titleId,
            titleKind = header?.titleKind,
        )
    }

    companion object {
        /**
         * Version of what this reader reads for files of [systemId]: raised for a system when its
         * reader learns to read more, so fingerprints cached by an older version are read again
         * once (library's `FingerprintCodec`), and those of other systems stay cached. 2: Switch
         * title ids and 3DS game codes. 1 for every other system.
         */
        fun readerVersion(systemId: String?): Int =
            READER_VERSIONS[systemId?.trim()?.lowercase(java.util.Locale.ROOT)] ?: 1

        private val READER_VERSIONS: Map<String, Int> = mapOf(
            "switch" to 2, "nintendoswitch" to 2, "nsw" to 2,
            "n3ds" to 2, "3ds" to 2,
        )

        /**
         * The system ids whose files a [Depth.FULL] read gives a content CRC a QUICK read does not:
         * [loose] files of cartridge systems (a whole-file CRC up to the size limit), and [zipped]
         * ones whose zip entry may carry a header or another byte order (the header-less CRC of
         * the unpacked entry; for other systems the directory's CRC already is the content's).
         * Discs, arcade sets and 7z archives gain nothing. For library's content pass.
         */
        fun contentReadSystems(): ContentReadSystems =
            ContentReadSystems(
                loose = CartridgeReaders.systemsWithReaders + CRC_ONLY_CARTRIDGE_SYSTEMS,
                zipped = CartridgeReaders.systemsWhoseStoredFormMayDiffer,
            )

        /**
         * Cartridge systems with no header reader whose bundled name pack is matched by CRC: a loose
         * file is one cartridge image, so its whole-file CRC (plain, [readLoose]) names it. Left out
         * on purpose: discs (psx, saturn, dreamcast, pcenginecd, megacd, neogeocd ...), computers
         * (disk and tape images, programs: c64, msx, amiga, atari800/st ...), carts with a header
         * the plain CRC would not match (c64/vic20 `.crt`), and sets named by zip name (arcade,
         * vc4000). Satellaview, Sufami and Atari 5200 are in: their No-Intro files are headerless,
         * and a file that matches nothing is only marked done. Each id must have a bundled pack (tested).
         */
        val CRC_ONLY_CARTRIDGE_SYSTEMS: Set<String> = setOf(
            "atari2600", "atarijaguar", "channelf", "colecovision", "crvision", "intellivision",
            "arcadia", "odyssey2", "vectrex", "supervision", "virtualboy", "vsmile", "megaduck",
            "pokemini", "ngp", "ngpc", "wonderswan", "wonderswancolor", "pcengine", "supergrafx",
            "mastersystem", "gamegear", "sg-1000", "pico", "videopac", "atari5200", "satellaview", "sufami",
        )

        /** 64 MB: the largest N64 cartridge; larger loose files are discs, read by serial instead. */
        const val DEFAULT_WHOLE_FILE_CRC_LIMIT: Long = 64L * 1024 * 1024

        private const val SIGNATURE_BYTES = 8
        private val ZIP_LOCAL_SIGNATURE = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        private val ZIP_EMPTY_SIGNATURE = byteArrayOf(0x50, 0x4B, 0x05, 0x06)

        // Also in SevenZipDirectory, which checks it again on its own read.
        private val SEVEN_ZIP_SIGNATURE = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C)
    }
}

/** See [FingerprintReader.contentReadSystems]. System ids are lower case, as in `platforms.json`. */
data class ContentReadSystems(val loose: Set<String>, val zipped: Set<String>)
