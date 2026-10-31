package com.plumemu.identify.fingerprint.disc

import com.plumemu.identify.fingerprint.Container
import com.plumemu.identify.fingerprint.ContainerFixtures
import com.plumemu.identify.fingerprint.FingerprintReader
import com.plumemu.identify.fingerprint.FingerprintReader.Depth
import com.plumemu.identify.source.ByteArraySource
import com.plumemu.identify.source.CountingSource
import com.plumemu.identify.source.RandomAccessSource
import com.plumemu.identify.source.SiblingOpener
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.Random

/** Every disc fake, whole, truncated and damaged: notes, never an exception; bytes read under the cap. */
class DiscRobustnessTest {

    private val bin = DiscImages.toRaw(DiscImages.ps1Iso(padTo = 256 * 1024), mode = 2)
    private val siblingFiles = mapOf(
        "game.bin" to bin,
        "track03.bin" to DiscImages.toRaw(DiscImages.iso(systemArea = DiscImages.dreamcastArea(), padTo = 64 * 1024)),
        "game.cue" to "FILE \"game.bin\" BINARY\nTRACK 01 MODE2/2352\nINDEX 01 00:00:00\n".toByteArray(),
    )

    /** (name, system, bytes) of every format, each big enough to look like a disc. */
    private val cases: List<Triple<String, String?, ByteArray>> by lazy {
        val wiiHeader = DiscImages.nintendoHeader("RZZE01", "Test Game Wii", wii = true)
        listOf(
            Triple("ps1.iso", "psx", DiscImages.ps1Iso(padTo = 4 shl 20)),
            Triple("ps1.bin", "psx", DiscImages.toRaw(DiscImages.ps1Iso(padTo = 4 shl 20))),
            Triple("ps2.iso", "ps2", DiscImages.ps2Iso(padTo = 4 shl 20)),
            Triple("psp.iso", "psp", DiscImages.pspIso(padTo = 4 shl 20)),
            Triple("psp.cso", "psp", PackedImages.cso(DiscImages.pspIso(padTo = 4 shl 20))),
            Triple("eboot.pbp", "psp", DiscImages.pbp(DiscImages.paramSfo(mapOf("DISC_ID" to "NPUZ99999", "TITLE" to "TEST GAME")))),
            Triple("cd.chd", "psx", PackedImages.chd(PackedImages.toFrames(bin), 2448 * 8, 2448, listOf("cdzl"), eccFrames = setOf(16))),
            Triple("dvd.chd", "ps2", PackedImages.chd(DiscImages.ps2Iso(), 8192, 2048, listOf("zlib"))),
            Triple("plain.chd", "psx", PackedImages.chd(PackedImages.toFrames(bin), 2448 * 8, 2448, compressedMap = false)),
            Triple("gc.iso", "gc", DiscImages.nintendoHeader().copyOf(4 shl 20)),
            Triple("wii.wbfs", "wii", PackedImages.wbfs(wiiHeader)),
            Triple("wii.rvz", "wii", PackedImages.rvz(wiiHeader)),
            Triple("gc.gcz", "gc", PackedImages.gcz(DiscImages.nintendoHeader().copyOf(1 shl 16))),
            Triple("saturn.bin", "saturn", DiscImages.toRaw(DiscImages.iso(systemArea = DiscImages.saturnArea(), padTo = 1 shl 20))),
            Triple("segacd.iso", "megacd", DiscImages.iso(systemArea = DiscImages.segaCdArea(), padTo = 1 shl 20)),
            Triple("game.cue", "psx", siblingFiles.getValue("game.cue")),
            Triple("game.gdi", "dreamcast", "3\n1 0 4 2352 track01.bin 0\n3 45000 4 2352 track03.bin 0\n".toByteArray()),
            Triple("game.m3u", "psx", "game.cue\n".toByteArray()),
            Triple("dc.chd", "dreamcast", PackedImages.dreamcastChd(DiscImages.toRaw(DiscImages.iso(systemArea = DiscImages.dreamcastArea(), padTo = 100 * 2048)))),
            Triple("wii.wia", "wii", PackedImages.rvz(wiiHeader, magic = "WIA")),
            Triple("game.nds", "nds", DiscImages.ndsWithBanner("TEST GAME: Banner\nSub\nMaker")),
            Triple("cdlz.chd", "psx", ChdLzmaTest.chdOf(ContainerFixtures.named("ps1-cd.cdlz.bin"), "cdlz")),
            Triple("cdlz-64k.chd", "psx", ChdLzmaTest.chdOf(ContainerFixtures.named("ps1-cd-64k.cdlz.bin"), "cdlz")),
            Triple("lzma.chd", "psx", ChdLzmaTest.chdOf(ContainerFixtures.named("ps1-dvd.lzma.bin"), "lzma")),
        )
    }

