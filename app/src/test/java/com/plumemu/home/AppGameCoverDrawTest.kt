package com.plumemu.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * How often an app game's cover card is drawn: the focused one once in place and once into the focus layer, every other
 * one once; an icon (and so its colour) landing late redraws that card only.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class AppGameCoverDrawTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val draws = HashMap<String, Int>()
    private lateinit var state: StripState

    @After
    fun unhook() {
        appGameCoverDrawHook = null
    }

    /** Icons of [FakeAppIcons] except [gated]'s, which waits for [gate]. */
    private class GatedIcons(val gated: String, val gate: CompletableDeferred<Unit>) : HomeAppIcons {
        val inner = FakeAppIcons(missing = emptySet())
        override val generation: StateFlow<Int> get() = inner.generation
        override val glowDispatcher get() = inner.glowDispatcher

        override fun cached(rowId: String, sizePx: Int): ImageBitmap? = if (rowId == gated) null else inner.cached(rowId, sizePx)

        override suspend fun load(rowId: String, sizePx: Int): ImageBitmap? {
            if (rowId == gated) gate.await()
            return inner.load(rowId, sizePx)
        }
    }

    private fun show(icons: HomeAppIcons) {
        appGameCoverDrawHook = { key -> draws.merge(key, 1, Int::plus) }
        compose.setContent {
            PlumTheme {
                val content = remember { appGamesBoard() }
                CompositionLocalProvider(LocalHomeAppIcons provides icons) {
                    HomeAt(content, gridActions(PORTS_ENTRY, 2), gridIds = setOf(content.entries[PORTS_ENTRY].id), onState = { state = it })
                }
            }
        }
        compose.waitForIdle()
        drawn()
    }

    /** Draws a frame (idling alone draws nothing under Robolectric). */
    private fun drawn() {
        compose.onRoot().captureToImage()
    }

    /**
     * The card's content is drawn once into its own layer; the focused card's copy in place and its larger copy in the focus
     * layer are that one recording drawn twice, so a focus move re-runs no card's drawing.
     */
    @Test
    fun aFocusMoveRunsNoCardDrawingAgainAndTheFocusedCardIsDrawnOnce() {
        show(FakeAppIcons(missing = emptySet()))
        val packages = listOf(gamePackage(20), gamePackage(21), gamePackage(22))
        assertEquals("each card's drawing ran once, the focused one too ($draws)", listOf(1, 1, 1), packages.map { draws[it] ?: 0 })
        compose.runOnIdle { state.dispatch(Action.Move(Direction.Right)) }
        compose.waitForIdle()
        drawn()
        assertEquals("the move left the drawings alone ($draws)", listOf(1, 1, 1), packages.map { draws[it] ?: 0 })
    }

    @Test
    fun anIconLandingLateRedrawsThatCardOnly() {
        val gate = CompletableDeferred<Unit>()
        show(GatedIcons(gamePackage(21), gate))
        val before = HashMap(draws)
        gate.complete(Unit)
        compose.waitForIdle()
        drawn()
        assertTrue("the late card drew again ($draws)", (draws[gamePackage(21)] ?: 0) > (before[gamePackage(21)] ?: 0))
        for (other in listOf(gamePackage(20), gamePackage(22))) assertEquals("$other unchanged", before[other], draws[other])
    }
}
