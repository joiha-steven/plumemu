package com.plumemu.identify.fingerprint

import com.plumemu.identify.fingerprint.FakeRoms.crcOf
import com.plumemu.identify.fingerprint.FingerprintReader.Depth
import com.plumemu.identify.source.ByteArraySource
import com.plumemu.identify.source.CountingSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** `Depth.FULL` unpacks a zipped ROM that may carry a header and gives the loose file's CRC. */
class ZipEntryContentTest {
    private val reader = FingerprintReader()

    private fun read(bytes: ByteArray, system: String, depth: Depth, reader: FingerprintReader = this.reader) =
        reader.read(ByteArraySource(bytes), "game.zip", system, depth)

    private fun key(crc: Long, size: Int) = String.format(Locale.ROOT, "crc:%08x:%d", crc, size)

    @Test
    fun zippedNesGivesTheDirectoryCrcAtQuickAndTheLooseFilesCrcAtFull() {
        val content = FakeRoms.filler(40_960, seed = 21)
        val stored = FakeRoms.withHeader(FakeRoms.inesHeader(), content)
        val zip = FakeRoms.zip("Test Game.nes" to stored)

        val quick = read(zip, "nes", Depth.QUICK)
        assertEquals(crcOf(stored), quick.crc32)
        assertEquals(crcOf(stored), quick.archiveCrc32)
        assertEquals(CrcSource.ZIP_DIRECTORY, quick.crcSource)
        assertEquals(stored.size.toLong(), quick.contentSize)
        assertEquals(0, quick.headerBytesSkipped)
        assertTrue(quick.notes.toString(), quick.notes.single().contains("16-byte header"))
        assertEquals(listOf(key(crcOf(stored), stored.size)), quick.fixKeys())

        val full = read(zip, "nes", Depth.FULL)
        val loose = reader.read(ByteArraySource(stored), "Test Game.nes", "nes", Depth.FULL)
        assertEquals(crcOf(content), full.crc32)
        assertEquals(loose.crc32, full.crc32)
        assertEquals(loose.contentSize, full.contentSize)
        assertEquals(16, full.headerBytesSkipped)
        assertEquals(CrcSource.ZIP_ENTRY_CONTENT, full.crcSource)
        assertEquals(crcOf(stored), full.archiveCrc32)
        assertEquals(emptyList<String>(), full.notes)
        // Content key first, then the key a QUICK read gave: a fix made at either depth is found.
        assertEquals(listOf(key(crcOf(content), content.size), key(crcOf(stored), stored.size)), full.fixKeys())
        assertEquals(loose.fixKey(), full.fixKey())
        assertTrue(full.fixKeys().containsAll(quick.fixKeys()))
    }

    @Test
    fun zippedSnesWithACopierHeader() {
        val rom = FakeRoms.snes(size = 1 shl 20)
        val full = read(FakeRoms.zip("game.smc" to FakeRoms.withHeader(FakeRoms.copierHeader(), rom)), "snes", Depth.FULL)
        assertEquals(crcOf(rom), full.crc32)
        assertEquals(rom.size.toLong(), full.contentSize)
        assertEquals(512, full.headerBytesSkipped)
        assertEquals(CrcSource.ZIP_ENTRY_CONTENT, full.crcSource)
    }

    @Test
    fun zippedSmdIsDeinterleaved() {
        val plain = FakeRoms.megaDrive(size = 1 shl 19)
        val full = read(FakeRoms.zip("game.smd" to FakeRoms.toSmd(plain)), "megadrive", Depth.FULL)
        assertEquals(crcOf(plain), full.crc32)
        assertEquals(plain.size.toLong(), full.contentSize)
        assertEquals(512, full.headerBytesSkipped)
    }

