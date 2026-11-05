package com.plumemu.home

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.plumemu.coreui.testing.ReferenceShots
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.coroutines.CoroutineContext

/**
 * The colours of the covers asked for in one frame are found in one hop to the worker dispatcher and written back in one hop
 * to the main thread, not two hops per cover: every hop back takes the UI dispatcher's lock a worker holds the main thread up on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoverGlowBatchTest {
    private class CountingDispatcher : CoroutineDispatcher() {
        var hops = 0

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            hops++
            block.run()
        }
    }

    private fun image(): ImageBitmap = android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF336699.toInt()) }.asImageBitmap()

    @Test
    fun fifteenCoversAskedInOneFrameHopToTheWorkerOnce() {
        val worker = CountingDispatcher()
        val colors = CoverGlowColors(worker)
        runBlocking {
            for (i in 0 until 15) launch { colors.learnSoon("cover-$i", image()) }
        }
        assertEquals("one hop for the whole batch", 1, worker.hops)
        for (i in 0 until 15) assertNotNull("cover-$i has its colour", colors["cover-$i"])
    }

    @Test
    fun aKeyThatHasItsColourAsksNoHop() {
        val worker = CountingDispatcher()
        val colors = CoverGlowColors(worker)
        runBlocking { colors.learnSoon("a", image()) }
        val after = worker.hops
        runBlocking { colors.learnSoon("a", image()) }
        assertEquals(after, worker.hops)
    }

    @Test
    fun learningOneByOneStillHopsForEach() {
        val worker = CountingDispatcher()
        val colors = CoverGlowColors(worker)
        runBlocking {
            for (i in 0 until 15) launch { colors.learn("cover-$i", image()) }
        }
        assertEquals("the answering form is not batched", 15, worker.hops)
    }
}
