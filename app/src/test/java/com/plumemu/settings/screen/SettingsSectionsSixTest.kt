package com.plumemu.settings.screen

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.test.core.app.ApplicationProvider
import com.plumemu.R
import com.plumemu.account.AccountStatus
import com.plumemu.coredata.settings.SecondScreenSettings
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.secondscreen.ScreenKind
import com.plumemu.secondscreen.SecondScreen
import com.plumemu.update.UpdateManifest
import com.plumemu.update.UpdateView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings has six sections: their order, Home screen's name, every old row still listed once, and the deep links. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsSectionsSixTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val screen = SecondScreen(4, "Screen-2", ScreenKind.BuiltIn)
    private val games = List(2) { SettingsGame(it + 1L, "Game $it", "Super Nintendo") }

    /** A handheld with games, a second screen in use, a vibrator and a signed-in ScreenScraper: every row that can be listed. */
    private val everything =
        ShotContent.library
            .copy(
                hiddenGames = games,
                unidentifiedGames = games,
                librarySystems = List(2) { LibrarySystem("system$it", "System ${'A' + it}", gameCount = it + 1) },
                account = AccountStatus.SignedIn("plumtester"),
                hasVibrator = true,
                hasStatistics = true,
                hasUpdates = true,
            ).withSecondScreen(screen, SecondScreenSettings(enabled = true))

    private val oldRows =
        listOf(
            SettingsOption.RomFolders, SettingsOption.Rescan, SettingsOption.FilesNotAdded, SettingsOption.Systems, SettingsOption.Emulators,
            SettingsOption.BoxArtSource, SettingsOption.ScraperAccount, SettingsOption.RetroAchievementsAccount,
            SettingsOption.Language, SettingsOption.Colors, SettingsOption.PageGrain, SettingsOption.TextSize, SettingsOption.Animations,
            SettingsOption.UseAsHome, SettingsOption.ShowApps, SettingsOption.SecondScreen, SettingsOption.SecondScreenInGame, SettingsOption.SecondScreenAppsOn,
            SettingsOption.ConfirmBottom, SettingsOption.SwitchSystemWith, SettingsOption.PushRightToPlay, SettingsOption.Keyboard, SettingsOption.Sounds, SettingsOption.Vibration,
            SettingsOption.Version, SettingsOption.ReportProblem, SettingsOption.BackupAndRestore, SettingsOption.Reset, SettingsOption.UsageStatistics, SettingsOption.Privacy, SettingsOption.Licenses,
        )

    @Test
    fun theSectionsAreExactlySixInOrder() {
        assertEquals(
            listOf(SettingsSection.Library, SettingsSection.Appearance, SettingsSection.HomeScreen, SettingsSection.Controls, SettingsSection.Statistics, SettingsSection.About),
            SettingsSection.entries,
        )
    }

    @Test
    fun everyRowOfTheOldTreeIsListedInExactlyOneSection() {
        for (option in oldRows) {
            val homes = SettingsSection.entries.filter { option in it.rowsIn(everything) }
            assertEquals("$option is in ${homes.map { it.name }}", 1, homes.size)
        }
    }

    private fun sectionTexts(content: SettingsContent): List<String> {
        compose.setContent { PlumTheme { SettingsAt(SettingsFocus(column = SettingsColumn.Sections, section = SettingsSection.HomeScreen), content = content) } }
        compose.waitForIdle()
        val tag = SettingsTags.section(SettingsSection.HomeScreen)
        return compose.onAllNodes(hasAnyAncestor(hasTestTag(tag)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
    }

    @Test
    fun homeScreenIsNamedScreensWhileASecondScreenIsFound() {
        val texts = sectionTexts(everything)
        assertTrue("$texts", context.getString(R.string.settings_section_screens) in texts)
        assertTrue("$texts", context.getString(R.string.settings_section_home_screen) !in texts)
    }

    @Test
    fun homeScreenKeepsItsNameWithoutASecondScreen() {
        val texts = sectionTexts(ShotContent.library)
        assertTrue("$texts", context.getString(R.string.settings_section_home_screen) in texts)
        assertTrue("$texts", context.getString(R.string.settings_section_screens) !in texts)
    }

    private fun crumbTexts(content: SettingsContent): List<String> {
        val focus = SettingsFocus(column = SettingsColumn.Options, section = SettingsSection.HomeScreen)
        compose.setContent { PlumTheme { SettingsAt(focus, content = content) } }
        compose.waitForIdle()
        return compose.onAllNodes(hasTestTag(SettingsTags.TOP_BAR) or hasAnyAncestor(hasTestTag(SettingsTags.TOP_BAR)), useUnmergedTree = true)
            .fetchSemanticsNodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
    }

    @Test
    fun theTopBarCrumbUsesTheSameName() {
        val withScreen = crumbTexts(everything)
        assertTrue("$withScreen", withScreen.any { context.getString(R.string.settings_section_screens) in it })
    }

    @Test
    fun theTopBarCrumbSaysHomeScreenWithoutASecondScreen() {
        val without = crumbTexts(ShotContent.library)
        assertTrue("$without", without.any { context.getString(R.string.settings_section_home_screen) in it })
    }

    @Test
    fun theUpdatePillLandsOnAboutVersion() {
        val found = UpdateManifest(41, "0.0.3", "https://example.com/p.apk", 1, "ab".repeat(32), 23, "2026-10-23", listOf("One."), null)
        val model = SettingsModel(NoActions).also { it.content = everything.copy(update = UpdateView(found = found)) }
        assertTrue(model.openUpdate(PlumSettingsForTests, wideGeometry))
        assertEquals(SettingsSection.About, model.focus.section)
        assertEquals(SettingsOption.Version, model.panelOption)
        assertEquals(PanelPage.Update, model.focus.page)
    }

    @Test
    fun theEmptyHomesFilesNotAddedLandsOnLibraryFilesNotAdded() {
        val model = SettingsModel(NoActions).also { it.content = SettingsContent() }
        assertTrue(model.openFilesNotAdded(PlumSettingsForTests, wideGeometry))
        assertEquals(SettingsSection.Library, model.focus.section)
        assertEquals(SettingsOption.FilesNotAdded, model.panelOption)
        assertEquals(PanelPage.FilesNotAdded, model.focus.page)
    }
}

private val PlumSettingsForTests = com.plumemu.coredata.settings.PlumSettings()
private val wideGeometry = SettingsGeometry(isNarrow = false, presetColumns = 5, defaultTextSize = com.plumemu.coredata.settings.TextSizeSetting.Medium)
