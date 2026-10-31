package com.plumemu.library.corpus

import java.io.File
import java.util.Locale

/** `build/corpus/report.md`, `<corpus>-failures.tsv` and the summary lines printed to stdout. */
internal object CorpusReport {
    fun summary(s: CorpusScores): List<String> = listOf(
        "corpus ${s.corpus}: ${s.files} files, ${s.games.size} expected games, ${s.scan.shown.size} shown games",
        "  system ${s.all.system}, grouping ${s.all.grouping}, name ${s.all.name}, name-readable ${s.all.readable}",
        "  missing ${s.missing}, junk-shown ${s.junkShown}",
        "  scan ${ms(s.scan.totalNanos)} (walk ${ms(s.scan.walkNanos)}, naming ${ms(s.scan.namingNanos)}), " +
            "${fmt(s.millisPer1000Files(s.scan.totalNanos))} ms per 1,000 files",
        "  top failing tags: " + topFailingTags(s).joinToString { "${it.first} ${it.second}" }.ifEmpty { "none" },
    ) + s.scan.identifyLog.take(5).map { "  identify log: $it" } +
        (if (s.scan.identifyLog.size > 5) listOf("  identify log: ... ${s.scan.identifyLog.size - 5} more lines") else emptyList())

    /** Tags by the number of failing expected games carrying them. */
    fun topFailingTags(s: CorpusScores, limit: Int = 10): List<Pair<String, Int>> =
        s.games.filter { it.failed }.flatMap { it.tags }.groupingBy { it }.eachCount().entries
            .sortedWith(compareBy({ -it.value }, { it.key })).take(limit).map { it.key to it.value }

    fun write(directory: File, all: List<CorpusScores>) {
        directory.mkdirs()
        File(directory, "report.md").writeText(markdown(all))
        for (s in all) File(directory, "${s.corpus}-failures.tsv").writeText(failures(s))
    }

    private fun markdown(all: List<CorpusScores>): String = buildString {
        appendLine("# Identification corpus report")
        appendLine()
        appendLine("Made by the library corpus harness (`library/src/test/java/com/plumemu/library/corpus/`).")
        appendLine("Percent of expected-game files (system) or expected games (the others); counts in brackets.")
        for (s in all) {
            appendLine()
            appendLine("## ${s.corpus}")
            appendLine()
            appendLine("${s.files} files, ${s.games.size} expected games, ${s.scan.shown.size} shown games.")
            appendLine()
            appendLine("| Metric | Score |")
            appendLine("|---|---|")
            appendLine("| system | ${s.all.system} |")
            appendLine("| grouping | ${s.all.grouping} |")
            appendLine("| name | ${s.all.name} |")
            appendLine("| name-readable | ${s.all.readable} |")
            appendLine("| missing (files) | ${s.missing} |")
            appendLine("| junk-shown (games) | ${s.junkShown} |")
            appendLine("| scan time | ${ms(s.scan.totalNanos)}: walk ${ms(s.scan.walkNanos)}, naming ${ms(s.scan.namingNanos)} |")
            appendLine("| per 1,000 files | ${fmt(s.millisPer1000Files(s.scan.totalNanos))} ms (walk ${fmt(s.millisPer1000Files(s.scan.walkNanos))}, naming ${fmt(s.millisPer1000Files(s.scan.namingNanos))}) |")
            appendLine()
            appendLine("Top failing tags (failing games): " + topFailingTags(s).joinToString { "`${it.first}` ${it.second}" }.ifEmpty { "none" })
            breakdown("By expected system", s.bySystem)
            breakdown("By tag", s.byTag)
        }
    }

    private fun StringBuilder.breakdown(heading: String, rates: Map<String, Rates>) {
        appendLine()
        appendLine("### $heading")
        appendLine()
        appendLine("| | system | grouping | name | name-readable |")
        appendLine("|---|---|---|---|---|")
        // A "|" splits a table cell even inside backticks (arcade|mame).
        for ((key, r) in rates) appendLine("| `${key.replace("|", "\\|")}` | ${r.system} | ${r.grouping} | ${r.name} | ${r.readable} |")
    }

    const val FAILURE_HEADER =
        "game\tfailed\ttags\texpected_system\texpected_title\texpected_files\tgot_system\tgot_title\tgot_files\tshown_games\tmethod\tconfidence"

