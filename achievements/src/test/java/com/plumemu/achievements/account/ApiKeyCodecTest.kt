package com.plumemu.achievements.account

import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** The codec with a software AES key; the Keystore round trip is in androidTest. */
class ApiKeyCodecTest {
    private val logLines = mutableListOf<String>()

    private class SoftwareKeys(var key: SecretKey? = null) : KeySource {
        override fun keyForEncryption(): SecretKey =
            key ?: KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { key = it }

        override fun existingKey(): SecretKey? = key
    }

    private val keys = SoftwareKeys()
    private val codec = AesGcmApiKeyCodec(keys) { logLines += it }

    @Test fun `a key comes back as it went in`() {
        for (plain in listOf("test-web-api-key-0123", "", "p\u00e4ss w\u00f6rd \u2603")) {
            assertEquals(plain, codec.decrypt(codec.encrypt(plain)))
        }
    }

    @Test fun `two encryptions of the same key differ`() {
        val first = codec.encrypt("test-web-api-key-0123")
        val second = codec.encrypt("test-web-api-key-0123")

        assertNotEquals(first, second)
        assertEquals(AesGcmApiKeyCodec.VERSION, first.decodeBase64()!![0])
    }

    @Test fun `changed text gives null, logged without the text`() {
        val stored = codec.encrypt("test-web-api-key-0123")
        val bytes = stored.decodeBase64()!!.toByteArray()
        for (index in listOf(1, 20, bytes.size - 1)) {
            val tampered = bytes.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
            assertNull(codec.decrypt(tampered.toByteString().base64()))
        }
        assertNull(codec.decrypt("not base64 at all !"))
        assertNull(codec.decrypt(""))
        assertTrue(logLines.isNotEmpty())
        assertFalse(logLines.any { it.contains("test-web-api-key-0123") || it.contains(stored) })
    }

    @Test fun `an unknown format version gives null`() {
        val bytes = codec.encrypt("test-web-api-key-0123").decodeBase64()!!.toByteArray()
        bytes[0] = 2

        assertNull(codec.decrypt(bytes.toByteString().base64()))
        assertTrue(logLines.any { it.contains("unknown format version") })
    }

    @Test fun `a lost or replaced key gives null`() {
        val stored = codec.encrypt("test-web-api-key-0123")

        keys.key = null
        assertNull(codec.decrypt(stored))

        keys.keyForEncryption()
        assertNull(codec.decrypt(stored))
    }

    @Test fun `building the Keystore cipher touches no key store`() {
        // There is no AndroidKeyStore on the JVM: any key store access here would throw.
        KeystoreApiKeyCipher(log = { logLines += it })
        assertTrue(logLines.isEmpty())
    }

    @Test fun `the key has its own alias, not the ScreenScraper password's`() {
        assertEquals("plumemu.retroachievements.apikey", KeystoreApiKeyCipher.KEY_ALIAS)
        assertNotEquals("plumemu.screenscraper.password", KeystoreApiKeyCipher.KEY_ALIAS)
    }
}
