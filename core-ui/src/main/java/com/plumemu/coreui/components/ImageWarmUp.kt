package com.plumemu.coreui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.theme.CardDimens
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Builds a raised card's shadows, the slab of a row on it and the slab of a page's row (Settings) on the image thread, soon after the
 * page shows, so the first opening of a card or of Settings does not build them in its first frame: they are exact Gaussians, on a weak
 * handheld a tenth of a second of the main thread each. [width] and [height] are the card's, [rowHeight] a row's; a slab of any width of
 * that height draws the same image ([SoftImages.drops], [SoftImages.inset]).
 */
@Composable
fun WarmCardImages(
    width: Dp,
    height: Dp,
    rowHeight: Dp,
) {
    val colors = PlumTheme.colors
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    LaunchedEffect(colors, density, width, height, rowHeight) {
        if (height <= 0.dp) return@LaunchedEffect
        delay(WARM_DELAY_MS)
        val card = with(density) { Size(width.toPx(), height.toPx()) }
        val row = with(density) { Size((width - PlumDimens.panelInset * 2).toPx(), rowHeight.roundToPx().toFloat()) }
        suspend fun slab(
            onCard: Boolean,
            under: Color,
        ) = onImageThread {
            drawOffScreen(density, layoutDirection, row) {
                val outline = FocusPillShape.createOutline(size, layoutDirection, this)
                drawSlabOutside(outline, colors.slab, colors.slabGlow, under = under)
                drawSlabFill(outline, colors.slab, onCard = onCard)
            }
        }
        val cardShadows = onImageThread {
            drawOffScreen(density, layoutDirection, card) { drawRaisedCard(colors.card, 0f, 0f, size.width, size.height, CardDimens.corner.toPx()) }
        }
        if (cardShadows && slab(onCard = true, under = colors.card.fill)) slab(onCard = false, under = colors.bg)
    }
}

private fun drawOffScreen(
    density: Density,
    layoutDirection: LayoutDirection,
    size: Size,
    block: DrawScope.() -> Unit,
) = CanvasDrawScope().draw(density, layoutDirection, Canvas(ImageBitmap(1, 1)), size, block)

/** Runs [part] on the image thread and waits for it; false if it could not run or failed (queue full, out of memory), or the caller was cancelled meanwhile. */
internal suspend fun onImageThread(part: () -> Unit): Boolean {
    var done = false
    suspendCancellableCoroutine { c ->
        val queued = BackgroundBuilds.submit {
            try {
                part()
                done = true
            } finally {
                c.resume(Unit)
            }
            null
        }
        if (!queued) c.resume(Unit)
    }
    currentCoroutineContext().ensureActive()
    return done
}

private const val WARM_DELAY_MS = 400L
