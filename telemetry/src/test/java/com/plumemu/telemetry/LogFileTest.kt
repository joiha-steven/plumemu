package com.plumemu.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The rotating log: two files at most, each under its cap; the export joins them in order. */
class LogFileTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun theLogRotatesAtTheCapAndKeepsTwoFiles() {
        val dir = folder.newFolder("logs")
        val log = LogFile(dir, maxBytes = 100)
        repeat(30) { log.append("line %02d is twenty\n".format(it)) }

        val current = File(dir, LogFile.CURRENT)
        val previous = File(dir, LogFile.PREVIOUS)
        assertEquals(setOf(LogFile.CURRENT, LogFile.PREVIOUS), dir.list()!!.toSet())
        assertTrue(current.length() <= 100)
        assertTrue(previous.length() <= 100)
        assertTrue("the newest line is in the current file", current.readText().endsWith("line 29 is twenty\n"))
        assertFalse("old lines are gone", (previous.readText() + current.readText()).contains("line 00"))
    }

    @Test
    fun theSnapshotIsTheHeaderThenPlumemusLogOlderFileFirstThenLogcat() {
        val dir = folder.newFolder("logs")
        val log = LogFile(dir, maxBytes = 40)
        listOf("first line of the log\n", "second line of the log\n", "third\n").forEach(log::append)
        assertTrue(File(dir, LogFile.PREVIOUS).isFile)
        val out = File(folder.root, "export/plumemu-log.txt")
        AppLog.writeSnapshot(out, "Plumemu 0.0.3-beta", log) { "10-24 08:00:00.000  123  123 I Plumemu: hello\n" }
        val text = out.readText()
        assertTrue(text.startsWith("Plumemu 0.0.3-beta\n\n== Plumemu's log"))
        assertTrue("older file first", text.contains("first line of the log\nsecond line of the log\nthird\n"))
        val logcatAt = text.indexOf("== Android's log, this app's lines only")
        assertTrue("logcat after the file", logcatAt > text.indexOf("third"))
        assertTrue(text.endsWith("I Plumemu: hello\n"))
    }

    @Test
    fun aLogcatThatCannotBeReadIsSaidAndTheFileStillGoes() {
        val log = LogFile(folder.newFolder("logs")).also { it.append("kept line\n") }
        val out = File(folder.root, "out.txt")
        AppLog.writeSnapshot(out, "h", log) { throw java.io.IOException("no logcat") }
        val text = out.readText()
        assertTrue(text.contains("kept line"))
        assertTrue(text.contains("Android's log could not be read (IOException)"))
    }

    @Test
    fun aFullQueueDropsLinesAndSaysHowManyWhenItCatchesUp() {
        val dir = folder.newFolder("logs")
        val writer = LogWriter(LogFile(dir), capacity = 10)
        val hold = CountDownLatch(1)
        val holding = CountDownLatch(1)
        writer.onWriter {
            holding.countDown()
            hold.await(5, TimeUnit.SECONDS)
        }
        holding.await(5, TimeUnit.SECONDS)
        repeat(15) { writer.enqueue("queued $it\n") }
        hold.countDown()
        assertTrue("the queue drains", (1..50).any { writer.flush() || run { Thread.sleep(20); false } })
        writer.enqueue("after\n")
        assertTrue(writer.flush())

        val text = File(dir, LogFile.CURRENT).readText()
        assertEquals(10, text.lines().count { it.startsWith("queued ") })
        assertTrue(text.contains("W/AppLog: 5 log lines dropped (the writer was behind)"))
        assertTrue(text.endsWith("after\n"))
    }

    @Test
    fun aFlushWithTheQueueFullReturnsAtOnceAndIsNotADroppedLine() {
        val dir = folder.newFolder("logs")
        val writer = LogWriter(LogFile(dir), capacity = 2)
        val hold = CountDownLatch(1)
        val holding = CountDownLatch(1)
        writer.onWriter {
            holding.countDown()
            hold.await(10, TimeUnit.SECONDS)
        }
        holding.await(5, TimeUnit.SECONDS)
        writer.enqueue("one\n")
        writer.enqueue("two\n")
        val started = System.nanoTime()
        assertFalse("nothing was written yet", writer.flush())
        assertTrue("no 5 s wait", System.nanoTime() - started < 1_000_000_000L)
        hold.countDown()
        // The two lines leave the queue as the writer takes them; a flush is then let in.
        assertTrue((1..50).any { writer.flush() || run { Thread.sleep(20); false } })
        assertEquals("one\ntwo\n", File(dir, LogFile.CURRENT).readText())
    }

    @Test
    fun aLineNamesItsTimeLevelAndTag() {
        assertEquals("2025-10-24T10:00:00.000Z W/Telemetry: hello\n", LogFile.line(1_761_300_000_000L, "W", "Telemetry", "hello"))
    }
}
