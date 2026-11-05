package com.plumemu.home

import androidx.compose.runtime.CompositionLocalProvider
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.TextSize
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The home empty states at 768x432, 576x432 and 832x468 in Graphite and Paper, and the largest text. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeEmptyStateShotsTest {
    private fun shot(
        name: String,
        why: EmptyLibrary,
        textSize: TextSize = TextSize.Small,
        inButtons: Boolean = false,
    ) = ReferenceShots.capture(name, textSize = textSize, sizes = ReferenceShots.Size.WithPocket6) {
        val notices = HomeNotices(emptyLibrary = MutableStateFlow(why)).also { it.emptyFocus.inButtons = inButtons }
        CompositionLocalProvider(LocalHomeNotices provides notices) { HomeAt(FakeHomeContent.noGames()) }
    }

    @Test fun noFolder() = shot("home_empty_state_no_folder", EmptyLibrary.NoFolder)

    @Test fun noGames() = shot("home_empty_state_no_games", EmptyLibrary.NoGames)

    /** The board's empty state: the primary button (Files not added) focused. */
    @Test fun noGamesPrimaryFocused() = shot("home_empty_state_no_games_primary", EmptyLibrary.NoGames, inButtons = true)

    @Test fun noFolderLargest() = shot("home_empty_state_no_folder", EmptyLibrary.NoFolder, TextSize.Largest)

    @Test fun noGamesLargest() = shot("home_empty_state_no_games", EmptyLibrary.NoGames, TextSize.Largest)
}
