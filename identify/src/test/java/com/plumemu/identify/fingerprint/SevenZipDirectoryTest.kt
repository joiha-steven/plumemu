package com.plumemu.identify.fingerprint

import com.plumemu.identify.fingerprint.FingerprintReader.Depth
import com.plumemu.identify.source.ByteArraySource
import com.plumemu.identify.source.CountingSource
import com.plumemu.identify.source.RandomAccessSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import java.util.zip.CRC32

/** 7z headers, from the archives `containers/make_fixtures.py` builds byte by byte. */
class SevenZipDirectoryTest {

    private val archives = ContainerFixtures.ofKind("7z")
    private val readable = archives.filter { it.note == null }

    private fun list(bytes: ByteArray, directory: SevenZipDirectory = SevenZipDirectory()) =
        directory.read(ByteArraySource(bytes))

    private fun crc(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset) =
        CRC32().apply { update(bytes, offset, length) }.value

    @Test
    fun everyReadableArchiveListsItsFilesFromTheHeaderOnly() {
        assertEquals(6, readable.size)
        for (fixture in readable) {
            val counting = CountingSource(ByteArraySource(fixture.bytes))
            val listing = SevenZipDirectory().read(counting)
            println("BYTES ${fixture.file} (${fixture.bytes.size} bytes, header ${fixture.fields["header"]}): read ${counting.bytesRead}")
            assertEquals(fixture.file, emptyList<String>(), listing.notes)
            val got = listing.entries.map { ContainerFixtures.Entry(it.name, it.uncompressedSize, it.crc32, it.isDirectory) }
            assertEquals(fixture.file, fixture.entries, got)
            // The signature header, the next header and the packed header: never the file data.
            assertTrue("${fixture.file} read ${counting.bytesRead}", counting.bytesRead < 600)
        }
    }

    @Test
    fun unsupportedHeaderCodersAreNotes() {
        val notes = archives.filter { it.note != null }
        assertEquals(3, notes.size)
        for (fixture in notes) {
            val listing = list(fixture.bytes)
            assertTrue(fixture.file, listing.entries.isEmpty())
            assertTrue("${fixture.file}: ${listing.notes}", listing.notes.single().contains(fixture.note!!))
        }
    }

    @Test
    fun fingerprintsComeFromTheChosenFile() {
        val plain = ContainerFixtures.named("plain.7z")
        val counting = CountingSource(ByteArraySource(plain.bytes))
        val f = FingerprintReader().read(counting, "game.7z", "gba", Depth.QUICK)
        val rom = plain.entries.single()
        assertEquals(Container.SEVEN_ZIP, f.container)
        assertEquals("Test Game \u00e9 (ZZZE).gba", f.innerName)
        assertEquals(rom.crc, f.crc32)
        assertEquals(CrcSource.SEVEN_ZIP_HEADER, f.crcSource)
        assertEquals(40_000L, f.contentSize)
        assertEquals(1, f.entryCount)
        assertEquals(String.format("crc:%08x:40000", rom.crc), f.fixKey())
        // FULL reads the same header and nothing more.
        val full = CountingSource(ByteArraySource(plain.bytes))
        assertEquals(f, FingerprintReader().read(full, "game.7z", "gba", Depth.FULL))
        assertEquals(counting.bytesRead, full.bytesRead)

        val solid = read("solid.7z", "gb")
        assertEquals("Test Game (Disk 2).gb", solid.innerName)
        assertEquals(3, solid.entryCount)
        assertEquals(ContainerFixtures.named("solid.7z").entries[1].crc, solid.crc32)

        val nes = read("lzma2-header.7z", "nes")
        assertEquals("Test Game.nes", nes.innerName)
        assertTrue(nes.notes.toString(), nes.notes.single().contains("16-byte header"))

        val folders = read("empty-and-dir.7z", "gbc")
        assertEquals("roms/Test Game.gbc", folders.innerName)
        assertEquals(2, folders.entryCount)

        val encrypted = read("encrypted-header.7z", "gba")
        assertEquals(Container.SEVEN_ZIP, encrypted.container)
        assertNull(encrypted.crc32)
        assertTrue(encrypted.notes.toString(), encrypted.notes.single().startsWith("7z: header is encrypted"))
    }

