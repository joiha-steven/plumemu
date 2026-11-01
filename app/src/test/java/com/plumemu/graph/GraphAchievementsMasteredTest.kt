package com.plumemu.graph

import com.plumemu.coredata.db.RaMatchEntity
import com.plumemu.coredata.db.RaProgressEntity
import com.plumemu.coreui.testing.ReferenceShots
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The trophy's data: [GraphAchievements.masteredGames] is empty and asks nothing while signed out. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GraphAchievementsMasteredTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val h by lazy { AchievementsHarness(temp.newFolder("ra")) }

    @After
    fun tearDown() = h.close()

    @Test
    fun signedOutThereAreNoMarksAndNoDatabaseQuery() = runBlocking {
        h.addGame("zork.sfc", "Zork")
        assertEquals(emptySet<Long>(), h.achievements.masteredGames.first())
        assertEquals("no RetroAchievements database query", 0, h.daoReads.get())
        assertEquals("no request", 0, h.server.requestCount)
        assertEquals("no client", 0, h.clientsBuilt.get())
    }

    @Test
    fun signedInOnlyAMasteredGameIsMarked() = runBlocking {
        val zork = h.addGame("zork.sfc", "Zork")
        val plain = h.addGame("plain.sfc", "Plain")
        val dao = h.db.raDao()
        dao.saveMatch(RaMatchEntity(zork, RaMatchEntity.MATCHED, 11, "a".repeat(32), null, "zork.sfc", 4, 1, 1))
        dao.saveMatch(RaMatchEntity(plain, RaMatchEntity.MATCHED, 12, "b".repeat(32), null, "plain.sfc", 4, 1, 1))
        dao.upsertProgress(
            listOf(
                RaProgressEntity(11, 3, 3, 3, RaProgressEntity.MASTERED, 1),
                RaProgressEntity(12, 3, 1, 0, null, 1),
            ),
        )
        h.signIn()
        assertEquals(setOf(zork), h.achievements.masteredGames.first())
        assertTrue("the database was asked once signed in", h.daoReads.get() > 0)
    }
}
