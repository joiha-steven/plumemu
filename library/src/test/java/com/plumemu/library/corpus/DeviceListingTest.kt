package com.plumemu.library.corpus

import com.plumemu.coredata.db.NameTrustNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Runs each real device's file listing (the private device listings folder, names and sizes only,
 * a listing is an input, never ground truth) through the real naming path and prints, per device
 * and per top folder: files, named from a pack (exact, likely), named only from the cleaned file
 * name, not games or skipped, and the file names no pack matched (also written to
 * `build/corpus/devices-<device>-unmatched.txt`). The `scores.txt` in that folder is the ratchet:
 * a loss fails; only `-Pdevices.update=true` writes it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceListingTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun deviceNamesDoNotGetWorse() {
        RealData.assumePrivateFile("tools/corpus/devices/README.md")
        val listings = DEVICES_DIR.listFiles { f -> f.isFile && f.name.endsWith(".txt") && f.name != "scores.txt" }.orEmpty().sortedBy { it.name }
        if (listings.isEmpty()) fail("devices: no listing in ${DEVICES_DIR.absolutePath}")
        val rules = RealData.systemRules()
        val stats = ArrayList<Stat>()
        RealData.REPORT_DIR.mkdirs()
        for (listing in listings) {
            val device = listing.nameWithoutExtension
            val corpus = parse(device, listing.readText(Charsets.UTF_8))
            val scan = CorpusRun.run(corpus, temp.newFolder(device), rules)
            val rowOf = scan.rows.associateBy { CorpusRun.pathOf(it.documentId) }
            val paths = corpus.rows.map { CorpusRun.key(it.path) }
            fun folderOf(path: String) = if ('/' in path) path.substringBefore('/') else "(root)"
            val byFolder = paths.groupBy(::folderOf).toSortedMap()
            val unmatched = ArrayList<String>()
            for ((folder, files) in byFolder + ("ALL" to paths)) {
                val rows = files.mapNotNull { rowOf[it] }
                val games = rows.filter { !it.isNotGame }
                val exact = games.count { it.nameTrust == NameTrustNames.IDENTIFIER_EXACT }
                val likely = games.count { it.nameTrust == "IDENTIFIER_LIKELY" }
                val fileOnly = games.size - exact - likely
                val stat = Stat(device, folder, files.size, exact, likely, fileOnly, files.size - games.size)
                stats += stat
                println(stat.line())
                if (folder == "ALL") {
                    games.filter { it.nameTrust == NameTrustNames.FILE_NAME }.mapTo(unmatched) { CorpusRun.pathOf(it.documentId) }
                }
            }
            File(RealData.REPORT_DIR, "devices-$device-unmatched.txt").writeText(unmatched.sorted().joinToString("\n", postfix = "\n"))
            println("devices $device: ${unmatched.size} files with no pack match:")
            unmatched.sorted().forEach { println("  unmatched $device: $it") }
        }
        if (System.getProperty("devices.update") == "true") {
            SCORES.writeText(format(stats))
            println("devices: wrote ${SCORES.absolutePath}")
            return
        }
        if (!SCORES.isFile) fail("devices: ${SCORES.absolutePath} is missing; run with -Pdevices.update=true")
        val problems = problems(parse(SCORES.readText()), stats)
        if (problems.isNotEmpty()) {
            fail(problems.joinToString("\n", postfix = "\n(an intended change: -Pdevices.update=true, then commit scores.txt)"))
        }
    }

    @Test
    fun ratchetCatchesALostNameAndAChangedListing() {
        val old = Stat("d", "snes", 10, 6, 2, 2, 0)
        fun check(now: Stat) = problems(parse(format(listOf(old))), listOf(now))
        assertTrue(check(old).isEmpty())
        assertTrue(check(Stat("d", "snes", 10, 7, 2, 1, 0)).isEmpty())
        assertEquals(1, check(Stat("d", "snes", 10, 5, 3, 2, 0)).size)
        assertEquals(1, check(Stat("d", "snes", 10, 6, 1, 3, 0)).size)
        assertEquals(1, check(Stat("d", "snes", 11, 6, 2, 3, 0)).size)
        assertEquals(3, problems(parse(format(listOf(old))), emptyList()).size)
    }

    /** One device folder (or `ALL`): the counts a person sees as "named" and "only from the file". */
    private data class Stat(
        val device: String,
        val folder: String,
        val files: Int,
        val exact: Int,
        val likely: Int,
        val fileNameOnly: Int,
        val notGamesOrSkipped: Int,
    ) {
        val key get() = "$device|$folder"
        fun line() = "devices $device | $folder: files $files, pack exact $exact, pack likely $likely, " +
            "file name only $fileNameOnly, not a game or skipped $notGamesOrSkipped"
    }

    private fun parse(device: String, text: String): Corpus {
        val rows = text.lines().filter { it.isNotBlank() }.mapIndexed { i, line ->
            val size = line.substringBefore('|').toLongOrNull() ?: throw CorpusFormatException("$device:${i + 1}: no size before '|'")
            val path = line.substringAfter('|').removePrefix("./").removeSuffix("\r")
            // Expected answers are not known: a listing is an input only.
            CorpusRow(i + 1, path, size, null, "-", null, emptyList(), emptyList(), Content.Empty)
        }
        return Corpus(device, rows)
    }

    private fun format(stats: List<Stat>): String = buildString {
        appendLine("# Device naming scores that must not get worse (DeviceListingTest).")
        appendLine("# files: equal; exact and exact+likely (named from a pack): may only rise.")
        appendLine("# Written only by: ./gradlew :library:testDebugUnitTest --tests '*DeviceListingTest' -Pdevices.update=true")
        for (s in stats) appendLine("${s.key}|files|${s.files}\n${s.key}|exact|${s.exact}\n${s.key}|pack-named|${s.exact + s.likely}")
    }

    private fun parse(text: String): Map<String, Int> = text.lines().filter { it.isNotBlank() && !it.startsWith("#") }
        .associate { it.substringBeforeLast('|') to it.substringAfterLast('|').toInt() }

    private fun problems(stored: Map<String, Int>, now: List<Stat>): List<String> {
        val current = now.flatMap { s ->
            listOf("${s.key}|files" to s.files, "${s.key}|exact" to s.exact, "${s.key}|pack-named" to s.exact + s.likely)
        }.toMap()
        return stored.mapNotNull { (key, was) ->
            val value = current[key] ?: return@mapNotNull "devices: '$key' is in scores.txt but not in this run"
            when {
                key.endsWith("|files") && value != was -> "devices: $key was $was, now $value (listing changed)"
                !key.endsWith("|files") && value < was -> "devices: $key fell from $was to $value"
                else -> null
            }
        } + (current.keys - stored.keys).map { "devices: '$it' has no line in scores.txt" }
    }

    companion object {
        val DEVICES_DIR = File(RealData.ROOT, "tools/corpus/devices")
        val SCORES = File(DEVICES_DIR, "scores.txt")
    }
}
