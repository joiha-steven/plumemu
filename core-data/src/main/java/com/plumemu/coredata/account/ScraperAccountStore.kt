package com.plumemu.coredata.account

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.plumemu.coredata.settings.AndroidSettingsLog
import com.plumemu.coredata.settings.SettingsLog
import com.plumemu.coredata.settings.readRetryDelayMs
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen

/**
 * The person's ScreenScraper account as stored. The password is only the
 * text the app's cipher made of it (the scraper's `KeystorePasswordCipher`): this module never sees
 * the plain password. [toString] shows neither the name nor the ciphertext, and the name is a log
 * secret too.
 */
class ScraperAccount(
    val username: String,
    val passwordCiphertext: String,
    /**
     * ScreenScraper refused this name and password after they were stored (the password was changed
     * on the site): Settings says "Wrong password: sign in again" and nothing is scraped.
     */
    val isRefused: Boolean = false,
) {
    override fun equals(other: Any?): Boolean =
        other is ScraperAccount && other.username == username && other.passwordCiphertext == passwordCiphertext && other.isRefused == isRefused

    override fun hashCode(): Int = (username.hashCode() * HASH_PRIME + passwordCiphertext.hashCode()) * HASH_PRIME + isRefused.hashCode()

    override fun toString(): String = "ScraperAccount(username=***, passwordCiphertext=***, isRefused=$isRefused)"

    private companion object {
        const val HASH_PRIME = 31
    }
}

/**
 * The account in its own Preferences DataStore file ([FILE_NAME]), apart from the settings: Reset
 * all settings does not sign the person out, and the settings model every screen reads never
 * carries the account. One instance per process (DataStore refuses two on one file): the app keeps
 * it in its graph.
 *
 * [account] reads off the main thread; null when no account is stored. A damaged file reads as
 * signed out (one log line); a read error reads as signed out and is tried again later, like the
 * settings. Log lines name keys and exception classes, never a value.
 */
class ScraperAccountStore internal constructor(
    private val store: DataStore<Preferences>,
    private val log: SettingsLog,
) {
    val account: Flow<ScraperAccount?> =
        store.data
            .retryWhen { e, attempt ->
                if (e !is IOException) return@retryWhen false
                log.warn("ScreenScraper account could not be read (${e.javaClass.simpleName}, attempt ${attempt + 1}); signed out until a read works")
                emit(emptyPreferences())
                delay(readRetryDelayMs(attempt))
                true
            }.map(::decode)
            .distinctUntilChanged()

    /**
     * One read of the file as it is now, for a backup: unlike [account], a read error is thrown, not
     * read as signed out, so a backup never leaves the account out unseen.
     */
    suspend fun readOnce(): ScraperAccount? = decode(store.data.first())

    /** Stores a name and password ScreenScraper has just accepted; replaces any earlier account and its refusal. */
    suspend fun signIn(
        username: String,
        passwordCiphertext: String,
    ) {
        require(username.isNotBlank() && passwordCiphertext.isNotBlank()) { "an account needs a username and a password" }
        store.edit {
            it[Keys.username] = username
            it[Keys.passwordCiphertext] = passwordCiphertext
            it.remove(Keys.refused)
        }
    }

    /**
     * The stored password no longer works: ScreenScraper refused it, or it cannot be decrypted any
     * more. Only the account with that very [passwordCiphertext] is marked: a run that started with
     * an older password must not mark the one the person signed in with since, even under the same
     * name (every encryption gives a new ciphertext).
     */
    suspend fun markRefused(
        username: String,
        passwordCiphertext: String,
    ) {
        store.edit { if (it[Keys.username] == username && it[Keys.passwordCiphertext] == passwordCiphertext) it[Keys.refused] = true }
    }

    /** Sign out: the name, the ciphertext and the refusal all go. */
    suspend fun signOut() {
        store.edit {
            it.remove(Keys.username)
            it.remove(Keys.passwordCiphertext)
            it.remove(Keys.refused)
        }
    }

    private fun decode(p: Preferences): ScraperAccount? {
        val username = p[Keys.username]
        val ciphertext = p[Keys.passwordCiphertext]
        if (username.isNullOrBlank() || ciphertext.isNullOrBlank()) return null
        return ScraperAccount(username, ciphertext, isRefused = p[Keys.refused] == true)
    }

    /** The file format: never rename a key, or the person is signed out. */
    internal object Keys {
        val username = stringPreferencesKey("screenscraper.username")
        val passwordCiphertext = stringPreferencesKey("screenscraper.password_ciphertext")
        val refused = booleanPreferencesKey("screenscraper.refused")
    }

    companion object {
        const val FILE_NAME = "screenscraper_account"

        /** The app's account file. Call once per process. */
        fun create(
            context: Context,
            scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        ): ScraperAccountStore {
            val app = context.applicationContext
            return create({ app.preferencesDataStoreFile(FILE_NAME) }, scope, AndroidSettingsLog)
        }

        internal fun create(
            file: () -> File,
            scope: CoroutineScope,
            log: SettingsLog,
        ): ScraperAccountStore {
            val store = PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { e ->
                    log.warn("ScreenScraper account file was damaged (${e.javaClass.simpleName}); signed out")
                    emptyPreferences()
                },
                scope = scope,
                produceFile = file,
            )
            return ScraperAccountStore(store, log)
        }
    }
}
