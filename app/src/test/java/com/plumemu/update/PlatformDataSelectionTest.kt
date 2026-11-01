package com.plumemu.update

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.platforms.PlatformDataVersion
import com.plumemu.update.PlatformDataFixtures.bundled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Which platform data the app starts with, and the files it keeps. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class PlatformDataSelectionTest {
    @get:Rule val folder = TemporaryFolder()

    private val logs = mutableListOf<String>()
    private val infos = mutableListOf<String>()
    private val dir by lazy { File(folder.root, "platform-data") }
    private val store by lazy { PlatformDataStore(dir) { logs += it } }

    private fun select(bundledVersion: PlatformDataVersion = bundled) =
        selectPlatformData(bundledVersion, { PlatformDataFixtures.json.byteInputStream() }, store, { infos += it }, { logs += it })

    private fun stored(version: Int, json: String = PlatformDataFixtures.json) = StoredPlatformData(version, "2026-11-0$version", "ab".repeat(32), json)

    @Test
    fun withNothingStoredTheBundledDataIsUsed() {
        val loaded = select()
        assertNotNull(loaded)
        assertEquals(bundled, loaded!!.version)
        assertTrue(loaded.catalog.systems.size >= 100)
        assertEquals(emptyList<String>(), logs)
    }

    @Test
    fun aStoredCopyThatIsNewerAndLoadsIsUsed() {
        store.write(stored(6))
        val loaded = select()!!
        assertEquals(6, loaded.version.dataVersion)
        assertEquals("2026-11-06", loaded.version.date)
        assertEquals(emptyList<String>(), logs)
    }

    @Test
    fun aStoredCopyThatFailsToLoadFallsBackToTheBundledDataWithOneLogLineAndIsForgotten() {
        store.write(stored(6, json = """{"formatVersion":2,"systems":[],"emulators":[]}"""))
        val loaded = select()!!
        assertEquals(bundled, loaded.version)
        // The loader logs its own line through Android's Log; ours is the only one that reaches `logs`.
        assertEquals(1, logs.size)
        assertTrue(logs[0].contains("does not load"))
        assertNull(store.read())
    }

    @Test
    fun aNewerBundledCopyBeatsAnOlderStoredOneAndTheStoredOneIsDeleted() {
        store.write(stored(3))
        val loaded = select(PlatformDataVersion(5, "00".repeat(32), "2026-11-20"))!!
        assertEquals(5, loaded.version.dataVersion)
        assertNull(store.read())
        assertFalse(File(dir, "record.json").exists())
        assertEquals(emptyList<String>(), logs)
    }

    @Test
    fun aStoredCopyEqualToTheBundledOneIsNotNewerAndIsDeleted() {
        store.write(stored(5))
        assertEquals(5, select(PlatformDataVersion(5, "00".repeat(32), "2026-11-20"))!!.version.dataVersion)
        assertNull(store.read())
    }

    @Test
    fun aBrokenBundledFileGivesNothing() {
        val loaded = selectPlatformData(bundled, { "{}".byteInputStream() }, store, {}, { logs += it })
        assertNull(loaded)
    }

    @Test
    fun aDataFileThatDoesNotMatchItsRecordReadsAsNothing() {
        store.write(stored(6))
        File(dir, "platforms.json").writeText(PlatformDataFixtures.json + " ")
        assertNull(store.read())
        assertEquals(1, logs.size)
        assertEquals(bundled, select()!!.version)
        // Forgotten, so that version can be fetched again.
        assertNull(store.readVersion())
        assertFalse(File(dir, "record.json").exists())
    }

    @Test
    fun aMalformedRecordReadsAsNothingWithOneLogLine() {
        store.write(stored(6))
        File(dir, "record.json").writeText("{broken")
        assertNull(store.read())
        assertEquals(1, logs.size)
    }

    @Test
    fun theVersionIsReadFromTheRecordAloneAndTwoStoresShareOneLock() {
        assertNull(store.readVersion())
        store.write(stored(6))
        // The data file is not read for it: damaged, the version still reads (read() then refuses the copy).
        File(dir, "platforms.json").writeText("damaged")
        assertEquals(6, PlatformDataStore(File(dir.path + "/")) { logs += it }.readVersion())
    }

    @Test
    fun noFilesIsNothingAndNoLogLine() {
        assertNull(store.read())
        assertEquals(emptyList<String>(), logs)
    }

    @Test
    fun aStoredCopyComesBackAsWritten() {
        val data = stored(6)
        store.write(data)
        assertEquals(data, PlatformDataStore(dir) { logs += it }.read())
    }
}
