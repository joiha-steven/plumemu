package com.plumemu.identify.fingerprint

import java.io.ByteArrayOutputStream
import java.util.Random
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Generated stand-ins for ROM files: pseudo-random filler with made-up headers written at the
 * offsets each format documents. No real ROM data, no real titles or codes.
 */
internal object FakeRoms {

    fun filler(size: Int, seed: Long = 7): ByteArray = ByteArray(size).also { Random(seed).nextBytes(it) }

    fun crcOf(bytes: ByteArray, from: Int = 0, to: Int = bytes.size): Long =
        CRC32().apply { update(bytes, from, to - from) }.value

    fun ByteArray.putAscii(at: Int, text: String, width: Int = text.length, pad: Char = ' ') {
        val padded = text.padEnd(width, pad)
        for (i in 0 until width) this[at + i] = padded[i].code.toByte()
    }

    fun gba(code: String = "ZZZE", title: String = "TEST GAME", size: Int = 1 shl 20, goodCheck: Boolean = true): ByteArray {
        val rom = filler(size)
        rom.putAscii(0xA0, title, 12, '\u0000')
        rom.putAscii(0xAC, code)
        rom[0xB2] = 0x96.toByte()
        var check = 0
        for (i in 0xA0..0xBC) check -= rom[i].toInt() and 0xFF
        rom[0xBD] = ((check - 0x19) and 0xFF).toByte()
        if (!goodCheck) rom[0xBD] = (rom[0xBD] + 1).toByte()
        return rom
    }

    fun nds(code: String = "ZZZE", title: String = "TESTDS", size: Int = 1 shl 20, goodCrc: Boolean = true): ByteArray {
        val rom = filler(size)
        rom.putAscii(0x00, title, 12, '\u0000')
        rom.putAscii(0x0C, code)
        var crc = 0xFFFF
        for (i in 0 until 0x15E) {
            crc = crc xor (rom[i].toInt() and 0xFF)
            repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1 }
        }
        if (!goodCrc) crc = crc xor 1
        rom[0x15E] = (crc and 0xFF).toByte()
        rom[0x15F] = (crc ushr 8).toByte()
        return rom
    }

    /** [colorCode] non-null writes a short title, the CGB flag and a manufacturer code. */
    fun gb(title: String = "TEST GB GAME", colorCode: String? = null, size: Int = 1 shl 16): ByteArray {
        val rom = filler(size)
        val logo = intArrayOf(0xCE, 0xED, 0x66, 0x66, 0xCC, 0x0D, 0x00, 0x0B)
        logo.forEachIndexed { i, b -> rom[0x104 + i] = b.toByte() }
        if (colorCode == null) {
            rom.putAscii(0x134, title, 16, '\u0000')
        } else {
            rom.putAscii(0x134, title, 11, '\u0000')
            rom.putAscii(0x13F, colorCode)
            rom[0x143] = 0x80.toByte()
        }
        var check = 0
        for (i in 0x134..0x14C) check = check - (rom[i].toInt() and 0xFF) - 1
        rom[0x14D] = (check and 0xFF).toByte()
        return rom
    }

    /** A big-endian (.z64) image. */
    fun n64(code: String = "NZZE", title: String = "TEST SIXTYFOUR", size: Int = 1 shl 20): ByteArray {
        val rom = filler(size)
        intArrayOf(0x80, 0x37, 0x12, 0x40).forEachIndexed { i, b -> rom[i] = b.toByte() }
        rom.putAscii(0x20, title, 20)
        rom.putAscii(0x3B, code)
        return rom
    }

    fun byteSwapped(z64: ByteArray): ByteArray = ByteArray(z64.size) { z64[it xor 1] }

    fun wordSwapped(z64: ByteArray): ByteArray = ByteArray(z64.size) { z64[(it and 3.inv()) + 3 - (it and 3)] }

    fun megaDrive(serial: String = "GM 00001009-00", title: String = "TEST  MEGA   GAME", size: Int = 1 shl 19): ByteArray {
        val rom = filler(size)
        rom.putAscii(0x100, "SEGA MEGA DRIVE ")
        rom.putAscii(0x120, title, 48)
        rom.putAscii(0x150, title, 48)
        rom.putAscii(0x180, serial, 14)
        return rom
    }

    /** Super Magic Drive layout: 512-byte header, then each 16 KB block split odd bytes / even bytes. */
    fun toSmd(plain: ByteArray): ByteArray {
        val block = 16 * 1024
        val out = ByteArray(512 + plain.size)
        out[0] = (plain.size / block).toByte()
        out[1] = 3
        out[8] = 0xAA.toByte()
        out[9] = 0xBB.toByte()
        for (start in plain.indices step block) {
            for (i in 0 until block / 2) {
                out[512 + start + i] = plain[start + 2 * i + 1]
                out[512 + start + block / 2 + i] = plain[start + 2 * i]
            }
        }
        return out
    }

    /** A LoROM or HiROM image with a valid internal header and an extended-header game code. */
    fun snes(size: Int = 4 shl 20, hiRom: Boolean = false, title: String = "TEST SUPER GAME", code: String? = "ZZZE"): ByteArray {
        val rom = filler(size)
        val base = if (hiRom) 0xFFC0 else 0x7FC0
        rom.putAscii(base, title, 21)
        rom[base + 0x15] = (if (hiRom) 0x21 else 0x20).toByte()
        rom[base + 0x1A] = (if (code != null) 0x33 else 0x01).toByte()
        if (code != null) rom.putAscii(base - 0x0E, code)
        val checksum = 0x1234
        val complement = checksum xor 0xFFFF
        rom[base + 0x1C] = (complement and 0xFF).toByte()
        rom[base + 0x1D] = (complement ushr 8).toByte()
        rom[base + 0x1E] = (checksum and 0xFF).toByte()
        rom[base + 0x1F] = (checksum ushr 8).toByte()
        return rom
    }

    fun withHeader(header: ByteArray, content: ByteArray): ByteArray = header + content

    fun copierHeader(): ByteArray = ByteArray(512).also { it[0] = 0x20 }

    fun inesHeader(trainer: Boolean = false): ByteArray = ByteArray(16).also {
        it.putAscii(0, "NES\u001A")
        it[4] = 2
        it[5] = 1
        if (trainer) it[6] = 0x04
    }

    fun fdsHeader(): ByteArray = ByteArray(16).also { it.putAscii(0, "FDS\u001A"); it[4] = 1 }

    fun a78Header(title: String = "TEST SEVEN"): ByteArray = ByteArray(128).also {
        it[0] = 1
        it.putAscii(1, "ATARI7800")
        it.putAscii(0x11, title, 32, '\u0000')
    }

    fun lynxHeader(title: String = "TEST LYNX"): ByteArray = ByteArray(64).also {
        it.putAscii(0, "LYNX")
        it.putAscii(0x0A, title, 32, '\u0000')
    }

    /** A zip written by the JDK; entries stored or deflated as the JDK chooses (deflated). */
    fun zip(vararg entries: Pair<String, ByteArray>, comment: String? = null, charset: java.nio.charset.Charset = Charsets.UTF_8): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out, charset).use { zip ->
            if (comment != null) zip.setComment(comment)
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
