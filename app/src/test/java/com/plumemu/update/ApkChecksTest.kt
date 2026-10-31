package com.plumemu.update

import com.plumemu.update.UpdateFixtures.CERT
import com.plumemu.update.UpdateFixtures.goodArchive
import com.plumemu.update.UpdateFixtures.installedFacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The checks after the hash: Plumemu, the offered version, signed exactly like the installed app. */
class ApkChecksTest {
    private val manifest = UpdateManifest(41, "0.0.3-alpha", UpdateFixtures.apkUrl, 1, "0".repeat(64), 23, "2026-10-23", emptyList(), null)

    private fun refused(
        archive: ApkFacts?,
        installed: ApkFacts? = installedFacts,
    ) = ApkChecks.refusal(archive, installed, manifest)?.first

    @Test fun theRightFileIsAccepted() = assertNull(refused(goodArchive))

    @Test fun aFileAndroidCannotReadIsRefused() = assertEquals(UpdateFailure.NotAnApk, refused(null))

    @Test fun anotherPackageIsRefused() = assertEquals(UpdateFailure.WrongPackage, refused(goodArchive.copy(packageName = "com.example.evil")))

    @Test fun anotherVersionIsRefused() = assertEquals(UpdateFailure.WrongVersion, refused(goodArchive.copy(versionCode = 42)))

    @Test fun anotherCertificateIsRefused() = assertEquals(UpdateFailure.WrongSigner, refused(goodArchive.copy(signers = setOf("ab".repeat(32)))))

    /** An extra signer is not "the same": the sets must be equal. */
    @Test fun anExtraCertificateIsRefused() = assertEquals(UpdateFailure.WrongSigner, refused(goodArchive.copy(signers = setOf(CERT, "ab".repeat(32)))))

    @Test fun anUnsignedFileIsRefused() = assertEquals(UpdateFailure.WrongSigner, refused(goodArchive.copy(signers = emptySet())))

    @Test fun withoutTheInstalledCertificatesNothingIsAccepted() {
        assertEquals(UpdateFailure.WrongSigner, refused(goodArchive, installed = null))
        assertEquals(UpdateFailure.WrongSigner, refused(goodArchive.copy(signers = emptySet()), installed = installedFacts.copy(signers = emptySet())))
    }
}
