package com.plumemu.library.scan

import com.plumemu.identify.source.readFully
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/** The files a cue sheet or playlist names, opened through the documents tree. */
class SiblingFilesTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun tree(): FileSystemDocumentTree {
        val root = temp.newFolder("ROMs")
        File(root, "psx").mkdirs()
        File(root, "psx/Zork (Track 1).bin").writeBytes(byteArrayOf(1, 2, 3))
        File(root, "psx/discs").mkdirs()
        File(root, "psx/discs/Zork (Disc 2).cue").writeBytes(byteArrayOf(9))
        File(root, "outside.bin").writeBytes(byteArrayOf(7))
        return FileSystemDocumentTree(root)
    }

    @Test
    fun opensBySameFolderNameIgnoringCaseAndOneFolderDown() {
        val tree = tree()
        val opener = SiblingFiles(tree).openerFor("fs:psx")
        opener.open("Zork (Track 1).bin")!!.use { assertArrayEquals(byteArrayOf(1, 2, 3), it.readFully(0, 10)) }
        opener.open("ZORK (TRACK 1).BIN")!!.use { assertEquals(3L, it.size) }
        opener.open("discs\\Zork (Disc 2).cue")!!.use { assertEquals(1L, it.size) }
        assertTrue(tree.opened.isNotEmpty())
        assertEquals("one listing per directory", 2, tree.listed.size)
    }

    @Test
    fun neverLeavesTheFolderAndAnswersNullForWhatIsNotThere() {
        val opener = SiblingFiles(tree()).openerFor("fs:psx")
        assertNull(opener.open("../outside.bin"))
        assertNull(opener.open("missing.bin"))
        assertNull(opener.open("discs"))
        assertNull(opener.open(""))
    }

    @Test(expected = IOException::class)
    fun aFolderThatCannotBeListedIsAnIoError() {
        val tree = tree()
        tree.failing += "fs:psx"
        SiblingFiles(tree).openerFor("fs:psx").open("Zork (Track 1).bin")
    }
}
