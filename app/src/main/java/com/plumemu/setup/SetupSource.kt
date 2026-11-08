package com.plumemu.setup

import com.plumemu.coredata.settings.BoxArtSourceSetting
import com.plumemu.coredata.settings.ConfirmButtonSetting
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coreui.theme.ColorSet
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

    /** A gamepad is connected (the confirm step exists while one is). */
    val hasGamepad: Flow<Boolean>

    /** What the look step may suggest for this device. */
    val deviceFacts: DeviceFacts

    /** `library.box_art_source` as stored; null until chosen. */
    val boxArtSource: Flow<BoxArtSourceSetting?>

    /**
     * The second screen's kind while setup should ask about it (a screen is there and
     * `second_screen.enabled` was never written); null otherwise.
     */
    val secondScreenQuestion: Flow<ScreenKind?>

    /** The device can reach the internet now (any network kind); read once when Ready opens. */
    fun isOnline(): Boolean

    /** Keeps the picker's grant and records the folder. May throw (provider or database error). */
    suspend fun addFolder(treeUri: String): AddFolderResult

    /** Forgets a picked folder (its games and its grant); false when it could not (logged). */
    suspend fun removeFolder(folderId: Long): Boolean

    /** A scan now, or one more pass of the running one; [scan] is busy from this call on. */
    fun requestScan()

    /** Detects the installed emulators and stores the defaults; null when it cannot (logged). */
    suspend fun emulatorCoverage(): EmulatorCoverage?

    suspend fun setUsageStatistics(isOn: Boolean)

    /** Settings › Controls › Confirm with B: [ConfirmButtonSetting.Bottom] when B confirms. */
    suspend fun setConfirmButton(setting: ConfirmButtonSetting)

    /** Picks [set] as the colours (what Settings › Appearance › Colors writes), and [textSize] when none was picked before. */
    suspend fun applyLook(
        set: ColorSet,
        textSize: TextSizeSetting?,
        blackPage: Boolean = false,
    )

    /** The colour set in force when it is one of the sets; null for own colours. The look step opens on it. */
    val currentColorSet: Flow<ColorSet?>

    /** Stores the box art source (what Settings › Accounts › Box art writes); throws when it could not. */
    suspend fun setBoxArtSource(source: BoxArtSourceSetting)

    suspend fun setSecondScreenEnabled(isOn: Boolean)

    /** Stores that setup is done; [questionsSkipped] when Set up later left the questions unasked (they come with the first folder). */
    suspend fun completeSetup(questionsSkipped: Boolean)
}