    @Test
    fun zippedV64AndN64ArePutBackInZ64Order() {
        val z64 = FakeRoms.n64(size = 1 shl 20)
        for ((name, stored) in listOf("game.v64" to FakeRoms.byteSwapped(z64), "game.n64" to FakeRoms.wordSwapped(z64))) {
            val full = read(FakeRoms.zip(name to stored), "n64", Depth.FULL)
            assertEquals(name, crcOf(z64), full.crc32)
            assertEquals(name, crcOf(stored), full.archiveCrc32)
            assertEquals(name, 0, full.headerBytesSkipped)
            assertEquals(name, CrcSource.ZIP_ENTRY_CONTENT, full.crcSource)
            assertEquals(name, emptyList<String>(), full.notes)
        }
    }

    @Test
    fun aStoredEntryIsReadToo() {
        val content = FakeRoms.filler(8192, seed = 5)
        val stored = FakeRoms.withHeader(FakeRoms.inesHeader(), content)
        val full = read(storedZip("game.nes", stored), "nes", Depth.FULL)
        assertEquals(crcOf(content), full.crc32)
        assertEquals(16, full.headerBytesSkipped)
    }

    @Test
    fun anEntryWithoutAHeaderIsReadOnlyAsFarAsTheHeaderChecks() {
        val rom = FakeRoms.snes(size = 4 shl 20)
        val counting = CountingSource(ByteArraySource(FakeRoms.zip("game.sfc" to rom)))
        val full = reader.read(counting, "game.zip", "snes", Depth.FULL)
        assertEquals(crcOf(rom), full.crc32)
        assertEquals(CrcSource.ZIP_DIRECTORY, full.crcSource)
        assertEquals(0, full.headerBytesSkipped)
        assertEquals(listOf(key(crcOf(rom), rom.size)), full.fixKeys())
        // The first 128 KB inflated (random filler barely compresses), not the 4 MB entry.
        assertTrue("read ${counting.bytesRead}", counting.bytesRead < 256 * 1024)
        // A format that never carries a header is not opened at all.
        val gba = CountingSource(ByteArraySource(FakeRoms.zip("game.gba" to FakeRoms.gba(size = 1 shl 20))))
        reader.read(gba, "game.zip", "gba", Depth.FULL)
        assertTrue("read ${gba.bytesRead}", gba.bytesRead < 2048)
    }

    @Test
    fun damagedDataKeepsTheDirectoryCrcWithANote() {
        val stored = FakeRoms.withHeader(FakeRoms.inesHeader(), FakeRoms.filler(40_960, seed = 9))
        val text = FakeRoms.withHeader(FakeRoms.inesHeader(), ByteArray(40_960) { "TEST PATTERN "[it % 13].code.toByte() })
        for ((what, entry) in listOf("random" to stored, "compressible" to text)) {
            val zip = FakeRoms.zip("game.nes" to entry)
            val dataStart = 30 + u16(zip, 26) + u16(zip, 28)
            // Near the start: the compressible entry deflates to a few dozen bytes.
            for (i in 0 until 8) zip[dataStart + 10 + i * 3] = (zip[dataStart + 10 + i * 3].toInt() xor 0x5A).toByte()
            val full = read(zip, "nes", Depth.FULL)
            assertEquals(what, crcOf(entry), full.crc32)
            assertEquals(what, CrcSource.ZIP_DIRECTORY, full.crcSource)
            assertEquals(what, 0, full.headerBytesSkipped)
            assertTrue("$what: ${full.notes}", full.notes.any { it.startsWith("zip: game.nes: ") && it.endsWith("the directory CRC (header included) is kept") })
        }
    }

    @Test
    fun aDirectoryThatListsTheWrongLengthIsANote() {
        val stored = FakeRoms.withHeader(FakeRoms.inesHeader(), FakeRoms.filler(8192, seed = 6))
        for ((delta, note) in listOf(1 to "inflates to 8208 of 8209 bytes", -1 to "inflates past its declared 8207 bytes")) {
            val zip = FakeRoms.zip("game.nes" to stored)
            val entry = (0 until zip.size - 4).first { zip[it] == 0x50.toByte() && zip[it + 1] == 0x4B.toByte() && zip[it + 2] == 1.toByte() && zip[it + 3] == 2.toByte() }
            val size = stored.size + delta
            for (i in 0 until 4) zip[entry + 24 + i] = (size ushr (8 * i)).toByte()
            val full = read(zip, "nes", Depth.FULL)
            assertEquals(CrcSource.ZIP_DIRECTORY, full.crcSource)
            assertTrue(full.notes.toString(), full.notes.any { it.contains(note) })
        }
    }

