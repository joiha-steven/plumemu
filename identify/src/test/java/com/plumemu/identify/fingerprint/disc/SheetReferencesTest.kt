package com.plumemu.identify.fingerprint.disc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetReferencesTest {
    @Test
    fun cueNamesEveryFileQuotedOrNot() {
        val cue = """
            FILE "Track 01.bin" BINARY
              TRACK 01 MODE2/2352
                INDEX 01 00:00:00
            FILE Track 02.bin BINARY
              TRACK 02 AUDIO
            file "Track 02.bin" BINARY
            REM FILE "not a file"
        """.trimIndent()
        assertEquals(listOf("Track 01.bin", "Track 02.bin"), SheetReferences.names("cue", cue))
    }

    @Test
    fun gdiNamesItsTracksAndSkipsTheCountLine() {
        val gdi = "3\r\n1 0 4 2352 \"track 01.bin\" 0\r\n2 756 0 2352 track02.raw 0\r\n3 45000 4 2352 track03.bin 0\r\n"
        assertEquals(listOf("track 01.bin", "track02.raw", "track03.bin"), SheetReferences.names("GDI", gdi))
    }

    @Test
    fun m3uNamesEveryLineButCommentsAndBlanks() {
        val m3u = "\uFEFF#EXTM3U\nFF7 (Disc 1).chd\n\n  disc2/FF7 (Disc 2).chd  \n# done\n"
        assertEquals(listOf("FF7 (Disc 1).chd", "disc2/FF7 (Disc 2).chd"), SheetReferences.names("m3u", m3u))
    }

    @Test
    fun onlySheetsAreRecognisedAndEmptyTextNamesNothing() {
        assertTrue(SheetReferences.isSheet("CUE"))
        assertFalse(SheetReferences.isSheet("bin"))
        assertEquals(emptyList<String>(), SheetReferences.names("bin", "FILE \"a.bin\" BINARY"))
        assertEquals(emptyList<String>(), SheetReferences.names("cue", ""))
    }
}
