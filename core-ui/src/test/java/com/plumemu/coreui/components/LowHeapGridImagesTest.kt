package com.plumemu.coreui.components

import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.CollarDimens
import com.plumemu.coreui.theme.PlumColors
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Collections

/**
 * A first grid of box covers, each of its own size, on a 48 MB heap (Android 6 handhelds): the collar images it needs are never built
 * on the main thread, all the image caches together stay within the heap's image budget, and once the grid is drawn a redraw builds
 * nothing (no image evicted and built again).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class LowHeapGridImagesTest {
    private val builds = Collections.synchronizedList(mutableListOf<Pair<String, Boolean>>())
    private val colors = PlumThemeSettings.Graphite.let { PlumComposeColors.from(PlumColors.derive(it.backgroundColor, it.textMode, it.accentColor)) }
    private val density = Density(DENSITY)
    private val target = ImageBitmap(TARGET_PX, TARGET_PX)

    @Before
    fun smallHeap() {
        ImageBuilding.clearCaches()
        ImageBuilding.budgetBytes = ImageBuilding.budgetFor(HEAP_BYTES)
        ImageBuilding.inBackground = true
        ImageBuilding.watcher = { name -> builds += name to (Looper.getMainLooper().thread === Thread.currentThread()) }
    }

    @After
    fun restore() {
        ImageBuilding.watcher = null
        ImageBuilding.inBackground = false
        ImageBuilding.budgetBytes = ImageBuilding.budgetFor(Long.MAX_VALUE)
        ImageBuilding.clearCaches()
    }

    /** Cover [i] as the grid draws it at rest: its collar, then the box's soft shadow (`coverBoxShadow`) inside it. */
    private fun drawCover(i: Int): ImageBitmap {
        val w = (COVER_W_DP + i * STEP_DP) * DENSITY
        val h = (COVER_H_DP - i * STEP_DP / 2) * DENSITY
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(target), Size(w, h)) {
            drawRect(colors.bg)
            drawCollar(colors.collar, CollarSize.Cover, CollarDimens.coverBoxCorner.toPx())
            val inset = (CollarSize.Cover.lip + CollarSize.Cover.groove).toPx()
            val box = Size(w - 2 * inset, h - inset - (CollarSize.Cover.lipBottom + CollarSize.Cover.grooveBottom).toPx())
            drawSoft(Offset(inset, inset), box, CollarDimens.coverBoxCorner.toPx(), CollarDimens.boxShadowSoftDrop.toPx(), CollarDimens.boxShadowSoftSigma.toPx(), colors.collar.shadow.copy(alpha = 0.4f), true)
        }
        return target
    }

    private fun drawGrid() {
        repeat(COVERS) { drawCover(it) }
        val end = System.nanoTime() + 60_000_000_000L
        while (BackgroundBuilds.waiting > 0 && System.nanoTime() < end) Thread.sleep(5)
        assertEquals("background builds did not finish", 0, BackgroundBuilds.waiting)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun aFirstGridOfCoversStaysWithinASmallHeapAndOffTheMainThread() {
        assertTrue("the test must draw on the main thread", Looper.getMainLooper().thread === Thread.currentThread())
        repeat(ROUNDS - 1) { drawGrid() }
        val settled = builds.size
        drawGrid()
        val collars = builds.filter { it.first == COLLAR_BUILD }
        val held = ImageBuilding.heldBytes
        val bound = ImageBuilding.budgetFor(HEAP_BYTES)
        val again = builds.drop(settled).map { it.first }
        val faults =
            listOfNotNull(
                "${collars.count { it.second }} of ${collars.size} collar images built on the main thread".takeIf { collars.any { it.second } },
                "the caches hold ${held / 1024} KB after a grid of $COVERS covers, over ${bound / 1024} KB (a sixth of a 48 MB heap)".takeIf { held > bound },
                "a redraw of the drawn grid built ${again.size} images again (${again.toSet()})".takeIf { again.isNotEmpty() },
            )
        println("LowHeapGridImagesTest: ${held / 1024} KB held, ${collars.size} collar builds (${collars.count { it.second }} on the main thread), ${builds.size} builds in all, ${again.size} on the last redraw")
        assertTrue(faults.joinToString("; "), faults.isEmpty())
    }

    @Test
    fun aCoverWhoseCollarIsStillBeingBuiltIsDrawnWithItsCollar() {
        val lipX = (CollarSize.Cover.lip.value / 2 * DENSITY).toInt()
        val midY = (COVER_H_DP * DENSITY / 2).toInt()
        val first = drawCover(0).toPixelMap()[lipX, midY]
        drawGrid()
        val landed = drawCover(0).toPixelMap()[lipX, midY]
        // Drawn layer by layer until the image lands: the same look, within the rounding of drawing through an image.
        val channels = listOf(first.red - landed.red, first.green - landed.green, first.blue - landed.blue)
        assertTrue("the lip before its image landed: $first, after: $landed", first.alpha == 1f && channels.all { kotlin.math.abs(it) <= 2f / 255f })
    }

    @Test
    fun theImageBudgetComesFromTheHeap() {
        assertEquals(HEAP_BYTES / 6, ImageBuilding.budgetFor(HEAP_BYTES))
        assertEquals(18L * 1024 * 1024, ImageBuilding.budgetFor(512L * 1024 * 1024))
    }

    private companion object {
        const val HEAP_BYTES = 48L * 1024 * 1024
        const val DENSITY = 2.5f
        const val COVERS = 24
        const val ROUNDS = 4
        const val COVER_W_DP = 120f
        const val COVER_H_DP = 170f
        const val STEP_DP = 3f
        const val TARGET_PX = 800
        const val COLLAR_BUILD = "Plum:collarImage"
    }
}
