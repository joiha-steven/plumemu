package com.plumemu.library.corpus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The corpus reader and the scores ratchet, without a scan. */
class CorpusReaderTest {
    private fun corpus(vararg rows: String) =
        CorpusReader.parse("t", (listOf("# comment", CorpusReader.HEADER) + rows).joinToString("\n"), KNOWN)

    private fun error(vararg rows: String): String = try {
        corpus(*rows)
        throw AssertionError("no error")
    } catch (e: CorpusFormatException) {
        e.message!!
    }

    @Test
    fun readsEscapesAlternativesTagsAndRecipes() {
        val c = corpus(
            "snes/Caf\\u00e9 (Japan).sfc\t12\tsnes\tg1\tCaf\\u00e9 ||  Cafe \tjapanese, clean\t-",
            "arcade/sf2.zip\t5\tarcade|mame\tg2\tStreet Zork\t-\tzip:a:b.rom:0000ABCD:4096",
            "snes/._x.sfc\t1\t-\t-\t-\tjunk\t-",
            "\ud83d\ude00/\\ud83d\\ude00.sfc\t1\tsnes\tg3\tSmile\t-\t-",
        )
        val (cafe, sf2, junk, smile) = c.rows
        assertEquals("snes/Caf\u00e9 (Japan).sfc", cafe.path)
        assertEquals(listOf("Caf\u00e9", "Cafe"), cafe.titles)
        assertEquals(listOf("japanese", "clean"), cafe.tags)
        assertEquals(setOf("arcade", "mame"), sf2.systems)
        assertEquals(Content.Zip("a:b.rom", 0xABCDL, 4096), sf2.content)
        assertEquals(emptyList<String>(), sf2.tags)
        assertEquals(null, junk.systems)
        assertEquals(null, junk.game)
        assertEquals("\ud83d\ude00/\ud83d\ude00.sfc", smile.path)
        assertEquals(6, smile.line)
        assertEquals(listOf("g1", "g2", "g3"), c.games.keys.toList())
    }

    @Test
    fun errorsNameTheLine() {
        assertTrue(error("a.sfc\t1\tsnes\tg\tT\t-").startsWith("t:3: expected 7"))
        assertTrue(error("a.sfc\t1\tsnes\tg\tT\t-\tfloppy:x").let { it.startsWith("t:3:") && "unknown content recipe" in it })
        assertTrue(error("a.sfc\tbig\tsnes\tg\tT\t-\t-").startsWith("t:3: size"))
        assertTrue(error("a\\x41.sfc\t1\tsnes\tg\tT\t-\t-").startsWith("t:3: bad escape"))
        assertTrue(error("a.sfc\t1\t-\tg\tT\t-\t-").startsWith("t:3: system and game"))
        assertTrue(error("a.sfc\t1\tsnes\tg\tT\t-\t-", "b.sfc\t1\tsnes\tg\tU\t-\t-").startsWith("t:4: game 'g' has title 'T' on line 3"))
        assertTrue(error("a.sfc\t1\tsnes\tg\tT\t-\t-", "a.sfc\t1\tsnes\th\tT\t-\t-").startsWith("t:4: path 'a.sfc' already on line 3"))
        assertTrue(error("../a.sfc\t1\tsnes\tg\tT\t-\t-").startsWith("t:3: path"))
        assertEquals(
            "t:4: 'snes' is the same folder as 'SNES' (line 3) on a case-blind disk",
            error("SNES/a.sfc\t1\tsnes\tg\tT\t-\t-", "snes/b.sfc\t1\tsnes\th\tU\t-\t-"),
        )
        assertEquals(
            "t:4: 'Card 1/a.SFC' is the same file as 'Card 1/a.sfc' (line 3) on a case-blind disk",
            error("Card 1/a.sfc\t1\tsnes\tg\tT\t-\t-", "Card 1/a.SFC\t1\tsnes\th\tU\t-\t-"),
        )
        assertTrue(error("a.sfc\t1\tsnes\tg\tT\t-\tzip:e:12345:1").startsWith("t:3: content"))
        assertEquals("t:3: system id 'genesis' is not in platforms.json", error("a.md\t1\tmegadrive|genesis\tg\tT\t-\t-"))
        val noHeader = try {
            CorpusReader.parse("t", "path\tsize\n", KNOWN)
            ""
        } catch (e: CorpusFormatException) {
            e.message!!
        }
        assertTrue(noHeader.startsWith("t:1: header"))
    }

