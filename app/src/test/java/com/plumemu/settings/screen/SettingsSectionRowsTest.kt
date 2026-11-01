package com.plumemu.settings.screen

import com.plumemu.account.AccountStatus
import com.plumemu.coredata.settings.SecondScreenSettings
import com.plumemu.secondscreen.ScreenKind
import com.plumemu.secondscreen.SecondScreen
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Settings' grouping: the sections in order and every section's fixed
 * rows in order, on a device without a second screen and on one with a built-in second screen.
 * Rows shown only sometimes (Systems, unknown folders, Undo last restore, Update available, …)
 * are left out by the content used here; their own tests cover them.
 */
internal class SettingsSectionRowsTest {
    private val builtIn = SecondScreen(4, "Screen-2", ScreenKind.BuiltIn)

    private fun content(screen: SecondScreen?) =
        SettingsContent(hasStatistics = true, hasUpdates = true, account = AccountStatus.SignedOut).withSecondScreen(screen, SecondScreenSettings())

    private fun rowsBySection(content: SettingsContent): Map<SettingsSection, List<SettingsOption>> =
        SettingsSection.shownIn(content).associateWith { it.rowsIn(content) }

    private val withoutSecondScreen: Map<SettingsSection, List<SettingsOption>> =
        linkedMapOf(
            SettingsSection.Library to
                listOf(
                    SettingsOption.RomFolders,
                    SettingsOption.Rescan,
                    SettingsOption.ScanOnStartup,
                    SettingsOption.SortGames,
                    SettingsOption.SystemOrder,
                    SettingsOption.RecentSize,
                    SettingsOption.ClearRecent,
                ),
            SettingsSection.Appearance to
                listOf(
                    SettingsOption.Language,
                    SettingsOption.Colors,
                    SettingsOption.Background,
                    SettingsOption.TextColor,
                    SettingsOption.Accent,
                    SettingsOption.Font,
                    SettingsOption.TextSize,
                    SettingsOption.TextWeight,
                    SettingsOption.EdgeFade,
                ),
            SettingsSection.HomeScreen to
                listOf(
                    SettingsOption.UseAsHome,
                    SettingsOption.ShowApps,
                    SettingsOption.ShowClock,
                    SettingsOption.ShowBattery,
                    SettingsOption.SystemInfo,
                    SettingsOption.LeftEdge,
                    SettingsOption.GridSize,
                ),
            SettingsSection.Emulators to listOf(SettingsOption.DetectEmulators),
            SettingsSection.Controls to
                listOf(
                    SettingsOption.ConfirmBottom,
                    SettingsOption.SwitchSystemWith,
                    SettingsOption.HoldToScrollFaster,
                    SettingsOption.PushRightToPlay,
                    SettingsOption.ShowButtonHints,
                    SettingsOption.Keyboard,
                    SettingsOption.Sounds,
                    SettingsOption.Vibration,
                ),
            SettingsSection.Statistics to emptyList(),
            SettingsSection.Accounts to listOf(SettingsOption.ScraperAccount, SettingsOption.RetroAchievementsAccount),
            SettingsSection.Advanced to
                listOf(
                    SettingsOption.Animations,
                    SettingsOption.Backup,
                    SettingsOption.Restore,
                    SettingsOption.ResetAll,
                    SettingsOption.ResetLibrary,
                    SettingsOption.ExportLogs,
                ),
            SettingsSection.About to
                listOf(
                    SettingsOption.Version,
                    SettingsOption.CheckForUpdates,
                    SettingsOption.CheckUpdatesDaily,
                    SettingsOption.UsageStatistics,
                    SettingsOption.Licenses,
                    SettingsOption.Privacy,
                    SettingsOption.ReportProblem,
                ),
        )

    @Test
    fun withoutASecondScreenTheSectionsAndRowsAreAsInTheSpec() {
        val shown = rowsBySection(content(null))
        assertEquals(withoutSecondScreen.keys.toList(), shown.keys.toList())
        withoutSecondScreen.forEach { (section, rows) -> assertEquals("$section", rows, shown[section]) }
    }

    @Test
    fun withABuiltInSecondScreenItsSectionFollowsHomeScreen() {
        val shown = rowsBySection(content(builtIn))
        val expected =
            withoutSecondScreen.entries.flatMap { (section, rows) ->
                listOf(section to rows) +
                    if (section == SettingsSection.HomeScreen) listOf(SettingsSection.SecondScreen to listOf(SettingsOption.SecondScreen, SettingsOption.SecondScreenInGame)) else emptyList()
            }
        assertEquals(expected.map { it.first }, shown.keys.toList())
        expected.forEach { (section, rows) -> assertEquals("$section", rows, shown[section]) }
    }

    @Test
    fun theBoxArtRegionIsNoRowButAnItemOfTheScreenScraperPanel() {
        val all = SettingsSection.entries.flatMap { it.rowsIn(content(builtIn)) }
        assertEquals(false, SettingsOption.BoxArtRegion in all)
    }
}
