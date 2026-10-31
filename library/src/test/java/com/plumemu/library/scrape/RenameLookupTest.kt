package com.plumemu.library.scrape

import com.plumemu.coredata.db.FixKeys
import com.plumemu.coredata.db.NameFixKind
import com.plumemu.coredata.db.NameTrustNames
import com.plumemu.coredata.db.ScrapeOutcome
import com.plumemu.library.scrape.ScrapeTestLibrary.Companion.boxFront
import com.plumemu.library.scrape.ScrapeTestLibrary.Companion.matched
import com.plumemu.scraper.GameMatch
import com.plumemu.scraper.MatchedBy
import com.plumemu.scraper.ScrapeResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The box-art lookup after a rename, on the library's side: asked only for a renamed game with no
 * box art; stored like a pick, and only if that still holds when the answer comes back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RenameLookupTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var lib: ScrapeTestLibrary

    @Before
    fun setUp() {
        lib = ScrapeTestLibrary(temp.newFile("plumemu.db").also { it.delete() })
    }

    @After
    fun tearDown() = lib.close()

    private val key = "crc:0000abcd:1024"
    private val name = "Zork Quest"

    private suspend fun renamedGame(kind: NameFixKind = NameFixKind.OWN_NAME, fixKeys: String = key): Long {
        val id = lib.add(lib.game("zq.sfc", fixKeys = fixKeys, title = "zq (file)")).single()
        lib.db.identifyDao().setFix(id, kind, name, name.lowercase(), null, fixedAt = 1)
        return id
    }

    private fun found(id: String = "2144") =
        PersonChoice.Picked(matched(title = "Zork Quest (US)", by = MatchedBy.PersonChoice).copy(match = GameMatch(id, MatchedBy.PersonChoice)), listOf(boxFront("grid"), boxFront("large")))

    private fun skip(reason: RenameSkip) = RenameLookup.Skip(reason)

    @Test
    fun aRenamedGameWithoutBoxArtIsAskedForByItsNewNameOnItsSystem() = runTest {
        val own = renamedGame()

        val lookup = lib.source().renameLookup(own) as RenameLookup.Ready

        assertEquals(name, lookup.title)
        assertEquals("snes", lookup.request.systemId)
        assertTrue("fetched fresh, like a pick", lookup.request.fresh)
    }

    @Test
    fun aChosenTitleIsAskedForToo() = runTest {
        val chosen = renamedGame(NameFixKind.CHOSEN_TITLE)

        assertEquals(name, (lib.source().renameLookup(chosen) as RenameLookup.Ready).title)
    }

    @Test
    fun aRunsNoMatchIsNoBoxArtAndAnyGameMayBeStored() = runTest {
        val id = renamedGame()
        val source = lib.source()
        source.deliver(source.pending(1).single(), ScrapeResult.NoMatch, emptyList())

        assertEquals(null, (source.renameLookup(id) as RenameLookup.Ready).fileMatchId)
        assertNull(source.storeRenameMatch(id, name, found("2144")))
    }

    @Test
    fun aRunsMatchByTheFileWithoutABoxFrontOnlyTakesTheSameGame() = runTest {
        // Rule: a game the file itself matched is never replaced by another
        // game a name search found; the same game's box art may still be stored.
        val id = renamedGame()
        val source = lib.source()
        source.deliver(source.pending(1).single(), matched(withSupport = false), emptyList())
        assertFalse(lib.db.scrapedGameDao().get(key)!!.hasBoxFront)

        assertEquals("1245", (source.renameLookup(id) as RenameLookup.Ready).fileMatchId)
        assertEquals(RenameSkip.MATCHED_BY_FILE, source.storeRenameMatch(id, name, found("2144")))
        assertEquals("the run's match stays", "1245", lib.db.scrapedGameDao().get(key)!!.screenScraperId)

        assertNull("the same game", source.storeRenameMatch(id, name, found("1245")))
        assertTrue(lib.db.scrapedGameDao().get(key)!!.hasBoxFront)
    }

    @Test
    fun aRunsMatchByNameWithoutABoxFrontMayBeReplaced() = runTest {
        val id = renamedGame()
        val source = lib.source()
        source.deliver(source.pending(1).single(), matched(by = MatchedBy.Name, withSupport = false), emptyList())

        assertEquals(null, (source.renameLookup(id) as RenameLookup.Ready).fileMatchId)
        assertNull(source.storeRenameMatch(id, name, found("2144")))
    }

    @Test
    fun aRunsBoxFrontIsLeftAlone() = runTest {
        val id = renamedGame()
        val source = lib.source()
        source.deliver(source.pending(1).single(), matched(), listOf(boxFront()))

        assertEquals(skip(RenameSkip.HAS_BOX_ART), source.renameLookup(id))
        assertEquals(RenameSkip.HAS_BOX_ART, source.storeRenameMatch(id, name, found("9999")))
        assertEquals("the run's game stays", "1245", lib.db.scrapedGameDao().get(key)!!.screenScraperId)
    }

    @Test
    fun thePersonsPickOrNoBoxArtIsNeverTouched() = runTest {
        val picked = renamedGame()
        val source = lib.source()
        source.storePersonChoice(picked, PersonChoice.Picked(matched(by = MatchedBy.PersonChoice).copy(match = GameMatch("1001", MatchedBy.PersonChoice)), listOf(boxFront())))
        assertEquals(skip(RenameSkip.PERSON_CHOICE), source.renameLookup(picked))
        assertEquals(RenameSkip.PERSON_CHOICE, source.storeRenameMatch(picked, name, found()))
        assertEquals("1001", lib.db.scrapedGameDao().get(key)!!.screenScraperId)

        source.storePersonChoice(picked, PersonChoice.NoBoxArt)
        assertEquals(skip(RenameSkip.PERSON_CHOICE), source.renameLookup(picked))
        assertEquals(RenameSkip.PERSON_CHOICE, source.storeRenameMatch(picked, name, found()))
        assertEquals(ScrapeOutcome.NO_MATCH, lib.db.scrapedGameDao().get(key)!!.outcome)
    }

    @Test
    fun notAGameOrBackToAutomaticIsNoRename() = runTest {
        val id = renamedGame()
        lib.db.identifyDao().setFix(id, NameFixKind.NOT_A_GAME, null, null, null, fixedAt = 2)
        assertEquals(skip(RenameSkip.NAME_CHANGED), lib.source().renameLookup(id))

        val other = lib.add(lib.game("other.sfc")).single()
        lib.db.identifyDao().setFix(other, NameFixKind.OWN_NAME, "Mine", "mine", null, fixedAt = 1)
        assertTrue(lib.db.identifyDao().backToAutomatic(other))
        assertEquals("the naming pass is to run", skip(RenameSkip.BEING_NAMED), lib.source().renameLookup(other))
        lib.sql("UPDATE game SET needs_identify = 0 WHERE id = ?", other)
        assertEquals("named again, no fix left", skip(RenameSkip.NAME_CHANGED), lib.source().renameLookup(other))
    }

    @Test
    fun aGameGoneOrBeingNamedIsSkipped() = runTest {
        val id = renamedGame()
        assertEquals(skip(RenameSkip.GAME_GONE), lib.source().renameLookup(id + 100))
        assertEquals(RenameSkip.GAME_GONE, lib.source().storeRenameMatch(id + 100, name, found()))

        assertEquals("a rescan is naming it", 1, lib.db.scrapedGameDao().nameAgainIfTrust(id, NameTrustNames.PERSON_FIX))
        assertEquals(skip(RenameSkip.BEING_NAMED), lib.source().renameLookup(id))
        assertEquals(RenameSkip.BEING_NAMED, lib.source().storeRenameMatch(id, name, found()))
        assertNull(lib.db.scrapedGameDao().get(key))
    }

    @Test
    fun aCertainMatchIsStoredLikeAPickAndTheNameStaysThePersons() = runTest {
        val id = renamedGame()
        val source = lib.source()

        assertNull(source.storeRenameMatch(id, name, found()))

        val row = lib.db.scrapedGameDao().get(key)!!
        assertTrue(row.isPersonChoice)
        assertEquals(ScrapeOutcome.MATCHED, row.outcome)
        assertEquals("2144", row.screenScraperId)
        assertTrue(row.hasBoxFront)
        assertEquals("the person's own name is kept", name to NameTrustNames.PERSON_FIX, lib.db.gameDao().game(id)!!.let { it.title to it.nameTrust })
        assertEquals("a run does not ask it again", emptyList<Any>(), source.pending(10))
    }

    @Test
    fun aNameChangedWhileTheLookupRanStoresNothing() = runTest {
        val id = renamedGame()
        lib.db.identifyDao().setFix(id, NameFixKind.OWN_NAME, "Another Name", "another name", null, fixedAt = 2)

        assertEquals(RenameSkip.NAME_CHANGED, lib.source().storeRenameMatch(id, name, found()))
        assertNull(lib.db.scrapedGameDao().get(key))
    }

    @Test
    fun boxArtThatArrivedWhileTheLookupRanIsKept() = runTest {
        val id = renamedGame()
        val source = lib.source()
        assertTrue(source.renameLookup(id) is RenameLookup.Ready)
        source.deliver(source.pending(1).single(), matched(), listOf(boxFront()))

        assertEquals(RenameSkip.HAS_BOX_ART, source.storeRenameMatch(id, name, found("9999")))
        assertEquals("1245", lib.db.scrapedGameDao().get(key)!!.screenScraperId)
    }

    @Test
    fun theContentKeyLandingBeforeTheStoreDoesNotMakeItMiss() = runTest {
        // A loose cartridge read at QUICK depth: no fingerprint key, its fix on the row key.
        val id = renamedGame(fixKeys = "")
        val rowKey = "game:$id"
        assertTrue(lib.source().renameLookup(id) is RenameLookup.Ready)

        // The rename's whole-file read lands: the game is now keyed by its content.
        assertTrue(lib.db.identifyDao().addContentKeys(id, 1024, 7, "", listOf(key)))
        assertNull(lib.source().storeRenameMatch(id, name, found()))

        val shownKey = FixKeys.forGame(id, lib.db.gameDao().game(id)!!.fixKeys).first()
        assertEquals("the key the screens read", key, shownKey)
        assertEquals("2144", lib.db.scrapedGameDao().get(shownKey)!!.screenScraperId)
        assertNull("nothing left under the old row key", lib.db.scrapedGameDao().get(rowKey))
    }

    @Test
    fun boxArtUnderTheRowKeyBeforeTheContentKeyLandedCounts() = runTest {
        val id = renamedGame(fixKeys = "")
        val source = lib.source()
        source.deliver(source.pending(1).single(), matched(), listOf(boxFront()))
        assertTrue(lib.db.identifyDao().addContentKeys(id, 1024, 7, "", listOf(key)))

        assertEquals("the game had box art when it was renamed", skip(RenameSkip.HAS_BOX_ART), source.renameLookup(id))
    }
}
