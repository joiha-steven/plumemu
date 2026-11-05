package com.plumemu.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.components.HINT_KEY_CAP_TAG
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.footerGapToKeys
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * On every step, scrolled to its end, the last whole part (a card, a button's collar, a note) ends at least [PlumDimens.footerGap]
 * above the top of the hint keys' collars: the keys reach above the bar's own bounds, so the gap is measured to what is drawn.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SetupBarGapTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val steps: List<Pair<String, SetupUiState>> =
        listOf(
            "chooseFolder" to SetupFakes.chooseFolder,
            "loadFailed" to SetupFakes.loadFailed,
            "folderChosen" to SetupFakes.folderChosen,
            "scanning" to SetupFakes.scanning,
            "confirm" to SetupFakes.confirm,
            "look" to SetupFakes.look,
            "secondScreen" to SetupFakes.secondScreen,
            "boxArtQuestion" to SetupFakes.boxArtQuestion,
            "ready" to SetupFakes.ready,
            "readyWithBoxArt" to SetupFakes.readyWithBoxArt,
            "readyStillNaming" to SetupFakes.readyStillNaming,
        )

    private val partTags: List<String> =
        SetupControl.entries.map { SetupTags.control(it) } +
            listOf(SetupTags.FOLDER_CARD, SetupTags.EMULATORS, SetupTags.LOOK_TILE, SetupTags.NOTE, SetupTags.BOX_ART_COMING, SetupTags.PROGRESS, SetupTags.MESSAGE, SetupTags.FOUND)

    private fun check() {
        var state by mutableStateOf(steps.first().second)
        var size by mutableStateOf(TextSize.Small)
        var density = Density(1f)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) {
                density = LocalDensity.current
                SetupAt(state)
            }
        }
        val problems = mutableListOf<String>()
        for (each in listOf(TextSize.Small, TextSize.Largest)) {
            size = each
            for ((name, step) in steps) {
                state = step
                compose.waitForIdle()
                val scroll = compose.onNodeWithTag(SetupTags.SCROLL)
                repeat(3) {
                    scroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
                    compose.waitForIdle()
                }
                val caps = compose.onAllNodesWithTag(HINT_KEY_CAP_TAG, useUnmergedTree = true).fetchSemanticsNodes()
                val keysTop = with(density) { caps.minOf { it.boundsInRoot.top }.toDp() } - (CollarSize.Hint.lip + CollarSize.Hint.groove)
                val bottom =
                    partTags.flatMap { tag -> compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().indices.map { tag to it } }
                        .maxOf { (tag, i) -> compose.onAllNodesWithTag(tag, useUnmergedTree = true)[i].getUnclippedBoundsInRoot().bottom }
                val gap = keysTop - bottom
                if (gap < footerGapToKeys(each) - SLACK) problems += "$each, $name: the last part ends ${gap.value} dp above the keys' top"
            }
        }
        assertTrue("needs the footer gap less the collars' reach:\n${problems.joinToString("\n")}", problems.isEmpty())
    }

    @Test @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun at576() = check()

    @Test @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun at768() = check()

    @Test @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun at832() = check()

    @Test @Config(qualifiers = "w640dp-h480dp-land-xhdpi")
    fun at640x480() = check()

    private companion object {
        val SLACK = 0.5.dp
    }
}
