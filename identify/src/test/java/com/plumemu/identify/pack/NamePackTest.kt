package com.plumemu.identify.pack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NamePackTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun consoleRoundTripThroughAMappedFile() {
        val file = temp.newFile("megadrive.pack").also { it.writeBytes(TestPacks.console()) }
        val pack = (NamePack.open(file) as PackOpenResult.Opened).pack
        assertEquals(PackKind.CONSOLE, pack.kind)
        assertEquals("megadrive", pack.systemId)
        assertEquals(20261021L, pack.dataVersion)
        assertEquals(3, pack.titleCount)
        assertEquals(4, pack.releaseCount)
        assertTrue(pack.verifyChecksum())
        assertEquals(listOf(PackSource("Test DAT", "2026-10-21")), pack.sources())

        val usa = pack.byCrc(TestPacks.CRC_ZORK, 524288L).single()
        assertEquals("Zork Quest II", usa.title)
        assertEquals(listOf("USA"), usa.regions)
        assertEquals(listOf("En"), usa.languages)
        assertNull(usa.variant)
        assertEquals(1991, usa.year)
        assertEquals("Testsoft", usa.publisher)
        assertEquals("Test DAT", usa.source?.name)

        val europe = pack.byCrc(TestPacks.CRC_ZORK_EUROPE).single()
        assertEquals("Rev 1", europe.variant)
        assertEquals(listOf("En", "Fr"), europe.languages)
    }

    @Test fun crcLookupHonoursSizeAndUnknownSize() {
        val pack = TestPacks.open(TestPacks.console())
        assertTrue("wrong size", pack.byCrc(TestPacks.CRC_ZORK, 1000L).isEmpty())
        assertEquals(1, pack.byCrc(TestPacks.CRC_ZORK, null).size)
        // Stored with unknown size: matches any size. Its high bit is set: unsigned order works.
        assertEquals("Moonlit Harbor", pack.byCrc(TestPacks.CRC_HIGH_BIT, 77L).single().title)
        assertTrue(pack.byCrc(0L).isEmpty())
        assertTrue(pack.byCrc((1L shl 32) - 1).isEmpty())
    }

    @Test fun codeLookupReturnsEveryReleaseWithTheCode() {
        val pack = TestPacks.open(TestPacks.console())
        assertEquals(listOf("USA", "Europe"), pack.byCode("ZZZE").flatMap { it.regions })
        assertEquals("Moonlit Harbor", pack.byCode("GM 00009999-00").single().title)
        assertTrue(pack.byCode("ZZZ").isEmpty())
        assertTrue(pack.byCode("ZZZEE").isEmpty())
        assertTrue(pack.byCode("").isEmpty())
    }

    @Test fun titlesCarryTheirReleasesAndRegions() {
        val pack = TestPacks.open(TestPacks.console())
        val titles = pack.allTitles().toList()
        assertEquals(listOf("Moonlit Harbor", "Zork Quest", "Zork Quest II"), titles.map { it.name })
        val zork = titles[2]
        assertEquals(2, zork.releaseCount)
        assertEquals(listOf("USA", "Europe"), zork.regions)
        assertEquals(listOf("Rev 1"), pack.releasesOf(zork.index).mapNotNull { it.variant })
        assertNull(titles[1].year)
        assertNull(titles[1].publisher)
        assertEquals(1994, titles[0].year)
    }

    @Test fun arcadeRoundTrip() {
        val pack = TestPacks.open(TestPacks.arcade())
        assertEquals(PackKind.ARCADE, pack.kind)
        assertEquals(5, pack.titleCount)
        val clone = pack.byShortName("ZorkFghtJ")
        assertEquals("Zork Fighters (Japan)", clone?.title)
        assertEquals("zorkfght", clone?.parentShortName)
        assertTrue(clone?.isClone == true)
        val parent = pack.byShortName("zorkfght")
        assertNull(parent?.parentShortName)
        assertEquals(1996, parent?.year)
        assertEquals("Testsoft", parent?.maker)
        assertTrue(pack.byShortName("testbios")?.isBios == true)
        assertTrue(pack.byShortName("testdev")?.isDevice == true)
        assertTrue(pack.byShortName("testslot")?.isMechanical == true)
        assertNull(pack.byShortName("zork"))
        assertNull(pack.byShortName("zzzz"))
        assertNull(pack.byShortName(""))
        assertEquals(listOf(PackSource("MAME software lists", "0.270")), pack.sources())
        assertTrue("a console lookup on an arcade pack finds nothing", pack.byCrc(1L).isEmpty())
    }

    @Test fun unknownRegionsAreDroppedWithAWarning() {
        val b = ConsolePackBuilder("nes", 1)
        val t = b.title("Zork Quest")
        b.release(t, regions = listOf("usa", "Atlantis"), languages = listOf("Klingon"))
        val pack = TestPacks.open(b.build())
        assertEquals(listOf("USA"), pack.release(0).regions)
        assertEquals(2, b.warnings.size)
    }

    @Test fun maskBitsAreFixed() {
        assertEquals(32, PackMasks.REGIONS.size)
        assertEquals(32, PackMasks.LANGUAGES.size)
        assertEquals(PackMasks.REGIONS.size, PackMasks.REGIONS.map { it.lowercase() }.toSet().size)
        assertEquals("USA", PackMasks.REGIONS[0])
        assertEquals("Russia", PackMasks.REGIONS[20])
        assertEquals(listOf("USA", "Portugal"), PackMasks.decode(1 or (1 shl 31), PackMasks.REGIONS))
    }

    // ---- rejected packs ----

    @Test fun badMagicIsRejected() {
        val bytes = TestPacks.console().also { it[0] = 'X'.code.toByte() }
        assertRejected(bytes, "magic")
    }

    @Test fun truncatedPackIsRejected() {
        val bytes = TestPacks.console()
        assertRejected(bytes.copyOf(40), "too short")
        assertRejected(bytes.copyOf(bytes.size - 100), "outside")
    }

    @Test fun sectionOutOfBoundsIsRejected() {
        val bytes = TestPacks.console()
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(PackFormat.HEADER_SIZE + 8, bytes.size)
        assertRejected(bytes, "outside")
    }

    @Test fun newerFormatVersionIsRejected() {
        val bytes = TestPacks.console().also { it[8] = 2 }
        assertRejected(bytes, "format version 2")
    }

    @Test fun missingSectionIsRejected() {
        val bytes = TestPacks.console()
        // Rename the CODE section's tag: the pack no longer has one.
        val at = PackFormat.HEADER_SIZE + 5 * PackFormat.SECTION_ENTRY_SIZE
        assertEquals("CODE", String(bytes, at, 4, Charsets.US_ASCII))
        bytes[at] = 'X'.code.toByte()
        assertRejected(bytes, "missing sections [CODE]")
    }

    @Test fun wrongChecksumOpensButFailsVerification() {
        val bytes = TestPacks.console()
        val titleSection = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(PackFormat.HEADER_SIZE + 2 * PackFormat.SECTION_ENTRY_SIZE + 4)
        bytes[titleSection + 8] = (bytes[titleSection + 8] + 1).toByte()
        val pack = TestPacks.open(bytes)
        assertFalse(pack.verifyChecksum())
    }

    @Test fun referenceOutsideThePoolThrowsCorruptPack() {
        val bytes = TestPacks.console()
        val titleSection = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(PackFormat.HEADER_SIZE + 2 * PackFormat.SECTION_ENTRY_SIZE + 4)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(titleSection, 1_000_000)
        val pack = TestPacks.open(bytes)
        try {
            pack.titleName(0)
            throw AssertionError("expected CorruptPackException")
        } catch (e: CorruptPackException) {
            assertTrue(e.message.orEmpty().contains("string reference"))
        }
    }

    @Test fun emptyFileIsRejected() {
        val file = temp.newFile("empty.pack")
        val result = NamePack.open(file)
        assertTrue(result is PackOpenResult.Rejected)
    }

    private fun assertRejected(bytes: ByteArray, reasonPart: String) {
        val result = NamePack.fromBytes(bytes)
        assertTrue("expected rejection, got $result", result is PackOpenResult.Rejected)
        val reason = (result as PackOpenResult.Rejected).reason
        assertTrue("reason '$reason' should mention '$reasonPart'", reason.contains(reasonPart))
    }
}
