package com.plumemu.launcher

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScanBatch
import com.plumemu.coredata.db.SystemEntity
import com.plumemu.launcher.choice.EmulatorChoices
import com.plumemu.launcher.detect.EmulatorDetector
import com.plumemu.launcher.play.PlayTimeTracker
import com.plumemu.library.storage.AndroidStorageRoots
import com.plumemu.platforms.PlatformCatalog
import com.plumemu.platforms.PlatformLoadResult
import com.plumemu.platforms.PlatformLoader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A real launch on a device or emulator. Needs the two fake emulators of
 * `src/androidTest/fake-emulator/build.sh` installed (see): one as
 * DuckStation (takes a URI in the `bootPath` extra), one as 64-bit RetroArch (takes a path).
 * Detection runs through the real PackageManager, so on Android 11+ it also proves the merged
 * `<queries>` make the emulators visible without QUERY_ALL_PACKAGES.
 */
@RunWith(AndroidJUnit4::class)
class RealLaunchTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: PlumDatabase
    private lateinit var catalog: PlatformCatalog
    private lateinit var host: ActivityScenario<HostActivity>
    private val reports = mutableListOf<Intent>()
    private val received = CountDownLatch(1)
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            reports += intent
            received.countDown()
        }
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, PlumDatabase::class.java).build()
        val text = context.assets.open("platforms.json").bufferedReader().use { it.readText() }
        catalog = (PlatformLoader.load(text, "assets/platforms.json") as PlatformLoadResult.Loaded).catalog
        registerReportReceiver()
        host = ActivityScenario.launch(HostActivity::class.java)
    }

    @After
    fun tearDown() {
        context.unregisterReceiver(receiver)
        host.close()
        db.close()
    }

    @Test
    fun aUriEmulatorReceivesTheDocumentAndCanReadItThroughTheGrant() = runBlocking {
        val detector = EmulatorDetector(context)
        val installed = detector.detect(catalog)
        assertTrue("fake DuckStation not visible: ${installed.packages}", installed.has(DUCKSTATION))

        val tree = DocumentsContract.buildTreeDocumentUri(FakeDocumentsProvider.AUTHORITY, FakeDocumentsProvider.ROOT).toString()
        val gameId = addGame("psx", tree, FakeDocumentsProvider.GAME)
        val launching = Launching.create(context, db, catalog)
        assertTrue(launching.detector.detect(catalog).has(DUCKSTATION))
        // Nothing starts before the person chooses: the first launch's answer, stored.
        launching.choices.chooseForSystem(checkNotNull(catalog.system("psx")), "duckstation")
        val launcher = launching.launcher

        val outcome = launcher.launch(gameId)
        assertTrue("outcome $outcome", outcome is LaunchOutcome.Started)
        val report = awaitReport()
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(
            DocumentsContract.buildTreeDocumentUri(FakeDocumentsProvider.AUTHORITY, FakeDocumentsProvider.ROOT),
            FakeDocumentsProvider.GAME,
        ).toString()
        assertEquals("$DUCKSTATION/$DUCKSTATION.EmulationActivity", report.getStringExtra("component"))
        assertNull(report.getStringExtra("action"))
        assertNull(report.getStringExtra("data"))
        val extras = report.extrasBundle()
        assertEquals(documentUri, extras.getString("bootPath"))
        assertEquals("false", extras.getString("resumeState"))
        assertEquals(FakeDocumentsProvider.GAME_CONTENT, report.getStringExtra("content"))
        val flags = report.getIntExtra("flags", 0)
        val wanted = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        assertEquals(wanted, flags and wanted)
    }

    @Test
    fun aPathEmulatorReceivesTheRealPath() = runBlocking {
        val detector = EmulatorDetector(context)
        assertTrue(detector.detect(catalog).has(RETROARCH))
        val tree = "content://com.android.externalstorage.documents/tree/primary%3AROMs"
        val gameId = addGame("snes", tree, "primary:ROMs/snes/Test Game.sfc")
        // Reading a file of internal storage needs a folder picked in the system picker, which a
        // test cannot do unattended; the file check is answered here, everything else is real.
        val choices = EmulatorChoices(db.emulatorChoiceDao(), catalog, detector::isLaunchable, AndroidLauncherLog, Dispatchers.IO)
        // Nothing starts before the person chooses: the first launch's answer, stored.
        choices.chooseForSystem(checkNotNull(catalog.system("snes")), "retroarch-snes9x")
        val launcher = GameLauncher(
            catalog, db.gameDao(),
            choices,
            { DocumentState.Present(null) },
            { _, _ -> error("only PS Vita emulators read a game file") },
            { context.startActivity(it) },
            PlayTimeTracker(context, db.playDao(), db.gameDao()),
            AndroidLauncherLog, Dispatchers.IO,
            storageRoots = AndroidStorageRoots(context),
        )
        val outcome = launcher.launch(gameId)
        assertTrue("outcome $outcome", outcome is LaunchOutcome.Started)
        val report = awaitReport()
        assertEquals("$RETROARCH/com.retroarch.browser.retroactivity.RetroActivityFuture", report.getStringExtra("component"))
        val extras = report.extrasBundle()
        assertEquals("/storage/emulated/0/ROMs/snes/Test Game.sfc", extras.getString("ROM"))
        assertEquals("/data/data/$RETROARCH/cores/snes9x_libretro_android.so", extras.getString("LIBRETRO"))
        assertEquals("/storage/emulated/0/Android/data/$RETROARCH/files/retroarch.cfg", extras.getString("CONFIGFILE"))
        assertNull(report.getStringExtra("content"))
        assertEquals(0, report.getIntExtra("flags", 0) and Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    // The report comes from the fake emulator, another app, so the receiver must be exported. Below
    // Android 13 there is no flag to say so (RECEIVER_EXPORTED is API 33) and receivers are exported
    // by default; lint asks for androidx.core's ContextCompat, which launcher does not depend on.
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerReportReceiver() {
        val filter = IntentFilter(REPORT_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    private fun awaitReport(): Intent {
        assertTrue("the fake emulator never reported", received.await(REPORT_TIMEOUT_S, TimeUnit.SECONDS))
        return reports.single()
    }

    private fun Intent.extrasBundle(): Bundle = checkNotNull(getBundleExtra("extras")) { "report without extras" }

    private suspend fun addGame(systemId: String, treeUri: String, documentId: String): Long {
        val folderId = db.scanDao().insertFolder(RomFolderEntity(treeUri = treeUri, displayName = "ROMs", addedAt = 0))
        val game = GameEntity(
            folderId = folderId, documentId = documentId, parentDocumentId = "root", relativePath = documentId,
            fileName = FakeDocumentsProvider.GAME_NAME, sizeBytes = 16, modifiedAt = 1, title = "Game", sortName = "game",
            regions = "", discGroup = null, discCount = 1, systemId = systemId, isPrimaryCopy = true,
        )
        db.scanDao().applyBatch(ScanBatch(folderId, listOf(SystemEntity(systemId, systemId)), listOf(game)))
        return db.scanDao().gamesInFolder(folderId).single().id
    }

    private companion object {
        const val REPORT_ACTION = "com.plumemu.launcher.test.FAKE_EMULATOR_REPORT"
        const val DUCKSTATION = "com.github.stenzek.duckstation"
        const val RETROARCH = "com.retroarch.aarch64"
        const val REPORT_TIMEOUT_S = 20L
    }
}
