package com.plumemu.identify.fingerprint.disc

import com.plumemu.identify.source.ByteArraySource
import com.plumemu.identify.source.CountingSource
import com.plumemu.identify.source.RandomAccessSource
import com.plumemu.identify.source.SiblingOpener
import java.io.ByteArrayOutputStream

/**
 * Generated disc images for tests: tiny ISO 9660 volumes, raw CD sectors, PARAM.SFO, PBP, Sega
 * system areas, GameCube / Wii headers. Made-up serials and titles only.
 */
internal object DiscImages {
    const val SECTOR = 2048

    fun ByteArray.putAscii(at: Int, text: String) {
        for (i in text.indices) this[at + i] = text[i].code.toByte()
    }

    fun ByteArray.putU32le(at: Int, value: Long) {
        for (i in 0 until 4) this[at + i] = (value ushr (8 * i)).toByte()
    }

    fun ByteArray.putU32be(at: Int, value: Long) {
        for (i in 0 until 4) this[at + i] = (value ushr (24 - 8 * i)).toByte()
    }

    fun ByteArray.putU64be(at: Int, value: Long) {
        for (i in 0 until 8) this[at + i] = (value ushr (56 - 8 * i)).toByte()
    }

    fun ByteArray.putU64le(at: Int, value: Long) {
        for (i in 0 until 8) this[at + i] = (value ushr (8 * i)).toByte()
    }

    /** A file or a directory for [iso]; a directory's [children] go one level down. */
    class Node(val name: String, val data: ByteArray = ByteArray(0), val children: List<Node>? = null)

    fun file(name: String, text: String) = Node(name, text.toByteArray(Charsets.UTF_8))

    fun file(name: String, data: ByteArray) = Node(name, data)

    fun dir(name: String, vararg children: Node) = Node(name, children = children.toList())

    /**
     * An ISO 9660 image of 2048-byte sectors: system area (sector 0 = [systemArea]), PVD at 16,
     * terminator at 17, root directory at 18, subdirectories and files after, then [padTo] bytes
     * of zeros so the image looks like a real disc to the size checks.
     */
    fun iso(vararg root: Node, systemArea: ByteArray? = null, padTo: Int = 1 shl 20): ByteArray {
        val sectors = ArrayList<ByteArray>()
        repeat(18) { sectors += ByteArray(SECTOR) }
        systemArea?.copyInto(sectors[0])
        sectors[16][0] = 1
        sectors[16].putAscii(1, "CD001")
        sectors[17][0] = 0xFF.toByte()
        sectors[17].putAscii(1, "CD001")
        val (rootLba, rootSize) = writeDirectory(sectors, root.toList())
        sectors[16].copyRecord(156, "\u0000", rootLba, rootSize, directory = true)
        val out = ByteArrayOutputStream()
        sectors.forEach { out.write(it) }
        while (out.size() < padTo) out.write(ByteArray(SECTOR))
        return out.toByteArray()
    }

    /**
     * Writes [nodes] as a directory of as many sectors as its records need (records never cross
     * a sector); returns its LBA and size. Files and subdirectories follow it.
     */
    private fun writeDirectory(sectors: MutableList<ByteArray>, nodes: List<Node>): Pair<Int, Long> {
        val names = listOf("\u0000", "\u0001") + nodes.map { if (it.children != null) it.name else it.name + ";1" }
        val places = ArrayList<Pair<Int, Int>>()
        var sector = 0
        var at = 0
        for (name in names) {
            val length = recordLength(name)
            if (at + length > SECTOR) {
                sector++
                at = 0
            }
            places += sector to at
            at += length
        }
        val lba = sectors.size
        val count = sector + 1
        repeat(count) { sectors += ByteArray(SECTOR) }
        val size = count.toLong() * SECTOR
        fun record(i: Int, target: Int, length: Long, directory: Boolean) =
            sectors[lba + places[i].first].copyRecord(places[i].second, names[i], target, length, directory)
        record(0, lba, size, true)
        record(1, lba, size, true)
        nodes.forEachIndexed { i, node ->
            val children = node.children
            if (children != null) {
                val (child, childSize) = writeDirectory(sectors, children)
                record(i + 2, child, childSize, true)
            } else {
                val first = sectors.size
                val blocks = maxOf(1, (node.data.size + SECTOR - 1) / SECTOR)
                repeat(blocks) { j -> sectors += node.data.copyOfRange(j * SECTOR, minOf(node.data.size, (j + 1) * SECTOR)).copyOf(SECTOR) }
                record(i + 2, first, node.data.size.toLong(), false)
            }
        }
        return lba to size
    }

