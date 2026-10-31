package com.plumemu.setup

import com.plumemu.graph.ScanReport
import com.plumemu.graph.ScanState
import com.plumemu.library.scan.AddFolderResult
import com.plumemu.secondscreen.ScreenKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** What setup reads and writes; the app graph supplies the real one (`graph/GraphSetupSource.kt`). */
internal interface SetupSource {
    /** Picked folders whose grant Android still holds (persisted URI permissions are the truth). */
    val folders: Flow<List<SetupFolder>>

    /** Systems with games, biggest first. */
    val systems: Flow<List<SystemCount>>

    val scan: StateFlow<ScanState>

    /** What the running scan is doing (the walk, then the naming) and how far along; read with [scan]. */
    val scanReport: StateFlow<ScanReport>

    val usageStatistics: Flow<Boolean>

    /** Settings › Controls › Push right to play, asked on the last step. */
    val pushRightToPlay: Flow<Boolean>

    /**
     * The second screen's kind while setup should ask about it (a screen is there and
     * `second_screen.enabled` was never written); null otherwise.
     */
    val secondScreenQuestion: Flow<ScreenKind?>

    /** Keeps the picker's grant and records the folder. May throw (provider or database error). */
    suspend fun addFolder(treeUri: String): AddFolderResult

    /** Forgets a picked folder (its games and its grant); false when it could not (logged). */
    suspend fun removeFolder(folderId: Long): Boolean

    /** A scan now, or one more pass of the running one; [scan] is busy from this call on. */
    fun requestScan()

    /** Detects the installed emulators and stores the defaults; null when it cannot (logged). */
    suspend fun emulatorCoverage(): EmulatorCoverage?

    suspend fun setUsageStatistics(isOn: Boolean)

    suspend fun setPushRightToPlay(isOn: Boolean)

    suspend fun setSecondScreenEnabled(isOn: Boolean)

    suspend fun completeSetup()
}
