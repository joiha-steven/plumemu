package com.plumemu.identify.pack

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden packs: the exact bytes format v1 gives for a small, fixed input (PACKS.md, "Canonical
 * order"). The expected hex was produced by an independent writer made from PACKS.md, not by
 * `PackWriter`; the same bytes are in `src/test/resources/packs/` for the Python tool (I2).
 * If this fails, the writer (or the doc) changed the format: that needs a format version.
 */
class PackGoldenTest {
    /** Input of `console.pack`, in this order. */
    private fun goldenConsole(): ByteArray {
        val b = ConsolePackBuilder("testsys", 20261021)
        val src = b.source("Test DAT", "2026-10-21")
        val zork = b.title("Zork Quest II", year = 1991, publisher = "Testsoft")
        val moon = b.title("Moonlit Harbor")
        // Order changes when lower-cased, equal keys, non-ASCII (see golden.py for the reasons).
        val alpha = b.title("alpha quest")
        val beta = b.title("Beta Quest")
        val zorkLower = b.title("Zork")
        val zorkUpper = b.title("ZORK")
        val poke = b.title("Pok\u00e9 Zork")
        b.release(zork, regions = listOf("USA"), languages = listOf("En"), source = src, crcs = listOf(0x1234ABCDL to 524288L), codes = listOf("ZZZE" to 1))
        b.release(zork, regions = listOf("Europe"), languages = listOf("En", "Fr"), variant = "Rev 1", source = src, crcs = listOf(0xF0000001L to null))
        b.release(moon, regions = listOf("Japan"), languages = listOf("Ja"))
        b.release(alpha, regions = listOf("Europe"))
        b.release(beta, regions = listOf("USA"))
        b.release(zorkLower, regions = listOf("USA"))
        b.release(zorkUpper, regions = listOf("World"))
        b.release(poke, regions = listOf("France"))
        return b.build()
    }

    /** Input of `arcade.pack`, in this order. */
    private fun goldenArcade(): ByteArray {
        val b = ArcadePackBuilder("arcade", 20261021)
        val src = b.source("MAME software lists", "0.270")
        b.set("zorkfghtj", "Zork Fighters (Japan)", maker = "Testsoft", year = 1996, parent = "zorkfght", source = src)
        b.set("zorkfght", "Zork Fighters", maker = "Testsoft", year = 1996, source = src)
        b.set("testbios", "Test System BIOS", flags = ArcadeFlags.BIOS, source = src)
        return b.build()
    }

    @Test fun consoleBytesAreExact() {
        val bytes = goldenConsole()
        assertEquals(CONSOLE_HEX, bytes.toHex())
        assertArrayEquals(resource("console.pack"), bytes)
    }

    @Test fun arcadeBytesAreExact() {
        val bytes = goldenArcade()
        assertEquals(ARCADE_HEX, bytes.toHex())
        assertArrayEquals(resource("arcade.pack"), bytes)
    }

    @Test fun goldenConsoleReadsBack() {
        val pack = TestPacks.open(resource("console.pack"))
        assertTrue(pack.verifyChecksum())
        assertEquals("testsys", pack.systemId)
        assertEquals(20261021L, pack.dataVersion)
        assertEquals(
            listOf("alpha quest", "Beta Quest", "Moonlit Harbor", "Pok\u00e9 Zork", "Zork", "ZORK", "Zork Quest II"),
            pack.allTitles().map { it.name }.toList(),
        )
        val usa = pack.byCrc(0x1234ABCDL, 524288L).single()
        assertEquals(listOf("USA"), usa.regions)
        assertEquals(1991, usa.year)
        assertEquals("Testsoft", usa.publisher)
        val europe = pack.byCrc(0xF0000001L, 1L).single()
        assertEquals("Rev 1", europe.variant)
        assertEquals(listOf("En", "Fr"), europe.languages)
        assertEquals("Zork Quest II", pack.byCode("ZZZE").single().title)
        val moon = pack.releasesOf(2).single()
        assertEquals(listOf("Japan"), moon.regions)
        assertNull(moon.source)
        assertEquals("equal keys keep input order: Zork then ZORK", listOf("USA"), pack.releasesOf(4).single().regions)
        assertEquals(listOf("World"), pack.releasesOf(5).single().regions)
        assertEquals(listOf(PackSource("Test DAT", "2026-10-21")), pack.sources())
    }

    @Test fun goldenArcadeReadsBack() {
        val pack = TestPacks.open(resource("arcade.pack"))
        assertTrue(pack.verifyChecksum())
        assertEquals("zorkfght", pack.byShortName("zorkfghtj")?.parentShortName)
        assertTrue(pack.byShortName("testbios")?.isBios == true)
        assertEquals(listOf("testbios", "zorkfght", "zorkfghtj"), (0 until pack.titleCount).map { pack.arcadeEntry(it).shortName })
    }

    @Test fun codeKindBytesArePinned() {
        assertEquals(
            listOf("GBA" to 1, "NDS" to 2, "GB" to 3, "N64" to 4, "MEGADRIVE" to 5, "SNES" to 6, "PS1" to 7, "PS2" to 8, "PSP" to 9,
                "GAMECUBE" to 10, "WII" to 11, "SATURN" to 12, "SEGACD" to 13, "DREAMCAST" to 14, "SWITCH" to 15, "N3DS" to 16),
            com.plumemu.identify.fingerprint.CodeKind.values().map { it.name to it.packByte },
        )
    }

    @Test fun maskListsArePinned() {
        assertEquals(
            listOf(
                "USA", "Europe", "Japan", "World", "Korea", "China", "Taiwan", "Hong Kong", "Asia", "Brazil",
                "Australia", "Canada", "UK", "France", "Germany", "Spain", "Italy", "Netherlands", "Sweden",
                "Scandinavia", "Russia", "Belgium", "Denmark", "Finland", "Greece", "India", "Latin America",
                "Mexico", "New Zealand", "Norway", "Poland", "Portugal",
            ),
            PackMasks.REGIONS,
        )
        assertEquals(
            listOf(
                "En", "Ja", "Fr", "De", "Es", "It", "Nl", "Pt", "Sv", "No", "Da", "Fi", "Zh", "Ko", "Pl", "Ru",
                "El", "Ca", "Hu", "Cs", "Tr", "Ar", "He", "Hr", "Ro", "Sk", "Sl", "Uk", "Bg", "Id", "Th", "Vi",
            ),
            PackMasks.LANGUAGES,
        )
    }

    private fun resource(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/packs/$name")) { "missing test resource packs/$name" }.use { it.readBytes() }

    private fun ByteArray.toHex(): String = joinToString("") { String.format("%02x", it.toInt() and 0xFF) }

    private companion object {
        const val CONSOLE_HEX =
            "504c4d5041434b00010001009d2835017465737473797300000000000000000006000000535452536c0000007d00000053" +
                "524353ec0000000c0000005449544cf80000008c00000052454c5384010000a0000000435243532402000018000000434f" +
                "44453c0200000c0000000b00616c7068612071756573740a00426574612051756573740e004d6f6f6e6c69742048617262" +
                "6f720a00506f6bc3a9205a6f726b04005a6f726b04005a4f524b0d005a6f726b205175657374204949080054657374736f" +
                "66740500526576203104005a5a5a45080054657374204441540a00323032362d31302d3231000000010000006700000071" +
                "00000000000000ffffffff0000000000000000010000000d000000ffffffff00000000010000000100000019000000ffff" +
                "ffff00000000020000000100000029000000ffffffff00000000030000000100000035000000ffffffff00000000040000" +
                "00010000003b000000ffffffff0000000005000000010000004100000050000000c7070000060000000200000000000000" +
                "0200000000000000ffffffffff000000010000000100000000000000ffffffffff000000020000000400000002000000ff" +
                "ffffffff000000030000000020000000000000ffffffffff000000040000000100000000000000ffffffffff0000000500" +
                "00000800000000000000ffffffffff000000060000000100000001000000ffffffff000000000600000002000000050000" +
                "005a00000000000000cdab34120000080006000000010000f0ffffffff07000000610000000100000006000000f8b311c4" +
                "6f5f9b266d0b499d64cee74aefc9db14a71f60fa2ce7eb17b8b4f8ad"

        const val ARCADE_HEX =
            "504c4d5041434b00010002009d283501617263616465000000000000000000000300000053545253480000007d000000" +
                "53524353c80000000c00000041524344d40000004800000008007465737462696f731000546573742053797374656d20" +
                "42494f5308007a6f726b666768740d005a6f726b204669676874657273080054657374736f667409007a6f726b666768" +
                "746a15005a6f726b20466967687465727320284a6170616e2913004d414d4520736f667477617265206c697374730500" +
                "302e323730000000010000006100000076000000000000000a000000ffffffff00000100ffffffff000000001c000000" +
                "2600000035000000cc070000ffffffff000000003f0000004a00000035000000cc07080001000000000000004e76a925" +
                "8ccee496d49de2d64b2b519a70bd31ae1e908510ccabdc02d2e1c7bf"
    }
}
