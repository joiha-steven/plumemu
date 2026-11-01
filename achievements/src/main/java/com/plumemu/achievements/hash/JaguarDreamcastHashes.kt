// Kotlin rewrite of rcheevos' Atari Jaguar CD, Dreamcast and PS3 hashes (src/hash/rc_hash_disc.c). rcheevos: MIT licence.
package com.plumemu.achievements.hash

import com.plumemu.achievements.hash.RaCannotHashReason.MissingFile
import com.plumemu.achievements.hash.RaCannotHashReason.NotARom
import java.io.IOException

private const val RAW_SECTOR = 2352

private fun notARom(message: String) = DiscFormatException(NotARom, message)

// --- Atari Jaguar CD

private const val JAGUAR_HEADER_TEXT = "ATARI APPROVED DATA HEADER ATRI "
private const val JAGUAR_HEADER_TEXT_SWAPPED = "TARA IPARPVODED TA AEHDAREA RT I"

/** Every homebrew disc carries this boot code; the game itself is a second program in track 2. */
internal const val JAGUAR_HOMEBREW_BOOT_HASH = "254487b59ab21bc005338e85cbf9fd2f"

private fun ByteArray.matches(at: Int, text: String): Boolean =
    at + text.length <= size && text.indices.all { this[at + it].toInt() == text[it].code }

/** A big-endian 32-bit value, or the same bytes in 16-bit swapped order. */
private fun ByteArray.u32(at: Int, swapped: Boolean): Long {
    fun b(i: Int): Long = this[at + i].toLong() and 0xFF
    return if (swapped) (b(0) shl 16) or (b(1) shl 24) or b(2) or (b(3) shl 8) else (b(0) shl 24) or (b(1) shl 16) or (b(2) shl 8) or b(3)
}

private fun ByteArray.swapPairs() {
    var i = 0
    while (i + 1 < size) {
        val t = this[i]
        this[i] = this[i + 1]
        this[i + 1] = t
        i += 2
    }
}

/**
 * The boot code of the first data track of the second session, found by its "ATARI APPROVED DATA HEADER"
 * (the data may be 16-bit swapped). A disc whose boot code is the common homebrew loader is hashed by the
 * program in track 2 instead. [homebrewBootHash] is a parameter so a test can name its own loader.
 */
@Throws(IOException::class)
internal fun hashJaguarCd(rom: RaRomSource, check: () -> Unit, homebrewBootHash: String = JAGUAR_HOMEBREW_BOOT_HASH): RaHashResult {
    val buffer = ByteArray(RAW_SECTOR)
    var track = DiscReader.openTrack(rom, DiscTracks.FIRST_OF_SECOND_SESSION, check)
    var sector = track.firstSector.toLong()
    var swapped = false
    var offset = 0
    var size = 0L
    try {
        track.readSector(sector, buffer, RAW_SECTOR)
        // The header is an unspecified distance into the sector, usually two bytes in.
        for (i in 64 until RAW_SECTOR - 32 - 4 * 3) {
            if (buffer.matches(i, JAGUAR_HEADER_TEXT_SWAPPED)) {
                swapped = true
            } else if (!buffer.matches(i, JAGUAR_HEADER_TEXT)) {
                continue
            }
            offset = i + 32 + 4
            size = buffer.u32(offset, swapped)
            break
        }
        if (size == 0L) throw notARom("Not a Jaguar CD")
    } catch (e: Throwable) {
        track.close()
        throw e
    }
    var secondTry = false
    while (true) {
        val md = newMd5()
        offset += 4
        size = minOf(size, MAX_HASH_BYTES)
        try {
            do {
                check()
                if (swapped) buffer.swapPairs()
                val remaining = RAW_SECTOR - offset
                if (remaining >= size) {
                    md.update(buffer, offset, size.toInt())
                    size = 0
                    break
                }
                md.update(buffer, offset, remaining)
                size -= remaining
                offset = 0
            } while (track.readSector(++sector, buffer, RAW_SECTOR) == RAW_SECTOR)
        } finally {
            track.close()
        }
        if (size > 0) throw notARom("Not enough data")
        val hash = md.hex()
        if (hash != homebrewBootHash || !swapped || secondTry) return RaHashResult.Hashed(hash)
        secondTry = true

        // Homebrew: the program is in track 2, behind 64 bytes of "KART" repeating.
        track = DiscReader.openTrack(rom, 2, check)
        try {
            sector = track.firstSector.toLong()
            buffer.fill(0)
            track.readSector(sector, buffer, RAW_SECTOR)
            if (!buffer.matches(0x5E, "RT!IRTKA")) throw notARom("Homebrew executable not found in track 2")
            offset = 0xA6
            size = buffer.u32(offset, true)
        } catch (e: Throwable) {
            track.close()
            throw e
        }
    }
}

