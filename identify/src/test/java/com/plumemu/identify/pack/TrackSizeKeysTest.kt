package com.plumemu.identify.pack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** The `TRKS` section: keys, writer, reader, collisions, packs without it. */
class TrackSizeKeysTest {
    private fun hex(text: String) = java.lang.Long.parseUnsignedLong(text, 16)

    /** Pinned in the Python tool's tests too: the two tools must agree. */
    @Test fun keysAreTheSameAsThePythonTools() {
        assertEquals(hex("67e7cc853f4932dc"), TrackSizeKeys.ordered(listOf(1000000L)))
        assertEquals(hex("c0131545818dc02d"), TrackSizeKeys.ordered(listOf(23520L, 11760L)))
        assertEquals(hex("1567685b1cd00d94"), TrackSizeKeys.total(35280L))
    }

    @Test fun orderAndKindMatter() {
        assertTrue(TrackSizeKeys.ordered(listOf(1L, 2L)) != TrackSizeKeys.ordered(listOf(2L, 1L)))
        assertTrue(TrackSizeKeys.ordered(listOf(7L)) != TrackSizeKeys.total(7L))
    }

    private fun pack(): NamePack {
        val b = ConsolePackBuilder("pcenginecd", 20261101)
        val unique = b.title("Moonlit Harbor")
        val twin = b.title("Twin Quest")
        val other = b.title("Other Quest")
        val single = b.title("Single Bin")
        b.release(unique, regions = listOf("Japan"), trackKeys = listOf(TrackSizeKeys.ordered(listOf(23520L, 11760L, 4704L)), TrackSizeKeys.total(39984L)))
        b.release(twin, regions = listOf("USA"), trackKeys = listOf(TrackSizeKeys.ordered(listOf(9408L, 2352L))))
        b.release(twin, regions = listOf("Europe"), variant = "Rev 1", trackKeys = listOf(TrackSizeKeys.ordered(listOf(9408L, 2352L))))
        b.release(other, regions = listOf("Japan"), trackKeys = listOf(TrackSizeKeys.ordered(listOf(9408L, 2352L))))
        b.release(single, regions = listOf("USA"), trackKeys = listOf(TrackSizeKeys.ordered(listOf(55000L))))
        // The same key twice on one release is stored once.
        b.release(single, regions = listOf("Europe"), trackKeys = listOf(TrackSizeKeys.ordered(listOf(66000L)), TrackSizeKeys.ordered(listOf(66000L))))
        return TestPacks.open(b.build())
    }

    @Test fun roundTripFindsTheDiscByItsOrderedSizes() {
        val hit = pack().byTrackSizes(listOf(23520L, 11760L, 4704L)).single()
        assertEquals("Moonlit Harbor", hit.title)
        assertEquals(listOf("Japan"), hit.regions)
        assertTrue(pack().byTrackSizes(listOf(4704L, 11760L, 23520L)).isEmpty())
        assertTrue(pack().byTrackSizes(listOf(23520L, 11760L)).isEmpty())
        assertTrue(pack().byTrackSizes(emptyList()).isEmpty())
    }

    @Test fun oneFileMatchesTheSumOfAMultiTrackDiscAndASingleTrackDisc() {
        assertEquals("Moonlit Harbor", pack().byTrackSizes(listOf(39984L)).single().title)
        assertEquals("Single Bin", pack().byTrackSizes(listOf(55000L)).single().title)
        assertEquals(1, pack().byTrackSizes(listOf(66000L)).size)
        // The sum is looked up for a single file only: two files never match a total.
        assertTrue(pack().byTrackSizes(listOf(19992L, 19992L)).isEmpty())
    }

    @Test fun sharedKeyReturnsEveryReleaseSoTheCallerSeesTheAmbiguity() {
        val hits = pack().byTrackSizes(listOf(9408L, 2352L))
        assertEquals(listOf("Other Quest", "Twin Quest", "Twin Quest"), hits.map { it.title })
        assertEquals(setOf(null, "Rev 1"), hits.filter { it.title == "Twin Quest" }.map { it.variant }.toSet())
    }

    @Test fun rowsAreSortedByUnsignedKeyThenRelease() {
        val b = ConsolePackBuilder("psx", 1)
        val t = b.title("A")
        // A key with the high bit set sorts after a small one (unsigned), a repeated key by release.
        b.release(t, trackKeys = listOf(-1L, 5L))
        b.release(t, trackKeys = listOf(5L))
        val bytes = b.build()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val count = buffer.getShort(32).toInt()
        val at = (0 until count).map { 36 + it * 12 }.first { String(bytes, it, 4, Charsets.US_ASCII) == "TRKS" }
        val offset = buffer.getInt(at + 4)
        val length = buffer.getInt(at + 8)
        assertEquals(36, length)
        val rows = (0 until 3).map { buffer.getLong(offset + it * 12) to buffer.getInt(offset + it * 12 + 8) }
        assertEquals(listOf(5L to 0, 5L to 1, -1L to 0), rows)
        val pack = TestPacks.open(bytes)
        assertEquals(2, pack.byTrackKey(5L).size)
        assertEquals(1, pack.byTrackKey(-1L).size)
    }

    @Test fun aPackWithoutTheSectionStillReadsAndAnswersNothing() {
        val older = TestPacks.open(TestPacks.console())
        assertTrue(older.byTrackSizes(listOf(1000L)).isEmpty())
        assertTrue(older.byTrackKey(1L).isEmpty())
        assertEquals("Zork Quest II", older.byCrc(TestPacks.CRC_ZORK).single().title)
    }

    @Test fun anArcadePackHasNoTracks() {
        val arcade = TestPacks.open(TestPacks.arcade())
        assertTrue(arcade.byTrackSizes(listOf(1000L)).isEmpty())
    }
}
