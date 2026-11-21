package com.plumemu.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.KeyboardSetting
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.TypedKey
import com.plumemu.coreui.components.LocalScanPulsePhase
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.coreui.theme.ColorShades
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.ShadeRole
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.BOARD_BATTERY
import com.plumemu.home.BOARD_CLOCK
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.toThemeSettings
import com.plumemu.settings.withColors
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The colour page of Background, Text color and Accent color as a person sees it: the title row with the colour's
 * name, the presets, the Color strip with its loupe, the shades and the Color code row, taken after the panel
 * scrolled the focused row into view. The model is driven by its own actions over a store-backed fake, so the
 * loupe shows the colour the cursor previews, as in use. Names: `settings_color_page_<look>_<size>_<set>[_largest]`.
 */
abstract class SettingsColorPageShotBase(
    private val size: ReferenceShots.Size,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    /** Writes land at once in [stored]; the preview is the real one. */
    private class ShotActions(initial: PlumSettings) : SettingsActions by NoActions {
        var stored by mutableStateOf(initial)
        override val colorPreview = ColorPreview()

        override fun update(transform: (PlumSettings) -> PlumSettings) {
            stored = transform(stored)
        }
    }

    /** What presses a shot makes on the model before it is taken. */
    private class Walk(
        val model: SettingsModel,
        val actions: ShotActions,
        val geometry: SettingsGeometry,
    ) {
        fun press(action: Action) = model.onAction(action, actions.stored, geometry)

        fun toPart(part: ColorPart) {
            var guard = 0
            while (model.colourPart != part && guard++ < 30) {
                val up = (model.colourPart == ColorPart.Hex) || (model.colourPart == ColorPart.Shade && part == ColorPart.Color)
                press(Action.Move(if (up && part != ColorPart.Hex) Direction.Up else Direction.Down))
            }
        }

        fun toStop(stop: Int) {
            var guard = 0
            while (model.colorStop != stop && guard++ < 40) press(Action.Move(if (model.colorStop < stop) Direction.Right else Direction.Left))
        }

        fun toEnd(direction: Direction) = repeat(8) { press(Action.Move(direction)) }
    }

    private data class Look(
        val name: String,
        val page: String,
        val set: String,
        val textSize: TextSize = TextSize.Small,
        val ownKeyboard: Boolean = false,
        val steps: Walk.() -> Unit,
    )

    private fun themeOf(look: Look): PlumThemeSettings =
        when (look.set) {
            "banana" -> setSettings(ColorSet.Banana).toThemeSettings().copy(buttonSet = ColorSet.Banana)
            "clear_grape" -> setSettings(ColorSet.ClearGrape).toThemeSettings().copy(buttonSet = ColorSet.ClearGrape)
            "paper" -> ReferenceShots.Preset.Paper.settings
            else -> ReferenceShots.Preset.Graphite.settings
        }.copy(textSize = look.textSize)

    private fun setSettings(set: ColorSet) = PlumSettings(appearance = AppearanceSettings().withColors(set), flags = SetupFlags(setupCompleted = true))

    /** [base] with the page's colour stored as one of its strip's shades, so the Shade row carries the check mark of the colour in effect. */
    private fun storedAsAShade(
        base: PlumSettings,
        page: String,
    ): PlumSettings {
        val theme = base.toThemeSettings()
        val role = if (page == "text") ShadeRole.Text else if (page == "accent") ShadeRole.Accent else ShadeRole.Background
        val now = if (page == "text") theme.textColor else if (page == "accent") theme.accentColor else theme.backgroundColor
        val (stop, i) = ColorShades.nearestStopAndShade(role, now, theme.backgroundColor)
        val shade = ColorShades.shades(role, stop, theme.backgroundColor)[i].value
        val a = base.appearance
        return base.copy(
            appearance =
                when (page) {
                    "text" -> a.copy(textColor = TextColorSetting.Custom(shade))
                    "accent" -> a.copy(accent = ColorSetting.Custom(shade))
                    else -> a.copy(background = ColorSetting.Custom(shade))
                },
        )
    }

    @Composable
    private fun ColourPageShot(look: Look) {
        val option =
            when (look.page) {
                "text" -> SettingsOption.TextColor
                "accent" -> SettingsOption.Accent
                else -> SettingsOption.Background
            }
        val base =
            when (look.set) {
                "banana" -> setSettings(ColorSet.Banana)
                "clear_grape" -> setSettings(ColorSet.ClearGrape)
                else -> settingsForShot()
            }
        val withShade = if (look.set == "graphite" || look.set == "paper") storedAsAShade(base, look.page) else base
        val start = if (look.ownKeyboard) withShade else withShade.copy(controls = withShade.controls.copy(keyboard = KeyboardSetting.Android))
        val geometry = settingsGeometry(PlumTheme.layout)
        val actions = remember { ShotActions(if (look.name == "text_blocked") start.copy(appearance = start.appearance.copy(background = ColorSetting.Custom(BLOCKED_GREY))) else start) }
        val model =
            remember {
                SettingsModel(actions, optionsAt(option)).also { model ->
                    val walk = Walk(model, actions, geometry)
                    walk.press(Action.Enter)
                    walk.(look.steps)()
                }
            }
        CompositionLocalProvider(LocalScanPulsePhase provides 0.5f) {
            SettingsLayout(actions.stored, model, NoTouch, TEST_VERSION, clock = { BOARD_CLOCK }, battery = { BOARD_BATTERY })
        }
    }

    /** [pocket6] shots are taken at 832x468 only (the other sizes' classes skip them, and the 832 class skips the rest). */
    private fun shoot(
        looks: List<Look>,
        pocket6: Boolean = false,
    ) {
        if ((size == ReferenceShots.Size.Pocket6) != pocket6) return
        var look by mutableStateOf(looks.first())
        compose.setContent { PlumTheme(themeOf(look)) { key(look) { ColourPageShot(look) } } }
        for (each in looks) {
            look = each
            compose.waitForIdle()
            val name = "settings_color_page_${each.name}_${size}_${each.set}" + if (each.textSize == TextSize.Small) "" else "_${each.textSize.name.lowercase()}"
            compose.onRoot().captureRoboImage("${ReferenceShots.OUTPUT_DIR}/$name.png")
        }
    }

    private fun on(
        name: String,
        page: String,
        sets: List<String>,
        textSize: TextSize = TextSize.Small,
        steps: Walk.() -> Unit,
    ) = sets.map { Look("${page}_$name", page, it, textSize, steps = steps) }

    private val bothSets = listOf("graphite", "paper")
    private val allSets = listOf("graphite", "paper", "banana", "clear_grape")

    @Test
    fun accentOnColorAndShade() =
        shoot(
            on("color", "accent", allSets) { toPart(ColorPart.Color) } +
                on("shade", "accent", allSets) { toPart(ColorPart.Shade) },
        )

    @Test
    fun backgroundAndText() =
        shoot(
            on("grey", "background", bothSets) { toPart(ColorPart.Color); toStop(0) } +
                on("hue", "background", bothSets) { toPart(ColorPart.Color); toStop(HUE_STOP) } +
                on("shade", "text", bothSets) { toPart(ColorPart.Shade) },
        )

    @Test
    fun codeRowAndTyping() =
        shoot(
            on("code", "accent", listOf("graphite")) { toPart(ColorPart.Hex) } +
                listOf(
                    Look("accent_typing", "accent", "graphite", ownKeyboard = true) {
                        toPart(ColorPart.Hex)
                        press(Action.Enter)
                        repeat(8) { press(Action.Typed(TypedKey.Backspace)) }
                        press(Action.Typed(TypedKey.Text("1a2b")))
                    },
                    Look("accent_refused", "accent", "graphite", ownKeyboard = true) {
                        toPart(ColorPart.Hex)
                        press(Action.Enter)
                        repeat(8) { press(Action.Typed(TypedKey.Backspace)) }
                        press(Action.Typed(TypedKey.Text(REFUSED_CODE)))
                        press(Action.Menu)
                    },
                ),
        )

    @Test
    fun bySystemAndBlockedText() =
        shoot(
            on("by_system", "accent", listOf("graphite")) {
                var guard = 0
                // An own colour opens the page under the presets: up to them first.
                while (model.colourPart != ColorPart.Preset && guard++ < 30) press(Action.Move(Direction.Up))
                guard = 0
                while (model.focus.panelIndex != ColorPages.bySystemIndex && guard++ < 30) press(Action.Move(Direction.Right))
            } + listOf(Look("text_blocked", "text", "graphite") {}),
        )

    @Test
    fun largestText() =
        shoot(
            listOf("graphite", "paper").flatMap { set ->
                listOf(ColorPart.Color, ColorPart.Shade).map { part -> Look("accent_${part.name.lowercase()}", "accent", set, TextSize.Largest) { toPart(part) } }
            },
        )

    /** Large text takes three columns, Larger two: the page reaches the right margin in both. */
    @Test
    fun pocket6AccentOnColor() =
        shoot(listOf(TextSize.Large, TextSize.Larger).map { Look("accent_color", "accent", "graphite", it) { toPart(ColorPart.Color) } }, pocket6 = true)

    /** The loupe at the grey stop, yellow, a pale shade and a near-black one, on each set (Background: its shades run from pale to near-black). */
    @Test
    fun loupeOnEverySet() =
        shoot(
            allSets.flatMap { set ->
                listOf(
                    Look("loupe_grey", "background", set) { toPart(ColorPart.Color); toStop(0) },
                    Look("loupe_yellow", "background", set) { toPart(ColorPart.Color); toStop(YELLOW_STOP) },
                    Look("loupe_pale", "background", set) { toPart(ColorPart.Color); toStop(YELLOW_STOP); toPart(ColorPart.Shade); toEnd(Direction.Left) },
                    Look("loupe_dark", "background", set) { toPart(ColorPart.Color); toStop(BLUE_STOP); toPart(ColorPart.Shade); toEnd(Direction.Right) },
                )
            },
        )

    private companion object {
        const val HUE_STOP = 9
        const val YELLOW_STOP = 6
        const val BLUE_STOP = 17
        const val BLOCKED_GREY = 0x777777
        const val REFUSED_CODE = "111214"
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsColorPage768ShotTest : SettingsColorPageShotBase(ReferenceShots.Size.Wide)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsColorPage576ShotTest : SettingsColorPageShotBase(ReferenceShots.Size.Narrow)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w832dp-h468dp-land-xhdpi")
class SettingsColorPage832ShotTest : SettingsColorPageShotBase(ReferenceShots.Size.Pocket6)
