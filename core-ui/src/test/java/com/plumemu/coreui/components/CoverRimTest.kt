package com.plumemu.coreui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The rim light (docs/design/rules.md › Grid › Cover rim and glow): white at the token opacity on a dark background, none on a light one. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class CoverRimTest {
    @get:Rule
    val compose = createComposeRule()

    private fun rimOn(settings: PlumThemeSettings): Color {
        var rim = Color.Unspecified
        compose.setContent { PlumTheme(settings) { rim = rememberCoverShadow().rim } }
        compose.waitForIdle()
        return rim
    }

    @Test
    fun graphiteHasAWhiteRim() = assertEquals(Color.White.copy(alpha = PlumDimens.COVER_RIM_ALPHA_DARK), rimOn(PlumThemeSettings.Default))

    @Test
    fun paperHasNoRim() = assertEquals(Color.Transparent, rimOn(PlumThemeSettings.Paper))
}
