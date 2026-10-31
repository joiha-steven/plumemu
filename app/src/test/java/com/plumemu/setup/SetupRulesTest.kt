package com.plumemu.setup

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure rules of setup: the emulator line, where a picked folder lives, which controls a step has. */
class SetupRulesTest {
    private val names = mapOf("snes" to "Super Nintendo", "gc" to "GameCube", "ps2" to "PlayStation 2")

    @Test
    fun everySystemCoveredNamesNone() {
        val coverage = emulatorCoverageOf(listOf("snes" to 248, "gc" to 12), mapOf("snes" to "snes9x", "gc" to "dolphin")) { names.getValue(it) }
        assertEquals(EmulatorCoverage(2, 2, emptyList()), coverage)
    }

    @Test
    fun someMissingAreNamedBiggestFirst() {
        val coverage =
            emulatorCoverageOf(listOf("snes" to 248, "gc" to 12, "ps2" to 40), mapOf("snes" to "snes9x", "gc" to null)) { names.getValue(it) }
        assertEquals(EmulatorCoverage(1, 3, listOf("PlayStation 2", "GameCube")), coverage)
    }

    @Test
    fun noneInstalledNamesThemAll() {
        val coverage = emulatorCoverageOf(listOf("snes" to 248, "gc" to 12), emptyMap()) { names.getValue(it) }
        assertEquals(EmulatorCoverage(0, 2, listOf("Super Nintendo", "GameCube")), coverage)
    }

    @Test
    fun noSystemsNoLine() {
        assertEquals(EmulatorCoverage(0, 0, emptyList()), emulatorCoverageOf(emptyList(), emptyMap()) { it })
    }

    @Test
    fun foldersOnTheDeviceAndOnAnSdCardAreReadFromTheTreeUri() {
        assertEquals(FolderPlace.Internal(listOf("ROMs")), folderPlaceOf("content://$EXTERNAL_STORAGE_AUTHORITY/tree/primary%3AROMs"))
        assertEquals(FolderPlace.Internal(listOf("Games", "snes")), folderPlaceOf("content://$EXTERNAL_STORAGE_AUTHORITY/tree/primary%3AGames%2Fsnes"))
        assertEquals(FolderPlace.Internal(emptyList()), folderPlaceOf("content://$EXTERNAL_STORAGE_AUTHORITY/tree/primary%3A"))
        assertEquals(FolderPlace.Removable(listOf("My Games")), folderPlaceOf("content://$EXTERNAL_STORAGE_AUTHORITY/tree/1A2B-3C4D%3AMy%20Games"))
        assertEquals(FolderPlace.Other, folderPlaceOf("content://com.example.cloud/tree/abc"))
        assertEquals("ROMs", folderNameOf("content://$EXTERNAL_STORAGE_AUTHORITY/tree/primary%3AROMs"))
        assertEquals("", folderNameOf("content://com.example.cloud/tree/abc"))
    }

    @Test
    fun aPlusInAFolderNameStaysAPlusAndPercentEscapesAreDecoded() {
        assertEquals(FolderPlace.Internal(listOf("ROMs+Hacks")), folderPlaceOf("content://$EXTERNAL_STORAGE_AUTHORITY/tree/primary%3AROMs+Hacks"))
        assertEquals(FolderPlace.Internal(listOf("ROMs+Hacks")), folderPlaceOf("content://$EXTERNAL_STORAGE_AUTHORITY/tree/primary%3AROMs%2BHacks"))
        assertEquals("100% Games", folderNameOf("content://$EXTERNAL_STORAGE_AUTHORITY/tree/primary%3A100%25%20Games"))
    }

    @Test
    fun theDocumentsShortcutIsInternalStorageDocuments() {
        assertEquals(FolderPlace.Internal(listOf("Documents", "ROMs")), folderPlaceOf("content://$EXTERNAL_STORAGE_AUTHORITY/tree/home%3AROMs"))
        assertEquals("Documents", folderNameOf("content://$EXTERNAL_STORAGE_AUTHORITY/tree/home%3A"))
    }

    @Test
    fun anNtfsDriveIsRemovableAndAnUnknownOrDotDotIdIsOther() {
        assertEquals(FolderPlace.Removable(listOf("Games")), folderPlaceOf("content://$EXTERNAL_STORAGE_AUTHORITY/tree/0123456789ABCDEF%3AGames"))
        assertEquals(FolderPlace.Other, folderPlaceOf("content://$EXTERNAL_STORAGE_AUTHORITY/tree/primary%3AROMs%2F..%2Fdata"))
        assertEquals(FolderPlace.Other, folderPlaceOf("content://$EXTERNAL_STORAGE_AUTHORITY/tree/secondary%3AROMs"))
    }

    @Test
    fun aTreeOnAnUnknownRootStillNamesTheFolderAfterTheColon() {
        assertEquals("ROMs", folderNameOf("content://$EXTERNAL_STORAGE_AUTHORITY/tree/secondary%3AROMs"))
        assertEquals("snes", folderNameOf("content://$EXTERNAL_STORAGE_AUTHORITY/tree/secondary%3AGames%2Fsnes"))
        assertEquals("", folderNameOf("content://$EXTERNAL_STORAGE_AUTHORITY/tree/secondary%3A"))
        assertEquals("", folderNameOf("content://$EXTERNAL_STORAGE_AUTHORITY/tree/secondary"))
    }

    @Test
    fun eachStepHasItsControlsAndOpensOnTheLast() {
        assertEquals(listOf(SetupControl.ChooseFolder, SetupControl.SetUpLater), controlsOf(SetupStep.Folders, folderCount = 0, hasScanFailed = false))
        assertEquals(listOf(SetupControl.FolderCard0, SetupControl.AddFolder, SetupControl.Continue), controlsOf(SetupStep.Folders, folderCount = 1, hasScanFailed = false))
        assertEquals(emptyList<SetupControl>(), controlsOf(SetupStep.Scan, folderCount = 1, hasScanFailed = false))
        assertEquals(listOf(SetupControl.TryAgain), controlsOf(SetupStep.Scan, folderCount = 1, hasScanFailed = true))
        val ready = controlsOf(SetupStep.Ready, folderCount = 1, hasScanFailed = false)
        assertEquals(listOf(SetupControl.UsageStatistics, SetupControl.PushRightToPlay, SetupControl.StartPlaying), ready)
        assertEquals(SetupControl.StartPlaying, focusIn(ready, requested = null))
        // A build with no statistics server asks nothing about them.
        val readyWithoutStatistics = controlsOf(SetupStep.Ready, folderCount = 1, hasScanFailed = false, asksAboutStatistics = false)
        assertEquals(listOf(SetupControl.PushRightToPlay, SetupControl.StartPlaying), readyWithoutStatistics)
        assertEquals(listOf(SetupControl.PushRightToPlay, SetupControl.StartPlaying), SetupUiState(step = SetupStep.Ready, asksAboutStatistics = false).controls)
        assertEquals(SetupControl.UsageStatistics, focusIn(ready, SetupControl.UsageStatistics))
        assertEquals("a focus the step does not have falls back", SetupControl.StartPlaying, focusIn(ready, SetupControl.Continue))
    }
}
