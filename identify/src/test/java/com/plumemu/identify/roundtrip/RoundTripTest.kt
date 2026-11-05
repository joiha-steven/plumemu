package com.plumemu.identify.roundtrip

import com.plumemu.identify.Identifier
import com.plumemu.identify.pack.BundledIndex
import com.plumemu.identify.pack.BundledPacks
import com.plumemu.identify.pack.PackAssets
import com.plumemu.identify.pack.PackKind
import com.plumemu.identify.pack.PackStore
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Locale

/**
 * The whole-catalogue round trip: every title of every bundled console pack (up to [CAP] per
 * system) becomes the file names people use ([NameVariants]), each goes through the name-match
 * path of a scan ([RoundTrip]) and must come back as its own title. Measures; changes nothing.
 *
 * Writes `build/roundtrip/report.md` and `failures.tsv` (and `ambiguous.tsv`), and fails when a number in
 * `src/test/resources/roundtrip/scores.txt` got worse (right fewer, wrong more), the sample or a
 * pack changed, or a line is missing. Only `-Proundtrip.update=true` writes scores.txt.
 * `-Proundtrip.cap=N` runs a smaller sample for a quick look: report only, no ratchet.
 */
class RoundTripTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun scoresDoNotGetWorse() {
        PackAssets.assumePacks()
        val cap = System.getProperty("roundtrip.cap")?.toIntOrNull() ?: CAP
        val started = System.nanoTime()
        val assets = PackAssets.dir
        val bundled = object : BundledPacks {
            override fun readIndex(): String = File(assets, "packs.txt").readText()
            override fun open(fileName: String): InputStream = FileInputStream(File(assets, fileName))
        }
        val store = PackStore(temp.newFolder("packs"), bundled, RoundTrip.quietLog)
        val identifier = Identifier(store, log = RoundTrip.quietLog)
        val index = BundledIndex.parse(bundled.readIndex())
        val results = ArrayList<SystemResult>()
        for (id in index.packs.keys.sorted()) {
            val pack = store.packFor(id) ?: error("pack $id did not open")
            if (pack.kind != PackKind.CONSOLE) continue
            results += RoundTrip.run(id, pack, identifier, cap)
        }
        val seconds = (System.nanoTime() - started) / 1e9
        val out = File("build/roundtrip").also { it.mkdirs() }
        File(out, "report.md").writeText(RoundTripReport.report(results, cap, seconds))
        File(out, "failures.tsv").writeText(RoundTripReport.failures(results))
        File(out, "ambiguous.tsv").writeText(RoundTripReport.ambiguous(results))
        RoundTripReport.summary(results, seconds).forEach(::println)
        println("roundtrip: report in ${out.absolutePath}/report.md")

        if (cap != CAP) {
            println("roundtrip: cap $cap is not the ratchet's $CAP; scores.txt not checked")
            return
        }
        val current = RoundTripReport.scores(results)
        if (System.getProperty("roundtrip.update") == "true") {
            SCORES.parentFile.mkdirs()
            SCORES.writeText(RoundTripReport.scoresFile(current))
            println("roundtrip: wrote ${SCORES.absolutePath}")
            return
        }
        if (!SCORES.isFile) fail("roundtrip: ${SCORES.path} is missing; $UPDATE")
        val problems = RoundTripReport.problems(RoundTripReport.parse(SCORES.readText()), current)
        if (problems.isNotEmpty()) {
            fail("roundtrip scores got worse or changed:\n" + problems.joinToString("\n") + "\n(better numbers or a changed pack: $UPDATE)")
        }
    }

    private companion object {
        /** Titles tried per system at most: every Nth distinct title, N = ceil(titles / CAP). Above every pack's size (the whole catalogue takes ~15 s), so all titles are tried. */
        const val CAP = 1_000_000
        val SCORES = File("src/test/resources/roundtrip/scores.txt")
        const val UPDATE = "check build/roundtrip/report.md, then rerun with -Proundtrip.update=true"
    }
}

/** Report, failure list and score file of a run. */
internal object RoundTripReport {
    private const val FAILURES_PER_CELL = 50
    private const val AMBIGUOUS_PER_SYSTEM = 100

    fun summary(results: List<SystemResult>, seconds: Double): List<String> {
        val total = Tally().also { t -> results.forEach { r -> r.byKind.values.forEach(t::add) } }
        return listOf(
            String.format(
                Locale.ROOT, "roundtrip: %d systems, %d names, right %s, wrong %d, unknown %d, ambiguous %d, %.0f s",
                results.size, total.total, total.percent(), total.wrong, total.unknown, total.ambiguous, seconds,
            ),
        )
    }

    private fun overall(results: List<SystemResult>): Map<String, Tally> {
        val all = LinkedHashMap<String, Tally>()
        for (kind in NameVariants.KINDS) all[kind] = Tally()
        for (r in results) for ((kind, t) in r.byKind) all.getOrPut(kind) { Tally() }.add(t)
        return all
    }

