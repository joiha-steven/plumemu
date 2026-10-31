package com.plumemu.launcher

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.os.ParcelFileDescriptor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmallFileReaderTest {
    private val resolver: ContentResolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
    private val reader = ContentResolverSmallFileReader(resolver)
    private val uri = "content://com.example.provider/tree/root/document/root%2FGame.psvita"

    /** Counts the bytes handed out, to prove a large file is not read whole. */
    private class CountingStream(size: Int) : InputStream() {
        private val inner = ByteArrayInputStream(ByteArray(size) { 'A'.code.toByte() })
        var handedOut = 0

        override fun read(): Int = inner.read().also { if (it >= 0) handedOut++ }

        override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, len).also { if (it > 0) handedOut += it }
    }

    @Test
    fun aShortFileIsReadCompletely() {
        shadowOf(resolver).registerInputStream(Uri.parse(uri), ByteArrayInputStream("PCSE00000\n".toByteArray()))
        assertEquals(SmallFileRead.Read("PCSE00000\n".toByteArray(), complete = true), reader.read(uri, 64))
    }

    @Test
    fun aLargeFileIsCutAfterOneByteMoreThanAsked() {
        val stream = CountingStream(10_000_000)
        shadowOf(resolver).registerInputStream(Uri.parse(uri), stream)
        val read = reader.read(uri, 64)
        assertEquals(SmallFileRead.Read("A".repeat(64).toByteArray(), complete = false), read)
        assertEquals(65, stream.handedOut)
    }

    @Test
    fun aFailingProviderIsUnreadableNotACrash() {
        val failing = object : InputStream() {
            override fun read(): Int = throw java.io.IOException("card removed")
        }
        shadowOf(resolver).registerInputStream(Uri.parse(uri), failing)
        assertTrue(reader.read(uri, 64) is SmallFileRead.Unreadable)
    }

    /** A provider that fails each open its own way; [failure] picks how. */
    class ThrowingProvider : ContentProvider() {
        override fun onCreate() = true
        override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, s: String?, a: Array<out String>?) = 0

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor = throw failure

        companion object {
            var failure: RuntimeException = UnsupportedOperationException()
        }
    }

    @Test
    fun aProviderThatThrowsAnythingIsUnreadableNotACrashNorADatabaseError() {
        Robolectric.setupContentProvider(ThrowingProvider::class.java, "com.example.throwing")
        val throwingUri = "content://com.example.throwing/tree/root/document/root%2FGame.psvita"
        for (failure in listOf(UnsupportedOperationException("no files"), SQLiteException("provider db"), SecurityException("revoked"), IllegalStateException("closed"))) {
            ThrowingProvider.failure = failure
            val read = reader.read(throwingUri, 64)
            assertTrue("${failure.javaClass.simpleName}: $read", read is SmallFileRead.Unreadable)
            assertEquals(failure.javaClass, (read as SmallFileRead.Unreadable).error.javaClass)
        }
    }
}
