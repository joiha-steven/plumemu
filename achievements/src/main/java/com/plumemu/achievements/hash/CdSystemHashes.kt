// Kotlin rewrite of rcheevos' CD-system hashes (src/hash/rc_hash_disc.c: 3DO, FM Towns, Neo Geo CD, PC Engine CD, PC-FX). rcheevos: MIT licence.
package com.plumemu.achievements.hash

import com.plumemu.achievements.hash.RaCannotHashReason.NotARom
import java.io.IOException
import java.security.MessageDigest

private const val SECTOR = 2048

private fun notARom(message: String) = DiscFormatException(NotARom, message)

private fun ByteArray.u8(at: Int): Int = if (at in indices) this[at].toInt() and 0xFF else 0

// --- 3DO

/** The 3DO volume header: Opera file system marker, then the block size and the root directory's block. */
private val OPERA_MARKER = byteArrayOf(0x01, 0x5A, 0x5A, 0x5A, 0x5A, 0x5A, 0x01)

private const val OPERA_HEADER_BYTES = 132
private const val NO_NEXT_BLOCK = 0xFFFF
private const val MAX_DIRECTORY_SECTORS = 65_536

/** The header plus the `LaunchMe` executable found in the root directory (which may span several sectors). */
@Throws(IOException::class)
internal fun hash3do(track: DiscTrack, check: () -> Unit): RaHashResult {
    val header = ByteArray(SECTOR)
    val got = track.readSector(0, header, OPERA_HEADER_BYTES)
    if (got < OPERA_HEADER_BYTES || !OPERA_MARKER.indices.all { header[it] == OPERA_MARKER[it] }) throw notARom("Not a 3DO CD")
    val md = newMd5()
    md.update(header, 0, OPERA_HEADER_BYTES)
    // The block size is at 0x4C and the root directory's block at 0x64; only the low three bytes are ever used.
    var blockSize = header.u8(0x4D) * 65536 + header.u8(0x4E) * 256 + header.u8(0x4F)
    var blockLocation = (header.u8(0x65) * 65536 + header.u8(0x66) * 256 + header.u8(0x67)).toLong() * blockSize
    val rootLocation = blockLocation
    var sector = blockLocation / SECTOR
    val buffer = ByteArray(SECTOR)
    var size = 0L
    var rounds = 0
    while (true) {
        check()
        if (++rounds > MAX_DIRECTORY_SECTORS) throw notARom("The 3DO root directory never ends")
        if (track.readSector(sector, buffer, SECTOR) < SECTOR) throw notARom("Could not read the 3DO root directory")
        var offset = buffer.u8(0x12) * 256 + buffer.u8(0x13)
        val stop = buffer.u8(0x0D) * 65536 + buffer.u8(0x0E) * 256 + buffer.u8(0x0F)
        while (offset < stop) {
            if (buffer.u8(offset + 0x03) == 0x02 && isLaunchMe(buffer, offset + 0x20)) {
                blockSize = buffer.u8(offset + 0x0D) * 65536 + buffer.u8(offset + 0x0E) * 256 + buffer.u8(offset + 0x0F)
                blockLocation = (buffer.u8(offset + 0x45) * 65536 + buffer.u8(offset + 0x46) * 256 + buffer.u8(offset + 0x47)).toLong() * blockSize
                size = (buffer.u8(offset + 0x11) * 65536 + buffer.u8(offset + 0x12) * 256 + buffer.u8(offset + 0x13)).toLong()
                break
            }
            // The number of extra copies of the file is at +0x43; each adds a 4-byte address.
            offset += 0x48 + buffer.u8(offset + 0x43) * 4
        }
        if (size != 0L) break
        // The listing may continue in another sector of the root directory.
        val next = buffer.u8(0x02) * 256 + buffer.u8(0x03)
        if (next == NO_NEXT_BLOCK) break
        sector = (rootLocation + next.toLong() * blockSize) / SECTOR
    }
    if (size == 0L) throw notARom("Could not find LaunchMe")
    hashDiscFile(md, track, blockLocation / SECTOR, size, "LaunchMe", check)
    return RaHashResult.Hashed(md.hex())
}

private fun lower(c: Int): Int = if (c in 'A'.code..'Z'.code) c + 32 else c

/** Whether [text] sits at [at] when letters are compared without case. */
private fun ByteArray.hasTextIgnoringCase(at: Int, text: String): Boolean = text.indices.all { lower(u8(at + it)) == lower(text[it].code) }

private fun isLaunchMe(buffer: ByteArray, at: Int): Boolean = buffer.hasTextIgnoringCase(at, "LaunchMe") && buffer.u8(at + 8) == 0

// --- Neo Geo CD

/** IPL.TXT lists the `.PRG` files that make up the game; they are hashed in the order it lists them. */
@Throws(IOException::class)
internal fun hashNeoGeoCd(track: DiscTrack, check: () -> Unit): RaHashResult {
    val ipl = Iso9660.find(track, "IPL.TXT") ?: throw notARom("Not a NeoGeo CD game disc")
    val text = ByteArray(1024)
    if (track.readSector(ipl.sector, text, text.size) == 0) throw notARom("Could not read IPL.TXT")
    text[text.size - 1] = 0
    val md = newMd5()
    var p = 0
    fun at(i: Int): Int = text.u8(i)
    do {
        val start = p
        while (at(p) != 0 && at(p) != '.'.code) p++
        if (text.hasTextIgnoringCase(p, ".PRG")) {
            val name = String(text, start, p + 4 - start, Charsets.ISO_8859_1)
            p += 4
            text[p++] = 0 // rc_hash ends the name in the buffer itself, which also swallows the character after it
            val file = Iso9660.find(track, name) ?: throw notARom("Could not read ${name.take(16)}")
            hashDiscFile(md, track, file.sector, file.size, name, check)
        }
        while (at(p) != 0 && at(p) != '\n'.code) p++
        if (at(p) != '\n'.code) break
        p++
    } while (at(p) != 0 && at(p) != 0x1A)
    return RaHashResult.Hashed(md.hex())
}

