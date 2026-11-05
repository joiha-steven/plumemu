package com.plumemu.coreui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.rememberListGlide
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * A focused row draws the same slab with animations off as with them on: the games list (halo drawn outside the list), the search list (halo drawn
 * by the list's own pill) and a Settings-style scrolling column. The page is captured in both and every pixel compared (within 1 of 255).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SlabMotionOffTest {
    @get:Rule
    val compose = createComposeRule()

    private enum class Kind { GamesList, SearchList, SettingsColumn }

    /** One composition per kind and theme; the motion setting is flipped in it and the page captured after each flip. */
    private fun shots(
        kind: Kind,
        settings: PlumThemeSettings,
    ): Pair<androidx.compose.ui.graphics.PixelMap, androidx.compose.ui.graphics.PixelMap> {
        var current by androidx.compose.runtime.mutableStateOf(settings.copy(animationsOn = false))
        compose.setContent {
            PlumTheme(current) {
                // A new subtree per motion setting, as a fresh screen would be (a row reports its box once, when it is placed).
                androidx.compose.runtime.key(current.animationsOn) {
                Box(Modifier.testTag("page").size(420.dp, 300.dp).background(PlumTheme.colors.bg).padding(horizontal = 40.dp, vertical = 20.dp)) {
                    when (kind) {
                        Kind.GamesList, Kind.SearchList -> GamesList(kind == Kind.GamesList)
                        Kind.SettingsColumn -> SettingsColumn()
                    }
                }
                }
            }
        }
        compose.waitForIdle()
        val off = compose.onNodeWithTag("page").captureToImage().toPixelMap()
        current = settings.copy(animationsOn = true)
        compose.waitForIdle()
        return compose.onNodeWithTag("page").captureToImage().toPixelMap() to off
    }

    @androidx.compose.runtime.Composable
    private fun GamesList(haloOutside: Boolean) {
        val layout = PlumTheme.layout
        val stride = layout.gameRowHeight + PlumDimens.listRowGap
        val glide = rememberListGlide(stride)
        val state = rememberLazyListState()
        val pill = rememberGlidingPill(state, glide, rowHeight = layout.gameRowHeight, stride = stride, haloOutside = haloOutside, blob = true) { 3 }
        Box(Modifier.fillMaxWidth().height(260.dp).drawBehind { pill.drawHalo(this) }) {
            pill.Provide {
                LazyColumn(state = state, modifier = Modifier.fillMaxWidth().then(pill.modifier).then(glide.modifier), contentPadding = PaddingValues(vertical = glide.extra)) {
                    items(7) { i ->
                        GameRow(title = "Game $i", trailing = "1994", look = if (i == 3) GameRowLook.Focused else GameRowLook.Normal, region = "USA")
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun SettingsColumn() {
        val layout = PlumTheme.layout
        val stride = layout.rowHeight + PlumDimens.panelRowGap
        val glide = rememberListGlide(stride)
        val scroll = rememberScrollState()
        val pill = rememberGlidingPill(scroll, glide, stride = stride) { 3 }
        pill.Provide {
            Column(Modifier.fillMaxWidth().height(260.dp).then(pill.modifier).then(glide.modifier).verticalScroll(scroll)) {
              Column(Modifier.padding(vertical = glide.extra).then(pill.contentModifier)) {
                for (i in 0 until 7) {
                    Row(Modifier.fillMaxWidth().height(layout.rowHeight).focusPill(i == 3)) {}
                    androidx.compose.foundation.layout.Spacer(Modifier.height(PlumDimens.panelRowGap))
                }
              }
            }
        }
    }

    private fun compare(
        kind: Kind,
        base: PlumThemeSettings,
    ) {
        run {
            val (on, off) = shots(kind, base)
            assertEquals(on.width, off.width)
            var worst = 0f
            var at = ""
            // A scrolling column or list clips what its rows draw at its own sides (the pill, drawn outside the scroll, is not clipped there): only its width is compared.
            val xs = if (kind != Kind.GamesList) 88 until on.width - 88 else 0 until on.width
            for (y in 0 until on.height) for (x in xs) {
                val a = on[x, y]
                val b = off[x, y]
                val d = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))
                if (d > worst) {
                    worst = d
                    at = "$x,$y"
                }
            }
            assertEquals("$kind on ${base.backgroundColor}: worst pixel difference ${(worst * 255).toInt()} of 255 at $at", 0.0, worst * 255.0, 1.0)
        }
    }

    @Test
    fun gamesListGraphite() = compare(Kind.GamesList, PlumThemeSettings.Graphite)

    @Test
    fun gamesListPaper() = compare(Kind.GamesList, PlumThemeSettings.Paper)

    @Test
    fun searchListGraphite() = compare(Kind.SearchList, PlumThemeSettings.Graphite)

    @Test
    fun searchListPaper() = compare(Kind.SearchList, PlumThemeSettings.Paper)

    @Test
    fun settingsColumnGraphite() = compare(Kind.SettingsColumn, PlumThemeSettings.Graphite)

    @Test
    fun settingsColumnPaper() = compare(Kind.SettingsColumn, PlumThemeSettings.Paper)
}
