package com.plumemu.home

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreui.testing.ReferenceShots
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

/**
 * Game rows keep a 40 dp touch area: a row is drawn 38 dp high (39.6 at Large) and
 * the 2 dp gap under it is part of its tap area, so a tap in the gap picks the row above it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class GameRowTouchAreaTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Test
    fun aTapInTheGapUnderARowPicksThatRowAndEveryRowIsAtLeast40Tall() {
        var size by mutableStateOf(TextSize.Small)
        val actions = mutableListOf<Action>()
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) { HomeAt(FakeHomeContent.board(), BoardGamesFocused, onAction = { actions += it }) } }
        val density = ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.density
        // Drawn row heights in dp, written out: 38 at Small and Medium (the minimum), 39.6 at Large.
        for ((each, drawnDp) in listOf(TextSize.Small to 38f, TextSize.Medium to 38f, TextSize.Large to 39.6f)) {
            size = each
            compose.waitForIdle()
            for (index in 0..1) {
                val row = compose.onNodeWithTag(HomeTags.game(index), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertTrue("${each.name} row $index tap area ${row.height / density} dp is under 40", row.height / density >= 40f - 0.05f)
                actions.clear()
                val tapY = row.top + (drawnDp + 1f) * density
                compose.onRoot().performTouchInput { click(Offset((row.left + row.right) / 2, tapY)) }
                compose.waitForIdle()
                assertEquals("${each.name}: a tap 1 dp under row $index's drawn bottom", listOf<Action>(Action.TapItem(Column.Games, index)), actions.toList())
            }
        }
    }
}
