// Kotlin rewrite of rcheevos' default CD reader (src/hash/rc_cdreader.c). rcheevos: MIT licence.
package com.plumemu.achievements.hash

import java.io.IOException

/** Which track to open, as rcheevos' `RC_HASH_CDTRACK_*` (a positive number is that track). */
internal object DiscTracks {
    const val FIRST_DATA: Int = -1
    const val LAST: Int = -2
    const val LARGEST: Int = -3
    const val FIRST_OF_SECOND_SESSION: Int = -4
}

/**
 * One track of a disc, read by absolute sector number (the number the ISO 9660 directory uses).
 * [trackFirstSector] is the first sector the track holds including its pregap.
 */
internal class DiscTrack(
    private val file: DiscFile,
    private val sectorSize: Int,
    private val sectorHeaderSize: Int,
    private val rawDataSize: Int,
    private val fileTrackOffset: Long,
    private val trackFirstSector: Int,
    private val pregapSectors: Int,
    private val check: () -> Unit = {},
) : AutoCloseable {
    /** The first sector of the data itself (after the pregap). */
    val firstSector: Int get() = trackFirstSector + pregapSectors

    /** Reads [requested] bytes from [sector] on, continuing into the next sectors; returns how many were read. */
    @Throws(IOException::class)
    fun readSector(sector: Long, buffer: ByteArray, requested: Int): Int {
        check() // a scan of a long directory reads many sectors; cancellation must be able to stop it
        if (sector < trackFirstSector) return 0
        var start = (sector - trackFirstSector) * sectorSize + sectorHeaderSize + fileTrackOffset
        var total = 0
        var left = requested
        while (left > rawDataSize) {
            val read = file.readAt(start, buffer, total, rawDataSize)
            total += read
            if (read < rawDataSize) return total
            start += sectorSize
            left -= rawDataSize
        }
        return total + file.readAt(start, buffer, total, left)
    }

    override fun close() = file.close()
}

/** Opens tracks of a `.cue` or `.gdi` + files, a `.cso`, or a bare `.iso`/`.bin` image. */
internal object DiscReader {
    private const val MAX_CUE_BYTES = 1 shl 20
    private val SYNC = byteArrayOf(0, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0)

    /**
     * Opens [track] of [rom]. A `.cue` is read for its tracks and files (found through
     * [RaRomSource.sibling]); `.cso` is unpacked on the fly; anything else is one track.
     * @throws DiscFormatException when the image cannot be used, [IOException] when reading fails.
     */
    @Throws(IOException::class, DiscFormatException::class)
    fun openTrack(rom: RaRomSource, track: Int, check: () -> Unit): DiscTrack {
        val extension = rom.fileName.extensionLowercase()
        return when (extension) {
            "cue" -> openCueTrack(rom, track, check)
            "gdi" -> openGdiTrack(rom, track, check)
            "cso", "ciso", "zso" -> openBinTrack(CsoDiscFile.open(rom, check), track, check)
            "chd" -> openChdTrack(rom, track, check)
            else -> openBinTrack(RawDiscFile(rom, check), track, check)
        }
    }

    /** A `.chd`: a CD image gives the track asked for; any other (DVD, plain disk) is one flat image, like an `.iso`. */
    private fun openChdTrack(rom: RaRomSource, track: Int, check: () -> Unit): DiscTrack {
        val image = ChdImage.open(rom, check)
        try {
            val tracks = image.cdTracks
            val file: DiscFile = if (tracks == null) ChdFlatFile(image) else ChdTrackFile(image, chooseChdTrack(tracks, track))
            if (tracks == null && track > 1) throw DiscFormatException(RaCannotHashReason.NotARom, "Track $track needs a CD image")
            return openBinTrack(file, DiscTracks.FIRST_DATA, check)
        } catch (e: Throwable) {
            image.close()
            throw e
        }
    }

    private fun chooseChdTrack(tracks: List<ChdCdTrack>, wanted: Int): ChdCdTrack {
        val data = tracks.filter { !it.isAudio }
        val chosen = when (wanted) {
            DiscTracks.FIRST_DATA -> data.firstOrNull()
            DiscTracks.LAST -> tracks.lastOrNull()
            DiscTracks.LARGEST -> data.maxByOrNull { it.frames }
            DiscTracks.FIRST_OF_SECOND_SESSION -> null
            else -> tracks.firstOrNull { it.number == wanted }
        }
        return chosen ?: throw DiscFormatException(RaCannotHashReason.NotARom, "The .chd has no track $wanted")
    }

