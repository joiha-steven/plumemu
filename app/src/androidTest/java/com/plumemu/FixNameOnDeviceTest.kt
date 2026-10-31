package com.plumemu

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScanBatch
import com.plumemu.coredata.db.SystemEntity
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.game.TitleFinder
import com.plumemu.graph.choicesOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Fix name… on a device with the biggest name list Plumemu ships (arcade). Not part of any gate: it replaces the person's library
 * with one made-up arcade game. Emulator only (skipped elsewhere); run it on the low-end emulator:
 * `adb shell am instrument -w -e class com.plumemu.FixNameOnDeviceTest com.plumemu.test/androidx.test.runner.AndroidJUnitRunner`.
 * Results go to logcat, tag `FixNameMeasure`.
 */
@RunWith(AndroidJUnit4::class)
class FixNameOnDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val device = UiDevice.getInstance(instrumentation)
    private val graph = (context as PlumApplication).graph

    @Before
    fun onlyOnAnEmulator() = assumeEmulator()

    /** Building the arcade list and matching each prefix of a typed name, off the main thread as the panel does. */
    @Test
    fun arcadeListBuildsAndMatchesEachKeystroke() {
        val pack = checkNotNull(graph.naming.packs.packFor(ARCADE)) { "the arcade pack is bundled" }
        val built = System.nanoTime()
        val finder = TitleFinder(choicesOf(pack))
        val buildMs = (System.nanoTime() - built) / 1_000_000
        val typed = "street fighter ii"
        val times = (1..typed.length).map { n ->
            val started = System.nanoTime()
            finder.find(typed.take(n))
            (System.nanoTime() - started) / 1_000_000.0
        }.sorted()
        Log.i(TAG, "arcade titles=${finder.size} build=${buildMs}ms find median=${times[times.size / 2]}ms max=${times.last()}ms")
        assertTrue(finder.size > 0)
    }

    /** The real panel: open Fix name… from the game menu with the gamepad and type; frames from gfxinfo. */
    @Test
    fun typingInFixNameFrames() = runBlocking {
        graph.settingsStore.update { it.copy(flags = SetupFlags(setupCompleted = true, homeRoleAsked = true), library = it.library.copy(scanOnStartup = false)) }
        seedOneArcadeGame()
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        // The emulator may hold a measurement library too: pick the arcade system by touch first.
        assertTrue("main screen up", device.wait(Until.hasObject(By.text("Recent")), TIMEOUT))
        UiScrollable(UiSelector().scrollable(true)).scrollTextIntoView(ARCADE_NAME)
        device.findObject(By.text(ARCADE_NAME)).click()
        assertTrue("main screen shows the game", device.wait(Until.hasObject(By.text(GAME_TITLE)), TIMEOUT))
        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT)
        device.pressKeyCode(KeyEvent.KEYCODE_BUTTON_START)
        assertTrue("menu open", device.wait(Until.hasObject(By.text("Name…")), TIMEOUT))
        // Into the Name… sub-panel and Fix name… by touch (both work); the measure is the typing.
        device.findObject(By.text("Name…")).click()
        assertTrue("name menu open", device.wait(Until.hasObject(By.text("Fix name…")), TIMEOUT))
        device.findObject(By.text("Fix name…")).click()
        assertTrue("Fix name open", device.wait(Until.hasObject(By.text("Fix name")), TIMEOUT))
        instrumentation.waitForIdleSync()
        device.executeShellCommand("dumpsys gfxinfo com.plumemu reset")
        repeat(GAME_TITLE.length) { device.pressKeyCode(KeyEvent.KEYCODE_DEL) }
        for (c in "street fighter") device.pressKeyCode(if (c == ' ') KeyEvent.KEYCODE_SPACE else KeyEvent.KEYCODE_A + (c - 'a'))
        instrumentation.waitForIdleSync()
        val stats = device.executeShellCommand("dumpsys gfxinfo com.plumemu").lines().filter {
            it.contains("Total frames") || it.contains("Janky") || it.contains("percentile")
        }
        stats.forEach { Log.i(TAG, "typing: ${it.trim()}") }
        val field = device.findObject(By.clazz("android.widget.EditText"))?.text
        Log.i(TAG, "typing: field holds the typed text = ${field == "street fighter"}")
        assertTrue("the keys reached the field", field == "street fighter")
    }

    private suspend fun seedOneArcadeGame() {
        val scan = graph.database.scanDao()
        if (scan.folders().any { it.treeUri == MEASURE_TREE }) return
        val folder = scan.insertFolder(RomFolderEntity(treeUri = MEASURE_TREE, displayName = "measure", addedAt = 1))
        val game =
            GameEntity(
                folderId = folder, documentId = "measure:sfx", parentDocumentId = "measure", relativePath = "sfx.zip", fileName = "sfx.zip",
                sizeBytes = 1, modifiedAt = 1, title = GAME_TITLE, sortName = GAME_TITLE.lowercase(), regions = "", discGroup = null,
                discCount = 1, systemId = ARCADE, isPrimaryCopy = true,
            )
        scan.applyBatch(ScanBatch(folder, systems = listOf(SystemEntity(ARCADE, ARCADE)), newGames = listOf(game)))
    }

    private companion object {
        const val TAG = "FixNameMeasure"
        const val ARCADE = "arcade"
        const val GAME_TITLE = "Street Fightr"
        const val TIMEOUT = 15_000L
        const val ARCADE_NAME = "Arcade"
        const val MEASURE_TREE = "content://measure/tree/arcade"
    }
}
