package com.plumemu.coreui.components

import androidx.compose.ui.graphics.Color
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The slab's drops image holds the accent's glow, so a colour page's step (a new accent) needs a new one, 60 ms to build on the emulator. With
 * images built in the background the draw takes the last image of the look at once and the exact one lands a few frames later.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class DropsStandInTest {
    private val builders = mutableListOf<Thread>()

    @Before
    fun setUp() {
        ImageBuilding.clearCaches()
        ImageBuilding.inBackground = true
        ImageBuilding.watcher = { builders += Thread.currentThread() }
    }

    @After
    fun tearDown() {
        ImageBuilding.watcher = null
        ImageBuilding.inBackground = false
    }

    private fun drops(accent: Int) =
        SoftImages.drops(
            400, 60, 12f,
            SoftImages.Inset(Color(0x33000000), 4f, 6f),
            SoftImages.Inset(Color(0x22000000), 10f, 14f),
            SoftImages.Inset(Color(accent), 0f, 11f),
            Color(0xFF202020),
        )

    @Test
    fun aNewAccentIsDrawnFromTheLastImageWhileTheExactOneIsBuiltOffTheMainThread() {
        val first = checkNotNull(drops(0x40FF0000))
        val me = Thread.currentThread()
        assertTrue("the first image is built here", builders.single() === me)
        builders.clear()
        val next = checkNotNull(drops(0x401E88E5))
        assertSame("a stand-in at once", first, next)
        assertTrue("nothing was built on the calling thread: $builders", builders.none { it === me })
        // The exact image lands from the build thread.
        val deadline = System.currentTimeMillis() + 10_000
        var landed = drops(0x401E88E5)
        while (landed === first && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
            landed = drops(0x401E88E5)
        }
        assertNotSame("the exact image replaces it", first, landed)
        assertNotNull(landed)
        assertEquals("one build for the new accent", 1, builders.size)
    }
}