    private fun read(file: String, system: String) =
        FingerprintReader().read(ByteArraySource(ContainerFixtures.named(file).bytes), file, system, Depth.QUICK)

    @Test
    fun limitsGiveNotes() {
        val lzma = ContainerFixtures.named("lzma-header.7z").bytes
        assertTrue(list(lzma, SevenZipDirectory(readLimit = 40)).notes.single().contains("over the 40-byte read limit"))
        assertTrue(list(lzma, SevenZipDirectory(decodedLimit = 50)).notes.single().contains("over the 50-byte decoded limit"))
        val plain = ContainerFixtures.named("plain.7z").bytes
        assertTrue(list(plain, SevenZipDirectory(readLimit = 50)).notes.single().contains("over the 50-byte read limit"))
    }

    @Test
    fun theNextHeaderAndThePackedHeaderShareOneReadBudget() {
        val lzma = ContainerFixtures.named("lzma-header.7z").bytes
        val counting = CountingSource(ByteArraySource(lzma))
        SevenZipDirectory().read(counting)
        // What is read besides the 32-byte signature header: the next header and the packed header.
        val both = (counting.bytesRead - 32).toInt()
        assertEquals(emptyList<String>(), list(lzma, SevenZipDirectory(readLimit = both)).notes)
        val over = list(lzma, SevenZipDirectory(readLimit = both - 1))
        assertTrue(over.notes.toString(), over.notes.single().contains("over the ${both - 1}-byte read limit"))
        assertTrue(over.entries.isEmpty())
    }

    @Test
    fun unknownFilePropertiesAreSkippedWhateverTheirId() {
        val name = "a.gba".toByteArray(Charsets.UTF_16LE) + byteArrayOf(0, 0)
        // kHeader, kFilesInfo, 1 file; property 0x99 (a 7z number: 0x80 then the low byte) of 2
        // bytes, then property 0x19 (kDummy) of 1; kEmptyStream and kEmptyFile set; kName; kEnd.
        val header = byteArrayOf(1, 5, 1, 0x80.toByte(), 0x99.toByte(), 2, 0x55, 0x66, 0x19, 1, 0) +
            byteArrayOf(0x0E, 1, 0x80.toByte(), 0x0F, 1, 0x80.toByte(), 0x11, (1 + name.size).toByte(), 0) + name +
            byteArrayOf(0, 0)
        val listing = list(sevenZipOf(header))
        assertEquals(emptyList<String>(), listing.notes)
        assertEquals(listOf(ZipEntryInfo("a.gba", 0, -1, 0, isDirectory = false)), listing.entries)
    }

    @Test
    fun anArchiveOfOnlyEmptyFilesAndDirectoriesHasNoStreams() {
        // No kMainStreamsInfo at all: the header goes straight to kFilesInfo.
        val fixture = ContainerFixtures.named("only-empty.7z")
        val listing = list(fixture.bytes)
        assertEquals(emptyList<String>(), listing.notes)
        assertEquals(listOf(true, false, false), listing.entries.map { it.isDirectory })
        assertTrue(listing.entries.all { it.uncompressedSize == 0L })
        val f = read("only-empty.7z", "gba")
        assertEquals(2, f.entryCount)
        assertNull(f.fixKey())
    }

    @Test
    fun damagedCompressedHeadersAreNotes() {
        for ((file, value) in listOf("lzma-header.7z" to 1, "lzma2-header.7z" to 5)) {
            val fixture = ContainerFixtures.named(file)
            // An LZMA stream must start with 0; 5 is not an LZMA2 control byte.
            val damaged = fixture.bytes.copyOf().also { it[fixture.int("pack")] = value.toByte() }
            val listing = list(damaged)
            assertTrue(file, listing.entries.isEmpty())
            assertTrue("$file: ${listing.notes}", listing.notes.single().startsWith("7z: header LZMA data damaged"))
        }
    }

    @Test(timeout = 60_000)
    fun truncatedArchivesGiveNotes() {
        for (fixture in archives) {
            for (length in 0 until fixture.bytes.size) {
                val listing = list(fixture.bytes.copyOf(length))
                assertTrue("${fixture.file} cut to $length", listing.entries.isEmpty() && listing.notes.size == 1)
            }
        }
    }

