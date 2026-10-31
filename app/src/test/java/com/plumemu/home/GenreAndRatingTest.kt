package com.plumemu.home

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.plumemu.coreui.testing.ReferenceShots
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The details' genre line: "Racing · 8/10", ScreenScraper's rating out of 20 shown out of 10, no players count. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GenreAndRatingTest {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun details(
        genre: String,
        rating: Int?,
    ) = GameDetails(year = null, publisher = "", genre = genre, players = "1-4", description = "", rating = rating)

    /** The genre lines of [all], in [locale]. */
    private fun linesOf(
        all: List<GameDetails>,
        locale: Locale = Locale.US,
    ): List<String> {
        val lines = mutableListOf<String>()
        val configuration = Configuration().apply { setLocale(locale) }
        compose.setContent { CompositionLocalProvider(LocalConfiguration provides configuration) { all.forEach { lines += genreAndRating(it) } } }
        compose.waitForIdle()
        return lines.takeLast(all.size)
    }

    @Test
    fun theRatingIsOutOfTenWithADecimalOnlyWhenNotWhole() {
        val lines = linesOf(listOf(details("Racing", 16), details("Racing", 20), details("Racing", 1), details("Racing", 17)))
        assertEquals(listOf("Racing · 8/10", "Racing · 10/10", "Racing · 0.5/10", "Racing · 8.5/10"), lines)
    }

    @Test
    fun theDecimalFollowsTheLocale() {
        assertEquals(listOf("Racing · 8,5/10"), linesOf(listOf(details("Racing", 17)), Locale.FRANCE))
    }

    @Test
    fun eitherPartAloneNoneWithNeitherAndNeverThePlayers() {
        val lines = linesOf(listOf(details("Racing", null), details("", 16), details("", null), details("Racing", 0), details("", 0)))
        // 0 is no rating: ScreenScraper gives no real 0/20.
        assertEquals(listOf("Racing", "8/10", "", "Racing", ""), lines)
    }
}
