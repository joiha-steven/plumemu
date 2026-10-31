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
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.SettingsTheme
import com.plumemu.settings.toThemeSettings
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
 * A finger drag on a slider of the "+" page (Settings › Appearance › Text color): each move changes the
 * [ColorPreview] only. What recomposes is the three slider rows (their value, spoken text and track
 * depend on the thumb) and the Color code row; the swatch grid, the bars and the option rows read
 * their colours while drawing and stay out. The names of what ran are printed so a report can quote them.
 */
@OptIn(InternalComposeTracingApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsColorPlusPageRecompositionTest {
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
        model =
            remember {
                SettingsModel(actions, optionsAt(SettingsOption.TextColor)).also { m ->
                    m.onAction(Action.Enter, stored, geometry)
                    val plus = ColorPages.presetCount(PanelPage.TextPresets, stored.toThemeSettings().backgroundColor) - 1
                    m.onTapPanelItem(plus, stored, geometry)
                    if (m.focus.page == PanelPage.TextPresets) m.onTapPanelItem(plus, stored, geometry)
                }
            }
        SettingsLayout(stored, model, NoTouch, TEST_VERSION, clock = { "21:40" }, battery = { "82%" })
    }

    @Test
    fun aHueMoveRecomposesOnlyTheSliderRowsAndTheColorCodeRow() {
        val preview = ColorPreview()
        compose.setContent { SettingsTheme(stored, null, preview) { Page(preview) } }
        compose.waitForIdle()
        val hue = ColorPages.palette(PanelPage.TextCustom, stored.toThemeSettings().backgroundColor).size
        fun move(fraction: Float): List<String> {
            calls.clear()
            compose.runOnIdle { model.onTouchSlider(hue, fraction, stored, geometry) }
            compose.waitForIdle()
            return calls.toList()
        }
        // A grey has no hue to show: give the colour some saturation first, as a text colour usually has.
        compose.runOnIdle { model.onTouchSlider(hue + 1, 0.6f, stored, geometry) }
        compose.waitForIdle()
        move(0.30f)
        val first = move(0.35f)
        val second = move(0.40f)
        val names = first.groupingBy { it }.eachCount()
        println("RECOMPOSITIONS per move on the + page: ${first.size} $names")
        assertEquals("every move costs the same", first.size, second.size)
        assertTrue("the preview is on: ${preview.appearance}", preview.appearance != null)
        // What reads a colour while drawing stays out: the swatch grid (48 swatches ran before), the top bar with its emphasised place, the option rows' dots, the panel title.
        listOf("Swatch", "TopBar", "OptionValueView", "PanelTitle", "OptionRow", "Hint").forEach { name ->
            assertEquals("$name does not recompose on a move: $names", 0, names.keys.count { it.substringAfterLast('.') == name })
        }
        assertEquals("the three slider rows follow the thumb", 3, names["com.plumemu.settings.screen.ColorSliderRow"])
        assertTrue("a move recomposes little (${first.size}): $names", first.size < MOVE_BUDGET)
    }

    private companion object {
        /** Per move on the "+" page; 511 before the swatches, bars and rows read colours while drawing, about 100 after. */
        const val MOVE_BUDGET = 150
    }
}
