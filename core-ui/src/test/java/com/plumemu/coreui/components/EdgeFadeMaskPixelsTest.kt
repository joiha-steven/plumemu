package com.plumemu.coreui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.max

/**
 * The edge fade mask draws the same pixels as the old whole-node offscreen layer. The reference ([legacyMask], [legacyMaskInRoom]) is a copy of
 * that implementation. Both are drawn over a gradient page with rows of several colours, a translucent shadow and a glow reaching past the node, inside an
 * ancestor layer at whole and at fractional rests (the home strip), and every pixel is compared. At most [MAX_DIFF] of 255 per channel: the old layer rounded
 * each translucent layer to 8-bit premultiplied before blending it on the page and the banded version blends it straight on (2 to 4 seen); a dropped band or a
 * list off whole pixels differs by tens. Fractional mask edges are included: the old rects were anti-aliased there.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class EdgeFadeMaskPixelsTest {
    @get:Rule
    val compose = createComposeRule()

    private class Fades(
        val topPx: Float = 0f,
        val bottomPx: Float = 0f,
        val clearTopPx: Float = 0f,
        val clearBottomPx: Float = 0f,
        override val canScroll: Boolean = true,
    ) : EdgeFades {
        override fun top(fadePx: Float, height: Float) = minOf(topPx, fadePx)

        override fun bottom(fadePx: Float, height: Float) = minOf(bottomPx, fadePx)

        override fun clearTop(height: Float) = clearTopPx

        override fun clearBottom(height: Float) = clearBottomPx
    }

    private fun legacyMask(fades: EdgeFades): Modifier =
        Modifier
            .graphicsLayer { compositingStrategy = if (fades.canScroll) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
            .drawWithContent {
                drawContent()
                val fadePx = PlumDimens.listFadeBand.toPx()
                val top = fades.top(fadePx, size.height).coerceIn(0f, size.height)
                val bottom = fades.bottom(fadePx, size.height).coerceIn(0f, size.height)
                val clearTop = fades.clearTop(size.height).coerceIn(0f, size.height)
                val clearBottom = fades.clearBottom(size.height).coerceIn(0f, size.height)
                if (clearTop > 0f) drawRect(Color.Transparent, size = Size(size.width, clearTop), blendMode = BlendMode.DstIn)
                if (clearBottom > 0f) drawRect(Color.Transparent, topLeft = Offset(0f, size.height - clearBottom), size = Size(size.width, clearBottom), blendMode = BlendMode.DstIn)
                if (top > 0f) drawRect(maskBrush(false, 0f, top), size = Size(size.width, top), blendMode = BlendMode.DstIn)
                if (bottom > 0f) {
                    val from = size.height - bottom
                    drawRect(maskBrush(true, from, bottom), topLeft = Offset(0f, from), size = Size(size.width, bottom), blendMode = BlendMode.DstIn)
                }
            }

    private fun legacyMaskInRoom(fades: EdgeFades, rampsOn: Boolean, room: () -> Float): Modifier =
        Modifier
            .graphicsLayer { compositingStrategy = if (fades.canScroll) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
            .drawWithContent {
                drawContent()
                val pad = room()
                val height = size.height - 2f * pad
                if (height <= 0f) return@drawWithContent
                val fadePx = PlumDimens.listFadeBand.toPx()
                val top = if (rampsOn) fades.top(fadePx, height).coerceIn(0f, height) else 0f
                val bottom = if (rampsOn) fades.bottom(fadePx, height).coerceIn(0f, height) else 0f
                val clearTop = fades.clearTop(height).coerceIn(0f, height)
                val clearBottom = fades.clearBottom(height).coerceIn(0f, height)
                if (clearTop > 0f) drawRect(Color.Transparent, size = Size(size.width, pad + clearTop), blendMode = BlendMode.DstIn)
                if (clearBottom > 0f) drawRect(Color.Transparent, topLeft = Offset(0f, pad + height - clearBottom), size = Size(size.width, clearBottom + pad), blendMode = BlendMode.DstIn)
                if (top > 0f) {
                    drawRect(Color.Transparent, size = Size(size.width, pad), blendMode = BlendMode.DstIn)
                    drawRect(maskBrush(false, pad, top), topLeft = Offset(0f, pad), size = Size(size.width, top), blendMode = BlendMode.DstIn)
                }
                if (bottom > 0f) {
                    val from = pad + height - bottom
                    drawRect(Color.Transparent, topLeft = Offset(0f, pad + height), size = Size(size.width, pad), blendMode = BlendMode.DstIn)
                    drawRect(maskBrush(true, from, bottom), topLeft = Offset(0f, from), size = Size(size.width, bottom), blendMode = BlendMode.DstIn)
                }
            }

    private val rowColours = listOf(0xFF3A6EA5, 0xFFC0504D, 0xFF9BBB59, 0xFF8064A2, 0xFFF79646, 0xFF4BACC6).map { Color(it) }

    /** The strip's rests as translations of the page content: whole, and fractional in x and y like the strip (-412.8 px) and a gliding list. */
    private val shifts = listOf(0f to 0f, -12.8f to 0f, 0.3f to 0.45f)

    /** Both variants in one composition, side by side for each of [shifts], then captured: the pairs in order. */
    private fun shots(mine: Modifier, reference: Modifier, glowOutside: Boolean = true): List<Pair<PixelMap, PixelMap>> {
        compose.setContent {
            androidx.compose.foundation.layout.Row {
                shifts.forEachIndexed { i, (dx, dy) ->
                    Page("mine$i", mine, dx, dy, glowOutside)
                    Page("ref$i", reference, dx, dy, glowOutside)
                }
            }
        }
        compose.waitForIdle()
        return shifts.indices.map { i ->
            compose.onNodeWithTag("mine$i").captureToImage().toPixelMap() to compose.onNodeWithTag("ref$i").captureToImage().toPixelMap()
        }
    }

    @androidx.compose.runtime.Composable
    private fun Page(tag: String, mask: Modifier, dx: Float, dy: Float, glowOutside: Boolean = true) {
        Box(
            Modifier.testTag(tag).size(60.dp, 140.dp).clipToBounds()
                .background(Brush.verticalGradient(listOf(Color(0xFF101820), Color(0xFFE8E0D0), Color(0xFF304050)))),
        ) {
            // Like the home strip: an ancestor layer at a fractional rest, the list inside it.
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = dx; translationY = dy }) {
                Column(
                    Modifier.fillMaxSize().then(mask)
                        // Glow past the node, then a translucent shadow band.
                        .drawBehind {
                            val past = if (glowOutside) 30f else 0f
                            drawRect(Color(0x663366FF), Offset(0f, -past), Size(size.width, size.height + 2f * past))
                            drawRect(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent), 100f, 260f), Offset(0f, 100f), Size(size.width, 160f))
                        },
                ) {
                    rowColours.forEachIndexed { i, c -> Box(Modifier.fillMaxWidth().weight(1f).background(c.copy(alpha = if (i == 2) 0.6f else 1f))) }
                }
            }
        }
    }

    /** The largest per-channel difference over all pairs, out of 255. */
    private fun worsts(name: String, mine: Modifier, reference: Modifier, glowOutside: Boolean = true): List<Int> {
        val all = ArrayList<Int>()
        shots(mine, reference, glowOutside).forEachIndexed { n, (a, b) ->
            var w = 0
            var at = ""
            for (y in 0 until a.height) for (x in 0 until a.width) {
                val p = a[x, y]
                val q = b[x, y]
                val d = maxOf(abs(p.red - q.red), abs(p.green - q.green), abs(p.blue - q.blue), abs(p.alpha - q.alpha))
                val di = (d * 255f + 0.5f).toInt()
                if (di > w) { w = di; at = "$x,$y mine=$p ref=$q" }
            }
            println("EdgeFadeMaskPixelsTest $name shift ${shifts[n]}: max channel difference $w/255 at $at")
            all.add(w)
        }
        return all
    }

    private fun worst(name: String, mine: Modifier, reference: Modifier) = worsts(name, mine, reference).max()

    private fun compare(name: String, mine: Modifier, reference: Modifier) {
        val w = worst(name, mine, reference)
        assertTrue("$name: max difference $w/255 over $MAX_DIFF", w <= MAX_DIFF)
    }

    private fun plain(f: Fades) = compare("plain", Modifier.listEdgeFadeMask(f, true), legacyMask(f))

    @Test fun topOnly() = plain(Fades(topPx = 24f))

    @Test fun bottomOnly() = plain(Fades(bottomPx = 24f))

    @Test fun both() = plain(Fades(topPx = 24f, bottomPx = 24f))

    @Test fun shortRamps() = plain(Fades(topPx = 9.4f, bottomPx = 17.6f))

    @Test fun clearZones() = plain(Fades(clearTopPx = 30f, clearBottomPx = 26f))

    @Test fun clearZonesFractional() = plain(Fades(clearTopPx = 30.4f, clearBottomPx = 26.7f))

    @Test fun clearWithRamps() = plain(Fades(topPx = 24f, bottomPx = 24f, clearTopPx = 11.5f, clearBottomPx = 40f))

    // A list that cannot scroll draws no mask (the old Auto layer would have punched holes in the page under it).
    @Test fun cannotScroll() = plain(Fades(canScroll = false))

    private fun inRoom(f: Fades, room: Float, rampsOn: Boolean = true) =
        compare("room $room ramps $rampsOn", Modifier.listEdgeFadeMaskInRoom(f, rampsOn) { room }, legacyMaskInRoom(f, rampsOn) { room })

    @Test fun roomBoth() = inRoom(Fades(topPx = 24f, bottomPx = 24f), 30f)

    @Test fun roomTopOnly() = inRoom(Fades(topPx = 24f), 30f)

    @Test fun roomBottomOnly() = inRoom(Fades(bottomPx = 24f), 30f)

    @Test fun roomFractional() = inRoom(Fades(topPx = 24f, bottomPx = 18.5f, clearTopPx = 6.3f), 29.6f)

    @Test fun roomClearZones() = inRoom(Fades(clearTopPx = 33f, clearBottomPx = 21f), 30f)

    @Test fun roomClearWithRamps() = inRoom(Fades(topPx = 24f, bottomPx = 24f, clearTopPx = 14f, clearBottomPx = 14f), 30f)

    @Test fun roomRampsOff() = inRoom(Fades(topPx = 24f, bottomPx = 24f, clearTopPx = 14f, clearBottomPx = 20f), 30f, rampsOn = false)

    @Test fun roomNone() = inRoom(Fades(), 30f)

    // Guard: a plain list (neither the old layer nor the snap) with nothing past its bounds equals the old drawing at whole rests only, so the comparison does see the snap.
    @Test fun withoutSnapFractionalRestsDiffer() {
        val w = worsts("no snap", Modifier.drawWithContent { drawContent() }, legacyMask(Fades()), glowOutside = false)
        assertTrue("whole rest should match, got ${w[0]}", w[0] <= MAX_DIFF)
        assertTrue("fractional rests without the snap should differ by more than $MAX_DIFF, got ${w.drop(1)}", w.drop(1).all { it > MAX_DIFF })
    }

    private companion object {
        const val MAX_DIFF = 4
    }
}
