package com.plumemu.setup

import com.plumemu.graph.ScanReport
import com.plumemu.graph.ScanState
import com.plumemu.library.scan.AddFolderResult
import com.plumemu.secondscreen.ScreenKind
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update

/** Setup's source in memory: folders, systems and the scan are set by the test. */
internal class FakeSetupSource : SetupSource {
    /** The folders as the database and Android's grants say; [folders] reads them. */
    val heldFolders = MutableStateFlow<List<SetupFolder>>(emptyList())

    /** Thrown by every collection of [folders] while set (a Room error). */
    var foldersFailure: Exception? = null
    var foldersCollections = 0

    override val folders: Flow<List<SetupFolder>> =
        flow {
            foldersCollections++
            foldersFailure?.let { throw it }
            emitAll(heldFolders)
        }
    override val systems = MutableStateFlow<List<SystemCount>>(emptyList())
    override val scan = MutableStateFlow(ScanState())
    override val scanReport = MutableStateFlow(ScanReport())
    override val usageStatistics = MutableStateFlow(false)
    override val pushRightToPlay = MutableStateFlow(false)

    /** The second screen's kind while undecided; set by the test, cleared by an answer (as the store does). */
    override val secondScreenQuestion = MutableStateFlow<ScreenKind?>(null)

    /** What [setSecondScreenEnabled] stored; null until then. */
    var secondScreenAnswer: Boolean? = null

    /** What [addFolder] answers, or throws. */
    var addAnswer: () -> AddFolderResult = { AddFolderResult.Added(heldFolders.value.size + 1L) }
    var coverage: EmulatorCoverage? = EmulatorCoverage(2, 2, emptyList())
    var coverageFailure: Exception? = null
    var saveFailure: Exception? = null

    val added = CopyOnWriteArrayList<String>()
    var scanRequests = 0
    var setupCompleted = false
    var completions = 0

    override suspend fun addFolder(treeUri: String): AddFolderResult {
        val answer = addAnswer()
        added += treeUri
        if (answer is AddFolderResult.Added) {
            heldFolders.update { it + SetupFolder(answer.folderId, treeUri, "ROMs", gameCount = 0, systemCount = 0, isCounted = false) }
        }
        return answer
    }

    /** What [removeFolder] answers, or throws. */
    var removeAnswer: () -> Boolean = { true }
    val removed = CopyOnWriteArrayList<Long>()

    /** While set, a removal waits for it: the folder's scan is busy. */
    var removeGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    var removeCalls = 0

    override suspend fun removeFolder(folderId: Long): Boolean {
        removeCalls++
        removeGate?.await()
        val answer = removeAnswer()
        if (answer) {
            removed += folderId
            heldFolders.update { folders -> folders.filter { it.id != folderId } }
        }
        return answer
    }

    override fun requestScan() {
        scanRequests++
        scan.update { it.copy(isBusy = true) }
    }

    /** The scan the model asked for has ended. */
    fun endScan(
        failed: Boolean = false,
        unavailable: Set<Long> = emptySet(),
    ) {
        scanReport.value = ScanReport()
        scan.value = ScanState(isBusy = false, hasFailed = failed, unavailableFolderIds = unavailable)
    }

    override suspend fun emulatorCoverage(): EmulatorCoverage? {
        coverageFailure?.let { throw it }
        return coverage
    }

    override suspend fun setUsageStatistics(isOn: Boolean) {
        saveFailure?.let { throw it }
        usageStatistics.value = isOn
    }

    override suspend fun setPushRightToPlay(isOn: Boolean) {
        saveFailure?.let { throw it }
        pushRightToPlay.value = isOn
    }

    override suspend fun setSecondScreenEnabled(isOn: Boolean) {
        saveFailure?.let { throw it }
        secondScreenAnswer = isOn
        secondScreenQuestion.value = null
    }

    override suspend fun completeSetup() {
        saveFailure?.let { throw it }
        setupCompleted = true
        completions++
    }
}
