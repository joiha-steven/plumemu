package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.settings.screen.ColorPages
import com.plumemu.settings.screen.SettingsAt
import com.plumemu.settings.screen.SettingsFocus
import com.plumemu.settings.screen.SettingsOption
import com.plumemu.settings.screen.SettingsSection
import com.plumemu.settings.screen.optionsAt
import com.plumemu.settings.screen.panelAt
import com.plumemu.settings.screen.settingsForShot
import com.plumemu.setup.SetupAt
import com.plumemu.setup.SetupFakes
import com.plumemu.coreui.testing.ReferenceShots
import androidx.compose.runtime.mutableStateOf
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings, setup and search on the 4:3 screens (640x480 and 640x432): no text reaches past the
 * screen's right edge or the right margin, and none ends under the bottom edge. Home's own states are
 * `HomeFourThreeBoundsTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class FourThreeScreensBoundsBase(
    private val screenHeight: Dp,
) {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val screenWidth = 640.dp

    /** The texts that end past the right margin (24 in) or under the screen's bottom edge, "'text' right / bottom". */
    private fun cut(show: @Composable () -> Unit): List<String> {
        compose.setContent { PlumTheme { show() } }
        compose.waitForIdle()
        val density = compose.density.density
        val found = mutableListOf<String>()

        fun walk(node: SemanticsNode) {
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString()
            val right = (node.positionInRoot.x + node.size.width) / density
            // Rows of a scrolling list lie past the bottom by design: only the clipped bottom counts.
            val bottom = node.boundsInRoot.bottom / density
            if (text != null && (right > (screenWidth - 24.dp).value + TOLERANCE || bottom > screenHeight.value + TOLERANCE)) found += "'$text' right=$right bottom=$bottom"
            node.children.forEach { walk(it) }
        }
        walk(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        return found
    }

    private fun assertWhole(
        what: String,
        show: @Composable () -> Unit,
    ) = assertEquals(what, emptyList<String>(), cut(show))

    @Test fun settingsSections() = assertWhole("sections") { SettingsAt(SettingsFocus(section = SettingsSection.Appearance)) }

    @Test fun settingsAppearanceOption() = assertWhole("Left edge") { SettingsAt(optionsAt(SettingsOption.LeftEdge), settingsForShot()) }

    @Test
    fun settingsBackgroundPanel() =
        assertWhole("Background panel") {
            val settings = settingsForShot()
            val (page, index) = ColorPages.openingFor(SettingsOption.Background, settings)
            SettingsAt(panelAt(SettingsOption.Background, page, index), settings)
        }

    @Test fun settingsAdvanced() = assertWhole("Advanced") { SettingsAt(optionsAt(SettingsOption.UsageStatistics)) }

    @Test fun setupFolder() = assertWhole("setup folder") { SetupAt(SetupFakes.chooseFolder) }

    @Test fun setupFolderChosen() = assertWhole("setup folder chosen") { SetupAt(SetupFakes.folderChosen) }

    @Test fun setupReady() = assertWhole("setup ready") { SetupAt(SetupFakes.ready) }

    @Test
    fun searchTyping() =
        assertWhole("search typing") {
            HomeAt(FakeHomeContent.withFavorites(), BoardGamesFocused, search = typingFor("super", FakeHomeContent.withFavorites()))
        }

    private companion object {
        const val TOLERANCE = 0.6f
    }
}

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h480dp-land-xhdpi")
class FourThreeScreens480BoundsTest : FourThreeScreensBoundsBase(480.dp)

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h432dp-land-xhdpi")
class FourThreeScreens432BoundsTest : FourThreeScreensBoundsBase(432.dp)

/**
 * Settings with a section focused, in Vietnamese at Large (a Retroid Pocket Nova): the options column
 * previews under the sections, and every row's label and value must end inside the right margin.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class SettingsSectionPreviewBase(
    private val size: TextSize,
) {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Test
    fun everySectionPreviewKeepsLabelsAndValuesInsideTheMargin() {
        val failures = mutableListOf<String>()
        var totalValues = 0
        val shown = mutableStateOf(SettingsSection.entries.first())
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) { SettingsAt(SettingsFocus(section = shown.value)) } }
        for (section in SettingsSection.entries) {
            compose.runOnIdle { shown.value = section }
            compose.waitForIdle()
            val density = compose.density.density
            var values = 0

            fun walk(node: SemanticsNode) {
                val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString()
                val tag = node.config.getOrNull(SemanticsProperties.TestTag)
                if (tag != null && tag.startsWith("settings_value_")) values++
                if (text != null && (node.positionInRoot.x + node.size.width) / density > 640f - 24f + 0.6f) failures += "${section.name}: '$text' right=${(node.positionInRoot.x + node.size.width) / density}"
                node.children.forEach { walk(it) }
            }
            walk(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode())
            totalValues += values
        }
        assertTrue("values were found (Statistics has none: its rows are figures)", totalValues > 10)
        assertEquals("labels and values inside the right margin", emptyList<String>(), failures.distinct().take(12))
    }
}

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "vi-w640dp-h432dp-land-xhdpi")
class SettingsSectionPreviewViLargeTest : SettingsSectionPreviewBase(TextSize.Large)

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "vi-w640dp-h480dp-land-xhdpi")
class SettingsSectionPreviewViLargestTest : SettingsSectionPreviewBase(TextSize.Largest)
