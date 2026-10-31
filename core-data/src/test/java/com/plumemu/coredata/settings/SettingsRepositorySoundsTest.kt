package com.plumemu.coredata.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Sounds' three levels on top of the old on/off key, with no change to the stored schema. */
class SettingsRepositorySoundsTest {
    @get:Rule val tmp = TemporaryFolder()

    private val file: File by lazy { File(tmp.root, "settings.preferences_pb") }

    /** Writes [edit] into a fresh file with the raw store, then reads the sounds level through the repository. */
    private fun levelAfter(edit: (androidx.datastore.preferences.core.MutablePreferences) -> Unit): SoundLevel = runBlocking {
        file.delete()
        val rawJob = Job()
        PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + rawJob), produceFile = { file }).edit { edit(it) }
        rawJob.cancelAndJoin()
        val job = Job()
        val level = SettingsRepository.create({ file }, CoroutineScope(Dispatchers.IO + job), SettingsLog { }).settings.first().advanced.sounds
        job.cancelAndJoin()
        level
    }

    @Test
    fun anOldOnSwitchReadsAsLoudAndOffAsOff() {
        assertEquals(SoundLevel.Loud, levelAfter { it[SettingsCodec.Keys.sounds] = true })
        assertEquals(SoundLevel.Off, levelAfter { it[SettingsCodec.Keys.sounds] = false })
    }

    @Test
    fun theLevelKeyBeatsTheOldSwitchAndNeitherKeyMeansLoud() {
        assertEquals(SoundLevel.Quiet, levelAfter { it[SettingsCodec.Keys.sounds] = false; it[SettingsCodec.Keys.soundLevel] = "quiet" })
        assertEquals(SoundLevel.Loud, levelAfter { it[SettingsCodec.Keys.vibration] = true })
    }

    @Test
    fun aLevelSurvivesAWriteAndTheOldKeyIsDropped() = runBlocking {
        file.delete()
        val job = Job()
        val repo = SettingsRepository.create({ file }, CoroutineScope(Dispatchers.IO + job), SettingsLog { })
        repo.update { it.copy(advanced = it.advanced.copy(sounds = SoundLevel.Quiet, vibration = false)) }
        job.cancelAndJoin()
        val job2 = Job()
        val again = SettingsRepository.create({ file }, CoroutineScope(Dispatchers.IO + job2), SettingsLog { }).settings.first().advanced
        job2.cancelAndJoin()
        assertEquals(SoundLevel.Quiet, again.sounds)
        assertEquals(false, again.vibration)
    }

    /** An older build reads only `advanced.sounds`: Off must stay off there, Quiet and Loud read as on. */
    @Test
    fun theOldSwitchIsStillWrittenBesideTheLevel() = runBlocking {
        file.delete()
        val job = Job()
        val repo = SettingsRepository.create({ file }, CoroutineScope(Dispatchers.IO + job), SettingsLog { })
        repo.update { it.copy(advanced = it.advanced.copy(sounds = SoundLevel.Off)) }
        val off = rawSwitch()
        repo.update { it.copy(advanced = it.advanced.copy(sounds = SoundLevel.Quiet)) }
        val quiet = rawSwitch()
        job.cancelAndJoin()
        assertEquals(false, off)
        assertEquals(true, quiet)
    }

    private suspend fun rawSwitch(): Boolean? {
        // The repository's own store is open on the file; a copy is read raw instead.
        val copy = File(tmp.root, "copy.preferences_pb").also { file.copyTo(it, overwrite = true) }
        val job = Job()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { copy })
        val value = store.data.first()[SettingsCodec.Keys.sounds]
        job.cancelAndJoin()
        return value
    }
}
