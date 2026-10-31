package com.plumemu.identify.pack

import com.plumemu.identify.arcade.ArcadeLookup
import com.plumemu.identify.arcade.ArcadeSystems
import com.plumemu.identify.codec.lzma.ByteArrayInput
import com.plumemu.identify.codec.lzma.lzmaAloneDecode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * Cross-check of the Python pack tool with this module's reader.
 *
 * `src/test/resources/packs/python/` is the tool's build of its made-up fixture
 * (the tool's test data); the tool's own tests check that the committed files are
 * current. The second test opens the real bundled packs in `src/main/assets/identify/`.
 */
class PythonPacksTest {
    @Test fun fixtureBuildOpensAndFindsTheMadeUpEntries() {
        val index = BundledIndex.parse(resourceText("packs.txt"))
        assertEquals(setOf("arcade", "gba", "gc", "nes", "psx", "switch", "wii"), index.packs.keys)
        val packs = index.packs.values.associate { it.systemId to openChecked(it, resourceBytes(it.fileName)) }

        val nes = packs.getValue("nes")
        assertEquals(listOf(PackSource("No-Intro", "20261001-000000"), PackSource("MAME software lists", "0.289")), nes.sources())
        // CRC of the header-less file: MAME's PRG and CHR chips joined by the tool (crc32_combine).
        val usa = nes.byCrc(NES_FILE_CRC, 24576L).single()
        assertEquals("Zork Quest", usa.title)
        assertEquals(listOf("USA"), usa.regions)
        assertEquals(1991, usa.year)
        assertEquals("Testsoft", usa.publisher)
        assertEquals("No-Intro", usa.source?.name)
        val japan = nes.byCrc(0x11111111L, 40960L).single()
        assertEquals("Rev 1", japan.variant)
        val legend = nes.byCrc(0x22222222L).single()
        assertEquals("The Legend of Testing", legend.title)
        assertEquals(listOf("En", "Fr", "De"), legend.languages)
        assertTrue(nes.allTitles().none { it.name.contains("BIOS") })

        val gba = packs.getValue("gba")
        assertEquals("Moonlit Harbor", gba.byCode("AZZE").single().title)
        assertEquals(listOf("Europe"), gba.byCode("BZZP").single().regions)
        assertTrue(gba.byCode("0000").isEmpty())

        val psx = packs.getValue("psx")
        assertEquals("Disc 2", psx.byCode("SLUS-99902").single().variant)
        assertEquals("Zork Station", psx.byCode("SLPS-99903").single().title)
        assertEquals("Zork Racing", packs.getValue("wii").byCode("RZZE01").single().title)
        assertEquals("Zork Cube", packs.getValue("gc").byCode("GZZP01").single().title)

        val switch = packs.getValue("switch")
        assertEquals(listOf(PackSource("GameTDB", "unknown"), PackSource("titledb", "test")), switch.sources())
        val saga = switch.byCode("0100AAAA00010000").single()
        assertEquals("Zork Switch Saga", saga.title)
        assertEquals(2019, saga.year)
        assertEquals(listOf("USA", "Europe", "Japan"), saga.regions)
        assertEquals("Demo", switch.byCode("0100BBBB00020000").single().variant)
        assertTrue("updates are not listed: identify maps them to their game", switch.byCode("0100AAAA00010800").isEmpty())

        val arcade = packs.getValue("arcade")
        assertEquals("zorkfght", arcade.byShortName("zorkfghtj")?.parentShortName)
        assertTrue(arcade.byShortName("testbios")?.isBios == true)
        assertTrue(arcade.byShortName("testdev")?.isDevice == true)
        assertNull(arcade.byShortName("gplfight"))
        assertNull(arcade.byShortName("zorkgpl")?.parentShortName)

        // MAME 2003-Plus names (the arcade alias test data): records of their own.
        val lookup = ArcadeLookup(arcade)
        val exact = lookup.find("zorkfgtj.zip")!!
        assertEquals("Zork Fighters (Japan 961201)", exact.title)
        assertEquals("Zork Fighters", exact.shortTitle)
        assertEquals("zorkfght", exact.parent)
        assertEquals("MAME 2003-Plus set names", arcade.byShortName("zorkfgtj")?.source?.name)
        val byTitle = lookup.find("zorkold.zip")!!
        assertEquals("Zork Fighters", byTitle.title)
        assertNull(byTitle.maker)
        assertNull(byTitle.parent)
        assertTrue(lookup.find("oldbios.zip")!!.hide)
        // An alias never stands for a set of the same name, nor for a set left out of the pack.
        assertEquals("Zork Fighters (World 961201)", lookup.find("zorkfght.zip")?.title)
        assertNull(arcade.byShortName("zorkgone"))
    }

    @Test fun bundledPacksOpenWithTheirIndexedChecksums() {
        PackAssets.assumePacks()
        val folder = PackAssets.dir
        val index = BundledIndex.parse(File(folder, "packs.txt").readText())
        assertTrue("no bundled packs listed", index.packs.isNotEmpty())
        for (entry in index.packs.values) {
            val pack = openChecked(entry, File(folder, entry.fileName).readBytes())
            assertEquals(entry.dataVersion, pack.dataVersion)
            assertTrue("${entry.systemId}: no sources recorded", pack.sources().isNotEmpty())
            assertEquals(entry.systemId == ArcadeSystems.PACK_ID, pack.kind == PackKind.ARCADE)
        }
        index.aliases.forEach { (system, target) -> assertNotNull("alias $system -> $target", index.packs[target]) }
        val unlisted = folder.list().orEmpty().filter { it.endsWith(".pack.lzma") }.toSet() - index.packs.values.map { it.fileName }.toSet()
        assertEquals(emptySet<String>(), unlisted)
    }

    private fun openChecked(entry: BundledPack, packed: ByteArray): NamePack {
        val bytes = lzmaAloneDecode(ByteArrayInput(packed), PackStore.DEFAULT_MAX_PACK_BYTES.toInt())
        val pack = TestPacks.open(bytes)
        assertTrue("${entry.systemId}: trailer", pack.verifyChecksum())
        assertEquals(entry.systemId, pack.systemId)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes.copyOf(bytes.size - 32))
        assertEquals(entry.sha256, digest.joinToString("") { String.format("%02x", it.toInt() and 0xFF) })
        return pack
    }

    private fun resourceBytes(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/packs/python/$name")) { "missing test resource packs/python/$name" }.use { it.readBytes() }

    private fun resourceText(name: String): String = String(resourceBytes(name), Charsets.UTF_8)

    private companion object {
        /** zlib CRC-32 of the fixture's PRG bytes (0..255 repeated, 16 KB) followed by CHR (0xAA, 8 KB). */
        const val NES_FILE_CRC = 0xB60ACD43L
    }
}
