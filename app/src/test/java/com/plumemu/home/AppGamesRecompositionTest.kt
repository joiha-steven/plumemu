package com.plumemu.home

import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.CompositionLocalProvider
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
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
 * A focus move across app-game icons (the Android games grid, and the app-game cells of the Ports grid) recomposes no tile
 * and loads no icon, as in the Apps grid ([HomeRecompositionTest]; that file is at its line limit).
 */
@OptIn(InternalComposeTracingApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class AppGamesRecompositionTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val calls = mutableListOf<String>()

    @Before
    fun trace() {
        Composer.setTracer(
            object : CompositionTracer {
                override fun isTraceInProgress(): Boolean = true

                override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {
                    calls += info
                }

                override fun traceEventEnd() = Unit
            },
        )
    }

    @After
    fun stopTrace() = Composer.setTracer(null)

    private fun moveAcrossIcons(entry: Int, from: Int, move: Direction) {
        lateinit var state: StripState
        val icons = FakeAppIcons(missing = emptySet())
        compose.setContent {
            PlumTheme {
                val content = androidx.compose.runtime.remember { appGamesBoard() }
                CompositionLocalProvider(LocalHomeAppIcons provides icons) {
                    HomeAt(content, gridActions(entry, from), gridIds = setOf(content.entries[entry].id), onState = { state = it })
                }
            }
        }
        compose.waitForIdle()
        val loaded = icons.loads.toList()
        assertTrue("icons were loaded for the shown tiles", loaded.isNotEmpty())
        compose.runOnIdle { calls.clear() }
        compose.runOnIdle { state.dispatch(Action.Move(move)) }
        compose.waitForIdle()
        val tiles = calls.filter { c -> listOf("AppTile", "AppGameCover", "GridCover", "AppGameIcon", "AppIconStandIn").any { c.startsWith("com.plumemu.home.$it ") } }
        assertEquals("tiles recomposed: $tiles", emptyList<String>(), tiles)
        assertEquals("a move loads no icon", loaded, icons.loads.toList())
    }

    @Test
    fun aMoveInTheAndroidGamesGridRecomposesNoTile() = moveAcrossIcons(ANDROID_GAMES_ENTRY, 0, Direction.Down)

    @Test
    fun aMoveAcrossAppGameCellsOfPortsRecomposesNoTile() = moveAcrossIcons(PORTS_ENTRY, 2, Direction.Right)
}
