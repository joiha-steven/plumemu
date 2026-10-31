package com.plumemu.library.corpus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The harness itself, on a hand-written sample whose answers are known for today's code
 * (`src/test/resources/corpus-sample/sample.tsv`): rows that must pass (cue+bin, m3u, a disc
 * pair, gdi tracks, regional copies, a zip whose CRC is in the real GBA pack, a name the real SNES
 * pack matches, an escaped title) and rows tagged `wrong` that must fail, so a harness bug that
 * counts everything as right (or wrong) cannot hide a regression.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CorpusHarnessTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun scoresTheSampleAsExpected() {
        val rules = RealData.systemRules()
        val corpus = CorpusReader.read(RealData.SAMPLE, rules.mapTo(HashSet()) { it.id })
        val scan = CorpusRun.run(corpus, temp.newFolder("run"), rules)
        val s = CorpusScorer.score(corpus, scan)
        CorpusReport.summary(s).forEach(::println)
        val games = s.games.associateBy { it.id }

        // Grouping: what each shown game stands for.
        assertEquals(setOf("psx/Zork Harbor Tales (USA).cue", "psx/Zork Harbor Tales (USA).bin"), games.getValue("cue-bin").shown!!.paths)
        assertEquals(5, games.getValue("m3u").shown!!.paths.size)
        assertEquals("psx/Zork Saga (USA).m3u", games.getValue("m3u").shown!!.row.fileName.let { "psx/$it" })
        assertEquals(4, games.getValue("gdi").shown!!.paths.size)
        assertEquals(2, games.getValue("copies").shown!!.paths.size)
        val right = listOf("cue-bin", "m3u", "disc-pair", "gdi", "copies", "pack-crc", "pack-name", "escaped", "readable-only")
        for (id in right) assertTrue("$id grouped", games.getValue(id).groupedRight)

        // Names: the real packs answer for the zip's CRC and the cleaned file name.
        assertEquals("Bionicle", games.getValue("pack-crc").shown!!.row.title)
        assertEquals("CRC", games.getValue("pack-crc").shown!!.row.idMethod)
        assertEquals("Super Bomberman 3", games.getValue("pack-name").shown!!.row.title)
        assertEquals("NAME_MATCH", games.getValue("pack-name").shown!!.row.idMethod)
        assertEquals("Caf\u00e9 Zork", games.getValue("escaped").shown!!.row.title)
        val readable = games.getValue("readable-only")
        assertTrue(!readable.nameRight && readable.readableRight)

        // Wrong on purpose.
        assertEquals(null, games.getValue("wrong-missing").shown)
        assertTrue(!games.getValue("wrong-name").nameRight && !games.getValue("wrong-name").readableRight)
        assertTrue(!games.getValue("wrong-split-1").groupedRight && !games.getValue("wrong-split-2").groupedRight)
        assertEquals("nes", games.getValue("wrong-system").shown!!.row.systemId)
        assertEquals(setOf("wrong-missing", "wrong-name", "wrong-split-1", "wrong-split-2", "wrong-system", "readable-only"), s.games.filter { it.failed }.map { it.id }.toSet())

        // 24 game files, 14 games; see the sample's rows.
        assertEquals(Ratio(22, 24), s.all.system)
        assertEquals(Ratio(11, 14), s.all.grouping)
        assertEquals(Ratio(11, 14), s.all.name)
        assertEquals(Ratio(12, 14), s.all.readable)
        assertEquals(1, s.missing)
        assertEquals(1, s.junkShown)
        assertEquals(Ratio(2, 5), s.byTag.getValue("wrong").grouping)
        assertEquals(Ratio(11, 11), s.bySystem.getValue("psx").system)
    }

    @Test
    fun theZipRecipeCarriesACrcTheRealGbaPackLists() {
        val naming = RealData.naming(temp.newFolder("packs"))
        val pack = naming.naming.packs.packFor("gba")!!
        assertEquals(listOf("Bionicle"), pack.byCrc(0x5412a24dL, 8_388_608).map { it.title }.distinct())
    }
}
