package com.plumemu.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/** What goes into Android's install sessions: the APK first, its `.dm` by the same base name, the APK alone when the `.dm` cannot be written. */
class SessionCommitterTest {
    @get:Rule val folder = TemporaryFolder()

    private val events = mutableListOf<String>()
    private var marker: Long? = null

    private inner class FakeSessions : InstallSessions {
        var next = 1
        var failWriteOf: String? = null
        var failure: Exception = IOException("pipe closed")

        override fun create(totalBytes: Long): Int = next++.also { events += "create $it ($totalBytes bytes)" }

        override fun write(
            sessionId: Int,
            name: String,
            file: File,
        ) {
            if (name == failWriteOf) {
                events += "write $sessionId $name failed"
                throw failure
            }
            events += "write $sessionId $name"
        }

        override fun commit(sessionId: Int) {
            events += "commit $sessionId"
        }

        override fun abandon(sessionId: Int) {
            events += "abandon $sessionId"
        }
    }

    private val sessions = FakeSessions()
    private val logs = mutableListOf<String>()
    private val committer = SessionCommitter(sessions, { marker = it }, { marker = null }, { logs += it })

    private fun file(
        name: String,
        size: Int,
    ) = folder.newFile(name).apply { writeBytes(ByteArray(size)) }

    @Test
    fun theApkThenItsDexMetadataGoInOneSessionUnderOneBaseName() {
        val carried = committer.commit(file("a.apk", 100), 41, file("a.dm", 7))
        assertTrue(carried)
        assertEquals(listOf("create 1 (107 bytes)", "write 1 plumemu-41.apk", "write 1 plumemu-41.dm", "commit 1"), events)
        assertEquals(41L, marker)
    }

    @Test
    fun withoutDexMetadataTheApkGoesAlone() {
        assertFalse(committer.commit(file("a.apk", 100), 41, null))
        assertEquals(listOf("create 1 (100 bytes)", "write 1 plumemu-41.apk", "commit 1"), events)
    }

    @Test
    fun aDexMetadataThatCannotBeWrittenAbandonsThatSessionAndTheApkGoesAloneInANewOne() {
        for (failure in listOf<Exception>(IOException("no space"), IllegalStateException("session sealed"))) {
            events.clear()
            sessions.failWriteOf = "plumemu-41.dm"
            sessions.failure = failure
            val carried = committer.commit(file("a${events.size}${failure.javaClass.simpleName}.apk", 100), 41, file("a${failure.javaClass.simpleName}.dm", 7))
            assertFalse(carried)
            val first = sessions.next - 2
            val second = sessions.next - 1
            assertEquals(
                listOf(
                    "create $first (107 bytes)", "write $first plumemu-41.apk", "write $first plumemu-41.dm failed", "abandon $first",
                    "create $second (100 bytes)", "write $second plumemu-41.apk", "commit $second",
                ),
                events,
            )
            assertEquals("the reopen marker names the version for the APK's session", 41L, marker)
            assertTrue(logs.last(), logs.last().contains("installing the APK alone"))
        }
    }

    @Test
    fun anApkThatCannotBeWrittenFailsWithoutASecondSessionAndClearsTheMarker() {
        sessions.failWriteOf = "plumemu-41.apk"
        try {
            committer.commit(file("a.apk", 100), 41, file("a.dm", 7))
            fail("expected an IOException")
        } catch (e: IOException) {
            // The update fails as before: Android cannot take the APK.
        }
        assertEquals(listOf("create 1 (107 bytes)", "write 1 plumemu-41.apk failed", "abandon 1"), events)
        assertEquals(null, marker)
    }

    @Test
    fun aPermissionTakenBackWhileWritingTheDmIsNotRetried() {
        sessions.failWriteOf = "plumemu-41.dm"
        sessions.failure = SecurityException("REQUEST_INSTALL_PACKAGES")
        try {
            committer.commit(file("a.apk", 100), 41, file("a.dm", 7))
            fail("expected an IOException")
        } catch (e: IOException) {
            // Refused like any session without the permission.
        }
        assertEquals(listOf("create 1 (107 bytes)", "write 1 plumemu-41.apk", "write 1 plumemu-41.dm failed", "abandon 1"), events)
        assertEquals(null, marker)
    }
}