    private fun recordLength(name: String) = 33 + name.length + if (name.length % 2 == 0) 1 else 0

    private fun ByteArray.copyRecord(at: Int, name: String, lba: Int, size: Long, directory: Boolean): Int {
        val length = recordLength(name)
        this[at] = length.toByte()
        putU32le(at + 2, lba.toLong())
        putU32be(at + 6, lba.toLong())
        putU32le(at + 10, size)
        putU32be(at + 14, size)
        this[at + 25] = if (directory) 2 else 0
        this[at + 32] = name.length.toByte()
        putAscii(at + 33, name)
        return length
    }

    /** The same image in raw 2352-byte sectors, Mode 1 (data at 16) or Mode 2 Form 1 (data at 24). */
    fun toRaw(cooked: ByteArray, mode: Int = 1): ByteArray {
        val count = cooked.size / SECTOR
        val out = ByteArray(count * 2352)
        for (s in 0 until count) {
            val at = s * 2352
            out[at] = 0
            for (i in 1..10) out[at + i] = 0xFF.toByte()
            out[at + 11] = 0
            out[at + 15] = mode.toByte()
            System.arraycopy(cooked, s * SECTOR, out, at + if (mode == 2) 24 else 16, SECTOR)
        }
        return out
    }

    /** A PARAM.SFO holding [values] as UTF-8 strings. */
    fun paramSfo(values: Map<String, String>): ByteArray {
        val keys = ByteArrayOutputStream()
        val data = ByteArrayOutputStream()
        val index = ByteArray(values.size * 16)
        values.entries.forEachIndexed { i, (key, value) ->
            val bytes = value.toByteArray(Charsets.UTF_8) + 0
            val max = (bytes.size + 3) / 4 * 4
            index[i * 16] = keys.size().toByte()
            index[i * 16 + 1] = (keys.size() shr 8).toByte()
            index[i * 16 + 2] = 0x04
            index[i * 16 + 3] = 0x02
            index.putU32le(i * 16 + 4, bytes.size.toLong())
            index.putU32le(i * 16 + 8, max.toLong())
            index.putU32le(i * 16 + 12, data.size().toLong())
            keys.write(key.toByteArray(Charsets.US_ASCII) + 0)
            data.write(bytes.copyOf(max))
        }
        while (keys.size() % 4 != 0) keys.write(0)
        val header = ByteArray(20)
        header.putAscii(1, "PSF")
        header[5] = 1
        header.putU32le(8, (20L + index.size))
        header.putU32le(12, (20L + index.size + keys.size()))
        header.putU32le(16, values.size.toLong())
        return header + index + keys.toByteArray() + data.toByteArray()
    }

    /** A PBP: header, PARAM.SFO, then [payload] standing in for the icon, data and archive sections. */
    fun pbp(sfo: ByteArray, payload: Int = 1 shl 20): ByteArray {
        val header = ByteArray(0x28)
        header.putAscii(1, "PBP")
        header[6] = 1
        header.putU32le(8, 0x28)
        for (i in 1 until 8) header.putU32le(8 + i * 4, 0x28L + sfo.size)
        return header + sfo + ByteArray(payload)
    }

