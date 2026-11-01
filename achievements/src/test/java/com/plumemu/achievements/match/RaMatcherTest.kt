package com.plumemu.achievements.match

import com.plumemu.achievements.api.RaGame
import com.plumemu.achievements.api.RaGameList
import com.plumemu.achievements.api.RaResult
import com.plumemu.achievements.api.Stale
import com.plumemu.achievements.hash.FailingSource
import com.plumemu.achievements.hash.MemorySource
import com.plumemu.achievements.hash.RaCannotHashReason
import com.plumemu.achievements.hash.RaConsoles
import com.plumemu.achievements.hash.RaHashResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.security.MessageDigest

class RaMatcherTest {
    private val rom = ByteArray(4096) { (it * 7).toByte() }
    private val romMd5 = MessageDigest.getInstance("MD5").digest(rom).joinToString("") { "%02x".format(it) }
    private val asked = mutableListOf<Int>()

    private fun game(id: Int, vararg hashes: String) = RaGame(id, "Game $id", RaConsoles.GAMEBOY, 10, 100, null, hashes.toList())

    private fun matcher(answer: (Int) -> RaResult<RaGameList>) = RaMatcher({ consoleId ->
        asked += consoleId
        answer(consoleId)
    })

    @Test fun `a hash the system's list holds is matched, with the real hasher`() = runBlocking {
        val matcher = matcher { RaResult.Ok(RaGameList(it, listOf(game(1, "ffffffffffffffffffffffffffffffff"), game(2, romMd5)))) }
        assertEquals(RaMatch.Matched(2, romMd5), matcher.match("gb", MemorySource("tetris.gb", rom)))
        assertEquals(listOf(RaConsoles.GAMEBOY), asked)
    }

    @Test fun `the list's hash is compared ignoring case`() = runBlocking {
        val matcher = matcher { RaResult.Ok(RaGameList(it, listOf(game(2, romMd5.uppercase())))) }
        assertEquals(RaMatch.Matched(2, romMd5), matcher.match("gb", MemorySource("tetris.gb", rom)))
    }

    @Test fun `systems sharing a console ask for that console's list`() = runBlocking {
        val matcher = matcher { RaResult.Ok(RaGameList(it, emptyList())) }
        matcher.match("ngpc", MemorySource("game.ngc", rom))
        matcher.match("supergrafx", MemorySource("game.pce", rom))
        assertEquals(listOf(RaConsoles.NEOGEO_POCKET, RaConsoles.PC_ENGINE), asked)
    }

    @Test fun `a hash no game has is no set`() = runBlocking {
        val matcher = matcher { RaResult.Ok(RaGameList(it, listOf(game(1, "ffffffffffffffffffffffffffffffff")))) }
        assertEquals(RaMatch.NoSet(romMd5), matcher.match("gb", MemorySource("hack.gb", rom)))
    }

    @Test fun `a system with no game at all is no set`() = runBlocking {
        val matcher = matcher { RaResult.Ok(RaGameList(it, emptyList())) }
        assertEquals(RaMatch.NoSet(romMd5), matcher.match("gb", MemorySource("hack.gb", rom)))
    }

    @Test fun `a system RetroAchievements does not have is not supported and reads nothing`() = runBlocking {
        val matcher = RaMatcher({ error("no list should be asked for") }) { _, _ -> error("nothing should be hashed") }
        assertEquals(RaMatch.NotSupported, matcher.match("switch", FailingSource("game.nsp")))
        assertEquals(RaMatch.NotSupported, matcher.match("no-such-system", FailingSource("game.bin")))
        assertTrue(asked.isEmpty())
    }

    @Test fun `a file that cannot be hashed asks for no list`() = runBlocking {
        val matcher = matcher { error("no list should be asked for") }
        val result = matcher.match("gb", MemorySource("empty.gb", ByteArray(0)))
        assertEquals(RaCannotHashReason.Empty, (result as RaMatch.CannotHash).reason)
        assertTrue(asked.isEmpty())
    }

    @Test fun `the hasher's reason and detail are passed on`() = runBlocking {
        val matcher = RaMatcher({ error("no list") }) { _, _ -> RaHashResult.CannotHash(RaCannotHashReason.NeedsDecoder, "7z") }
        assertEquals(RaMatch.CannotHash(RaCannotHashReason.NeedsDecoder, "7z"), matcher.match("psx", MemorySource("game.7z", rom)))
    }

    @Test fun `a file that cannot be read is unreadable, not cannot hash`() = runBlocking {
        val matcher = matcher { error("no list should be asked for") }
        val result = matcher.match("gb", FailingSource("gone.gb"))
        assertTrue(result.toString(), result is RaMatch.Unreadable && result.cause is IOException)
        assertTrue(asked.isEmpty())
    }

    @Test fun `a failed list is passed on so the caller can show it`() = runBlocking {
        for (failure in listOf(RaResult.WrongKey, RaResult.NoConnection, RaResult.RateLimited(60), RaResult.Busy(503), RaResult.BadAnswer("x"))) {
            assertEquals(RaMatch.Failed(failure), matcher { failure }.match("gb", MemorySource("tetris.gb", rom)))
        }
    }

    @Test fun `a stale list holding the hash still matches`() = runBlocking {
        val stale = Stale(savedAtMillis = 1, reason = RaResult.NoConnection)
        val matcher = matcher { RaResult.Ok(RaGameList(it, listOf(game(2, romMd5))), stale) }
        assertEquals(RaMatch.Matched(2, romMd5), matcher.match("gb", MemorySource("tetris.gb", rom)))
    }

    @Test fun `a stale list without the hash is the failure, never no set`() = runBlocking {
        val stale = Stale(savedAtMillis = 1, reason = RaResult.Busy(502))
        val matcher = matcher { RaResult.Ok(RaGameList(it, listOf(game(1, "ffffffffffffffffffffffffffffffff"))), stale) }
        assertEquals(RaMatch.Failed(RaResult.Busy(502)), matcher.match("gb", MemorySource("new-game.gb", rom)))
    }

    @Test fun `a stored no-set hash is matched once the list has it, without reading the file`() = runBlocking {
        var games = listOf(game(1, "ffffffffffffffffffffffffffffffff"))
        val matcher = RaMatcher({ RaResult.Ok(RaGameList(it, games)) }) { _, _ -> error("the file must not be hashed") }
        assertEquals(RaMatch.NoSet(romMd5), matcher.matchHash("gb", romMd5))
        games = games + game(7, romMd5)
        assertEquals(RaMatch.Matched(7, romMd5), matcher.matchHash("gb", romMd5.uppercase()))
    }

    @Test fun `a stored hash with a stale list without it is the failure`() = runBlocking {
        val stale = Stale(savedAtMillis = 1, reason = RaResult.NoConnection)
        val matcher = matcher { RaResult.Ok(RaGameList(it, emptyList()), stale) }
        assertEquals(RaMatch.Failed(RaResult.NoConnection), matcher.matchHash("gb", romMd5))
        assertEquals(RaMatch.Failed(RaResult.WrongKey), matcher { RaResult.WrongKey }.matchHash("gb", romMd5))
    }

    @Test fun `a stored hash of a system RetroAchievements does not have is not supported`() = runBlocking {
        assertEquals(RaMatch.NotSupported, matcher { error("no list") }.matchHash("switch", romMd5))
        assertTrue(asked.isEmpty())
    }
}
