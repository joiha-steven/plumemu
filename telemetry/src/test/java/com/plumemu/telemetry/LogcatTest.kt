package com.plumemu.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch

/** The logcat part of a snapshot: only this process's lines, the newest, never longer than its timeout. */
class LogcatTest {
    private val sample =
        """
        --------- beginning of main
        10-24 08:00:00.100  4242  4242 I Plumemu: ours
        10-24 08:00:00.200  1111  1111 W OtherApp: someone else's secret
        10-24 08:00:00.300  4242  4300 E Telemetry: ours too
        10-24 08:00:00.400 42420 42420 I LookAlike: a longer pid
        not a threadtime line at all
        10-24 08:00:00.500   424   424 D Short: a shorter pid
        """.trimIndent() + "\n"

    @Test
    fun onlyThisProcesssLinesAndTheSeparatorsAreKept() {
        assertEquals(
            "--------- beginning of main\n" +
                "10-24 08:00:00.100  4242  4242 I Plumemu: ours\n" +
                "10-24 08:00:00.300  4242  4300 E Telemetry: ours too\n",
            Logcat.ownLines(sample, 4242),
        )
    }

    @Test
    fun pidIsAskedFromAndroid7() {
        assertEquals(listOf("logcat", "-d", "-v", "threadtime"), Logcat.command(4242, 23))
        assertEquals(listOf("logcat", "-d", "-v", "threadtime", "--pid=4242"), Logcat.command(4242, 24))
    }

    @Test
    fun aFinishedLogcatGivesItsOwnLines() {
        val text = Logcat.read(start = { FakeProcess(ByteArrayInputStream(sample.toByteArray())) }, pid = 4242)
        assertEquals(Logcat.ownLines(sample, 4242), text)
    }

    @Test
    fun theNewestLinesAreKeptWithinTheCap() {
        val lines = (1..2000).joinToString("") { "10-24 08:00:00.%03d  4242  4242 I Plumemu: line %05d\n".format(it % 1000, it) }
        val text = Logcat.read(start = { FakeProcess(ByteArrayInputStream(lines.toByteArray())) }, pid = 4242, maxBytes = 2_000)
        assertTrue(text.startsWith(Logcat.LEFT_OUT + "\n10-24"))
        assertTrue(text.endsWith("line 02000\n"))
        assertFalse(text.contains("line 00001"))
        assertTrue(text.toByteArray().size <= 2_000 + Logcat.LEFT_OUT.length + 1)
    }

    @Test
    fun aLogcatThatNeverEndsIsStoppedAndWhatWasReadIsKept() {
        val never = NeverEnding("10-24 08:00:00.100  4242  4242 I Plumemu: before the hang\n")
        val process = FakeProcess(never)
        val started = System.nanoTime()
        val text = Logcat.read(start = { process }, pid = 4242, timeoutMillis = 300)
        assertTrue("returns after its timeout", System.nanoTime() - started < 3_000_000_000L)
        assertTrue(text.contains("before the hang"))
        assertTrue(text.endsWith(Logcat.TIMED_OUT + "\n"))
        assertTrue("the process is ended", process.destroyed)
    }

    @Test
    fun aLogcatThatCannotStartThrowsForTheSnapshotToSaySo() {
        try {
            Logcat.read(start = { throw java.io.IOException("no logcat") }, pid = 1)
            error("expected a failure")
        } catch (e: java.io.IOException) {
            assertEquals("no logcat", e.message)
        }
    }

    /** Gives [first], then blocks until the process is destroyed. */
    private class NeverEnding(first: String) : InputStream() {
        private val head = ByteArrayInputStream(first.toByteArray())
        val released = CountDownLatch(1)

        override fun read(): Int {
            val next = head.read()
            if (next >= 0) return next
            released.await()
            return -1
        }

        /** Like a pipe: what is there now, else wait. */
        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int {
            if (len == 0) return 0
            if (head.available() > 0) return head.read(b, off, len)
            released.await()
            return -1
        }
    }

    private class FakeProcess(private val input: InputStream) : Process() {
        @Volatile
        var destroyed = false

        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()

        override fun getInputStream(): InputStream = input

        override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

        override fun waitFor(): Int = 0

        override fun exitValue(): Int = 0

        override fun destroy() {
            destroyed = true
            (input as? NeverEnding)?.released?.countDown()
        }
    }
}
