package com.plumemu.identify.measure

import com.plumemu.identify.IdentifyLog
import com.plumemu.identify.IdentifyRequest
import com.plumemu.identify.Identifier
import com.plumemu.identify.Method
import com.plumemu.identify.fingerprint.FingerprintReader.Depth
import com.plumemu.identify.name.NameMatcher
import com.plumemu.identify.pack.ArcadePackBuilder
import com.plumemu.identify.pack.ArcadeFlags
import com.plumemu.identify.pack.BundledIndex
import com.plumemu.identify.pack.BundledPacks
import com.plumemu.identify.pack.ConsolePackBuilder
import com.plumemu.identify.pack.FakeBundled
import com.plumemu.identify.pack.PackKind
import com.plumemu.identify.pack.PackStore
import com.plumemu.identify.pack.TestPacks
import com.plumemu.identify.source.CountingSource
import com.plumemu.identify.source.FileSource
import com.plumemu.identify.source.RandomAccessSource
import com.plumemu.identify.source.SiblingOpener
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * The 10,000-file measurement for identification. Opt-in: skipped unless
 * Gradle passes `-Pidentify.measure=true`, so the
 * normal test run pays nothing. Prints its tables and writes them to
 * `identify/build/measure/identify.txt`. Laptop JVM numbers; device numbers are measured separately.
 */
class IdentifyMeasurementTest {
    @get:Rule val temp = TemporaryFolder()

    private val out = StringBuilder()
    private val logLines = AtomicInteger()
    private val quietLog = IdentifyLog { logLines.incrementAndGet() }

    @Before fun optIn() {
        assumeTrue("set -Pidentify.measure=true to run", System.getProperty("identify.measure") == "true")
    }

    @Test fun tenThousandFiles() {
        val folder = temp.newFolder("roms")
        val corpus = MeasureCorpus(folder)
        val generated = timed { corpus.generate() }
        line("Generated 10,000 files in %.1f s under a temp folder (sparse files; %d MB on disk)", generated / 1e3, diskMegabytes(folder))
        line("On disk by folder (MB): %s", folder.listFiles().orEmpty().sortedBy { it.name }.joinToString(", ") { "${it.name} ${diskMegabytes(it)}" })

        val packsBuilt = System.nanoTime()
        val bundled = FakeBundled.of(*buildPacks(corpus).toTypedArray())
        line("Generated packs built and gzipped in %.1f s", (System.nanoTime() - packsBuilt) / 1e9)
        val packDir = temp.newFolder("packs")
        val heapBefore = usedHeap()
        val (cold, warm, heapWith) = runPasses(corpus, PackStore(packDir, bundled, quietLog))
        val heapAfter = usedHeap()
        report("QUICK, first pass (packs unpacked and name indexes built on first use, JIT cold)", cold)
        report("QUICK, second pass (same Identifier, warm)", warm)
        line("")
        line(
            "Heap in use after GC: %d KB before, %d KB with the Identifier after both passes, %d KB once it is dropped:" +
                " the Identifier, its open packs and name indexes hold about %d KB",
            heapBefore / 1024, heapWith / 1024, heapAfter / 1024, (heapWith - heapAfter) / 1024,
        )
        line("Packs unpacked to storage: %d KB", packDir.listFiles().orEmpty().sumOf { it.length() } / 1024)
        line("Methods (first pass): %s; log lines: %d", cold.methods.toSortedMap().toString(), logLines.get())
        write()
    }

    /** Both QUICK passes, then FULL over the loose cartridges, with one Identifier; returns the heap it holds. */
    private fun runPasses(corpus: MeasureCorpus, store: PackStore): Triple<Pass, Pass, Long> {
        val identifier = Identifier(store, log = quietLog)
        val cold = runPass(identifier, corpus.files, Depth.QUICK)
        val warm = runPass(identifier, corpus.files, Depth.QUICK)
        val heapWith = usedHeap()
        val indexes = MeasureCorpus.CONSOLE_SYSTEMS.sumOf { system -> store.packFor(system)?.let { identifier.matcherFor(it).indexBytes() } ?: 0L }
        line("Name indexes of the %d generated console packs: %d KB of arrays", MeasureCorpus.CONSOLE_SYSTEMS.size, indexes / 1024)
        val loose = corpus.files.filter { it.kind == Kind.LOOSE_CART }
        val full = runPass(identifier, loose, Depth.FULL)
        val fullTitle = String.format(Locale.ROOT, "FULL, the %d loose cartridge files (whole-file CRC, %d MB in all)", loose.size, loose.sumOf { it.file.length() } shr 20)
        report(fullTitle, full)
        return Triple(cold, warm, heapWith)
    }

