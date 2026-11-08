package com.plumemu.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SlabDimens
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A focused choice card's shadow and glow on a Setup step whose content fits: the scroll column's clip must leave the slab's
 * reach ([SlabDimens.glowReach]) below and beside the card, so the shadow fades out instead of ending in a flat line or a vertical edge.
 */
abstract class SetupSlabShadowRoomBase {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var theme by mutableStateOf(PlumThemeSettings.Graphite)
    private var state by mutableStateOf(SetupFakes.boxArtQuestion)
    private var density = Density(1f)

    private fun px(dp: Dp) = with(density) { dp.toPx() }

    private fun step(a: Color, b: Color) = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))

    private val steps =
        listOf(
            "box art last" to SetupFakes.boxArtQuestion.copy(focus = SetupControl.BoxArtYes),
            "box art first" to SetupFakes.boxArtQuestion,
            "box art sources" to SetupFakes.boxArtSources,
            "box art sources last" to SetupFakes.boxArtSources.copy(focus = SetupControl.UseBoxArt),
        )

    @Test
    fun aFocusedCardsShadowIsNotCutByTheScrollColumn() {
        compose.setContent {
            density = LocalDensity.current
            PlumTheme(theme) {
                Box(Modifier.fillMaxSize()) { SetupAt(state) }
            }
        }
        val problems = mutableListOf<String>()
        for (text in listOf(TextSize.Small, TextSize.Largest)) {
            for ((themeName, look) in listOf("Graphite" to PlumThemeSettings.Graphite, "Paper" to PlumThemeSettings.Paper)) {
                theme = look.copy(textSize = text)
                for ((name, shown) in steps) {
                    state = shown
                    compose.waitForIdle()
                    val who = "$name $themeName ${text.name}"
                    val scroll = compose.onNodeWithTag(SetupTags.SCROLL, useUnmergedTree = true).getBoundsInRoot()
                    val card = compose.onNodeWithTag(SetupTags.control(shown.focus!!), useUnmergedTree = true).getUnclippedBoundsInRoot()
                    val hint = compose.onNodeWithTag(SetupTags.HINT_BACK, useUnmergedTree = true).getBoundsInRoot()
                    val pixels = compose.onRoot().captureToImage().toPixelMap()
                    val x = px((card.left + card.right) / 2).toInt()
                    val bottom = px(card.bottom).toInt()
                    val reach = px(SlabDimens.glowReach).toInt()
                    val last = minOf(bottom + reach, px(hint.top).toInt() - 2)
                    // A step whose content runs past the window really scrolls: its edge treatment stays.
                    if (scroll.bottom < card.bottom) continue
                    // The first card of a column that starts with it (wide screens): its glow above must fade, not stop at the clip.
                    if ((card.top - scroll.top).value < FIRST_CARD_DP && name == "box art first") {
                        val top = px(card.top).toInt()
                        var topWorst = 0f
                        var topAt = 0
                        for (y in top - reach until top - 4) {
                            val s = step(pixels[x, y], pixels[x, y + 1])
                            if (s > topWorst) {
                                topWorst = s
                                topAt = top - y
                            }
                        }
                        if (topWorst > MAX_STEP) problems += "$who: glow steps ${(topWorst * 255).toInt()}/255 at ${topAt}px above the card: a flat cut"
                    }
                    if (name == "box art first") continue
                    var worst = 0f
                    var at = 0
                    for (y in bottom + 4 until last) {
                        val s = step(pixels[x, y], pixels[x, y + 1])
                        if (s > worst) {
                            worst = s
                            at = y - bottom
                        }
                    }
                    if (worst > MAX_STEP) problems += "$who: shadow steps ${(worst * 255).toInt()}/255 at ${at}px below the card: a flat cut"
                    val rowY = px((card.top + card.bottom) / 2).toInt()
                    val left = px(card.left).toInt()
                    var sideWorst = 0f
                    var sideAt = 0
                    for (dx in 4 until reach) {
                        val s = step(pixels[left - dx, rowY], pixels[left - dx - 1, rowY])
                        if (s > sideWorst) {
                            sideWorst = s
                            sideAt = dx
                        }
                    }
                    if (sideWorst > MAX_STEP) problems += "$who: shadow steps ${(sideWorst * 255).toInt()}/255 at ${sideAt}px left of the card: a vertical edge"
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private companion object {
        const val MAX_STEP = 5.5f / 255f
        const val FIRST_CARD_DP = 20f
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w832dp-h468dp-land-xhdpi")
class SetupSlabShadowRoom832Test : SetupSlabShadowRoomBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SetupSlabShadowRoom768Test : SetupSlabShadowRoomBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SetupSlabShadowRoom576Test : SetupSlabShadowRoomBase()
