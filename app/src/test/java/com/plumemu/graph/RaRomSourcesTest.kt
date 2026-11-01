package com.plumemu.graph

import com.plumemu.library.scan.DocumentEntry
import com.plumemu.library.scan.DocumentTree
import com.plumemu.library.scan.OpenedFile
import com.plumemu.library.scan.TreeResult
import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** A library file as the RetroAchievements hasher reads it: by plain path and through a document tree. */
class RaRomSourcesTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var root: File

    @Before
    fun setUp() {
        root = temp.newFolder("psx")
        File(root, "Game.cue").writeText("FILE \"game.bin\" BINARY")
        File(root, "Game.bin").writeBytes(byteArrayOf(1, 2, 3))
        File(root, "Tracks").mkdir()
        File(root, "Tracks/Track02.bin").writeBytes(byteArrayOf(4, 5))
    }

    @Test
    fun aPlainFileHasItsPathExactSizeAndBytes() {
        val cue = FileRaRomSource(File(root, "Game.cue"))
        assertEquals(File(root, "Game.cue").path, cue.key)
        assertEquals("Game.cue", cue.fileName)
        assertEquals(File(root, "Game.cue").length(), cue.sizeBytes)
        val bin = cue.sibling("Game.bin")!!
        assertArrayEquals(byteArrayOf(1, 2, 3), bin.open().use { it.readBytes() })
        assertEquals(3L, bin.sizeBytes)
    }

    @Test
    fun aPlainFileFindsSiblingsIgnoringCaseAndInSubfolders() {
        val cue = FileRaRomSource(File(root, "Game.cue"))
        // A case-insensitive disk (macOS) finds the exact-name stat; elsewhere the listing does: same bytes either way.
        assertArrayEquals(byteArrayOf(1, 2, 3), cue.sibling("game.BIN")!!.open().use { it.readBytes() })
        assertArrayEquals(byteArrayOf(4, 5), cue.sibling("tracks/track02.bin")!!.open().use { it.readBytes() })
        assertEquals("Track02.bin", cue.sibling("Tracks\\Track02.bin")?.fileName)
        assertNull(cue.sibling("missing.bin"))
        assertNull("a folder is not a file", cue.sibling("Tracks"))
        assertNull(cue.sibling("../psx/Game.bin"))
    }

    @Test
    fun aDocumentReadsThroughTheTreeAndFindsSiblingsByName() {
        val tree = FileTree(root)
        val cue = DocumentRaRomSource(tree, "Game.cue", "", "content://tree/psx/Game.cue", "Game.cue", File(root, "Game.cue").length())
        assertEquals("FILE \"game.bin\" BINARY", cue.open().use { it.readBytes().decodeToString() })
        val bin = cue.sibling("game.bin")!!
        assertEquals("Game.bin", bin.fileName)
        assertEquals(3L, bin.sizeBytes)
        assertArrayEquals(byteArrayOf(1, 2, 3), bin.open().use { it.readBytes() })
        val track = cue.sibling("TRACKS/track02.BIN")!!
        assertArrayEquals(byteArrayOf(4, 5), track.open().use { it.readBytes() })
        assertNull(cue.sibling("missing.bin"))
        assertNull(cue.sibling("Tracks"))
    }

    @Test
    fun aDocumentHashListsEachFolderOnce() {
        val tree = FileTree(root)
        val cue = DocumentRaRomSource(tree, "Game.cue", "", "content://tree/psx/Game.cue", "Game.cue", 1)
        cue.sibling("Game.bin")
        cue.sibling("game.bin")
        val track = cue.sibling("Tracks/Track02.bin")!!
        track.sibling("Track02.bin")
        assertEquals(mapOf("" to 1, "Tracks" to 1), tree.listings)
    }

    @Test
    fun aDocumentThatCannotBeOpenedThrowsWithoutItsName() {
        val tree = FileTree(root)
        val gone = DocumentRaRomSource(tree, "Secret Game.bin", "", "content://tree/psx/Secret%20Game.bin", "Secret Game.bin", 3)
        val e = runCatching { gone.open() }.exceptionOrNull()
        assert(e is IOException)
        assert(!e!!.message.orEmpty().contains("Secret")) { e.message.orEmpty() }
    }

    /** A document tree over a JVM folder: document ids are paths relative to it ("" is the root). */
    private class FileTree(private val base: File) : DocumentTree {
        override val rootDocumentId: String = ""

        private fun fileOf(id: String) = if (id.isEmpty()) base else File(base, id)

        private fun entryOf(id: String, file: File) = DocumentEntry(id, file.name, if (file.isFile) file.length() else 0, file.lastModified(), file.isDirectory)

        override fun stat(documentId: String): TreeResult<DocumentEntry> {
            val file = fileOf(documentId)
            return if (file.exists()) TreeResult.Found(entryOf(documentId, file)) else TreeResult.Failed(IOException("none"))
        }

        val listings = HashMap<String, Int>()

        override fun listChildren(documentId: String): TreeResult<List<DocumentEntry>> {
            listings[documentId] = (listings[documentId] ?: 0) + 1
            val children = fileOf(documentId).listFiles() ?: return TreeResult.Failed(IOException("not a folder"))
            return TreeResult.Found(children.map { entryOf(if (documentId.isEmpty()) it.name else "$documentId/${it.name}", it) })
        }

        override fun open(documentId: String): TreeResult<OpenedFile> {
            val file = fileOf(documentId)
            if (!file.isFile) return TreeResult.Failed(IOException("no file"))
            val channel = FileChannel.open(file.toPath(), StandardOpenOption.READ)
            return TreeResult.Found(
                object : OpenedFile {
                    override val channel: FileChannel = channel

                    override fun close() = channel.close()
                },
            )
        }
    }
}
