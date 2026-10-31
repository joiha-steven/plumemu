package com.plumemu.settings.screen

import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.SettingsTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * One move of a slider drag changes only the preview overlay ([ColorPreview]) and the store is never
 * touched. The theme's colours are States read while drawing, so the Settings screen redraws and
 * recomposes almost nothing. The count is printed so a report can quote it; the test pins it under
 * [MOVE_BUDGET] and the same move twice costing the same.
 */
@OptIn(InternalComposeTracingApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsColorPreviewRecompositionTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val calls = mutableListOf<String>()

    @Before
    fun trace() {
        Composer.setTracer(
            object : CompositionTracer {
                override fun isTraceInProgress(): Boolean = true

                override fun traceEventStart(
                    key: Int,
                    dirty1: Int,
                    dirty2: Int,
                    info: String,
                ) {
                    calls += info
                }

                override fun traceEventEnd() = Unit
            },
        )
    }

    @After
    fun stopTracing() = Composer.setTracer(null)

    @Test
    fun aDragMoveRecomposesTheScreenOncePerMoveAndTheCountIsSteady() {
        val preview = ColorPreview()
        val stored = PlumSettings()
        compose.setContent {
            // As MainScreen does: the stored settings go down, the preview reaches only the theme's colours.
            SettingsTheme(stored, null, preview) { SettingsAt(SettingsFocus(), stored) }
        }
        compose.waitForIdle()
        fun move(rgb: Int): Int {
            calls.clear()
            compose.runOnIdle { preview.show(stored.appearance.copy(background = ColorSetting.Custom(rgb)), fingerDown = true) }
            compose.waitForIdle()
            return calls.size
        }
        val first = move(0x202040)
        val second = move(0x203040)
        println("RECOMPOSITIONS per drag move: $first then $second")
        assertEquals("every move costs the same", first, second)
        assertTrue("a move recomposes little: colours are read while drawing ($first)", first < MOVE_BUDGET)
    }

    /** The way it was built before (the overlay laid over the settings handed down): kept to show what the budget guards against. */
    @Test
    fun layingTheOverlayOverTheStoredSettingsRecomposesTheWholeScreen() {
        val preview = ColorPreview()
        val stored = PlumSettings()
        compose.setContent {
            val shown = preview.over(stored)
            SettingsTheme(shown) { SettingsAt(SettingsFocus(), shown) }
        }
        compose.waitForIdle()
        calls.clear()
        compose.runOnIdle { preview.show(stored.appearance.copy(background = ColorSetting.Custom(0x202040)), fingerDown = true) }
        compose.waitForIdle()
        println("RECOMPOSITIONS per drag move, overlay laid over the settings: ${calls.size}")
        assertTrue("the old way recomposes far more than the budget (${calls.size})", calls.size > MOVE_BUDGET * 4)
    }

    private companion object {
        /** Per move; 610 before the colours were read while drawing, 9 after. */
        const val MOVE_BUDGET = 50
    }
}
