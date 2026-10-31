package com.plumemu.graph

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.NewPinnedShortcut
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScanBatch
import com.plumemu.coredata.db.SystemEntity
import com.plumemu.coredata.settings.GridSizeSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.game.AppGameActions
import com.plumemu.launcher.choice.CoreToConfirm
import com.plumemu.launcher.play.PlaySessionOutcome
import com.plumemu.telemetry.LaunchFrom
import com.plumemu.telemetry.ScanTrigger
import com.plumemu.usage.RecordingSink
import com.plumemu.usage.assertNoFreeText
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The usage events that go through the graph (GraphUsage.kt) on the real graph and platform data:
 * names and fields, never a title or a file name, and nothing for what the platform data does not list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GraphUsageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val sink = RecordingSink()
    private val graph = AppGraph(context, log = {}, telemetry = sink)
    private val actions = AppGameActions(graph, context)

    @After
    fun tearDown() {
        graph.database.close()
        context.deleteDatabase("plumemu.db")
    }

    private fun addGame(
        file: String,
        systemId: String,
    ): Long =
        runBlocking {
            val scan = graph.database.scanDao()
            val folder = scan.folderByTreeUri(TREE)?.id ?: scan.insertFolder(RomFolderEntity(treeUri = TREE, displayName = "ROMs", addedAt = 0))
            val row =
                GameEntity(
                    folderId = folder, documentId = "primary:ROMs/$systemId/$file", parentDocumentId = "primary:ROMs/$systemId",
                    relativePath = "$systemId/$file", fileName = file, sizeBytes = 1, modifiedAt = 1, title = file.substringBeforeLast('.'),
                    sortName = file.lowercase(), regions = "USA", discGroup = null, discCount = 1, systemId = systemId, isPrimaryCopy = true,
                )
            scan.applyBatch(ScanBatch(folder, listOf(SystemEntity(systemId, systemId)), listOf(row)))
            scan.gamesInFolder(folder).single { it.fileName == file }.id
        }

    private fun awaitEvents(count: Int): List<Pair<String, Map<String, String>>> =
        runBlocking {
            withTimeout(TIMEOUT_MS) { while (sink.events.size < count) delay(10) }
            sink.sent
        }

    @Test
    fun aLaunchSendsItsSystemPlaceAndResultNeverTheGame() {
        val id = addGame("Zork Quest (USA).sfc", "snes")
        runBlocking { actions.launch(id.toString(), LaunchFrom.Favorites) }
        val (name, fields) = awaitEvents(1).single()
        assertEquals("game_launched", name)
        assertEquals("snes", fields["system_id"])
        assertEquals("favorites", fields["from"])
        // No emulator is installed in Robolectric: the person would be offered one.
        assertEquals("emulator_missing", fields["result"])
        assertNoFreeText(sink.events)
    }

    @Test
    fun aFavoriteSendsItsSystem() {
        val id = addGame("Zork Quest (USA).sfc", "snes")
        runBlocking { actions.setFavorite(id.toString(), true) }
        assertEquals(listOf("favorite_changed" to mapOf("system_id" to "snes", "change" to "added")), awaitEvents(1))
    }

    @Test
    fun aPlayRecordedSendsWholeMinutesWithItsEmulator() {
        val id = addGame("Zork Quest (USA).sfc", "snes")
        graph.trackSession(PlaySessionOutcome.Recorded(id, seconds = 125, emulatorId = "retroarch-bsnes"))
        assertEquals(listOf("game_session" to mapOf("system_id" to "snes", "emulator_id" to "retroarch-bsnes", "minutes" to "2")), awaitEvents(1))
    }

    @Test
    fun aPlayWithACoreQuestionIsSentOnlyOnceTheQuestionIsOverAndNeverAfterNo() {
        val id = addGame("Zork Quest (USA).sfc", "snes")
        val core = CoreToConfirm("com.retroarch", "bsnes_libretro_android.so")
        val notices = graph.libraryNotices
        runBlocking {
            val kept = graph.database.playDao().recordSession(id, endedAt = 200_000, seconds = 125)!!
            graph.afterPlay(PlaySessionOutcome.Recorded(id, 125, "retroarch-bsnes", core, kept))
            val first = notices.coreQuestion.value!!
            notices.coreQuestionShown(first)
            delay(300)
            assertEquals("held while the question is open", emptyList<Any>(), sink.sent)
            graph.coreQuestionOver(first) // Yes or B
            assertEquals(listOf("game_session" to mapOf("system_id" to "snes", "emulator_id" to "retroarch-bsnes", "minutes" to "2")), awaitEvents(1))
            graph.coreQuestionOver(first)

            val takenBack = graph.database.playDao().recordSession(id, endedAt = 400_000, seconds = 125)!!
            graph.afterPlay(PlaySessionOutcome.Recorded(id, 125, "retroarch-bsnes", core, takenBack))
            val second = notices.coreQuestion.value!!
            actions.takeBackPlay(takenBack) // No
            graph.coreQuestionOver(second)
            delay(500)
        }
        assertEquals("Over twice and No send nothing more", 1, sink.sent.size)
    }

    @Test
    fun aHomeMadeAgainWithAQuestionShownSendsItsPlayOnce() {
        val id = addGame("Zork Quest (USA).sfc", "snes")
        val core = CoreToConfirm("com.retroarch", "bsnes_libretro_android.so")
        runBlocking {
            val added = graph.database.playDao().recordSession(id, endedAt = 200_000, seconds = 125)!!
            graph.afterPlay(PlaySessionOutcome.Recorded(id, 125, "retroarch-bsnes", core, added))
            val question = graph.libraryNotices.coreQuestion.value!!
            graph.libraryNotices.coreQuestionShown(question)
            graph.coreQuestionsRestarted() // the old home's panel is gone: as B
            assertEquals(listOf("game_session" to mapOf("system_id" to "snes", "emulator_id" to "retroarch-bsnes", "minutes" to "2")), awaitEvents(1))
            graph.coreQuestionsRestarted()
            graph.coreQuestionOver(question)
            delay(500)
        }
        assertEquals("sent once", 1, sink.sent.size)
    }

    @Test
    fun nothingForWhatThePlatformDataDoesNotList() {
        val app = addGame("Some App.apk", "android_apps_not_a_system")
        runBlocking { actions.setFavorite(app.toString(), true) }
        graph.trackSession(PlaySessionOutcome.Recorded(app, seconds = 600, emulatorId = null))
        graph.trackViewChanged("apps", isGrid = true, PlumSettings())
        runBlocking { delay(500) }
        assertEquals(emptyList<Any>(), sink.sent)
    }

    @Test
    fun aPinnedShortcutSendsNothingThoughItsSystemIsListed() {
        val id =
            runBlocking {
                graph.database.pinnedShortcutDao().add(
                    NewPinnedShortcut("app.example", "game_1", 0, "A Shortcut", "a_shortcut", "steam", "Steam", addedAt = 1),
                ).gameId
            }
        runBlocking {
            actions.launch(id.toString(), LaunchFrom.List)
            actions.setFavorite(id.toString(), true)
        }
        graph.trackSession(PlaySessionOutcome.Recorded(id, seconds = 600, emulatorId = "retroarch-bsnes"))
        runBlocking { delay(500) }
        assertEquals(emptyList<Any>(), sink.sent)
    }

    @Test
    fun selectSendsTheViewWithTheStoredGridSize() {
        val stored = PlumSettings().let { it.copy(appearance = it.appearance.copy(gridSize = GridSizeSetting.Large)) }
        graph.trackViewChanged("snes", isGrid = true, stored)
        assertEquals(listOf("view_changed" to mapOf("system_id" to "snes", "view" to "grid", "grid_size" to "large")), awaitEvents(1))
    }

    @Test
    fun aSettingsWriteIsCountedOnceItHasLanded() {
        graph.settingsWriter.submitWatched({ it.copy(homeScreen = it.homeScreen.copy(showBattery = false)) }, graph::trackSettingChanges)
        assertEquals(listOf("setting_changed" to mapOf("key" to "show_battery", "value" to "off")), awaitEvents(1))
    }

    @Test
    fun aScanSendsTheLibrarysSizeAsRanges() {
        addGame("Zork Quest (USA).sfc", "snes")
        graph.trackScan(ScanEnded(ScanTrigger.FolderAdded, isOk = true, hasFolderLost = true, millis = 4_200))
        assertEquals(
            listOf("library_scanned" to mapOf("trigger" to "folder_added", "games_bucket" to "1–99", "systems" to "1", "seconds" to "4", "result" to "folder_lost")),
            awaitEvents(1),
        )
    }

    @Test
    fun anUpdateIsCountedOnceWithTheVersionItReplaced() {
        graph.versionSeen.swap("0.0.1-alpha")
        graph.trackUpdateInstalled()
        val (name, fields) = awaitEvents(1).single()
        assertEquals("update_installed", name)
        assertEquals("0.0.1-alpha", fields["from_version"])
        graph.trackUpdateInstalled()
        runBlocking { delay(300) }
        assertEquals("the same version again is no update", 1, sink.events.size)
    }

    private companion object {
        const val TREE = "content://com.android.externalstorage.documents/tree/primary%3AROMs"
        const val TIMEOUT_MS = 10_000L
    }
}
