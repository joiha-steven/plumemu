package com.plumemu

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.graph.AppGraph
import com.plumemu.library.scan.AndroidFolderPermissions
import com.plumemu.library.scan.RomFolders
import java.io.ByteArrayOutputStream
import java.util.regex.Pattern
import org.junit.Assume.assumeTrue

/**
 * The on-device tests replace the app's library and settings, and log the window tree (with the
 * fixture's synthetic names) when a step fails: they run on an emulator only and are skipped, with
 * this message, anywhere else.
 */
internal fun assumeEmulator() {
    val emulator = Build.HARDWARE in EMULATOR_HARDWARE || Build.FINGERPRINT.startsWith("generic") ||
        Build.PRODUCT.contains("sdk")
    assumeTrue("Skipped: this test replaces the library and runs on an emulator only (hardware ${Build.HARDWARE})", emulator)
}

private val EMULATOR_HARDWARE = setOf("ranchu", "goldfish")

/**
 * First-run setup as a person goes through it, shared by the on-device tests: reset to a first
 * launch, and Android's own folder picker driven to `/sdcard/ROMs`.
 */
internal class SetupDriver(
    private val context: Context,
    private val device: UiDevice,
    private val graph: AppGraph,
) {
    /** Setup not done, no folders: what a first launch sees (games of forgotten folders go too). */
    suspend fun startAsANewPerson() {
        graph.settingsStore.update { it.copy(flags = SetupFlags()) }
        val folders = RomFolders(graph.database.scanDao(), AndroidFolderPermissions(context.contentResolver))
        graph.database.scanDao().folders().forEach { folders.removeFolder(it) }
    }

    /** Drives Android's picker (opened by setup) to `/sdcard/ROMs` and taps its Select button. */
    fun pickRomsFolderInThePicker() {
        try {
            check(device.wait(Until.hasObject(By.pkg(PICKER)), TIMEOUT)) { "picker did not open" }
            val storageName = By.text(Pattern.compile("(?i)(internal storage|${Pattern.quote(Build.MODEL)})"))
            // The drawer of roots opens by itself on a first pick; open it when it did not. On
            // Android 8+ the picker may already be inside ROMs (setup's initial folder).
            if (!device.hasObject(By.text(Pattern.compile("(?i)(select|use this folder|allow)")))) {
                if (!device.wait(Until.hasObject(storageName), SHORT_TIMEOUT)) device.findObject(By.desc("Show roots"))?.click()
                val storage = device.wait(Until.findObject(storageName), TIMEOUT) ?: error("no internal storage root in the drawer")
                storage.click()
                findRomsFolder().click()
            }
            device.waitForIdle()
            val select = device.wait(Until.findObject(By.text(Pattern.compile("(?i)(select|use this folder)"))), TIMEOUT) ?: error("no Select button")
            select.click()
            // Android 10+ asks once more before granting the folder.
            device.wait(Until.findObject(By.text(Pattern.compile("(?i)allow"))), SHORT_TIMEOUT)?.click()
            check(device.wait(Until.gone(By.pkg(PICKER)), TIMEOUT)) { "folder picker did not return" }
        } catch (e: Exception) {
            logWindow("Picker automation failed", e)
            throw e
        }
    }

    /**
     * Writes the window hierarchy to the log, for a UI step that did not find what it expected; in
     * pieces, because logcat cuts a line at about 4,000 bytes. The tree holds what the screen shows:
     * the fixture's synthetic titles and folder names (these tests run on an emulator only).
     */
    fun logWindow(what: String, e: Throwable) {
        val dump = ByteArrayOutputStream()
        device.dumpWindowHierarchy(dump)
        Log.e(TAG, "$what; window follows", e)
        dump.toString().chunked(LOG_PIECE).forEach { Log.e(TAG, it) }
    }

    /** Folders are listed by name; ROMs sits below the fold, and the list may redraw while scrolling. */
    private fun findRomsFolder(): UiObject2 {
        repeat(SCROLL_ATTEMPTS) {
            device.waitForIdle()
            device.findObject(By.text("ROMs"))?.let { return it }
            val list = device.wait(Until.findObject(By.res(PICKER, "list")), TIMEOUT) ?: error("no folder list")
            try {
                list.scrollUntil(Direction.DOWN, Until.findObject(By.text("ROMs")))?.let { return it }
            } catch (e: StaleObjectException) {
                Log.i(TAG, "Folder list redrawn while scrolling; trying again", e)
            }
        }
        error("no ROMs folder")
    }

    companion object {
        private const val TAG = "SetupDriver"
        const val PICKER = "com.android.documentsui"
        const val TIMEOUT = 15_000L

        /** The 10,000-file fixture scans in about 2.5 s on the API 23 emulator; generous for a cold one. */
        const val SCAN_TIMEOUT = 120_000L
        const val SHORT_TIMEOUT = 3_000L
        private const val SCROLL_ATTEMPTS = 3
        private const val LOG_PIECE = 3_000
    }
}