    private fun openBinTrack(file: DiscFile, track: Int, check: () -> Unit): DiscTrack {
        try {
            // A bare image is one track, so "the first data track", "the last" and "the largest" are all it.
            val onlyTrack = track == 1 || track == DiscTracks.FIRST_DATA || track == DiscTracks.LAST || track == DiscTracks.LARGEST
            if (!onlyTrack) throw DiscFormatException(RaCannotHashReason.NotARom, "Track $track needs a .cue sheet")
            val probe = probeSectorSize(file, 0, 0)
            val layout = probe ?: when {
                file.size % 2352 == 0L -> Layout(2352, 24, 0)
                file.size % 2048 == 0L -> Layout(2048, 0, 0)
                file.size % 2336 == 0L -> Layout(2336, 8, 0)
                else -> throw DiscFormatException(RaCannotHashReason.NotARom, "Could not tell the sector size of this disc image")
            }
            return DiscTrack(file, layout.sectorSize, layout.headerSize, 2048, 0, layout.firstSector, 0, check)
        } catch (e: Throwable) {
            file.close()
            throw e
        }
    }

    private class Layout(val sectorSize: Int, val headerSize: Int, val firstSector: Int)

    /**
     * rcheevos' `cdreader_determine_sector_size`: the cue sheet may lie, so look for the sync pattern
     * at each sector size and for "CD001" (always in sector 16). Null when none of the three fits.
     */
    private fun probeSectorSize(file: DiscFile, pregap: Int, fileTrackOffset: Long): Layout? {
        val tocSector = 16 + pregap
        val header = ByteArray(32)
        if (file.readAt(tocSector * 2352L + fileTrackOffset, header, 0, 32) < 32) return null
        rawLayout(header, 2352, tocSector)?.let { return it }
        header.fill(0)
        file.readAt(tocSector * 2336L + fileTrackOffset, header, 0, 32)
        rawLayout(header, 2336, tocSector)?.let { return it }
        header.fill(0)
        file.readAt(tocSector * 2048L + fileTrackOffset, header, 0, 32)
        if (String(header, 1, 5, Charsets.ISO_8859_1) == "CD001") return Layout(2048, 0, 0)
        return null
    }

    private fun rawLayout(header: ByteArray, sectorSize: Int, tocSector: Int): Layout? {
        if (!header.copyOfRange(0, 12).contentEquals(SYNC)) return null
        val headerSize = if (String(header, 25, 5, Charsets.ISO_8859_1) == "CD001") 24 else 16
        return Layout(sectorSize, headerSize, sectorOfHeader(header) - tocSector)
    }

    /** The sector an MSF stamp in a raw sector header names; 150 frames (2 s) lead-in come off. */
    private fun sectorOfHeader(header: ByteArray): Int {
        fun bcd(b: Byte): Int = ((b.toInt() and 0xFF) shr 4) * 10 + (b.toInt() and 0x0F)
        return ((bcd(header[12]) * 60) + bcd(header[13])) * 75 + bcd(header[14]) - 150
    }

    // --- .cue

    private class CueTrack {
        var id = 0
        var sectorSize = 0
        var sectorCount = 0
        var firstSector = 0
        var pregapSectors = 0
        var isData = false
        var fileTrackOffset = 0L
        var fileFirstSector = 0
        var mode = ""
        var filename = ""

        fun copyFrom(o: CueTrack) {
            id = o.id; sectorSize = o.sectorSize; sectorCount = o.sectorCount; firstSector = o.firstSector
            pregapSectors = o.pregapSectors; isData = o.isData; fileTrackOffset = o.fileTrackOffset
            fileFirstSector = o.fileFirstSector; mode = o.mode; filename = o.filename
        }

        fun reset() = copyFrom(CueTrack())
    }

