package com.plumemu.graph

import com.plumemu.achievements.hash.RaCannotHashReason
import com.plumemu.achievements.hash.RaHashResult
import com.plumemu.coredata.db.RaDao
import com.plumemu.coredata.db.RaMatchEntity
import com.plumemu.coredata.db.RaProgressEntity
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.graph.AchievementsHarness.Companion.GAME_LIST
import com.plumemu.graph.AchievementsHarness.Companion.GAME_PROGRESS
import com.plumemu.graph.AchievementsHarness.Companion.gameList
import com.plumemu.graph.AchievementsHarness.Companion.ok
import com.plumemu.graph.AchievementsHarness.Companion.progress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A file that needs a decoder is matched by its name: stored, counted as matched, shown as such, and re-checked once. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GraphAchievementsByNameTest {
    @get:org.junit.Rule val timeout = graphTestTimeout()

    @get:Rule
    val temp = TemporaryFolder()

    private val h by lazy { AchievementsHarness(temp.newFolder("ra")) }

    @After
    fun tearDown() = h.close()

    private fun needsDecoder(vararg files: String) = files.forEach { h.hashes[it] = RaHashResult.CannotHash(RaCannotHashReason.NeedsDecoder) }

    @Test
    fun aFileThatNeedsADecoderIsMatchedByNameStoredAndCountsAsMatched() = runBlocking {
        val zork = h.addGame("zork.7z", "Zork")
        h.signIn()
        needsDecoder("zork.7z")
        h.answers[GAME_LIST] = { ok(gameList(raId = 11)) }
        h.answers[GAME_PROGRESS] = { ok(progress(raId = 11, awarded = 2)) }

        val ready = h.achievements.progressFor(zork) as AchievementsState.Ready
        assertTrue("Info is told the match is by name", ready.matchedByName)
        assertEquals(2, ready.awardedSoftcore)

        val row = h.db.raDao().match(zork)!!
        assertEquals(RaMatchEntity.MATCHED_BY_NAME, row.status)
        assertEquals(11L, row.raGameId)
        assertNull(row.md5)
        assertEquals("progress follows the name match", 2, h.db.raDao().observeProgress(zork).first()?.awardedSoftcore)
        assertEquals(mapOf(zork to 11L), h.db.raDao().matchedRaGameIds(listOf(zork)))
        assertTrue(h.db.raDao().matchedRaGameIdList().contains(11L))

        // Stored: the file is not hashed again and the state is the same.
        assertTrue((h.achievements.progressFor(zork) as AchievementsState.Ready).matchedByName)
        assertEquals(1, h.hashed.count { it == "zork.7z" })
    }

    @Test
    fun aNameMatchedGameIsMasteredAndCountsForTheAfterGameCheck() = runBlocking {
        val zork = h.addGame("zork.7z", "Zork")
        h.signIn()
        h.db.raDao().saveMatch(RaMatchEntity(zork, RaMatchEntity.MATCHED_BY_NAME, 11, null, null, "k", 4, 1, 1))
        h.db.raDao().upsertProgress(listOf(RaProgressEntity(11, 3, 3, 3, RaProgressEntity.MASTERED, 1)))
        assertEquals(setOf(zork), h.db.raDao().masteredGameIds().first())

        var asked = 0
        h.answers[GAME_PROGRESS] = {
            asked++
            ok(progress(raId = 11, awarded = 3))
        }
        h.achievements.afterGame(zork)
        assertEquals("afterGame asked about the name-matched game", 1, asked)
    }

    @Test
    fun noGameWithTheNameKeepsCannotHash() = runBlocking {
        val mario = h.addGame("mario.7z", "Mario")
        h.signIn()
        needsDecoder("mario.7z")
        h.answers[GAME_LIST] = { ok(gameList(raId = 11)) }

        assertEquals(AchievementsState.CannotHash(RaCannotHashReason.NeedsDecoder), h.achievements.progressFor(mario))
        assertEquals(RaMatchEntity.CANNOT_HASH, h.db.raDao().match(mario)?.status)
    }

    @Test
    fun otherCannotHashReasonsAreNotNameMatched() = runBlocking {
        val zork = h.addGame("zork.sfc", "Zork")
        h.signIn()
        h.hashes["zork.sfc"] = RaHashResult.CannotHash(RaCannotHashReason.Empty)
        h.answers[GAME_LIST] = { ok(gameList(raId = 11)) }

        assertEquals(AchievementsState.CannotHash(RaCannotHashReason.Empty), h.achievements.progressFor(zork))
        assertEquals(0, h.server.requestCount)
    }

    @Test
    fun oldNeedsDecoderRowsAreCheckedAgainOnceAndOtherRowsStay() = runBlocking {
        val zork = h.addGame("zork.7z", "Zork")
        val empty = h.addGame("empty.sfc", "Empty")
        val later = h.addGame("later.7z", "Zork Later")
        h.signIn()
        needsDecoder("zork.7z", "later.7z")
        h.answers[GAME_LIST] = { ok(gameList(raId = 11)) }
        h.answers[GAME_PROGRESS] = { ok(progress(raId = 11, awarded = 1)) }
        h.marks.version = 0
        val dao: RaDao = h.db.raDao()
        dao.saveMatch(RaMatchEntity(zork, RaMatchEntity.CANNOT_HASH, null, null, "NeedsDecoder", "k", 4, 1, 1))
        dao.saveMatch(RaMatchEntity(empty, RaMatchEntity.CANNOT_HASH, null, null, "Empty", "k2", 0, 1, 1))

        assertTrue(h.achievements.progressFor(zork) is AchievementsState.Ready)
        assertEquals("the old needs-decoder row was redone", RaMatchEntity.MATCHED_BY_NAME, dao.match(zork)?.status)
        assertEquals("another reason stays", RaMatchEntity.CANNOT_HASH, dao.match(empty)?.status)
        assertEquals(GraphAchievements.MATCHER_VERSION, h.marks.version)

        // Once: a needs-decoder row written after the version was marked is not deleted again.
        dao.saveMatch(RaMatchEntity(later, RaMatchEntity.CANNOT_HASH, null, null, "NeedsDecoder", "k3", 4, 1, 1))
        h.signIn() // a new account object, so current() runs its start-up work again
        h.achievements.progressFor(zork)
        assertEquals(RaMatchEntity.CANNOT_HASH, dao.match(later)?.status)
        assertFalse(h.logLines.any { it.contains("Could not") })
    }

    @Test
    fun aRestoredLibraryChecksOldNeedsDecoderRowsAgain() = runBlocking {
        val zork = h.addGame("zork.7z", "Zork")
        h.signIn()
        needsDecoder("zork.7z")
        h.answers[GAME_LIST] = { ok(gameList(raId = 11)) }
        h.answers[GAME_PROGRESS] = { ok(progress(raId = 11, awarded = 1)) }
        // The version is already current; a restore brings back a row an older matcher wrote.
        h.db.raDao().saveMatch(RaMatchEntity(zork, RaMatchEntity.CANNOT_HASH, null, null, "NeedsDecoder", "k", 4, 1, 1))
        h.achievements.afterRestore()
        assertEquals(0, h.marks.version)

        assertTrue(h.achievements.progressFor(zork) is AchievementsState.Ready)
        assertEquals(RaMatchEntity.MATCHED_BY_NAME, h.db.raDao().match(zork)?.status)
        assertEquals(GraphAchievements.MATCHER_VERSION, h.marks.version)
    }

    @Test
    fun aRestoreWhileTheAccountIsInUseStillChecksAgain() = runBlocking {
        val zork = h.addGame("zork.7z", "Zork")
        h.signIn()
        needsDecoder("zork.7z")
        h.answers[GAME_LIST] = { ok(gameList(raId = 11)) }
        h.answers[GAME_PROGRESS] = { ok(progress(raId = 11, awarded = 1)) }
        // Info was open before the restore: the account's Achievements is already made and held.
        assertTrue(h.achievements.progressFor(zork) is AchievementsState.Ready)
        assertEquals(GraphAchievements.MATCHER_VERSION, h.marks.version)

        h.achievements.afterRestore()
        assertEquals(0, h.marks.version)
        h.achievements.progressFor(zork)
        // Only the re-check marks the version again, so this shows it ran without a restart.
        assertEquals(GraphAchievements.MATCHER_VERSION, h.marks.version)
    }
}
