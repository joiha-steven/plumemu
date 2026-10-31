package com.plumemu.coreui.theme

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The font weight a text mode picks follows a colour slider's preview: a drag that
 * turns light text on a dark background into dark text on a light one draws the lighter or heavier
 * weight at once, not at the lift. A move that keeps the mode rebuilds nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class PlumThemePreviewTypeTest {
    @get:Rule
    val compose = createComposeRule()

    private val preview = mutableStateOf<PlumThemeSettings?>(null)
    private val seen = mutableListOf<TextMode>()

    private fun start() {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite, colorPreview = preview) { seen += PlumTheme.type.textMode }
        }
        compose.waitForIdle()
    }

    @Test
    fun thePreviewFlippingTheTextModeChangesTheTypeAndPuttingItBackChangesItBack() {
        start()
        assertEquals(listOf(TextMode.Light), seen)

        compose.runOnIdle { preview.value = PlumThemeSettings.Paper }
        compose.waitForIdle()
        assertEquals("the dragged colour reads as dark text", TextMode.Dark, seen.last())

        compose.runOnIdle { preview.value = null }
        compose.waitForIdle()
        assertEquals(TextMode.Light, seen.last())
    }

    @Test
    fun aMoveThatKeepsTheTextModeRecomposesNothing() {
        start()
        compose.runOnIdle { preview.value = PlumThemeSettings.Graphite.copy(background = BackgroundChoice.Preset(BackgroundPreset.Black)) }
        compose.waitForIdle()
        compose.runOnIdle { preview.value = PlumThemeSettings.Graphite.copy(text = Rgb(0xE0E0F0)) }
        compose.waitForIdle()
        assertEquals("light text stayed light: the type was not rebuilt", listOf(TextMode.Light), seen)
    }
}
