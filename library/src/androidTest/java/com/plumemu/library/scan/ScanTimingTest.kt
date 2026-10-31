package com.plumemu.library.scan

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.identify.Identifier
import com.plumemu.identify.IdentifyLog
import com.plumemu.identify.android.AssetBundledPacks
import com.plumemu.identify.fingerprint.FingerprintReader
import com.plumemu.identify.pack.PackStore
import com.plumemu.library.matching.SystemRule
import org.json.JSONObject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.regex.Pattern

/**
 * First-scan timing through the real DocumentsContract walker (the app asks for no storage permission, so this is the only way in).
 * Runs only with the instrumentation argument `scanTiming=true`. Numbers go to logcat under tag
 * `PlumScanTiming`.
 *
 * The argument `scanFolder` names the folder under `/sdcard` the picker selects:
 * - `ROMs` (the default): 10,000 files pushed by a fixture script, scanned with
 *   the fixture's own rules ([firstScanAndRescanOfTenThousandFiles]).
 * - any other folder: a real library layout (a copy of
 *   a real library's names), scanned with the shipped `platforms.json` rules
 *   ([realLayoutWithNaming]).
 */
@RunWith(AndroidJUnit4::class)
class ScanTimingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val arguments = InstrumentationRegistry.getArguments()
    private val folderName: String = arguments.getString("scanFolder") ?: FIXTURE_FOLDER

    private val rules = listOf(
        SystemRule("nes", setOf("nes"), setOf("nes")),
        SystemRule("snes", setOf("snes"), setOf("sfc", "smc")),
        SystemRule("gb", setOf("gb"), setOf("gb")),
        SystemRule("gbc", setOf("gbc"), setOf("gbc")),
        SystemRule("gba", setOf("gba"), setOf("gba")),
        SystemRule("n64", setOf("n64"), setOf("z64", "n64")),
        SystemRule("megadrive", setOf("megadrive"), setOf("md", "gen")),
        SystemRule("mastersystem", setOf("mastersystem"), setOf("sms")),
        SystemRule("psx", setOf("psx"), setOf("chd", "cue", "bin", "m3u")),
        SystemRule("psp", setOf("psp"), setOf("iso", "cso")),
    )

    @Test
    fun firstScanAndRescanOfTenThousandFiles() = runBlocking {
        assumeTrue("pass -e scanTiming true", arguments.getString("scanTiming") == "true")
        assumeTrue("the fixture folder only", folderName == FIXTURE_FOLDER)
        val tree = pickFolder()
        val permissions = AndroidFolderPermissions(context.contentResolver)

        // Time until the first batch is in the database, on its own fresh database.
        val firstBatchMs = withFreshDatabase(DB_FIRST_BATCH) { db ->
            RomFolders(db.scanDao(), permissions).addPickedFolder(tree.toString(), "ROMs")
            val scanner = LibraryScanner(db, permissions, { ContentResolverDocumentTree(context.contentResolver, tree) }, rules)
            timed { scanner.scan().first { it is ScanEvent.Progress } }.second
        }

        withFreshDatabase(DB_MAIN) { db ->
            val added = RomFolders(db.scanDao(), permissions).addPickedFolder(tree.toString(), "ROMs")
            assertTrue("folder not recorded: $added", added is AddFolderResult.Added)
            val counting = CountingTree(ContentResolverDocumentTree(context.contentResolver, tree))
            val scanner = LibraryScanner(db, permissions, { counting }, rules)

            val (first, firstMs) = timed { scanned(scanner) }
            val firstLists = counting.lists
            val rows = db.scanDao().gamesInFolder((added as AddFolderResult.Added).folderId).size

            counting.reset()
            val (rescan, rescanMs) = timed { scanned(scanner) }
            val rescanLists = counting.lists
            val rescanStats = counting.stats

            shell("touch $ADDED_FILE")
            val (change, changeMs) = timed { scanned(scanner) }
            shell("rm $ADDED_FILE")


            log("device=${Build.MANUFACTURER} ${Build.MODEL} android=${Build.VERSION.RELEASE} api=${Build.VERSION.SDK_INT}")
            log(
                "first scan: files=${first.filesSeen} games=${first.gamesAdded} rows=$rows ms=$firstMs " +
                    "listQueries=$firstLists rowsPerSecond=${rows * 1000L / maxOf(firstMs, 1)}",
            )
            log("first batch in the database after ms=$firstBatchMs")
            log(
                "no-change rescan: ms=$rescanMs listQueries=$rescanLists statQueries=$rescanStats " +
                    "unchanged=${rescan.directoriesUnchanged}",
            )
            log("one-file rescan: ms=$changeMs listed=${change.directoriesListed} added=${change.gamesAdded}")

            assertEquals(0, rescanLists)
            assertEquals(1, change.directoriesListed)
            assertEquals(1, change.gamesAdded)
        }
        measureWithNaming(tree, permissions)
    }

    /**
     * The same 10,000 files with the identification pass after the walk, the real name
     * packs from the APK's assets unpacked from scratch. Time to the first list, the walk, the
     * pass, peak memory (Java heap in use and PSS, sampled at every scan event), and a rescan with
     * nothing changed, which must open no file.
     */
    private suspend fun measureWithNaming(tree: Uri, permissions: AndroidFolderPermissions): Unit = withFreshDatabase(DB_NAMED) { db ->
        RomFolders(db.scanDao(), permissions).addPickedFolder(tree.toString(), "ROMs")
        val packDir = File(context.noBackupFilesDir, "identify-packs-timing").apply { deleteRecursively() }
        val log = IdentifyLog { Log.i(TAG, "identify: $it") }
        val packs = PackStore(packDir, AssetBundledPacks(context.assets), log)
        val naming = GameNaming(packs, Identifier(packs, FingerprintReader(), log))
        val counting = CountingTree(ContentResolverDocumentTree(context.contentResolver, tree))
        val scanner = LibraryScanner(db, permissions, { counting }, rules, naming = naming)
        val memory = PeakMemory()

        val start = SystemClock.elapsedRealtime()
        var firstListMs = -1L
        var walkMs = -1L
        var named = 0
        var events = 0
        scanner.scan().collect { event ->
            // PSS reads /proc/self/smaps (milliseconds): sampled every tenth event, not every one.
            memory.sample(withPss = events++ % 10 == 0)
            val at = SystemClock.elapsedRealtime() - start
            when (event) {
                is ScanEvent.Progress -> if (firstListMs < 0) firstListMs = at
                is ScanEvent.FolderFinished -> walkMs = at
                is ScanEvent.Named -> named = event.gamesNamed
            }
        }
        val totalMs = SystemClock.elapsedRealtime() - start
        memory.sample(withPss = true)
        val opens = counting.opens

        counting.reset()
        val (_, rescanMs) = timed { scanner.scan().toList() }
        val rescanOpens = counting.opens

        log("with naming: first list ms=$firstListMs walk ms=$walkMs total ms=$totalMs pass ms=${totalMs - walkMs} named=$named opened=$opens")
        log("with naming: peak javaHeapKB=${memory.javaKb} peak pssKB=${memory.pssKb} packsOnDiskKB=${packDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } / 1024}")
        log("with naming: no-change rescan ms=$rescanMs opened=$rescanOpens listQueries=${counting.lists}")
        assertEquals(10_000, named)
        assertEquals(0, rescanOpens)
        check(packDir.deleteRecursively()) { "timing packs left behind" }
    }

    /**
     * A real library layout (2,161 files as empty files) with the shipped
     * rules and name packs, in this order, each on a fresh database:
     * 1. the walk alone (no naming), which also tells which systems the folder has;
     * 2. the first scan with naming and no pack unpacked yet (the very first scan on a device);
     * 3. unpacking the packs of those systems alone (`PackStore.prepare`), into a fresh directory;
     * 4. the first scan with naming and those packs already unpacked (a new store over them, as
     *    after an app restart): the number the speed ceiling is about;
     * 5. a rescan with nothing changed, which must open no file.
     */
    @Test
    fun realLayoutWithNaming() = runBlocking {
        assumeTrue("pass -e scanTiming true", arguments.getString("scanTiming") == "true")
        assumeTrue("pass -e scanFolder <folder>, not the fixture", folderName != FIXTURE_FOLDER)
        val rules = shippedRules()
        val tree = pickFolder()
        val permissions = AndroidFolderPermissions(context.contentResolver)
        log("device=${Build.MANUFACTURER} ${Build.MODEL} android=${Build.VERSION.RELEASE} api=${Build.VERSION.SDK_INT} folder=$folderName rules=${rules.size}")

        val systems = withFreshDatabase(DB_MAIN) { db ->
            val added = RomFolders(db.scanDao(), permissions).addPickedFolder(tree.toString(), folderName)
            check(added is AddFolderResult.Added) { "folder not recorded: $added" }
            val scanner = LibraryScanner(db, permissions, { ContentResolverDocumentTree(context.contentResolver, tree) }, rules)
            val (walk, walkMs) = timed { scanned(scanner) }
            val games = db.scanDao().gamesInFolder(added.folderId)
            val systems = games.mapTo(sortedSetOf<String>()) { it.systemId }
            log(
                "walk alone: ms=$walkMs files=${walk.filesSeen} rows=${games.size} junk=${walk.skippedJunk} " +
                    "unmatched=${walk.skippedUnmatched} systems=${systems.joinToString(",")}",
            )
            systems
        }

        val coldPacks = File(context.noBackupFilesDir, "identify-packs-cold").apply { deleteRecursively() }
        val cold = withFreshDatabase(DB_NAMED) { db -> namedScan(db, tree, permissions, rules, coldPacks) }
        log("packs not unpacked: ${cold.firstScan}")

        val packDir = File(context.noBackupFilesDir, "identify-packs-timing").apply { deleteRecursively() }
        val (ready, unpackMs) = timed { packStore(packDir).prepare(systems) }
        log("unpack: ms=$unpackMs systems=${systems.size} packsReady=${ready.size} packsOnDiskKB=${kilobytesIn(packDir)}")

        val warm = withFreshDatabase(DB_NAMED) { db -> namedScan(db, tree, permissions, rules, packDir) }
        log("packs unpacked: ${warm.firstScan}")
        log("packs unpacked: peak javaHeapKB=${warm.javaKb} peak pssKB=${warm.pssKb}")
        log("packs unpacked: no-change rescan ms=${warm.rescanMs} opened=${warm.rescanOpens} listQueries=${warm.rescanLists}")

        assertTrue("nothing named", warm.named > 0)
        assertEquals(0, warm.rescanOpens)
        check(coldPacks.deleteRecursively() && packDir.deleteRecursively()) { "timing packs left behind" }
    }

    /** One first scan with naming and the rescan after it; see [realLayoutWithNaming]. */
    private class NamedScan(
        val firstListMs: Long,
        val walkMs: Long,
        val totalMs: Long,
        val named: Int,
        val opens: Int,
        val javaKb: Long,
        val pssKb: Long,
        val rescanMs: Long,
        val rescanOpens: Int,
        val rescanLists: Int,
    ) {
        val firstScan: String
            get() = "first list ms=$firstListMs walk ms=$walkMs pass ms=${totalMs - walkMs} total ms=$totalMs named=$named opened=$opens"
    }

    private suspend fun namedScan(
        db: PlumDatabase,
        tree: Uri,
        permissions: AndroidFolderPermissions,
        rules: List<SystemRule>,
        packDir: File,
    ): NamedScan {
        RomFolders(db.scanDao(), permissions).addPickedFolder(tree.toString(), folderName)
        val packs = packStore(packDir)
        val naming = GameNaming(packs, Identifier(packs, FingerprintReader(), identifyLog))
        val counting = CountingTree(ContentResolverDocumentTree(context.contentResolver, tree))
        val scanner = LibraryScanner(db, permissions, { counting }, rules, naming = naming)
        val memory = PeakMemory()

        val start = SystemClock.elapsedRealtime()
        var firstListMs = -1L
        var walkMs = -1L
        var named = 0
        var events = 0
        scanner.scan().collect { event ->
            memory.sample(withPss = events++ % 10 == 0)
            val at = SystemClock.elapsedRealtime() - start
            when (event) {
                is ScanEvent.Progress -> if (firstListMs < 0) firstListMs = at
                is ScanEvent.FolderFinished -> walkMs = at
                is ScanEvent.Named -> named = event.gamesNamed
            }
        }
        val totalMs = SystemClock.elapsedRealtime() - start
        memory.sample(withPss = true)
        val opens = counting.opens

        counting.reset()
        val (_, rescanMs) = timed { scanner.scan().toList() }
        return NamedScan(
            firstListMs, walkMs, totalMs, named, opens, memory.javaKb, memory.pssKb,
            rescanMs, counting.opens, counting.lists,
        )
    }

    private val identifyLog = IdentifyLog { Log.i(TAG, "identify: $it") }

    private fun packStore(directory: File) = PackStore(directory, AssetBundledPacks(context.assets), identifyLog)

    private fun kilobytesIn(directory: File) = directory.walkTopDown().filter { it.isFile }.sumOf { it.length() } / 1024

    /**
     * `platforms.json` as the app turns it into rules (`app` `AppGraph.systemRulesOf`), read from
     * this test APK's assets (library/build.gradle.kts adds the platforms assets to androidTest
     * only; library itself must not depend on platforms). Same parsing as the JVM corpus
     * harness's `RealData.systemRules`.
     */
    private fun shippedRules(): List<SystemRule> {
        val text = instrumentation.context.assets.open("platforms.json").use { it.readBytes().toString(Charsets.UTF_8) }
        val systems = JSONObject(text).getJSONArray("systems")
        return (0 until systems.length()).map { i ->
            val s = systems.getJSONObject(i)
            fun strings(key: String) = s.getJSONArray(key).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
            SystemRule(
                s.getString("id"), strings("folders"), strings("extensions"),
                setOf(s.getString("name"), s.getString("shortName"), s.getString("displayName")), strings("emulators"),
            )
        }
    }

    /** Highest Java heap in use and PSS seen; PSS includes the mapped pack pages a lookup touched. */
    private class PeakMemory {
        var javaKb = 0L
        var pssKb = 0L

        fun sample(withPss: Boolean) {
            val runtime = Runtime.getRuntime()
            javaKb = maxOf(javaKb, (runtime.totalMemory() - runtime.freeMemory()) / 1024)
            if (withPss) pssKb = maxOf(pssKb, Debug.getPss())
        }
    }

    private suspend fun scanned(scanner: LibraryScanner): ScanStats {
        val outcome = scanner.scan().toList().filterIsInstance<ScanEvent.FolderFinished>().single().outcome
        check(outcome is FolderOutcome.Scanned) { "scan failed: $outcome" }
        return outcome.stats
    }

    private inline fun <T> timed(block: () -> T): Pair<T, Long> {
        val start = SystemClock.elapsedRealtime()
        val value = block()
        return value to SystemClock.elapsedRealtime() - start
    }

    private inline fun <T> withFreshDatabase(name: String, block: (PlumDatabase) -> T): T {
        context.deleteDatabase(name)
        val db = Room.databaseBuilder(context, PlumDatabase::class.java, name).build()
        try {
            return block(db)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    /** Drives Android's folder picker to `/sdcard/<scanFolder>` and returns the granted tree. */
    private fun pickFolder(): Uri {
        TreePickerActivity.reset()
        context.startActivity(Intent(context, TreePickerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            check(device.wait(Until.hasObject(By.pkg(PICKER)), TIMEOUT)) { "picker did not open" }
            val storageName = By.text(Pattern.compile("(?i)(internal storage|${Pattern.quote(Build.MODEL)})"))
            // The drawer of roots opens by itself on a first pick; open it when it did not.
            if (!device.wait(Until.hasObject(storageName), SHORT_TIMEOUT)) device.findObject(By.desc("Show roots"))?.click()
            val storage = device.wait(Until.findObject(storageName), TIMEOUT) ?: error("no internal storage root in the drawer")
            storage.click()
            findFolder().click()
            device.waitForIdle()
            val select = device.wait(Until.findObject(By.text(Pattern.compile("(?i)select"))), TIMEOUT)
                ?: error("no Select button")
            select.click()
            return TreePickerActivity.await(TIMEOUT)
        } catch (e: Exception) {
            val dump = ByteArrayOutputStream()
            device.dumpWindowHierarchy(dump)
            Log.e(TAG, "Picker automation failed; window:\n$dump", e)
            throw e
        }
    }

    /** Folders are listed by name; ours may sit below the fold, and the list may redraw while scrolling. */
    private fun findFolder(): UiObject2 {
        repeat(SCROLL_ATTEMPTS) {
            device.waitForIdle()
            device.findObject(By.text(folderName))?.let { return it }
            val list = device.wait(Until.findObject(By.res(PICKER, "list")), TIMEOUT) ?: error("no folder list")
            try {
                list.scrollUntil(Direction.DOWN, Until.findObject(By.text(folderName)))?.let { return it }
            } catch (e: StaleObjectException) {
                Log.i(TAG, "Folder list redrawn while scrolling; trying again", e)
            }
        }
        error("no $folderName folder")
    }

    private fun shell(command: String) {
        // The returned descriptor is the command's output; reading it to the end waits for the command.
        val output = instrumentation.uiAutomation.executeShellCommand(command)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
    }

    private fun log(line: String) = Log.i(TAG, line)

    private class CountingTree(private val real: DocumentTree) : DocumentTree {
        var lists = 0
        var stats = 0
        var opens = 0

        fun reset() {
            lists = 0
            stats = 0
            opens = 0
        }

        override val rootDocumentId get() = real.rootDocumentId

        override fun stat(documentId: String): TreeResult<DocumentEntry> {
            stats++
            return real.stat(documentId)
        }

        override fun listChildren(documentId: String): TreeResult<List<DocumentEntry>> {
            lists++
            return real.listChildren(documentId)
        }

        override fun open(documentId: String): TreeResult<OpenedFile> {
            opens++
            return real.open(documentId)
        }
    }

    private companion object {
        const val TAG = "PlumScanTiming"
        const val FIXTURE_FOLDER = "ROMs"
        const val PICKER = "com.android.documentsui"
        const val DB_MAIN = "scan-timing.db"
        const val DB_FIRST_BATCH = "scan-timing-first-batch.db"
        const val DB_NAMED = "scan-timing-named.db"
        // No spaces or quotes: on Android 6 executeShellCommand splits on spaces and runs no shell.
        const val ADDED_FILE = "/sdcard/ROMs/snes/ZzAddedGame.sfc"
        const val TIMEOUT = 15_000L
        const val SHORT_TIMEOUT = 3_000L
        const val SCROLL_ATTEMPTS = 3
    }
}
