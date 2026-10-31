package com.plumemu.coredata.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.plumemu.coredata.account.ScraperAccountStore
import com.plumemu.coredata.settings.SettingsLog
import com.plumemu.coredata.settings.SettingsRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What a backup reads: unlike the screens' flows, which turn a read error into defaults or "signed
 * out", the one-shot reads throw, so the backup fails with its message instead of carrying either.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StrictReadsTest {
    private val log = SettingsLog { }

    /** A store whose every read fails, as storage that does not answer. */
    private val unreadable =
        object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("storage busy") }

            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw IOException("storage busy")
        }

    @Test
    fun aSettingsReadErrorThrows() {
        val failure = runCatching { runBlocking { SettingsRepository(unreadable, log).readOnce() } }.exceptionOrNull()
        assertTrue("got $failure", failure is IOException)
    }

    @Test
    fun anAccountReadErrorThrows() {
        val failure = runCatching { runBlocking { ScraperAccountStore(unreadable, log).readOnce() } }.exceptionOrNull()
        assertTrue("got $failure", failure is IOException)
    }
}
