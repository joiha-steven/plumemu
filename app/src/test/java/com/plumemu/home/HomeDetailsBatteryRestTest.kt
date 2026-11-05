package com.plumemu.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.battery.BatteryPace
import com.plumemu.battery.BatteryPaceNow
import com.plumemu.battery.LocalBatteryPaceNow
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.game.EmulatorToStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The battery's time changes while a game rests (the level falls): no cell of the details box moves, whatever the time says, on a long
 * emulator name that sits on a row of its own.
 */
abstract class HomeDetailsBatteryRestBase {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun texts(node: SemanticsNode): List<SemanticsNode> = (if (SemanticsProperties.Text in node.config) listOf(node) else emptyList()) + node.children.flatMap(::texts)

    private fun places(): Map<String, Rect> = texts(compose.onNodeWithTag(PANEL, useUnmergedTree = true).fetchSemanticsNode()).associate { it.config[SemanticsProperties.Text].joinToString("") to it.boundsInRoot }

    @Test
    fun aTimeThatChangesWhileRestingMovesNoCell() {
        val reading = mutableStateOf<BatteryReading?>(BatteryReading(100, false))
        // The pace now follows the level: 14 h 15 min on a full charge, 1 h 1 min at 40 %.
        val reader = BatteryPaceNow { BatteryPace(if (reading.value?.percent == 100) 855.0 else 61.0, 7.0, 1_000L) }
        val details = GameDetails(year = 1994, publisher = "Nintendo", genre = "Role-Playing Game / Action RPG", players = "1", description = "", rating = 17)
        val play = ShownPlay.Read(GamePlay(2, 2057 * 60, EmulatorToStart("RetroArch: Beetle PSX HW", isInstalled = false), BatteryPace(855.0, 7.0, 1_000L)))
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Small, font = PlumFont.Jost)) {
                CompositionLocalProvider(LocalBatteryReading provides remember { reading }, LocalBatteryPaceNow provides reader) {
                    Column(Modifier.width(PlumDimens.detailsColumnWidth).testTag(PANEL)) { DetailsBlock(details, play) }
                }
            }
        }
        compose.waitForIdle()
        val before = places()
        assertTrue("the battery says its time: ${before.keys}", before.keys.any { it.contains("14") && it.contains("15") })
        compose.runOnIdle { reading.value = BatteryReading(40, false) }
        compose.waitForIdle()
        val after = places()
        val changed = after.keys - before.keys
        assertEquals("only the battery's time changed: $changed", 1, changed.size)
        val battery = changed.single()
        assertTrue("the time is lower: '$battery'", !battery.contains("14"))
        // Every label and value that is the same text stands where it stood; the battery's time keeps its cell.
        for ((text, box) in before) after[text]?.let { assertEquals("'$text' moved: $box -> $it", box.topLeft, it.topLeft) }
        val batteryBefore = before.entries.first { it.key !in after.keys }.value
        assertEquals("the battery's time stays in its cell", batteryBefore.topLeft, after.getValue(battery).topLeft)
    }

    private companion object {
        const val PANEL = "details_panel"
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeDetailsBatteryRest768Test : HomeDetailsBatteryRestBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-367dpi")
class HomeDetailsBatteryRestThorTest : HomeDetailsBatteryRestBase()
