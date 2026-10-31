package com.plumemu.settings.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpRect
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.graph.ScanPhase
import com.plumemu.graph.ScanReport
import com.plumemu.graph.ScanResult
import com.plumemu.graph.UnavailableFolder
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.library.scan.UnavailableReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Rescan library panel's scan states as the shots and tests show them. */
internal object RescanShots {
    private val systems = ShotContent.library.systems

    /** The walk: 1,200 files seen, no total known. */
    val walking = ShotContent.library.copy(isScanning = true, scan = ScanReport(phase = ScanPhase.Reading, filesSeen = 1200))

    val naming = ShotContent.library.copy(isScanning = true, scan = ScanReport(phase = ScanPhase.Naming, filesSeen = 2054, gamesNamed = 830, gamesToName = 2054))

    fun done(result: ScanResult) = ShotContent.library.copy(gameCount = 2054, systems = emptyList(), systemCount = 14, scan = ScanReport(result = result))

    /** A second press on a running scan: the notice sits under the progress. */
    val busy = walking

    val withChanges = done(ScanResult(scannedFolders = 2, gamesAdded = 3, gamesRemoved = 2))
    val noChanges = done(ScanResult(scannedFolders = 2))
    val folderUnavailable = done(ScanResult(unavailable = listOf(UnavailableFolder(2, UnavailableReason.PERMISSION_REVOKED))))
    val mixed = done(ScanResult(scannedFolders = 1, gamesAdded = 5, unavailable = listOf(UnavailableFolder(2, UnavailableReason.FOLDER_MISSING))))
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsRescanTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun show(content: SettingsContent) {
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { SettingsAt(optionsAt(SettingsOption.Rescan), content = content) } }
    }

    private fun see(text: String) = compose.onNodeWithText(text).assertIsDisplayed()

    @Test
    fun theWalkShowsFilesSeenAndNoTotal() {
        show(RescanShots.walking)
        see("Reading folders · 1,200 files")
        compose.onNodeWithTag(SettingsPanelTags.SCAN_PULSE).assertExists()
    }

    @Test
    fun aScanThatHasNoEventYetSaysItIsReading() {
        show(ShotContent.library.copy(isScanning = true))
        see("Reading folders…")
    }

    @Test
    fun namingShowsHowManyAreNamedOfThoseToName() {
        show(RescanShots.naming)
        see("Naming games · 830 of 2,054")
        compose.onNodeWithTag(SettingsPanelTags.SCAN_PULSE).assertExists()
    }

    @Test
    fun aBusyPressShowsTheNoticeUnderTheProgress() {
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { SettingsAt(optionsAt(SettingsOption.Rescan), content = RescanShots.busy, busyPress = true) } }
        see("Reading folders · 1,200 files")
        see("Already reading your games.")
    }

    @Test
    fun namingBeforeTheTotalIsKnownShowsOnlyTheCount() {
        show(ShotContent.library.copy(isScanning = true, scan = ScanReport(phase = ScanPhase.Naming, gamesNamed = 830)))
        see("Naming games · 830")
    }

    @Test
    fun doneWithChangesNamesGamesSystemsNewAndRemoved() {
        show(RescanShots.withChanges)
        see("Done · 2,054 games in 14 systems · 3 new files · 2 files removed")
        compose.onNodeWithTag(SettingsPanelTags.SCAN_PULSE).assertDoesNotExist()
    }

    @Test
    fun doneWithNothingChangedSaysSo() {
        show(RescanShots.noChanges)
        see("Done · 2,054 games in 14 systems · no changes")
    }

    @Test
    fun aZeroCountIsDropped() {
        // The catalog could not list the systems (systems is empty): the count still comes from the games' own rows.
        show(RescanShots.done(ScanResult(scannedFolders = 1, gamesRemoved = 4)))
        see("Done · 2,054 games in 14 systems · 4 files removed")
    }

    @Test
    fun anUnavailableFolderIsNamedWithWhatToDo() {
        show(RescanShots.folderUnavailable)
        see("SD card › ROMs: Plumemu lost access to it. Pick the folder again.")
        compose.onNodeWithText("Done", substring = true).assertDoesNotExist()
    }

    @Test
    fun aMissingFolderAndAFailedReadAreToldApart() {
        assertEquals(
            listOf("SD card › ROMs: not found. Is the SD card in?"),
            textsOf(RescanShots.done(ScanResult(unavailable = listOf(UnavailableFolder(2, UnavailableReason.FOLDER_MISSING))))),
        )
    }

    @Test
    fun aFailedReadSaysTryAgain() {
        assertEquals(
            listOf("Internal storage › ROMs: could not be read. Try again."),
            textsOf(RescanShots.done(ScanResult(unavailable = listOf(UnavailableFolder(1, UnavailableReason.READ_FAILED))))),
        )
    }

    @Test
    fun someFoldersScannedAndOneUnavailableShowsBothFacts() {
        show(RescanShots.mixed)
        see("Done · 2,054 games in 14 systems · 5 new files")
        see("SD card › ROMs: not found. Is the SD card in?")
    }

    @Test
    fun aScanThatCouldNotRunSaysSo() {
        show(RescanShots.done(ScanResult(failed = true)))
        see("Could not read your games. Try again.")
    }

    @Test
    fun withoutAResultThePanelIsTheNormalBody() {
        show(ShotContent.library)
        see("Reads your folders again and adds or removes what changed.")
        compose.onNodeWithText("Done", substring = true).assertDoesNotExist()
        compose.onNodeWithTag(SettingsPanelTags.STATUS).assertDoesNotExist()
    }

    private fun textsOf(content: SettingsContent): List<String> {
        var lines = emptyList<String>()
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { lines = rescanLines(content) } }
        compose.waitForIdle()
        return lines
    }
}

