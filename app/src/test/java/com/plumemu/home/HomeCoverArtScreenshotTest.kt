package com.plumemu.home

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.TextSize
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The main screen with real box art: covers at their own shape on the
 * grid's shelves, titled placeholders, pictures still decoding, the details column with its cover,
 * facts and synopsis, a long title and publisher, and the list's years. At 768x432 and 576x432,
 * Graphite and Paper ([ReferenceShots]).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeCoverArtScreenshotTest {
    private val allTitles = FakeHomeContent.withArtwork().entries.flatMap { e -> e.rows.map { it.title } }.toSet()

    @Test
    fun gridWithArt() = ReferenceShots.capture("home_art_grid") { CoverGridBoard(rememberSource { coverArtSource() }) }

    @Test
    fun gridPortraitWithArt() = ReferenceShots.capture("home_art_grid_portrait") { CoverGridPortraitBoard(rememberSource { coverArtSource() }) }

    @Test
    fun gridWithoutArt() = ReferenceShots.capture("home_art_grid_none") { CoverGridBoard(rememberSource { coverArtSource(noArt = allTitles) }) }

    /** Some covers still decoding (their slot in the picture's shape), some without art, the rest landed. */
    @Test
    fun gridMixed() =
        ReferenceShots.capture("home_art_grid_mixed") {
            CoverGridBoard(
                rememberSource {
                    coverArtSource(
                        noArt = setOf("EarthBound", "Super Mario World"),
                        pending = setOf(pictureOf("Donkey Kong Country 2"), pictureOf("Super Metroid"), pictureOf("Zelda: A Link to the Past")),
                    )
                },
            )
        }

    @Test
    fun detailsWithArt() = ReferenceShots.capture("home_art_details") { CoverDetailsBoard(rememberSource { coverArtSource() }) }

    @Test
    fun detailsWithoutArt() = ReferenceShots.capture("home_art_details_none") { CoverDetailsBoard(rememberSource { coverArtSource(noArt = setOf("Super Metroid")) }) }

    @Test
    fun detailsLongTitleAndPublisher() =
        ReferenceShots.capture("home_art_details_long") {
            val source = rememberSource { longSource() }
            CoverDetailsBoard(source, withSuperMetroidTitled(LONG_DETAILS_TITLE))
        }

    /** The games list with box art: years from the results, the region where a game has none. */
    @Test
    fun listWithYears() =
        ReferenceShots.capture("home_art_list_years") {
            HomeWithArt(rememberSource { coverArtSource(absent = setOf("EarthBound", "Yoshi's Island")) }, BoardGamesFocused)
        }

    /** The second screen's window is up: no details column, the list takes `width − 96` and writes year · region. */
    @Test
    fun listWithYearsWhileTheSecondScreenShowsTheDetails() =
        ReferenceShots.capture("home_art_list_second_screen") {
            HomeWithArt(rememberSource { coverArtSource(absent = setOf("EarthBound", "Yoshi's Island")) }, BoardGamesFocused, detailsOnSecondScreen = true)
        }

    @Test
    fun listWithYearsWhileTheSecondScreenShowsTheDetailsAtLargestText() =
        ReferenceShots.capture("home_art_list_second_screen", textSize = TextSize.Largest) {
            HomeWithArt(rememberSource { coverArtSource(absent = setOf("EarthBound", "Yoshi's Island")) }, BoardGamesFocused, detailsOnSecondScreen = true)
        }

    private fun pictureOf(title: String): String {
        val row = FakeHomeContent.withArtwork().entries[FakeHomeContent.BOARD_SNES_INDEX].rows.first { it.title == title }
        return "/media/${row.id}/box-front-grid.webp"
    }
}

/** Super Metroid's result with a long publisher. */
internal fun longSource(): FakeArtSource {
    val source = coverArtSource()
    val key = artKey(SUPER_METROID_ID)
    val art = source.results.value.getValue(key)
    source.results.value = source.results.value + (key to art.copy(details = art.details.copy(publisher = LONG_PUBLISHER)))
    return source
}
