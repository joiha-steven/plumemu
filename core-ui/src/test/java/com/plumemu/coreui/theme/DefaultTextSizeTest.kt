package com.plumemu.coreui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Not picked means Large, on every screen width. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class DefaultTextSizeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aFreshInstallWithNoStoredSizeRendersAtLargesScale() {
        var seen: TextSize? = null
        var scale = 0f
        compose.setContent {
            PlumTheme(PlumThemeSettings.Default) {
                Box(Modifier.size(1.dp))
                seen = PlumTheme.type.textSize
                scale = PlumTheme.layout.textScale
            }
        }
        compose.waitForIdle()
        assertEquals(TextSize.Large, seen)
        assertEquals(TextSize.Large.heightScale, scale, 0f)
        assertEquals(1.1f, scale, 1e-6f)
    }

    @Test
    fun aPickedSizeStillWinsOverTheDefault() {
        var seen: TextSize? = null
        compose.setContent {
            PlumTheme(PlumThemeSettings.Default.copy(textSize = TextSize.Medium)) { seen = PlumTheme.type.textSize }
        }
        compose.waitForIdle()
        assertEquals(TextSize.Medium, seen)
    }
}
