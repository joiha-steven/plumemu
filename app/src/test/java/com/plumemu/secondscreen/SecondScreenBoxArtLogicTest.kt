package com.plumemu.secondscreen

import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripFocus
import com.plumemu.home.ArtPicture
import com.plumemu.home.DetailsExtras
import com.plumemu.home.GameArt
import com.plumemu.home.GameDetails
import com.plumemu.home.GameRowModel
import com.plumemu.home.HomeArtSource
import com.plumemu.home.HomeContent
import com.plumemu.home.SystemGames
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The data side of the card's box art and details: who may read them, what is read, what is kept. */
@OptIn(ExperimentalCoroutinesApi::class)
class SecondScreenBoxArtLogicTest {
    private val names = EntryNames(recent = "Recent", favorites = "Favorites")
    private val metroid = GameRowModel("1", "Super Metroid", "USA", null, scrapeKey = "crc:1")
    private val picture = ArtPicture("/media/1/front.webp", 1240, 886, version = 5)
    private val facts = GameDetails(year = 1994, publisher = "Nintendo", genre = "Action", players = "1", description = "Samus returns.")
    private val result = GameArt(grid = null, large = picture, details = facts)

    private fun content(hasBoxArt: Boolean) =
        HomeContent.of(
            recent = listOf(metroid),
            favorites = emptyList(),
            apps = emptyList(),
            systems = listOf(SystemGames("snes", "Super Nintendo", listOf(metroid))),
            hasBoxArt = hasBoxArt,
        )

    private fun onTheGame(on: HomeContent): SecondScreenContent {
        val index = on.entries.indexOfFirst { it.id == "snes" }
        val look = HomeLook(StripFocus(column = Column.Games, systemIndex = index, systemId = "snes", rows = mapOf("snes" to 0)))
        return secondScreenContentOf(on, look, GameSystems.of(on), names)
    }

    @Test
    fun signedInTheCardNamesTheGamesResultSoItsArtCanBeRead() {
        assertEquals("crc:1", (onTheGame(content(hasBoxArt = true)) as SecondScreenContent.Game).scrapeKey)
    }

    @Test
    fun signedOutTheCardCarriesNoKeyAndSoNothingIsRead() {
        assertNull((onTheGame(content(hasBoxArt = false)) as SecondScreenContent.Game).scrapeKey)
    }

    private fun contentWithRowDetails(hasBoxArt: Boolean): HomeContent {
        val row = metroid.copy(details = facts)
        return HomeContent.of(listOf(row), emptyList(), emptyList(), listOf(SystemGames("snes", "Super Nintendo", listOf(row))), hasBoxArt = hasBoxArt)
    }

    @Test
    fun aRowsOwnDetailsComeWithTheCardOnlyWhenTheTopScreenShowsDetails() {
        // the top screen's details column exists only with box art, so the bottom shows them only then.
        assertEquals(facts, (onTheGame(contentWithRowDetails(hasBoxArt = true)) as SecondScreenContent.Game).details)
        assertNull((onTheGame(contentWithRowDetails(hasBoxArt = false)) as SecondScreenContent.Game).details)
    }

    private val noDatabase = SecondScreenPlayFacts(plays = { error("the play history must not be read") }, resultOf = { if (it == "crc:1") result else null })

    private fun card(key: String? = "crc:1") = SecondScreenContent.Game("Super Metroid", "Super Nintendo", gameId = null, isPlayKnown = false, scrapeKey = key)

    @Test
    fun theFillReadsThePictureAndTheDetailsOfTheKeyAndLeavesTheRestAlone() =
        runBlocking {
            assertEquals(card().copy(art = picture, details = facts), noDatabase.fill(card()))
        }

    @Test
    fun aCardThatHasItsDetailsFromItsRowReadsNothing() =
        runBlocking {
            assertNull(noDatabase.fill(card().copy(details = facts)))
        }

    @Test
    fun aGameWithoutAKeyOrWithoutAResultReadsNoPicture() =
        runBlocking {
            assertNull("no key: nothing to read", noDatabase.fill(card(key = null)))
            assertEquals("a key with no saved front: the card stays without art", card("crc:9"), noDatabase.fill(card("crc:9")))
        }

    @Test
    fun aCardThatHasItsPictureIsNotReadAgain() =
        runBlocking {
            assertNull(noDatabase.fill(card().copy(art = picture)))
        }

    @Test
    fun withoutAReaderNoArtIsRead() =
        runBlocking {
            assertNull(SecondScreenPlayFacts(plays = { error("no") }).fill(card()))
        }

    @Test
    fun aCompactWindowNeverOpensTheArtRead() =
        runBlocking {
            var asked = 0
            val compact = SecondScreenPlayFacts(plays = { error("no") }, resultOf = { asked++; result }, isArtShown = { false })
            assertNull(compact.fill(card()))
            assertEquals(0, asked)
        }

    private class OneResult(
        private val art: Map<String, GameArt>,
        private val synopses: Map<String, String> = emptyMap(),
        private val ratings: Map<String, Int> = emptyMap(),
    ) : HomeArtSource {
        var synopsisReads = 0

        override fun observe(keys: List<String>): Flow<Map<String, GameArt>> = flowOf(art.filterKeys { it in keys })

        override suspend fun extras(key: String): DetailsExtras {
            synopsisReads++
            return DetailsExtras(synopses[key].orEmpty(), ratings[key])
        }

        override val images = com.plumemu.home.CoverImages.None
    }

    @Test
    fun theLargeFrontFactsAndSynopsisOfTheHomeScreensSourceAreTheOnesShown() =
        runBlocking {
            val grid = ArtPicture("/media/1/grid.webp", 560, 400)
            // The source's results carry no synopsis (read for the focused game only): the card reads it with them.
            val stored = facts.copy(description = "")
            val source =
                OneResult(mapOf("crc:1" to GameArt(grid = grid, large = picture, details = stored)), synopses = mapOf("crc:1" to "Samus returns."), ratings = mapOf("crc:1" to 17))
            val read = source.cardResult("crc:1")
            assertEquals(picture, read?.large)
            assertEquals(facts.copy(rating = 17), read?.details)
            assertNull(source.cardResult("crc:2"))
            assertEquals("no result: its synopsis is not read", 1, source.synopsisReads)
        }
}
