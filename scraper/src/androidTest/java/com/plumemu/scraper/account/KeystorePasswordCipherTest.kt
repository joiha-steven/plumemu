package com.plumemu.scraper.account

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

/** The real Android Keystore key (API 23+); run on an emulator or device. */
@RunWith(AndroidJUnit4::class)
class KeystorePasswordCipherTest {
    private val cipher = KeystorePasswordCipher()

    @Test fun roundTripThroughTheKeystore() {
        val first = cipher.encrypt("test-user-password")
        val second = cipher.encrypt("test-user-password")

        assertNotEquals(first, second)
        assertEquals("test-user-password", cipher.decrypt(first))
        assertEquals("test-user-password", cipher.decrypt(second))
    }

    @Test fun aDeletedKeyGivesNull() {
        val stored = cipher.encrypt("test-user-password")
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(KeystorePasswordCipher.KEY_ALIAS)

        assertNull(cipher.decrypt(stored))
    }
}