    private fun readAll(bytes: ByteArray, name: String, system: String?, siblings: SiblingOpener? = null) =
        FingerprintReader().read(ByteArraySource(bytes), name, system, Depth.QUICK, siblings)

    @Test
    fun everyFormatIsReadUnderTheCap() {
        for ((name, system, bytes) in cases) {
            val siblings = DiscImages.Siblings(siblingFiles)
            val counting = CountingSource(ByteArraySource(bytes))
            val f = FingerprintReader().read(counting, name, system, Depth.QUICK, siblings)
            val read = counting.bytesRead + siblings.bytesRead()
            println("BYTES $name (${bytes.size} bytes): ${f.productCode} read $read bytes ${f.notes}")
            assertTrue("$name: ${f.notes}", f.productCode != null)
            assertTrue("$name read $read", read < DiscReader.DEFAULT_CAP)
        }
    }

    @Test
    fun truncatedImagesGiveNotesNotExceptions() {
        for ((name, system, bytes) in cases) {
            val lengths = listOf(0, 1, 7, 8, 20, 100, 0x80, 600, 2048, 16 * 2048 + 10, 17 * 2352, 40_000, bytes.size / 2, bytes.size - 1)
            for (length in lengths.filter { it <= bytes.size }) {
                val f = readAll(bytes.copyOf(length), name, system, DiscImages.Siblings(siblingFiles))
                assertTrue("$name cut to $length: ${f.notes}", f.notes.none { it.startsWith("read failed") })
            }
        }
        // Truncated siblings too.
        for (length in listOf(0, 100, 16 * 2352, 20 * 2352)) {
            val cut = siblingFiles.mapValues { (key, value) -> if (key.endsWith(".bin")) value.copyOf(minOf(length, value.size)) else value }
            for (name in listOf("game.cue", "game.gdi", "game.m3u")) {
                val (_, system, bytes) = cases.first { it.first == name }
                val f = readAll(bytes, name, system, DiscImages.Siblings(cut))
                assertTrue("$name with siblings cut to $length: ${f.notes}", f.notes.none { it.startsWith("read failed") })
                assertTrue("$name with siblings cut to $length", f.productCode == null || length >= 16 * 2352)
            }
        }
    }

    @Test
    fun damagedImagesGiveNotesNotExceptions() {
        val random = Random(11)
        for ((name, system, bytes) in cases) {
            // Damage the first 40 KB, where every structure read here lives.
            repeat(60) { round ->
                val damaged = bytes.copyOf()
                val span = minOf(damaged.size, 40 * 1024)
                repeat(1 + round % 12) { damaged[random.nextInt(span)] = random.nextInt(256).toByte() }
                val f = readAll(damaged, name, system, DiscImages.Siblings(siblingFiles))
                assertTrue("$name round $round: ${f.notes}", f.notes.none { it.startsWith("read failed") })
            }
        }
    }

    @Test
    fun theCapStopsAReadWithANote() {
        val notes = ArrayList<String>()
        val f = DiscReader(capBytes = 1000).read(ByteArraySource(DiscImages.ps1Iso()), Container.ISO, "psx", null, notes)
        assertTrue("${f?.notes} $notes", (f?.notes ?: notes).any { it.contains("1000-byte read cap") })
    }

    @Test
    fun siblingFailuresReachTheCaller() {
        val failing = SiblingOpener {
            object : RandomAccessSource {
                override val size = 1L shl 20
                override fun readAt(position: Long, dst: ByteArray, offset: Int, length: Int): Int = throw IOException("card removed")
                override fun close() = Unit
            }
        }
        assertThrows(IOException::class.java) { readAll(siblingFiles.getValue("game.cue"), "game.cue", "psx", failing) }
        val throwing = SiblingOpener { throw IOException("permission revoked") }
        assertThrows(IOException::class.java) { readAll(siblingFiles.getValue("game.cue"), "game.cue", "psx", throwing) }
    }
}
