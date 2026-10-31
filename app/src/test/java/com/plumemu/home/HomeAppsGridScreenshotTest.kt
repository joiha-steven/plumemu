package com.plumemu.home

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.TextSize
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Apps as a grid of icons, signed out of ScreenScraper: made-up apps with
 * plain generated icons, long names in two lines with "…", "Notes" without an icon (its stand-in), the
 * focus on "Music" (row 2). At 768x432 and 576x432, Graphite and Paper, and at Largest.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeAppsGridScreenshotTest {
    @Test
    fun appsGrid() = ReferenceShots.capture("home_grid_apps") { AppsGridBoard(focused = FOCUSED) }

    @Test
    fun appsGridAtLargestText() = ReferenceShots.capture("home_grid_apps", textSize = TextSize.Largest) { AppsGridBoard(focused = FOCUSED) }

    /** The focus on the last app: the grid scrolled to its end, its top row whole and under the header's gap, no tail of a cut row. */
    @Test
    fun appsGridScrolledToTheEnd() = ReferenceShots.capture("home_grid_apps_end") { AppsGridBoard(focused = AppsGridApps.lastIndex) }

    @Test
    fun appsGridScrolledToTheEndAtLargestText() = ReferenceShots.capture("home_grid_apps_end", textSize = TextSize.Largest) { AppsGridBoard(focused = AppsGridApps.lastIndex) }

    private companion object {
        /** "Music": on the second row at every width and text size measured. */
        const val FOCUSED = 8
    }
}
