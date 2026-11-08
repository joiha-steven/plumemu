package com.plumemu.secondscreen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.SettingsTheme
import com.plumemu.settings.withColors
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The system card in each colour set's font and text size (the looks of `SecondScreenColorSetsScreenshotTest`): the three tiles are shown,
 * or, when they truly cannot fit, no empty band taller than one status line is left above the foot.
 */
abstract class SecondScreenColorSetsFitBase(
    private val size: SecondSize,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var stored by mutableStateOf(PlumSettings())
    private var content by mutableStateOf<SecondScreenContent>(SecondScreenContent.Calm)

    @Before
    fun hostTheCard() {
        RuntimeEnvironment.setQualifiers("w${size.widthDp}dp-h${size.heightDp}dp-land-xhdpi")
        compose.setContent { SettingsTheme(stored) { SecondScreenCard(content, images = SecondScreenStatisticsSamples.covers()) } }
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    @Test
    fun systemTilesShowOrNoBandIsLeftInEveryLook() {
        val faults = mutableListOf<String>()
        val looks =
            listOf(
                Pair(ColorSet.Ivory, TextSizeSetting.Medium),
                Pair(ColorSet.PeaGreen, TextSizeSetting.Large),
                Pair(ColorSet.Daylight, TextSizeSetting.Largest),
            )
        for ((set, textSize) in looks) {
            content = SecondScreenContent.Calm
            compose.waitForIdle()
            stored = PlumSettings(appearance = AppearanceSettings().withColors(set).copy(textSize = textSize, ownColors = null))
            content = SYSTEM_WITH_LAST
            compose.waitForIdle()
            val name = "${set.name} ${size.name}"
            if (compose.onAllNodesWithTag(EntryTags.TILE_PLAY).fetchSemanticsNodes().any { it.layoutInfo.isPlaced }) continue
            // Tiles gone: what is left above the foot must not leave a band of more than a status line.
            val above = if (compose.onAllNodesWithTag(EntryTags.STATUS).fetchSemanticsNodes().isNotEmpty()) bounds(EntryTags.STATUS).bottom else bounds(EntryTags.COVERS).bottom
            val band = bounds(EntryTags.FOOT).top - above
            val line = bounds(EntryTags.STATUS).height
            if (band > line) faults += "$name: tiles dropped and a ${band}px band is left above the foot (status ${line}px)"
        }
        if (faults.isNotEmpty()) fail(faults.joinToString("\n"))
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenColorSetsFitRoomyTest : SecondScreenColorSetsFitBase(SecondSize.Roomy)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenColorSetsFitCompactTest : SecondScreenColorSetsFitBase(SecondSize.Compact)