// --- Dreamcast

private const val DREAMCAST_MARKER = "SEGA SEGAKATANA "
private const val IP_BIN_BYTES = 256
private const val BOOT_NAME_AT = 96
private const val BOOT_NAME_MAX = 16

private fun isSpace(c: Int): Boolean = c == ' '.code || c in 9..13

/** The IP.BIN meta data in sector 0 of track 3 (or, for a MIL-CD, of the first data track), then the boot executable. */
@Throws(IOException::class)
internal fun hashDreamcast(rom: RaRomSource, check: () -> Unit): RaHashResult {
    val meta = ByteArray(IP_BIN_BYTES)
    // Track 3 of a GD-ROM is the data track that holds the TOC and IP.BIN; any other disc has no such track.
    var missingTrack: DiscFormatException? = null
    var track: DiscTrack? = try {
        DiscReader.openTrack(rom, 3, check)
    } catch (e: DiscFormatException) {
        // A track file that is not there is the better answer than "not a Dreamcast disc" if the fallback fails too.
        if (e.reason == MissingFile) missingTrack = e
        null
    }
    track?.readSector(track.firstSector.toLong(), meta, meta.size)
    if (!meta.matches(0, DREAMCAST_MARKER)) {
        track?.close()
        track = DiscReader.openTrack(rom, DiscTracks.FIRST_DATA, check)
        meta.fill(0)
        try {
            track.readSector(track.firstSector.toLong(), meta, meta.size)
        } catch (e: Throwable) {
            track.close()
            throw e
        }
        if (!meta.matches(0, DREAMCAST_MARKER)) {
            track.close()
            throw missingTrack ?: notARom("Not a Dreamcast CD")
        }
    }
    var current: DiscTrack = track!!
    try {
        val md = newMd5()
        md.update(meta)
        var length = 0
        while (length < BOOT_NAME_MAX && !isSpace(meta[BOOT_NAME_AT + length].toInt() and 0xFF)) length++
        if (length == 0) throw notARom("Boot executable not specified on IP.BIN")
        val name = String(meta, BOOT_NAME_AT, length, Charsets.ISO_8859_1)
        val exe = Iso9660.find(current, name) ?: throw notARom("Could not locate boot executable")
        // Normally the executable is in the data track; a GD-ROM's last track holds it when it is not.
        if (current.readSector(exe.sector, ByteArray(1), 1) == 0) {
            current.close()
            current = DiscReader.openTrack(rom, DiscTracks.LAST, check)
        }
        hashDiscFile(md, current, exe.sector, exe.size, "boot executable", check)
        return RaHashResult.Hashed(md.hex())
    } finally {
        current.close()
    }
}

// --- PS3 executable of an extracted game

/** A flat or installed game: `PARAM.SFO` beside `EBOOT.BIN`, both hashed whole. The retail layout keeps PARAM.SFO one folder up, which a sibling lookup cannot reach. */
@Throws(IOException::class)
internal fun hashPs3Executable(eboot: RaRomSource, check: () -> Unit): RaHashResult {
    val param = eboot.safeSibling("PARAM.SFO") ?: throw DiscFormatException(MissingFile, "PARAM.SFO is not next to ${eboot.fileName}")
    if (eboot.sizeBytes <= 0L) return RaHashResult.CannotHash(RaCannotHashReason.Empty, "The file has no bytes")
    val md = newMd5()
    for (source in listOf(param, eboot)) {
        source.open().use { feed(it, md, Long.MAX_VALUE, check) }
    }
    return RaHashResult.Hashed(md.hex())
}
