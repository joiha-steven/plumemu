package com.plumemu.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** [codePoints] as a string: the english-only guard allows no other script in a source file. */
internal fun text(vararg codePoints: Int): String = String(codePoints, 0, codePoints.size)

/** Pok, e with an acute accent, mon, then [rest]. */
internal fun pokemon(rest: String): String = text(0x50, 0x6F, 0x6B, 0xE9, 0x6D, 0x6F, 0x6E, 0x20) + rest

/** Titles and shapes the poster cover's tests share. Non-English text is built from code points (the english-only guard). */
internal object PosterEdgeCases {
    const val LONG = "The Legend of Zelda: A Link to the Past & Four Swords"
    val JAPANESE = text(0x30d5, 0x30a1, 0x30a4, 0x30ca, 0x30eb, 0x30d5, 0x30a1, 0x30f3, 0x30bf, 0x30b8, 0x30fc, 0x56, 0x49)
    val VIETNAMESE = text(0x48, 0x75, 0x79, 0x1ec1, 0x6e, 0x20, 0x54, 0x68, 0x6f, 0x1ea1, 0x69, 0x20, 0x52, 0x1ed3, 0x6e, 0x67, 0x20, 0x56, 0x69, 0x1ec7, 0x74)
    const val ONE_WORD = "Tetris"
    val titles = listOf(LONG, JAPANESE, VIETNAMESE, ONE_WORD, "Mega Man 2", "Super Mario World", pokemon("Crystal"))

    /** Width x height in dp: a small and a large cell, landscape (1.4) and tall (0.71). */
    val sizes = listOf(74f to 53f, 74f to 104f, 128f to 91f, 100f to 141f)

    /** Sample titles for the 23 looks, in palette order. */
    val sampleTitles =
        listOf(
            "Super Mario World", "Chrono Trigger", "Mega Man 2", "Mega Man 3", "Sonic the Hedgehog", "EarthBound", "Donkey Kong Country 2", "Super Metroid",
            "Castlevania", "Contra", "Final Fantasy VI", "Kirby's Dream Land", "Tetris", "Metal Slug", "F-Zero", "Street Fighter II",
            "Secret of Mana", pokemon("Crystal"), "Bomberman", "Zelda: A Link to the Past", "Gradius", "Yoshi's Island", "Pac-Man",
        )
}

/** The drawn box front on the screen background: a wall of the sample titles, then the hard cases. Graphite and Paper at 768x432 and 576x432. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class PosterCoverScreenshotTest {
    @Composable
    private fun Cover(
        title: String,
        width: Dp,
        height: Dp,
        system: String = "Super Nintendo",
        line: String = "",
    ) = PosterCover(title, system, "SNES", Modifier.size(width, height).clip(RoundedCornerShape(6.dp)), line)

    @Test
    fun wall() =
        ReferenceShots.capture("poster_cover_wall") {
            BoxWithConstraints(Modifier.fillMaxSize().background(PlumTheme.colors.bg).padding(12.dp)) {
                val gap = 6.dp
                val width = (maxWidth - gap * 7) / 8
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    PosterEdgeCases.sampleTitles.chunked(8).forEachIndexed { row, titles ->
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            titles.forEachIndexed { i, title -> Cover(title, width, width * 1.4f, line = if (i % 2 == 0) "Publisher \u00B7 199$i" else "") }
                        }
                    }
                }
            }
        }

    @Test
    fun hardCasesSmall() = hardCases("poster_cover_small", small = true)

    @Test
    fun hardCasesLarge() = hardCases("poster_cover_large", small = false)

    private fun hardCases(
        name: String,
        small: Boolean,
    ) = ReferenceShots.capture(name) {
        val (landscape, tall) = if (small) PosterEdgeCases.sizes[0] to PosterEdgeCases.sizes[1] else PosterEdgeCases.sizes[2] to PosterEdgeCases.sizes[3]
        // The small cells show the short name (SNES) for a long system name; the large ones the full name.
        val system = if (small) "Super Nintendo Entertainment System" else "Super Nintendo"
        val titles = listOf(PosterEdgeCases.LONG, PosterEdgeCases.JAPANESE, PosterEdgeCases.VIETNAMESE, "Championship")
        Column(Modifier.fillMaxSize().background(PlumTheme.colors.bg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (shape in listOf(landscape, tall)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    titles.forEachIndexed { i, title -> Cover(title, shape.first.dp, shape.second.dp, system, "Publisher \u00B7 1994") }
                }
            }
        }
    }
}
