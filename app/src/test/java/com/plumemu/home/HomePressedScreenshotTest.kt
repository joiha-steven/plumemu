package com.plumemu.home

import com.plumemu.coreui.theme.TextSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coreui.components.LocalHeldKeys
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A games list with the confirm button held: the focused row dips to 0.97 and its fill takes a step toward
 * the text colour, next to the pressed "A Play" key cap (design/rules.md > Shapes and motion > Pressed).
 * A row presses only for a press that began while it was focused, so the shot puts A down after the first
 * frame and lets the dip finish on the test clock. One class per size (the qualifiers fix it), both presets in each.
 */
abstract class HomePressedShotBase(
    private val size: ReferenceShots.Size,
) {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun shot(preset: ReferenceShots.Preset) {
        val held = mutableStateOf<Set<String>>(emptySet())
        compose.setContent {
            PlumTheme(preset.settings.copy(textSize = TextSize.Medium)) {
                CompositionLocalProvider(LocalHeldKeys provides held) { HomeAt(FakeHomeContent.board(), BoardGamesFocused) }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            held.value = setOf("A")
            Snapshot.sendApplyNotifications()
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(300)
        compose.onRoot().captureRoboImage("${ReferenceShots.OUTPUT_DIR}/${ReferenceShots.fileName("home_list_row_pressed", size, preset)}")
    }

    @Test
    fun graphite() = shot(ReferenceShots.Preset.Graphite)

    @Test
    fun paper() = shot(ReferenceShots.Preset.Paper)
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomePressedScreenshot768Test : HomePressedShotBase(ReferenceShots.Size.Wide)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class HomePressedScreenshot576Test : HomePressedShotBase(ReferenceShots.Size.Narrow)
