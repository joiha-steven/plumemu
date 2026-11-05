package com.plumemu.setup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.plumemu.R
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The "not your home app yet" card's two buttons both keep their words at the largest text on the narrowest screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeRoleCardFitTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun okKeepsItsWordAtLargest() {
        compose.setContent { PlumTheme(PlumThemeSettings.Paper.copy(textSize = TextSize.Largest)) { HomeRolePromptAt(showsNotYet = true) } }
        val ok = ApplicationProvider.getApplicationContext<Context>().getString(R.string.home_role_ok)
        compose.onNodeWithText(ok, useUnmergedTree = true).assertIsDisplayed()
        val button = compose.onNodeWithTag(HomeRoleTags.button(1), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val word = compose.onNodeWithText(ok, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("OK's word is inside its button: $word in $button", word.left >= button.left && word.right <= button.right)
        val card = compose.onNodeWithTag(HomeRoleTags.SCROLL, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("OK is whole inside the card, unscrolled: $button in $card", button.bottom <= card.bottom)
        assertTrue("the button is a button, not a sliver: ${button.right - button.left}", button.right - button.left >= 44.dp)
    }
}
