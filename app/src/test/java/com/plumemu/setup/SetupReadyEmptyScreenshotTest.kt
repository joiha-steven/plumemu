package com.plumemu.setup

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.TextSize
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The last step with no folder (Set up later) and with a folder that holds no games: its title says so. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SetupReadyEmptyScreenshotTest {
    private val noFolder = SetupUiState(step = SetupStep.Ready, focus = SetupControl.StartPlaying)
    private val noGames = SetupFakes.ready.copy(folders = listOf(SetupFakes.romsFolder.copy(gameCount = 0, systemCount = 0)), systems = emptyList())

    @Test
    fun readyWithNoFolder() = ReferenceShots.capture("setup_ready_no_folder", sizes = ReferenceShots.Size.entries) { SetupAt(noFolder) }

    @Test
    fun readyWithNoFolderAtLargestText() = ReferenceShots.capture("setup_ready_no_folder", textSize = TextSize.Largest) { SetupAt(noFolder) }

    @Test
    fun readyWithAFolderAndNoGames() = ReferenceShots.capture("setup_ready_no_games", sizes = ReferenceShots.Size.entries) { SetupAt(noGames) }

    @Test
    fun readyWithAFolderAndNoGamesAtLargestText() = ReferenceShots.capture("setup_ready_no_games", textSize = TextSize.Largest) { SetupAt(noGames) }

    @Test
    fun readyWithNoFolderOnThePocket6() = ReferenceShots.capture("setup_ready_no_folder", sizes = listOf(ReferenceShots.Size.Pocket6)) { SetupAt(noFolder) }

    @Test
    fun readyWithAFolderAndNoGamesOnThePocket6() = ReferenceShots.capture("setup_ready_no_games", sizes = listOf(ReferenceShots.Size.Pocket6)) { SetupAt(noGames) }
}
