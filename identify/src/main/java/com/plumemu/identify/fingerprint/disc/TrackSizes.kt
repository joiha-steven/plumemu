package com.plumemu.identify.fingerprint.disc

import com.plumemu.identify.fingerprint.Container
import com.plumemu.identify.fingerprint.extensionOf
import com.plumemu.identify.fingerprint.u64le
import com.plumemu.identify.pack.TrackSizeKeys
import com.plumemu.identify.source.RandomAccessSource
import com.plumemu.identify.source.SiblingOpener
import com.plumemu.identify.source.readFully
import java.io.IOException
import java.util.Locale

/**
 * The track-size keys of a disc ([TrackSizeKeys]), from sizes alone: the files a cue sheet,
 * `.gdi` or playlist names (opened only to ask their length), a CHD's header and metadata
 * entries (no hunk is decoded), a CSO header, the length of an image. Normalised as
 * `identify/PACKS.md`, "Normalising a copy", says, so a copy matches the Redump split-bin dump.
 *
 * Every size list gives two keys, the ordered list and the sum (one size gives the single-track and
 * the multi-track kind), so a CHD or single-bin copy of a multi-track disc still matches.
 */
internal object TrackSizes {
    /** A cue sheet or `.gdi` names at most 99 tracks. */
    private const val MAX_FILES = 99
    private const val SECTOR = 2352L
    private const val COOKED = 2048L

    /** Beyond a CD's capacity (an 80-minute disc plus overburn): a bare image this big is a DVD. */
    private const val CD_MAX_BYTES = 900_000_000L

    /** Systems whose discs are DVD-type or UMD, never a CD: a bare `.iso` is one size. */
    private val DVD_SYSTEMS = setOf("gc", "gamecube", "ngc", "wii", "wiiu", "psp", "playstationportable", "ps3", "xbox360", "triforce")

    /**
     * Systems with CD and DVD discs whose Redump CDs are always bin/cue and DVDs always `.iso`
     * (PS2: 8,606 `.iso` dumps, 116 of them under [CD_MAX_BYTES]; 3,168 bin/cue CDs, none as `.iso`):
     * an image of cooked sectors is a DVD whatever its size. A raw image is still a CD.
     */
    private val COOKED_IS_DVD_SYSTEMS = setOf("ps2", "playstation2")

    /**
     * [TrackKeys] of the disc in [source] (of [container], as `FingerprintReader` found it). A
     * damaged disc gives no keys and a note; a sheet whose track file is missing or empty gives
     * [TrackKeys.incomplete] (null keys: asked again later); an [IOException] passes through.
     */
    @Throws(IOException::class)
    fun read(
        source: RandomAccessSource,
        container: Container,
        systemId: String?,
        siblings: SiblingOpener?,
        chdHeader: ChdHeader? = null,
    ): TrackKeys =
        try {
            compute(source, container, systemId?.trim()?.lowercase(Locale.ROOT), siblings, nested = false, chdHeader)
        } catch (e: Incomplete) {
            TrackKeys.incomplete(e.message)
        } catch (e: NotRead) {
            TrackKeys.none(e.message)
        } catch (e: IOException) {
            throw e
        } catch (e: RuntimeException) {
            TrackKeys.none("track sizes: read failed: ${e.javaClass.simpleName}")
        }

    /** True for the containers a track key can be computed for. */
    fun reads(container: Container): Boolean = container in READ

    private val READ = setOf(Container.CUE, Container.GDI, Container.M3U, Container.CHD, Container.CSO, Container.ISO, Container.BIN_RAW)

