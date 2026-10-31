package com.plumemu.setup

import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * First-run setup against the boards RP5-O0 (before a folder is chosen), RP5-O1 and Nova-7 (a
 * folder chosen), RP5-O4 and Nova-9 (library ready), plus what the boards do not draw:
 * the scan in progress, the second screen question and the home screen question.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SetupScreenshotTest {
    @Test
    fun folderStepEmpty() = ReferenceShots.capture("setup_folder_empty", sizes = ReferenceShots.Size.entries) { SetupAt(SetupFakes.chooseFolder) }

    @Test
    fun folderStepWithACard() = ReferenceShots.capture("setup_folder_card", sizes = ReferenceShots.Size.entries) { SetupAt(SetupFakes.folderChosen) }

    @Test
    fun loadFailed() = ReferenceShots.capture("setup_load_failed", sizes = ReferenceShots.Size.entries) { SetupAt(SetupFakes.loadFailed) }

    @Test
    fun scanInProgress() = ReferenceShots.capture("setup_scan") { SetupAt(SetupFakes.scanning) }

    @Test
    fun secondScreenQuestion() = ReferenceShots.capture("setup_second_screen") { SetupAt(SetupFakes.secondScreen) }

    @Test
    fun libraryReady() = ReferenceShots.capture("setup_ready", sizes = ReferenceShots.Size.entries) { SetupAt(SetupFakes.ready) }

    @Test
    fun libraryReadyAfterASignInSaysBoxArtIsComing() = ReferenceShots.capture("setup_ready_box_art") { SetupAt(SetupFakes.readyWithBoxArt) }

    @Test
    fun libraryReadyWithTheStatisticsRowFocused() =
        ReferenceShots.capture("setup_ready_stats_focused") { SetupAt(SetupFakes.ready.copy(focus = SetupControl.UsageStatistics)) }

    @Test
    fun libraryReadyWithTheOneHandOptionOnAndFocused() =
        ReferenceShots.capture("setup_ready_one_hand") { SetupAt(SetupFakes.ready.copy(pushRightToPlay = true, focus = SetupControl.PushRightToPlay)) }

    @Test
    fun homeRolePrompt() = ReferenceShots.capture("home_role_prompt") { HomeRolePromptAt() }
}
