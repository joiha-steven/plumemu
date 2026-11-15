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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Where apps of the Apps entry open: the stored value, an absent key and an unknown value. */
class SecondScreenAppsOnSettingTest {
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
    fun appsOnRoundTripsBothValuesAndIsStoredInLowerCase() = runBlocking {
        for ((value, text) in listOf(AppsScreen.Bottom to "bottom", AppsScreen.Top to "top")) {
            open().setSecondScreen(SecondScreenSettings(appsOn = value))
            closeAll()
            assertEquals(value, open().settings.first().secondScreen.appsOn)
            closeAll()
            val job = Job()
            val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }).data.first()
            job.cancelAndJoin()
            assertEquals(text, raw[SettingsCodec.Keys.secondScreenAppsOn])
        }
    }

    @Test
    fun anAbsentAppsOnKeyReadsAsTop() = runBlocking {
        assertEquals(AppsScreen.Top, open().settings.first().secondScreen.appsOn)
        assertTrue(logLines.toString(), logLines.none { "second_screen.apps_on" in it })
    }

    @Test
    fun anAppsOnValueThisVersionDoesNotKnowReadsAsTopAndLogsTheKey() = runBlocking {
        val job = Job()
        val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        raw.edit { it[SettingsCodec.Keys.secondScreenAppsOn] = "middle" }
        job.cancelAndJoin()

        assertEquals(AppsScreen.Top, open().settings.first().secondScreen.appsOn)
        assertTrue(logLines.toString(), logLines.any { "second_screen.apps_on" in it })
        assertTrue("values are never logged: $logLines", logLines.none { "middle" in it })
    }
}
