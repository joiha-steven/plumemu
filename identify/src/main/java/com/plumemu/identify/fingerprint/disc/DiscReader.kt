package com.plumemu.identify.fingerprint.disc

import com.plumemu.identify.fingerprint.Container
import com.plumemu.identify.fingerprint.Fingerprints
import com.plumemu.identify.fingerprint.extensionOf
import com.plumemu.identify.source.RandomAccessSource
import com.plumemu.identify.source.SiblingOpener
import com.plumemu.identify.source.readFully
import java.io.IOException
import java.util.Locale

/**
 * Reads the serial and title a disc carries, from the first few KB of its image, through the
 * containers people store discs in (a disc is never read whole). Every read of one
 * disc, sibling files included, is capped at [capBytes]; what could not be read is a note.
 *
 * | Container | How it is found | What is read |
 * |---|---|---|
 * | CHD v5 | "MComprHD" | header SHA-1, map, the hunks holding the sectors needed (stored, `zlib`, `cdzl`, `lzma`, `cdlz`) |
 * | CSO v1 / ZSO | "CISO" / "ZISO" | v1: index and the deflate blocks needed; v2 and ZSO: a note |
 * | PBP | "\0PBP" | `PARAM.SFO` |
 * | WBFS, RVZ, WIA, GCZ | magic at 0 | the copy of the GameCube / Wii disc header |
 * | cue sheet, `.gdi`, `.m3u` | extension | the list, then the data track or first entry via the [SiblingOpener] |
 * | `.iso`, `.gcm` | extension | GameCube / Wii header, or 2048-byte sectors |
 * | `.bin`, `.img` | extension, when the system is a disc system or unknown | as `.iso`, raw 2352-byte sectors when sector 0 starts with a sync |
 *
 * Inside a sector image: a Sega system area at sector 0 (Saturn, Sega CD, Dreamcast), else ISO
 * 9660 with `SYSTEM.CNF` (PS1, PS2) or `UMD_DATA.BIN` and `PSP_GAME/PARAM.SFO` (PSP).
 */
internal class DiscReader(private val capBytes: Long = DEFAULT_CAP) {

    /** What one disc read found so far; filled step by step so a later failure keeps it. */
    private class Found(var container: Container, val system: String?) {
        var id: DiscId? = null
        var chdSha1: String? = null
        var innerName: String? = null
        var recognised = false
        val notes = ArrayList<String>()
    }

    /**
     * Fingerprints of the disc in [source], whose [container] came from [containerOf]; null
     * when a `.iso`/`.bin`/`.img` turned out to hold no disc structure at all, so the caller
     * can read it as a loose file. [notesIfNone] receives the notes in that case.
     *
     * @throws IOException when [source] or a sibling fails to read.
     */
    @Throws(IOException::class)
    fun read(
        source: RandomAccessSource,
        container: Container,
        systemId: String?,
        siblings: SiblingOpener?,
        notesIfNone: MutableList<String>,
    ): Fingerprints? {
        val system = systemId?.trim()?.lowercase(Locale.ROOT)
        val found = Found(container, system)
        val cap = ReadCap(capBytes)
        val skip = systemSkipNote(system)
        if (skip == null) {
            readInto(found, CappedSource(source, cap), cap, siblings, nested = false)
        } else {
            // No serial to read on this system's discs, but a CHD's own SHA-1 is still a key.
            found.notes += skip
            if (container == Container.CHD) guarded(found) { found.chdSha1 = ChdHeader.read(CappedSource(source, cap)).rawSha1 }
        }
        if (!found.recognised && (container == Container.ISO || container == Container.BIN_RAW)) {
            notesIfNone += found.notes
            return null
        }
        val id = found.id
        return Fingerprints(
            size = source.size,
            container = found.container,
            innerName = found.innerName,
            productCode = id?.productCode,
            codeKind = id?.codeKind,
            internalTitle = id?.title,
            notes = found.notes,
            titleIsCanonical = id?.titleIsCanonical == true && id.title != null,
            chdSha1 = found.chdSha1,
        )
    }