    fun report(results: List<SystemResult>, cap: Int, seconds: Double): String = buildString {
        appendLine("# Name round trip")
        appendLine()
        appendLine("Every title of every bundled console pack (arcade excluded) as the file names in `NameVariants`, cleaned by")
        appendLine("`TitleCleaner` and matched by `Identifier` with no readable content. Sample: " +
            (if (results.all { it.sampled == it.titles }) "all distinct titles" else "every Nth distinct title, at most $cap per system") + ". Run took ${String.format(Locale.ROOT, "%.0f", seconds)} s.")
        appendLine("Right = the shown title is the original or has its match key. Ambiguous = it has the key but is another title of")
        appendLine("the pack that shares it, so no name could tell them apart (not a failure). Wrong = another title. Unknown = no match.")
        appendLine()
        appendLine("## By kind, all systems")
        appendLine()
        appendLine("| kind | names | right % | wrong | unknown | ambiguous |")
        appendLine("|---|---:|---:|---:|---:|---:|")
        for ((kind, t) in overall(results)) {
            if (t.total > 0) appendLine("| $kind | ${t.total} | ${t.percent()} | ${t.wrong} | ${t.unknown} | ${t.ambiguous} |")
        }
        appendLine()
        appendLine("## Systems by failures (wrong + unknown)")
        appendLine()
        appendLine("| system | titles | tried | names | right % | wrong | unknown | ambiguous |")
        appendLine("|---|---:|---:|---:|---:|---:|---:|---:|")
        for (r in results.sortedByDescending { r -> r.byKind.values.sumOf { it.wrong + it.unknown } }) {
            val t = Tally().also { t -> r.byKind.values.forEach(t::add) }
            appendLine("| ${r.system} | ${r.titles} | ${r.sampled} | ${t.total} | ${t.percent()} | ${t.wrong} | ${t.unknown} | ${t.ambiguous} |")
        }
        appendLine()
        appendLine("## System x kind (right % (wrong/unknown))")
        appendLine()
        val kinds = NameVariants.KINDS.filter { k -> results.any { (it.byKind[k]?.total ?: 0) > 0 } }
        appendLine("| system | " + kinds.joinToString(" | ") + " |")
        appendLine("|---|" + kinds.joinToString("|") { "---:" } + "|")
        for (r in results) {
            appendLine(
                "| ${r.system} | " + kinds.joinToString(" | ") { k ->
                    r.byKind[k]?.takeIf { it.total > 0 }?.let { "${it.percent()} (${it.wrong}/${it.unknown})" } ?: "-"
                } + " |",
            )
        }
    }

    /** system, kind, file name, expected, shown; the first [FAILURES_PER_CELL] of each system and kind. */
    fun failures(results: List<SystemResult>): String = buildString {
        appendLine("system\tkind\tverdict\tfile name\texpected\tshown")
        for (r in results) {
            val seen = HashMap<String, Int>()
            for (f in r.failures.filter { it.verdict != Verdict.AMBIGUOUS }) {
                val n = seen.merge(f.kind, 1, Int::plus) ?: 1
                if (n <= FAILURES_PER_CELL) appendLine("${f.system}\t${f.kind}\t${f.verdict.name.lowercase()}\t${f.fileName}\t${f.expected}\t${f.shown}")
            }
        }
    }

    /** The ambiguous names of kind "exact" (the first [AMBIGUOUS_PER_SYSTEM] per system): the titles that share a key with the original. */
    fun ambiguous(results: List<SystemResult>): String = buildString {
        appendLine("system\tfile name\texpected\tshown")
        for (r in results) {
            r.failures.filter { it.verdict == Verdict.AMBIGUOUS && it.kind == "exact" }.take(AMBIGUOUS_PER_SYSTEM)
                .forEach { appendLine("${it.system}\t${it.fileName}\t${it.expected}\t${it.shown}") }
        }
    }

    /** "system kind" to its counts, in file order. */
    fun scores(results: List<SystemResult>): Map<String, Tally> {
        val out = LinkedHashMap<String, Tally>()
        for (r in results) for (kind in NameVariants.KINDS) r.byKind[kind]?.let { out["${r.system} $kind"] = it }
        return out
    }

    fun scoresFile(scores: Map<String, Tally>): String = buildString {
        appendLine("# Name round-trip scores that must not get worse (identify RoundTripTest).")
        appendLine("# <system> <kind> right/names wrong <n>: right may only rise, wrong may only fall; names is the sample size.")
        appendLine("# Written only by: ./gradlew :identify:testDebugUnitTest --tests '*RoundTripTest' -Proundtrip.update=true")
        for ((key, t) in scores) appendLine("$key ${t.right}/${t.total} wrong ${t.wrong}")
    }

    class Line(val right: Int, val total: Int, val wrong: Int)

    fun parse(text: String): Map<String, Line> {
        val out = LinkedHashMap<String, Line>()
        for (line in text.lines()) {
            if (line.isBlank() || line.startsWith("#")) continue
            val p = line.split(' ')
            val (right, total) = p[2].split('/').map { it.toInt() }
            out["${p[0]} ${p[1]}"] = Line(right, total, p[4].toInt())
        }
        return out
    }

    fun problems(old: Map<String, Line>, now: Map<String, Tally>): List<String> {
        val out = ArrayList<String>()
        for ((key, before) in old) {
            val t = now[key]
            when {
                t == null -> out += "$key: no longer produced"
                t.total != before.total -> out += "$key: names ${before.total} -> ${t.total} (pack or sample changed)"
                t.right < before.right -> out += "$key: right ${before.right} -> ${t.right}"
                t.wrong > before.wrong -> out += "$key: wrong ${before.wrong} -> ${t.wrong}"
            }
        }
        for (key in now.keys) if (key !in old) out += "$key: not in scores.txt"
        return out
    }
}
