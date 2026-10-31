package com.plumemu.home

import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The home states that only exist with box art and a width between the two original reference
 * sizes: the systems column open and the games list focused with the details beside it, at the
 * 4:3 screens (640x480 and 640x432, the Retroid Pocket Nova) as well as the original two. The
 * other 4:3 home shots sit with their screens (HomeScreenshotTest, HomeArtScreenshotTest ...).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeFourThreeScreenshotTest {
    @Test
    fun artSystemsFocused() = ReferenceShots.capture("home_art_systems", sizes = ReferenceShots.Size.entries) { HomeAt(FakeHomeContent.withArtwork(), BoardSystemsFocused) }

    @Test
    fun artGamesFocused() = ReferenceShots.capture("home_art_games", sizes = ReferenceShots.Size.entries) { HomeAt(FakeHomeContent.withArtwork(), BoardGamesFocused) }
}
