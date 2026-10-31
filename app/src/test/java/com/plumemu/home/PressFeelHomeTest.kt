package com.plumemu.home

import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreui.components.GAME_ROW_DRAWN_TAG
import com.plumemu.coreui.components.LocalConfirmCap
import com.plumemu.coreui.components.LocalHeldKeys
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Holding the confirm button on the home screen's games list presses the focused row and recomposes nothing
 * (design/rules.md > Shapes and motion > Pressed): the held keys are read in a coroutine and in
 * draw and layer blocks only. Counted with Compose's composition tracer, as in [HomeRecompositionTest].
 */
@OptIn(InternalComposeTracingApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class PressFeelHomeTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val calls = mutableListOf<String>()
    private val held = mutableStateOf<Set<String>>(emptySet())

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

    /** The drawn rows' fill pixel just below the ring (a 2 dp ring sits inside the pill's edge), one per row. */
    private fun rowFills(): List<Color> {
        val rows = compose.onAllNodesWithTag(GAME_ROW_DRAWN_TAG, useUnmergedTree = true)
        val nodes = rows.fetchSemanticsNodes()
        return nodes.indices.map { index ->
            // A row of a page kept beside the shown one has no size: nothing to read.
            if (nodes[index].size.width == 0 || nodes[index].size.height == 0) return@map Color.Unspecified
            // A row clipped away entirely (outside the window) captures as an empty image: nothing to read.
            val image = runCatching { rows[index].captureToImage() }.getOrNull() ?: return@map Color.Unspecified
            image.toPixelMap()[image.width / 2, FILL_Y]
        }
    }

    private fun show(confirmCap: String) {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                CompositionLocalProvider(LocalHeldKeys provides held, LocalConfirmCap provides confirmCap) {
                    HomeAt(FakeHomeContent.board(), listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX), Action.Enter))
                }
            }
        }
        compose.waitForIdle()
    }

    private fun holdAndReadFills(key: String): List<Color> {
        compose.runOnIdle {
            held.value = setOf(key)
            Snapshot.sendApplyNotifications()
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(300)
        return rowFills()
    }

    private fun release() {
        compose.runOnIdle {
            held.value = emptySet()
            Snapshot.sendApplyNotifications()
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500)
    }

    @Test
    fun holdingAPressesOnlyTheFocusedRowRecomposesNothingAndLettingGoReturnsToRest() {
        show("A")
        val resting = rowFills()
        compose.runOnIdle { calls.clear() }
        val pressed = holdAndReadFills("A")
        val changed = resting.indices.filter { resting[it] != pressed[it] }
        assertEquals("exactly the focused row changes: $changed", 1, changed.size)
        release()
        assertEquals("and back to rest", resting, rowFills())
        assertEquals("a press and release recompose nothing: $calls", emptyList<String>(), calls)
    }

    @Test
    fun theBackButtonPressesNoRow() {
        show("A")
        val resting = rowFills()
        assertEquals("B is not the confirm button", resting, holdAndReadFills("B"))
    }

    @Test
    fun underConfirmWithBTheButtonPrintedBPressesTheFocusedRow() {
        show("B")
        val resting = rowFills()
        assertEquals("A does nothing when B confirms", resting, holdAndReadFills("A"))
        release()
        assertNotEquals("B presses the focused row", resting, holdAndReadFills("B"))
    }

    @Test
    fun aRowThatTakesTheFocusWhileAIsHeldStaysAtRestUntilAIsPressedAgain() {
        lateinit var strip: com.plumemu.coreinput.strip.StripState
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                CompositionLocalProvider(LocalHeldKeys provides held, LocalConfirmCap provides "A") {
                    HomeAt(FakeHomeContent.board(), listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX)), onState = { strip = it })
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            held.value = setOf("A")
            Snapshot.sendApplyNotifications()
            strip.dispatch(Action.Enter) // A on the system opens its games: the first row takes the focus
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(300)
        val whileHeld = rowFills()
        release()
        assertEquals("the new row did not press for the A that opened it", rowFills(), whileHeld)
        assertNotEquals("a fresh press shows", whileHeld, holdAndReadFills("A"))
    }
}

/** Below the focus pill's ring, inside its fill (the ring is 2 dp, 4 px at the test's density). */
private const val FILL_Y = 10
