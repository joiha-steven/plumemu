package com.plumemu.settings.screen

import com.plumemu.R
import com.plumemu.coredata.settings.BoxArtSourceSetting
import com.plumemu.account.AccountStatus
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the hint bar says A does on each kind of row: "Open" on a row
 * that opens a page or the sign-in form, "Change" on a value, a switch, a colour dot and a system's
 * emulator (their panels are choices), "Select" on an action, nothing on read-only rows (Privacy,
 * Version, the box art status) at any width.
 */
internal class SettingsHintKindsTest : SettingsModelHarness() {
    private fun hintOn(
        section: SettingsSection,
        option: SettingsOption,
    ): Int? {
        backToTheFirstSection()
        openSection(section)
        focusOption(option)
        assertEquals(option, model.focusedOption)
        return confirmHint(model)
    }

    /** [openSection] walks down only: start every look from Library in the sections column. */
    private fun backToTheFirstSection() {
        repeat(3) { if (model.focus.column != SettingsColumn.Sections) press(Action.Back) }
        repeat(SettingsSection.entries.size) { move(Direction.Up) }
        assertEquals(SettingsColumn.Sections, model.focus.column)
        assertEquals(SettingsSection.entries.first(), model.focus.section)
    }

    @Test
    fun rowsThatOpenAPageOrTheSignInSayOpen() {
        model.content = model.content.copy(account = AccountStatus.SignedOut, chosenBoxArtSource = BoxArtSourceSetting.ScreenScraper)
        assertEquals(R.string.settings_hint_open, hintOn(SettingsSection.Library, SettingsOption.RomFolders))
        for (option in listOf(SettingsOption.BoxArtSource, SettingsOption.ScraperAccount, SettingsOption.RetroAchievementsAccount)) {
            assertEquals(option.name, R.string.settings_hint_open, hintOn(SettingsSection.Accounts, option))
        }
        for (option in listOf(SettingsOption.BackupAndRestore, SettingsOption.Reset)) {
            assertEquals(option.name, R.string.settings_hint_open, hintOn(SettingsSection.Advanced, option))
        }
        assertEquals(R.string.settings_hint_open, hintOn(SettingsSection.About, SettingsOption.Licenses))
    }

    @Test
    fun theSignedInAccountRowSaysOpenToo() {
        model.content = model.content.copy(account = AccountStatus.SignedIn("plumtester"))
        assertEquals(R.string.settings_hint_open, hintOn(SettingsSection.Accounts, SettingsOption.ScraperAccount))
    }

    @Test
    fun valuesSwitchesAndChoicePanelsSayChange() {
        for (option in listOf(SettingsOption.Colors, SettingsOption.Accent)) {
            assertEquals(option.name, R.string.settings_hint_change, hintOn(SettingsSection.Appearance, option))
        }
        assertEquals(R.string.settings_hint_change, hintOn(SettingsSection.HomeScreen, SettingsOption.ClockAndBattery))
        assertEquals(R.string.settings_hint_change, hintOn(SettingsSection.Library, SettingsOption.SortGames))
        assertEquals(R.string.settings_hint_change, hintOn(SettingsSection.Advanced, SettingsOption.Animations))
    }

    @Test
    fun aSystemsEmulatorRowOpensAChoiceAndSaysChange() {
        model.content = model.content.copy(systems = listOf(SystemEmulators("snes", "Super Nintendo", null, null, emptyList())))
        openSection(SettingsSection.Emulators)
        assertEquals(SettingsOption.SystemEmulator, model.focusedOption)
        assertEquals(R.string.settings_hint_change, confirmHint(model))
    }

    @Test
    fun actionsSaySelect() {
        assertEquals(R.string.settings_hint_select, hintOn(SettingsSection.Library, SettingsOption.Rescan))
    }

    @Test
    fun readOnlyRowsHaveNoAHintOnAnyWidth() {
        // Version first: the focus only walks down, and About opens on the row it was left on.
        assertNull(hintOn(SettingsSection.About, SettingsOption.Version))
        assertNull(hintOn(SettingsSection.About, SettingsOption.Privacy))
    }

