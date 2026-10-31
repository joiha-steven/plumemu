package com.plumemu.library.scan

import com.plumemu.coredata.db.ArcadeShownGame
import com.plumemu.coredata.db.NameFixKind
import com.plumemu.coredata.db.NameTrustNames
import com.plumemu.identify.Confidence
import com.plumemu.identify.Identification
import com.plumemu.identify.Method
import com.plumemu.identify.ScrapeHints
import com.plumemu.library.naming.ArcadeCopies
import com.plumemu.library.naming.ArcadeTitleClashes
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.io.File

/**
 * Arcade games show short titles; sets of one game in one directory are one row (arcade versions
 * step 2), and shown sets of one folder that would show the same short title show their full
 * descriptions instead: a bootleg always, others when two or more remain. A person's fix
 * is never touched. Made-up sets from [TestNaming]'s arcade pack: zorkfghtu and zorkfghtj are
 * copies of zorkfght, zorkfgt2 is another game of the same name, zorkfghtb a bootleg.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArcadeTitleClashTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var fx: ScanFixture
    private lateinit var naming: GameNaming

    @Before
    fun setUp() {
        ShadowLog.clear()
        fx = ScanFixture(temp.newFolder("fixture"), copyFixtures = false)
        naming = TestNaming.naming(temp.newFolder("packs"))
    }

    @After
    fun tearDown() = fx.close()

    private fun write(path: String) = File(fx.roms, path).apply {
        parentFile?.mkdirs()
        writeBytes(TestNaming.zip("x.rom", ByteArray(16)))
        setLastModified(ScanFixture.BASE_TIME - 60_000)
    }

    private var scans = 0

    /** Every scan gives the folders a new time, so the walk lists them again (added and deleted files). */
    private suspend fun scan() {
        val time = ScanFixture.BASE_TIME + 10_000L * scans++
        fx.roms.walkTopDown().filter { it.isDirectory }.forEach { it.setLastModified(time) }
        fx.tree.opened.clear()
        fx.scanner(naming = naming).scan().toList()
    }

    private suspend fun titles(): Map<String, String> = fx.games().associate { it.fileName to it.title }

    private suspend fun shownTitles(): Map<String, String> = fx.games().filter { it.isPrimaryCopy && !it.isNotGame }.associate { it.fileName to it.title }

    @Test
    fun aLoneSetShowsItsShortTitle() = runTest {
        write("arcade/zorkfghtu.zip")
        write("arcade/zorkqst.zip")
        scan()
        assertEquals(mapOf("zorkfghtu.zip" to "Zork Fighters", "zorkqst.zip" to "Zork Quest"), titles())
        assertTrue("arcade sets are never opened", fx.tree.opened.isEmpty())
    }

    @Test
    fun copiesOfOneGameAreOneRowShowingTheShortTitle() = runTest {
        write("arcade/zorkfght.zip")
        write("arcade/zorkfghtu.zip")
        write("arcade/zorkfghtj.zip")
        write("arcade/testbios.zip")
        scan()
        assertEquals("the US copy shows", mapOf("zorkfghtu.zip" to "Zork Fighters"), shownTitles())
        assertEquals("the copies left out show the short title", setOf("Zork Fighters"), fx.games().filter { !it.isPrimaryCopy }.map { it.title }.toSet())
        assertEquals(setOf("zorkfght.zip", "zorkfghtj.zip"), fx.games().filter { !it.isPrimaryCopy }.map { it.fileName }.toSet())
        val bios = fx.game("testbios.zip")
        assertTrue("a BIOS set is never grouped or changed", bios.isNotGame && bios.isPrimaryCopy)
    }

    @Test
    fun setsThatWouldShareAShortTitleShowTheirDescriptionsUntilTheClashIsGone() = runTest {
        write("arcade/zorkfght.zip")
        write("arcade/zorkfgt2.zip")
        write("arcade/zorkfghtb.zip")
        write("arcade/zorkqst.zip")
        scan()
        assertEquals(
            mapOf(
                "zorkfght.zip" to "Zork Fighters",
                "zorkfgt2.zip" to "Zork Fighters (Japan, other hardware)",
                "zorkfghtb.zip" to "Zork Fighters (bootleg)",
                "zorkqst.zip" to "Zork Quest",
            ),
            titles(),
        )
        assertEquals("zork fighters (japan, other hardware)", fx.game("zorkfgt2.zip").sortName)

        // Another game gone: the bootleg still shows its description, the original the short title.
        File(fx.roms, "arcade/zorkfgt2.zip").delete()
        scan()
        assertEquals(mapOf("zorkfght.zip" to "Zork Fighters", "zorkfghtb.zip" to "Zork Fighters (bootleg)", "zorkqst.zip" to "Zork Quest"), titles())

        File(fx.roms, "arcade/zorkfghtb.zip").delete()
        File(fx.roms, "arcade/zorkfght.zip").delete()
        write("arcade/zorkfgt2.zip")
        scan()
        assertEquals(mapOf("zorkfgt2.zip" to "Zork Fighters", "zorkqst.zip" to "Zork Quest"), titles())
        assertEquals("zork fighters", fx.game("zorkfgt2.zip").sortName)
    }

    @Test
    fun setsOfTwoSystemsOrTwoCopiesOfOneSetDoNotClash() = runTest {
        write("arcade/zorkfgt2.zip")
        write("fbneo/zorkfghtu.zip")
        write("arcade/copy/zorkfghtu.zip")
        write("arcade/zorkfghtu.zip")
        scan()
        val bySystem = fx.games().groupBy { it.systemId }.mapValues { (_, games) -> games.map { it.title }.sorted() }
        assertEquals(listOf("Zork Fighters"), bySystem.getValue("fbneo"))
        // In arcade, zorkfgt2 and zorkfghtu clash; the two copies of zorkfghtu (two directories)
        // do not clash with each other.
        assertEquals(
            listOf("Zork Fighters (Japan, other hardware)", "Zork Fighters (US 961201)", "Zork Fighters (US 961201)"),
            bySystem.getValue("arcade"),
        )
    }

    @Test
    fun aPersonsFixIsNeverChangedAndDoesNotTakePartInAClash() = runTest {
        write("arcade/zorkfght.zip")
        write("arcade/zorkfgt2.zip")
        scan()
        val dao = fx.db.identifyDao()
        val other = fx.game("zorkfgt2.zip")
        assertTrue(dao.setFix(other.id, NameFixKind.OWN_NAME, "Zork Fighters", "zork fighters", null, fixedAt = 1))
        scan()
        // The fix shows "Zork Fighters" too, but only identifier names are told apart; the fix stays.
        assertEquals(mapOf("zorkfght.zip" to "Zork Fighters", "zorkfgt2.zip" to "Zork Fighters"), titles())
        assertEquals(NameTrustNames.PERSON_FIX, fx.game("zorkfgt2.zip").nameTrust)

        // A fix made after the clash was read is not overwritten by the conditional write.
        write("arcade/zorkfghtb.zip")
        scan()
        val bootleg = fx.game("zorkfghtb.zip")
        assertEquals("Zork Fighters (bootleg)", bootleg.title)
        assertTrue(dao.setFix(bootleg.id, NameFixKind.OWN_NAME, "My Zork", "my zork", null, fixedAt = 2))
        assertEquals(0, dao.setArcadeTitle(bootleg.id, "Zork Fighters (bootleg)", "Zork Fighters", "zork fighters"))
        assertEquals("My Zork", fx.game("zorkfghtb.zip").title)
    }

    @Test
    fun aRescanWithNothingChangedLooksNoDescriptionUpAndANewArcadeGameDoes() = runTest {
        write("arcade/zorkfght.zip")
        write("arcade/zorkfgt2.zip")
        scan()
        val dao = fx.db.identifyDao()
        val other = fx.game("zorkfgt2.zip")
        assertEquals("Zork Fighters (Japan, other hardware)", other.title)
        // A stale short title put back by hand: the clash step would correct it if it ran.
        assertEquals(1, dao.setArcadeTitle(other.id, "Zork Fighters (Japan, other hardware)", "Zork Fighters", "zork fighters"))
        scan()
        assertEquals("nothing named, deleted or regrouped: the step did not run", "Zork Fighters", fx.game("zorkfgt2.zip").title)
        write("arcade/zorkqst.zip")
        scan()
        assertEquals("an arcade game was named: the folder is looked at again", "Zork Fighters (Japan, other hardware)", fx.game("zorkfgt2.zip").title)
    }

    /**
     * The arcade pack cannot be installed (damaged in the bundle): the pass keeps the stored
     * grouping as it is, even for a directory the walk listed again, says so in the log, and the
     * next scan with a working pack groups again.
     */
    @Test
    fun withoutTheArcadePackTheGroupingStaysAndTheNextScanGroupsAgain() = runTest {
        write("arcade/zorkfght.zip")
        write("arcade/zorkfghtu.zip")
        scan()
        assertEquals(mapOf("zorkfghtu.zip" to "Zork Fighters"), shownTitles())
        fx.db.gameDao().setFavorite(fx.game("zorkfght.zip").id, true)

        val broken = TestNaming.naming(temp.newFolder("broken-packs"), arcadeBroken = true)
        ShadowLog.clear()
        val time = ScanFixture.BASE_TIME + 10_000L * scans++
        fx.roms.walkTopDown().filter { it.isDirectory }.forEach { it.setLastModified(time) }
        fx.scanner(naming = broken).scan().toList()
        assertEquals("kept as it was", mapOf("zorkfghtu.zip" to "Zork Fighters"), shownTitles())
        assertFalse(fx.game("zorkfght.zip").isPrimaryCopy)
        assertTrue(
            "logged",
            ShadowLog.getLogs().any { it.msg.contains("arcade pack unavailable; copies not grouped") },
        )

        scan()
        assertEquals("the favourite now shows", mapOf("zorkfght.zip" to "Zork Fighters"), shownTitles())
    }

    /**
     * A newer arcade pack no longer lists a set the grouping had left out: the pass names it from
     * its file, shows it again at once although its directory did not change, and has the next
     * scan list the directory again, so the catalogue decides it from then on.
     */
    @Test
    fun aSetANewerPackDropsLeavesTheGroupingAndShowsAgain() = runTest {
        write("arcade/zorkfght.zip")
        write("arcade/zorkfghtu.zip")
        write("arcade/zorkfghtj.zip")
        scan()
        assertFalse(fx.game("zorkfghtj.zip").isPrimaryCopy)

        val newer = TestNaming.naming(temp.newFolder("packs-v2"), arcadeVersion = 2)
        fx.tree.listed.clear()
        fx.scanner(naming = newer).scan().toList() // nothing changed on the card
        assertTrue("the directory was not listed", fx.tree.listed.none { it.contains("arcade") })
        val dropped = fx.game("zorkfghtj.zip")
        assertTrue("shown again", dropped.isPrimaryCopy)
        assertEquals("zorkfghtj", dropped.title)
        assertEquals(mapOf("zorkfghtu.zip" to "Zork Fighters", "zorkfghtj.zip" to "zorkfghtj"), shownTitles())
        val arcadeDirectory = fx.db.scanDao().directories(fx.folderId).single { it.relativePath == "arcade" }
        assertEquals("listed again by the next scan", 0L, arcadeDirectory.lastModified)

        fx.tree.listed.clear()
        fx.scanner(naming = newer).scan().toList()
        assertTrue("listed again", fx.tree.listed.any { it.contains("arcade") })
        assertTrue("the catalogue keeps it shown", fx.game("zorkfghtj.zip").isPrimaryCopy)
        assertFalse(fx.game("zorkfght.zip").isPrimaryCopy)
    }

    @Test
    fun aLookupThePackDoesNotAnswerStopsTheGrouping() {
        val failed = { _: String ->
            Identification(
                title = "x", regions = emptyList(), variant = null, year = null, publisher = null,
                confidence = Confidence.UNKNOWN, method = Method.FILE_NAME,
                hide = false, parent = null, fingerprints = null, fixKeys = emptyList(),
                scrapeHints = ScrapeHints(null, null, null, null, "x.zip", null, "arcade"), packVersion = null,
            )
        }
        assertThrows(ArcadeCopies.NotLookedUp::class.java) { ArcadeCopies.find("dino", failed) }
    }

    @Test
    fun clashRuleOnItsOwn() {
        fun game(id: Long, title: String, system: String = "arcade") = ArcadeShownGame(id, system, "f$id.zip", title)
        val full = mapOf(1L to "Zork (World)", 2L to "Zork (US)", 3L to "Zork (World)", 5L to "Quest (Japan)")
        val games = listOf(game(1, "Zork"), game(2, "Zork"), game(3, "ZORK", "fbneo"), game(5, "Quest (Japan)"))
        // 1 and 2 clash; 3 is another system; 5 is alone and shrinks.
        assertEquals(mapOf(1L to "Zork (World)", 2L to "Zork (US)", 5L to "Quest"), ArcadeTitleClashes.changes(games) { full[it.id] })
    }

    @Test
    fun aGroupWithADescriptionThatCannotBeLookedUpIsLeftAsItIs() {
        fun game(id: Long, title: String) = ArcadeShownGame(id, "arcade", "f$id.zip", title)
        val shown = listOf(game(1, "Zork (World)"), game(2, "Zork (US)"))
        // 1 of 2 looked up: "Zork (World)" alone must not make both fall back to "Zork".
        assertEquals(emptyMap<Long, String>(), ArcadeTitleClashes.changes(shown) { if (it.id == 1L) "Zork (World)" else null })
        // 0 of 2 looked up (pack unavailable or broken).
        assertEquals(emptyMap<Long, String>(), ArcadeTitleClashes.changes(shown) { null })
        // A lone game needs no lookup and still shrinks.
        assertEquals(mapOf(1L to "Zork"), ArcadeTitleClashes.changes(listOf(game(1, "Zork (World)"))) { null })
    }
}