    @Test
    fun anEntryOpenedAtFullWithoutAHeaderDropsTheGuessesMadeFromItsName() {
        // A z64 named .n64, and a .nes whose length suggests an iNES header that is not there.
        val z64 = FakeRoms.n64(size = 1 shl 16)
        val n64 = read(FakeRoms.zip("game.n64" to z64), "n64", Depth.FULL)
        assertEquals(crcOf(z64), n64.crc32)
        assertEquals(CrcSource.ZIP_DIRECTORY, n64.crcSource)
        assertEquals(emptyList<String>(), n64.notes)
        assertTrue(read(FakeRoms.zip("game.n64" to z64), "n64", Depth.QUICK).notes.single().contains("byte-swapped"))
        val nes = FakeRoms.filler(16 + 8192 * 3, seed = 8)
        val full = read(FakeRoms.zip("game.nes" to nes), "nes", Depth.FULL)
        assertEquals(crcOf(nes), full.crc32)
        assertEquals(0, full.headerBytesSkipped)
        assertEquals(emptyList<String>(), full.notes)
        assertTrue(read(FakeRoms.zip("game.nes" to nes), "nes", Depth.QUICK).notes.single().contains("16-byte header"))
    }

    @Test
    fun encryptedEntriesAndOtherMethodsAreNotUnpacked() {
        val stored = FakeRoms.withHeader(FakeRoms.inesHeader(), FakeRoms.filler(8192, seed = 2))
        // (field offset in the central entry, value, note): general-purpose flag bit 0, method.
        val cases = listOf(Triple(8, 1, "game.nes is encrypted"), Triple(10, 12, "uses method 12"), Triple(10, 14, "uses method 14"))
        for ((field, value, note) in cases) {
            val zip = FakeRoms.zip("game.nes" to stored)
            val at = centralEntry(zip) + field
            zip[at] = (zip[at].toInt() or value).toByte()
            val full = read(zip, "nes", Depth.FULL)
            assertEquals(note, crcOf(stored), full.crc32)
            assertEquals(note, CrcSource.ZIP_DIRECTORY, full.crcSource)
            assertTrue("$note: ${full.notes}", full.notes.any { it.contains(note) && it.endsWith("the directory CRC (header included) is kept") })
            // The length still suggests a header: that doubt stays.
            assertTrue(full.notes.toString(), full.notes.any { it.contains("16-byte header") })
        }
    }

    @Test
    fun aLocalExtraFieldLongerThanTheCentralOneIsSkipped() {
        // Info-ZIP "UT" timestamps: 13 bytes in the local header, 9 in the central directory.
        val content = FakeRoms.filler(8192, seed = 12)
        val stored = FakeRoms.withHeader(FakeRoms.inesHeader(), content)
        val full = read(handMadeZip("game.nes", stored, localExtra = ut(13), centralExtra = ut(9)), "nes", Depth.FULL)
        assertEquals(full.notes.toString(), crcOf(content), full.crc32)
        assertEquals(16, full.headerBytesSkipped)
    }

    private fun centralEntry(zip: ByteArray): Int =
        (0 until zip.size - 4).first { zip[it] == 0x50.toByte() && zip[it + 1] == 0x4B.toByte() && zip[it + 2] == 1.toByte() && zip[it + 3] == 2.toByte() }

    /** An "UT" extra field of [total] bytes: id, data length, flags, then timestamps. */
    private fun ut(total: Int): ByteArray = ByteArray(total).also {
        it[0] = 0x55
        it[1] = 0x54
        it[2] = (total - 4).toByte()
        it[4] = 3
    }

