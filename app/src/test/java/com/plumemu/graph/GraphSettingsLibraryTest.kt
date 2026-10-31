package com.plumemu.graph

import com.plumemu.content.LibraryHomeContentTest
import com.plumemu.coredata.db.FolderGameCount
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.SystemGameCount
import com.plumemu.settings.screen.EmulatorEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What Settings › Emulators and ROM folders show, from the shipped platform data: the launch's
 * order (the person's choice, else the first installed default, never a legal-risk one), names and
 * order from the data, folders Plumemu can no longer read.
 */
class GraphSettingsLibraryTest {
    private val catalog = LibraryHomeContentTest.catalog
    private val rows = listOf(SystemGameCount("switch", "switch", 2), SystemGameCount("psx", "psx", 5), SystemGameCount("unknown", "unknown", 1))

    @Test
    fun automaticIsTheFirstInstalledDefaultInTheDataOrder() {
        val systems = systemEmulatorsOf(catalog, rows, setOf("retroarch-swanstation", "duckstation"), emptyMap())
        assertEquals("data order; a system the data does not know has no emulators to pick", listOf("psx", "switch"), systems.map { it.systemId })
        val psx = systems.first()
        assertEquals("PlayStation", psx.name)
        assertEquals("duckstation", psx.automatic?.id)
        assertNull(psx.chosen)
        assertEquals(listOf("duckstation", "retroarch-swanstation"), psx.installed.map { it.id })
        assertEquals(0, psx.currentIndex)
    }

    @Test
    fun aLegalRiskEmulatorIsListedAndChoosableButNeverAutomatic() {
        val switch = systemEmulatorsOf(catalog, rows, setOf("eden"), emptyMap()).single { it.systemId == "switch" }
        assertNull("nothing automatic: None installed", switch.automatic)
        assertEquals(listOf("eden"), switch.installed.map { it.id })
        assertTrue(switch.installed.single().isLegalRisk)

        val chosen = systemEmulatorsOf(catalog, rows, setOf("eden"), mapOf("switch" to "eden")).single { it.systemId == "switch" }
        assertEquals(EmulatorEntry("eden", chosen.chosen?.name.orEmpty(), isLegalRisk = true, isInstalled = true), chosen.chosen)
        assertEquals(1, chosen.currentIndex)
    }

    @Test
    fun aChosenEmulatorSinceUninstalledIsShownAsMissingAndStaysListed() {
        val psx = systemEmulatorsOf(catalog, rows, setOf("duckstation"), mapOf("psx" to "retroarch-swanstation")).first()
        assertEquals("retroarch-swanstation", psx.chosen?.id)
        assertFalse(psx.chosen?.isInstalled ?: true)
        assertEquals(listOf("duckstation", "retroarch-swanstation"), psx.choices.map { it.id })
        assertEquals(2, psx.currentIndex)
    }

    @Test
    fun noEmulatorInstalledLeavesAutomaticEmpty() {
        val psx = systemEmulatorsOf(catalog, rows, emptySet(), emptyMap()).first()
        assertNull(psx.automatic)
        assertEquals(emptyList<EmulatorEntry>(), psx.choices)
    }

    @Test
    fun foldersWithoutAGrantOrUnreadableAtTheLastScanAreMarked() {
        val folders = listOf(folder(1, "a", scanned = true), folder(2, "b", scanned = true), folder(3, "c", scanned = false))
        val counts = listOf(FolderGameCount(1, 10, 2), FolderGameCount(2, 4, 1))
        val shown = settingsFolders(folders, counts, isHeld = { it != "b" }, unreadable = setOf(3L))
        assertEquals(listOf(1L, 2L, 3L), shown.map { it.id })
        assertEquals(listOf(false, true, true), shown.map { it.isUnavailable })
        assertEquals(listOf(10, 4, 0), shown.map { it.gameCount })
        assertEquals(listOf(true, true, false), shown.map { it.isCounted })
    }

    private fun folder(
        id: Long,
        uri: String,
        scanned: Boolean,
    ) = RomFolderEntity(id = id, treeUri = uri, displayName = "", addedAt = 1, lastScanFinishedAt = if (scanned) 2 else null)
}
