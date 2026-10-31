package com.plumemu.identify.fingerprint.disc

import com.plumemu.identify.fingerprint.u64be
import com.plumemu.identify.fingerprint.u8
import com.plumemu.identify.source.RandomAccessSource
import com.plumemu.identify.source.readFully
import java.io.IOException

/** Where a CD CHD's data track starts (in CHD frames) and its track type, e.g. "MODE2_RAW". */
internal data class ChdDataTrack(val frame: Long, val type: String)

/**
 * The track list in a CD CHD's metadata, read to find the data track: the first track that is
 * not audio, or for a Dreamcast GD-ROM track 3 (the high-density area holding `IP.BIN`).
 *
 * Metadata entries (MAME `chd.cpp`) form a list from the header's metadata offset: tag u32,
 * flags u8, data length u24, next entry offset u64, then the data. Tracks are listed as text
 * under `CHTR` (`TRACK:1 TYPE:MODE1_RAW SUBTYPE:NONE FRAMES:n`), `CHT2` (adding `PREGAP:g
 * PGTYPE:t PGSUB:s POSTGAP:q`) or, for a GD-ROM, `CHGD` (adding `PAD:p`). Tracks follow each
 * other in the CHD's frames, each padded to a multiple of 4 frames (MAME `cdrom.cpp`,
 * `CD_TRACK_PADDING`); a pregap whose type starts with "V" is stored in front of the track's
 * data. Types: MODE1 and MODE2_FORM1 hold 2048 bytes, MODE2 and MODE2_FORM_MIX 2336 (data after
 * an 8-byte subheader), MODE1_RAW and MODE2_RAW whole 2352-byte sectors.
 */
internal object ChdTracks {
    private val TAGS = setOf("CHTR", "CHT2", "CHGD")
    private const val GD_ROM_TAG = "CHGD"
    private const val ENTRY_HEADER = 16
    private const val MAX_ENTRIES = 128
    private const val MAX_TEXT = 256
    private const val TRACK_PADDING = 4
    private const val DREAMCAST_DATA_TRACK = 3
    private val FIELD = Regex("([A-Z]+):(\\S+)")

    /** The data track, or null when the CHD lists no tracks (then frame 0 is taken). */
    @Throws(IOException::class)
    fun dataTrack(file: RandomAccessSource, metaOffset: Long): ChdDataTrack? {
        var gdRom = false
        val tracks = ArrayList<Map<String, String>>()
        var next = metaOffset
        var entries = 0
        while (next > 0L && next < file.size && entries++ < MAX_ENTRIES) {
            val head = file.readFully(next, ENTRY_HEADER)
            if (head.size < ENTRY_HEADER) throw NotRead("chd: metadata entry at $next is truncated")
            val tag = String(head, 0, 4, Charsets.ISO_8859_1)
            val length = (head.u8(5) shl 16) or (head.u8(6) shl 8) or head.u8(7)
            if (tag in TAGS && length <= MAX_TEXT) {
                val text = String(file.readFully(next + ENTRY_HEADER, length), Charsets.ISO_8859_1)
                tracks += FIELD.findAll(text).associate { it.groupValues[1] to it.groupValues[2] }
                gdRom = gdRom || tag == GD_ROM_TAG
            }
            val following = head.u64be(8)
            if (following != 0L && following <= next) break
            next = following
        }
        if (tracks.isEmpty()) return null
        var frame = 0L
        for (track in tracks.sortedBy { it["TRACK"]?.toIntOrNull() ?: Int.MAX_VALUE }) {
            val frames = count(track, "FRAMES") ?: throw NotRead("chd: track list has no frame count")
            val type = track["TYPE"] ?: ""
            val wanted = if (gdRom) track["TRACK"]?.toIntOrNull() == DREAMCAST_DATA_TRACK else type != "AUDIO"
            if (wanted) {
                val pregap = count(track, "PREGAP") ?: 0L
                return ChdDataTrack(frame + if (track["PGTYPE"]?.startsWith("V") == true) pregap else 0L, type)
            }
            frame += (frames + TRACK_PADDING - 1) / TRACK_PADDING * TRACK_PADDING
        }
        throw NotRead(if (gdRom) "chd: GD-ROM has no track 3" else "chd: no data track in the track list")
    }

    /** A frame count field; [NotRead] when it is negative or not a number. */
    private fun count(track: Map<String, String>, key: String): Long? {
        val text = track[key] ?: return null
        val value = text.toLongOrNull()
        if (value == null || value < 0 || value > MAX_FRAMES) throw NotRead("chd: track $key \"${text.take(12)}\" is out of range")
        return value
    }

    /** Far more frames than any disc holds (a dual-layer DVD is about 2.1 million sectors). */
    private const val MAX_FRAMES = 1L shl 32

    /** Bytes of user data skipped at the start of a cooked sector of [type] (no sync header). */
    fun cookedDataOffset(type: String): Int = if (type == "MODE2" || type == "MODE2_FORM_MIX") 8 else 0
}
