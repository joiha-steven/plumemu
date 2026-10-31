package com.plumemu.library.scan

import com.plumemu.coredata.db.GameScanFields
import com.plumemu.coredata.db.StoredGame
import org.junit.Assert.assertEquals
import org.junit.Test

class RenameMatcherTest {
    private fun row(
        id: Long,
        name: String,
        parent: String = "dir",
        size: Long = 0,
        sortName: String = name.substringBefore(' ').lowercase(),
        discGroup: String? = null,
        system: String = "psx",
    ) = StoredGame(id, GameScanFields(id.toString(), parent, "$parent/$name", name, size, 0, name, sortName, "", discGroup, 1, system, true))

    @Test
    fun sameNameAndSizeInAnotherDirectoryIsAMove() {
        val match = RenameMatcher.match(listOf(row(1, "Crash.cue", "PS1")), listOf(row(2, "Crash.cue", "psx")))
        assertEquals(listOf(1L to 2L), match.map { it.removed.id to it.inserted.id })
    }

    @Test
    fun anotherSystemIsNeverTheSameGame() {
        assertEquals(emptyList<Any>(), RenameMatcher.match(listOf(row(1, "Game.zip", system = "snes")), listOf(row(2, "Game.zip", system = "gba"))))
    }

    @Test
    fun ambiguousCandidatesMatchNothing() {
        val removed = listOf(row(1, "Game.cue", "a"), row(2, "Game.cue", "b"))
        assertEquals(emptyList<Any>(), RenameMatcher.match(removed, listOf(row(3, "Game.cue", "c"))))
    }

    @Test
    fun discGroupFindsTheNewPlaylist() {
        val disc = row(1, "MGS (USA) (Disc 1).chd", discGroup = "MGS (USA)")
        val playlist = row(2, "MGS (USA).m3u", discGroup = "MGS (USA)", sortName = "other")
        assertEquals(listOf(1L to 2L), RenameMatcher.match(listOf(disc), listOf(playlist)).map { it.removed.id to it.inserted.id })
    }

    @Test
    fun sameTitleInTheSameDirectoryIsARename() {
        val match = RenameMatcher.match(listOf(row(1, "Zelda (USA).sfc", sortName = "zelda")), listOf(row(2, "Zelda (USA) [!].sfc", sortName = "zelda")))
        assertEquals(1, match.size)
    }

    @Test
    fun sameSizeInTheSameDirectoryIsNotARename() {
        // Cartridge sizes are powers of two: two different games often have the same size.
        assertEquals(0, RenameMatcher.match(listOf(row(1, "a.sfc", size = 524_288)), listOf(row(2, "b.sfc", size = 524_288))).size)
        assertEquals(0, RenameMatcher.match(listOf(row(1, "a.sfc")), listOf(row(2, "b.sfc"))).size)
    }

    @Test
    fun aHackOfTheSameTitleIsNotARename() {
        val original = row(1, "Contra (USA).sfc", sortName = "contra", system = "snes")
        val hack = row(2, "Contra (USA) [h1].sfc", sortName = "contra", system = "snes")
        assertEquals(emptyList<Any>(), RenameMatcher.match(listOf(original), listOf(hack)))
    }
}
