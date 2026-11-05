package com.plumemu.coreui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A focus value that moves redraws the collar and recomposes nothing: it is read only while drawing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class CollarRecompositionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aMovingFocusRecomposesNothing() {
        val focus = mutableFloatStateOf(0f)
        var compositions = 0
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                compositions++
                Box(Modifier.collar(CollarSize.Control) { focus.floatValue }.size(80.dp, 38.dp))
            }
        }
        compose.waitForIdle()
        assertEquals(1, compositions)
        for (v in listOf(0.3f, 0.6f, 1f)) {
            focus.floatValue = v
            compose.waitForIdle()
        }
        assertEquals("compositions after the focus moved", 1, compositions)
    }
}
