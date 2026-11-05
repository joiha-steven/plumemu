package com.plumemu.setup

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.TextSize
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
    fun confirmStep() = ReferenceShots.capture("setup_confirm") { SetupAt(SetupFakes.confirm) }

    @Test
    fun confirmStepOnThePocket6() = ReferenceShots.capture("setup_confirm", sizes = listOf(ReferenceShots.Size.Pocket6)) { SetupAt(SetupFakes.confirm) }

    @Test
    fun folderStepEmptyOnThePocket6() = ReferenceShots.capture("setup_folder_empty", sizes = listOf(ReferenceShots.Size.Pocket6)) { SetupAt(SetupFakes.chooseFolder) }

    @Test
    fun lookStep() = ReferenceShots.capture("setup_look") { SetupAt(SetupFakes.look) }

    @Test
    fun lookStepOnThePocket6() = ReferenceShots.capture("setup_look", sizes = listOf(ReferenceShots.Size.Pocket6)) { SetupAt(SetupFakes.look) }

    @Test
    fun lookStepAtLargestText() = ReferenceShots.capture("setup_look", textSize = TextSize.Largest) { SetupAt(SetupFakes.look) }

    @Test
    fun libraryReadyOnThePocket6() = ReferenceShots.capture("setup_ready", sizes = listOf(ReferenceShots.Size.Pocket6)) { SetupAt(SetupFakes.ready) }

    @Test
    fun libraryReadyAtLargestText() = ReferenceShots.capture("setup_ready", textSize = TextSize.Largest) { SetupAt(SetupFakes.ready) }

    @Test
    fun lookStepWithAnotherTileFocused() = ReferenceShots.capture("setup_look_focus_last") { SetupAt(SetupFakes.look.copy(focus = SetupControl.Look5)) }

    @Test
    fun boxArtChoicesWithoutScreenScraperKeys() =
        ReferenceShots.capture("setup_box_art_two_choices") { SetupAt(SetupFakes.boxArtQuestion.copy(hasScreenScraper = false, focus = SetupControl.BoxArtLibretro)) }

    @Test
    fun scanInProgress() = ReferenceShots.capture("setup_scan") { SetupAt(SetupFakes.scanning) }

    @Test
    fun secondScreenQuestion() = ReferenceShots.capture("setup_second_screen") { SetupAt(SetupFakes.secondScreen) }

    @Test
    fun libraryReady() = ReferenceShots.capture("setup_ready", sizes = ReferenceShots.Size.entries) { SetupAt(SetupFakes.ready) }

    @Test
    fun libraryReadyAfterASignInSaysBoxArtIsComing() = ReferenceShots.capture("setup_ready_box_art") { SetupAt(SetupFakes.readyWithBoxArt) }

    @Test
    fun confirmStepReachedAgainShowsTheStoredAnswer() =
        ReferenceShots.capture("setup_confirm_again", sizes = listOf(ReferenceShots.Size.Wide, ReferenceShots.Size.FourThree)) { SetupAt(SetupFakes.confirmAgain) }

    @Test
    fun libraryReadyWithTheStatisticsRowFocused() =
        ReferenceShots.capture("setup_ready_stats_focused") { SetupAt(SetupFakes.ready.copy(focus = SetupControl.UsageStatistics)) }

    @Test
    fun homeRolePrompt() = ReferenceShots.capture("home_role_prompt") { HomeRolePromptAt() }

    @Test
    fun homeRolePromptWhenAndroidDidNotMakePlumemuTheHomeApp() =
        ReferenceShots.capture("home_role_not_yet", sizes = ReferenceShots.Size.WithPocket6) { HomeRolePromptAt(showsNotYet = true) }

    @Test
    fun homeRolePromptWhenAndroidDidNotMakePlumemuTheHomeAppAtLargest() =
        ReferenceShots.capture("home_role_not_yet", textSize = com.plumemu.coreui.theme.TextSize.Largest, sizes = ReferenceShots.Size.WithPocket6) { HomeRolePromptAt(showsNotYet = true) }
}