    /** What the cue sheet says about one track, plus the bin file that holds it. */
    private fun openCueTrack(cue: RaRomSource, wanted: Int, check: () -> Unit): DiscTrack {
        val text = readCue(cue)
        val current = CueTrack()
        val previous = CueTrack()
        val largest = CueTrack()
        var track = wanted
        var session = 1
        var done = false

        fun binSectors(c: CueTrack): Int = ((cue.safeSibling(c.filename)?.sizeBytes ?: 0L).toInt().toUInt().toLong() / c.sectorSize).toInt()

        for (rawLine in text.split('\n')) {
            check()
            val line = rawLine.trimStart(' ', '\t').trimEnd('\r')
            when {
                line.startsWith("INDEX ", ignoreCase = true) -> {
                    val parts = line.substring(6).trim().split(Regex("\\s+"), limit = 2)
                    val index = leadingInt(parts[0])
                    val sectorOffset = parseMsf(parts.getOrElse(1) { "" })
                    if (current.firstSector == -1) {
                        current.firstSector = sectorOffset
                        if (current.filename == previous.filename) {
                            previous.sectorCount = current.firstSector - previous.firstSector
                            current.fileTrackOffset += previous.sectorCount.toLong() * previous.sectorSize
                        }
                        if (track == DiscTracks.LARGEST && previous.sectorCount > largest.sectorCount && previous.isData) {
                            largest.copyFrom(previous)
                        }
                    }
                    if (index == 1) {
                        current.pregapSectors = sectorOffset - current.firstSector
                        if (current.id == track) {
                            done = true
                        } else if (track == DiscTracks.FIRST_DATA && current.isData) {
                            track = current.id
                            done = true
                        } else if (track == DiscTracks.FIRST_OF_SECOND_SESSION && session == 2) {
                            track = current.id
                            done = true
                        }
                    }
                }
                line.startsWith("TRACK ", ignoreCase = true) -> {
                    if (current.sectorSize != 0) previous.copyFrom(current)
                    val rest = line.substring(6).trimStart()
                    current.id = leadingInt(rest)
                    current.pregapSectors = -1
                    current.firstSector = -1
                    current.mode = rest.substringAfter(' ', "").trim().substringBefore(' ')
                    current.isData = current.mode.startsWith("MODE")
                    current.sectorSize = if (current.isData) leadingInt(current.mode.drop(6)) else 2352
                }
                line.startsWith("FILE ", ignoreCase = true) -> {
                    if (current.sectorSize != 0) {
                        previous.copyFrom(current)
                        if (previous.sectorCount == 0) previous.sectorCount = binSectors(previous) - previous.firstSector
                        if (track == DiscTracks.LARGEST && previous.isData && previous.sectorCount > largest.sectorCount) {
                            largest.copyFrom(previous)
                        }
                    }
                    current.reset()
                    current.fileFirstSector = previous.fileFirstSector + previous.firstSector + previous.sectorCount
                    current.filename = fileNameOf(line.substring(5))
                }
                line.startsWith("REM ", ignoreCase = true) -> {
                    val rest = line.substring(4).trimStart()
                    if (rest.startsWith("SESSION ", ignoreCase = true)) session = leadingInt(rest.substring(8).trimStart())
                }
            }
            if (done) break
        }

        if (track == DiscTracks.LARGEST) {
            if (current.sectorSize != 0 && current.isData) {
                current.sectorCount = binSectors(current) - current.firstSector
                if (largest.sectorCount > current.sectorCount) current.copyFrom(largest)
            } else {
                current.copyFrom(largest)
            }
            track = current.id
        } else if (track == DiscTracks.LAST && !done) {
            track = current.id
        }
        if (current.id != track || track == 0) throw DiscFormatException(RaCannotHashReason.NotARom, "The .cue sheet has no track $wanted")
        return openCueBin(cue, current, check)
    }

    // --- .gdi

    /** One line of a `.gdi`: `track lba type sectorSize file offset`; [type] 4 is data, 0 audio. */
    private class GdiTrack(val id: Int, val lba: Int, val type: Int, val sectorSize: Int, val file: String)

    private fun parseGdi(text: String): Pair<Int, List<GdiTrack>> {
        val lines = text.split('\n').map { it.trim() }
        val declared = leadingInt(lines.firstOrNull() ?: "")
        val tracks = lines.drop(1).mapNotNull { line ->
            val fields = Regex("\"[^\"]*\"|\\S+").findAll(line).map { it.value }.toList()
            if (fields.size < 5) return@mapNotNull null
            val numbers = fields.take(4).map { it.toIntOrNull() ?: return@mapNotNull null }
            GdiTrack(numbers[0], numbers[1], numbers[2], numbers[3], fields[4].trim('"').replace('\\', '/'))
        }
        return declared to tracks
    }