    @Test
    fun theSameSpellingInSeveralPlacesIsNoClash() {
        val c = corpus(
            "Card 1/SNES/a.sfc\t1\tsnes\tg\tT\t-\t-",
            "Card 2/snes/b.sfc\t1\tsnes\th\tU\t-\t-",
            "SNES/c.sfc\t1\tsnes\ti\tV\t-\t-",
            "SNES/d.sfc\t1\tsnes\tj\tW\t-\t-",
        )
        assertEquals(4, c.rows.size)
    }

    @Test
    fun readableNamesIgnoreCaseAndPunctuationButNotWords() {
        assertTrue(CorpusScorer.readablySame("zork quest ii", "Zork Quest 2"))
        assertTrue(CorpusScorer.readablySame("Legend of Zork, The", "The Legend of Zork"))
        assertTrue(!CorpusScorer.readablySame("Zork Quest", "Zork Quest 2"))
    }

    @Test
    fun theRatchetComparesCountsNotRoundedPercents() {
        val stored = ScoreRatchet.parse("# c\nsynthetic system 3490/5240  # 66.6%\nsynthetic grouping 1/1\nsynthetic name 1/2\n" +
            "synthetic name-readable 1/1\nsynthetic missing 3\nsynthetic junk-shown 0\n")
        // 3488/5240 still prints 66.6%: only the count shows the two games lost.
        assertEquals(
            listOf("synthetic system got worse: stored 3490/5240 (66.6%), now 3488/5240 (66.6%)", "synthetic missing got worse: stored 3, now 4"),
            ScoreRatchet.problems(stored, listOf(scores(system = Ratio(3488, 5240), missing = 4))),
        )
        assertEquals(emptyList<String>(), ScoreRatchet.problems(stored, listOf(scores(system = Ratio(3491, 5240), missing = 2))))
        val changed = ScoreRatchet.problems(stored, listOf(scores(system = Ratio(3491, 5300), missing = 3)))
        assertEquals(listOf("synthetic system: the corpus changed (stored total 5240, now 5300); ${ScoreRatchet.UPDATE}"), changed)
        val written = ScoreRatchet.parse(ScoreRatchet.format(listOf(scores(system = Ratio(3491, 5240), missing = 2))))
        assertEquals("3491/5240", written.getValue("synthetic" to "system"))
        assertEquals("2", written.getValue("synthetic" to "missing"))
    }

    @Test
    fun theRatchetFailsOnWhatIsNotThere() {
        val stored = ScoreRatchet.parse("owner system 1/1\nsynthetic system 1/1\n")
        val problems = ScoreRatchet.problems(stored, listOf(scores(system = Ratio(1, 1), missing = 0)))
        assertTrue(problems.contains("corpus owner is in scores.txt but owner.tsv is gone; ${ScoreRatchet.UPDATE}"))
        assertTrue(problems.contains("synthetic grouping has no line in scores.txt; ${ScoreRatchet.UPDATE}"))
        assertTrue(problems.contains("synthetic missing has no line in scores.txt; ${ScoreRatchet.UPDATE}"))
    }

    private fun scores(system: Ratio, missing: Int): CorpusScores {
        val rates = Rates(system, Ratio(1, 1), Ratio(1, 2), Ratio(1, 1))
        return CorpusScores("synthetic", 1, rates, missing, 0, emptyMap(), emptyMap(), emptyList(), CorpusScan(emptyList(), 0, 0, emptyList()))
    }

    private companion object {
        val KNOWN = setOf("snes", "arcade", "mame", "megadrive")
    }
}
