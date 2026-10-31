package com.plumemu.textsize

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.ButtonLetters
import com.plumemu.shortcuts.PinConfirmLayout
import com.plumemu.shortcuts.PinConfirmTags
import com.plumemu.shortcuts.PinMessage
import com.plumemu.shortcuts.PinScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The pin confirm screen at every text size, as `SettingsStatisticsFitTest` checks
 * Statistics: at the six sizes in Plum Sans and at Large and Largest in Serif and Mono, no text is
 * ellipsized, clipped or on top of another (the panel may scroll), and the focused button is fully
 * on screen: a long game name with Cancel focused, and the widget refusal.
 */
abstract class PinConfirmFitBase(
    private val widthDp: Int,
    private val heightDp: Int,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun check(
        name: String,
        screen: PinScreen,
        focus: Int,
    ) {
        var size by mutableStateOf(TextSize.Largest)
        var font by mutableStateOf(PlumFont.Sans)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size, font = font)) {
                Box(Modifier.fillMaxSize().background(PlumTheme.colors.bg)) { PinConfirmLayout(screen, focus, ButtonLetters.Default, onAction = {}) }
            }
        }
        val combinations =
            TextSize.entries.map { PlumFont.Sans to it } +
                listOf(PlumFont.Serif, PlumFont.Mono).flatMap { listOf(it to TextSize.Large, it to TextSize.Largest) }
        val failures = mutableListOf<String>()
        for ((eachFont, each) in combinations) {
            font = eachFont
            size = each
            compose.waitForIdle()
            val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
            val label = "$name at ${each.name} ${eachFont.name} on ${widthDp}x$heightDp"
            textProblems(root, FitAllowance(setOf(PinConfirmTags.SCROLL), isLargest = each == TextSize.Largest)).mapTo(failures) { "$label: $it" }
            val focused = findTagged(root, PinConfirmTags.button(focus))
            if (focused == null) failures += "$label: the focused button is not drawn" else notFullyShown(focused, root)?.let { failures += "$label: the focused button $it" }
        }
        assertTrue(failures.size.toString() + " problems:\n" + failures.take(40).joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun aLongNameWithCancelFocused() =
        check("pin_long", PinScreen.Asking("The Elder Scrolls V: Skyrim Special Edition Anniversary Upgrade", "Shortcuts"), focus = 1)

    @Test
    fun theWidgetRefusal() = check("pin_widget", PinScreen.Message(PinMessage.Widget), focus = 0)
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class PinConfirmFit768Test : PinConfirmFitBase(768, 432)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-xhdpi")
class PinConfirmFit837Test : PinConfirmFitBase(837, 471)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w667dp-h375dp-land-xhdpi")
class PinConfirmFit667Test : PinConfirmFitBase(667, 375)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class PinConfirmFit576Test : PinConfirmFitBase(576, 432)
