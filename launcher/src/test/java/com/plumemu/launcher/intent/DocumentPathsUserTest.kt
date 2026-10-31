package com.plumemu.launcher.intent

import androidx.test.core.app.ApplicationProvider
import com.plumemu.library.storage.AndroidStorageRoots
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowEnvironment
import java.nio.file.Paths

/**
 * Paths from Android's own answers ([AndroidStorageRoots]): a second user or a work profile has its own
 * internal storage, `/storage/emulated/<user>`; a volume Android does not list stays at `/storage/<id>`.
 * Android 6 (minSdk) and 14.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 34])
class DocumentPathsUserTest {
    private val roots = AndroidStorageRoots(ApplicationProvider.getApplicationContext())

    private fun path(documentId: String) = DocumentPaths.realPath(DocumentPaths.EXTERNAL_STORAGE_AUTHORITY, documentId, roots)

    @Test
    fun internalStorageIsTheOneAndroidNamesForThisUser() {
        ShadowEnvironment.setExternalStorageDirectory(Paths.get("/storage/emulated/10"))
        assertEquals("/storage/emulated/10/ROMs/snes/x.sfc", path("primary:ROMs/snes/x.sfc"))
        assertEquals("/storage/emulated/10/Documents/ROMs/x.sfc", path("home:ROMs/x.sfc"))
    }

    @Test
    fun aVolumeAndroidDoesNotListIsUnderStorage() {
        assertEquals("/storage/1A2B-3C4D/ROMs/y.gba", path("1A2B-3C4D:ROMs/y.gba"))
        assertEquals("/storage/0123456789ABCDEF/ROMs/y.gba", path("0123456789ABCDEF:ROMs/y.gba"))
    }
}
