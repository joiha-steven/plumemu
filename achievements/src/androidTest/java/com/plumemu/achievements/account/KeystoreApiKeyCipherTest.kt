package com.plumemu.achievements.account

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

/** The real Android Keystore key (API 23+); run on an emulator or device. */
@RunWith(AndroidJUnit4::class)
class KeystoreApiKeyCipherTest {
    private val cipher = KeystoreApiKeyCipher()

    @Test fun roundTripThroughTheKeystore() {
        val first = cipher.encrypt("test-web-api-key-0123")
        val second = cipher.encrypt("test-web-api-key-0123")

        assertNotEquals(first, second)
        assertEquals("test-web-api-key-0123", cipher.decrypt(first))
        assertEquals("test-web-api-key-0123", cipher.decrypt(second))
    }

    @Test fun aDeletedKeyGivesNull() {
        val stored = cipher.encrypt("test-web-api-key-0123")
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(KeystoreApiKeyCipher.KEY_ALIAS)

        assertNull(cipher.decrypt(stored))
    }
}
