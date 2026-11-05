package com.plumemu.game

import android.content.Context
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.plumemu.R
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.BoxArtPanelFixtures
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The "Name" label of Fix box art's field ends before the sunken field's drawn edge (its bounds less the overhang
 * the well and collar are drawn past them), in every language at both reference widths.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
class FixBoxArtFieldLabelTest(
    private val locale: String,
    private val widthDp: Int,
) {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Test
    fun theLabelEndsBeforeTheFieldsEdge() {
        RuntimeEnvironment.setQualifiers("$locale-w${widthDp}dp-h432dp-land-xhdpi".trimStart('-'))
        val state = BoxArtPanelFixtures.results()
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { BoxArtPanelFixtures.Screen(BoxArtPanelFixtures.panel(state)) } }
        compose.waitForIdle()
        val label = ApplicationProvider.getApplicationContext<Context>().getString(R.string.game_fix_box_art_field_label)
        val labelBounds = compose.onAllNodesWithText(label, useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        val typed = compose.onNodeWithTag(FixNameTags.typedText(FixBoxArtTags.FIELD), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val drawnEdge = typed.left - PlumDimens.fieldInsetX - CollarSize.Field.sidePadding
        assertTrue("[$locale ${widthDp}dp] label '$label' ends at ${labelBounds.right}, the field's drawn edge is at $drawnEdge", labelBounds.right <= drawnEdge)
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0} {1}dp")
        fun cases(): List<Array<Any>> = listOf("", "es", "fr", "ja", "ko", "vi", "zh", "zh-rTW").flatMap { l -> listOf(768, 576).map { arrayOf<Any>(l, it) } }
    }
}
