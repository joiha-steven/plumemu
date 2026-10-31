package com.plumemu.game

import com.plumemu.coredata.db.AppKind
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.plumemu.R
import com.plumemu.coredata.db.NewAppGame
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.graph.AppGraph
import com.plumemu.graph.systemRulesOf
import com.plumemu.launcher.AppGameProblem
import com.plumemu.launcher.FailureReason
import com.plumemu.launcher.LaunchOutcome
import com.plumemu.launcher.apps.AppLaunchOutcome
import com.plumemu.launcher.choice.ChoiceSource
import com.plumemu.launcher.play.PlaySessionOutcome
import com.plumemu.library.matching.FolderMatch
import com.plumemu.library.matching.SystemMatcher
import com.plumemu.telemetry.LaunchFrom
import kotlinx.coroutines.runBlocking
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
 * Play on an installed app listed as a game, on the real graph with a fake starter: it
 * starts the app's package and notes the play like a pinned shortcut's; a gone app or a failed start
 * is its own message and leaves no play behind; a reserved row without its record never reaches the
 * emulator engine. Its menu has no emulator or file entries, and the `android` system never takes a folder.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class AppGameLaunchTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val graph = AppGraph(context, log = {})
    private val starts = mutableListOf<String>()
    private var answer: AppLaunchOutcome = AppLaunchOutcome.Started
    private var failure: RuntimeException? = null

    @After
    fun tearDown() {
        graph.database.close()
        context.deleteDatabase("plumemu.db")
    }

    private suspend fun addCeleste(): Long {
        val dao = graph.database.appGameDao()
        dao.sync(listOf(NewAppGame("com.celeste", "Celeste", "celeste", "android", "Android games")), now = 1)
        return dao.records().single().gameId
    }

    private fun actions() =
        AppGameActions(graph, context, appGameStarter = { packageName ->
            starts += packageName
            failure?.let { throw it }
            answer
        })

    private suspend fun play(id: Long) = actions().launch(id.toString(), LaunchFrom.List)

    private suspend fun pendingPlay() = graph.launching()!!.playTime.onResume()

    @Test
    fun playStartsTheAppsPackageAndNotesAPendingPlay() = runBlocking {
        val id = addCeleste()

        val outcome = play(id)

        assertEquals(LaunchOutcome.Started(id, "com.celeste", "com.celeste", ChoiceSource.InstalledApp, coreToConfirm = null), outcome)
        assertEquals(listOf("com.celeste"), starts)
        val play = pendingPlay()
        assertTrue("a session is pending, the package as its emulator: $play", play is PlaySessionOutcome.TooShort)
        play as PlaySessionOutcome.TooShort
        assertEquals(id, play.gameId)
        assertEquals("com.celeste", play.emulatorId)
        assertNull(play.coreToConfirm)
        assertNull("no emulator was chosen or stored", graph.database.emulatorChoiceDao().systemChoice("android"))
    }

    @Test
    fun anAppNoLongerInstalledSaysSoByItsNameAndLeavesNoPlay() = runBlocking {
        val id = addCeleste()
        answer = AppLaunchOutcome.Missing

        val outcome = play(id)

        assertEquals(LaunchOutcome.AppGameRefused(id, "Celeste", AppGameProblem.NotInstalled), outcome)
        assertEquals(PlaySessionOutcome.NothingPending, pendingPlay())
        val message = launchMessage(outcome, null)!!
        assertEquals(UiText.Res(R.string.app_error_missing_title, listOf("Celeste")), message.title)
        assertEquals(listOf<UiText>(UiText.Res(R.string.game_error_app_game_missing_body)), message.body)
    }

    @Test
    fun aRefusedOrThrowingStartIsAStartFailureWithNoPlay() = runBlocking {
        val id = addCeleste()
        answer = AppLaunchOutcome.Failed(SecurityException("refused"))
        assertEquals(LaunchOutcome.AppGameRefused(id, "Celeste", AppGameProblem.StartFailed), play(id))
        assertEquals(PlaySessionOutcome.NothingPending, pendingPlay())

        failure = IllegalStateException("system service died")
        val outcome = play(id)
        assertEquals(LaunchOutcome.AppGameRefused(id, "Celeste", AppGameProblem.StartFailed), outcome)
        assertEquals(PlaySessionOutcome.NothingPending, pendingPlay())
        assertEquals(UiText.Res(R.string.app_error_start_title, listOf("Celeste")), launchMessage(outcome, null)!!.title)
    }

    @Test
    fun aReservedRowWithoutItsRecordIsAnInternalFailureNeverTheEngine() = runBlocking {
        val id = addCeleste()
        graph.database.openHelper.writableDatabase.execSQL("DELETE FROM app_game")

        assertEquals(LaunchOutcome.Failed(id, FailureReason.Internal), play(id))
        assertTrue("Android was not asked", starts.isEmpty())
        assertEquals(PlaySessionOutcome.NothingPending, pendingPlay())
    }

    @Test
    fun aPortsAppIsFlaggedSoItsMenuOffersShowAsGame() = runBlocking {
        val dao = graph.database.appGameDao()
        dao.sync(listOf(NewAppGame("com.port", "Port", "port", "ports", "Ports")), now = 1)
        val id = dao.records().single().gameId

        val facts = actions().facts(id.toString())!!

        assertTrue(facts.isAppGame && facts.isPortApp)
        val entries = gameMenuEntries(facts).map { it.key }
        assertTrue(AppKindEntryId.ShowAsGame.name in entries && AppKindEntryId.ShowAsPort.name !in entries && AppKindEntryId.NotAGame.name in entries)
    }

    @Test
    fun anAppGamesRowOfNoRecordCannotBeAnswered() = runBlocking {
        val id = addCeleste()
        graph.database.openHelper.writableDatabase.execSQL("DELETE FROM app_game")

        val failure = runCatching { actions().setAppKind(id.toString(), AppKind.APP) }.exceptionOrNull()

        assertTrue("the caller must hear: $failure", failure is IllegalStateException)
    }

    @Test
    fun anAppGamesMenuHasNoPlayWithOtherVersionsNameOrFixBoxArt() = runBlocking {
        val id = addCeleste()

        val facts = actions().facts(id.toString())!!

        assertTrue(facts.isAppGame)
        assertFalse("an Android game is not a port app", facts.isPortApp)
        assertFalse(facts.canFixBoxArt)
        assertNull("no emulator to name", facts.emulator)
        assertNull(facts.versions)
        val entries = gameMenuEntries(facts.copy(canFixBoxArt = true)).map { it.key }
        assertEquals(
            listOf(
                GameMenuEntryId.Play, GameMenuEntryId.Favorite, GameMenuEntryId.Hide, GameMenuEntryId.Name,
                AppKindEntryId.ShowAsPort, AppKindEntryId.NotAGame, GameMenuEntryId.Info,
            ).map { it.name },
            entries,
        )
        assertEquals("Name… holds only Use my own name", listOf(NameEntryId.OwnName.name), nameMenuEntries(facts).map { it.key })
    }

    @Test
    fun thePersonsOwnNameForAnAppGameShowsAndSurvivesTheNextSync() = runBlocking {
        val id = addCeleste()

        val notice = graph.nameFixes.setOwnName(id, "My Celeste")

        assertNull("no box-art lookup for an app", notice)
        assertEquals("My Celeste", graph.database.gameDao().game(id)!!.title)
        val facts = actions().facts(id.toString())!!
        assertEquals("Back to automatic once a name is given", listOf(NameEntryId.OwnName, NameEntryId.BackToAutomatic).map { it.name }, nameMenuEntries(facts).map { it.key })

        graph.database.appGameDao().sync(listOf(NewAppGame("com.celeste", "Celeste", "celeste", "android", "Android games")), now = 2)
        assertEquals("My Celeste", graph.database.gameDao().game(id)!!.title)
    }

    @Test
    fun aFolderNamedAndroidOrAndroidGamesDoesNotMatchTheAppsSystem() = runBlocking {
        val rules = systemRulesOf(graph.catalog()!!)
        assertFalse(rules.any { it.id == "android" })
        val matcher = SystemMatcher(rules)
        for (folder in listOf("Android", "Android games", "android")) {
            val match = matcher.matchFolder(folder)
            assertFalse("$folder matched $match", match is FolderMatch.System && match.rule.id == "android")
        }
        assertTrue("ports still takes its folder", (matcher.matchFolder("ports") as? FolderMatch.System)?.rule?.id == "ports")
    }
}