    @Throws(IOException::class)
    private fun compute(source: RandomAccessSource, container: Container, system: String?, siblings: SiblingOpener?, nested: Boolean, chdHeader: ChdHeader? = null): TrackKeys =
        when (container) {
            Container.CUE -> fromSheet(source, siblings, "cue", system) { TrackLists.cueTrackFiles(it) }
            Container.GDI -> fromSheet(source, siblings, "gdi", system) { TrackLists.gdiTrackFiles(it) }
            Container.M3U -> fromPlaylist(source, siblings, system, nested)
            Container.CHD -> fromChd(CappedSource(source, ReadCap(DiscReader.DEFAULT_CAP)), chdHeader)
            Container.CSO -> fromCso(CappedSource(source, ReadCap(DiscReader.DEFAULT_CAP)))
            Container.ISO, Container.BIN_RAW -> fromImage(CappedSource(source, ReadCap(DiscReader.DEFAULT_CAP)), system)
            else -> TrackKeys.none()
        }

    /** Cue sheet or `.gdi`: one size per track file in track order; one file is one total. */
    @Throws(IOException::class)
    private fun fromSheet(
        source: RandomAccessSource,
        siblings: SiblingOpener?,
        what: String,
        system: String?,
        files: (String) -> List<SheetFile>?,
    ): TrackKeys {
        val listed = files(readSmallText(source, TrackLists.MAX_LIST_BYTES, what)) ?: throw NotRead("$what: no track files")
        if (listed.size > MAX_FILES) throw NotRead("$what: ${listed.size} track files is too many")
        val opener = siblings ?: throw Incomplete("$what: track files not opened (no sibling opener)")
        // Stops at the first missing or empty file: a broken sheet costs at most one open per track.
        val sizes = listed.map { file ->
            val size = (opener.open(file.name) ?: throw Incomplete("$what: ${file.name} not found, no track key yet")).use { it.size }
            if (size <= 0) throw Incomplete("$what: ${file.name} is empty, no track key yet")
            rawSize(size, file.layout)
        }
        return TrackKeys(keysOf(sizes))
    }

    /** The size of a track file in Redump's raw 2352-byte sectors, whatever its stored sectors are. */
    private fun rawSize(size: Long, layout: SectorLayout?): Long = when (layout) {
        SectorLayout.COOKED_2048 -> if (size % COOKED == 0L) size / COOKED * SECTOR else size
        SectorLayout.MODE2_2336 -> if (size % layout.stride == 0L) size / layout.stride * SECTOR else size
        else -> size
    }

    /** A playlist: the first disc names the game, so its track keys are the playlist's. */
    @Throws(IOException::class)
    private fun fromPlaylist(source: RandomAccessSource, siblings: SiblingOpener?, system: String?, nested: Boolean): TrackKeys {
        if (nested) throw NotRead("m3u: a playlist inside a playlist is not followed")
        val entry = TrackLists.m3uFirstEntry(readSmallText(source, TrackLists.MAX_LIST_BYTES, "m3u")) ?: throw NotRead("m3u: no entry")
        val opener = siblings ?: throw Incomplete("m3u: $entry not opened (no sibling opener)")
        val file = opener.open(entry) ?: throw Incomplete("m3u: $entry not found, no track key yet")
        return file.use {
            val inner = DiscReader.containerOf(it.readFully(0, DiscReader.SIGNATURE_BYTES), extensionOf(entry), systemId = null)
                ?: throw NotRead("m3u: $entry is not a disc image")
            val folder = entry.replace('\\', '/').substringBeforeLast('/', "")
            val innerSiblings = if (folder.isEmpty()) opener else SiblingOpener { name -> opener.open("$folder/$name") }
            compute(it, inner, system, innerSiblings, nested = true)
        }
    }