    /** A PSP disc: UMD_DATA.BIN and PSP_GAME/PARAM.SFO. */
    fun pspIso(serial: String = "ULUS-99999", title: String = "TEST GAME: The Portable", padTo: Int = 1 shl 20): ByteArray = iso(
        file("UMD_DATA.BIN", "$serial|0000000000000001|0001|G"),
        dir("PSP_GAME", file("ICON0.PNG", ByteArray(3000)), file("PARAM.SFO", paramSfo(mapOf("CATEGORY" to "UG", "DISC_ID" to serial.replace("-", ""), "TITLE" to title)))),
        padTo = padTo,
    )

    fun ps1Iso(boot: String = "BOOT = cdrom:\\SLUS_999.99;1", padTo: Int = 1 shl 20): ByteArray =
        iso(file("SYSTEM.CNF", "$boot\r\nTCB = 4\r\nEVENT = 10\r\nSTACK = 801FFFF0\r\n"), file("SLUS_999.99", ByteArray(5000)), padTo = padTo)

    fun ps2Iso(padTo: Int = 1 shl 20): ByteArray =
        iso(file("SYSTEM.CNF", "BOOT2 = cdrom0:\\SLES_999.98;1\nVER = 1.00\nVMODE = PAL\n"), padTo = padTo)

    /** Sector 0 of a Sega disc: [id] at 0, the serial and title where that system keeps them. */
    fun segaArea(id: String, serialAt: Int, serial: String, titleAt: Int, title: String): ByteArray =
        ByteArray(SECTOR).also {
            it.fill(' '.code.toByte(), 0, 0x200)
            it.putAscii(0, id)
            it.putAscii(serialAt, serial)
            it.putAscii(titleAt, title)
        }

    fun saturnArea() = segaArea("SEGA SEGASATURN ", 0x20, "T-99999H", 0x60, "TEST GAME")

    fun segaCdArea() = segaArea("SEGADISCSYSTEM  ", 0x180, "GM T-99999 -00", 0x150, "TEST GAME CD")

    fun dreamcastArea() = segaArea("SEGA SEGAKATANA ", 0x40, "T-99999N", 0x80, "TEST GAME DC")

    /** A GameCube or Wii disc header of 0x440 bytes. */
    fun nintendoHeader(id: String = "GZZE01", title: String = "Test Game: Cube Edition", wii: Boolean = false): ByteArray =
        ByteArray(0x440).also {
            it.putAscii(0, id)
            if (wii) it.putU32be(0x18, 0x5D1C9EA3L) else it.putU32be(0x1C, 0xC2339F3DL)
            it.putAscii(0x20, title)
        }

    /** FakeRoms.nds with a version 1 banner at 0x8000 holding [english], header CRC redone. */
    fun ndsWithBanner(english: String): ByteArray {
        val rom = com.plumemu.identify.fingerprint.FakeRoms.nds()
        rom.putU32le(0x68, 0x8000)
        val banner = ByteArray(0x840)
        banner[0] = 1
        english.toByteArray(Charsets.UTF_16LE).copyInto(banner, 0x340)
        val crc = com.plumemu.identify.fingerprint.cart.NdsReader.crc16Modbus(banner, 0x20, 0x820)
        banner[2] = crc.toByte()
        banner[3] = (crc ushr 8).toByte()
        banner.copyInto(rom, 0x8000)
        val headerCrc = com.plumemu.identify.fingerprint.cart.NdsReader.crc16Modbus(rom, 0, 0x15E)
        rom[0x15E] = headerCrc.toByte()
        rom[0x15F] = (headerCrc ushr 8).toByte()
        return rom
    }

    /** Opens names from [files] (as a cue or playlist writes them), recording names and bytes read. */
    class Siblings(private val files: Map<String, ByteArray>) : SiblingOpener {
        val opened = ArrayList<String>()
        private val sources = ArrayList<CountingSource>()

        override fun open(fileName: String): RandomAccessSource? {
            opened += fileName
            val bytes = files[fileName] ?: return null
            return CountingSource(ByteArraySource(bytes)).also { sources += it }
        }

        fun bytesRead(): Long = sources.sumOf { it.bytesRead }
    }
}