    @Test
    fun filesNotAddedSaysChooseOnlyOnAnUnknownFoldersLine() {
        val folder = FolderKey(1, "Xbox 360")
        val items =
            listOf(
                NotAddedItem.Heading(NotAddedGroup.NoSystem, null),
                NotAddedItem.Folder(folder, 3),
                NotAddedItem.File(1, "", "Mystery.iso"),
                NotAddedItem.More(NotAddedGroup.NoSystem, null, 340),
            )
        model.content = SettingsContent(unknownFolders = listOf(SettingsFolderRow(folder, 3, FolderAnswer.Unanswered)), notAdded = items)
        assertEquals(R.string.settings_hint_open, hintOn(SettingsSection.Library, SettingsOption.FilesNotAdded))
        press(Action.Enter)
        assertEquals(PanelPage.FilesNotAdded, model.focus.page)
        // The heading is no stop: the cursor opens on the line under it.
        val hints = (1..items.lastIndex).map { index ->
            if (index > 1) move(Direction.Down)
            assertEquals(index, model.focus.panelIndex)
            confirmHint(model)
        }
        assertEquals("A Choose only where A goes somewhere", listOf(R.string.settings_hint_choose, null, null), hints)
    }

    /**
     * Every row and what A says on it on a wide screen, written out by hand (not derived from the
     * row's kind), so a row given the wrong kind or left out of [opensPage] shows here.
     */
    @Test
    fun everyRowSaysWhatItsADoes() {
        val open = R.string.settings_hint_open
        val change = R.string.settings_hint_change
        val select = R.string.settings_hint_select
        val none: Int? = null
        val expected: Map<SettingsOption, Int?> =
            mapOf(
                SettingsOption.Colors to change,
                SettingsOption.Background to change,
                SettingsOption.TextColor to change,
                SettingsOption.Accent to change,
                SettingsOption.TextSize to change,
                SettingsOption.LeftEdge to change,
                SettingsOption.GridSize to change,
                SettingsOption.ConfirmBottom to change,
                SettingsOption.Animations to change,
                SettingsOption.Sounds to change,
                SettingsOption.UsageStatistics to change,
                SettingsOption.BackupAndRestore to open,
                SettingsOption.Backup to open,
                SettingsOption.Restore to open,
                SettingsOption.UndoRestore to open,
                SettingsOption.Reset to open,
                SettingsOption.ResetAll to open,
                SettingsOption.ResetLibrary to open,
                SettingsOption.ExportLogs to select,
                SettingsOption.Version to none,
                SettingsOption.Updates to open,
                SettingsOption.CheckUpdatesDaily to change,
                SettingsOption.Licenses to open,
                SettingsOption.Privacy to none,
                SettingsOption.ReportProblem to open,
                SettingsOption.Language to open,
                SettingsOption.Vibration to change,
                SettingsOption.RomFolders to open,
                SettingsOption.Rescan to select,
                SettingsOption.SortGames to change,
                SettingsOption.SystemOrder to change,
                SettingsOption.ClearRecent to open,
                SettingsOption.Systems to open,
                SettingsOption.UnidentifiedGames to open,
                SettingsOption.HiddenGames to open,
                // A choice of emulators, like Colors' panel: "Change".
                SettingsOption.SystemEmulator to change,
                SettingsOption.FilesNotAdded to open,
                SettingsOption.UnreadableFolders to open,
                SettingsOption.PushRightToPlay to change,
                SettingsOption.ShowButtonHints to change,
                SettingsOption.Keyboard to change,
                SettingsOption.SwitchSystemWith to change,
                SettingsOption.UseAsHome to select,
                SettingsOption.ShowApps to change,
                SettingsOption.ClockAndBattery to change,
                SettingsOption.SystemInfo to change,
                SettingsOption.SecondScreen to change,
                SettingsOption.SecondScreenInGame to change,
                SettingsOption.BoxArtSource to open,
                SettingsOption.ScraperAccount to open,
                SettingsOption.RetroAchievementsAccount to open,
                SettingsOption.ScraperStatus to none,
                SettingsOption.BoxArtRegion to change,
                SettingsOption.Statistic to none,
                SettingsOption.ExactPlayTime to change,
            )
        assertEquals("every row has its expected hint", SettingsOption.entries.toSet(), expected.keys)
        for ((option, hint) in expected) assertEquals(option.name, hint, rowConfirmHint(option))
    }
}
