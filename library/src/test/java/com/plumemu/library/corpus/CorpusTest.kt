package com.plumemu.library.corpus

import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Runs the app's scan over every `.tsv` in `src/test/resources/corpus/` and scores what a person would see in the game list: right system,
 * right grouping, right name. Writes `build/corpus/report.md` and `<corpus>-failures.tsv`, prints
 * a summary, and fails when a score in `corpus/scores.txt` got worse, a corpus changed or
 * disappeared, or a line is missing. Only `-Pcorpus.update=true` writes scores.txt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CorpusTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun scoresDoNotGetWorse() {
        // scores.txt holds numbers for a private library listing (the file below), absent from the public source.
        RealData.assumePrivateFile("library/src/test/resources/corpus/owner.tsv")
        RealData.assumePrivateFile("library/src/test/resources/corpus/synthetic.tsv")
        val corpora = CORPUS_DIR.listFiles { f -> f.isFile && f.name.endsWith(".tsv") }.orEmpty().sortedBy { it.name }
        if (corpora.isEmpty()) fail("corpus: no .tsv in ${CORPUS_DIR.absolutePath}; the corpora are part of the repository")
        val rules = RealData.systemRules()
        val systemIds = rules.mapTo(HashSet()) { it.id }
        val scores = corpora.map { file ->
            val corpus = CorpusReader.read(file, systemIds)
            CorpusScorer.score(corpus, CorpusRun.run(corpus, temp.newFolder(corpus.name), rules))
        }
        scores.flatMap(CorpusReport::summary).forEach(::println)
        CorpusReport.write(RealData.REPORT_DIR, scores)
        println("corpus: report in ${RealData.REPORT_DIR.absolutePath}/report.md")

        // scores.txt is written only on request, never as a side effect of a normal run.
        if (System.getProperty("corpus.update") == "true") {
            SCORES.writeText(ScoreRatchet.format(scores))
            println("corpus: wrote ${SCORES.absolutePath}")
            return
        }
        if (!SCORES.isFile) fail("corpus: ${SCORES.absolutePath} is missing; ${ScoreRatchet.UPDATE}")
        val problems = ScoreRatchet.problems(ScoreRatchet.parse(SCORES.readText()), scores)
        if (problems.isNotEmpty()) {
            fail(problems.joinToString("\n", postfix = "\n(an intended change: ${ScoreRatchet.UPDATE}, then commit scores.txt)"))
        }
    }

    companion object {
        val CORPUS_DIR = RealData.CORPUS_DIR
        val SCORES = File(CORPUS_DIR, "scores.txt")
    }
}
