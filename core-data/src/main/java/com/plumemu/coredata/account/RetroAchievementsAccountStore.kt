package com.plumemu.coredata.account

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen

/**
 * The person's RetroAchievements account as stored. The Web API key is only the text the app's
 * cipher made of it (achievements' `KeystoreApiKeyCipher`): this module never sees the plain key.
 * [toString] shows neither the name, the ULID nor the ciphertext.
 */
class RetroAchievementsAccount(
    val userName: String,
    /** RetroAchievements' id of the user; calls name the user by it, as user names can change. */
    val ulid: String,
    val keyCiphertext: String,
    /** Hardcore points at the last check, for Settings. */
    val points: Int,
    /**
     * RetroAchievements refused the key after it was stored (it was reset on the site, or it
     * cannot be decrypted any more): Settings asks to sign in again and nothing is asked.
     */
    val isKeyRefused: Boolean = false,
) {
    override fun equals(other: Any?): Boolean =
        other is RetroAchievementsAccount && other.userName == userName && other.ulid == ulid &&
            other.keyCiphertext == keyCiphertext && other.points == points && other.isKeyRefused == isKeyRefused

    override fun hashCode(): Int = listOf(userName, ulid, keyCiphertext, points, isKeyRefused).hashCode()

    override fun toString(): String =
        "RetroAchievementsAccount(userName=***, ulid=***, keyCiphertext=***, points=$points, isKeyRefused=$isKeyRefused)"
}

/**
 * The RetroAchievements account in its own Preferences DataStore file ([FILE_NAME]), apart from the
 * settings and from the ScreenScraper account: Reset all settings does not sign the person out. One
 * instance per process (DataStore refuses two on one file): the app keeps it in its graph.
 *
 * Building one reads nothing; [account] and [isSignedIn] read the file when collected, off the main
 * thread, and never decrypt. A damaged file reads as signed out (one log line); a read error reads
 * as signed out and is tried again later. Log lines name keys and exception classes, never a value.
 */
class RetroAchievementsAccountStore internal constructor(
    private val store: DataStore<Preferences>,
    private val log: SettingsLog,
) {
    val account: Flow<RetroAchievementsAccount?> =
        store.data
            .retryWhen { e, attempt ->
                if (e !is IOException) return@retryWhen false
                log.warn("RetroAchievements account could not be read (${e.javaClass.simpleName}, attempt ${attempt + 1}); signed out until a read works")
                emit(emptyPreferences())
                delay(readRetryDelayMs(attempt))
                true
            }.map(::decode)
            .distinctUntilChanged()

    /** Whether an account is stored: the stored values only, nothing decrypted, nothing asked. */
    val isSignedIn: Flow<Boolean> = account.map { it != null }.distinctUntilChanged()

    /** Stores an account RetroAchievements has just accepted; replaces any earlier one and its refusal. */
    suspend fun signIn(
        userName: String,
        ulid: String,
        keyCiphertext: String,
        points: Int,
    ) {
        require(userName.isNotBlank() && ulid.isNotBlank() && keyCiphertext.isNotBlank()) {
            "an account needs a user name, a ULID and a key"
        }
        store.edit {
            it[Keys.userName] = userName
            it[Keys.ulid] = ulid
            it[Keys.keyCiphertext] = keyCiphertext
            it[Keys.points] = points
            it.remove(Keys.keyRefused)
        }
    }

    /** New points and the name as RetroAchievements now writes it, for the account [ulid]; nothing when another is stored. */
    suspend fun updateProfile(
        ulid: String,
        userName: String,
        points: Int,
    ) {
        store.edit {
            if (it[Keys.ulid] == ulid && userName.isNotBlank()) {
                it[Keys.userName] = userName
                it[Keys.points] = points
            }
        }
    }

    /**
     * The stored key no longer works. Only the account with that very [keyCiphertext] is marked: a
     * call that started with an older key must not mark the one the person signed in with since
     * (every encryption gives a new ciphertext).
     */
    suspend fun markKeyRefused(
        ulid: String,
        keyCiphertext: String,
    ) {
        store.edit { if (it[Keys.ulid] == ulid && it[Keys.keyCiphertext] == keyCiphertext) it[Keys.keyRefused] = true }
    }

    /** Sign out: every stored value goes. */
    suspend fun signOut() {
        store.edit { prefs -> Keys.all.forEach { prefs.remove(it) } }
    }

    private fun decode(p: Preferences): RetroAchievementsAccount? {
        val userName = p[Keys.userName]
        val ulid = p[Keys.ulid]
        val ciphertext = p[Keys.keyCiphertext]
        if (userName.isNullOrBlank() || ulid.isNullOrBlank() || ciphertext.isNullOrBlank()) return null
        return RetroAchievementsAccount(userName, ulid, ciphertext, p[Keys.points] ?: 0, isKeyRefused = p[Keys.keyRefused] == true)
    }

    /** The file format: never rename a key, or the person is signed out. */
    internal object Keys {
        val userName = stringPreferencesKey("retroachievements.user_name")
        val ulid = stringPreferencesKey("retroachievements.ulid")
        val keyCiphertext = stringPreferencesKey("retroachievements.key_ciphertext")
        val points = intPreferencesKey("retroachievements.points")
        val keyRefused = booleanPreferencesKey("retroachievements.key_refused")
        val all: List<Preferences.Key<*>> = listOf(userName, ulid, keyCiphertext, points, keyRefused)
    }

    companion object {
        const val FILE_NAME = "retroachievements_account"

        /** The app's account file. Call once per process; nothing is read until collected. */
        fun create(
            context: Context,
            scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        ): RetroAchievementsAccountStore {
            val app = context.applicationContext
            return create({ app.preferencesDataStoreFile(FILE_NAME) }, scope, AndroidSettingsLog)
        }

        internal fun create(
            file: () -> File,
            scope: CoroutineScope,
            log: SettingsLog,
        ): RetroAchievementsAccountStore {
            val store = PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { e ->
                    log.warn("RetroAchievements account file was damaged (${e.javaClass.simpleName}); signed out")
                    emptyPreferences()
                },
                scope = scope,
                produceFile = file,
            )
            return RetroAchievementsAccountStore(store, log)
        }
    }
}
