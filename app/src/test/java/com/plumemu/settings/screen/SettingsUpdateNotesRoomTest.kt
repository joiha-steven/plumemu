package com.plumemu.settings.screen

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.update.CheckOutcome
import com.plumemu.update.LastCheck
import com.plumemu.update.UpdateManifest
import com.plumemu.update.UpdateView
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The narrow column beside the options at 768x432: Check now and the switch sit below the update, so its notes keep their room. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-mdpi")
class SettingsUpdateNotesRoomTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val release =
        UpdateManifest(
            41, "0.0.3-alpha", "https://plumemu-app.stevensdesk.com/update/p.apk", 9_800_000, "ab".repeat(32), 23, "2026-10-23",
            List(6) { "Folders are checked again when you come back to Plumemu." },
            null,
        )

    @Test
    fun theNotesKeepTheirRoomAndWhatIsBelowFades() {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                val focus = SettingsFocus(column = SettingsColumn.Options, section = SettingsSection.About, rememberedOption = mapOf(SettingsSection.About to 1))
                SettingsAt(focus, content = SettingsContent(update = UpdateView(LastCheck(1, CheckOutcome.Found), release)))
            }
        }
        val notes = compose.onNodeWithTag(SettingsTags.UPDATE_NOTES, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val height = notes.bottom - notes.top
        assertTrue("notes are $height high", height >= 100.dp)
        compose.onNodeWithTag(SettingsTags.UPDATE_PANEL + FADE_BOTTOM_SUFFIX, useUnmergedTree = true).assertExists()
    }
}
