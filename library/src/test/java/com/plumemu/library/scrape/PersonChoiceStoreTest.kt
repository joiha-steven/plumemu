package com.plumemu.library.scrape

import com.plumemu.coredata.db.NameFixKind
import com.plumemu.coredata.db.NameTrustNames
import com.plumemu.coredata.db.ScrapeOutcome
import com.plumemu.coredata.db.ScrapedPicture
import com.plumemu.coredata.db.ScrapedPictures
import com.plumemu.identify.NameTrust
import com.plumemu.library.scrape.ScrapeTestLibrary.Companion.DAY
import com.plumemu.library.scrape.ScrapeTestLibrary.Companion.boxFront
import com.plumemu.library.scrape.ScrapeTestLibrary.Companion.matched
import com.plumemu.library.scrape.ScrapeTestLibrary.Companion.support
import com.plumemu.scraper.GameMatch
import com.plumemu.scraper.MatchedBy
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

/** "Fix box art...": the person's pick or "No box art", stored for the game's fix key. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersonChoiceStoreTest {
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

    private suspend fun addGame(doc: String = "zq.sfc", trust: String = NameTrustNames.FILE_NAME, title: String = "zork quest (file)"): Long =
        lib.add(lib.game(doc, fixKeys = key, title = title, trust = trust)).single()

    private fun picked(title: String = "Picked Zork", id: String = "2144") =
        matched(title = title, by = MatchedBy.PersonChoice).copy(match = GameMatch(id, MatchedBy.PersonChoice))

    private val bothSizes = listOf(boxFront("grid"), boxFront("large"), support("grid"), support("large"))

    private suspend fun shown(id: Long) = lib.db.gameDao().game(id)!!.let { it.title to it.nameTrust }

    private suspend fun row() = lib.db.scrapedGameDao().get(key)!!

    @Test
    fun aPickIsStoredAsThePersonsChoiceWithItsPicturesAtBothSizes() = runTest {
        val id = addGame()
        val source = lib.source()

        assertEquals(PersonChoiceStore.STORED, source.storePersonChoice(id, PersonChoice.Picked(picked(), bothSizes)))

        val row = row()
        assertTrue(row.isPersonChoice)
        assertEquals(ScrapeOutcome.MATCHED, row.outcome)
        assertEquals("2144", row.screenScraperId)
        assertEquals("Picked Zork", row.title)
        assertTrue(row.hasBoxFront)
        assertFalse(row.supportMissing)
        val pictures = ScrapedPictures.decode(row.pictures).map { it.kind to it.sizeName }
        assertEquals(
            listOf(ScrapedPicture.BOX_FRONT to "grid", ScrapedPicture.BOX_FRONT to "large", ScrapedPicture.SUPPORT to "grid", ScrapedPicture.SUPPORT to "large"),
            pictures,
        )
        assertEquals("a run does not ask it", emptyList<Any>(), source.pending(10))
    }

    @Test
    fun aPickReplacesTheNameTheIdentifierOrARunGaveOnEveryCopy() = runTest {
        val exact = addGame("zq.sfc", trust = NameTrustNames.IDENTIFIER_EXACT, title = "Zork Quest (identifier)")
        val copy = lib.add(lib.game("copy/zq.sfc", fixKeys = key, title = "zork (file)")).single()
        val source = lib.source()
        source.deliver(source.pending(1).single(), matched(title = "Zork by Hash"), listOf(boxFront()))
        assertEquals("the run's exact name does not beat the identifier's", "Zork Quest (identifier)" to NameTrustNames.IDENTIFIER_EXACT, shown(exact))
        assertEquals("Zork by Hash", shown(copy).first)

        source.storePersonChoice(exact, PersonChoice.Picked(picked(), bothSizes))

        assertEquals("Picked Zork" to NameTrust.SCRAPER_PERSON_CHOICE.name, shown(exact))
        assertEquals("Picked Zork" to NameTrust.SCRAPER_PERSON_CHOICE.name, shown(copy))
    }

    @Test
    fun aNameTheFixNameScreenStoredIsKept() = runTest {
        val id = addGame()
        lib.db.identifyDao().setFix(id, NameFixKind.CHOSEN_TITLE, "Zork Quest (Fix name)", "zork quest (fix name)", "No-Intro", fixedAt = 1)

        lib.source().storePersonChoice(id, PersonChoice.Picked(picked(), bothSizes))

        assertEquals("Zork Quest (Fix name)" to NameTrustNames.PERSON_FIX, shown(id))
        assertTrue("the box art is still the person's pick", row().isPersonChoice)
    }

    @Test
    fun pickingAgainReplacesThePreviousChoiceAndItsName() = runTest {
        val id = addGame()
        val source = lib.source()
        source.storePersonChoice(id, PersonChoice.Picked(picked("First Pick", "1001"), bothSizes))

        source.storePersonChoice(id, PersonChoice.Picked(picked("Second Pick", "1002"), listOf(boxFront("grid"), boxFront("large"))))

        assertEquals(listOf("1002", "Second Pick"), row().let { listOf(it.screenScraperId, it.title) })
        assertTrue(row().supportMissing)
        assertEquals("Second Pick" to NameTrust.SCRAPER_PERSON_CHOICE.name, shown(id))
    }

    @Test
    fun noBoxArtStoresNoPicturesAndKeepsTheName() = runTest {
        val id = addGame()
        val source = lib.source()
        source.storePersonChoice(id, PersonChoice.Picked(picked(), bothSizes))

        assertEquals(PersonChoiceStore.STORED_TO_NAME_AGAIN, source.storePersonChoice(id, PersonChoice.NoBoxArt))

        val row = row()
        assertTrue(row.isPersonChoice)
        assertEquals(ScrapeOutcome.NO_MATCH, row.outcome)
        assertNull(row.screenScraperId)
        assertEquals(emptyList<ScrapedPicture>(), ScrapedPictures.decode(row.pictures))
        assertFalse(row.hasBoxFront)
        // The name is not changed yet; the next naming pass names the game again (ScrapeAfterScanTest).
        assertEquals("Picked Zork" to NameTrust.SCRAPER_PERSON_CHOICE.name, shown(id))
        assertTrue(lib.db.gameDao().game(id)!!.needsIdentify)
    }

    @Test
    fun noBoxArtLeavesAnAutomaticNameAsItIs() = runTest {
        val id = addGame(trust = NameTrustNames.IDENTIFIER_EXACT, title = "Zork Quest")

        assertEquals(PersonChoiceStore.STORED, lib.source().storePersonChoice(id, PersonChoice.NoBoxArt))

        assertEquals("Zork Quest" to NameTrustNames.IDENTIFIER_EXACT, shown(id))
        assertFalse(lib.db.gameDao().game(id)!!.needsIdentify)
    }

    @Test
    fun runsDownloadMissingAndReDownloadNeverTouchAChoice() = runTest {
        val picks = addGame("zq.sfc")
        val none = lib.add(lib.game("none.sfc", fixKeys = "crc:00000001:1024")).single()
        val source = lib.source()
        val requests = source.pending(10)
        source.storePersonChoice(picks, PersonChoice.Picked(picked(), bothSizes))
        source.storePersonChoice(none, PersonChoice.NoBoxArt)

        assertEquals("Download missing", 0, source.askAgain(AskAgain.Missing))
        assertEquals("Re-download", 0, source.askAgain(AskAgain.All))
        lib.now += 31 * DAY
        assertEquals("not even a no-match 31 days old", emptyList<Any>(), source.pending(10))
        // A run that had them in its page delivers its own answers: both choices stay.
        requests.forEach { source.deliver(it, matched(title = "Run's Zork"), listOf(boxFront())) }

        assertEquals(listOf("2144", "Picked Zork"), row().let { listOf(it.screenScraperId, it.title) })
        assertFalse(row().askAgain)
        val noArt = lib.db.scrapedGameDao().get("crc:00000001:1024")!!
        assertEquals(ScrapeOutcome.NO_MATCH to true, noArt.outcome to noArt.isPersonChoice)
        assertEquals("Picked Zork" to NameTrust.SCRAPER_PERSON_CHOICE.name, shown(picks))
        assertEquals("the person's no-art game keeps its name", "none" to NameTrustNames.FILE_NAME, shown(none))
        assertEquals(emptyList<Any>(), source.pendingSupport(10))
    }

    @Test
    fun aChoiceClearsAnAskAgainMark() = runTest {
        val id = addGame()
        val source = lib.source()
        source.deliver(source.pending(1).single(), matched(), listOf(boxFront()))
        source.askAgain(AskAgain.All)
        assertTrue(row().askAgain)

        source.storePersonChoice(id, PersonChoice.Picked(picked(), bothSizes))

        assertFalse(row().askAgain)
        assertEquals(emptyList<Any>(), source.pending(10))
    }

    @Test
    fun aGameBeingNamedOrGoneStoresNothing() = runTest {
        val id = addGame()
        val source = lib.source()
        lib.sql("UPDATE game SET needs_identify = 1 WHERE id = ?", id)

        assertEquals(PersonChoiceStore.NOT_NAMED_YET, source.storePersonChoice(id, PersonChoice.Picked(picked(), bothSizes)))
        assertEquals(PersonChoiceStore.GAME_GONE, source.storePersonChoice(id + 100, PersonChoice.NoBoxArt))
        assertNull(lib.db.scrapedGameDao().get(key))
        assertNull(source.personChoiceRequest(id))
    }

    @Test
    fun thePickIsAskedLikeARunsRequestButFresh() = runTest {
        val id = addGame()
        val source = lib.source()
        val runs = source.pending(1).single()

        val request = source.personChoiceRequest(id)!!

        assertTrue(request.fresh)
        assertEquals(runs.copy(fresh = true).let { listOf(it.systemId, it.title, it.regions, it.languages, it.hints, it.rom.key) }, request.let { listOf(it.systemId, it.title, it.regions, it.languages, it.hints, it.rom.key) })
        assertNull(source.personChoiceRequest(id + 100))
    }

    @Test
    fun aRowKeyedGameIsStoredUnderItsRowKey() = runTest {
        val id = lib.add(lib.game("nokey.sfc", fixKeys = "")).single()

        lib.source().storePersonChoice(id, PersonChoice.NoBoxArt)

        assertEquals(id, lib.db.scrapedGameDao().get("game:$id")!!.gameId)
    }

}
