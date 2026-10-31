package com.plumemu.home.search

import com.plumemu.home.FakeHomeContent
import com.plumemu.home.GameRowModel
import com.plumemu.home.HomeContent
import com.plumemu.home.SystemGames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Search's matching rules. */
class SearchIndexTest {
    private val content =
        HomeContent.of(
            recent = listOf(GameRowModel("r1", "Super Mario World", "USA")),
            favorites = listOf(GameRowModel("f1", "Pok\u00e9mon \u00c9meraude", "FRA")),
            apps = listOf(GameRowModel("app", "Mario Kart Tour", "")),
            systems =
                listOf(
                    SystemGames(
                        "snes",
                        "Super Nintendo",
                        listOf(
                            GameRowModel("s1", "Super Mario World", "USA"),
                            GameRowModel("s2", "The Legend of Zelda: A Link to the Past", "USA"),
                            GameRowModel("s3", "\u0110\u01b0\u1eddng L\u00ean \u0110\u1ec9nh Olympia", "VNM"),
                        ),
                    ),
                    SystemGames("gba", "Game Boy Advance", listOf(GameRowModel("g1", "Pok\u00e9mon \u00c9meraude", "FRA"), GameRowModel("g2", "Mario Kart", "USA"))),
                ),
        )
    private val index = SearchIndex.of(content)

    private fun ids(query: String) = index.find(query).map { it.row.id }

    @Test
    fun caseAndAccentsDoNotMatter() {
        assertEquals(listOf("g1"), ids("POKEMON emeraude"))
        assertEquals(listOf("g1"), ids("pok\u00e9mon"))
        assertEquals("letters without a decomposition are spelled out", listOf("s3"), ids("duong len dinh"))
    }

    @Test
    fun wordsMatchInAnyOrderAndPunctuationIsASpace() {
        assertEquals(listOf("s1"), ids("world super"))
        assertEquals(listOf("s2"), ids("zelda link"))
        assertEquals(listOf("s2"), ids("zelda: a link"))
        assertEquals(listOf("s1", "g2"), ids("  mario  "))
    }

    @Test
    fun apostrophesAreDroppedNotSpaces() {
        val apostrophes =
            SearchIndex.of(
                HomeContent.of(
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    listOf(
                        SystemGames(
                            "snes",
                            "Super Nintendo",
                            listOf(GameRowModel("y1", "Yoshi's Island", "USA"), GameRowModel("y2", "Kirby\u2019s Dream Land", "USA"), GameRowModel("y3", "Ogre\u02BCs Battle", "USA"), GameRowModel("y4", "Kirby\u2018s Avalanche", "USA")),
                        ),
                    ),
                ),
            )
        assertEquals(listOf("y1"), apostrophes.find("yoshis island").map { it.row.id })
        assertEquals(listOf("y1"), apostrophes.find("yoshi's").map { it.row.id })
        assertEquals(listOf("y2"), apostrophes.find("kirbys dream").map { it.row.id })
        assertEquals(listOf("y3"), apostrophes.find("ogres").map { it.row.id })
        assertEquals("a left quote typed as an apostrophe", listOf("y4"), apostrophes.find("kirbys avalanche").map { it.row.id })
    }

    @Test
    fun aBlankQueryFindsNothingAndNoMatchIsEmpty() {
        assertTrue(ids("").isEmpty())
        assertTrue(ids("   ").isEmpty())
        assertTrue(ids("metroid").isEmpty())
    }

    @Test
    fun onlySystemListsAreSearchedInTheirOrderAndEachHitKnowsItsSystem() {
        // Recent and Favorites repeat system games, Apps are not games.
        assertEquals(listOf("s1", "g2"), ids("mario"))
        assertEquals(listOf("snes", "gba"), index.find("mario").map { it.systemId })
    }

    @Test
    fun hiddenSystemsGamesAreStillFoundAfterTheColumnsOnes() {
        // Settings › Library › Systems hides a system from the column, not its games from Search.
        val withHidden =
            HomeContent.of(
                recent = emptyList(),
                favorites = emptyList(),
                apps = emptyList(),
                systems = listOf(SystemGames("gba", "Game Boy Advance", listOf(GameRowModel("g2", "Mario Kart", "USA")))),
                hiddenSystems = listOf(SystemGames("snes", "Super Nintendo", listOf(GameRowModel("s1", "Super Mario World", "USA")))),
            )
        val found = SearchIndex.of(withHidden).find("mario")
        assertEquals(listOf("g2", "s1"), found.map { it.row.id })
        assertEquals(listOf("gba", "snes"), found.map { it.systemId })
        assertEquals("the hidden system is not in the column", listOf("gba"), withHidden.entries.drop(withHidden.firstSystemIndex).map { it.id })
    }

    @Test
    fun tenThousandGamesAreFoundQuickly() {
        val large = SearchIndex.of(FakeHomeContent.large(10_000))
        assertEquals(10_000, large.size)
        large.find("title 1") // warm up
        val started = System.nanoTime()
        val repeats = 20
        repeat(repeats) { large.find("nintendo title 12") }
        val perQueryMs = (System.nanoTime() - started) / 1_000_000.0 / repeats
        println("search over 10,000 games: %.2f ms per query on the JVM".format(perQueryMs))
        // A frame is 16 ms; matching runs off the main thread, this only guards against a slow rewrite.
        assertTrue("took $perQueryMs ms", perQueryMs < 16.0)
    }
}
