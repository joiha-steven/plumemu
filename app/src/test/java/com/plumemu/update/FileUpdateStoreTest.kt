package com.plumemu.update

import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The record survives a restart (a reboot) as written; a broken file reads as empty, never crashes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class FileUpdateStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private val logs = mutableListOf<String>()
    private val file by lazy { File(folder.root, "update-record.json") }

    private fun store() = FileUpdateStore(file) { logs += it }

    @Test
    fun aFullRecordComesBackEqual() {
        val manifest = UpdateManifest(41, "0.0.3-alpha", UpdateFixtures.apkUrl, 1234, "ab".repeat(32), 23, "2026-10-23", listOf("One.", "Two."), null)
        val record = UpdateRecord(lastSuccessAt = 1_800_000_000_000, lastAutoFailureAt = 1_800_000_050_000, lastCheck = LastCheck(1_800_000_100_000, CheckOutcome.Failed(CheckFailure.Timeout)), found = manifest)
        store().write(record)
        assertEquals(record, store().read())
        for (outcome in listOf(CheckOutcome.UpToDate, CheckOutcome.Found, CheckOutcome.NeedsNewerAndroid, CheckOutcome.Failed(CheckFailure.BadManifest))) {
            val next = UpdateRecord(lastCheck = LastCheck(5, outcome))
            store().write(next)
            assertEquals(next, store().read())
        }
    }

    @Test
    fun noFileIsAnEmptyRecordWithoutALogLine() {
        assertEquals(UpdateRecord(), store().read())
        assertEquals(emptyList<String>(), logs)
    }

    @Test
    fun aBrokenFileIsAnEmptyRecordAndOneLogLine() {
        file.writeText("{not json")
        assertEquals(UpdateRecord(), store().read())
        assertEquals(1, logs.size)
    }
}
