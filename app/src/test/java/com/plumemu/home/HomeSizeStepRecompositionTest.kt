package com.plumemu.home

import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.ProvideGridSize
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A Size step (L1/R1) re-packs the grid under the same cells; each visible cover must not run its composables again and again
 * for it (42-48 recompositions a press on the handhelds, all on the UI thread). Counted with Compose's composition tracer.
 */
@OptIn(InternalComposeTracingApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeSizeStepRecompositionTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val calls = mutableListOf<String>()

    @Before
    fun traceCompositions() {
        Composer.setTracer(
            object : CompositionTracer {
                override fun isTraceInProgress(): Boolean = true

                override fun traceEventStart(
                    key: Int,
                    dirty1: Int,
                    dirty2: Int,
                    info: String,
                ) {
                    calls += info
                }

                override fun traceEventEnd() = Unit
            },
        )
    }

    @After
    fun stopTracing() = Composer.setTracer(null)

    private val isCover = SemanticsMatcher("a grid cover") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("home_cover_") == true }

    @Test
    fun aSizeStepRunsEachVisibleCoverAtMostTwice() {
        var size by mutableStateOf(GridSize.Large)
        compose.setContent {
            PlumTheme { ProvideGridSize(size) { HomeWithArt(rememberSource { realCoverSource() }, gridActions(FakeHomeContent.BOARD_SNES_INDEX, 0), gridIds = setOf("snes")) } }
        }
        compose.waitForIdle()
        for (next in listOf(GridSize.Medium, GridSize.Small, GridSize.Large)) {
            compose.runOnIdle { calls.clear() }
            size = next
            compose.waitForIdle()
            val counts = calls.map { it.substringBefore(' ') }.filter { it.startsWith("com.plumemu.") }.groupingBy { it.substringAfterLast('.') }.eachCount()
            println("SIZESTEP $next $counts")
            val perCover = counts.filterKeys { it in setOf("GridCoverAt", "GridCover", "CoverArt") }
            val visible = compose.onAllNodes(isCover, useUnmergedTree = true).fetchSemanticsNodes().size
            assertTrue("covers are shown at $next", visible > 0)
            for ((name, n) in perCover) assertTrue("$name ran $n times for $visible visible covers at $next: $counts", n <= 2 * visible)
        }
    }
}
