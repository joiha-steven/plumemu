package com.plumemu.library.scrape

import com.plumemu.coredata.db.NewPinnedShortcut
import com.plumemu.library.scrape.ScrapeTestLibrary.Companion.boxFront
import com.plumemu.library.scrape.ScrapeTestLibrary.Companion.matched
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** DroidDeck's pinned shortcuts under `steam` are looked up by name like a `.steam` file. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShortcutScrapeTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var lib: ScrapeTestLibrary

    @Before
    fun setUp() {
        lib = ScrapeTestLibrary(temp.newFile("plumemu.db").also { it.delete() })
    }

    @After
    fun tearDown() = lib.close()

    private suspend fun pin(id: String, label: String, system: String): Long =
        lib.db.pinnedShortcutDao().add(
            NewPinnedShortcut(
                packageName = "com.droiddeck.launcher", shortcutId = id, userSerial = 0, label = label,
                sortName = label.lowercase(), systemId = system, systemName = system, addedAt = 1,
            ),
        ).gameId

    @Test
    fun aSteamShortcutIsAskedByItsTitleUnderSteamAndItsBoxArtIsStored() = runTest {
        val id = pin("game_1", "Hades", "steam")
        val source = lib.source()

        val request = source.pending(10).single()
        assertEquals("Hades", request.title)
        assertEquals("steam", request.systemId)
        source.deliver(request, matched(title = "Hades"), listOf(boxFront()))

        assertEquals(1, lib.db.scrapedGameDao().countWithBoxFront())
        assertEquals(emptyList<String>(), source.pending(10).map { it.title })
        assertEquals(id, (request.rom as DocumentRomSource).gameId)
    }

    @Test
    fun aDroidDeckShortcutIdGivesTheRequestItsSteamAppId() = runTest {
        pin("game:413150", "Stardew Valley", "steam")
        pin("game_1", "Hades", "steam")

        val ids = lib.source().pending(10).associate { it.title to it.steamAppId }

        assertEquals(mapOf("Stardew Valley" to 413150L, "Hades" to null), ids)
    }

    @Test
    fun onlyShortcutsOfTheNameOnlySystemAreAsked() = runTest {
        // core-data lists them in SQL (PinnedShortcutDao.NOT_SKIPPED_BY_BOX_ART); SystemIdsTest pins the scraper's set to it.
        val systems = listOf("steam", "windows", "shortcuts", "android", "snes")
        systems.forEach { pin("game_$it", "Game $it", it) }

        val asked = lib.source().pending(100).map { it.systemId }

        assertEquals(listOf("steam"), asked)
        assertEquals(1, lib.db.scrapedGameDao().countListedForBoxArt())
    }
}
