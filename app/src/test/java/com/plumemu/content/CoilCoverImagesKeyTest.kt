package com.plumemu.content

import com.plumemu.home.ArtPicture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The memory cache key of a decoded cover: the engine writes a re-downloaded picture under the
 * same path, so the stored size and the result's time must change the key, or an old decode is
 * shown for the new file (or stretched to its new shape).
 */
class CoilCoverImagesKeyTest {
    private val picture = ArtPicture("/media/g1/box-front-grid.webp", 560, 400, version = 7)

    private fun key(p: ArtPicture = picture, w: Int = 280, h: Int = 200) = CoilCoverImages.cacheKey(p, w, h)

    @Test
    fun theSameFileAtTheSameSizeIsOneEntry() = assertEquals(key(), key(picture.copy()))

    @Test
    fun aFileSavedAgainWithAnotherShapeIsANewEntry() = assertNotEquals(key(), key(picture.copy(width = 400, height = 400)))

    @Test
    fun aFileSavedAgainAtTheSameShapeLaterIsANewEntry() = assertNotEquals(key(), key(picture.copy(version = 8)))

    @Test
    fun anotherDrawnSizeIsANewEntry() = assertNotEquals(key(), key(w = 300, h = 214))
}
