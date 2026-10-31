package com.plumemu.graph

import com.plumemu.content.LibraryHomeContentTest
import com.plumemu.coredata.db.AnsweredFolderRow
import com.plumemu.coredata.db.UnknownFolderRow
import com.plumemu.settings.screen.FolderAnswer
import com.plumemu.settings.screen.FolderKey
import com.plumemu.settings.screen.SettingsFolderRow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Settings › Library's answers for unknown folders outlive the screen: an answer asked while a scan holds the store
 * is still stored (and then scanned) after Settings is gone, "Saving…" shows meanwhile, a failure is said and logged
 * without scanning, and an answer whose system left the platform data lists its folder as unknown again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FolderAnswersTest {
    private val key = FolderKey(1, "Xbox 360")
    private val stored = mutableListOf<Pair<FolderKey, String?>>()
    private var scans = 0
    private val logs = mutableListOf<String>()

    private fun TestScope.answers(store: suspend (FolderKey, String?) -> Unit) =
        FolderAnswers(backgroundScope, StandardTestDispatcher(testScheduler), store, { scans++ }, { logs += it })

    @Test
    fun anAnswerAskedDuringAScanIsStoredAndScannedAfterTheScreenIsGone() = runTest {
        val scanEnds = CompletableDeferred<Unit>()
        val answers = answers { k, s ->
            scanEnds.await() // the store waits for the running scan
            stored += k to s
        }
        var told: Boolean? = null
        answers.answer(key, "x360") { told = it }
        runCurrent()
        assertEquals("Saving… while it waits", setOf(key), answers.saving.value)
        assertTrue(stored.isEmpty())
        // Settings is closed here: nothing of the screen is left to cancel the write (its scope is the process's).
        scanEnds.complete(Unit)
        runCurrent()
        assertEquals(listOf(key to "x360"), stored)
        assertEquals(1, scans)
        assertEquals(emptySet<FolderKey>(), answers.saving.value)
        assertEquals(true, told)
    }

    @Test
    fun aFailedStoreIsReportedLoggedWithoutNamesAndNeverScans() = runTest {
        val answers = answers { _, _ -> error("disk full for /storage/Xbox 360") }
        var told: Boolean? = null
        answers.answer(key, null) { told = it }
        runCurrent()
        assertEquals(false, told)
        assertEquals(0, scans)
        assertEquals(emptySet<FolderKey>(), answers.saving.value)
        assertEquals(1, logs.size)
        assertTrue(logs.single(), "Xbox" !in logs.single() && "/storage" !in logs.single())
    }

    @Test
    fun twoQuickAnswersForOneFolderEndOnTheLastAndSavingLastsUntilBothAreStored() = runTest {
        val gate = CompletableDeferred<Unit>()
        val answers = answers { k, s ->
            if (stored.isEmpty()) gate.await()
            stored += k to s
        }
        answers.answer(key, "x360") {}
        answers.answer(key, "gc") {}
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf(key to "x360", key to "gc"), stored)
        assertEquals(emptySet<FolderKey>(), answers.saving.value)
        assertEquals(2, scans)
    }

    private val catalog = LibraryHomeContentTest.catalog

    @Test
    fun unansweredFoldersComeFirstThenAnsweredOnesWithTheSystemsDisplayName() {
        val rows =
            settingsUnknownFolders(
                unknown = listOf(UnknownFolderRow(2, "Zed", 1), UnknownFolderRow(1, "Wii U", 5)),
                answered = listOf(AnsweredFolderRow(1, "Halo", "psx", 0), AnsweredFolderRow(1, "Docs", null, 0)),
                catalog = catalog,
            )
        assertEquals(listOf("Wii U", "Zed", "Docs", "Halo"), rows.map { it.name })
        assertEquals(FolderAnswer.Ignored, rows[2].answer)
        assertEquals(FolderAnswer.OfSystem("psx", "PlayStation"), rows[3].answer)
        assertEquals(listOf(5, 1, 0, 0), rows.map { it.files })
    }

    @Test
    fun anAnswerWhoseSystemLeftThePlatformDataIsListedAsUnknownAgainWithItsFilesAndWarnedOnce() {
        val warned = mutableListOf<Pair<FolderKey, String>>()
        val rows =
            settingsUnknownFolders(
                unknown = emptyList(),
                answered = listOf(AnsweredFolderRow(1, "Halo", "gone-system", 4)),
                catalog = catalog,
            ) { k, s -> warned += k to s }
        assertEquals(listOf(SettingsFolderRow(FolderKey(1, "Halo"), 4, FolderAnswer.Unanswered)), rows)
        assertEquals(listOf(FolderKey(1, "Halo") to "gone-system"), warned)
    }

    @Test
    fun theSystemChoicesAreEverySystemThatOpensFilesNamedAsTheMainScreenNamesThemAToZ() {
        val choices = folderSystemChoicesOf(catalog, java.util.Locale.ENGLISH)
        assertTrue(choices.isNotEmpty())
        assertEquals(choices.map { it.name }, choices.map { it.name }.sortedWith(java.text.Collator.getInstance(java.util.Locale.ENGLISH)))
        assertEquals("PlayStation", choices.single { it.id == "psx" }.name)
    }
}
