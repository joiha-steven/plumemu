package com.plumemu.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Text tiles (no box art) with a word nearly as wide as the tile and one wider than it: whole words, "…" after the cut one. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class TextTileWordsScreenshotTest {
    @Test
    fun textTilesKeepWholeWords() =
        ReferenceShots.capture("text_tiles_words") {
            val layout = PlumTheme.layout
            val cell = DpSize(layout.gridCoverWidth(MixedGridShape), layout.gridRowHeight(MixedGridShape))
            Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                for (title in listOf("Captain Toad: Treasure Tracker", "Treasure", "Supercalifragilisticexpialidocious Mario")) {
                    CoverArt(title, null, cell, Alignment.BottomCenter, 6.dp, PlumDimens.coverPaddingPortrait, subtitle = "1998", titleStyle = PlumTheme.type.coverTitle(layout.gridSize))
                }
            }
        }
}
