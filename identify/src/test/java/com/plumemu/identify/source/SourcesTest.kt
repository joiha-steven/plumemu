package com.plumemu.identify.source

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel

class SourcesTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val bytes = ByteArray(1000) { it.toByte() }

    @Test
    fun fileSourceReadsAtPositionsAndReportsTheEnd() {
        val file = temp.newFile("rom.bin").apply { writeBytes(bytes) }
        FileSource(file).use { source ->
            assertEquals(1000L, source.size)
            assertArrayEquals(bytes.copyOfRange(990, 1000), source.readFully(990, 10))
            assertArrayEquals(bytes.copyOfRange(995, 1000), source.readFully(995, 50))
            assertEquals(0, source.readFully(1000, 10).size)
            assertEquals(-1, source.readAt(1000, ByteArray(4), 0, 4))
        }
    }

    @Test
    fun fileSourceFromAChannelReadsTheSame() {
        val file = temp.newFile("rom.bin").apply { writeBytes(bytes) }
        FileSource(FileInputStream(file).channel).use { source ->
            assertArrayEquals(bytes.copyOfRange(100, 200), source.readFully(100, 100))
        }
    }

    @Test
    fun fileSourceClosesTheChannelWhenItsSizeCannotBeRead() {
        val channel = BrokenSizeChannel()
        assertThrows(IOException::class.java) { FileSource(channel) }
        assertFalse(channel.isOpen)
    }

    @Test
    fun aCloseFailureIsKeptAsSuppressed() {
        val channel = BrokenSizeChannel(closeFails = true)
        val thrown = assertThrows(IOException::class.java) { FileSource(channel) }
        assertEquals("descriptor revoked", thrown.message)
        assertEquals("close failed", thrown.suppressed.single().message)
    }

    @Test
    fun byteArraySourceClampsLikeAFile() {
        val source = ByteArraySource(bytes)
        val dst = ByteArray(20)
        assertEquals(10, source.readAt(990, dst, 5, 15))
        assertEquals(bytes[990], dst[5])
        assertEquals(-1, source.readAt(2000, dst, 0, 1))
        assertEquals(0, source.readFully(5000, 3).size)
    }

    @Test
    fun readFullyLoopsOverShortReads() {
        val stingy = object : RandomAccessSource {
            override val size = 1000L
            override fun readAt(position: Long, dst: ByteArray, offset: Int, length: Int): Int =
                ByteArraySource(bytes).readAt(position, dst, offset, minOf(length, 7))
            override fun close() = Unit
        }
        assertArrayEquals(bytes.copyOfRange(10, 110), stingy.readFully(10, 100))
    }

    @Test
    fun countingSourceCountsBytesAndCalls() {
        val counting = CountingSource(ByteArraySource(bytes))
        counting.readFully(0, 100)
        counting.readFully(950, 100)
        assertEquals(150L, counting.bytesRead)
        assertTrue(counting.readCalls >= 2)
    }
}

/** A channel whose size() fails, as a revoked document's descriptor can. Only size and close matter. */
private class BrokenSizeChannel(private val closeFails: Boolean = false) : FileChannel() {
    override fun size(): Long = throw IOException("descriptor revoked")
    override fun implCloseChannel() {
        if (closeFails) throw IOException("close failed")
    }
    override fun read(dst: ByteBuffer): Int = throw UnsupportedOperationException()
    override fun read(dsts: Array<out ByteBuffer>, offset: Int, length: Int): Long = throw UnsupportedOperationException()
    override fun read(dst: ByteBuffer, position: Long): Int = throw UnsupportedOperationException()
    override fun write(src: ByteBuffer): Int = throw UnsupportedOperationException()
    override fun write(srcs: Array<out ByteBuffer>, offset: Int, length: Int): Long = throw UnsupportedOperationException()
    override fun write(src: ByteBuffer, position: Long): Int = throw UnsupportedOperationException()
    override fun position(): Long = 0
    override fun position(newPosition: Long): FileChannel = this
    override fun truncate(size: Long): FileChannel = this
    override fun force(metaData: Boolean) = Unit
    override fun transferTo(position: Long, count: Long, target: WritableByteChannel): Long = 0
    override fun transferFrom(src: ReadableByteChannel, position: Long, count: Long): Long = 0
    override fun map(mode: MapMode, position: Long, size: Long): MappedByteBuffer = throw UnsupportedOperationException()
    override fun lock(position: Long, size: Long, shared: Boolean): FileLock = throw UnsupportedOperationException()
    override fun tryLock(position: Long, size: Long, shared: Boolean): FileLock = throw UnsupportedOperationException()
}
