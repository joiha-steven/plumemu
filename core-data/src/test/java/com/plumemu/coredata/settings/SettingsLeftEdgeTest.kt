package com.plumemu.coredata.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** `appearance.left_edge`: stored, read back, absent in an older file, unknown names ignored. */
class SettingsLeftEdgeTest {
    @get:Rule val tmp = TemporaryFolder()

    private val logLines = CopyOnWriteArrayList<String>()
    private val log = SettingsLog { logLines += it }
    private val scopes = mutableListOf<Job>()
    private val file: File by lazy { File(tmp.root, "settings.preferences_pb") }

    private fun open(): SettingsRepository {
        val job = Job()
        scopes += job
        return SettingsRepository.create({ file }, CoroutineScope(Dispatchers.IO + job), log)
    }

    private suspend fun closeAll() {
        scopes.forEach { it.cancelAndJoin() }
        scopes.clear()
    }

    @After
    fun tearDown() = runBlocking { closeAll() }

    @Test
    fun leftEdgeRoundTripsAndAFileWithoutTheKeyReadsAsNotPicked() = runBlocking {
        assertNull("a fresh file has no left edge", open().settings.first().appearance.leftEdge)
        closeAll()
        for (edge in LeftEdgeSetting.entries) {
            open().setAppearance(AppearanceSettings(leftEdge = edge))
            closeAll()
            assertEquals(edge, open().settings.first().appearance.leftEdge)
            closeAll()
        }
        assertEquals(listOf("Counts", "Codes", "Letters"), LeftEdgeSetting.entries.map { it.name })
        open().setAppearance(AppearanceSettings(leftEdge = null))
        closeAll()
        assertNull(open().settings.first().appearance.leftEdge)
        assertEquals(LeftEdgeSetting.Counts, LeftEdgeSetting.Default)
    }

    @Test
    fun aLeftEdgeNameThisVersionDoesNotKnowReadsAsNotPickedAndLogsTheKey() = runBlocking {
        val job = Job()
        val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        raw.edit { it[SettingsCodec.Keys.leftEdge] = "Sideways" }
        job.cancelAndJoin()
        assertNull(open().settings.first().appearance.leftEdge)
        assertTrue(logLines.toString(), logLines.any { "appearance.left_edge" in it })
        assertTrue("values are never logged: $logLines", logLines.none { "Sideways" in it })
    }
}
