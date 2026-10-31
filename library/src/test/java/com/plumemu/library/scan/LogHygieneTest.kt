package com.plumemu.library.scan

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.library.matching.SystemRule
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * Providers put the document URI, and so file and folder names, in their exception messages
 * (docs/conventions/code.md). No log line of the scan code may carry one, neither in
 * its text nor through an attached exception, whose stack trace prints the message.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LogHygieneTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, PlumDatabase::class.java).build()
    private val treeUri: Uri = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "root")

    @Before
    fun setUp() {
        ShadowLog.clear()
        Robolectric.setupContentProvider(ContentResolverDocumentTreeTest.MisbehavingProvider::class.java, AUTHORITY)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun aRefusedScanLogsIdsAndTheExceptionClassOnly() = runTest {
        ContentResolverDocumentTreeTest.MisbehavingProvider.answer = { throw SecurityException(PROVIDER_MESSAGE) }
        val folderId = db.scanDao().insertFolder(RomFolderEntity(treeUri = treeUri.toString(), displayName = "ROMs", addedAt = 0))
        val permissions = FakeFolderPermissions().apply { held += treeUri.toString() }
        val tree = ContentResolverDocumentTree(context.contentResolver, treeUri)
        val scanner = LibraryScanner(db, permissions, { tree }, listOf(SystemRule("snes", setOf("snes"), setOf("sfc"))))

        val events = scanner.scan().toList()

        assertEquals(
            listOf(ScanEvent.FolderFinished(folderId, FolderOutcome.Unavailable(UnavailableReason.PERMISSION_REVOKED))),
            events,
        )
        assertTrue("the refusal is logged", lines().any { "SecurityException" in it.msg })
        assertClean()
    }

    @Test
    fun aCrashingProviderLogsTheExceptionClassOnly() {
        ContentResolverDocumentTreeTest.MisbehavingProvider.answer = { throw IllegalArgumentException(PROVIDER_MESSAGE) }
        val tree = ContentResolverDocumentTree(context.contentResolver, treeUri)

        assertTrue(tree.listChildren("root/snes") is TreeResult.Failed)
        assertTrue("the failure is logged", lines().any { "IllegalArgumentException" in it.msg })
        assertClean()
    }

    @Test
    fun aGrantAndroidRefusesLogsTheExceptionClassOnly() {
        val refusing = object : ContentResolver(context) {
            override fun takePersistableUriPermission(uri: Uri, modeFlags: Int) = throw SecurityException(PROVIDER_MESSAGE)

            override fun releasePersistableUriPermission(uri: Uri, modeFlags: Int) = throw SecurityException(PROVIDER_MESSAGE)
        }
        val permissions = AndroidFolderPermissions(refusing)

        assertEquals(false, permissions.take(treeUri.toString()))
        permissions.release(treeUri.toString())

        assertEquals(lines().joinToString { it.msg }, 2, lines().count { "SecurityException" in it.msg })
        assertClean()
    }

    private fun lines(): List<ShadowLog.LogItem> = ShadowLog.getLogsForTag("PlumLibrary")

    private fun assertClean() {
        val all = ShadowLog.getLogs()
        for (line in all) {
            for (secret in SECRETS) assertTrue("'$secret' in log line: ${line.msg}", secret !in line.msg)
            assertNull("an exception attached to a log line prints its message: ${line.msg}", line.throwable)
        }
    }

    private companion object {
        const val AUTHORITY = "com.plumemu.test.loghygiene"
        const val FILE_NAME = "Secret Quest (USA).sfc"
        const val PROVIDER_MESSAGE =
            "Permission Denial: reading content://com.android.externalstorage.documents/tree/primary%3AROMs" +
                "/document/primary%3AROMs%2Fsnes%2F$FILE_NAME requires that you obtain access"
        val SECRETS = listOf(FILE_NAME, "Secret Quest", "primary%3AROMs", "/ROMs")
    }
}