    /**
     * A CD CHD: the `FRAMES` of each track's metadata x 2352 (never the padded layout). A DVD-type
     * CHD (hunks of 2048-byte sectors): its logical size, which no other disc is told apart by.
     */
    @Throws(IOException::class)
    private fun fromChd(source: RandomAccessSource, known: ChdHeader?): TrackKeys {
        val header = known ?: ChdHeader.read(source)
        if (header.version != 5) throw NotRead("chd: version ${header.version} track list not read")
        if (!header.isCd) return TrackKeys(keysOf(listOf(header.logicalBytes)))
        val frames = ChdTracks.trackFrames(source, header.metaOffset)
        // A CD CHD with no track list at all (not made by chdman) is simply not keyed; a damaged one is a note.
        if (frames.isEmpty()) return TrackKeys.none()
        if (frames.size > MAX_FILES || frames.any { it.frames <= 0 }) throw NotRead("chd: track list is out of range")
        val raw = keysOf(frames.map { it.frames * SECTOR })
        // A DVD copied with `chdman createcd` is one cooked `MODE1` track: Redump's size is frames x 2048.
        val cooked = if (frames.all { it.type == "MODE1" }) keysOf(frames.map { it.frames * COOKED }) else emptyList()
        return TrackKeys(raw + cooked)
    }

    /** A CSO's uncompressed size (header only; the block index is never read). */
    @Throws(IOException::class)
    private fun fromCso(source: RandomAccessSource): TrackKeys {
        val header = source.readFully(0, CSO_HEADER)
        if (header.size < CSO_HEADER) throw NotRead("cso: header is truncated")
        val size = header.u64le(8)
        if (size <= 0) throw NotRead("cso: size is out of range")
        return TrackKeys(keysOf(listOf(size)))
    }

    /**
     * A bare image. Raw 2352-byte sectors: the size. Cooked 2048-byte sectors of a CD system
     * (an `.iso` ripped from a CD, or any image under [CD_MAX_BYTES] unless the system is DVD-type):
     * both its size x 2352 / 2048 and its size. A DVD-type image (any image of a DVD system,
     * a cooked image of a [COOKED_IS_DVD_SYSTEMS] system): its size only, no CD key.
     */
    @Throws(IOException::class)
    private fun fromImage(source: RandomAccessSource, system: String?): TrackKeys {
        val size = source.size
        if (size <= 0) throw NotRead("image: empty")
        if (system in DVD_SYSTEMS) return TrackKeys(keysOf(listOf(size)))
        val layout = SectorImage.detect(source)
        if (layout != SectorLayout.COOKED_2048) {
            val raw = layout.stride.toLong()
            return TrackKeys(keysOf(listOf(if (layout == SectorLayout.RAW_2448) size / raw * SECTOR else size)))
        }
        if (size > CD_MAX_BYTES || system in COOKED_IS_DVD_SYSTEMS) return TrackKeys(keysOf(listOf(size)))
        val asCd = if (size % COOKED == 0L) listOf(rawSize(size, SectorLayout.COOKED_2048)) else null
        return TrackKeys(keysOfLists(listOfNotNull(asCd, listOf(size))))
    }

    /** For each size list its ordered key then its sum's key, without repeats. */
    private fun keysOfLists(lists: List<List<Long>>): List<Long> {
        val out = LinkedHashSet<Long>()
        for (sizes in lists) {
            out += TrackSizeKeys.ordered(sizes)
            out += TrackSizeKeys.total(sizes.sum())
        }
        return out.toList()
    }

    private fun keysOf(sizes: List<Long>): List<Long> = keysOfLists(listOf(sizes))

    private const val CSO_HEADER = 24

    /** A track file the sheet names is not there (yet): no key now, asked again at the next naming. */
    private class Incomplete(message: String) : RuntimeException(message)
}

/**
 * Track-size keys read from a disc: [keys] strongest first; empty when the disc gives none (a
 * damaged or unkeyed image, reason in [note]); null when they are incomplete (a track file missing
 * or unreadable now), so the fingerprint stores none and a later naming asks again. They only look
 * a title up: they are never a fix key or the ScreenScraper key (two games can share a layout).
 */
internal class TrackKeys(val keys: List<Long>?, val note: String? = null) {
    companion object {
        fun none(note: String? = null) = TrackKeys(emptyList(), note = note)

        fun incomplete(note: String?) = TrackKeys(null, note = note)
    }
}
