package com.plumemu.settings.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.plumemu.account.AccountStatus
import com.plumemu.account.RaAccountStatus
import com.plumemu.coredata.backup.BackupKind
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.graph.BackupState
import com.plumemu.graph.RestoreState
import com.plumemu.graph.WayBackSet
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.platforms.PlatformDataVersion
import com.plumemu.secondscreen.ScreenKind
import com.plumemu.secondscreen.SecondScreen
import com.plumemu.update.CheckFailure
import com.plumemu.update.CheckOutcome
import com.plumemu.update.LastCheck
import com.plumemu.update.UpdateStep
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
 * plainer names of Clock and battery and Send usage statistics.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-xhdpi")
class SettingsRowWordsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val settings = PlumSettings()

    /** The value text of each of [options] with the default content, in one composition. */
    private fun valuesOf(options: List<SettingsOption>): Map<SettingsOption, OptionValue> {
        var values = emptyMap<SettingsOption, OptionValue>()
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                values = options.associateWith { optionValue(it, settings, "0.0.3-beta") }
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
                value = optionValue(option, settings, "0.0.3-beta", content).words ?: "(not text)"
            }
        }
        compose.waitForIdle()
        return label to value
    }

    /** The kinds of value are told apart by their mark: "‹ X ›" cycles, "X »" opens a panel, a read-only row has none. */
    @Test
    fun cyclingValuesAndPanelValuesCarryDifferentMarks() {
        val cycling = listOf(SettingsOption.TextSize, SettingsOption.SortGames, SettingsOption.Keyboard, SettingsOption.SwitchSystemWith)
        for ((option, value) in valuesOf(cycling)) {
            assertEquals(option.name + ": " + value, true, value is OptionValue.Choice)
        }
    }

    @Test
    fun panelValuesCarryTheOpeningMark() {
        val opening =
            listOf(
                SettingsOption.RomFolders, SettingsOption.Systems, SettingsOption.BackupAndRestore, SettingsOption.Reset,
                SettingsOption.Updates, SettingsOption.Licenses, SettingsOption.ReportProblem, SettingsOption.ScraperAccount, SettingsOption.RetroAchievementsAccount,
            )
        for ((option, value) in valuesOf(opening)) {
            assertEquals(option.name + ": " + value, true, value is OptionValue.Opens)
        }
    }

    @Test
    fun readOnlyRowsHaveNoMark() {
        for ((option, value) in valuesOf(listOf(SettingsOption.Version, SettingsOption.Privacy, SettingsOption.ScraperStatus))) {
            assertEquals(option.name + ": " + value, true, value is OptionValue.Text)
        }
    }

    @Test
    fun aColourDotRowOpensWithTheOpeningMark() {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) { OptionRow(SettingsOption.Accent, optionValue(SettingsOption.Accent, settings, "0.0.3-beta"), OptionLook.Normal) }
        }
        compose.waitForIdle()
        compose.onNodeWithTag(SettingsTags.chevronRight(SettingsTags.value(SettingsOption.Accent)), useUnmergedTree = true).assertExists()
    }

    @Test
    fun theScreenScraperRowKeepsItsNameAndSaysSignInWhileSignedOut() {
        assertEquals("ScreenScraper" to "Sign in", rowOf(SettingsOption.ScraperAccount, SettingsContent(account = AccountStatus.SignedOut)))
    }

    @Test
    fun theRetroAchievementsRowKeepsItsNameAndSaysSignInWhileSignedOut() {
        assertEquals("RetroAchievements" to "Sign in", rowOf(SettingsOption.RetroAchievementsAccount, SettingsContent()))
    }

    @Test
    fun theRetroAchievementsRowSaysTheUserNameSignedIn() {
        assertEquals("RetroAchievements" to "Signed in as hung", rowOf(SettingsOption.RetroAchievementsAccount, SettingsContent(raAccount = RaAccountStatus.SignedIn("hung", 1234))))
    }

    @Test
    fun theRetroAchievementsRowSaysSignInAgainAfterARefusedKey() {
        assertEquals(
            "RetroAchievements" to "Sign in again",
            rowOf(SettingsOption.RetroAchievementsAccount, SettingsContent(raAccount = RaAccountStatus.SignedIn("hung", 1234, isKeyRefused = true))),
        )
    }

    @Test
    fun theScreenScraperRowKeepsItsNameSignedIn() {
        val (label, value) = rowOf(SettingsOption.ScraperAccount, SettingsContent(account = AccountStatus.SignedIn("plumtester")))
        assertEquals("ScreenScraper", label)
        assertEquals("nothing short to say: the name", "Signed in as plumtester", value)
    }

    @Test
    fun undoRestoreSaysItKeepsACopyAndItsSize() {
        val content = SettingsContent(wayBack = WayBackSet(BackupKind.Everything, restoredAtMillis = 0L, bytes = 120_000_000L))
        assertEquals("Undo restore" to "Copy · 120 MB", rowOf(SettingsOption.UndoRestore, content))
    }

    @Test
    fun aFailedUpdateCheckSaysCheckFailedAndWhen() {
        // Oct 22, 2026, noon UTC: a day before today, so a date rather than a time.
        val at = 1_792_670_400_000L
        val content = SettingsContent(update = UpdateView(lastCheck = LastCheck(at, CheckOutcome.Failed(CheckFailure.Offline))))
        val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(at))
        assertEquals("Updates" to "Check failed · $date", rowOf(SettingsOption.Updates, content))
    }

    @Test
    fun aCheckThatFoundAReleaseSaysUpdateAvailableLikeTheRowUnderVersion() {
        val at = 1_792_670_400_000L
        val content = SettingsContent(update = UpdateView(lastCheck = LastCheck(at, CheckOutcome.Found)))
        val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(at))
        assertEquals("Updates" to "Update available · $date", rowOf(SettingsOption.Updates, content))
    }

    @Test
    fun privacyShowsNoChevronBecauseANeverOpensIt() {
        assertEquals("Privacy" to "", rowOf(SettingsOption.Privacy))
    }

    @Test
    fun theRenamedSwitchesSayWhatTheyDo() {
        val renamed = listOf(SettingsOption.ClockAndBattery, SettingsOption.UsageStatistics)
        var labels = emptyList<String>()
        compose.setContent { labels = renamed.map { optionLabel(it, SettingsContent(), 0) } }
        compose.waitForIdle()
        assertEquals(listOf("Clock and battery", "Send usage statistics"), labels)
    }

    @Test
    fun versionsPanelNamesThePlatformDataOnceItIsKnown() {
        var content by mutableStateOf(SettingsContent())
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { SettingsAt(optionsAt(SettingsOption.Version), content = content) } }
        compose.waitForIdle()
        compose.onNodeWithTag(SettingsPanelTags.PLATFORM_DATA).assertDoesNotExist()
        content = SettingsContent(platformData = PlatformDataVersion(6, "0".repeat(64), "2026-11-01"))
        compose.waitForIdle()
        compose.onNodeWithTag(SettingsPanelTags.PLATFORM_DATA).assertExists()
        compose.onNodeWithText("Platform data · Version 6", substring = true).assertExists()
        compose.onNodeWithText("The list of systems and emulators Plumemu knows.", substring = true).assertExists()
    }

    @Test
    fun clockAndBatterysPanelMentionsTheSecondScreensClockOnlyWithASecondScreen() {
        val alsoThere = "The battery also shows on the second screen's clock."
        var content by mutableStateOf(SettingsContent())
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { SettingsAt(optionsAt(SettingsOption.ClockAndBattery), content = content) } }
        compose.waitForIdle()
        compose.onNodeWithText("The time and battery level at the top right of the screen.").assertExists()
        compose.onNodeWithText(alsoThere, substring = true).assertDoesNotExist()
        content = SettingsContent(secondScreen = SecondScreen(2, "Screen-2", ScreenKind.BuiltIn))
        compose.waitForIdle()
        compose.onNodeWithText("The time and battery level at the top right of the screen. $alsoThere").assertExists()
    }

    private fun valueOf(
        option: SettingsOption,
        content: SettingsContent,
    ): OptionValue {
        var value: OptionValue = OptionValue.Text("")
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { value = optionValue(option, settings, "0.0.3-beta", content) } }
        compose.waitForIdle()
        return value
    }

    /** A sign-in state says what it means: who is signed in, with a check; the rest (signed out, refused) say what to do, with none. */
    @Test
    fun aSignedInAccountSaysWhoWithACheck() {
        assertEquals(OptionValue.Opens("Signed in as plumtester", isSignedIn = true), valueOf(SettingsOption.ScraperAccount, SettingsContent(account = AccountStatus.SignedIn("plumtester"))))
    }

    @Test
    fun aSignedInRetroAchievementsAccountSaysWhoWithACheck() {
        assertEquals(OptionValue.Opens("Signed in as hung", isSignedIn = true), valueOf(SettingsOption.RetroAchievementsAccount, SettingsContent(raAccount = RaAccountStatus.SignedIn("hung", 1234))))
    }

    @Test
    fun aSignedInAccountWithNoNameSaysSignedIn() {
        assertEquals(OptionValue.Opens("Signed in", isSignedIn = true), valueOf(SettingsOption.ScraperAccount, SettingsContent(account = AccountStatus.SignedIn(""))))
    }

    @Test
    fun aRetroAchievementsAccountWithNoNameSaysSignedIn() {
        assertEquals(OptionValue.Opens("Signed in", isSignedIn = true), valueOf(SettingsOption.RetroAchievementsAccount, SettingsContent(raAccount = RaAccountStatus.SignedIn("  ", 0))))
    }

    @Test
    fun filesNotAddedCountsTheFilesWithNoFolderWaiting() {
        val files = listOf(NotAddedItem.File(1, "", "a.iso"), NotAddedItem.More(NotAddedGroup.NoSystem, null, 340))
        assertEquals(OptionValue.Opens("341"), valueOf(SettingsOption.FilesNotAdded, SettingsContent(notAdded = files)))
    }

    @Test
    fun filesNotAddedHasNoValueWithNothingLeftOut() {
        assertEquals(OptionValue.Opens(""), valueOf(SettingsOption.FilesNotAdded, SettingsContent()))
    }

    private val waitingFolder = SettingsFolderRow(FolderKey(1, "Xbox 360"), 3, FolderAnswer.Unanswered)
    private val answeredFolder = SettingsFolderRow(FolderKey(2, "Halo"), 0, FolderAnswer.OfSystem("x360", "Xbox 360"))
    private val oneFile = listOf(NotAddedItem.File(1, "", "a.iso"))

    @Test
    fun filesNotAddedSaysOneFolderWaitsBeforeAnyCount() {
        assertEquals(OptionValue.Opens("1 folder to sort"), valueOf(SettingsOption.FilesNotAdded, SettingsContent(unknownFolders = listOf(waitingFolder, answeredFolder), notAdded = oneFile)))
    }

    @Test
    fun filesNotAddedSaysHowManyFoldersWait() {
        val other = SettingsFolderRow(FolderKey(1, "Wii U"), 5, FolderAnswer.Unanswered)
        assertEquals(OptionValue.Opens("2 folders to sort"), valueOf(SettingsOption.FilesNotAdded, SettingsContent(unknownFolders = listOf(waitingFolder, other), notAdded = oneFile)))
    }

    @Test
    fun anAnsweredFolderWaitsForNothing() {
        assertEquals(OptionValue.Opens("1"), valueOf(SettingsOption.FilesNotAdded, SettingsContent(unknownFolders = listOf(answeredFolder), notAdded = oneFile)))
    }

    @Test
    fun aSignedOutAccountHasNoCheck() {
        assertEquals(OptionValue.Opens("Sign in"), valueOf(SettingsOption.ScraperAccount, SettingsContent(account = AccountStatus.SignedOut)))
    }

    @Test
    fun aRefusedAccountHasNoCheck() {
        assertEquals(OptionValue.Opens("Sign in again"), valueOf(SettingsOption.ScraperAccount, SettingsContent(account = AccountStatus.SignedIn("plumtester", isRefused = true))))
    }

    /** A word that says what runs ("Backing up…") stands alone: the chevron is for rows that wait to be opened. */
    @Test
    fun aRunningBackupRestoreUndoOrUpdateShowsItsWordWithoutTheOpeningMark() {
        val running =
            mapOf(
                SettingsOption.Backup to SettingsContent(backup = BackupState.Running(BackupKind.Everything)),
                SettingsOption.Restore to SettingsContent(restore = RestoreState.Reading()),
                SettingsOption.UndoRestore to SettingsContent(restore = RestoreState.Replacing(isUndo = true, withLibrary = false)),
                SettingsOption.Updates to SettingsContent(update = UpdateView(found = com.plumemu.update.UpdateManifest(41, "0.0.3", "https://example.com/p.apk", 1, "ab".repeat(32), 23, "2026-10-23", listOf("One."), null), step = UpdateStep.Downloading(1, 2))),
                SettingsOption.BackupAndRestore to SettingsContent(backup = BackupState.Running(BackupKind.Settings)),
            )
        var values = emptyMap<SettingsOption, OptionValue>()
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                values = running.mapValues { (option, content) -> optionValue(option, settings, "0.0.3-beta", content) }
            }
        }
        compose.waitForIdle()
        for ((option, value) in values) {
            assertEquals(option.name + ": " + value, true, value is OptionValue.Text && value.words.orEmpty().isNotEmpty())
        }
    }
}
