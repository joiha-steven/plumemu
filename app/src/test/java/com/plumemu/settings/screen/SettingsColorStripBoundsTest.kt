package com.plumemu.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.plumemu.coredata.settings.KeyboardSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.ColorShades
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.ShadeRole
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The colour page's parts measured on the Accent page with the cursor on Color: the title, the presets,
 * the Color row, the Shade row, the name line and the Color code row are one panel gap apart (within 1 dp);
 * the strip's left and right edges are the row-text lines of its row, the first shade starts on the same
 * line, the Shade keys are the presets' size and pitch; the knob stays inside the bar at the first and the
 * last stop; the name line carries no code. Graphite and Paper, Small and Largest, both reference widths.
 */
abstract class SettingsColorStripBoundsBase {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var colorRow = 0
    private var shadeKeys = 0
    private var model: SettingsModel? = null
    private var shownSettings: com.plumemu.coredata.settings.PlumSettings? = null
    private var shownGeometry: SettingsGeometry? = null

    @Composable
    private fun Page() {
        val base = settingsForShot()
        val settings = base.copy(controls = base.controls.copy(keyboard = KeyboardSetting.Android))
        val geometry = settingsGeometry(PlumTheme.layout)
        val made =
            remember {
                SettingsModel(NoActions, optionsAt(SettingsOption.Accent)).also { m ->
                    m.onAction(Action.Enter, settings, geometry)
                    var guard = 0
                    while (m.colourPart != ColorPart.Color && guard++ < 40) m.onAction(Action.Move(Direction.Up), settings, geometry)
                    colorRow = m.focus.panelIndex
                    shadeKeys = m.colorShades(checkNotNull(m.focus.page)).size
                }
            }
        model = made
        shownSettings = settings
        shownGeometry = geometry
        SettingsLayout(settings, made, NoTouch, TEST_VERSION, clock = { "21:40" }, battery = { "82%" })
    }

    private fun Dp.near(
        other: Dp,
        within: Float = 0.5f,
    ) = abs(value - other.value) <= within

    private fun bounds(tag: String): DpRect =
        if (tag == STRIP_LOUPE_TAG) {
            // The loupe is moved by a layer translation, which only the layout coordinates' bounds in the root include.
            val r = compose.onNodeWithTag(tag).fetchSemanticsNode().layoutInfo.coordinates.boundsInRoot()
            with(compose.density) { DpRect(r.left.toDp(), r.top.toDp(), r.right.toDp(), r.bottom.toDp()) }
        } else {
            compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        }

