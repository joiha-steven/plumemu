package com.plumemu.scraper

import com.plumemu.scraper.hash.FileHashCache
import com.plumemu.scraper.hash.RomHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class RomHasherTest {
    @get:Rule val tmp = TemporaryFolder()

    private val helloWorld = RomHashes(
        crc32 = "0D4A1185",
        md5 = "5eb63bbbe01eeed093cb22bb8f5acdc3",
        sha1 = "2aae6c35c94fcfb415dbe95f408b9ce91ee846ed",
    )

    /** A ROM in memory that counts how often it is opened. */
    private class CountingRom(
        override val fileName: String,
        private val bytes: ByteArray,
        override var lastModifiedMillis: Long = 1_000L,
    ) : RomSource {
        var opens = 0
        override val key: String get() = "/roms/$fileName"
        override val sizeBytes: Long get() = bytes.size.toLong()
        override fun open(): InputStream = ByteArrayInputStream(bytes).also { opens++ }
    }

    private fun cache(file: File = File(tmp.root, "hashes.tsv")) = FileHashCache(file, Dispatchers.IO) { }

    private fun hasher(cache: HashCache = cache(), limit: Long = RomHasher.FULL_HASH_LIMIT_BYTES) =
        RomHasher(cache, Dispatchers.IO, limit)

    @Test fun `a small file gets CRC32, MD5 and SHA1`() = runTest {
        assertEquals(helloWorld, hasher().hash(CountingRom("Game.gb", "hello world".toByteArray())))
    }

    @Test fun `CRC32 is 8 uppercase digits with leading zeros`() = runTest {
        // CRC32 of "a" is e8b7be43; of the empty file is 0.
        assertEquals("E8B7BE43", hasher().hash(CountingRom("a.nes", "a".toByteArray())).crc32)
        assertEquals("00000000", hasher().hash(CountingRom("empty.nes", ByteArray(0))).crc32)
    }

    @Test fun `a file over the full-hash limit gets CRC32 only`() = runTest {
        val hashes = hasher(limit = 8).hash(CountingRom("Disc.iso", "hello world".toByteArray()))

        assertEquals(RomHashes(crc32 = "0D4A1185", md5 = null, sha1 = null), hashes)
    }

    @Test fun `a file of several buffers hashes the same as in one piece`() = runTest {
        val big = ByteArray(RomHasher.BUFFER_BYTES * 3 + 17) { (it * 31).toByte() }
        val file = File(tmp.root, "big.bin").apply { writeBytes(big) }
        val crc = java.util.zip.CRC32().apply { update(big) }.value
        val md5 = java.security.MessageDigest.getInstance("MD5").digest(big)
            .joinToString("") { "%02x".format(it) }

        val hashes = hasher().hash(FileRomSource(file))

        assertEquals("%08X".format(crc), hashes.crc32)
        assertEquals(md5, hashes.md5)
    }

    @Test fun `compressed disc images are not hashed at all`() = runTest {
        for (name in listOf("Game.chd", "Game.RVZ", "Game.cso", "Game.7z", "Game.Chd")) {
            val rom = CountingRom(name, "hello world".toByteArray())
            assertEquals(name, RomHashes.NONE, hasher().hash(rom))
            assertEquals(name, 0, rom.opens)
        }
    }

    @Test fun `a cached file is not read again, even by a new cache on the same file`() = runTest {
        val rom = CountingRom("Game.gb", "hello world".toByteArray())
        val hasher = hasher()

        hasher.hash(rom)
        assertEquals(helloWorld, hasher.hash(rom))
        assertEquals(helloWorld, hasher(cache()).hash(rom))

        assertEquals(1, rom.opens)
    }

    @Test fun `a changed date or size rehashes`() = runTest {
        val rom = CountingRom("Game.gb", "hello world".toByteArray())
        val hasher = hasher()
        hasher.hash(rom)

        rom.lastModifiedMillis = 2_000L
        hasher.hash(rom)
        assertEquals(2, rom.opens)

        val grown = CountingRom("Game.gb", "hello world!".toByteArray(), lastModifiedMillis = 2_000L)
        hasher.hash(grown)
        assertEquals(1, grown.opens)
        // The newest entry wins after a reload.
        assertEquals(rom.opens, 2)
        assertNull(cache().get(rom.key, rom.sizeBytes, 1_000L))
    }

    @Test fun `a damaged cache line is dropped and the rest survives`() = runTest {
        val file = File(tmp.root, "hashes.tsv")
        cache(file).put("/roms/a.gb", 11, 1, helloWorld)
        // A crash in the middle of an append leaves a cut line without a newline.
        file.appendText("/roms/b.gb\t11\t1\t0D4A1185\t5eb63bbb")

        val reloaded = cache(file)

        assertEquals(helloWorld, reloaded.get("/roms/a.gb", 11, 1))
        assertNull(reloaded.get("/roms/b.gb", 11, 1))
        reloaded.put("/roms/c.gb", 11, 1, helloWorld)
        assertEquals(helloWorld, cache(file).get("/roms/c.gb", 11, 1))
        assertTrue(file.readText().endsWith("\n"))
    }

    @Test fun `keys with tabs, newlines and backslashes survive the file`() = runTest {
        val file = File(tmp.root, "hashes.tsv")
        val key = "content://tree/A\tB\nC\\D"
        cache(file).put(key, 11, 1, helloWorld)

        assertEquals(helloWorld, cache(file).get(key, 11, 1))
    }

    @Test fun `hashing a large image stops when the scrape is cancelled`() = runBlocking {
        val reading = CountDownLatch(1)
        val endless = object : RomSource {
            override val key = "/roms/endless.iso"
            override val fileName = "endless.iso"
            override val sizeBytes = Long.MAX_VALUE
            override val lastModifiedMillis = 1L
            override fun open(): InputStream = object : InputStream() {
                override fun read(): Int = 0
                override fun read(b: ByteArray, off: Int, len: Int): Int = len.also { reading.countDown() }
            }
        }
        val job = launch(Dispatchers.Default) { hasher().hash(endless) }
        assertTrue(reading.await(5, TimeUnit.SECONDS))

        // Real time: the endless stream would never end on its own.
        withTimeout(5_000) { job.cancelAndJoin() }

        assertTrue(job.isCancelled)
    }

    /** The run's parallel games read at most two ROM files at once. */
    @Test fun `at most two ROMs are read at once, whatever the parallel games`() = runBlocking {
        val release = CountDownLatch(1)
        val open = AtomicInteger()
        val most = AtomicInteger()
        val opened = Semaphore(0)
        fun blocking(n: Int) = object : RomSource {
            override val key = "/roms/blocking$n.md"
            override val fileName = "blocking$n.md"
            override val sizeBytes = 4L
            override val lastModifiedMillis = 1L
            override fun open(): InputStream {
                most.accumulateAndGet(open.incrementAndGet(), ::maxOf)
                opened.release()
                return object : InputStream() {
                    private var done = false
                    override fun read(): Int = error("read in buffers")
                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        release.await(5, TimeUnit.SECONDS)
                        if (done) return -1
                        done = true
                        return 4
                    }
                    override fun close() {
                        open.decrementAndGet()
                    }
                }
            }
        }
        val hasher = hasher()
        val jobs = (1..5).map { n -> launch(Dispatchers.Default) { hasher.hash(blocking(n)) } }
        assertTrue("two reads started", opened.tryAcquire(2, 5, TimeUnit.SECONDS))
        // A third would start within this wait if the limit let it.
        assertFalse("no third read while two are open", opened.tryAcquire(1, 300, TimeUnit.MILLISECONDS))
        assertEquals(2, open.get())

        release.countDown()
        withTimeout(5_000) { jobs.forEach { it.join() } }
        assertEquals(RomHasher.MAX_PARALLEL_READS, most.get())
        assertEquals(0, open.get())
    }
}
