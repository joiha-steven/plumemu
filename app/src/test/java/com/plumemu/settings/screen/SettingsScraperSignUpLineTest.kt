package com.plumemu.settings.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import com.plumemu.account.AccountShots
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The ScreenScraper sign-in form says where to get the free account; the RetroAchievements form keeps its own lines. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsScraperSignUpLineTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Test
    fun scraperFormSaysWhereToGetTheAccount() {
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { SignInFormAt(AccountShots.State.Empty) } }
        compose.onNodeWithText("No account yet? Make a free one at screenscraper.fr.", useUnmergedTree = true).assertIsDisplayed()
    }
}
