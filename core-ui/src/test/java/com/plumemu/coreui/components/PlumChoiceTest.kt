package com.plumemu.coreui.components

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class PlumChoiceTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tappingASegmentSelectsItsIndex() {
        val picked = mutableListOf<Int>()
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                PlumSegmented(listOf("List", "Grid", "Shelf"), 0, focused = false, focusedIndex = null, enabled = true) { picked += it }
            }
        }
        compose.onNodeWithText("Shelf").performClick()
        compose.onNodeWithText("Grid").performClick()
        assertEquals(listOf(2, 1), picked)
    }

    @Test
    fun anOffSegmentedIgnoresTaps() {
        val picked = mutableListOf<Int>()
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                PlumSegmented(listOf("List", "Grid"), 0, focused = false, focusedIndex = null, enabled = false) { picked += it }
            }
        }
        compose.onNodeWithText("Grid").performClick()
        assertEquals(emptyList<Int>(), picked)
    }

    @Test
    fun anOffChoiceIgnoresTaps() {
        var taps = 0
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                PlumChoice("Plumemu covers", selected = false, focused = false, enabled = false, modifier = Modifier.clickable(enabled = false) { taps++ })
            }
        }
        compose.onNodeWithText("Plumemu covers").performClick()
        assertEquals(0, taps)
    }

    @Test
    fun anOnChoiceTakesTaps() {
        var taps = 0
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                PlumChoice("Box art only", selected = false, focused = false, enabled = true, modifier = Modifier.clickable { taps++ })
            }
        }
        compose.onNodeWithText("Box art only").performClick()
        assertEquals(1, taps)
    }
}