    private fun failures(s: CorpusScores): String = buildString {
        appendLine(FAILURE_HEADER)
        for (g in s.games.filter { it.failed }) {
            val failed = listOfNotNull(
                "system".takeIf { !g.systemRight }, "grouping".takeIf { !g.groupedRight },
                "name".takeIf { !g.nameRight }, "name-readable".takeIf { !g.readableRight },
            )
            val row = g.shown?.row
            appendLine(
                listOf(
                    g.id, failed.joinToString(","), g.tags.joinToString(","), g.systemColumn,
                    g.files.first().titles.joinToString(" || "), g.files.joinToString(" | ") { it.path },
                    row?.systemId ?: "-", row?.title ?: "-", g.shown?.paths?.sorted()?.joinToString(" | ") ?: "-",
                    g.shownGames.toString(), row?.idMethod ?: "-", row?.idConfidence ?: "-",
                ).joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') },
            )
        }
    }

    private fun ms(nanos: Long) = String.format(Locale.ROOT, "%.2f s", nanos / 1e9)

    private fun fmt(value: Double) = String.format(Locale.ROOT, "%.1f", value)
}

/**
 * `scores.txt`: per corpus the numbers that must not get worse, as counts so that one game
 * lost shows: `passed/total` for system, grouping, name and name-readable (the percent follows
 * as a comment), plain counts for missing and junk-shown. A total that changed means the corpus
 * was regenerated; that fails too until the scores are rewritten on purpose.
 */
internal object ScoreRatchet {
    const val UPDATE = "rerun with -Pcorpus.update=true after checking build/corpus/report.md"

    /** (corpus, metric) -> the stored value as written (`3489/5239` or `278`). */
    fun parse(text: String): Map<Pair<String, String>, String> = text.lines()
        .map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }
        .associate { line ->
            val parts = line.split(Regex("\\s+"))
            require(parts.size == 3 && VALUE.matches(parts[2])) { "scores.txt: bad line '$line' (corpus metric passed/total, or corpus metric count)" }
            (parts[0] to parts[1]) to parts[2]
        }

    fun format(all: List<CorpusScores>): String = buildString {
        appendLine("# Corpus scores that must not get worse (library corpus harness).")
        appendLine("# passed/total may only gain passes; missing and junk-shown counts may only fall.")
        appendLine("# Written only by: ./gradlew :library:testDebugUnitTest --tests '*CorpusTest' -Pcorpus.update=true")
        for (s in all.sortedBy { it.corpus }) {
            for ((metric, r) in s.ratios) appendLine("${s.corpus} $metric ${r.passed}/${r.total}  # ${fmt(r.percent)}%")
            for ((metric, n) in s.counts) appendLine("${s.corpus} $metric $n")
        }
    }

    /** Every reason the current [all] does not pass against [stored]; empty when it does. */
    fun problems(stored: Map<Pair<String, String>, String>, all: List<CorpusScores>): List<String> {
        val present = all.map { it.corpus }.toSet()
        val gone = stored.keys.map { it.first }.distinct().filter { it !in present }
            .map { "corpus $it is in scores.txt but $it.tsv is gone; $UPDATE" }
        return gone + all.flatMap { s ->
            s.ratios.mapNotNull { (metric, now) -> ratioProblem(s.corpus, metric, stored[s.corpus to metric], now) } +
                s.counts.mapNotNull { (metric, now) ->
                    val before = stored[s.corpus to metric]
                    when {
                        before == null -> missingLine(s.corpus, metric)
                        before.contains('/') -> "${s.corpus} $metric: scores.txt holds '$before', expected a count; $UPDATE"
                        now > before.toInt() -> "${s.corpus} $metric got worse: stored $before, now $now"
                        else -> null
                    }
                }
        }
    }

    private fun ratioProblem(corpus: String, metric: String, before: String?, now: Ratio): String? {
        if (before == null) return missingLine(corpus, metric)
        val passed = before.substringBefore('/').toInt()
        val total = before.substringAfter('/', missingDelimiterValue = "").toIntOrNull()
            ?: return "$corpus $metric: scores.txt holds '$before', expected passed/total; $UPDATE"
        return when {
            total != now.total -> "$corpus $metric: the corpus changed (stored total $total, now ${now.total}); $UPDATE"
            now.passed < passed ->
                "$corpus $metric got worse: stored $passed/$total (${fmt(Ratio(passed, total).percent)}%), now ${now.passed}/$total (${fmt(now.percent)}%)"
            else -> null
        }
    }

    private fun missingLine(corpus: String, metric: String) = "$corpus $metric has no line in scores.txt; $UPDATE"

    private val VALUE = Regex("""\d+(/\d+)?""")

    private fun fmt(value: Double) = String.format(Locale.ROOT, "%.1f", value)
}
