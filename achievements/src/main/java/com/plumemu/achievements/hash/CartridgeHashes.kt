// Kotlin rewrite of rcheevos' rc_hash for cartridge systems (src/hash/rc_hash.c, rc_hash_rom.c).
// rcheevos: https://github.com/RetroAchievements/rcheevos, MIT licence.
package com.plumemu.achievements.hash

import com.plumemu.achievements.hash.RaCannotHashReason.NotARom
import java.io.IOException
import java.util.Locale

/**
 * One function per rc_hash method. Each reads one file, streamed, and returns a result;
 * an [IOException] from the file escapes to [RaHasher], which turns it into `Unreadable`.
 *
 * The caller has checked that the file is not empty; [size] is [RaRomSource.sizeBytes].
 */

/** `rc_hash_whole_file`: MD5 of the first 64 MiB of the file. */
@Throws(IOException::class)
internal fun hashWholeFile(source: RaRomSource, size: Long, check: () -> Unit): RaHashResult {
    val md = newMd5()
    source.open().use { feed(it, md, minOf(size, MAX_HASH_BYTES), check) }
    return RaHashResult.Hashed(md.hex())
}

/**
 * The header-skipping methods hash the file minus a header. rc_hash buffers at most 64 MiB first and
 * decides on that size, so [skipFor] gets the capped size too. [skipFor] sees the first
 * `min(size, 512)` bytes and returns how many bytes to leave out (never more than the file has).
 */
@Throws(IOException::class)
private fun hashAfterHeader(
    source: RaRomSource,
    size: Long,
    check: () -> Unit,
    skipFor: (head: ByteArray, size: Long) -> Int,
): RaHashResult {
    val capped = minOf(size, MAX_HASH_BYTES)
    val head = ByteArray(minOf(capped, 512L).toInt())
    val md = newMd5()
    source.open().use { input ->
        val read = input.readUpTo(head, 0, head.size)
        val skip = skipFor(head, capped).coerceAtMost(read)
        md.update(head, skip, read - skip)
        feed(input, md, capped - read, check)
    }
    return RaHashResult.Hashed(md.hex())
}

private fun ByteArray.startsWith(vararg bytes: Int, at: Int = 0): Boolean {
    if (size < at + bytes.size) return false
    return bytes.indices.all { this[at + it].toInt() and 0xFF == bytes[it] }
}

private fun String.asciiBytes(): IntArray = IntArray(length) { this[it].code }

/** `rc_hash_nes`: skips the 16-byte `NES\x1A` (iNES) or `FDS\x1A` header. */
@Throws(IOException::class)
internal fun hashNes(source: RaRomSource, size: Long, check: () -> Unit): RaHashResult =
    hashAfterHeader(source, size, check) { head, capped ->
        when {
            capped > 16 && head.startsWith('N'.code, 'E'.code, 'S'.code, 0x1A) -> 16
            capped > 16 && head.startsWith('F'.code, 'D'.code, 'S'.code, 0x1A) -> 16
            else -> 0
        }
    }

/** `rc_hash_lynx`: skips the 64-byte header that starts `LYNX` and a zero byte. */
@Throws(IOException::class)
internal fun hashLynx(source: RaRomSource, size: Long, check: () -> Unit): RaHashResult =
    hashAfterHeader(source, size, check) { head, capped ->
        if (capped > 64 && head.startsWith('L'.code, 'Y'.code, 'N'.code, 'X'.code, 0)) 64 else 0
    }

/** `rc_hash_7800`: skips the 128-byte header with `ATARI7800` at offset 1. */
@Throws(IOException::class)
internal fun hash7800(source: RaRomSource, size: Long, check: () -> Unit): RaHashResult =
    hashAfterHeader(source, size, check) { head, capped ->
        if (capped > 128 && head.startsWith(*"ATARI7800".asciiBytes(), at = 1)) 128 else 0
    }

