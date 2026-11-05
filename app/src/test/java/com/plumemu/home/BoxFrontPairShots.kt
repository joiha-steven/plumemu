package com.plumemu.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coreui.components.coverBoxShadow
import com.plumemu.coreui.components.collar
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.CollarDimens
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Not a check: writes the covers of the V2 boards' "Covers, whole, in a thin collar" row as ours draw them, at 3 px per board px
 * (720 dpi), to `build/v2pairs/audit/pairs/ours_drawnbox_<preset>.png`, for pairing with a headless-Chrome crop of the board.
 * Runs only with `PLUMEMU_PAIRS=1` in the environment (and `-Proborazzi.test.record=true`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class BoxFrontPairShots {
    @Composable
    private fun Boxed(
        title: String,
        widthBoardPx: Int,
        line: String = "",
    ) {
        val corner = CollarDimens.coverBoxCorner
        Box(Modifier.collar(CollarSize.Cover, corner, halo = false) { 0f }) {
            Box(Modifier.size((widthBoardPx / 1.5f).dp, (156 / 1.5f).dp).coverBoxShadow(corner).clip(RoundedCornerShape(corner))) {
                PosterCover(title, "Super Nintendo", "SNES", Modifier.fillMaxSize(), line)
            }
        }
    }

    @Test
    fun coversRow() {
        assumeTrue(System.getenv("PLUMEMU_PAIRS") == "1")
        RuntimeEnvironment.setQualifiers("w640dp-h200dp-land-720dpi")
        for (preset in ReferenceShots.Preset.entries) {
            captureRoboImage(filePath = "../build/v2pairs/audit/pairs/ours_drawnbox_${preset.label}.png") {
                PlumTheme(preset.settings) {
                    Row(Modifier.fillMaxSize().background(PlumTheme.colors.bg).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                        Boxed("Super Metroid", 112, "Nintendo · 1994")
                        Boxed("Contra III", 214, "Konami · 1992")
                        Boxed("Castlevania: Symphony of the Night", 112, "Konami · 1997")
                        Boxed(PosterEdgeCases.VIETNAMESE, 112)
                        Boxed(text(0x110, 0x1b0, 0x1edd, 0x6e, 0x67, 0x20, 0x56, 0x1ec1, 0x20, 0x4e, 0x68, 0xe0, 0x20, 0x43, 0x1ee7, 0x61, 0x20, 0x42, 0xe9, 0x20, 0x4d, 0xe8, 0x6f), 214, "Publisher · 1995")
                    }
                }
            }
        }
    }
}
