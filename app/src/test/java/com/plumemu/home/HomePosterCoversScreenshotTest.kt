package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Poster covers for games without a picture (poster-covers plan, step 3), 768x432 and 576x432, Graphite
 * and Paper: a Super Nintendo grid signed out (twelve games, one very long title, one Japanese), a mixed Recent grid signed out
 * (each game in its own system's shape), the list with its details column signed out (768 only; 576 shows the list alone), and
 * a signed-in grid where some games have pictures and some posters.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomePosterCoversScreenshotTest {
    @Test
    fun singleSystemGridSignedOut() =
        ReferenceShots.capture("home_grid_posters_single") {
            HomeAt(posterSnesBoard(), gridActions(POSTER_SNES_INDEX, 1), gridIds = setOf("snes"))
        }

    @Test
    fun mixedRecentGridSignedOut() =
        ReferenceShots.capture("home_grid_posters_recent") {
            HomeAt(mixedSignedOutBoard(), gridActions(0, 1), gridIds = setOf("recent"))
        }

    @Test
    fun listWithDetailsColumnSignedOut() =
        ReferenceShots.capture("home_grid_posters_details") {
            WithFacts { HomeAt(posterSnesBoard(), listOf(Action.TapItem(Column.Systems, POSTER_SNES_INDEX), Action.Enter, Action.TapItem(Column.Games, 6))) }
        }

    @Test
    fun signedInGridWithPicturesAndPosters() =
        ReferenceShots.capture("home_grid_posters_signed_in") {
            val content = posterSnesBoardWithDetails()
            // Every third game has no result at all: its poster stands among the real pictures.
            val source =
                rememberSource {
                    realCoverSource(content).apply {
                        results.value = results.value.filterKeys { key -> content.entries[POSTER_SNES_INDEX].rows.indexOfFirst { artKey(it.id) == key } % 3 != 1 }
                    }
                }
            HomeWithArt(source, gridActions(POSTER_SNES_INDEX, 1), content, gridIds = setOf("snes"))
        }
}

@Composable
private fun WithFacts(content: @Composable () -> Unit) = CompositionLocalProvider(LocalHomePlayFacts provides FakePlayFacts(), content = content)
