package com.plumemu

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On an emulator with files in `/sdcard/ROMs`: goes through
 * first-run setup as a person would. Setup shows, "Choose folder" opens Android's own picker,
 * which is driven to `/sdcard/ROMs`; the folder's card fills in, Continue scans, the library is
 * ready, Start playing opens the main screen with systems under their display names, and the home
 * screen question is answered "Not now". Then a Home intent brings back the same MainActivity.
 *
 * Emulator only (skipped elsewhere). It changes the app's data on that emulator (it forgets earlier folders, adds this one, scans,
 * sets the setup flags). Run it by hand:
 * `adb shell am instrument -w -e class com.plumemu.LibraryOnDeviceTest com.plumemu.test/androidx.test.runner.AndroidJUnitRunner`.
 */
@RunWith(AndroidJUnit4::class)
class LibraryOnDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val graph = (context.applicationContext as PlumApplication).graph
    private val setup = SetupDriver(context, device, graph)

    @Before
    fun onlyOnAnEmulator() = assumeEmulator()

    @Test
    fun firstRunSetupPicksScansAndOpensTheLibrary() = runBlocking {
        setup.startAsANewPerson()
        ActivityScenario.launch(MainActivity::class.java).use {
            check(device.wait(Until.hasObject(By.text(context.getString(R.string.setup_folder_title))), TIMEOUT)) { "setup did not show" }
            device.findObject(By.text(context.getString(R.string.setup_choose_folder))).click()
            setup.pickRomsFolderInThePicker()

            // The card: where the folder is, then what the scan found in it.
            assertTrue("folder card", device.wait(Until.hasObject(By.text("Internal storage \u203a ROMs")), TIMEOUT))
            assertTrue("counted", device.wait(Until.hasObject(By.textContains(" games \u00b7 ")), SCAN_TIMEOUT))
            device.findObject(By.text(context.getString(R.string.setup_continue))).click()

            assertTrue("library ready", device.wait(Until.hasObject(By.text(context.getString(R.string.setup_ready_title))), SCAN_TIMEOUT))
            val systems = graph.database.gameDao().observeSystemsWithGames().first()
            Log.i(TAG, "Library: ${systems.size} systems, ${systems.sumOf { it.gameCount }} games")
            assertTrue("systems with games: $systems", systems.isNotEmpty())
            device.findObject(By.text(context.getString(R.string.setup_start_playing))).click()

            // The main screen under the platform data's display names, then the question, once.
            assertTrue("\"$SNES_NAME\" on the main screen", device.wait(Until.hasObject(By.text(SNES_NAME)), TIMEOUT))
            assertTrue("the fixture's GBA folder too", device.hasObject(By.text("Game Boy Advance")))
            val notNow = device.wait(Until.findObject(By.text(context.getString(R.string.home_role_not_now))), TIMEOUT)
            notNow?.click() ?: Log.i(TAG, "No home screen question: Plumemu is already the home app here")
            assertTrue("question gone", device.wait(Until.gone(By.text(context.getString(R.string.home_role_title))), TIMEOUT))

            val flags = graph.settings.filterNotNull().first { it.flags.homeRoleAsked }.flags
            assertTrue("setup completed: $flags", flags.setupCompleted)

            homeBringsBackTheSameMainScreen()
        }
    }

    /**
     * Plumemu answers HOME with `singleTask`: a Home intent while it runs must reach the existing
     * MainActivity (onNewIntent), not stack a fresh one.
     */
    private fun homeBringsBackTheSameMainScreen() {
        val before = resumedMainActivity()
        val home =
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setClassName(context, MainActivity::class.java.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(home)
        instrumentation.waitForIdleSync()
        assertTrue("main screen still there", device.wait(Until.hasObject(By.text(SNES_NAME)), TIMEOUT))
        assertSame("the same MainActivity, not a new one", before, resumedMainActivity())
    }

    private fun resumedMainActivity(): MainActivity {
        var found: MainActivity? = null
        instrumentation.runOnMainSync {
            found = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
        }
        return found ?: error("no resumed MainActivity")
    }

    private companion object {
        const val TAG = "LibraryOnDeviceTest"
        const val SNES_NAME = "Super Nintendo"
        const val TIMEOUT = SetupDriver.TIMEOUT
        const val SCAN_TIMEOUT = SetupDriver.SCAN_TIMEOUT
    }
}