    /** One stored entry, local and central headers written by hand so their extras can differ. */
    private fun handMadeZip(name: String, data: ByteArray, localExtra: ByteArray, centralExtra: ByteArray): ByteArray {
        val nameBytes = name.toByteArray(Charsets.US_ASCII)
        val crc = crcOf(data)
        val out = java.nio.ByteBuffer.allocate(30 + nameBytes.size + localExtra.size + data.size + 46 + nameBytes.size + centralExtra.size + 22)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        out.putInt(0x04034B50).putShort(10).putShort(0).putShort(0).putInt(0).putInt(crc.toInt())
        out.putInt(data.size).putInt(data.size).putShort(nameBytes.size.toShort()).putShort(localExtra.size.toShort())
        out.put(nameBytes).put(localExtra).put(data)
        val central = out.position()
        out.putInt(0x02014B50).putShort(20).putShort(10).putShort(0).putShort(0).putInt(0).putInt(crc.toInt())
        out.putInt(data.size).putInt(data.size).putShort(nameBytes.size.toShort()).putShort(centralExtra.size.toShort())
        out.putShort(0).putShort(0).putShort(0).putInt(0).putInt(0)
        out.put(nameBytes).put(centralExtra)
        val centralSize = out.position() - central
        out.putInt(0x06054B50).putShort(0).putShort(0).putShort(1).putShort(1).putInt(centralSize).putInt(central).putShort(0)
        return out.array()
    }

    @Test
    fun aLocalHeaderOutsideTheArchiveIsANote() {
        val zip = FakeRoms.zip("game.nes" to FakeRoms.withHeader(FakeRoms.inesHeader(), FakeRoms.filler(8192)))
        zip[0] = 0 // the local header signature
        val full = read(zip, "nes", Depth.FULL)
        assertEquals(CrcSource.ZIP_DIRECTORY, full.crcSource)
        assertTrue(full.notes.toString(), full.notes.any { it.contains("local header has a bad signature") })
    }

    @Test
    fun anEntryOverTheLimitIsNotUnpacked() {
        val stored = FakeRoms.withHeader(FakeRoms.inesHeader(), FakeRoms.filler(8192))
        val full = read(FakeRoms.zip("game.nes" to stored), "nes", Depth.FULL, FingerprintReader(wholeFileCrcLimit = 4096))
        assertEquals(crcOf(stored), full.crc32)
        assertTrue(full.notes.toString(), full.notes.any { it.contains("over the 4096-byte limit") })
    }

    @Test
    fun aDataPrefixInFrontOfTheArchiveIsAllowedFor() {
        val content = FakeRoms.filler(8192, seed = 4)
        val zip = ByteArray(1000) + FakeRoms.zip("game.nes" to FakeRoms.withHeader(FakeRoms.inesHeader(), content))
        val full = read(zip, "nes", Depth.FULL)
        assertEquals(full.notes.toString(), crcOf(content), full.crc32)
    }

    private fun u16(bytes: ByteArray, at: Int) = (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)

    private fun storedZip(name: String, bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            val entry = ZipEntry(name)
            entry.method = ZipEntry.STORED
            entry.size = bytes.size.toLong()
            entry.crc = CRC32().apply { update(bytes) }.value
            zip.putNextEntry(entry)
            zip.write(bytes)
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    @Test
    fun sevenZipSaysItsHeaderStaysInTheCrc() {
        // lzma2-header.7z holds a 24,592-byte .nes (16 + 3 x 8 KB): its length suggests a header.
        val bytes = ContainerFixtures.named("lzma2-header.7z").bytes
        val full = reader.read(ByteArraySource(bytes), "game.7z", "nes", Depth.FULL)
        assertEquals(CrcSource.SEVEN_ZIP_HEADER, full.crcSource)
        assertEquals(full.crc32, full.archiveCrc32)
        assertTrue(full.notes.toString(), full.notes.any { it.startsWith("7z: file data is never unpacked") })
        assertNull(reader.read(ByteArraySource(bytes), "game.7z", "nes", Depth.QUICK).notes.firstOrNull { it.startsWith("7z: file data") })
    }
}
