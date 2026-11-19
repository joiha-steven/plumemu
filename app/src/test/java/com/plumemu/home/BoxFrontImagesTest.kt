package com.plumemu.home

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.boxFieldFor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Drawn box fronts and tile fields kept across compositions by [LocalBoxFrontImages]. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class BoxFrontImagesTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val shown = mutableStateOf(true)
    private val width = mutableStateOf(100.dp)
    private val tints = mutableStateOf(mapOf("SNES" to Color(0xFF7B5EA7)))

    @After
    fun clearProbe() {
        boxFrontProbe = null
    }

    private fun showCover(cache: BoxFrontImages?, tile: Boolean = false) {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                CompositionLocalProvider(
                    LocalBoxFrontImages provides cache,
                    LocalSystemTints provides tints,
                ) {
                    if (shown.value) {
                        if (tile) {
                            val field = rememberTileField(PosterSpec("Super Metroid", "Super Nintendo", "SNES"), null)
                            androidx.compose.foundation.layout.Box(Modifier.size(width.value, 141.dp).then(field.fill))
                        } else {
                            PosterCover("Super Metroid", "Super Nintendo", "SNES", Modifier.size(width.value, 141.dp))
                        }
                    }
                }
            }
        }
    }

    private fun frame() {
        compose.waitForIdle()
        compose.onRoot().captureToImage()
    }

    private fun removeAndShowAgain() {
        shown.value = false
        compose.waitForIdle() // nothing is on screen to capture now
        shown.value = true
        frame()
    }

    @Test
    fun aCoverComposedAgainWithACacheBuildsOnce() {
        val probe = BoxFrontProbe().also { boxFrontProbe = it }
        showCover(BoxFrontImages(1 shl 20))
        frame()
        val drawsBefore = probe.imageDraws
        removeAndShowAgain()
        assertEquals("one build", 1, probe.builds)
        assertEquals("drawn again", true, probe.imageDraws > drawsBefore)
    }

    @Test
    fun aTileFieldComposedAgainWithACacheBuildsOnce() {
        val probe = BoxFrontProbe().also { boxFrontProbe = it }
        showCover(BoxFrontImages(1 shl 20), tile = true)
        frame()
        val drawsBefore = probe.imageDraws
        removeAndShowAgain()
        assertEquals("one build", 1, probe.builds)
        assertEquals("drawn again", true, probe.imageDraws > drawsBefore)
    }

    @Test
    fun withoutACacheEachCompositionBuildsItsOwn() {
        val probe = BoxFrontProbe().also { boxFrontProbe = it }
        showCover(null)
        frame()
        removeAndShowAgain()
        assertEquals("built twice", 2, probe.builds)
    }

    @Test
    fun aChangedSizeBuildsANewImage() {
        val probe = BoxFrontProbe().also { boxFrontProbe = it }
        showCover(BoxFrontImages(1 shl 20))
        frame()
        width.value = 110.dp
        frame()
        assertEquals("a new size, a new image", 2, probe.builds)
        width.value = 100.dp
        frame()
        assertEquals("the first size is still kept", 2, probe.builds)
    }

    @Test
    fun aChangedColourBuildsANewImage() {
        val probe = BoxFrontProbe().also { boxFrontProbe = it }
        showCover(BoxFrontImages(1 shl 20))
        frame()
        removeAndShowAgain()
        assertEquals(1, probe.builds)
        tints.value = mapOf("SNES" to Color(0xFF2E8B57))
        removeAndShowAgain()
        assertEquals("a new colour, a new image", 2, probe.builds)
    }

    @Test
    fun theCacheEvictsByBytesAndKeepsTheNewest() {
        val cache = BoxFrontImages(2 * 10 * 10 * 4) // two 10 x 10 images
        val look = boxFieldFor(Color(0xFF7B5EA7), "t")
        fun key(title: String) = BoxFrontKey.of(true, title, "", "", look, Size(10f, 10f), 1f, 1f)
        fun image() = ImageBitmap(10, 10)
        val a = cache.getOrPut(key("a")) { image() }
        cache.getOrPut(key("b")) { image() }
        cache.getOrPut(key("c")) { image() }
        var rebuilt = false
        cache.getOrPut(key("a")) { rebuilt = true; image() }
        assertEquals("the oldest was evicted", true, rebuilt)
        assertNotNull(a)
        var keptBuilt = false
        cache.getOrPut(key("c")) { keptBuilt = true; image() }
        assertEquals("the newest is kept", false, keptBuilt)
    }

    @Test
    fun aNullImageIsNotStoredAndClearEmptiesTheCache() {
        val cache = BoxFrontImages(1 shl 20)
        val look = boxFieldFor(Color(0xFF7B5EA7), "t")
        val key = BoxFrontKey.of(true, "t", "", "", look, Size(10f, 10f), 1f, 1f)
        assertNull(cache.getOrPut(key) { null })
        val image = ImageBitmap(10, 10)
        assertSame(image, cache.getOrPut(key) { image })
        cache.clear()
        val next = ImageBitmap(10, 10)
        assertSame(next, cache.getOrPut(key) { next })
    }
}
