package com.plumemu.textsize

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import android.content.Intent
import android.os.BatteryManager
import com.plumemu.battery.BATTERY_LINE_SAMPLES
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.coreui.theme.WidthClass
import com.plumemu.home.FakePlayFacts
import com.plumemu.home.HomeAt
import com.plumemu.home.HomeTags
import com.plumemu.home.LibraryLikeContent
import com.plumemu.home.LocalHomePlayFacts
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The details block's labels (GENRE, RATING, PLAYED, LAST PLAYED, EMULATOR, BATTERY) and values never end in an ellipsis, at the six text
 * sizes on the four reference screens: the label drops its letter spacing, then wraps to a second line; a long value
 * wraps inside the box and the emulator's name takes a row of its own (the box leaves a cell out whole, never cuts one).
 */
abstract class HomeDetailsNoEllipsisBase {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var size by mutableStateOf(TextSize.Largest)

    private fun ellipsized(node: SemanticsNode): List<String> {
        // The subtitle and the fact lines are one line with an ellipsis by design; the facts box's values (the emulator's name too) are not.
        if (SemanticsProperties.TestTag in node.config && node.config[SemanticsProperties.TestTag] in DETAILS_ONE_LINE - HomeTags.DETAILS_EMULATOR) return emptyList()
        val own =
            if (SemanticsProperties.Text in node.config) {
                val layouts = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                val layout = layouts.firstOrNull()
                if (layout != null && (0 until layout.lineCount).any { layout.isLineEllipsized(it) }) {
                    listOf(node.config[SemanticsProperties.Text].joinToString())
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }
        return own + node.children.flatMap(::ellipsized)
    }

    // The system's battery broadcast is sticky; a test can only send one with this deprecated call.
    @Suppress("DEPRECATION")
    private fun battery(level: Int) {
        RuntimeEnvironment.getApplication().sendStickyBroadcast(
            Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_LEVEL, level).putExtra(BatteryManager.EXTRA_SCALE, 100),
        )
    }

    @Test
    fun noLabelOrValueEndsInAnEllipsis() {
        // The longest real time on a charge ("About 14 h 15 min"), so the battery cell is in the block at every size.
        val sample = BATTERY_LINE_SAMPLES[2]
        battery(sample.level)
        var wide = false
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) {
                CompositionLocalProvider(LocalHomePlayFacts provides FakePlayFacts(pace = sample.pace)) {
                    wide = PlumTheme.layout.widthClass == WidthClass.Wide
                    val toDetailsStop = if (PlumTheme.layout.widthClass == WidthClass.Narrow) listOf(Action.Move(Direction.Right)) else emptyList()
                    HomeAt(LibraryLikeContent.of(hasBoxArt = true), LibraryLikeContent.gamesFocused(LibraryLikeContent.SUPER_METROID) + toDetailsStop)
                }
            }
        }
        val problems = mutableListOf<String>()
        var sawLabel = false
        for (eachFont in listOf(PlumFont.Jost)) {
            for (each in TextSize.entries) {
                size = each
                compose.waitForIdle()
                val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
                val details = findTagged(root, HomeTags.DETAILS) ?: run { problems += "${each.name} ${eachFont.name}: no details column"; null } ?: continue
                sawLabel = sawLabel || findTagged(details, HomeTags.DETAILS_FACTS) != null
                if (wide) {
                    val cell = findTagged(details, HomeTags.DETAILS_BATTERY)
                    val box = findTagged(details, HomeTags.DETAILS_FACTS)
                    // Placed inside the box (measured coordinates); above Medium the box may not hold it (the cover keeps its floor), and then the policy leaves it out whole.
                    val why = if (cell == null || box == null) "is not there" else notShownInBox(cell, box)
                    if (why != null && each.factor <= TextSize.Small.factor) problems += "${each.name} ${eachFont.name}: the battery cell $why"
                }
                val dbg = findTagged(details, HomeTags.DETAILS_BATTERY)?.let { "cell ${it.boundsInRoot} block ${findTagged(details, HomeTags.DETAILS_FACTS)?.boundsInRoot} details ${details.boundsInRoot} cover ${findTagged(details, HomeTags.DETAILS_COVER)?.boundsInRoot}" }
                ellipsized(details).mapTo(problems) { "${each.name} ${eachFont.name}: '$it' ends in an ellipsis $dbg" }
            }
        }
        assertTrue("the block was never drawn", sawLabel)
        assertTrue(problems.size.toString() + " problems:\n" + problems.take(30).joinToString("\n"), problems.isEmpty())
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class HomeDetailsNoEllipsis576Test : HomeDetailsNoEllipsisBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h480dp-land-xhdpi")
class HomeDetailsNoEllipsis640Test : HomeDetailsNoEllipsisBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeDetailsNoEllipsis768Test : HomeDetailsNoEllipsisBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w832dp-h468dp-land-xhdpi")
class HomeDetailsNoEllipsis832Test : HomeDetailsNoEllipsisBase()
