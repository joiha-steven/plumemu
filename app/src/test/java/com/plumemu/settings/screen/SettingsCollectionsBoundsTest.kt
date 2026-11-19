package com.plumemu.settings.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.textsize.FitAllowance
import com.plumemu.textsize.findTagged
import com.plumemu.textsize.notFullyShown
import com.plumemu.textsize.textProblems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings › Library › Collections measured, not looked at: no text cut, clipped or overlapped, the focused item whole, in Graphite and
 * Paper at the two reference sizes; and the row's value says how many there are.
 */
abstract class SettingsCollectionsBoundsBase(private val widthDp: Int) {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun measure(what: String, focus: SettingsFocus, content: SettingsContent, focused: Int? = null) {
        var theme by mutableStateOf(PlumThemeSettings.Graphite)
        compose.setContent { PlumTheme(theme) { SettingsAt(focus, content = content) } }
        for (each in listOf(PlumThemeSettings.Graphite, PlumThemeSettings.Paper)) {
            theme = each
            compose.waitForIdle()
            val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
            val allow = FitAllowance(setOf(SettingsTags.OPTION_LIST, SettingsTags.SECTIONS, SettingsTags.PANEL_LIST), isLargest = false)
            assertEquals("$what $widthDp ${each.background}: text problems", emptyList<String>(), textProblems(root, allow))
            focused?.let { index ->
                val item = checkNotNull(findTagged(root, SettingsTags.panelItem(index))) { "$what: the focused item is not drawn" }
                assertNull("$what: the focused item", notFullyShown(item, root))
            }
        }
    }

    @Test fun list() = measure("list", SettingsCollectionsShotsBase.list, SettingsCollectionsShotsBase.content, focused = 1)

    @Test fun item() = measure("item", SettingsCollectionsShotsBase.item, SettingsCollectionsShotsBase.content, focused = 1)

    @Test fun delete() = measure("delete", SettingsCollectionsShotsBase.delete, SettingsCollectionsShotsBase.content, focused = SettingsModel.CONFIRM_CANCEL)

    @Test fun libraryRow() = measure("library row", SettingsCollectionsShotsBase.row, SettingsCollectionsShotsBase.content)

    private fun valueText(content: SettingsContent): String {
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { SettingsAt(SettingsCollectionsShotsBase.row, content = content) } }
        compose.waitForIdle()
        val node = compose.onNodeWithTag(SettingsTags.valueText(SettingsTags.value(SettingsOption.Collections)), useUnmergedTree = true).fetchSemanticsNode()
        return node.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("")
    }

    @Test fun theRowValueCountsTheCollections() = assertEquals("4 collections", valueText(SettingsCollectionsShotsBase.content))

    @Test fun theRowValueCountsOneCollection() = assertEquals("1 collection", valueText(SettingsCollectionsShotsBase.content.copy(collections = SettingsCollectionsShotsBase.collections.take(1))))
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsCollectionsBoundsRp5Test : SettingsCollectionsBoundsBase(768)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsCollectionsBoundsNovaTest : SettingsCollectionsBoundsBase(576)
