package com.plumemu.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.remember
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.ColorShades
import com.plumemu.coreui.theme.PlumTheme
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
 * A finger's move on the Color strip of Text color's page: each move changes the [ColorPreview] only.
 * What recomposes is the colour page, its Color row, the Shade row with its shades, the name line and the Color
 * code row; the presets, the bars and the option rows read their colours while drawing and stay out.
 * The names of what ran are printed so a report can quote them.
 */
@OptIn(InternalComposeTracingApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsColorPageRecompositionTest {
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
                    calls += info.substringBefore(" (")
                }

                override fun traceEventEnd() = Unit
            },
        )
    }

    @After
    fun stopTracing() = Composer.setTracer(null)

    private lateinit var model: SettingsModel
    private lateinit var geometry: SettingsGeometry
    private val stored = PlumSettings(appearance = AppearanceSettings(background = ColorSetting.Preset("Graphite")), flags = SetupFlags(setupCompleted = true))

    @Composable
    private fun Page(preview: ColorPreview) {
        val actions = remember { object : SettingsActions by NoActions { override val colorPreview: ColorPreview get() = preview } }
        geometry = settingsGeometry(PlumTheme.layout)
        model = remember { SettingsModel(actions, optionsAt(SettingsOption.TextColor)).also { m -> m.onAction(Action.Enter, stored, geometry) } }
        SettingsLayout(stored, model, NoTouch, TEST_VERSION, clock = { "21:40" }, battery = { "82%" })
    }

    @Test
    fun aStripMoveRecomposesOnlyTheColourPagePartsThatShowTheColour() {
        val preview = ColorPreview()
        compose.setContent { SettingsTheme(stored, null, preview) { Page(preview) } }
        compose.waitForIdle()
        fun move(stop: Int): List<String> {
            calls.clear()
            compose.runOnIdle { model.onTouchStop(stop, stored, geometry, drag = true) }
            compose.waitForIdle()
            return calls.toList()
        }
        move(4)
        val first = move(5)
        val second = move(6)
        val names = first.groupingBy { it }.eachCount()
        println("RECOMPOSITIONS per move on the colour page: ${first.size} $names")
        assertEquals("every move costs the same", first.size, second.size)
        assertTrue("the preview is on: ${preview.appearance}", preview.appearance != null)
        // What reads a colour while drawing stays out: the presets, the top bar with its emphasised place, the option rows' dots, the panel title.
        listOf("TopBar", "OptionValueView", "PanelTitle", "OptionRow", "Hint", "PresetKeys").forEach { name ->
            assertEquals("$name does not recompose on a move: $names", 0, names.keys.count { it.substringAfterLast('.') == name })
        }
        assertEquals("the page itself reads neither the colour nor the shades: $names", 0, names.keys.count { it.substringAfterLast('.') == "ColourPage" })
        // The knob, the bar and the row's label read their colours while drawing, so the Color row is not recomposed at all.
        assertEquals("the Color row follows the knob in draw: $names", 0, names.keys.count { it.substringAfterLast('.') == "ColorStripRow" })
        val swatches = names.entries.filter { it.key.substringAfterLast('.') == "Swatch" }.sumOf { it.value }
        assertTrue("only the shades' keys recompose ($swatches): $names", swatches <= ColorShades.SHADE_COUNT)
        assertTrue("a move recomposes little (${first.size}): $names", first.size < MOVE_BUDGET)
    }

    /** Moves with the D-pad: one step along the Color strip, one along the Shade keys, one along the presets. */
    private fun stepCounts(): Triple<Int, Int, Int> {
        val preview = ColorPreview()
        compose.setContent { SettingsTheme(stored, null, preview) { Page(preview) } }
        compose.waitForIdle()
        fun step(d: Direction): Pair<Int, Map<String, Int>> {
            calls.clear()
            compose.runOnIdle { model.onAction(Action.Move(d), stored, geometry) }
            compose.waitForIdle()
            return calls.size to calls.groupingBy { it }.eachCount()
        }
        // Enter put the cursor on the first preset: step along the presets first, then down to Color.
        val (preset, presetNames) = step(Direction.Right).let { step(Direction.Left).also { r -> println("PRESET step: ${r.first} ${r.second}") } }
        var guard = 0
        while (model.colourPart != ColorPart.Color && guard++ < 40) compose.runOnIdle { model.onAction(Action.Move(Direction.Down), stored, geometry) }
        compose.waitForIdle()
        step(Direction.Right)
        val (strip, stripNames) = step(Direction.Right)
        println("STRIP step: $strip $stripNames")
        while (model.colourPart != ColorPart.Shade && guard++ < 80) compose.runOnIdle { model.onAction(Action.Move(Direction.Down), stored, geometry) }
        compose.waitForIdle()
        step(Direction.Right)
        val (shade, shadeNames) = step(Direction.Right)
        println("SHADE step: $shade $shadeNames")
        return Triple(preset, strip, shade)
    }

    @Test
    fun aStripStepAndAShadeStepCostAboutWhatAPresetStepCosts() {
        val (preset, strip, shade) = stepCounts()
        println("STEPS preset=$preset strip=$strip shade=$shade")
        assertTrue("a strip step recomposes about as little as a preset step: $strip vs $preset", strip <= preset + STEP_SLACK)
        assertTrue("a shade step recomposes about as little as a preset step: $shade vs $preset", shade <= preset + STEP_SLACK)
    }

    private companion object {
        const val STEP_SLACK = 10
        /** Per move on the colour page: 169 measured (the Color row, the Shade row with its five keys, the name line, the Color code row; the page itself no longer reads the colour or the shades), budget about 20 % above. The "+" page's sliders cost about 100 against 150. */
        const val MOVE_BUDGET = 200
    }
}
