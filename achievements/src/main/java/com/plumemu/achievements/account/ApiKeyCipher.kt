package com.plumemu.achievements.account

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.plumemu.achievements.api.RetroAchievementsClient
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts the person's RetroAchievements Web API key before it is stored. Storing the text is not
 * done here: core-data keeps it in its DataStore, and keeps only what [encrypt] returned. Building
 * one touches no key store: the key is made or read on the first [encrypt] or [decrypt].
 */
public interface ApiKeyCipher {
    /** The key as Base64 text. Throws [GeneralSecurityException] when the key store fails. */
    public fun encrypt(plain: String): String

    /**
     * The key, or null when [stored] cannot be read back (changed text, a key lost with a
     * restored backup or a wiped key store, an unknown format): the person signs in again.
     */
    public fun decrypt(stored: String): String?
}

/** Where the AES key comes from: the Android Keystore on a device, a software key in a test. */
internal interface KeySource {
    /** The key to encrypt with, made the first time. */
    fun keyForEncryption(): SecretKey

    /** The key if it exists; null when it was never made or is gone. */
    fun existingKey(): SecretKey?
}

/**
 * AES-256-GCM in a versioned envelope: Base64 of (format version byte, 12-byte IV,
 * ciphertext + 16-byte tag). The version byte is also authenticated (GCM associated data). Each
 * encryption takes a fresh random IV from the cipher. Log lines name the exception class only,
 * never the text.
 */
internal class AesGcmApiKeyCodec(
    private val keys: KeySource,
    private val log: (String) -> Unit,
) : ApiKeyCipher {

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // No IV passed: the cipher draws a random one (the Keystore insists on it).
        val key = try {
            keys.keyForEncryption()
        } catch (e: IOException) {
            throw GeneralSecurityException("key store unreadable", e)
        }
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        if (iv == null || iv.size != IV_BYTES) throw GeneralSecurityException("unexpected IV length")
        cipher.updateAAD(byteArrayOf(VERSION))
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return (byteArrayOf(VERSION) + iv + sealed).toByteString().base64()
    }

    override fun decrypt(stored: String): String? = try {
        open(stored)
    } catch (e: Exception) {
        // Any failure: a changed tag, a wiped key store, and Keystore implementations that throw
        // runtime exceptions of their own. The person signs in again rather than the app crashing.
        log("api key: could not decrypt (${e.javaClass.simpleName})")
        null
    }

    private fun open(stored: String): String? {
        val bytes = stored.decodeBase64()?.toByteArray()
        if (bytes == null || bytes.size < 1 + IV_BYTES + TAG_BYTES) return failed("not a stored key")
        if (bytes[0] != VERSION) return failed("unknown format version")
        val key = keys.existingKey() ?: return failed("key is gone")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BYTES * Byte.SIZE_BITS, bytes, 1, IV_BYTES))
        cipher.updateAAD(byteArrayOf(VERSION))
        val plain = cipher.doFinal(bytes, 1 + IV_BYTES, bytes.size - 1 - IV_BYTES)
        return String(plain, Charsets.UTF_8)
    }

    private fun failed(why: String): String? {
        log("api key: could not decrypt ($why)")
        return null
    }

    companion object {
        const val VERSION: Byte = 1
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BYTES = 16
    }
}

/** An AES-256 key that never leaves the Android Keystore (works from API 23). */
internal class AndroidKeystoreKeySource(private val alias: String = KeystoreApiKeyCipher.KEY_ALIAS) : KeySource {

    override fun existingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(alias, null) as? SecretKey
    }

    // Locked process-wide (every instance shares the Keystore alias) and checked again inside the
    // lock, so two first encryptions at once do not make two keys, one replacing the other.
    override fun keyForEncryption(): SecretKey = synchronized(KEY_LOCK) {
        existingKey() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_BITS)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        val KEY_LOCK = Any()
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_BITS = 256
    }
}

/**
 * [ApiKeyCipher] with its AES key in the Android Keystore under [KEY_ALIAS], its own alias: signing
 * out of ScreenScraper or deleting its key never touches this one.
 */
public class KeystoreApiKeyCipher(
    log: (String) -> Unit = { android.util.Log.w(RetroAchievementsClient.LOG_TAG, it) },
) : ApiKeyCipher by AesGcmApiKeyCodec(AndroidKeystoreKeySource(), log) {
    public companion object {
        public const val KEY_ALIAS: String = "plumemu.retroachievements.apikey"
    }
}
