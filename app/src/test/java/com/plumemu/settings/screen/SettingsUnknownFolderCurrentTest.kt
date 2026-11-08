package com.plumemu.settings.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.R
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * An unknown folder's page marks Current the answer its line shows: the chosen system's row, and
 * never "Ignore this folder" for a system the list does not offer (the platform data not read yet).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w768dp-h432dp-land-xhdpi")
internal class SettingsUnknownFolderCurrentTest {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val halo = SettingsFolderRow(FolderKey(2, "Halo"), 0, FolderAnswer.OfSystem("x360", "Xbox 360"))

    private fun currentBadges(choices: List<FolderSystemChoice>): Int {
        val content = ShotContent.library.copy(unknownFolders = listOf(halo), folderSystemChoices = choices).let { it.copy(notAdded = notAddedItems(emptyList(), it.unknownFolders)) }
        val filesNotAdded = SettingsSection.Library.rowsIn(content).indexOf(SettingsOption.FilesNotAdded)
        val focus =
            SettingsFocus(
                column = SettingsColumn.Panel,
                section = SettingsSection.Library,
                rememberedOption = mapOf(SettingsSection.Library to filesNotAdded),
                page = PanelPage.UnknownFolder,
                panelIndex = 0,
                folderKey = halo.key,
            )
        compose.setContent { SettingsAt(focus, settingsForShot(), content) }
        compose.onNodeWithTag(SettingsTags.UNKNOWN_FOLDER_PANEL).assertIsDisplayed()
        val current = ApplicationProvider.getApplicationContext<android.content.Context>().getString(R.string.settings_badge_current)
        return compose.onAllNodesWithText(current).fetchSemanticsNodes().size
    }

    @Test
    fun anAnswerTheListDoesNotOfferMarksNothingCurrent() = assertEquals(0, currentBadges(emptyList()))

    @Test
    fun theChosenSystemIsTheOneMarkedCurrent() = assertEquals(1, currentBadges(listOf(FolderSystemChoice("x360", "Xbox 360"), FolderSystemChoice("wii", "Wii"))))
}