    @Test
    fun partsAreEvenlySpacedAndTheStripSitsOnTheRowTextLines() {
        var size by mutableStateOf(TextSize.Small)
        var theme by mutableStateOf(PlumThemeSettings.Graphite)
        compose.setContent { PlumTheme(theme.copy(textSize = size)) { Page() } }
        val failures = mutableListOf<String>()
        for ((name, each, look) in listOf(Triple("Graphite", TextSize.Small, PlumThemeSettings.Graphite), Triple("Paper", TextSize.Small, PlumThemeSettings.Paper), Triple("Graphite", TextSize.Largest, PlumThemeSettings.Graphite), Triple("Paper", TextSize.Largest, PlumThemeSettings.Paper))) {
            size = each
            theme = look
            compose.waitForIdle()
            val tag = "$name $each"
            val title = compose.onNodeWithText("Accent color", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val presets = bounds(COLOUR_PRESETS_TAG)
            val color = bounds(SettingsTags.panelItem(colorRow))
            val shade = bounds(SHADE_ROW_TAG)
            val nameText = bounds(COLOUR_NAME_TAG)
            val hex = bounds(SettingsTags.HEX_ROW)
            val parts = listOf("title" to title, "presets" to presets, "Color" to color, "Shade" to shade, "Color code" to hex)
            parts.zipWithNext().forEach { (a, b) ->
                val gap = b.second.top - a.second.bottom
                if (!gap.near(PlumDimens.settingsPanelGap, 1f)) failures += "$tag gap ${a.first} to ${b.first} is $gap, not ${PlumDimens.settingsPanelGap}"
            }
            // The shades wrap like the presets where the panel is narrow (Largest at 768): then the Shade row is a key row taller.
            val wraps = shade.bottom - shade.top > color.bottom - color.top + 1.dp
            if (!wraps && !(color.bottom - color.top).near(shade.bottom - shade.top, 1f)) failures += "$tag Color row ${color.bottom - color.top} high, Shade row ${shade.bottom - shade.top}"
            if (wraps && !(shade.bottom - shade.top - (color.bottom - color.top)).near(PlumDimens.settingsPresetSwatch + PlumDimens.settingsPresetSwatchGap, 1f)) failures += "$tag wrapped Shade row is ${shade.bottom - shade.top} high next to ${color.bottom - color.top}"

            // The strip: its edges are the row-text lines of its row.
            val strip = bounds(STRIP_BAR_TAG)
            val pad = PlumDimens.settingsPanelRowPadding
            if (!strip.left.near(color.left + pad)) failures += "$tag strip left ${strip.left} not on the row-text line ${color.left + pad}"
            if (!strip.right.near(color.right - pad)) failures += "$tag strip right ${strip.right} not on the row-text line ${color.right - pad}"
            val label = compose.onNodeWithText("Color", useUnmergedTree = true).getUnclippedBoundsInRoot()
            if (!label.left.near(strip.left)) failures += "$tag Color label ${label.left} not on the strip's left ${strip.left}"

            // The shades: the first starts on the strip's left edge; they are the presets' size and pitch.
            val first = bounds(SettingsTags.panelItem(colorRow + 1))
            if (!first.left.near(strip.left)) failures += "$tag first shade left ${first.left} not on the strip's left ${strip.left}"
            val key = bounds(SettingsTags.panelItem(0))
            for (i in 0 until shadeKeys) {
                val b = bounds(SettingsTags.panelItem(colorRow + 1 + i))
                if (!(b.right - b.left).near(PlumDimens.settingsPresetSwatch) || !(b.bottom - b.top).near(PlumDimens.settingsPresetSwatch)) failures += "$tag shade $i is ${b.right - b.left} x ${b.bottom - b.top}, not the presets' ${PlumDimens.settingsPresetSwatch}"
                if (i > 0) {
                    val before = bounds(SettingsTags.panelItem(colorRow + i))
                    val pitch = PlumDimens.settingsPresetSwatch + PlumDimens.settingsPresetSwatchGap
                    if (b.top.near(before.top)) {
                        if (!(b.left - before.left).near(pitch)) failures += "$tag shade $i pitch ${b.left - before.left}"
                    } else if (!(b.top - before.top).near(pitch) || !b.left.near(first.left)) {
                        failures += "$tag shade $i wrapped to ${b.left},${b.top}"
                    }
                }
            }
            if (!(key.right - key.left).near(PlumDimens.settingsPresetSwatch)) failures += "$tag preset key is ${key.right - key.left} wide"

            // The title row's name: it ends on the row-text line (the strip's right edge), clear of the title, and never repeats the Color code row's code.
            if (!nameText.right.near(strip.right)) failures += "$tag name right ${nameText.right} not on the row-text line ${strip.right}"
            if (nameText.left < title.right) failures += "$tag name ${nameText.left} overlaps the title, which ends at ${title.right}"
            if (nameText.top < title.top - 2.dp || nameText.bottom > title.bottom + 2.dp) failures += "$tag name $nameText not on the title's line $title"
            val nameString = compose.onNodeWithTag(COLOUR_NAME_TAG).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()
            if (nameString.contains('#')) failures += "$tag name repeats a code: $nameString"

            // The loupe at rest: inside the band, straddling the bar (taller than it), the label clear above and the row's edges clear around.
            val loupe = bounds(STRIP_LOUPE_TAG)
            if (loupe.top < strip.top - 0.5.dp || loupe.bottom > strip.bottom + 0.5.dp) failures += "$tag loupe $loupe leaves the band $strip"
            if (!((loupe.bottom - loupe.top) - PlumDimens.settingsSliderTrack >= 12.dp)) failures += "$tag loupe ${loupe.bottom - loupe.top} not clearly taller than the bar ${PlumDimens.settingsSliderTrack}"
            if (label.bottom > strip.top) failures += "$tag label ends ${label.bottom} below the band's top ${strip.top}"
            if (strip.top < color.top || strip.bottom > color.bottom) failures += "$tag band $strip leaves the Color row $color"
        }
        assertFalse("the name line was never read", model == null)
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun theKnobStaysInsideTheBarAtTheFirstAndTheLastStop() {
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { Page() } }
        compose.waitForIdle()
        val strip = bounds(STRIP_BAR_TAG)
        val density = compose.density
        val width = with(density) { (strip.right - strip.left).toPx() }
        val knob = with(density) { PlumDimens.settingsSliderThumb.toPx() }
        val count = ColorShades.stopCount(ShadeRole.Accent)
        val first = stopCentre(0, width, knob, count)
        val last = stopCentre(count - 1, width, knob, count)
        assertEquals("first loupe's left edge on the bar's left", 0f, first - knob / 2, 0.5f)
        assertEquals("last loupe's right edge on the bar's right", width, last + knob / 2, 0.5f)
        assertTrue("the loupe is taller than the bar", PlumDimens.settingsSliderThumb > PlumDimens.settingsSliderTrack)
        assertTrue("the band holds the loupe", PlumDimens.settingsStripBand >= PlumDimens.settingsSliderThumb)
        assertTrue("the band holds the bar", PlumDimens.settingsStripBand >= PlumDimens.settingsSliderTrack)
        // The stop the knob rests on is the stop a finger at its centre asks for, at both ends.
        assertEquals(0, stopAt(first, width, knob, count))
        assertEquals(count - 1, stopAt(last, width, knob, count))
        // And the cursor reaches those stops: Left and Right until the model stops moving.
        val m = checkNotNull(model)
        val settings = checkNotNull(shownSettings)
        val geometry = checkNotNull(shownGeometry)
        repeat(count + 2) { m.onAction(Action.Move(Direction.Right), settings, geometry) }
        assertEquals(count - 1, m.colorStop)
        repeat(count + 2) { m.onAction(Action.Move(Direction.Left), settings, geometry) }
        assertEquals(0, m.colorStop)
    }

    /** The drawn loupe is centred on its stop (within 1 px) at the first stop, a middle one and the last, and stays in the band and the row. */
    @Test
    fun theLoupeIsCentredOnItsStopAndInsideTheRowAtBothEnds() {
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { Page() } }
        compose.waitForIdle()
        val m = checkNotNull(model)
        val settings = checkNotNull(shownSettings)
        val geometry = checkNotNull(shownGeometry)
        val count = ColorShades.stopCount(ShadeRole.Accent)
        val density = compose.density
        val strip = bounds(STRIP_BAR_TAG)
        val row = bounds(SettingsTags.panelItem(colorRow))
        val width = with(density) { (strip.right - strip.left).toPx() }
        val knob = with(density) { PlumDimens.settingsSliderThumb.toPx() }
        val failures = mutableListOf<String>()
        repeat(count + 2) { m.onAction(Action.Move(Direction.Left), settings, geometry) }
        for (target in listOf(0, count / 2, count - 1)) {
            while (m.colorStop < target) m.onAction(Action.Move(Direction.Right), settings, geometry)
            compose.waitForIdle()
            val loupe = bounds(STRIP_LOUPE_TAG)
            val expected = with(density) { stopCentre(target, width, knob, count).toDp() }
            val centre = (loupe.left + loupe.right) / 2 - strip.left
            if (abs(centre.value - expected.value) > 1f / density.density) failures += "stop $target: loupe centre $centre, stop centre $expected"
            if (loupe.left < row.left || loupe.right > row.right) failures += "stop $target: loupe $loupe leaves the row $row"
            if (loupe.left < strip.left - 0.5.dp || loupe.right > strip.right + 0.5.dp) failures += "stop $target: loupe $loupe leaves the bar $strip"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** A finger's drag lifts the loupe by [PlumDimens.settingsLoupeLift] (top stays inside the row), and lifting the finger seats it again; the D-pad never lifts it. */
    @Test
    fun theLoupeLiftsOnAFingerDragAndSeatsOnLift() {
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { Page() } }
        compose.waitForIdle()
        val rest = bounds(STRIP_LOUPE_TAG)
        val row = bounds(SettingsTags.panelItem(colorRow))
        val m = checkNotNull(model)
        m.onAction(Action.Move(Direction.Right), checkNotNull(shownSettings), checkNotNull(shownGeometry))
        compose.waitForIdle()
        assertTrue("the D-pad keeps the loupe seated", bounds(STRIP_LOUPE_TAG).top.near(rest.top))
        compose.onNodeWithTag(STRIP_BAR_TAG).performTouchInput {
            down(center)
            moveBy(Offset(60f, 0f))
        }
        compose.waitForIdle()
        val lifted = bounds(STRIP_LOUPE_TAG)
        assertTrue("lifted ${rest.top - lifted.top}, not ${PlumDimens.settingsLoupeLift}", (rest.top - lifted.top).near(PlumDimens.settingsLoupeLift, 1f))
        assertTrue("the lifted loupe $lifted stays inside the row $row", lifted.top >= row.top)
        compose.onNodeWithTag(STRIP_BAR_TAG).performTouchInput { up() }
        compose.waitForIdle()
        assertTrue("seated again", bounds(STRIP_LOUPE_TAG).top.near(rest.top))
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsColorStripBounds768Test : SettingsColorStripBoundsBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsColorStripBounds576Test : SettingsColorStripBoundsBase()
