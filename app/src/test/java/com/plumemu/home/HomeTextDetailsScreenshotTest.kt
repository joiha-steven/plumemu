package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.TextSize
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The details column's play facts as the library reads them (not on the board, so shot for
 * review): the text-only column without box art at 768 (at 576 the list alone, unchanged: no
 * column on narrow screens), at Medium and at Largest; and the box-art column with the read facts.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeTextDetailsScreenshotTest {
    @Test
    fun textOnlyColumn() =
        ReferenceShots.capture("home_details_text", sizes = ReferenceShots.Size.entries) { WithFacts { HomeAt(LibraryLikeContent.of(), LibraryLikeContent.gamesFocused(LibraryLikeContent.SUPER_METROID)) } }

    @Test
    fun textOnlyColumnLargest() =
        ReferenceShots.capture("home_details_text", textSize = TextSize.Largest) {
            WithFacts { HomeAt(LibraryLikeContent.of(), LibraryLikeContent.gamesFocused(LibraryLikeContent.SUPER_METROID)) }
        }

    @Test
    fun textOnlyColumnInRecent() =
        ReferenceShots.capture("home_details_text_recent") { WithFacts { HomeAt(LibraryLikeContent.of(), listOf(Action.TapItem(Column.Systems, 0), Action.Enter)) } }

    @Test
    fun boxArtColumnWithReadFacts() =
        ReferenceShots.capture("home_details_read_facts") {
            WithFacts { HomeAt(LibraryLikeContent.of(hasBoxArt = true), LibraryLikeContent.gamesFocused(LibraryLikeContent.SUPER_METROID)) }
        }
}

@Composable
private fun WithFacts(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalHomePlayFacts provides FakePlayFacts(), content = content)
