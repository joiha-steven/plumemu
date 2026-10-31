package com.plumemu.settings.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import com.plumemu.account.AccountStatus
import com.plumemu.coredata.backup.BackupKind
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.graph.WayBackSet
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.secondscreen.ScreenKind
import com.plumemu.secondscreen.SecondScreen
import com.plumemu.update.CheckFailure
import com.plumemu.update.CheckOutcome
import com.plumemu.update.LastCheck
import com.plumemu.update.UpdateView
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.DateFormat
import java.util.Date

/**
 * The labels and values the settings pass renamed, as the rows show them: the
 * ScreenScraper row keeps its name signed in or out ("Sign in\u00A0»" while out), Undo restore's
 * "Copy · 120 MB", Check for updates' "Check failed · <date>", Privacy without "›", and the
 * plainer names of Find new games automatically, Show clock and Send usage statistics.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-xhdpi")
class SettingsRowWordsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val settings = PlumSettings()

    /** The value text of each of [options] with the default content, in one composition. */
    private fun valuesOf(options: List<SettingsOption>): Map<SettingsOption, String> {
        var values = emptyMap<SettingsOption, String>()
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                values = options.associateWith { (optionValue(it, settings, "0.0.3-beta") as? OptionValue.Text)?.text ?: "(not text)" }
            }
        }
        compose.waitForIdle()
        return values
    }

    /** The label and the value text [option] shows with [content]. */
    private fun rowOf(
        option: SettingsOption,
        content: SettingsContent = SettingsContent(),
    ): Pair<String, String> {
        var label = ""
        var value = ""
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                label = optionLabel(option, content, 0)
                value = (optionValue(option, settings, "0.0.3-beta", content) as? OptionValue.Text)?.text ?: "(not text)"
            }
        }
        compose.waitForIdle()
        return label to value
    }

    /** The kinds of value are told apart by their mark: "‹ X ›" cycles, "X »" opens a panel, a read-only row has none. */
    @Test
    fun cyclingValuesAndPanelValuesCarryDifferentMarks() {
        val cycling = listOf(SettingsOption.Font, SettingsOption.TextSize, SettingsOption.SortGames, SettingsOption.Keyboard, SettingsOption.SwitchSystemWith)
        for ((option, value) in valuesOf(cycling)) {
            assertEquals(option.name + ": " + value, true, value.startsWith("‹ ") && value.endsWith(" ›"))
        }
    }

    @Test
    fun panelValuesCarryTheOpeningMark() {
        val opening =
            listOf(
                SettingsOption.RomFolders, SettingsOption.Systems, SettingsOption.Backup, SettingsOption.Restore, SettingsOption.ResetAll,
                SettingsOption.ResetLibrary, SettingsOption.Licenses, SettingsOption.ReportProblem, SettingsOption.ScraperAccount,
            )
        for ((option, value) in valuesOf(opening)) {
            assertEquals(option.name + ": " + value, true, value.endsWith("\u00BB"))
            assertEquals(option.name + " has no cycling arrows", false, value.contains("‹") || value.contains("›"))
        }
    }

    @Test
    fun readOnlyRowsHaveNoMark() {
        for ((option, value) in valuesOf(listOf(SettingsOption.Version, SettingsOption.Privacy, SettingsOption.ScraperStatus))) {
            assertEquals(option.name + ": " + value, false, value.contains("»") || value.contains("›"))
        }
    }

    @Test
    fun aColourDotRowOpensWithTheOpeningMark() {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) { OptionRow(SettingsOption.Accent, optionValue(SettingsOption.Accent, settings, "0.0.3-beta"), OptionLook.Normal) }
        }
        compose.waitForIdle()
        compose.onNode(hasText("\u00A0\u00BB", substring = true)).assertExists()
    }

    @Test
    fun theScreenScraperRowKeepsItsNameAndSaysSignInWhileSignedOut() {
        assertEquals("ScreenScraper" to "Sign in\u00A0»", rowOf(SettingsOption.ScraperAccount, SettingsContent(account = AccountStatus.SignedOut)))
    }

    @Test
    fun theScreenScraperRowKeepsItsNameSignedIn() {
        val (label, value) = rowOf(SettingsOption.ScraperAccount, SettingsContent(account = AccountStatus.SignedIn("plumtester")))
        assertEquals("ScreenScraper", label)
        assertEquals("nothing short to say: the name", "plumtester\u00A0»", value)
    }

    @Test
    fun undoRestoreSaysItKeepsACopyAndItsSize() {
        val content = SettingsContent(wayBack = WayBackSet(BackupKind.Everything, restoredAtMillis = 0L, bytes = 120_000_000L))
        assertEquals("Undo restore" to "Copy · 120 MB\u00A0»", rowOf(SettingsOption.UndoRestore, content))
    }

    @Test
    fun aFailedUpdateCheckSaysCheckFailedAndWhen() {
        // Oct 22, 2026, noon UTC: a day before today, so a date rather than a time.
        val at = 1_792_670_400_000L
        val content = SettingsContent(update = UpdateView(lastCheck = LastCheck(at, CheckOutcome.Failed(CheckFailure.Offline))))
        val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(at))
        assertEquals("Check for updates" to "Check failed · $date", rowOf(SettingsOption.CheckForUpdates, content))
    }

    @Test
    fun aCheckThatFoundAReleaseSaysUpdateAvailableLikeTheRowUnderVersion() {
        val at = 1_792_670_400_000L
        val content = SettingsContent(update = UpdateView(lastCheck = LastCheck(at, CheckOutcome.Found)))
        val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(at))
        assertEquals("Check for updates" to "Update available · $date", rowOf(SettingsOption.CheckForUpdates, content))
    }

    @Test
    fun privacyShowsNoChevronBecauseANeverOpensIt() {
        assertEquals("Privacy" to "", rowOf(SettingsOption.Privacy))
    }

    @Test
    fun theRenamedSwitchesSayWhatTheyDo() {
        val renamed = listOf(SettingsOption.ScanOnStartup, SettingsOption.ShowClock, SettingsOption.UsageStatistics)
        var labels = emptyList<String>()
        compose.setContent { labels = renamed.map { optionLabel(it, SettingsContent(), 0) } }
        compose.waitForIdle()
        assertEquals(listOf("Find new games automatically", "Show clock", "Send usage statistics"), labels)
    }

    @Test
    fun showBatterysPanelMentionsTheSecondScreensClockOnlyWithASecondScreen() {
        val alsoThere = "Also on the second screen's clock."
        var content by mutableStateOf(SettingsContent())
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { SettingsAt(optionsAt(SettingsOption.ShowBattery), content = content) } }
        compose.waitForIdle()
        compose.onNodeWithText("The battery level at the top right of the screen.").assertExists()
        compose.onNodeWithText(alsoThere, substring = true).assertDoesNotExist()
        content = SettingsContent(secondScreen = SecondScreen(2, "Screen-2", ScreenKind.BuiltIn))
        compose.waitForIdle()
        compose.onNodeWithText("The battery level at the top right of the screen. $alsoThere").assertExists()
    }
}