    @Test(timeout = 60_000)
    fun damagedHeadersGiveNotesNeverExceptions() {
        val random = Random(7)
        for (fixture in archives) {
            val headerStart = nextHeaderStart(fixture.bytes)
            // Damage the next header and fix its CRCs, so the parser itself meets the bad bytes.
            repeat(1500) { round ->
                val damaged = fixture.bytes.copyOf()
                repeat(1 + round % 6) {
                    val at = headerStart + random.nextInt(damaged.size - headerStart)
                    damaged[at] = if (round % 3 == 0) 0xFF.toByte() else random.nextInt(256).toByte()
                }
                fixCrcs(damaged, headerStart)
                list(damaged)
            }
            // And the packed header, CRCs left as they are.
            val pack = fixture.int("pack")
            if (pack > 0) {
                repeat(300) {
                    val damaged = fixture.bytes.copyOf()
                    damaged[pack + random.nextInt(headerStart - pack)] = random.nextInt(256).toByte()
                    val listing = list(damaged)
                    assertTrue(fixture.file, listing.entries.isEmpty() || listing.entries == list(fixture.bytes).entries)
                }
            }
        }
    }

    @Test(timeout = 10_000)
    fun hugeCountsAreRefusedBeforeAnythingIsAllocated() {
        val huge = byteArrayOf(0xFF.toByte(), -1, -1, -1, -1, -1, -1, -1, 0x7F)
        val headers = mapOf(
            "files" to byteArrayOf(1, 5) + huge + byteArrayOf(0, 0),
            "folders" to byteArrayOf(1, 4, 7, 11) + huge + byteArrayOf(0),
            "coders" to byteArrayOf(1, 4, 7, 11, 1, 0) + huge,
            "streams" to byteArrayOf(1, 4, 7, 11, 1, 0, 1, 1, 0x21, 12, 0x10, 0, 8, 13) + huge + byteArrayOf(9, 0, 0, 0),
            "property" to byteArrayOf(1, 5, 1, 0x11) + huge,
            "external names" to byteArrayOf(1, 5, 1, 0x11, 1, 1, 0, 0),
            "pack sizes" to byteArrayOf(1, 4, 6, 0) + huge,
        )
        for ((what, header) in headers) {
            val listing = list(sevenZipOf(header))
            assertTrue(what, listing.entries.isEmpty())
            assertTrue("$what: ${listing.notes}", listing.notes.single().startsWith("7z: "))
        }
    }

    @Test
    fun anEmptyArchiveListsNothing() {
        val listing = list(sevenZipOf(ByteArray(0)))
        assertEquals(emptyList<ZipEntryInfo>(), listing.entries)
        assertEquals(emptyList<String>(), listing.notes)
    }

    @Test
    fun storageFailuresReachTheCaller() {
        val bytes = ContainerFixtures.named("lzma-header.7z").bytes
        val failing = object : RandomAccessSource {
            override val size = bytes.size.toLong()
            override fun readAt(position: Long, dst: ByteArray, offset: Int, length: Int): Int {
                if (position > 0) throw IOException("card removed")
                return ByteArraySource(bytes).readAt(position, dst, offset, length)
            }
            override fun close() = Unit
        }
        assertThrows(IOException::class.java) { SevenZipDirectory().read(failing) }
    }

    /** A 7z of the signature header and [header] as the next header, CRCs right. */
    private fun sevenZipOf(header: ByteArray): ByteArray {
        val out = ByteBuffer.allocate(32 + header.size).order(ByteOrder.LITTLE_ENDIAN)
        out.put(byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C, 0, 4))
        out.putInt(0).putLong(0).putLong(header.size.toLong()).putInt(crc(header).toInt())
        out.put(header)
        val bytes = out.array()
        fixCrcs(bytes, 32)
        return bytes
    }

    private fun nextHeaderStart(bytes: ByteArray): Int =
        32 + ByteBuffer.wrap(bytes, 12, 8).order(ByteOrder.LITTLE_ENDIAN).long.toInt()

    /** Rewrites the next-header CRC and the start-header CRC after the next header changed. */
    private fun fixCrcs(bytes: ByteArray, headerStart: Int) {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(28, crc(bytes, headerStart).toInt())
        buffer.putInt(8, crc(bytes, 12, 20).toInt())
    }
}
