package com.plumemu.library.corpus

import com.plumemu.identify.name.MatchKey
import java.util.Locale

/** [passed] of [total]; [percent] with one decimal, 100 when there is nothing to count. */
internal data class Ratio(val passed: Int, val total: Int) {
    val percent: Double get() = if (total == 0) 100.0 else Math.round(passed * 1000.0 / total) / 10.0

    override fun toString() = String.format(Locale.ROOT, "%.1f%% (%d/%d)", percent, passed, total)
}

/** The four rates over one slice of a corpus (all of it, one system, one tag). */
internal data class Rates(val system: Ratio, val grouping: Ratio, val name: Ratio, val readable: Ratio)

/** How one expected game came out. */
internal data class GameResult(
    val id: String,
    val files: List<CorpusRow>,
    /** Files inside a shown game of an expected system. */
    val filesOnRightSystem: Int,
    val groupedRight: Boolean,
    /** The shown game holding most of its files; null when none holds any. */
    val shown: ShownGame?,
    val nameRight: Boolean,
    val readableRight: Boolean,
    /** Shown games holding at least one of its files. */
    val shownGames: Int,
) {
    val systemRight get() = filesOnRightSystem == files.size
    val failed get() = !systemRight || !groupedRight || !nameRight
    val tags: Set<String> get() = files.flatMapTo(sortedSetOf()) { it.tags }
    val systemColumn: String get() = files.first().systemColumn
}

internal data class CorpusScores(
    val corpus: String,
    val files: Int,
    val all: Rates,
    /** Expected-game files in no shown game. */
    val missing: Int,
    /** Shown games made only of files that are not games. */
    val junkShown: Int,
    val bySystem: Map<String, Rates>,
    val byTag: Map<String, Rates>,
    val games: List<GameResult>,
    val scan: CorpusScan,
) {
    /** The ratcheted ratios and counts, in scores.txt order. */
    val ratios: List<Pair<String, Ratio>>
        get() = listOf("system" to all.system, "grouping" to all.grouping, "name" to all.name, "name-readable" to all.readable)
    val counts: List<Pair<String, Int>> get() = listOf("missing" to missing, "junk-shown" to junkShown)

    fun millisPer1000Files(nanos: Long): Double = if (files == 0) 0.0 else nanos / 1e6 / files * 1000
}

internal object CorpusScorer {
    fun score(corpus: Corpus, scan: CorpusScan): CorpusScores {
        val games = corpus.games.map { (id, files) -> result(id, files, scan) }
        val gameFiles = corpus.rows.filter { it.game != null }
        val missing = gameFiles.count { scan.gameOfPath[CorpusRun.key(it.path)] == null }
        val notGames = corpus.rows.filter { it.game == null }.mapTo(HashSet()) { CorpusRun.key(it.path) }
        val junkShown = scan.shown.count { it.paths.isNotEmpty() && it.paths.all { p -> p in notGames } }
        return CorpusScores(
            corpus = corpus.name,
            files = corpus.rows.size,
            all = rates(gameFiles, games, scan),
            missing = missing,
            junkShown = junkShown,
            bySystem = slices(gameFiles, games, scan, { listOf(it.systemColumn) }, { listOf(it.systemColumn) }),
            byTag = slices(gameFiles, games, scan, { it.tags }, { it.tags.toList() }),
            games = games,
            scan = scan,
        )
    }

    private fun <K : Comparable<K>> slices(
        files: List<CorpusRow>,
        games: List<GameResult>,
        scan: CorpusScan,
        fileKeys: (CorpusRow) -> List<K>,
        gameKeys: (GameResult) -> List<K>,
    ): Map<K, Rates> {
        val keys = (files.flatMap(fileKeys) + games.flatMap(gameKeys)).toSortedSet()
        return keys.associateWith { k -> rates(files.filter { k in fileKeys(it) }, games.filter { k in gameKeys(it) }, scan) }
    }

    private fun rates(files: List<CorpusRow>, games: List<GameResult>, scan: CorpusScan) = Rates(
        system = Ratio(files.count { onRightSystem(it, scan) }, files.size),
        grouping = Ratio(games.count { it.groupedRight }, games.size),
        name = Ratio(games.count { it.nameRight }, games.size),
        readable = Ratio(games.count { it.readableRight }, games.size),
    )

    private fun onRightSystem(row: CorpusRow, scan: CorpusScan): Boolean =
        scan.gameOfPath[CorpusRun.key(row.path)]?.row?.systemId in row.systems.orEmpty()

    private fun result(id: String, files: List<CorpusRow>, scan: CorpusScan): GameResult {
        val holders = files.mapNotNull { scan.gameOfPath[CorpusRun.key(it.path)] }
        val counts = holders.groupingBy { it }.eachCount()
        val most = counts.values.maxOrNull()
        val tied = counts.filterValues { it == most }.keys
        val firstHolder = scan.gameOfPath[CorpusRun.key(files.first().path)]
        val shown = if (firstHolder != null && firstHolder in tied) firstHolder else holders.firstOrNull { it in tied }
        val expectedPaths = files.mapTo(HashSet()) { CorpusRun.key(it.path) }
        val title = shown?.row?.title?.trim()
        val alternatives = files.first().titles
        return GameResult(
            id = id,
            files = files,
            filesOnRightSystem = files.count { onRightSystem(it, scan) },
            groupedRight = counts.size == 1 && shown!!.paths == expectedPaths,
            shown = shown,
            nameRight = title != null && title in alternatives,
            readableRight = title != null && alternatives.any { readablySame(title, it) },
            shownGames = counts.size,
        )
    }

    /** Equal after identify's `MatchKey` (case, accents, punctuation, articles, roman numerals). */
    fun readablySame(a: String, b: String): Boolean {
        if (a == b) return true
        val key = MatchKey.of(a)
        return key.isNotEmpty() && key == MatchKey.of(b)
    }
}