/** `rc_hash_scv`: skips the 32-byte header that starts `EmuSCV`. */
@Throws(IOException::class)
internal fun hashScv(source: RaRomSource, size: Long, check: () -> Unit): RaHashResult =
    hashAfterHeader(source, size, check) { head, capped ->
        if (capped > 32 && head.startsWith(*"EmuSCV".asciiBytes())) 32 else 0
    }

/** `rc_hash_snes`: skips a 512-byte copier header, found when the size is 512 over a multiple of 8 KiB. */
@Throws(IOException::class)
internal fun hashSnes(source: RaRomSource, size: Long, check: () -> Unit): RaHashResult =
    hashAfterHeader(source, size, check) { _, capped ->
        if (capped - (capped / 0x2000) * 0x2000 == 512L) 512 else 0
    }

/**
 * `rc_hash_pce`: PC Engine HuCards have no magic, so a 512-byte header is assumed when bit 9 of
 * the size is set (rc_hash tests `size & 512`, as the Beetle PCE core does).
 */
@Throws(IOException::class)
internal fun hashPce(source: RaRomSource, size: Long, check: () -> Unit): RaHashResult =
    hashAfterHeader(source, size, check) { _, capped -> if (capped and 512L != 0L) 512 else 0 }

/**
 * `rc_hash_n64`: hashes the ROM as big endian (`.z64`). A first byte of 0x37 means byte-swapped
 * (`.v64`), 0x40 little endian (`.n64`); 0x80 and the 64DD bytes 0xE8 and 0x22 are hashed as they are.
 * Like rc_hash, a trailing partial word is swapped too (the missing bytes count as zeros, where
 * rc_hash reads stale buffer bytes); real ROMs are a multiple of 4 bytes and never hit it.
 */
@Throws(IOException::class)
internal fun hashN64(source: RaRomSource, size: Long, check: () -> Unit): RaHashResult {
    val md = newMd5()
    source.open().use { input ->
        val first = input.read()
        val swap: ((ByteArray, Int) -> Unit)? = when (first) {
            0x80, 0xE8, 0x22 -> null
            0x37 -> ::swapBytes16
            0x40 -> ::swapBytes32
            else -> return RaHashResult.CannotHash(NotARom, "Not a Nintendo 64 ROM (first byte $first)")
        }
        val capped = minOf(size, MAX_HASH_BYTES)
        // The byte read above is put back by hashing it here, swapped with the rest of its word.
        val buffer = ByteArray(CHUNK_BYTES)
        buffer[0] = first.toByte()
        var remaining = capped
        var filled = 1
        while (remaining > 0) {
            check()
            val want = minOf(remaining, CHUNK_BYTES.toLong()).toInt()
            val got = input.readUpTo(buffer, filled, want - filled) + filled
            filled = 0
            if (got <= 0) break
            swap?.invoke(buffer, got)
            md.update(buffer, 0, got)
            remaining -= got
            if (got < want) break
        }
    }
    return RaHashResult.Hashed(md.hex())
}

private fun zeroPadToWord(buffer: ByteArray, length: Int) {
    var i = length
    while (i % 4 != 0) buffer[i++] = 0
}

private fun swapBytes16(buffer: ByteArray, length: Int) {
    zeroPadToWord(buffer, length)
    var i = 0
    while (i < length) {
        val a = buffer[i]; val b = buffer[i + 1]; val c = buffer[i + 2]; val d = buffer[i + 3]
        buffer[i] = b; buffer[i + 1] = a; buffer[i + 2] = d; buffer[i + 3] = c
        i += 4
    }
}

private fun swapBytes32(buffer: ByteArray, length: Int) {
    zeroPadToWord(buffer, length)
    var i = 0
    while (i < length) {
        val a = buffer[i]; val b = buffer[i + 1]; val c = buffer[i + 2]; val d = buffer[i + 3]
        buffer[i] = d; buffer[i + 1] = c; buffer[i + 2] = b; buffer[i + 3] = a
        i += 4
    }
}

