package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Rule

/**
 * Evidence pictures for a UI change: one PNG per theme under `build/ui-evidence/<item>/<phase>_<size>_<theme>.png` at the repository root.
 * Off in the normal suite (they assert nothing): they run only with `UI_EVIDENCE=1` or `UI_EVIDENCE_PHASE` set in the environment.
 * The phase is `UI_EVIDENCE_PHASE` (`before` or `after`, default `after`). Not a golden: nothing compares against these files.
 */
abstract class UiEvidenceBase(
    private val size: String,
) {
    protected val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    protected fun shoot(
        item: String,
        textSize: TextSize = TextSize.Small,
        scene: @Composable () -> Unit,
    ) {
        assumeTrue("evidence pictures: set UI_EVIDENCE=1", System.getenv("UI_EVIDENCE") == "1" || System.getenv("UI_EVIDENCE_PHASE") != null)
        var paper by mutableStateOf(false)
        compose.setContent {
            PlumTheme((if (paper) PlumThemeSettings.Paper else PlumThemeSettings.Graphite).copy(textSize = textSize)) {
                key(paper) { scene() }
            }
        }
        val phase = System.getenv("UI_EVIDENCE_PHASE") ?: "after"
        val dir = File("../build/ui-evidence/$item").apply { mkdirs() }
        for (each in listOf(false, true)) {
            paper = each
            compose.waitForIdle()
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            File(dir, "${phase}_${size}_${if (each) "paper" else "graphite"}.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
