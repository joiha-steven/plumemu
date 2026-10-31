package com.plumemu.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.Rgb
import com.plumemu.coreui.theme.SystemAccents
import com.plumemu.coreui.theme.toComposeColor
import com.plumemu.settings.SettingsTheme
import com.plumemu.settings.ViewedSystem
import com.plumemu.settings.systemColors
import com.plumemu.settings.toThemeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Accent color › By system on the home screen: the accent is the colour of the system
 * in view, and a system move costs no more than it does with a fixed accent. Recompositions are counted
 * with Compose's composition tracer, as `HomeRecompositionTest` does.
 */
@OptIn(InternalComposeTracingApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeAccentBySystemTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val calls = mutableListOf<String>()

    @Before
    fun traceCompositions() {
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

    private val fixedSettings = PlumSettings()
    private val bySystemSettings = PlumSettings(appearance = fixedSettings.appearance.copy(accent = ColorSetting.BySystem(ColorSetting.Preset("Blue"))))
    private val allColors = shippedCatalog.systemColors()

    /** The accent a probe drew last: read in a draw lambda, as the focus ring is. */
    private var drawnAccent: Color = Color.Unspecified

    /** The accent a probe read while composing, one entry per composition of the probe (a flash of the fallback shows here). */
    private val seenInComposition = mutableListOf<Color>()
    private lateinit var state: StripState
    private val viewed = MutableStateFlow<String?>(null)
    private val settings = mutableStateOf(bySystemSettings)

    private fun show(
        first: PlumSettings,
        colors: Map<String, Rgb> = allColors,
    ) {
        settings.value = first
        val content = FakeHomeContent.board()
        // One per Activity, as `MainActivity` has it: a new one on every recomposition would reset the accent.
        val viewedSystem = ViewedSystem(viewed) { colors }
        compose.setContent {
            SettingsTheme(settings.value, viewedSystem) {
                Box(Modifier.fillMaxSize()) {
                    HomeAt(content, listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX)), onState = { state = it })
                    FollowShownSystem(content, state) { viewed.value = it }
                    AccentProbe()
                }
            }
        }
        settle()
    }

    /** Idle, then a drawn frame (a native-graphics test draws only when asked), so the probe's draw lambda runs. */
    private fun settle() {
        compose.waitForIdle()
        compose.onRoot().captureToImage()
    }

    /** Its own scope, so its read of the accent in composition recomposes only this probe, not the screen under test. */
    @Composable
    private fun AccentProbe() {
        val themeColors = PlumTheme.colors
        Box(Modifier.size(8.dp).drawBehind { drawnAccent = themeColors.accent })
        val composed = themeColors.accent
        SideEffect { seenInComposition += composed }
    }

    private fun move(direction: Direction) {
        compose.runOnIdle { state.dispatch(Action.Move(direction)) }
        settle()
    }

    private fun systemColor(
        id: String,
        settings: PlumSettings = bySystemSettings,
        colors: Map<String, Rgb> = allColors,
    ): Color {
        val theme = settings.toThemeSettings()
        return SystemAccents(theme.backgroundColor, colors).colorOf(id)!!.toComposeColor()
    }

    private val fallback: Color get() = bySystemSettings.toThemeSettings().accentColor.toComposeColor()

    @Test
    fun theAccentIsTheColourOfTheSystemInViewAndFollowsAMove() {
        show(bySystemSettings)
        assertEquals(systemColor("snes"), drawnAccent)
        move(Direction.Down)
        assertEquals("gba", shownSystemId(FakeHomeContent.board(), state.focus.systemIndex))
        assertEquals(systemColor("gba"), drawnAccent)
        assertTrue("the two systems have different colours", systemColor("snes") != systemColor("gba"))
    }

    @Test
    fun recentFavoritesAndAppsShowTheFallbackAccent() {
        show(bySystemSettings)
        compose.runOnIdle { state.dispatch(Action.TapItem(Column.Systems, 0)) }
        settle()
        assertEquals(fallback, drawnAccent)
        move(Direction.Down)
        assertEquals(fallback, drawnAccent)
        move(Direction.Down)
        assertEquals(fallback, drawnAccent)
        move(Direction.Down)
        assertEquals("a system again", systemColor("snes"), drawnAccent)
    }

    @Test
    fun aSystemWithNoColourShowsTheFallbackAccent() {
        show(bySystemSettings, colors = allColors - "gba")
        assertEquals(systemColor("snes"), drawnAccent)
        move(Direction.Down)
        assertEquals(fallback, drawnAccent)
    }

    @Test
    fun withBySystemOffTheAccentStaysTheOneChosen() {
        show(fixedSettings)
        val chosen = fixedSettings.toThemeSettings().accentColor.toComposeColor()
        assertEquals(chosen, drawnAccent)
        move(Direction.Down)
        assertEquals(chosen, drawnAccent)
    }

    @Test
    fun theLastViewedSystemKeepsItsColourWhenTheHomeScreenIsGone() {
        show(bySystemSettings)
        move(Direction.Down)
        assertEquals("gba", viewed.value)
        // Settings replaces the home screen: its Follow effect is cancelled and the value stays.
        assertEquals(systemColor("gba"), drawnAccent)
        compose.runOnIdle { settings.value = bySystemSettings.copy(advanced = bySystemSettings.advanced.copy(vibration = !bySystemSettings.advanced.vibration)) }
        settle()
        assertEquals(systemColor("gba"), drawnAccent)
    }

    @Test
    fun aSettingsChangeKeepsTheSystemColourInEveryFrameAfterIt() {
        show(bySystemSettings)
        move(Direction.Down)
        assertEquals(systemColor("gba"), drawnAccent)
        seenInComposition.clear()
        // A state reset by the new settings (a new ViewedSystem) would compose the fallback accent in between.
        settings.value = bySystemSettings.copy(advanced = bySystemSettings.advanced.copy(vibration = !bySystemSettings.advanced.vibration))
        settle()
        assertTrue("accents composed after the change: $seenInComposition", seenInComposition.all { it == systemColor("gba") })
        assertEquals(systemColor("gba"), drawnAccent)
    }

    @Test
    fun aSystemMoveRunsTheSameComposablesWithBySystemAsWithAFixedAccent() {
        show(fixedSettings)
        // Build the neighbouring pages first, so both legs move over built pages.
        repeat(2) {
            move(Direction.Down)
            move(Direction.Up)
        }
        calls.clear()
        move(Direction.Down)
        val fixedRan = ran()
        move(Direction.Up)

        compose.runOnIdle { settings.value = bySystemSettings }
        settle()
        repeat(2) {
            move(Direction.Down)
            move(Direction.Up)
        }
        calls.clear()
        move(Direction.Down)
        val bySystemRan = ran()
        assertEquals(systemColor("gba"), drawnAccent)
        println("fixed accent: $fixedRan\nby system:    $bySystemRan")
        assertEquals("By system recomposes what a fixed accent does, no more", fixedRan, bySystemRan)
    }

    /** Composables of the home screen and the shared components that ran, by name with how often. */
    private fun ran(): Map<String, Int> =
        calls
            .filterNot { "HomeAccentBySystemTest" in it }
            .filter { it.startsWith("com.plumemu.home.") || it.startsWith("com.plumemu.coreui.components.") || it.startsWith("com.plumemu.settings.") }
            .groupingBy { it.substringBefore(' ') }
            .eachCount()
}
