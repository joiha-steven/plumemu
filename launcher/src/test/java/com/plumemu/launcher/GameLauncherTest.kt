package com.plumemu.launcher

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.launcher.choice.ChoiceSource
import com.plumemu.launcher.choice.EmulatorChoices
import com.plumemu.launcher.play.PlaySessionOutcome
import com.plumemu.launcher.play.PlayTimeTracker
import com.plumemu.launcher.play.SharedPreferencesSessionStore
import com.plumemu.launcher.play.SystemPlayClock
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameLauncherTest {
    private lateinit var db: PlumDatabase
    private val catalog = TestCatalogs.small
    private val installed = mutableSetOf<String>()
    private val documents = mutableMapOf<String, DocumentState>()
    private val absentVolumes = mutableSetOf<String>()
    private val started = mutableListOf<Intent>()
    private var startFailure: RuntimeException? = null
    private val log = RecordingLog()
    private lateinit var playTime: PlayTimeTracker
    private lateinit var launcher: GameLauncher
    private lateinit var choices: EmulatorChoices

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        val io = UnconfinedTestDispatcher()
        playTime = PlayTimeTracker(
            SharedPreferencesSessionStore(ApplicationProvider.getApplicationContext()),
            db.playDao(), db.gameDao(),
            SystemPlayClock(ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver), log, io,
        )
        launcher = GameLauncher(
            catalog,
            db.gameDao(),
            EmulatorChoices(db.emulatorChoiceDao(), catalog, { it.packageName in installed }, log, io).also { choices = it },
            { uri -> documents[uri] ?: DocumentState.Missing },
            { _, _ -> error("only PS Vita emulators read a game file") },
            { intent -> startFailure?.let { throw it } ?: started.add(intent) },
            playTime,
            log,
            io,
            { uri -> StorageVolumes.removableVolumeOf(uri) in absentVolumes },
            storageRoots = OWNER_STORAGE,
        )
    }

    @After
    fun tearDown() {
        // No line may carry a file name or a path.
        for (line in log.lines) {
            assertFalse(line, line.contains("Secret") || line.contains("/storage") || line.contains("ROMs"))
        }
        db.close()
    }

    /** A game of [systemId]; [systemChoice] is stored as the person's choice for SNES (null: nothing chosen yet, the launch asks). */
    private suspend fun game(
        tree: String = INTERNAL_TREE,
        systemId: String = "snes",
        present: Boolean = true,
        systemChoice: String? = "ra-snes9x",
    ): Long {
        val documentId = if (tree == INTERNAL_TREE) "primary:ROMs/$systemId/Secret Game.sfc" else "usb:$systemId/Secret Game.sfc"
        val id = db.addGame(systemId, documentId, tree)
        if (systemId == "snes" && systemChoice != null) db.emulatorChoiceDao().setPersonSystemChoice("snes", systemChoice)
        if (present) documents[documentUri(tree, documentId)] = DocumentState.Present("application/octet-stream")
        return id
    }

    private fun documentUri(tree: String, documentId: String): String =
        android.provider.DocumentsContract.buildDocumentUriUsingTree(android.net.Uri.parse(tree), documentId).toString()

    @Test
    fun aPathEmulatorStartsWithTheRealPathAndThePlayTimeIsNoted() = runTest {
        installed += "com.ra"
        val id = game()
        val outcome = launcher.launch(id)
        assertEquals(LaunchOutcome.Started(id, "ra-snes9x", "com.ra", ChoiceSource.PersonForSystem, "snes9x_libretro_android.so"), outcome)
        val intent = started.single()
        assertEquals("/storage/emulated/0/ROMs/snes/Secret Game.sfc", intent.getStringExtra("ROM"))
        assertEquals("/data/data/com.ra/cores/snes9x_libretro_android.so", intent.getStringExtra("LIBRETRO"))
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertTrue(playTime.onResume() is PlaySessionOutcome.TooShort)
        assertTrue(log.lines.any { it.startsWith("I launch: game $id started with ra-snes9x") })
    }

    @Test
    fun aUriEmulatorGetsTheDocumentUriWithAReadGrant() = runTest {
        installed += "com.snes9x"
        val id = game(USB_TREE, systemChoice = "snes9x")
        assertTrue(launcher.launch(id) is LaunchOutcome.Started)
        val intent = started.single()
        val uri = documentUri(USB_TREE, "usb:snes/Secret Game.sfc")
        assertEquals(uri, intent.getStringExtra("uri"))
        assertEquals(uri, intent.clipData?.getItemAt(0)?.uri.toString())
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun aPathEmulatorOnAFolderWithoutPathNeedsAFilePath() = runTest {
        installed += "com.ra"
        val id = game(USB_TREE)
        assertEquals(LaunchOutcome.NeedsFilePath(id, EmulatorOffer("ra-snes9x", "RetroArch: Snes9x", "com.ra")), launcher.launch(id))
        assertTrue(started.isEmpty())
    }

    @Test
    fun aMissingFileIsNamedForTheScreenAndNothingStarts() = runTest {
        installed += "com.ra"
        val id = game(present = false)
        assertEquals(LaunchOutcome.FileMissing(id, "Secret Game.sfc", FileProblem.Gone), launcher.launch(id))
        assertTrue(started.isEmpty())
        assertEquals(PlaySessionOutcome.NothingPending, playTime.onResume())
    }

    @Test
    fun anUnreachableFolderIsAFileProblemToo() = runTest {
        installed += "com.ra"
        val id = game()
        documents.replaceAll { _, _ -> DocumentState.Unreachable(SecurityException("revoked")) }
        assertEquals(LaunchOutcome.FileMissing(id, "Secret Game.sfc", FileProblem.FolderUnreachable), launcher.launch(id))
        assertTrue(started.isEmpty())
    }

    @Test
    fun aGameOnATakenOutSdCardSaysTheStorageIsNotFound() = runTest {
        installed += "com.snes9x"
        absentVolumes += "1A2B-3C4D"
        val id = game(SD_TREE, present = false, systemChoice = "snes9x")
        assertEquals(LaunchOutcome.FileMissing(id, "Secret Game.sfc", FileProblem.StorageNotFound), launcher.launch(id))
        documents.clear()
        documents[documentUri(SD_TREE, "usb:snes/Secret Game.sfc")] = DocumentState.Unreachable(java.io.FileNotFoundException("no root"))
        assertEquals("a provider that errors says the same", LaunchOutcome.FileMissing(id, "Secret Game.sfc", FileProblem.StorageNotFound), launcher.launch(id))
        assertTrue(started.isEmpty())
    }

    @Test
    fun aFileMissingOnAMountedCardKeepsTheFileGoneProblem() = runTest {
        installed += "com.snes9x"
        val id = game(SD_TREE, present = false, systemChoice = "snes9x")
        assertEquals(LaunchOutcome.FileMissing(id, "Secret Game.sfc", FileProblem.Gone), launcher.launch(id))
    }

    @Test
    fun onlyExplicitCardOutStatesCountAsAbsentAndAFailedAskCountsAsPresent() {
        fun absent(state: String) = AndroidStorageVolumes({ state }, log).isAbsent(SD_TREE)
        for (out in listOf("removed", "unmounted", "bad_removal", "ejecting")) assertTrue(out, absent(out))
        for (inState in listOf("mounted", "mounted_ro", "unknown", "unmountable", "nofs", "checking", "shared")) assertFalse(inState, absent(inState))
        assertFalse("internal storage is never asked", AndroidStorageVolumes({ error("asked") }, log).isAbsent(INTERNAL_TREE))
        assertFalse(AndroidStorageVolumes({ throw IllegalStateException("binder") }, log).isAbsent(SD_TREE))
        assertTrue(log.lines.toString(), log.lines.any { "IllegalStateException" in it })
    }

    @Test
    fun onlyAnExternalStorageTreeOfARemovableVolumeNamesAVolume() {
        assertEquals("1A2B-3C4D", StorageVolumes.removableVolumeOf(SD_TREE))
        assertEquals(null, StorageVolumes.removableVolumeOf(INTERNAL_TREE))
        assertEquals(null, StorageVolumes.removableVolumeOf(USB_TREE))
        assertEquals("1A2B-3C4D", StorageVolumes.removableVolumeOf(documentUri(SD_TREE, "1A2B-3C4D:ROMs/a.sfc")))
    }

    @Test
    fun anUninstalledChosenEmulatorIsReportedWithItsStorePackage() = runTest {
        val id = game()
        db.emulatorChoiceDao().setPersonSystemChoice("snes", "snes9x")
        installed += "com.ra"
        assertEquals(
            LaunchOutcome.EmulatorMissing(id, EmulatorOffer("snes9x", "Snes9x EX+", "com.snes9x"), ChoiceSource.PersonForSystem),
            launcher.launch(id),
        )
        assertTrue(started.isEmpty())
    }

    @Test
    fun theFirstLaunchWithNothingChosenAsksAmongTheInstalledOnesRecommendedFirst() = runTest {
        installed += setOf("com.risky", "com.snes9x", "com.ra")
        val id = game(systemChoice = null)
        assertEquals(
            LaunchOutcome.EmulatorNotChosen(
                id, "snes", "Super Nintendo",
                listOf(
                    EmulatorOffer("ra-snes9x", "RetroArch: Snes9x", "com.ra"),
                    EmulatorOffer("risky", "Risky", "com.risky", isLegalRisk = true),
                    EmulatorOffer("ra-bsnes", "RetroArch: bsnes", "com.ra"),
                    EmulatorOffer("snes9x", "Snes9x EX+", "com.snes9x"),
                ),
                "ra-snes9x",
            ),
            launcher.launch(id),
        )
        assertTrue(started.isEmpty())
        assertEquals(PlaySessionOutcome.NothingPending, playTime.onResume())
        assertTrue(log.lines.toString(), log.lines.any { it.startsWith("I launch: game $id: no emulator chosen for snes") })
    }

    @Test
    fun onlyALegalRiskEmulatorInstalledIsAskedAboutWithNoRecommendation() = runTest {
        installed += "com.risky"
        val id = game(systemChoice = null)
        assertEquals(
            LaunchOutcome.EmulatorNotChosen(id, "snes", "Super Nintendo", listOf(EmulatorOffer("risky", "Risky", "com.risky", isLegalRisk = true)), null),
            launcher.launch(id),
        )
        assertTrue(started.isEmpty())
    }

    @Test
    fun theAnswerIsKeptForTheSystemAndTheNextLaunchStartsStraightAway() = runTest {
        installed += setOf("com.snes9x", "com.ra")
        val id = game(systemChoice = null)
        val other = db.addGame("snes", "primary:ROMs/snes/Other.sfc")
        documents[documentUri(INTERNAL_TREE, "primary:ROMs/snes/Other.sfc")] = DocumentState.Present("application/octet-stream")
        assertTrue(launcher.launch(id) is LaunchOutcome.EmulatorNotChosen)
        choices.chooseForSystem(checkNotNull(catalog.system("snes")), "ra-bsnes")
        assertEquals(
            LaunchOutcome.Started(other, "ra-bsnes", "com.ra", ChoiceSource.PersonForSystem, "bsnes_libretro_android.so"),
            launcher.launch(other),
        )
        // Changed later (Settings › Emulators): the new one is used from the next launch.
        choices.chooseForSystem(checkNotNull(catalog.system("snes")), "ra-snes9x")
        assertEquals("ra-snes9x", (launcher.launch(id) as LaunchOutcome.Started).emulatorId)
    }

    @Test
    fun noInstalledEmulatorOffersTheCandidatesWithoutLegalRisk() = runTest {
        val id = game(systemChoice = null)
        assertEquals(
            LaunchOutcome.NoEmulatorForSystem(
                id, "snes",
                listOf(
                    EmulatorOffer("ra-snes9x", "RetroArch: Snes9x", "com.ra"),
                    EmulatorOffer("ra-bsnes", "RetroArch: bsnes", "com.ra"),
                    EmulatorOffer("snes9x", "Snes9x EX+", "com.snes9x"),
                ),
            ),
            launcher.launch(id),
        )
    }

    @Test
    fun aChosenRetroArchCoreIsTheOneThatStarts() = runTest {
        installed += "com.ra"
        val id = game()
        db.emulatorChoiceDao().setPersonGameChoice(id, "snes", "ra-bsnes")
        val outcome = launcher.launch(id)
        assertEquals(LaunchOutcome.Started(id, "ra-bsnes", "com.ra", ChoiceSource.PersonForGame, "bsnes_libretro_android.so"), outcome)
        assertEquals("/data/data/com.ra/cores/bsnes_libretro_android.so", started.single().getStringExtra("LIBRETRO"))
    }

    @Test
    fun aChosenEmulatorTheDataNoLongerListsIsMissingWithoutAPackage() = runTest {
        installed += "com.ra"
        val id = game()
        db.emulatorChoiceDao().setPersonSystemChoice("snes", "removed-by-a-data-update")
        assertEquals(
            LaunchOutcome.EmulatorMissing(id, EmulatorOffer("removed-by-a-data-update", null, null), ChoiceSource.PersonForSystem),
            launcher.launch(id),
        )
    }

    @Test
    fun aGameChoiceTheDataNoLongerListsForItsSystemIsPassedOver() = runTest {
        installed += "com.ra"
        val id = game()
        db.emulatorChoiceDao().setPersonGameChoice(id, "snes", "removed-by-a-data-update")
        // The game follows its system's choice.
        assertEquals("ra-snes9x", (launcher.launch(id) as LaunchOutcome.Started).emulatorId)
    }

    @Test
    fun aGameNoLongerInTheLibraryFails() = runTest {
        assertEquals(LaunchOutcome.Failed(42, FailureReason.GameNotInLibrary), launcher.launch(42))
    }

    @Test
    fun aSystemTheDataNoLongerKnowsFails() = runTest {
        val id = game(systemId = "gone")
        assertEquals(LaunchOutcome.Failed(id, FailureReason.UnknownSystem), launcher.launch(id))
    }

    @Test
    fun anActivityAndroidCannotFindFailsAndCountsNoPlayTime() = runTest {
        installed += "com.ra"
        val id = game()
        val error = ActivityNotFoundException("no activity")
        startFailure = error
        assertEquals(LaunchOutcome.Failed(id, FailureReason.ActivityNotFound, error), launcher.launch(id))
        assertEquals(PlaySessionOutcome.NothingPending, playTime.onResume())
    }

    @Test
    fun aDatabaseErrorBecomesFailedInsteadOfACrash() = runTest {
        installed += "com.ra"
        val id = game()
        db.openHelper.writableDatabase.execSQL("DROP TABLE play")
        db.openHelper.writableDatabase.execSQL("DROP TABLE media")
        db.openHelper.writableDatabase.execSQL("DROP TABLE emulator_choice")
        db.openHelper.writableDatabase.execSQL("DROP TABLE game")
        val outcome = launcher.launch(id)
        assertEquals(FailureReason.DatabaseError, (outcome as LaunchOutcome.Failed).reason)
        assertTrue(started.isEmpty())
    }

    @Test
    fun aClosedDatabaseBecomesFailedInsteadOfACrash() = runTest {
        installed += "com.ra"
        val id = game()
        db.close()
        assertEquals(FailureReason.DatabaseError, (launcher.launch(id) as LaunchOutcome.Failed).reason)
    }

    @Test
    fun aFailedWriteOfTheRecommendationStillAsks() = runTest {
        installed += "com.ra"
        val id = game(systemChoice = null)
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER full_disk BEFORE INSERT ON emulator_choice BEGIN SELECT RAISE(ABORT, 'disk full'); END",
        )
        assertTrue(launcher.launch(id) is LaunchOutcome.EmulatorNotChosen)
        assertTrue(log.lines.toString(), log.lines.any { it.startsWith("W ") && it.contains("default") })
    }

    @Test
    fun anyOtherErrorFromTheStartIsFailedAndCountsNoPlayTime() = runTest {
        installed += "com.ra"
        val id = game()
        startFailure = IllegalArgumentException("bad intent")
        val outcome = launcher.launch(id)
        assertEquals(FailureReason.StartFailed, (outcome as LaunchOutcome.Failed).reason)
        assertEquals(PlaySessionOutcome.NothingPending, playTime.onResume())
    }

    @Test
    fun aProviderMessageWithThePathNeverReachesTheLog() = runTest {
        installed += "com.ra"
        val id = game()
        val leak = SecurityException(
            "Permission Denial: reading com.android.externalstorage.ExternalStorageProvider uri " +
                "content://com.android.externalstorage.documents/tree/primary%3AROMs/document/primary%3AROMs%2Fsnes%2FSecret%20Game.sfc",
        )
        documents.replaceAll { _, _ -> DocumentState.Unreachable(leak) }
        assertEquals(LaunchOutcome.FileMissing(id, "Secret Game.sfc", FileProblem.FolderUnreachable), launcher.launch(id))
        // tearDown checks every line, error included, for the file name and the folder.
    }

    @Test
    fun aRefusedStartFails() = runTest {
        installed += "com.ra"
        val id = game()
        val error = SecurityException("not exported")
        startFailure = error
        assertEquals(LaunchOutcome.Failed(id, FailureReason.NotAllowed, error), launcher.launch(id))
    }
}

private const val SD_TREE = "content://com.android.externalstorage.documents/tree/1A2B-3C4D%3AROMs"
