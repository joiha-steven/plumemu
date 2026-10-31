package com.plumemu

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.DocumentsContract
import android.util.Log
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.plumemu.launcher.play.PlayTimeTracker
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The alpha from first launch to a game and back, on an emulator with the 10,000-file
 * fixture in `/sdcard/ROMs` and a fake DuckStation that reports to `com.plumemu` and stays open
 * past the 10 s minimum. Run it with the end-to-end script, which installs all of that on a fresh app.
 *
 * Setup through Android's own picker, the scan, the main screen; DuckStation chosen for
 * PlayStation; PlayStation reached with R2 from the list setup ends in, its first game played by touch (tap another row, tap it to focus, tap again); the fake
 * emulator must receive that game's document URI with a read grant and be able to read it; back in
 * Plumemu the session is counted and the game is first in Recent. Its own log lines carry ids only;
 * when a step fails the window tree, with the fixture's synthetic names, is logged.
 * Emulator only: skipped on any other device.
 */
@RunWith(AndroidJUnit4::class)
class EndToEndTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val graph = (context.applicationContext as PlumApplication).graph
    private val setup = SetupDriver(context, device, graph)
    private val reports = mutableListOf<Intent>()
    private val received = CountDownLatch(1)
    private var receiverRegistered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            reports += intent
            received.countDown()
        }
    }

    @Before
    fun setUp() {
        assumeEmulator()
        assertNotNull(
            "the fake DuckStation is not installed (scripts/e2e.sh installs it)",
            runCatching { context.packageManager.getPackageInfo(DUCKSTATION_PACKAGE, 0) }.getOrNull(),
        )
        registerReportReceiver()
    }

    @After
    fun tearDown() {
        // Not registered when the test was skipped (not an emulator) or its check failed first.
        if (receiverRegistered) context.unregisterReceiver(receiver)
    }

    @Test
    fun setupThenPlayAGameWithTheChosenEmulatorAndComeBack() = runBlocking {
        setup.startAsANewPerson()
        ActivityScenario.launch(MainActivity::class.java).use {
            try {
                goThroughSetup()
                val catalog = checkNotNull(graph.catalog()) { "no platform data" }
                val psx = checkNotNull(catalog.system(PSX)) { "no PlayStation in the platform data" }
                checkNotNull(graph.launching()) { "launching unavailable" }.choices.chooseForSystem(psx, DUCKSTATION_ID)

                val psxGames = graph.database.gameDao().observeSystemGames(PSX).first()
                val game = psxGames[0]
                val notPlayed = psxGames[1]
                val target = checkNotNull(graph.database.gameDao().launchTarget(game.id)) { "no launch target" }
                val expectedUri = DocumentsContract.buildDocumentUriUsingTree(Uri.parse(target.treeUri), target.documentId)

                playByTouch(psx.displayName, game.title, notPlayed.title)
                val report = awaitReport()
                assertEquals("$DUCKSTATION_PACKAGE/$DUCKSTATION_ACTIVITY", report.getStringExtra("component"))
                val extras = checkNotNull(report.getBundleExtra("extras")) { "report without extras" }
                // Compared, never printed: the URI holds the folder and file name.
                assertTrue("bootPath is the played game's document URI", extras.getString("bootPath") == expectedUri.toString())
                assertTrue("the read grant is set", report.getIntExtra("flags", 0) and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                val content = report.getStringExtra("content")
                assertTrue("the emulator read the game through the grant", content != null && !content.startsWith("error:"))

                // The fake emulator closes after HOLD; Plumemu comes back and counts the session.
                assertTrue("the fake emulator closed", device.wait(Until.gone(By.pkg(DUCKSTATION_PACKAGE)), HOLD_MS + TIMEOUT))
                assertTrue("Plumemu back in front", device.wait(Until.hasObject(By.pkg(context.packageName)), TIMEOUT))
                val play = awaitPlayRecorded(game.id)
                Log.i(TAG, "Session counted: game ${game.id}, ${play.totalSeconds} s")
                assertTrue("at least the minimum counted", play.totalSeconds >= PlayTimeTracker.MIN_SESSION_SECONDS)
                assertEquals("first in Recent", game.id, graph.database.gameDao().observeRecent(RECENT_LIMIT).first().first().id)

                // Recent on the main screen lists the game. Plumemu comes back on the games list;
                // Back slides the strip to the system column, where Recent is.
                val recentLabel = context.getString(R.string.home_entry_recent)
                val recent = By.text(recentLabel)
                if (!device.hasObject(recent)) device.pressBack()
                // The column keeps the focus on PlayStation, scrolled so that Recent, the first entry, is off screen:
                // D-pad up (touch on a row opens it, it cannot scroll) until Recent is composed.
                var ups = 0
                while (!device.hasObject(recent) && ups < MAX_COLUMN_MOVES) {
                    device.pressDPadUp()
                    ups++
                    device.waitForIdle()
                }
                Log.i(TAG, "Recent shown after $ups D-pad up presses")
                device.wait(Until.findObject(recent), TIMEOUT)?.click() ?: error("no Recent entry on the main screen")
                val row = device.wait(Until.findObject(By.text(game.title)), TIMEOUT) ?: error("the game not in Recent")
                // One tap focuses the row (a second would play): the header then names the list,
                // "Recent · 1 game", so this list is Recent and not PlayStation's.
                row.click()
                assertTrue("Recent's header", device.wait(Until.hasObject(By.textStartsWith("$recentLabel \u00b7 ")), TIMEOUT))
                assertTrue("a PlayStation game never played is not in Recent", !device.hasObject(By.text(notPlayed.title)))
            } catch (e: Throwable) {
                setup.logWindow("End-to-end step failed", e)
                throw e
            }
        }
    }

    /** First-run setup with the fixture folder, as LibraryOnDeviceTest checks it in detail. */
    private fun goThroughSetup() {
        check(device.wait(Until.hasObject(By.text(context.getString(R.string.setup_folder_title))), TIMEOUT)) { "setup did not show" }
        device.findObject(By.text(context.getString(R.string.setup_choose_folder))).click()
        val scanStarted = SystemClock.elapsedRealtime()
        setup.pickRomsFolderInThePicker()
        check(device.wait(Until.hasObject(By.textContains(" games · ")), SetupDriver.SCAN_TIMEOUT)) { "folder not counted" }
        device.findObject(By.text(context.getString(R.string.setup_continue))).click()
        // Since 0.0.2 setup may ask about ScreenScraper box art; this test declines it. When the
        // question never shows, the loop just waits for the ready screen as before.
        val readyBy = By.text(context.getString(R.string.setup_ready_title))
        val deadline = SystemClock.elapsedRealtime() + SetupDriver.SCAN_TIMEOUT
        while (!device.hasObject(readyBy) && SystemClock.elapsedRealtime() < deadline) {
            if (device.hasObject(By.text(context.getString(R.string.setup_box_art_question_title)))) {
                checkNotNull(device.findObject(By.text(context.getString(R.string.setup_box_art_not_now)))) { "box-art Not now button not found" }.click()
                // One tap only: wait for the question to go, so a second tap cannot land on the next screen.
                check(device.wait(Until.gone(By.text(context.getString(R.string.setup_box_art_question_title))), TIMEOUT)) { "box-art question still shown after Not now" }
            }
            SystemClock.sleep(POLL_MS)
        }
        check(device.hasObject(readyBy)) { "library not ready" }
        Log.i(TAG, "Picker to library ready: ${SystemClock.elapsedRealtime() - scanStarted} ms")
        // One tap, as a person gives it. The ready screen appears while naming still runs in the background (commit
        // 2f4ca4e7); a run once saw the first tap lost (screen unchanged for 30 s). That is the app's bug, not
        // the test's: never tap again here.
        device.waitForIdle()
        device.findObject(By.text(context.getString(R.string.setup_start_playing))).click()
        check(device.wait(Until.gone(readyBy), TIMEOUT)) { "Start playing did nothing" }
        device.wait(Until.findObject(By.text(context.getString(R.string.home_role_not_now))), TIMEOUT)?.click()
            ?: Log.i(TAG, "No home screen question: Plumemu is already the home app here")
        check(device.wait(Until.gone(By.text(context.getString(R.string.home_role_title))), TIMEOUT)) { "home question still shown" }
        // Setup ends inside a list on purpose (the Home-button change): focus lands on Recent's first game, or with
        // nothing played yet on the first system's first game. The test starts from there; it does not press Back to
        // the system column, because the column cannot be read reliably (see playByTouch).
        check(device.wait(Until.hasObject(By.text(context.getString(R.string.home_hint_play))), TIMEOUT)) { "setup did not end inside a list" }
    }

    /**
     * R2 (switch system by default; R1 jumps a letter, docs/conventions/input.md) from the list setup ended in until the list of [systemName] shows, then a tap on its game to focus it
     * and a second tap to play. The list's header ("PlayStation \u00b7 1,000 games") names the system shown; it is the
     * signal because Compose keeps the system column and the list both in the accessibility tree, and its rows below
     * the fold (the larger text shows about 7 systems, PlayStation is not one) either lack their name or keep stale
     * bounds while the column glides, so a system's name on a row says neither that it is on screen nor which pane
     * has the focus. A tap on a column row opens that list but cannot scroll the column, so touch reaches it only
     * for the visible systems.
     */
    private fun playByTouch(systemName: String, title: String, otherTitle: String) {
        val header = By.textStartsWith("$systemName \u00b7 ")
        var presses = 0
        while (!device.hasObject(header) && presses < MAX_SYSTEM_SWITCHES) {
            device.pressKeyCode(KeyEvent.KEYCODE_BUTTON_R2)
            presses++
            device.wait(Until.hasObject(header), SWITCH_WAIT_MS)
        }
        Log.i(TAG, "List of $systemName shown after $presses R2 presses")
        check(device.hasObject(header)) { "the list of $systemName never showed" }
        // The new system's games fade in; a row found mid-fade goes stale before the click.
        device.waitForIdle()
        // L2 / R2 leave the focus on the list's first game, which is [title], and a tap on the focused row plays. So
        // the focus is first moved off it by a tap on another row (which must not play), then tapped back (focus by
        // touch), then tapped once more to play.
        for (tapped in listOf(otherTitle, title, title)) {
            val row = device.wait(Until.findObject(By.text(tapped)), TIMEOUT) ?: error("game not in the list")
            row.click()
            device.waitForIdle()
        }
    }

    private suspend fun awaitPlayRecorded(gameId: Long): com.plumemu.coredata.db.PlayEntity {
        val deadline = SystemClock.elapsedRealtime() + HOLD_MS + TIMEOUT
        while (SystemClock.elapsedRealtime() < deadline) {
            graph.database.playDao().play(gameId)?.let { return it }
            delay(POLL_MS)
        }
        error("no play session counted")
    }

    // The report comes from the fake emulator, another app, so the receiver must be exported
    // (RECEIVER_EXPORTED exists from Android 13; before it receivers are exported by default).
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerReportReceiver() {
        val filter = IntentFilter(REPORT_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        receiverRegistered = true
    }

    private fun awaitReport(): Intent {
        assertTrue("the fake emulator never reported", received.await(TIMEOUT, TimeUnit.MILLISECONDS))
        return reports.single()
    }

    private companion object {
        const val TAG = "EndToEndTest"
        const val PSX = "psx"
        const val DUCKSTATION_ID = "duckstation"
        const val DUCKSTATION_PACKAGE = "com.github.stenzek.duckstation"
        const val DUCKSTATION_ACTIVITY = "com.github.stenzek.duckstation.EmulationActivity"
        const val REPORT_ACTION = "com.plumemu.launcher.test.FAKE_EMULATOR_REPORT"

        /** How long the end-to-end script builds the fake emulator to stay open: past the 10 s minimum. */
        const val HOLD_MS = 11_000L
        const val TIMEOUT = SetupDriver.TIMEOUT
        const val RECENT_LIMIT = 20
        const val POLL_MS = 200L
        const val MAX_SYSTEM_SWITCHES = 15
        const val MAX_COLUMN_MOVES = 15
        const val SWITCH_WAIT_MS = 1_000L
    }
}
