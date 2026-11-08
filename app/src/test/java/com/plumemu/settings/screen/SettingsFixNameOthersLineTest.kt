package com.plumemu.settings.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.game.FixNameTags
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fix name…: when no title is close but the list still ranks some for the name, the results under
 * "No close title found" are headed "Other titles", never listed bare under the line that says none.
 * (TitleSearch.find ranks by shared word stems, suggest only keeps titles above a closeness floor.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w768dp-h432dp-land-xhdpi")
internal class SettingsFixNameOthersLineTest {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Test
    fun resultsUnderNoCloseTitleAreHeadedOtherTitles() {
        compose.setContent { NameFixTypingSettings(android = true, suggestions = emptyList()) }
        compose.onNodeWithTag(FixNameTags.NO_SUGGESTION).assertIsDisplayed()
        compose.onNodeWithTag(FixNameTags.OTHERS).assertIsDisplayed()
    }
}
