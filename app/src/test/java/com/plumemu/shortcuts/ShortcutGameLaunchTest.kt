package com.plumemu.shortcuts

import com.plumemu.telemetry.LaunchFrom
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.plumemu.PinShortcutActivity
import com.plumemu.coredata.db.NewPinnedShortcut
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.game.AppGameActions
import com.plumemu.graph.AppGraph
import com.plumemu.launcher.FailureReason
import com.plumemu.launcher.LaunchOutcome
import com.plumemu.launcher.ShortcutProblem
import com.plumemu.launcher.choice.ChoiceSource
import com.plumemu.launcher.play.PlaySessionOutcome
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * On the real graph with a fake [ShortcutHost]: Play on a shortcut game asks Android to start it
 * and notes the play like an emulator's, with the app's package as the emulator; every
 * refusal is its own outcome and leaves no play behind; the engine is never asked for a Steam app or
 * a file. And Android finds the confirm screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class ShortcutGameLaunchTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val graph = AppGraph(context, log = {})
    private val host = FakeShortcutHost()

    @After
    fun tearDown() {
        graph.database.close()
        context.deleteDatabase("plumemu.db")
    }

    private suspend fun addBroforce(): Long =
        graph.database.pinnedShortcutDao().add(
            NewPinnedShortcut("app.gamenative", "game_274190", 0, "Broforce", "broforce", "steam", "Steam", addedAt = 1),
        ).gameId

    private suspend fun play(id: Long) = AppGameActions(graph, context, shortcutHost = host).launch(id.toString(), LaunchFrom.List)

    private suspend fun pendingPlay() = graph.launching()!!.playTime.onResume()

    private fun installGameNative() {
        val installed = android.content.pm.PackageInfo().apply {
            packageName = "app.gamenative"
            applicationInfo = android.content.pm.ApplicationInfo().apply { packageName = "app.gamenative"; nonLocalizedLabel = "GameNative" }
        }
        org.robolectric.Shadows.shadowOf(context.packageManager).installPackage(installed)
    }

    @Test
    fun playingAShortcutGameStartsItThroughAndroidAndNotesThePlay() = runBlocking {
        val id = addBroforce()

        val outcome = play(id)

        assertEquals(LaunchOutcome.Started(id, "app.gamenative", "app.gamenative", ChoiceSource.PinnedShortcut, coreToConfirm = null), outcome)
        assertEquals("started from the record: package, shortcut id, user", listOf(Triple("app.gamenative", "game_274190", 0L)), host.starts.toList())
        val play = pendingPlay()
        assertTrue("a session is pending, its app as the emulator: $play", play is PlaySessionOutcome.TooShort)
        play as PlaySessionOutcome.TooShort
        assertEquals(id, play.gameId)
        assertEquals("app.gamenative", play.emulatorId)
        assertNull("no RetroArch core: no core question", play.coreToConfirm)
        assertNull("no emulator was chosen or stored for it", graph.database.emulatorChoiceDao().systemChoice("steam"))
    }

    @Test
    fun notTheHomeAppSaysSoAndLeavesNoPlay() = runBlocking {
        val id = addBroforce()
        host.startAnswer = ShortcutStart.NotHomeApp

        assertEquals(LaunchOutcome.ShortcutRefused(id, null, ShortcutProblem.NotHomeApp), play(id))
        assertEquals(PlaySessionOutcome.NothingPending, pendingPlay())
    }

    @Test
    fun aDisabledOrRemovedShortcutSaysItIsNoLongerAvailableAndNamesItsApp() = runBlocking {
        installGameNative()
        val id = addBroforce()
        host.startAnswer = ShortcutStart.Unavailable

        assertEquals(LaunchOutcome.ShortcutRefused(id, "GameNative", ShortcutProblem.Unavailable), play(id))
        assertEquals(PlaySessionOutcome.NothingPending, pendingPlay())
    }

    @Test
    fun anotherFailureOrAThrowingHostIsAStartFailureWithNoPlay() = runBlocking {
        installGameNative()
        val id = addBroforce()
        host.startAnswer = ShortcutStart.Failed
        assertEquals(LaunchOutcome.ShortcutRefused(id, "GameNative", ShortcutProblem.StartFailed), play(id))
        assertEquals(PlaySessionOutcome.NothingPending, pendingPlay())

        host.failure = IllegalStateException("system service died")
        assertEquals(LaunchOutcome.ShortcutRefused(id, "GameNative", ShortcutProblem.StartFailed), play(id))
        assertEquals(PlaySessionOutcome.NothingPending, pendingPlay())
    }

    @Test
    fun aShortcutRowWithoutItsRecordIsAnInternalFailureNeverTheEngine() = runBlocking {
        val id = addBroforce()
        graph.database.openHelper.writableDatabase.execSQL("DELETE FROM pinned_shortcut")

        assertEquals(LaunchOutcome.Failed(id, FailureReason.Internal), play(id))
        assertTrue("Android was not asked", host.starts.isEmpty())
        assertEquals(PlaySessionOutcome.NothingPending, pendingPlay())
        assertNull("no Steam app was asked for or stored", graph.database.emulatorChoiceDao().systemChoice("steam"))
    }

    @Test
    fun aShortcutGamesFactsSayItIsAShortcutAndOfferNoFixBoxArt() = runBlocking {
        val id =
            graph.database.pinnedShortcutDao().add(
                NewPinnedShortcut("app.gamenative", "game_274190", 0, "Broforce", "broforce", "steam", "Steam", addedAt = 1),
            ).gameId

        val facts = AppGameActions(graph, context).facts(id.toString())!!

        assertTrue(facts.isShortcut)
        assertEquals(false, facts.canFixBoxArt)
    }

    @Test
    fun androidFindsTheConfirmScreenForAPinRequestAndItIsExported() {
        val intent = Intent("android.content.pm.action.CONFIRM_PIN_SHORTCUT").setPackage(context.packageName)
        val found = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
            ?: context.packageManager.queryIntentActivities(intent, 0).single().activityInfo
        assertEquals(PinShortcutActivity::class.java.name, found.name)
        assertTrue(found.exported)
        val widget = Intent("android.content.pm.action.CONFIRM_PIN_APPWIDGET").setPackage(context.packageName)
        assertEquals("widgets are not offered to Plumemu", 0, context.packageManager.queryIntentActivities(widget, 0).size)
    }
}
