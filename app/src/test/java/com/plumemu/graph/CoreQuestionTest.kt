package com.plumemu.graph

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScanBatch
import com.plumemu.coredata.db.SystemEntity
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.game.AppGameActions
import com.plumemu.launcher.choice.CoreToConfirm
import com.plumemu.launcher.play.PlaySessionOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The core check on the real graph and platform data: which
 * return asks "Did <game> start?", which one gets the closed-at-once message (never both), and what
 * Yes and No keep.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class CoreQuestionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val graph = AppGraph(context, log = {})
    private val actions = AppGameActions(graph, context)
    private val mesen = CoreToConfirm("com.retroarch", "mesen_libretro_android.so")

    @After
    fun tearDown() {
        graph.database.close()
        context.deleteDatabase("plumemu.db")
    }

    private fun addGame(): Long =
        runBlocking {
            val scan = graph.database.scanDao()
            val folder = scan.insertFolder(RomFolderEntity(treeUri = TREE, displayName = "ROMs", addedAt = 0))
            val row =
                GameEntity(
                    folderId = folder, documentId = "primary:ROMs/nes/Zork.nes", parentDocumentId = "primary:ROMs/nes",
                    relativePath = "nes/Zork.nes", fileName = "Zork.nes", sizeBytes = 1, modifiedAt = 1, title = "Zork Quest",
                    sortName = "zork quest", regions = "USA", discGroup = null, discCount = 1, systemId = "nes", isPrimaryCopy = true,
                )
            scan.applyBatch(ScanBatch(folder, listOf(SystemEntity("nes", "nes")), listOf(row)))
            scan.gamesInFolder(folder).single().id
        }

    @Test
    fun anUnconfirmedCoreBackInUnderTenSecondsIsAskedAboutNotToldClosedAtOnce() = runBlocking {
        val id = addGame()
        graph.afterPlay(PlaySessionOutcome.TooShort(id, 4, "retroarch-mesen", mesen))
        val question = graph.libraryNotices.coreQuestion.value!!
        assertEquals("Zork Quest", question.gameTitle)
        assertEquals("RetroArch: Mesen", question.emulatorName)
        assertEquals(mesen, question.core)
        assertNull("nothing was recorded, nothing to take back", question.added)
        assertNull("never both", graph.libraryNotices.closedAtOnce.value)
    }

    @Test
    fun aReturnWhoseActivityIsGoneMidWayStillAsksTheQuestion() = runBlocking {
        // The hung RetroArch brought the Activity back as a new one: the old one's scope is
        // cancelled while the return is being handled, and the pending session must not vanish unasked.
        val id = addGame()
        graph.launching()!!.playTime.onGameLaunched(id, "retroarch-mesen", mesen)
        val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        activityScope.launch(start = CoroutineStart.UNDISPATCHED) { graph.afterReturn() }
        activityScope.coroutineContext[kotlinx.coroutines.Job]!!.cancel()
        val question = withTimeout(5_000) { graph.libraryNotices.coreQuestion.filterNotNull().first() }
        assertEquals("Zork Quest", question.gameTitle)
    }

    @Test
    fun anEmulatorWithoutACoreToConfirmGetsTheClosedAtOnceMessageOnly() = runBlocking {
        val id = addGame()
        graph.afterPlay(PlaySessionOutcome.TooShort(id, 4, "nes-emu"))
        assertNull(graph.libraryNotices.coreQuestion.value)
        assertEquals("Zork Quest", graph.libraryNotices.closedAtOnce.value?.gameTitle)
    }

    @Test
    fun aRecordedPlayWithAnUnconfirmedCoreIsAskedAboutWithItsSession() = runBlocking {
        val id = addGame()
        val added = graph.database.playDao().recordSession(id, endedAt = 62_000, seconds = 62)!!
        graph.afterPlay(PlaySessionOutcome.Recorded(id, 62, "retroarch-mesen", mesen, added))
        assertEquals(added, graph.libraryNotices.coreQuestion.value?.added)
        assertNull(graph.libraryNotices.closedAtOnce.value)
    }

    @Test
    fun aRecordedPlayWithNoCoreToConfirmAsksNothing() = runBlocking {
        val id = addGame()
        graph.afterPlay(PlaySessionOutcome.Recorded(id, 62, "retroarch-mesen"))
        assertNull(graph.libraryNotices.coreQuestion.value)
        assertNull(graph.libraryNotices.closedAtOnce.value)
    }

    @Test
    fun yesKeepsTheCoreAsStartedForThatRetroArchBuildOnly() = runBlocking {
        val cores = graph.launching()!!.confirmedCores
        assertFalse(cores.isConfirmed(mesen))
        actions.confirmCore(mesen)
        assertTrue(cores.isConfirmed(mesen))
        assertFalse("another build's core is still asked about", cores.isConfirmed(mesen.copy(packageName = "com.retroarch.ra32")))
        assertFalse("another core too", cores.isConfirmed(mesen.copy(core = "fceumm_libretro_android.so")))
    }

    @Test
    fun noTakesBackThatOneSessionAndLeavesTheGameOutOfRecent() = runBlocking {
        val id = addGame()
        val play = graph.database.playDao()
        val added = play.recordSession(id, endedAt = 62_000, seconds = 62)!!
        assertTrue(actions.takeBackPlay(added))
        assertNull(play.play(id))
        assertTrue(play.sessions(id).isEmpty())
        assertFalse("a second No takes nothing more", actions.takeBackPlay(added))
    }

    @Test
    fun noClearsThePersonsChoicesOfTheFailedCoreOnly() = runBlocking {
        val id = addGame()
        val choices = graph.database.emulatorChoiceDao()
        choices.setPersonSystemChoice("nes", "retroarch-mesen")
        choices.setPersonGameChoice(id, "nes", "retroarch-mesen")
        actions.forgetFailedCore(id.toString(), "retroarch-mesen")
        assertNull("system choice of the failed core is gone", choices.systemChoice("nes"))
        assertNull("game choice of the failed core is gone", choices.gameChoice(id))
    }

    @Test
    fun noLeavesChoicesOfOtherEmulatorsAlone() = runBlocking {
        val id = addGame()
        val choices = graph.database.emulatorChoiceDao()
        choices.setPersonSystemChoice("nes", "retroarch-fceumm")
        choices.setPersonGameChoice(id, "nes", "nes-emu")
        actions.forgetFailedCore(id.toString(), "retroarch-mesen")
        assertEquals("retroarch-fceumm", choices.systemChoice("nes")?.emulatorId)
        assertEquals("nes-emu", choices.gameChoice(id)?.emulatorId)
    }

    @Test
    fun yesLeavesTheChoicesAsTheyAre() = runBlocking {
        val id = addGame()
        val choices = graph.database.emulatorChoiceDao()
        choices.setPersonSystemChoice("nes", "retroarch-mesen")
        choices.setPersonGameChoice(id, "nes", "retroarch-mesen")
        actions.confirmCore(mesen)
        assertEquals("retroarch-mesen", choices.systemChoice("nes")?.emulatorId)
        assertEquals("retroarch-mesen", choices.gameChoice(id)?.emulatorId)
    }

    private companion object {
        const val TREE = "content://com.android.externalstorage.documents/tree/primary%3AROMs"
    }
}