// --- PC Engine CD, PC-FX

private const val PCE_MARKER = "PC Engine CD-ROM SYSTEM"

private fun ByteArray.hasText(at: Int, text: String): Boolean = text.indices.all { u8(at + it) == text[it].code }

/** Reads [count] 2048-byte sectors from [first] into [md]; a disc that ends early is refused. */
@Throws(IOException::class)
private fun hashSectors(md: MessageDigest, track: DiscTrack, first: Long, count: Long, check: () -> Unit) {
    val buffer = ByteArray(SECTOR)
    for (n in 0 until count) {
        check()
        if (track.readSector(first + n, buffer, SECTOR) < SECTOR) throw notARom("The disc ends inside the boot program")
        md.update(buffer, 0, SECTOR)
    }
}

/** The second sector names the boot program (title and where it is); GameExpress discs boot a `BOOT.BIN` instead. */
@Throws(IOException::class)
internal fun hashPceCd(track: DiscTrack, check: () -> Unit): RaHashResult {
    val head = ByteArray(SECTOR)
    val first = track.firstSector.toLong()
    if (track.readSector(first + 1, head, 128) < 128) throw notARom("Not a PC Engine CD")
    val md = newMd5()
    if (head.hasText(32, PCE_MARKER)) {
        md.update(head, 106, 22) // the title is the last 22 bytes of the header
        val sector = (head.u8(0) shl 16) + (head.u8(1) shl 8) + head.u8(2)
        hashSectors(md, track, first + sector, head.u8(3).toLong(), check)
    } else {
        val boot = Iso9660.find(track, "BOOT.BIN")
        if (boot == null || boot.size >= MAX_HASH_BYTES) throw notARom("Not a PC Engine CD")
        hashDiscFile(md, track, boot.sector, boot.size, "BOOT.BIN", check)
    }
    return RaHashResult.Hashed(md.hex())
}

private const val PCFX_MARKER = "PC-FX:Hu_CD-ROM"

/** The PC-FX boot header is in the first two sectors of the largest data track (or track 2). */
@Throws(IOException::class)
internal fun hashPcFx(rom: RaRomSource, check: () -> Unit): RaHashResult {
    val buffer = ByteArray(SECTOR)
    var track = DiscReader.openTrack(rom, DiscTracks.LARGEST, check)
    try {
        var first = track.firstSector.toLong()
        track.readSector(first, buffer, 32)
        if (!buffer.hasText(0, PCFX_MARKER)) {
            track.close()
            track = DiscReader.openTrack(rom, 2, check)
            first = track.firstSector.toLong()
            buffer.fill(0)
            track.readSector(first, buffer, 32)
        }
        if (buffer.hasText(0, PCFX_MARKER)) return hashPcFxProgram(track, first, buffer, check)
        // Some PC-FX discs are laid out as PC Engine CDs.
        track.readSector(first + 1, buffer, 128)
        if (buffer.hasText(32, PCE_MARKER)) {
            try {
                return hashPceCd(track, check)
            } catch (e: DiscFormatException) {
                throw notARom("Not a PC-FX CD (${e.message})")
            }
        }
        throw notARom("Not a PC-FX CD")
    } finally {
        track.close()
    }
}

@Throws(IOException::class)
private fun hashPcFxProgram(track: DiscTrack, first: Long, buffer: ByteArray, check: () -> Unit): RaHashResult {
    track.readSector(first + 1, buffer, 128)
    val md = newMd5()
    md.update(buffer, 0, 128)
    // The program's sector is in bytes 32..34 and its length in sectors in bytes 36..38.
    val sector = (buffer.u8(34) shl 16) + (buffer.u8(33) shl 8) + buffer.u8(32)
    val count = (buffer.u8(38) shl 16) + (buffer.u8(37) shl 8) + buffer.u8(36)
    hashSectors(md, track, first + sector, count.toLong(), check)
    return RaHashResult.Hashed(md.hex())
}

// --- FM Towns

/** Executables and the batch file that starts them: `.EXP` (32-bit), `.EXE` (16-bit), `AUTOEXEC.BAT`. */
private fun isFmTownsProgram(name: String): Boolean {
    if (name.length <= 5 || name[name.length - 4] != '.') return false
    if (name.length == 12 && name.equals("AUTOEXEC.BAT", ignoreCase = true)) return true
    val extension = name.substring(name.length - 3)
    return extension.equals("exp", ignoreCase = true) || extension.equals("exe", ignoreCase = true)
}

private const val FM_TOWNS_BOOT_BYTES = 1024L * 1024

/** Every program file sorted by path, or, when there are none, the boot code of a disc with an `IPL4` boot sector. */
@Throws(IOException::class)
internal fun hashFmTownsDisc(track: DiscTrack, check: () -> Unit): RaHashResult {
    val files = Iso9660.list(track, ::isFmTownsProgram)
    val md = newMd5()
    if (files.isEmpty()) {
        val head = ByteArray(16)
        if (track.readSector(0, head, head.size) == head.size && head.hasText(0, "IPL4")) {
            hashDiscFile(md, track, 0, FM_TOWNS_BOOT_BYTES, "boot", check)
            return RaHashResult.Hashed(md.hex())
        }
        throw notARom("No EXP files found")
    }
    for (file in files) hashDiscFile(md, track, file.sector, file.size, file.path, check)
    return RaHashResult.Hashed(md.hex())
}
