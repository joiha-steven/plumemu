package com.plumemu.achievements.match

import com.plumemu.achievements.api.RaGame
import com.plumemu.achievements.api.RaGameList
import com.plumemu.achievements.api.RaResult
import com.plumemu.achievements.api.Stale
import com.plumemu.achievements.hash.MemorySource
import com.plumemu.achievements.hash.RaCannotHashReason
import com.plumemu.achievements.hash.RaConsoles
import com.plumemu.achievements.hash.RaHashResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The name-match fallback for files whose format needs a decoder ([RaMatch.MatchedByName]). */
class RaMatcherByNameTest {
    private val rom = MemorySource("Crash Bandicoot (USA).chd", ByteArray(16))
    private val names = listOf("Crash Bandicoot", "Crash Bandicoot (USA)")
    private val asked = mutableListOf<Int>()

    private fun game(id: Int, title: String) = RaGame(id, title, RaConsoles.PLAYSTATION, 10, 100, null, listOf("ffffffffffffffffffffffffffffffff"))

    private fun matcher(
        reason: RaCannotHashReason = RaCannotHashReason.NeedsDecoder,
        answer: () -> RaResult<RaGameList>,
    ) = RaMatcher(
        gameList = {
            asked += it
            answer()
        },
        hash = { _, _ -> RaHashResult.CannotHash(reason, "zstd") },
    )

    private fun list(vararg games: RaGame, stale: Stale? = null) = RaResult.Ok(RaGameList(RaConsoles.PLAYSTATION, games.toList()), stale)

    @Test fun `one game with the file's name is matched by name`() = runBlocking {
        val result = matcher { list(game(1, "Spyro"), game(2, "Crash Bandicoot")) }.match("psx", rom, names)
        assertEquals(RaMatch.MatchedByName(2), result)
        assertEquals(listOf(RaConsoles.PLAYSTATION), asked)
    }

    @Test fun `names are compared ignoring case and punctuation`() = runBlocking {
        val result = matcher { list(game(2, "CRASH: Bandicoot!")) }.match("psx", rom, listOf("crash bandicoot"))
        assertEquals(RaMatch.MatchedByName(2), result)
    }

    @Test fun `no game with the name stays cannot hash`() = runBlocking {
        val result = matcher { list(game(1, "Spyro")) }.match("psx", rom, names)
        assertEquals(RaMatch.CannotHash(RaCannotHashReason.NeedsDecoder, "zstd"), result)
    }

    @Test fun `two different games with the name are not guessed`() = runBlocking {
        val result = matcher { list(game(1, "Crash Bandicoot"), game(2, "Crash Bandicoot")) }.match("psx", rom, names)
        assertEquals(RaMatch.CannotHash(RaCannotHashReason.NeedsDecoder, "zstd"), result)
    }

    @Test fun `one game id listed twice is still one game`() = runBlocking {
        val result = matcher { list(game(2, "Crash Bandicoot"), game(2, "Crash Bandicoot | Crash")) }.match("psx", rom, names)
        assertEquals(RaMatch.MatchedByName(2), result)
    }

    @Test fun `hacks homebrew prototypes unlicensed and subsets are ignored`() = runBlocking {
        val games = arrayOf(
            game(1, "~Hack~ Crash Bandicoot"),
            game(3, "~Homebrew~ Crash Bandicoot"),
            game(4, "~Prototype~ Crash Bandicoot"),
            game(5, "~Unlicensed~ Crash Bandicoot"),
            game(6, "Crash Bandicoot [Subset - Bonus]"),
        )
        assertEquals(RaMatch.CannotHash(RaCannotHashReason.NeedsDecoder, "zstd"), matcher { list(*games) }.match("psx", rom, names))
        // With a plain game among them, only that one is the match (and the hacks do not make it ambiguous).
        assertEquals(RaMatch.MatchedByName(9), matcher { list(*games, game(9, "Crash Bandicoot")) }.match("psx", rom, names))
    }

    @Test fun `each part of an alternatives title is a name`() = runBlocking {
        val result = matcher { list(game(7, "Rockman | Mega Man")) }.match("psx", rom, listOf("Mega Man"))
        assertEquals(RaMatch.MatchedByName(7), result)
        assertEquals(RaMatch.MatchedByName(7), matcher { list(game(7, "Rockman | Mega Man")) }.match("psx", rom, listOf("Rockman")))
    }

    @Test fun `other reasons do not match by name and ask for no list`() = runBlocking {
        for (reason in RaCannotHashReason.entries.filter { it != RaCannotHashReason.NeedsDecoder }) {
            val result = matcher(reason) { error("no list should be asked for") }.match("psx", rom, names)
            assertEquals(reason, (result as RaMatch.CannotHash).reason)
        }
        assertTrue(asked.isEmpty())
    }

    @Test fun `without names there is no name match and no list`() = runBlocking {
        val result = matcher { error("no list should be asked for") }.match("psx", rom)
        assertEquals(RaCannotHashReason.NeedsDecoder, (result as RaMatch.CannotHash).reason)
        assertTrue(asked.isEmpty())
    }

    @Test fun `a failed list is passed on`() = runBlocking {
        assertEquals(RaMatch.Failed(RaResult.NoConnection), matcher { RaResult.NoConnection }.match("psx", rom, names))
    }

    @Test fun `a stale list without a name match is the failure`() = runBlocking {
        val stale = Stale(savedAtMillis = 1, reason = RaResult.Busy(502))
        assertEquals(RaMatch.Failed(RaResult.Busy(502)), matcher { list(game(1, "Spyro"), stale = stale) }.match("psx", rom, names))
    }

    @Test fun `a stale list with one name match still matches`() = runBlocking {
        val stale = Stale(savedAtMillis = 1, reason = RaResult.NoConnection)
        assertEquals(RaMatch.MatchedByName(2), matcher { list(game(2, "Crash Bandicoot"), stale = stale) }.match("psx", rom, names))
    }

    @Test fun `the caller's name key is used on both sides`() = runBlocking {
        val matcher = RaMatcher({ list(game(2, "Crash Bandicoot, The")) }, nameKey = { it.take(5).lowercase() }) { _, _ ->
            RaHashResult.CannotHash(RaCannotHashReason.NeedsDecoder)
        }
        assertEquals(RaMatch.MatchedByName(2), matcher.match("psx", rom, listOf("CRASH something else")))
    }
}