private fun ByteArray.u32le(at: Int): Long =
    (this[at].toLong() and 0xFF) or ((this[at + 1].toLong() and 0xFF) shl 8) or
        ((this[at + 2].toLong() and 0xFF) shl 16) or ((this[at + 3].toLong() and 0xFF) shl 24)

private const val DS_HEADER_BYTES = 512
private const val DS_ICON_BYTES = 0xA00L
private const val DS_MAX_CODE_BYTES = 16L * 1024 * 1024

/**
 * `rc_hash_nintendo_ds` (also DSi): MD5 of the first 0x160 header bytes, the ARM9 code, the ARM7
 * code and the 0xA00-byte icon block, found through the header's addresses. A 512-byte SuperCard
 * header in front is skipped. Code or icon cut short by the end of the file is padded with zeros
 * (rc_hash reads unset memory there).
 */
@Throws(IOException::class)
internal fun hashNintendoDs(source: RaRomSource, check: () -> Unit): RaHashResult {
    RangeReader(source, check).use { reader ->
        val header = ByteArray(DS_HEADER_BYTES)
        if (reader.readAt(0, header) != DS_HEADER_BYTES) {
            return RaHashResult.CannotHash(NotARom, "Nintendo DS header cut short")
        }
        var offset = 0L
        val superCard = header.startsWith(0x2E, 0x00, 0x00, 0xEA) && header.startsWith(0x44, 0x46, 0x96, 0x00, at = 0xB0)
        if (superCard) {
            offset = DS_HEADER_BYTES.toLong()
            reader.readAt(offset, header)
        }
        val arm9Addr = header.u32le(0x20)
        val arm9Size = header.u32le(0x2C)
        val arm7Addr = header.u32le(0x30)
        val arm7Size = header.u32le(0x3C)
        val iconAddr = header.u32le(0x68)
        // Each size alone is checked: rc_hash adds them as 32-bit numbers, which can wrap past the limit.
        if (arm9Size > DS_MAX_CODE_BYTES || arm7Size > DS_MAX_CODE_BYTES || arm9Size + arm7Size > DS_MAX_CODE_BYTES) {
            return RaHashResult.CannotHash(NotARom, "ARM9 ($arm9Size) + ARM7 ($arm7Size) code sizes exceed 16 MiB")
        }
        val md = newMd5()
        md.update(header, 0, 0x160)
        reader.digestAt(md, arm9Addr + offset, arm9Size)
        reader.digestAt(md, arm7Addr + offset, arm7Size)
        reader.digestAt(md, iconAddr + offset, DS_ICON_BYTES)
        return RaHashResult.Hashed(md.hex())
    }
}

/**
 * `rc_hash_text` (Arduboy `.hex`): MD5 of the text with every line ending turned into `\n`, and a
 * `\n` after the last line even when the file has none. Only the first 64 MiB is read.
 */
@Throws(IOException::class)
internal fun hashArduboyText(source: RaRomSource, size: Long, check: () -> Unit): RaHashResult {
    val md = newMd5()
    val newline = byteArrayOf('\n'.code.toByte())
    val buffer = ByteArray(CHUNK_BYTES)
    var remaining = minOf(size, MAX_HASH_BYTES)
    var afterCarriageReturn = false
    var lineOpen = false
    source.open().use { input ->
        while (remaining > 0) {
            check()
            val got = input.readUpTo(buffer, 0, minOf(remaining, CHUNK_BYTES.toLong()).toInt())
            if (got <= 0) break
            remaining -= got
            var runStart = 0
            for (i in 0 until got) {
                val b = buffer[i].toInt()
                if (b == '\r'.code || b == '\n'.code) {
                    if (i > runStart) md.update(buffer, runStart, i - runStart)
                    runStart = i + 1
                    if (b == '\n'.code && afterCarriageReturn) {
                        afterCarriageReturn = false
                    } else {
                        md.update(newline)
                        afterCarriageReturn = b == '\r'.code
                    }
                    lineOpen = false
                } else {
                    lineOpen = true
                    afterCarriageReturn = false
                }
            }
            if (got > runStart) md.update(buffer, runStart, got - runStart)
        }
    }
    if (lineOpen) md.update(newline)
    return RaHashResult.Hashed(md.hex())
}

