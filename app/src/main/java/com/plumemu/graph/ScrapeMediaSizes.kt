package com.plumemu.graph

import com.plumemu.scraper.media.MediaSize

/**
 * The pixel sizes every box front and disc picture is saved at (own aspect,
 * enough for the grid, the details column and the Thor's bottom screen). The engine fits each
 * picture inside a size, keeping its aspect, never enlarging it, and asks ScreenScraper for no
 * more than the largest (scraper/README.md › What it does, 8).
 *
 * - [Grid], 560x400 px: the largest grid cell of docs/design/rules.md › Grid on the widest screen
 *   in the device table, the Thor's upper screen (837 dp wide, 1920 px, so 2.29 px per dp). Grid
 *   area 837 - 96 = 741 dp. Landscape covers: 3 columns, (741 - 2 x 14) / 3 = 238 dp wide, 238 /
 *   1.4 = 170 dp high, 545 x 390 px. Portrait covers: 6 columns, (741 - 5 x 12) / 6 = 114 dp wide,
 *   114 / 0.78 = 146 dp high, 260 x 334 px. 560 x 400 holds both, so a grid of covers decodes a
 *   picture a quarter the size of [Large]. These are the board's two shapes only: since grid step
 *   G1 each system's cells follow its own ratio, and 400 px is too short for some of them on the
 *   Thor (PSP at 0.575: 114 / 0.575 = 198 dp, 454 px; a landscape median of 1.2 to 1.34: 238 dp
 *   wide, 178 to 198 dp, 408 to 454 px), and the Large grid size is larger still (PSP: 238 dp,
 *   546 px). The saved sizes stay: the grid draws the large file wherever the
 *   grid one is shorter than the picture's drawn height (`home/HomeGrid.kt` `gridPicture`,
 *   docs/design/rules.md › Grid).
 * - [Large], 1240x1080 px: the Thor's bottom screen, 538 x 468 dp at 369 dpi. Also covers the
 *   details column (248 dp, 569 px on the Thor) and the narrow details cover (228 x 164 dp).
 *   A box front is requested no taller than 720 px (the largest draw, the second-screen card, is
 *   about 506 px tall), so its saved large picture is at most 720 tall; 1240x1080 stays the
 *   ceiling and the disc's size.
 *
 * [ScrapeMediaSizesTest] derives the same numbers from the rules, so a change there shows here.
 */
internal object ScrapeMediaSizes {
    val Grid = MediaSize("grid", 560, 400)
    val Large = MediaSize("large", 1240, 1080)

    val all: List<MediaSize> = listOf(Grid, Large)
}
