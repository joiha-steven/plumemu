package com.plumemu.identify.pack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * The bundled packs ship as `.pack.lzma` (LZMA_Alone, written by the Python pack tool); the
 * store unpacks them once into app storage. These tests use the real assets in
 * `src/main/assets/identify/` (the working directory of a module's unit tests is the module).
 */
class BundledLzmaPacksTest {
    @get:Rule val temp = TemporaryFolder()

    private val assets = PackAssets.dir

    @Before fun needThePacks() = PackAssets.assumePacks()

    private fun realBundle(): BundledPacks = object : BundledPacks {
        override fun readIndex(): String = File(assets, "packs.txt").readText()
        override fun open(fileName: String): InputStream = FileInputStream(File(assets, fileName))
    }

    @Test fun everyListedPackDecodesToItsIndexedChecksumAndInstalls() {
        val index = BundledIndex.parse(checkNotNull(realBundle().readIndex()))
        assertTrue("no bundled packs listed", index.packs.isNotEmpty())
        assertTrue("an entry is not .pack.lzma", index.packs.values.all { it.fileName == "${it.systemId}.pack.lzma" })
        val dir = temp.newFolder("packs")
        val log = RecordingLog()
        val store = PackStore(dir, realBundle(), log)
        val started = System.nanoTime()
        for (entry in index.packs.values) {
            val pack = store.packFor(entry.systemId)
            assertNotNull("${entry.systemId}: no pack\n${log.lines}", pack)
            assertEquals(entry.dataVersion, pack!!.dataVersion)
            assertEquals(entry.sha256, pack.checksum)
            assertTrue("${entry.systemId}: trailer", pack.verifyChecksum())
            // The installed file is the raw pack, as it was before the packs were compressed with LZMA.
            assertTrue(File(dir, "${entry.systemId}.pack").length() > 68)
        }
        val millis = (System.nanoTime() - started) / 1_000_000
        println("first-run unpack of ${index.packs.size} bundled packs: $millis ms on the JVM")
        org.junit.Assume.assumeTrue("absolute time budgets are checked on the development machine, not on shared CI runners", System.getProperty("plumemu.timingBudgets") != "false")
        // First-run cost bound: every pack (the largest is 2.6 MB raw, all 85 are 14 MB) decodes in well under a minute on a CI JVM.
        assertTrue("took $millis ms", millis < 60_000)
        assertEquals(emptyList<String>(), log.lines.filter { "failed" in it || "corrupt" in it })
    }

    @Test fun corruptAndTruncatedAssetsAreLoggedByPackNameAndFallBackToNoPack() {
        val good = File(assets, "nes.pack.lzma").readBytes()
        val sha = BundledIndex.parse(File(assets, "packs.txt").readText()).packs.getValue("nes")
        val real = "nes nes.pack.lzma ${sha.dataVersion} ${sha.sha256}\n"
        val flipped = good.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 0x55).toByte() }
        val cases = mapOf(
            "truncated" to good.copyOf(good.size / 2),
            "only the header" to good.copyOf(13),
            "empty" to ByteArray(0),
            "bit flipped" to flipped,
            "garbage" to ByteArray(5000) { (it * 31).toByte() },
        )
        for ((name, bytes) in cases) {
            val log = RecordingLog()
            val store = PackStore(temp.newFolder(), FakeBundled(real, mapOf("nes.pack.lzma" to bytes)), log)
            assertNull("$name: a damaged pack must not be used", store.packFor("nes"))
            assertTrue("$name: the log names the pack\n${log.lines}", log.lines.any { it.startsWith("pack nes:") && "failed" in it })
        }
    }

    @Test fun aStreamThatOutgrowsTheCapIsLoggedAndFallsBack() {
        val overlong = checkNotNull(javaClass.getResourceAsStream("/packs/overlong-bundled.lzma")).use { it.readBytes() }
        val index = "nes nes.pack.lzma 20261021 ${"0".repeat(64)}\n"
        val log = RecordingLog()
        val store = PackStore(temp.newFolder(), FakeBundled(index, mapOf("nes.pack.lzma" to overlong)), log)
        assertNull(store.packFor("nes"))
        assertTrue(log.lines.toString(), log.lines.any { it.startsWith("pack nes:") && "does not end within" in it })
    }
}