/** Progress and result lines start where the panel's body does, and stay clear of the Rescan row, at all six sizes. */
abstract class SettingsRescanAlignBase {
    protected val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    protected fun linesAlign(label: String, narrow: Boolean = false) {
        var size by mutableStateOf(TextSize.Small)
        var content by mutableStateOf(RescanShots.walking)
        var busy by mutableStateOf(false)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) { SettingsAt(rescanFocus(narrow), content = content, busyPress = busy) } }
        val problems = mutableListOf<String>()
        for ((state, pressed) in listOf(RescanShots.walking to false, RescanShots.walking to true, RescanShots.naming to false, RescanShots.withChanges to false, RescanShots.mixed to false)) {
            content = state
            busy = pressed
            for (each in TextSize.entries) {
                size = each
                compose.waitForIdle()
                val at = "$label ${each.name} ${state.scan.phase} busy=$pressed"
                val body = compose.onNode(hasText("Reads your folders again", substring = true), useUnmergedTree = true).getUnclippedBoundsInRoot()
                val first = bounds(if (state.isScanning) SettingsPanelTags.SCAN_PULSE else SettingsPanelTags.STATUS)
                if (kotlin.math.abs(first.left.value - body.left.value) > 0.5f) problems += "$at: line at ${first.left}, body at ${body.left}"
                // Narrow: the panel is a stop of its own, the list is not on screen.
                val row = if (narrow) null else bounds(SettingsTags.option(SettingsOption.Rescan))
                if (row != null && first.left.value < row.right.value) problems += "$at: line at ${first.left} overlaps the Rescan row ending at ${row.right}"
                if (first.top.value < body.bottom.value) problems += "$at: line starts at ${first.top} inside the body ending at ${body.bottom}"
                if (pressed) {
                    val notice = bounds(SettingsTags.NOTICE)
                    if (kotlin.math.abs(notice.left.value - body.left.value) > 0.5f) problems += "$at: notice at ${notice.left}, body at ${body.left}"
                    if (notice.top.value < first.bottom.value) problems += "$at: notice overlaps the progress line"
                }
                if (state.scan.result?.unavailable?.isNotEmpty() == true && state.scan.result?.scannedFolders == 1) {
                    val extra = bounds(SettingsPanelTags.rescanExtra(1))
                    if (kotlin.math.abs(extra.left.value - body.left.value) > 0.5f) problems += "$at: folder line at ${extra.left}, body at ${body.left}"
                    if (extra.top.value < first.bottom.value) problems += "$at: folder line overlaps the result line"
                }
            }
        }
        assertTrue(problems.size.toString() + " problems:\n" + problems.joinToString("\n"), problems.isEmpty())
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsRescanAlign768Test : SettingsRescanAlignBase() {
    @Test fun linesAlignWithTheBody() = linesAlign("768x432")
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsRescanAlign576Test : SettingsRescanAlignBase() {
    @Test fun linesAlignWithTheBody() = linesAlign("576x432", narrow = true)
}

private fun rescanFocus(narrow: Boolean) = optionsAt(SettingsOption.Rescan).let { if (narrow) it.copy(column = SettingsColumn.Panel) else it }
