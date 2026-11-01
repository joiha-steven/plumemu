package com.plumemu.update

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.update.PlatformDataFixtures.appKey
import com.plumemu.update.PlatformDataFixtures.otherKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPairGenerator

/** The signature check and the manifest text it covers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class PlatformDataSignatureTest {
    private val text = PlatformDataFixtures.signedText(6, 2, 900, "ab".repeat(32))

    private fun bytes(signatureBase64: String) = CertificateSignatureVerifier.decode(signatureBase64)!!

    @Test
    fun theSignedTextIsExactlyTheContractsLines() {
        val manifest = (PlatformDataManifests.read(PlatformDataFixtures.published().manifest, PlatformDataFixtures.MANIFEST_URL) as PlatformDataManifests.Result.Ok).manifest
        assertEquals(PlatformDataFixtures.signedText(6, 2, 900, manifest.sha256), manifest.signedText)
        assertEquals("plumemu-platforms-data\ndataVersion=6\nformatVersion=2\nminVersionCode=900\nsha256=${"ab".repeat(32)}\n", text)
    }

    @Test
    fun anUpperCaseSha256IsAcceptedAndSignedAsLowerCase() {
        val p = PlatformDataFixtures.published(edit = { put("sha256", getString("sha256").uppercase()) })
        val manifest = (PlatformDataManifests.read(p.manifest, PlatformDataFixtures.MANIFEST_URL) as PlatformDataManifests.Result.Ok).manifest
        assertEquals(manifest.sha256, manifest.sha256.lowercase())
        assertEquals(PlatformDataFixtures.FILE_URL, manifest.fileUrl)
    }

    @Test
    fun aSignatureByTheTrustedKeyVerifiesAndOneByAnotherKeyDoesNot() {
        val verifier = CertificateSignatureVerifier { listOf(appKey.public) }
        val bytes = text.toByteArray()
        assertTrue(verifier.verifies(bytes, bytes(PlatformDataFixtures.sign(text, appKey))))
        assertFalse(verifier.verifies(bytes, bytes(PlatformDataFixtures.sign(text, otherKey))))
        assertFalse(verifier.verifies((text + "x").toByteArray(), bytes(PlatformDataFixtures.sign(text, appKey))))
    }

    @Test
    fun anyOneOfTheAppsCertificatesIsEnough() {
        val verifier = CertificateSignatureVerifier { listOf(otherKey.public, appKey.public) }
        assertTrue(verifier.verifies(text.toByteArray(), bytes(PlatformDataFixtures.sign(text, appKey))))
    }

    @Test
    fun noCertificatesOrANonRsaKeyNeverVerify() {
        val signature = bytes(PlatformDataFixtures.sign(text, appKey))
        assertFalse(CertificateSignatureVerifier { emptyList() }.verifies(text.toByteArray(), signature))
        val ec = KeyPairGenerator.getInstance("EC").generateKeyPair().public
        assertFalse(CertificateSignatureVerifier { listOf(ec) }.verifies(text.toByteArray(), signature))
    }

    @Test
    fun garbageIsNotBase64() {
        assertNull(CertificateSignatureVerifier.decode("***"))
        assertNull(CertificateSignatureVerifier.decode(""))
    }

    @Test
    fun anX509CertificateGivesItsRsaKeyAndAnUnreadableOneIsLeftOut() {
        val der = java.util.Base64.getDecoder().decode(PlatformDataFixtures.CERTIFICATE_BASE64)
        val keys = publicKeysOf(listOf(der, "not a certificate".toByteArray()))
        assertEquals(1, keys.size)
        assertEquals("RSA", keys[0].algorithm)
    }
}
