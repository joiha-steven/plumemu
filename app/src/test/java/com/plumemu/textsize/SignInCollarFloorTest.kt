package com.plumemu.textsize

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.plumemu.account.SignInItem
import com.plumemu.account.SignInTags
import com.plumemu.account.SignInTypingShots
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.ButtonDimens
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.screen.SignInTypingSettings
import com.plumemu.setup.SetupSignInTypingAt
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * With Plumemu's tray up on the shortest screen (667 x 375), Sign in's collar hangs over the gap under the password field so the
 * face keeps its place, but never closer than `signInCollarFloor` to the field: the drawn collar top, worked out from the
 * button's box (a collar kept inside the box would be the full collar high), stays that far from the field's bottom.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w667dp-h375dp-land-xhdpi")
class SignInCollarFloorTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun check(
        name: String,
        screen: @Composable () -> Unit,
    ) {
        var font by mutableStateOf(PlumFont.Sans)
        var density = Density(1f)
        var capHeight = 0.dp
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Largest, font = font)) {
                density = LocalDensity.current
                capHeight = PlumTheme.layout.setupButtonHeight
                screen()
            }
        }
        val problems = mutableListOf<String>()
        for (each in PlumFont.entries) {
            font = each
            compose.waitForIdle()
            val field = compose.onNodeWithTag(SignInTags.PASSWORD, useUnmergedTree = true).getUnclippedBoundsInRoot()
            val button = compose.onNodeWithTag(SignInTags.item(SignInItem.SignIn), useUnmergedTree = true).getUnclippedBoundsInRoot()
            // The box is the cap and the small collar's top and bottom (the tray is up), less what hangs above it.
            val hang = capHeight + CollarSize.Key.topPadding + CollarSize.Key.bottomPadding - (button.bottom - button.top)
            val gap = (button.top - hang) - field.bottom
            if (gap < ButtonDimens.signInCollarFloor - SLACK) problems += "$name, ${each.name}: the button's collar is ${gap.value} dp under the field (hang ${hang.value})"
            assertTrue("$name, ${each.name}: the whole small collar top hangs, so the face keeps its place (hang ${hang.value})", kotlin.math.abs((hang - CollarSize.Key.topPadding).value) <= SLACK.value)
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun setupPassword() = check("setup_password") { SetupSignInTypingAt(SignInTypingShots.State.Password) }

    @Test
    fun settingsPassword() = check("settings_password") { SignInTypingSettings(SignInTypingShots.State.Password) }

    private companion object {
        val SLACK = 0.5.dp
    }
}
