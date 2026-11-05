package com.plumemu.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.DpRect
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A setup button's bounds are its collar's: the collar keeps the column's gap from the part above (at least [MIN_GAP]) and
 * its left edge is the column's start (the cards'), at 576, 768, 832 and 640 x 480, Medium and Largest.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SetupButtonCollarTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun bounds(
        tag: String,
        index: Int = 0,
    ): DpRect = compose.onAllNodesWithTag(tag, useUnmergedTree = true)[index].getUnclippedBoundsInRoot()

    private fun check(
        state: SetupUiState,
        button: SetupControl,
        aboveTag: String,
    ) {
        var size by mutableStateOf(TextSize.Small)
        compose.setContent { PlumTheme(PlumThemeSettings.Default.copy(textSize = size)) { SetupAt(state) } }
        for (each in listOf(TextSize.Small, TextSize.Largest)) {
            size = each
            compose.waitForIdle()
            val collar = bounds(SetupTags.control(button))
            val above = bounds(aboveTag)
            val gap = collar.top.value - above.bottom.value
            assertTrue("$button at $each: its collar is $gap under the part above, under $MIN_GAP", gap >= MIN_GAP - TOLERANCE)
            assertEquals("$button at $each: its collar's left edge is the column start", above.left.value, collar.left.value, 1f)
        }
    }

    private fun ready() = check(SetupFakes.ready, SetupControl.StartPlaying, SetupTags.control(SetupControl.UsageStatistics))

    private fun folderCard() = check(SetupFakes.folderChosen, SetupControl.Continue, SetupTags.control(SetupControl.AddFolder))

    @Test @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun readyAt576() = ready()

    @Test @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun readyAt768() = ready()

    @Test @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun readyAt832() = ready()

    @Test @Config(qualifiers = "w640dp-h480dp-land-xhdpi")
    fun readyAt640x480() = ready()

    @Test @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun folderCardAt576() = folderCard()

    @Test @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun folderCardAt768() = folderCard()

    @Test @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun folderCardAt832() = folderCard()

    @Test @Config(qualifiers = "w640dp-h480dp-land-xhdpi")
    fun folderCardAt640x480() = folderCard()

    private companion object {
        /** The column's normal gap between parts. */
        const val MIN_GAP = 10f
        const val TOLERANCE = 0.6f
    }
}
