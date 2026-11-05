package com.plumemu.coreui.components

import android.os.Looper
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.ListGlide
import com.plumemu.coreui.theme.PlumColors
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.Settle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Collections

/** The games list pill's warm-up builds its slab images off the main thread, so the real draw after it builds nothing; a cancelled warm-up builds nothing. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GlidingPillWarmTest {
    private val builds = Collections.synchronizedList(mutableListOf<Pair<String, Boolean>>())
    private val colors = PlumThemeSettings.Graphite.let { PlumComposeColors.from(PlumColors.derive(it.backgroundColor, it.textMode, it.accentColor)) }
    private val density = Density(2f)
    private val scope = CoroutineScope(Job())

    @Before
    fun watch() {
        ImageBuilding.clearCaches()
        ImageBuilding.watcher = { name -> builds += name to (Looper.getMainLooper().thread === Thread.currentThread()) }
    }

    @After
    fun restore() {
        ImageBuilding.watcher = null
        ImageBuilding.clearCaches()
    }

    private fun pillAndInput(): Pair<GlidingPill, PillInput> {
        val pill = GlidingPill(Settle(scope)) { 0L }
        val glide = ListGlide(Settle(scope)) { 0L }
        return pill to PillInput(null, null, glide, colors, FocusPillShape, 80f, 84f, 0f, false, true, false, true) { 0 }
    }

    @Test
    fun afterWarmARealDrawBuildsNothingAndNothingWasBuiltOnTheMainThread() {
        val (pill, input) = pillAndInput()
        runBlocking { pill.warm(input, WIDTH, density, LayoutDirection.Ltr) }
        assertTrue("warm built nothing", builds.isNotEmpty())
        assertTrue("warm built on the main thread: $builds", builds.none { it.second })
        builds.clear()
        val size = Size(WIDTH.toFloat(), input.heightPx)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(ImageBitmap(1, 1)), size) {
            val outline = input.shape.createOutline(size, LayoutDirection.Ltr, this)
            drawSlabOutside(outline, colors.slab, colors.slabGlow, colors.slabBlob, colors.bg)
            drawSlabFill(outline, colors.slab, false)
        }
        assertEquals("the real draw built images after warm: $builds", 0, builds.size)
    }

    @Test
    fun aWarmCancelledDuringItsWaitBuildsNothing() {
        val (pill, input) = pillAndInput()
        runBlocking {
            val job = launch { pill.warm(input, WIDTH, density, LayoutDirection.Ltr) }
            job.cancelAndJoin()
        }
        Thread.sleep(300)
        assertEquals("a cancelled warm built: $builds", 0, builds.size)
    }

    private companion object {
        const val WIDTH = 600
    }
}