    /**
     * rc_hash's `.gdi` reader: tracks are separate files, each placed at the LBA the line gives (no
     * pregap). A line's sector size picks the layout when the sector headers cannot tell.
     */
    private fun openGdiTrack(gdi: RaRomSource, wanted: Int, check: () -> Unit): DiscTrack {
        val (declared, tracks) = parseGdi(readCue(gdi))
        fun binSize(t: GdiTrack): Long = gdi.safeSibling(t.file)?.sizeBytes ?: 0L
        val chosen = when (wanted) {
            DiscTracks.FIRST_DATA -> tracks.firstOrNull { it.type == 4 }
            DiscTracks.LAST -> tracks.firstOrNull { it.id == declared }
            DiscTracks.LARGEST -> tracks.filter { it.type == 4 }.fold(null as GdiTrack?) { best, t ->
                check()
                if (best == null || binSize(t) > binSize(best)) t else best
            }
            DiscTracks.FIRST_OF_SECOND_SESSION -> null
            else -> tracks.firstOrNull { it.id == wanted }
        } ?: throw DiscFormatException(RaCannotHashReason.NotARom, "The .gdi has no track $wanted")
        val bin = gdi.safeSibling(chosen.file)
            ?: throw DiscFormatException(RaCannotHashReason.MissingFile, "The .gdi names \"${chosen.file}\", which is not next to it")
        val file = RawDiscFile(bin, check)
        try {
            val layout = probeSectorSize(file, 0, 0) ?: modeLayout("MODE1/${chosen.sectorSize}")
                ?: throw DiscFormatException(RaCannotHashReason.NotARom, "Could not tell the sector size of track ${chosen.id}")
            return DiscTrack(file, layout.sectorSize, layout.headerSize, 2048, 0, chosen.lba, 0, check)
        } catch (e: Throwable) {
            file.close()
            throw e
        }
    }

    private fun openCueBin(cue: RaRomSource, t: CueTrack, check: () -> Unit): DiscTrack {
        val bin = cue.safeSibling(t.filename)
            ?: throw DiscFormatException(RaCannotHashReason.MissingFile, "The .cue names \"${t.filename}\", which is not next to it")
        val file = RawDiscFile(bin, check)
        try {
            val offset = t.fileTrackOffset
            val probe = probeSectorSize(file, t.pregapSectors, offset)
            val layout = probe ?: modeLayout(t.mode)
                ?: throw DiscFormatException(RaCannotHashReason.NotARom, "Could not tell the sector size of the ${t.mode} track")
            // The probe moves the track's first sector to what the sector headers say.
            val first = if (probe != null && probe.sectorSize != 2048) probe.firstSector else t.fileFirstSector + t.firstSector
            val rawData = if (layout.sectorSize == 2352 && layout.headerSize == 0) 2352 else 2048
            return DiscTrack(file, layout.sectorSize, layout.headerSize, rawData, offset, first, t.pregapSectors, check)
        } catch (e: Throwable) {
            file.close()
            throw e
        }
    }

    /** The cue's own idea of the layout; rcheevos uses it only when the sector headers cannot be read. */
    private fun modeLayout(mode: String): Layout? = when {
        mode.startsWith("MODE2/2352") -> Layout(2352, 24, 0)
        mode.startsWith("MODE1/2048") -> Layout(2048, 0, 0)
        mode.startsWith("MODE2/2336") -> Layout(2336, 8, 0)
        mode.startsWith("MODE1/2352") -> Layout(2352, 16, 0)
        mode.startsWith("AUDIO") -> Layout(2352, 0, 0)
        else -> null
    }

    private fun readCue(cue: RaRomSource): String {
        cue.open().use { input ->
            val buffer = ByteArray(minOf(cue.sizeBytes, MAX_CUE_BYTES.toLong()).toInt().coerceAtLeast(1))
            val got = input.readUpTo(buffer, 0, buffer.size)
            return String(buffer, 0, got, Charsets.ISO_8859_1)
        }
    }

    /** `"name with spaces.bin" BINARY` or `name.bin BINARY`. */
    private fun fileNameOf(rest: String): String {
        val text = rest.trimStart()
        val name = if (text.startsWith("\"")) text.substring(1).substringBefore('"') else text.substringBefore(' ').trim()
        return name.replace('\\', '/')
    }

    private fun leadingInt(text: String): Int {
        var end = 0
        while (end < text.length && text[end].isDigit()) end++
        return text.substring(0, end).toIntOrNull() ?: 0
    }

    /** `mm:ss:ff` to a sector count. */
    private fun parseMsf(text: String): Int {
        val parts = text.trim().split(':')
        val m = leadingInt(parts.getOrElse(0) { "" })
        val s = leadingInt(parts.getOrElse(1) { "" })
        val f = leadingInt(parts.getOrElse(2) { "" })
        return ((m * 60) + s) * 75 + f
    }
}
