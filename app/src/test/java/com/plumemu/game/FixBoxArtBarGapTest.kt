package com.plumemu.game

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SlabDimens
import com.plumemu.home.BoxArtPanelFixtures
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Fix box art's rows keep the same gap from the slab's bar to the text as the other card panels (8 dp: the text column less the bar). */
abstract class FixBoxArtBarGapBase {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Test
    fun theBarIsClearOfTheText() {
        val state = BoxArtPanelFixtures.results()
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { BoxArtPanelFixtures.Screen(BoxArtPanelFixtures.panel(state)) } }
        compose.waitForIdle()
        val d = compose.density.density
        val row = compose.onNodeWithTag(GamePanelTags.row(fixBoxArtRows(state).first().key), useUnmergedTree = true)
        val slab = row.getUnclippedBoundsInRoot()
        val text = row.fetchSemanticsNode().children.first { SemanticsProperties.Text in it.config }.boundsInRoot
        val gap = text.left / d - slab.left.value - (SlabDimens.barInset.value + SlabDimens.barWidth.value)
        assertEquals("bar to text gap", 8f, gap, 0.5f)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class FixBoxArtBarGap768Test : FixBoxArtBarGapBase()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class FixBoxArtBarGap576Test : FixBoxArtBarGapBase()
