package com.plumemu.settings.screen

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.plumemu.MainScreen
import com.plumemu.content.HomeState
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.components.SystemRowTags
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.LeftColumnDimens
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeCallbacks
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings in three columns (768x432, the default text size and font): the sections stay on screen while an option or a
 * panel has the focus, the open one is marked (its label one dot further in), and keys and touch work across the columns.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsColumnsNavigationTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val settings = mutableStateOf(PlumSettings(flags = SetupFlags(setupCompleted = true, homeRoleAsked = true)))
    private lateinit var router: InputRouter

    private val host = SettingsHost(versionName = TEST_VERSION, update = { settings.value = it(settings.value) }, resetAllSettings = {}, resetLibrary = {})

    private fun open() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            MainScreen(settings.value, HomeState.Ready(FakeHomeContent.board()), router, HomeCallbacks(), onRetry = {}, settingsHost = host)
        }
        send(Action.Menu)
    }

    private fun send(vararg actions: Action) {
        for (action in actions) {
            compose.runOnIdle { router.send(action) }
            compose.waitForIdle()
        }
    }

    private val first = SettingsSection.shownIn(SettingsContent())[0]
    private val second = SettingsSection.shownIn(SettingsContent())[1]

    private fun tag(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)

    private fun labelLeft(section: SettingsSection): Float =
        compose.onNode(hasTestTag(SystemRowTags.LABEL) and hasAnyAncestor(hasTestTag(SettingsTags.section(section))), useUnmergedTree = true).getUnclippedBoundsInRoot().left.value

    /** The sections column is on screen (the open section and the divider at least); every label starts at the same x whether the open section wears the dot, the slab or neither. */
    private fun assertSectionsVisible(
        open: SettingsSection,
    ) {
        tag(SettingsTags.section(open)).assertIsDisplayed()
        for (section in SettingsSection.shownIn(SettingsContent())) {
            // The open section's dot sits at the bar's place: every label starts at the same x, marked or not (`SectionDotTest`).
            assertEquals("$section's label start while $open is open", LeftColumnDimens.sectionLabelStart.value, labelLeft(section), 0.5f)
        }
        assertTrue(compose.onAllNodesWithTag(SettingsTags.SECTIONS_DIVIDER, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun keysMoveAcrossTheThreeColumnsAndTheSectionsStayMarked() {
        open()
        assertSectionsVisible(first)
        send(Action.Move(Direction.Right)) // into the options
        tag(SettingsTags.OPTIONS).assertIsDisplayed()
        assertSectionsVisible(first)
        send(Action.NextSystem) // R1: the next section, sections still on screen
        assertSectionsVisible(second)
        send(Action.PreviousSystem)
        assertSectionsVisible(first)
        send(Action.Back) // B: back to the sections
        assertSectionsVisible(first)
        send(Action.Move(Direction.Right), Action.Move(Direction.Left)) // Left from a plain row goes back too
        assertSectionsVisible(first)
    }

    @Test
    fun aPanelOpenedByKeysKeepsTheSectionsOnScreen() {
        open()
        tag(SettingsTags.section(SettingsSection.Appearance)).performClick()
        tag(SettingsTags.section(SettingsSection.Appearance)).performClick()
        tag(SettingsTags.option(SettingsOption.Accent)).performClick() // its options, on Accent color
        send(Action.Move(Direction.Right)) // into Accent color's panel
        assertSectionsVisible(SettingsSection.Appearance)
        send(Action.Back)
        assertSectionsVisible(SettingsSection.Appearance)
    }

    @Test
    fun aTapOnAVisibleSectionOpensItWhileAnOptionIsFocused() {
        open()
        tag(SettingsTags.section(SettingsSection.Appearance)).performClick()
        tag(SettingsTags.section(SettingsSection.Appearance)).performClick() // the second tap enters its options
        tag(SettingsTags.option(SettingsOption.Accent)).performClick() // an option is focused
        assertSectionsVisible(SettingsSection.Appearance)
        tag(SettingsTags.section(SettingsSection.Advanced)).performScrollTo().performClick() // the sections scroll at the default size
        compose.waitForIdle()
        assertSectionsVisible(SettingsSection.Advanced) // opened, the cursor on it (as a tap on a section does with two columns)
        tag(SettingsTags.option(SettingsOption.Animations)).assertIsDisplayed()
        tag(SettingsTags.section(SettingsSection.Advanced)).performClick() // the second tap enters it
        compose.waitForIdle()
        assertSectionsVisible(SettingsSection.Advanced)
    }

    @Test
    fun aTapOnAPanelItemStillActsWithTheSectionsOnScreen() {
        open()
        tag(SettingsTags.section(SettingsSection.Appearance)).performClick()
        tag(SettingsTags.section(SettingsSection.Appearance)).performClick()
        tag(SettingsTags.value(SettingsOption.Colors)).performClick()
        compose.waitForIdle()
        repeat(2) {
            tag(SettingsTags.panelItem(COLORS_BACKGROUND_ITEM)).performScrollTo().performClick()
            compose.waitForIdle()
        }
        tag(SettingsTags.panelItem(0)).performClick()
        compose.waitForIdle()
        assertEquals(ColorSetting.Preset("Graphite"), settings.value.appearance.background)
        assertSectionsVisible(SettingsSection.Appearance)
    }
}
