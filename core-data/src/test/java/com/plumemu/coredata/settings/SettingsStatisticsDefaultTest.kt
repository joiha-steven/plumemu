package com.plumemu.coredata.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Usage statistics are off for a new install; an install that finished setup keeps the old "on". */
class SettingsStatisticsDefaultTest {
    @get:Rule val tmp = TemporaryFolder()

    private val file: File by lazy { File(tmp.root, "settings.preferences_pb") }
    private val scopes = mutableListOf<Job>()

    private fun open(): SettingsRepository {
        val job = Job()
        scopes += job
        return SettingsRepository.create({ file }, CoroutineScope(Dispatchers.IO + job), SettingsLog { })
    }

    private fun closeAll() = runBlocking { scopes.forEach { it.cancelAndJoin() }; scopes.clear() }

    @After
    fun tearDown() = closeAll()

    /** Writes [block] into the stored file the way an older build left it, then opens the repository on it. */
    private fun openOnStored(block: (MutablePreferences) -> Unit): SettingsRepository {
        val job = Job()
        val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        runBlocking {
            raw.edit { block(it) }
            job.cancelAndJoin()
        }
        return open()
    }

    @Test
    fun anInstallThatFinishedSetupWithNoStoredStatisticsKeyKeepsTheOldOn() = runBlocking {
        val repo = openOnStored { it[SettingsCodec.Keys.setupCompleted] = true }
        assertEquals("an existing install keeps its old default", true, repo.settings.first().advanced.usageStatistics)
    }

    @Test
    fun aStoredStatisticsChoiceWinsOverTheMigrationFallback() = runBlocking {
        val off =
            openOnStored {
                it[SettingsCodec.Keys.setupCompleted] = true
                it[SettingsCodec.Keys.usageStatistics] = false
            }
        assertEquals(false, off.settings.first().advanced.usageStatistics)
    }

    @Test
    fun finishingSetupLeavesStatisticsOffForANewInstall() = runBlocking {
        open().markSetupCompleted()
        closeAll()
        assertEquals("setup completion does not switch statistics on", false, open().settings.first().advanced.usageStatistics)
    }
}
