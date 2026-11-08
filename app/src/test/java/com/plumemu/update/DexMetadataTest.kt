package com.plumemu.update

import android.content.pm.PackageInstaller
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Which `.dm` a device takes, and which installer answers may be the `.dm`'s fault. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class DexMetadataTest {
    private val p = DexMetadataFile(28, 30, "https://h/u/p.dm", 10, "ab".repeat(32))
    private val s = DexMetadataFile(31, null, "https://h/u/s.dm", 10, "cd".repeat(32))

    @Test
    fun eachApiLevelTakesTheEntryOfItsProfileFormat() {
        val entries = listOf(s, p)
        assertNull(DexMetadata.forSdk(entries, 27))
        assertNull(DexMetadata.forSdk(entries, 23))
        for (sdk in 28..30) assertEquals("API $sdk", p, DexMetadata.forSdk(entries, sdk))
        for (sdk in listOf(31, 33, 35, 40)) assertEquals("API $sdk", s, DexMetadata.forSdk(entries, sdk))
    }

    @Test
    fun belowAndroid9NothingIsTakenWhateverTheManifestSays() {
        val tooLow = DexMetadataFile(21, null, "https://h/u/x.dm", 10, "ab".repeat(32))
        for (sdk in 21..27) assertNull("API $sdk", DexMetadata.forSdk(listOf(tooLow), sdk))
        assertEquals(tooLow, DexMetadata.forSdk(listOf(tooLow), 28))
    }

    @Test
    fun aGapInTheRangesTakesNothing() {
        assertNull(DexMetadata.forSdk(listOf(p), 31))
        assertNull(DexMetadata.forSdk(emptyList(), 33))
    }

    @Test
    fun overlappingRangesAreFound() {
        assertFalse(DexMetadata.overlap(listOf(s, p)))
        assertTrue(DexMetadata.overlap(listOf(p, p.copy(minSdk = 30, maxSdk = 31))))
        assertTrue(DexMetadata.overlap(listOf(s, s.copy(minSdk = 40))))
    }

    @Test
    fun onlyAnswersThatABadDmCanCauseAreRetried() {
        assertTrue(DexMetadata.mayComeFromDexMetadata(PackageInstaller.STATUS_FAILURE_INVALID, null))
        assertTrue(DexMetadata.mayComeFromDexMetadata(PackageInstaller.STATUS_FAILURE, "INSTALL_FAILED_INTERNAL_ERROR"))
        assertTrue(DexMetadata.mayComeFromDexMetadata(PackageInstaller.STATUS_FAILURE_STORAGE, "INSTALL_FAILED_BAD_DEX_METADATA: x"))
        for (status in listOf(PackageInstaller.STATUS_FAILURE_STORAGE, PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE, PackageInstaller.STATUS_FAILURE_BLOCKED)) {
            assertFalse("status $status", DexMetadata.mayComeFromDexMetadata(status, "INSTALL_FAILED_UPDATE_INCOMPATIBLE"))
        }
    }
}
