package com.plumemu.library.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class ExternalStorageIdsTest {
    private val owner = object : StorageRoots {
        override fun internal() = File("/storage/emulated/0")

        override fun volume(id: String) = File("/storage", id)
    }

    private val secondUser = object : StorageRoots {
        override fun internal() = File("/storage/emulated/10")

        override fun volume(id: String) = File("/storage", id)
    }

    private val tree = "content://${ExternalStorageIds.AUTHORITY}/tree/"

    @Test
    fun rootsAreReadAsBefore() {
        assertEquals(StorageDocument(StorageRoot.Internal, listOf("ROMs", "snes")), ExternalStorageIds.parse("primary:ROMs/snes"))
        assertEquals(StorageDocument(StorageRoot.Internal, emptyList()), ExternalStorageIds.parse("primary:"))
        assertEquals(StorageDocument(StorageRoot.Volume("1A2B-3C4D"), listOf("Games")), ExternalStorageIds.parse("1A2B-3C4D:Games"))
        assertEquals(StorageDocument(StorageRoot.Volume("abcd-ef01"), emptyList()), ExternalStorageIds.parse("abcd-ef01:"))
    }

    @Test
    fun theDocumentsShortcutAndLongVolumeIdsAreRead() {
        val home = ExternalStorageIds.parse("home:ROMs")
        assertEquals(StorageDocument(StorageRoot.Home, listOf("ROMs")), home)
        assertEquals(listOf("Documents", "ROMs"), home?.internalPath)
        assertEquals(StorageRoot.Volume("0123456789ABCDEF"), ExternalStorageIds.parse("0123456789ABCDEF:Games")?.root)
        val ext4 = "3e7b2c1a-5d4f-4a8b-9c0d-1e2f3a4b5c6d"
        assertEquals(StorageRoot.Volume(ext4), ExternalStorageIds.parse("$ext4:Games")?.root)
        assertNull(ExternalStorageIds.parse("1A2B-3C4D:Games")?.internalPath)
    }

    @Test
    fun badIdsAreRefused() {
        for (id in listOf("ROMs/x", ":ROMs", "secondary:ROMs", "../x:ROMs", "1A2B/3C4D:ROMs", "1A2:ROMs", "1A2B-3C:ROMs", "-1A2B:x", "")) {
            assertNull(id, ExternalStorageIds.parse(id))
        }
    }

    @Test
    fun dotAndDotDotAreRefusedUnderEveryRoot() {
        for (id in listOf("primary:ROMs/../../data", "primary:./x", "home:../Android", "1A2B-3C4D:a/../b", "primary:..")) {
            assertNull(id, ExternalStorageIds.parse(id))
        }
    }

    @Test
    fun pathsComeFromTheRoots() {
        assertEquals("/storage/emulated/0/ROMs/x.sfc", ExternalStorageIds.parse("primary:ROMs/x.sfc")?.realPath(owner))
        assertEquals("/storage/emulated/0", ExternalStorageIds.parse("primary:")?.realPath(owner))
        assertEquals("/storage/emulated/10/ROMs/x.sfc", ExternalStorageIds.parse("primary:ROMs/x.sfc")?.realPath(secondUser))
        assertEquals("/storage/emulated/10/Documents/ROMs/x.sfc", ExternalStorageIds.parse("home:ROMs/x.sfc")?.realPath(secondUser))
        assertEquals("/storage/1A2B-3C4D/ROMs/y.gba", ExternalStorageIds.parse("1A2B-3C4D:ROMs/y.gba")?.realPath(owner))
        assertEquals("/storage/0123456789ABCDEF", ExternalStorageIds.parse("0123456789ABCDEF:")?.realPath(owner))
        val noRoots = object : StorageRoots {
            override fun internal(): File? = null

            override fun volume(id: String): File? = null
        }
        assertNull(ExternalStorageIds.parse("primary:x")?.realPath(noRoots))
        assertNull(ExternalStorageIds.parse("1A2B-3C4D:x")?.realPath(noRoots))
    }

    @Test
    fun treeUrisArePercentDecodedAndAPlusStaysAPlus() {
        assertEquals("primary:ROMs+Hacks", ExternalStorageIds.treeDocumentId("${tree}primary%3AROMs+Hacks"))
        assertEquals("primary:ROMs+Hacks", ExternalStorageIds.treeDocumentId("${tree}primary%3AROMs%2BHacks"))
        assertEquals("primary:100% Games/snes", ExternalStorageIds.treeDocumentId("${tree}primary%3A100%25%20Games%2Fsnes"))
        assertEquals("primary:Jeux \u00e9", ExternalStorageIds.treeDocumentId("${tree}primary%3AJeux%20%C3%A9"))
        // A document URI under a tree: the tree's id.
        assertEquals("1A2B-3C4D:ROMs", ExternalStorageIds.treeDocumentId("${tree}1A2B-3C4D%3AROMs/document/1A2B-3C4D%3AROMs%2Fa.sfc"))
    }

    @Test
    fun otherUrisHaveNoTreeId() {
        assertNull(ExternalStorageIds.treeDocumentId("content://com.example.cloud/tree/primary%3AROMs"))
        assertNull(ExternalStorageIds.treeDocumentId("content://${ExternalStorageIds.AUTHORITY}/document/primary%3AROMs"))
        assertNull(ExternalStorageIds.treeDocumentId("${tree}primary%3AROMs%ZZ"))
        assertNull(ExternalStorageIds.treeDocumentId("${tree}primary%3AROMs/other/x"))
        assertNull(ExternalStorageIds.treeDocumentId("not a uri at all"))
        assertNull(ExternalStorageIds.treeDocumentId("file:///storage/emulated/0/ROMs"))
    }
}