    /**
     * Runs [step], turning [NotRead] and any other RuntimeException (a bug or a pathological
     * file) into a note while keeping what [found] already holds (container, CHD SHA-1).
     * IOException passes through.
     */
    @Throws(IOException::class)
    private inline fun guarded(found: Found, step: () -> Unit) {
        try {
            step()
        } catch (e: NotRead) {
            found.notes += e.message ?: "disc: not read"
        } catch (e: IOException) {
            throw e
        } catch (e: RuntimeException) {
            found.notes += "disc: read failed: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    @Throws(IOException::class)
    private fun readInto(found: Found, source: RandomAccessSource, cap: ReadCap, siblings: SiblingOpener?, nested: Boolean) {
        guarded(found) {
            when (found.container) {
                Container.CHD -> readChd(found, source)
                Container.CSO -> readImage(found, SectorImage(CsoSource.open(source), SectorLayout.COOKED_2048))
                Container.ZSO -> found.notes += "zso: blocks are LZ4, not read (no LZ4 decoder)"
                Container.PBP -> found.id = PlayStationDiscs.fromPbp(source, found.notes)
                Container.WBFS, Container.RVZ, Container.WIA, Container.GCZ ->
                    found.id = NintendoDiscs.fromContainer(source, found.container)
                Container.CUE, Container.GDI -> readTrackList(found, source, cap, siblings)
                Container.M3U -> readPlaylist(found, source, cap, siblings, nested)
                else -> readPlain(found, source)
            }
        }
    }

    @Throws(IOException::class)
    private fun readPlain(found: Found, source: RandomAccessSource) {
        val layout = SectorImage.detect(source)
        if (layout == SectorLayout.COOKED_2048) {
            NintendoDiscs.fromHeader(source.readFully(0, NintendoDiscs.HEADER))?.let {
                found.recognised = true
                found.id = it
                return
            }
        }
        found.container = if (layout == SectorLayout.COOKED_2048) Container.ISO else Container.BIN_RAW
        readImage(found, SectorImage(source, layout))
    }

    /** Sega system area at sector 0, else ISO 9660 PlayStation files. */
    @Throws(IOException::class)
    private fun readImage(found: Found, image: SectorImage) {
        SegaDiscs.fromSystemArea(image.read(0, SegaDiscs.SYSTEM_AREA))?.let {
            found.recognised = true
            found.id = it
            return
        }
        val iso = Iso9660.open(image) ?: throw NotRead("disc: no ISO 9660 volume and no Sega system area")
        found.recognised = true
        found.id = PlayStationDiscs.fromIso(iso, found.notes)
    }

    @Throws(IOException::class)
    private fun readChd(found: Found, source: RandomAccessSource) {
        val header = ChdHeader.read(source)
        found.chdSha1 = header.rawSha1
        if (header.version != 5) throw NotRead("chd: version ${header.version} hunks not read")
        val chd = ChdSource(source, header)
        if (!header.isCd) {
            readImage(found, SectorImage(chd, SectorLayout.COOKED_2048))
            return
        }
        val track = ChdTracks.dataTrack(source, header.metaOffset)
        val base = (track?.frame ?: 0L) * ChdHeader.CD_FRAME
        val cooked = ChdTracks.cookedDataOffset(track?.type ?: "")
        readImage(found, SectorImage(chd, SectorLayout.RAW_2448, base, cooked))
    }

    @Throws(IOException::class)
    private fun readTrackList(found: Found, source: RandomAccessSource, cap: ReadCap, siblings: SiblingOpener?) {
        val cue = found.container == Container.CUE
        val what = if (cue) "cue" else "gdi"
        val text = readSmallText(source, TrackLists.MAX_LIST_BYTES, what)
        val track = (if (cue) TrackLists.cueDataTrack(text, dreamcast = found.system == "dreamcast") else TrackLists.gdiDataTrack(text))
            ?: throw NotRead(if (cue) "cue: no data track" else "gdi: no data track 3")
        found.innerName = track.fileName
        val opener = siblings ?: throw NotRead("$what: ${track.fileName} not opened (no sibling opener)")
        val file = opener.open(track.fileName) ?: throw NotRead("$what: ${track.fileName} not found")
        file.use {
            // Sectors before the track in its file are taken to have the track's size.
            val image = SectorImage(CappedSource(it, cap), track.layout, track.firstSector * track.layout.stride)
            readImage(found, image)
        }
    }

    @Throws(IOException::class)
    private fun readPlaylist(found: Found, source: RandomAccessSource, cap: ReadCap, siblings: SiblingOpener?, nested: Boolean) {
        if (nested) throw NotRead("m3u: a playlist inside a playlist is not followed")
        val entry = TrackLists.m3uFirstEntry(readSmallText(source, TrackLists.MAX_LIST_BYTES, "m3u"))
            ?: throw NotRead("m3u: no entry")
        found.innerName = entry
        val opener = siblings ?: throw NotRead("m3u: $entry not opened (no sibling opener)")
        val file = opener.open(entry) ?: throw NotRead("m3u: $entry not found")
        file.use {
            val capped = CappedSource(it, cap)
            val inner = containerOf(capped.readFully(0, SIGNATURE_BYTES), extensionOf(entry), systemId = null)
                ?: throw NotRead("m3u: $entry is not a disc image")
            if (inner == Container.M3U) throw NotRead("m3u: a playlist inside a playlist is not followed")
            val folder = entry.replace('\\', '/').substringBeforeLast('/', "")
            val innerSiblings = if (folder.isEmpty()) opener else SiblingOpener { name -> opener.open("$folder/$name") }
            val innerFound = Found(inner, found.system)
            readInto(innerFound, capped, cap, innerSiblings, nested = true)
            found.id = innerFound.id
            found.chdSha1 = innerFound.chdSha1
            found.recognised = true
            found.notes += innerFound.notes
        }
    }

    companion object {
        /** 64 KB per disc, sibling files included. */
        const val DEFAULT_CAP: Long = 64L * 1024
        const val SIGNATURE_BYTES = 8

        /**
         * Plumemu system ids and folder names (`platforms/src/main/assets/platforms.json`) whose
         * `.bin` / `.img` files are disc images.
         */
        private val DISC_SYSTEMS = setOf(
            "psx", "ps1", "playstation", "psone", "ps2", "playstation2", "psp", "playstationportable",
            "gc", "gamecube", "ngc", "wii", "saturn", "saturnjp", "megacd", "megacdjp", "segacd", "dreamcast",
        )

        /** Disc systems (ids and folder names) whose discs carry no serial this module reads. */
        private val UNREAD_SYSTEMS = setOf("pcenginecd", "tg-cd", "tgcd", "3do", "neogeocd", "neogeocdjp")

        /**
         * The disc container of a file from its first [SIGNATURE_BYTES] bytes, then its
         * extension, then [systemId]; null when it is not a disc this module reads.
         */
        fun containerOf(start: ByteArray, extension: String, systemId: String?): Container? {
            if (ChdHeader.isChd(start)) return Container.CHD
            CsoSource.containerOf(start)?.let { return it }
            if (PlayStationDiscs.isPbp(start)) return Container.PBP
            NintendoDiscs.containerOf(start)?.let { return it }
            val system = systemId?.trim()?.lowercase(Locale.ROOT)
            return when (extension.lowercase(Locale.ROOT)) {
                "cue" -> Container.CUE
                "gdi" -> Container.GDI
                "m3u" -> Container.M3U
                "iso", "gcm" -> Container.ISO
                "bin", "img" -> Container.ISO.takeIf { system == null || system in DISC_SYSTEMS }
                else -> null
            }
        }

        /**
         * Why a file of [systemId] or [extension] gets no disc read at all, or null. A CHD of a
         * system without serials is still read for its header SHA-1 ([read] does that).
         */
        fun skipNote(extension: String, systemId: String?): String? = when {
            extension.lowercase(Locale.ROOT) == "cdi" -> "cdi: DiscJuggler image not read (track layout not supported)"
            else -> systemSkipNote(systemId?.trim()?.lowercase(Locale.ROOT))
        }

        private fun systemSkipNote(system: String?): String? =
            if (system in UNREAD_SYSTEMS) "$system: disc serial not read (no serial this module knows on the disc)" else null
    }
}
