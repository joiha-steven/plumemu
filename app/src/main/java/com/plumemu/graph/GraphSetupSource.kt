package com.plumemu.graph

import com.plumemu.telemetry.ScanTrigger
import android.content.Context
import com.plumemu.coredata.db.FolderGameCount
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.library.scan.AddFolderResult
import com.plumemu.library.scan.AndroidFolderPermissions
import com.plumemu.library.scan.FolderPermissions
import com.plumemu.library.scan.RomFolders
import com.plumemu.secondscreen.ScreenKind
import com.plumemu.setup.EmulatorCoverage
import com.plumemu.setup.SetupFolder
import com.plumemu.setup.SetupSource
import com.plumemu.setup.SystemCount
import com.plumemu.setup.emulatorCoverageOf
import com.plumemu.setup.folderNameOf
import com.plumemu.telemetry.SwitchPlace
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Setup's reads and writes on the app graph: folders and counts from Room, the scan from [StartupWork]. */
internal class GraphSetupSource(
    private val graph: AppGraph,
    context: Context,
    private val io: CoroutineDispatcher,
) : SetupSource {
    private val permissions: FolderPermissions = AndroidFolderPermissions(context.applicationContext.contentResolver)
    private val romFolders by lazy { RomFolders(graph.database.scanDao(), permissions) }

    /** Bumped after each pick: a grant taken again for a folder already recorded changes no table. */
    private val grantsChanged = MutableStateFlow(0)

    override val folders: Flow<List<SetupFolder>> =
        combine(graph.database.scanDao().observeFolders(), graph.database.gameDao().observeFolderCounts(), grantsChanged) { folders, counts, _ ->
            // Asks Android for its grants on every change: a folder revoked in Android's settings leaves the list.
            grantedFolders(folders, counts, permissions::isHeld)
        }.flowOn(io).distinctUntilChanged()

    override val systems: Flow<List<SystemCount>> =
        graph.database.gameDao().observeSystemsWithGames().map { counts ->
            val catalog = graph.catalog()
            counts
                .map { SystemCount(catalog?.system(it.id)?.displayName ?: it.name, it.gameCount) }
                .sortedWith(compareByDescending<SystemCount> { it.gameCount }.thenBy { it.name })
        }.distinctUntilChanged()

    override val scan: StateFlow<ScanState> get() = graph.startup.scanState

    override val scanReport: StateFlow<ScanReport> get() = graph.startup.scanReport

    override val usageStatistics: Flow<Boolean> =
        graph.settings.filterNotNull().map { it.advanced.usageStatistics }.distinctUntilChanged()

    override val pushRightToPlay: Flow<Boolean> =
        graph.settings.filterNotNull().map { it.controls.pushRightToPlay }.distinctUntilChanged()

    override val secondScreenQuestion: Flow<ScreenKind?> =
        combine(graph.secondScreens.current, graph.settings.filterNotNull().map { it.secondScreen.enabled }) { screen, enabled ->
            screen?.kind?.takeIf { enabled == null }
        }.distinctUntilChanged()

    override suspend fun addFolder(treeUri: String): AddFolderResult =
        try {
            withContext(io) { romFolders.addPickedFolder(treeUri, folderNameOf(treeUri)) }
        } finally {
            grantsChanged.value++
        }

    override suspend fun removeFolder(folderId: Long): Boolean =
        try {
            graph.startup.removeFolder {
                withContext(io) {
                    // Only this folder's row (its games go with it) and only its grant.
                    graph.database.scanDao().folders().firstOrNull { it.id == folderId }?.let { romFolders.removeFolder(it) }
                }
            }.await()
        } finally {
            grantsChanged.value++
        }

    override fun requestScan() {
        graph.startup.requestScan(ScanTrigger.Setup)
    }

    override suspend fun emulatorCoverage(): EmulatorCoverage? {
        val catalog = graph.catalog() ?: return null
        val launching = graph.launching() ?: return null
        // Detected again here: an emulator installed while setup was open counts.
        val installed = launching.detector.detect(catalog)
        // A system of installed apps (Android games) needs no emulator: not counted.
        val systems = graph.database.gameDao().observeSystemsWithGames().first().filterNot { catalog.system(it.id)?.fromInstalledApps == true }
        launching.choices.refreshDefaults(systems.map { it.id }, installed)
        // Found, not picked: a system with nothing chosen asks on its first game, and a
        // legal-risk emulator (Switch) counts as found though it is never recommended.
        val found =
            systems.associate { system ->
                system.id to catalog.system(system.id)?.let(catalog::emulatorsOf)?.firstOrNull { it.id in installed.emulatorIds }?.id
            }
        val names = systems.associate { it.id to (catalog.system(it.id)?.displayName ?: it.name) }
        return emulatorCoverageOf(systems.map { it.id to it.gameCount }, found) { id -> names[id] ?: id }
    }

    override suspend fun setUsageStatistics(isOn: Boolean) {
        // Before the store: the Settings follower (followUsageStatistics) then finds it done, so the
        // place sent with stats_declined / stats_switched_on is setup's.
        graph.telemetry?.setEnabled(isOn, SwitchPlace.Setup)
        graph.settingsStore.update { it.copy(advanced = it.advanced.copy(usageStatistics = isOn)) }
    }

    override suspend fun setPushRightToPlay(isOn: Boolean) {
        graph.settingsStore.update { it.copy(controls = it.controls.copy(pushRightToPlay = isOn)) }
    }

    override suspend fun setSecondScreenEnabled(isOn: Boolean) {
        graph.settingsStore.update { it.copy(secondScreen = it.secondScreen.copy(enabled = isOn)) }
    }

    override suspend fun completeSetup() = graph.settingsStore.markSetupCompleted()

}

/**
 * The folders setup shows: recorded ones Android still holds a grant for (after process death
 * setup comes back with exactly these), with their games and systems; counted once a scan of the
 * folder has ended.
 */
internal fun grantedFolders(
    folders: List<RomFolderEntity>,
    counts: List<FolderGameCount>,
    isHeld: (treeUri: String) -> Boolean,
): List<SetupFolder> {
    val byFolder = counts.associateBy(FolderGameCount::folderId)
    return folders.filter { isHeld(it.treeUri) }.map { folder ->
        val count = byFolder[folder.id]
        SetupFolder(
            id = folder.id,
            treeUri = folder.treeUri,
            displayName = folder.displayName,
            gameCount = count?.gameCount ?: 0,
            systemCount = count?.systemCount ?: 0,
            isCounted = folder.lastScanFinishedAt != null,
        )
    }
}