    @Test fun realBundledPacks() {
        com.plumemu.identify.pack.PackAssets.assumePacks()
        val assets = File("src/main/assets/identify")
        val index = BundledIndex.parse(File(assets, "packs.txt").readText())
        val bundled = object : BundledPacks {
            override fun readIndex(): String = File(assets, "packs.txt").readText()
            override fun open(fileName: String): InputStream = FileInputStream(File(assets, fileName))
        }
        val dir = temp.newFolder("real")
        val store = PackStore(dir, bundled, quietLog)
        line("Real bundled packs (%d), unpacked on first use by PackStore, then a NameMatcher index each:", index.packs.size)
        line("%-14s %9s %10s %8s %9s %10s %10s %9s", "pack", "gz KB", "unpacked KB", "titles", "unpack ms", "index KB", "index ms", "heap KB")
        var gz = 0L
        var unpacked = 0L
        var indexBytes = 0L
        var unpackMs = 0.0
        for (entry in index.packs.values.sortedByDescending { File(assets, it.fileName).length() }) {
            val start = System.nanoTime()
            val pack = store.packFor(entry.systemId) ?: error("${entry.systemId} did not open")
            val ms = (System.nanoTime() - start) / 1e6
            // The arcade pack has sets, not titles: no name index.
            val heap = usedHeap()
            val matcher = NameMatcher(pack).takeIf { pack.kind == PackKind.CONSOLE }
            val indexStart = System.nanoTime()
            matcher?.match("warm up the index")
            val indexMs = (System.nanoTime() - indexStart) / 1e6
            val heapDelta = usedHeap() - heap
            val matcherBytes = matcher?.indexBytes() ?: 0L
            val packBytes = File(dir, "${entry.systemId}${PackStore.PACK_SUFFIX}").length()
            val gzBytes = File(assets, entry.fileName).length()
            gz += gzBytes
            unpacked += packBytes
            indexBytes += matcherBytes
            unpackMs += ms
            line(
                "%-14s %9d %10d %8d %9.1f %10d %10.1f %9d", entry.systemId, gzBytes / 1024, packBytes / 1024, pack.titleCount,
                ms, matcherBytes / 1024, indexMs, heapDelta / 1024,
            )
        }
        line("All: %d KB gzipped, %d KB unpacked, unpack %.0f ms in all, name indexes %d KB", gz / 1024, unpacked / 1024, unpackMs, indexBytes / 1024)
        write()
    }

    // --- running -----------------------------------------------------------------------------

    private class Sample(val kind: Kind, val bytes: Long, val calls: Long, val nanos: Long)

    private class Pass(val samples: List<Sample>, val wallNanos: Long, val methods: Map<Method, Int>)

    private fun runPass(identifier: Identifier, files: List<CorpusFile>, depth: Depth): Pass {
        val samples = ArrayList<Sample>(files.size)
        val methods = HashMap<Method, Int>()
        val start = System.nanoTime()
        for (f in files) {
            val opened = ArrayList<CountingSource>()
            val siblings = SiblingOpener { name ->
                File(f.file.parentFile, name).takeIf { it.isFile }?.let { CountingSource(FileSource(it)).also { s -> opened += s } }
            }
            val t0 = System.nanoTime()
            val source = CountingSource(FileSource(f.file))
            val result = try {
                identifier.identify(IdentifyRequest(f.systemId, f.file.name, f.cleanedTitle, source = source, siblings = siblings, depth = depth))
            } finally {
                source.close()
                opened.forEach(RandomAccessSource::close)
            }
            val nanos = System.nanoTime() - t0
            methods.merge(result.method, 1, Int::plus)
            samples += Sample(f.kind, source.bytesRead + opened.sumOf { it.bytesRead }, source.readCalls + opened.sumOf { it.readCalls }, nanos)
        }
        return Pass(samples, System.nanoTime() - start, methods)
    }

