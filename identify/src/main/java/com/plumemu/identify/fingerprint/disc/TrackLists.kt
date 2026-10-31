package com.plumemu.identify.fingerprint.disc

import java.util.Locale

/** The data track a track list points to: its file (as written, relative), layout and first sector in the file. */
internal data class TrackFile(val fileName: String, val layout: SectorLayout, val firstSector: Long = 0)

/**
 * Text lists that name other files: cue sheets, Dreamcast `.gdi` track lists and `.m3u`
 * playlists. Names may be quoted (with spaces inside) or bare.
 */
internal object TrackLists {
    /** Lists longer than this are not track lists (a cue of 99 tracks is about 6 KB). */
    const val MAX_LIST_BYTES = 64 * 1024

    /** Dreamcast GD-ROM: the high-density area, holding `IP.BIN`, starts at this LBA (track 3). */
    private const val GD_HIGH_DENSITY_LBA = 45000L
    private const val GDI_DATA_TYPE = 4

    /**
     * The data track of a cue sheet: `FILE "name" BINARY|MOTOROLA|...`, `TRACK nn
     * MODE1/2048|MODE1/2352|MODE2/2352|MODE2/2336|CDI/2352|AUDIO`, `INDEX 01 mm:ss:ff` (the
     * track's start within its file, 75 frames a second). Normally the first data track. A
     * Dreamcast cue (Redump writes `REM HIGH-DENSITY AREA` before track 3, and track 1 is a data
     * track of the low-density area) gives the first data track after that marker, or when
     * [dreamcast] is set and there is no marker, the first data track numbered 3 or more.
     */
    fun cueDataTrack(text: String, dreamcast: Boolean = false): TrackFile? {
        val tracks = cueTracks(text)
        val data = tracks.filter { it.layout != null }
        val chosen = when {
            tracks.any { it.highDensity } -> data.firstOrNull { it.highDensity }
            dreamcast -> data.firstOrNull { it.number >= 3 }
            else -> data.firstOrNull()
        } ?: return null
        return TrackFile(chosen.file ?: return null, chosen.layout ?: return null, chosen.start)
    }

    private class CueTrack(val number: Int, val file: String?, val layout: SectorLayout?, val highDensity: Boolean) {
        var start = 0L
    }

    private fun cueTracks(text: String): List<CueTrack> {
        val tracks = ArrayList<CueTrack>()
        var file: String? = null
        var highDensity = false
        for (line in text.lineSequence()) {
            val words = words(line)
            when (words.firstOrNull()?.uppercase(Locale.ROOT)) {
                "REM" -> if (line.uppercase(Locale.ROOT).contains("HIGH-DENSITY AREA")) highDensity = true
                "FILE" -> file = words.getOrNull(1)
                "TRACK" -> tracks += CueTrack(
                    number = words.getOrNull(1)?.toIntOrNull() ?: 0,
                    file = file,
                    layout = cueLayout(words.getOrNull(2)?.uppercase(Locale.ROOT)),
                    highDensity = highDensity,
                )
                "INDEX" -> if (words.getOrNull(1) == "01") tracks.lastOrNull()?.let { it.start = msfFrames(words.getOrNull(2)) ?: 0 }
            }
        }
        return tracks
    }

    /**
     * The track holding `IP.BIN` in a `.gdi`: first line the track count, then per track
     * `number lba type sectorSize fileName offset` (type 4 is data). The data track that starts
     * the high-density area (LBA 45000); failing that, the first data track after track 2.
     */
    fun gdiDataTrack(text: String): TrackFile? {
        val tracks = text.lineSequence().drop(1).map { words(it) }.filter { it.size >= 5 }.mapNotNull { w ->
            val number = w[0].toIntOrNull() ?: return@mapNotNull null
            val lba = w[1].toLongOrNull() ?: return@mapNotNull null
            val type = w[2].toIntOrNull() ?: return@mapNotNull null
            val layout = when (w[3]) {
                "2352" -> SectorLayout.RAW_2352
                "2048" -> SectorLayout.COOKED_2048
                else -> return@mapNotNull null
            }
            GdiTrack(number, lba, type, TrackFile(w[4], layout))
        }.toList()
        val data = tracks.filter { it.type == GDI_DATA_TYPE }
        val chosen = data.firstOrNull { it.lba == GD_HIGH_DENSITY_LBA } ?: data.firstOrNull { it.number >= 3 }
        return chosen?.file
    }

    /** Every track file a `.gdi` names (the fifth word of each track line after the count). */
    fun gdiFileNames(text: String): List<String> =
        text.lineSequence().drop(1).map { words(it) }.filter { it.size >= 5 && it[0].toIntOrNull() != null }.map { it[4] }.toList()

    /** The first entry of a playlist: the first line that is not blank or a `#` comment. */
    fun m3uFirstEntry(text: String): String? =
        text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") }

    private data class GdiTrack(val number: Int, val lba: Long, val type: Int, val file: TrackFile)

    private fun cueLayout(mode: String?): SectorLayout? = when (mode) {
        "MODE1/2048", "MODE2/2048" -> SectorLayout.COOKED_2048
        "MODE1/2352", "MODE2/2352", "CDI/2352" -> SectorLayout.RAW_2352
        "MODE2/2336", "CDI/2336" -> SectorLayout.MODE2_2336
        else -> null
    }

    private fun msfFrames(msf: String?): Long? {
        val parts = msf?.split(':')?.map { it.toLongOrNull() } ?: return null
        if (parts.size != 3 || parts.any { it == null || it < 0 }) return null
        return (parts[0]!! * 60 + parts[1]!!) * 75 + parts[2]!!
    }

    /** Splits a line on spaces and tabs, keeping a double-quoted part (quotes removed) as one word. */
    private fun words(line: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        val text = line.trim()
        while (i < text.length) {
            if (text[i].isWhitespace()) {
                i++
                continue
            }
            if (text[i] == '"') {
                val end = text.indexOf('"', i + 1).let { if (it < 0) text.length else it }
                out += text.substring(i + 1, end)
                i = end + 1
            } else {
                var end = i
                while (end < text.length && !text[end].isWhitespace()) end++
                out += text.substring(i, end)
                i = end
            }
        }
        return out
    }
}
