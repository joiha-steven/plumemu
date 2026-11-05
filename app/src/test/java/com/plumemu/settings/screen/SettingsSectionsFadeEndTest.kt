package com.plumemu.settings.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SlabDimens
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A scrolling Settings column ends [SlabDimens.listEndRoom] above its box, and its clip cuts the rows there: the bottom fade must lie over
 * the rows' last stretch inside that clip, not below it (where it fades nothing and the cut row ends in a hard edge, its top half showing).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsSectionsFadeEndTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun check() {
        var at by mutableIntStateOf(0)
        val sections = SettingsSection.shownIn(SettingsContent())
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Large)) { SettingsAt(SettingsFocus(section = sections[at])) } }
        var faded = 0
        val problems = mutableListOf<String>()
        for (i in sections.indices) {
            at = i
            compose.waitForIdle()
            val fades = compose.onAllNodesWithTag(SettingsTags.SECTIONS + FADE_BOTTOM_SUFFIX, useUnmergedTree = true)
            if (fades.fetchSemanticsNodes().isEmpty()) continue
            faded++
            val column = compose.onNodeWithTag(SettingsTags.SECTIONS, useUnmergedTree = true).getUnclippedBoundsInRoot()
            val fade = fades[0].getUnclippedBoundsInRoot()
            val windowEnd = column.bottom.value - SlabDimens.listEndRoom.value
            if (fade.bottom.value > windowEnd + 0.5f) problems += "focus ${sections[i]}: the fade ends at ${fade.bottom.value} dp, below the clip of the rows at $windowEnd dp"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
        assertTrue("the sections scroll at Large (a bottom fade shows at some focus)", faded > 0)
    }

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun at832x468() = check()

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun at768x432() = check()

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun at576x432() = check()

    @Test
    @Config(qualifiers = "w640dp-h480dp-land-xhdpi")
    fun at640x480() = check()
}