    private fun report(title: String, pass: Pass) {
        line("")
        line("%s: %d files in %.2f s", title, pass.samples.size, pass.wallNanos / 1e9)
        line("%-20s %6s %10s %8s %8s %10s %7s %8s %8s", "kind", "files", "bytes mean", "p50", "p95", "max", "calls", "ms mean", "ms p95")
        val groups = pass.samples.groupBy { it.kind }.toSortedMap()
        for ((kind, samples) in groups) row(kind.label, samples)
        row("all", pass.samples)
        val total = pass.samples.sumOf { it.bytes }
        line("Total read: %,d bytes in %,d calls", total, pass.samples.sumOf { it.calls })
    }

    private fun row(label: String, samples: List<Sample>) {
        val bytes = samples.map { it.bytes }.sorted()
        val ms = samples.map { it.nanos / 1e6 }.sorted()
        line(
            "%-20s %6d %10.0f %8d %8d %10d %7.1f %8.3f %8.3f", label, samples.size, bytes.average(), percentile(bytes, 50),
            percentile(bytes, 95), bytes.last(), samples.map { it.calls }.average(), ms.average(), percentile(ms, 95),
        )
    }

    private fun <T> percentile(sorted: List<T>, p: Int): T = sorted[((sorted.size - 1) * p / 100.0).toInt()]

    // --- packs -------------------------------------------------------------------------------

    private fun buildPacks(corpus: MeasureCorpus): List<Pair<String, ByteArray>> {
        val packs = MeasureCorpus.CONSOLE_SYSTEMS.map { system -> system to consolePack(corpus, system) }
        val random = Random(40)
        val arcade = ArcadePackBuilder("arcade", 20261021)
        corpus.arcadeSets.forEachIndexed { i, shortName ->
            val clone = i % 5 == 4
            val flags = (if (clone) ArcadeFlags.CLONE else 0) or (if (i % 50 == 0) ArcadeFlags.BIOS else 0)
            arcade.set(shortName, TestPacks.madeUpTitle(random), maker = "Testsoft", year = 1980 + i % 25, flags = flags, parent = if (clone) corpus.arcadeSets[i - 1] else null)
        }
        return packs + ("arcade" to arcade.build())
    }

    /** 5,000 titles, 8,000 releases: the listed CRCs and codes, made-up ones for the rest. */
    private fun consolePack(corpus: MeasureCorpus, system: String): ByteArray {
        val random = Random(system.hashCode())
        val builder = ConsolePackBuilder(system, 20261021)
        val titles = corpus.titlesOf(system)
        val handles = titles.mapIndexed { i, name -> builder.title(name, year = 1985 + i % 30, publisher = "Testsoft") }
        val crcs = corpus.crcsOf(system).groupBy({ it.first }, { Pair<Long, Long?>(it.second, it.third) })
        val codes = corpus.codesOf(system).groupBy({ it.first }, { it.second to it.third })
        for (r in 0 until MeasureCorpus.RELEASES) {
            val title = r % titles.size
            val first = r < titles.size
            val listedCrcs = if (first) crcs[title].orEmpty() else emptyList()
            val listedCodes = if (first) codes[title].orEmpty() else emptyList()
            builder.release(
                handles[title],
                regions = listOf(if (r % 3 == 0) "Japan" else "USA"),
                crcs = listedCrcs.ifEmpty { listOf(random.nextLong(0, 1L shl 32) to random.nextLong(1L shl 16, 8L shl 20)) },
                codes = listedCodes,
            )
        }
        return builder.build()
    }

    // --- output ------------------------------------------------------------------------------

    private fun line(format: String, vararg args: Any) {
        val text = if (args.isEmpty()) format else String.format(Locale.ROOT, format, *args)
        println(text)
        out.append(text).append('\n')
    }

    private fun write() {
        val dir = File("build/measure").also { it.mkdirs() }
        val name = if (out.contains("Real bundled packs")) "identify-real-packs.txt" else "identify.txt"
        File(dir, name).writeText(out.toString())
        println("written to ${File(dir, name).absolutePath}")
    }

    private fun usedHeap(): Long {
        val runtime = Runtime.getRuntime()
        repeat(3) {
            System.gc()
            Thread.sleep(50)
        }
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private inline fun timed(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }

    private fun diskMegabytes(folder: File): Long {
        val process = ProcessBuilder("du", "-sk", folder.absolutePath).redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().readText()
        process.waitFor()
        return (text.trim().substringBefore('\t').toLongOrNull() ?: -1L) / 1024
    }
}