private const val NEO_HEADER_BYTES = 4096L

/** `rc_hash_neogeo_cart` (`.neo` files): MD5 of the ROM data after the 4096-byte header. */
@Throws(IOException::class)
internal fun hashNeoGeoCart(source: RaRomSource, size: Long, check: () -> Unit): RaHashResult {
    val magic = ByteArray(4)
    val md = newMd5()
    source.open().use { input ->
        if (input.readUpTo(magic, 0, 4) != 4 || !magic.startsWith('N'.code, 'E'.code, 'O'.code, 1)) {
            return RaHashResult.CannotHash(NotARom, "Not a valid .neo file")
        }
        if (size <= NEO_HEADER_BYTES) return RaHashResult.CannotHash(NotARom, "Not a valid .neo file (no ROM data)")
        input.skipUpTo(NEO_HEADER_BYTES - 4)
        feed(input, md, minOf(size - NEO_HEADER_BYTES, MAX_HASH_BYTES), check)
    }
    return RaHashResult.Hashed(md.hex())
}

private val ARCADE_SUBSYSTEM_FOLDERS = setOf(
    "nes", "fds", "sms", "msx", "ngp", "pce", "chf", "sgx", "tg16", "msx1", "neocd", "coleco", "sg1000",
    "genesis", "gamegear", "megadriv", "pcengine", "channelf", "spectrum", "megadrive", "supergrafx",
    "zxspectrum", "mastersystem", "colecovision",
)

/** rc_hash's own limit on the folder-prefixed name (its buffer is 128 bytes). */
private const val ARCADE_NAME_LIMIT = 128

private fun String.asciiLowercase(): String =
    buildString(length) { for (c in this@asciiLowercase) append(if (c in 'A'..'Z') c + 32 else c) }

/**
 * `rc_hash_arcade`: the MD5 of the file name without its extension, as the arcade cores are strict
 * about having the right ROM data. [path] is a file path or, for an extracted set, a folder. When the
 * parent folder names one of the FBNeo subsystems (`nes`, `coleco`, `sgx`...), the name is hashed as
 * `<folder>_<name>` with the folder in lower case.
 */
internal fun hashArcade(path: String): RaHashResult {
    val nameStart = maxOf(path.lastIndexOf('/'), path.lastIndexOf('\\')) + 1
    val fileName = path.substring(nameStart)
    val dot = fileName.lastIndexOf('.')
    if (dot < 0) return hashName(fileName)
    val name = fileName.substring(0, dot)
    if (nameStart > 1) {
        var folderStart = nameStart - 1
        while (folderStart > 0 && path[folderStart - 1] != '/' && path[folderStart - 1] != '\\') folderStart--
        val folder = path.substring(folderStart, nameStart - 1).asciiLowercase()
        if (folder in ARCADE_SUBSYSTEM_FOLDERS) {
            val prefixed = "${folder}_$name"
            if (prefixed.toByteArray(Charsets.UTF_8).size < ARCADE_NAME_LIMIT) return hashName(prefixed)
        }
    }
    return hashName(name)
}

private fun hashName(name: String): RaHashResult {
    val md = newMd5()
    md.update(name.toByteArray(Charsets.UTF_8))
    return RaHashResult.Hashed(md.hex())
}

internal fun String.extensionLowercase(): String {
    val dot = lastIndexOf('.')
    return if (dot < 0) "" else substring(dot + 1).lowercase(Locale.ROOT)
}
