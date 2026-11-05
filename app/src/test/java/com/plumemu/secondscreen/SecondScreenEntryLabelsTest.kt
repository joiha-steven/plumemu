package com.plumemu.secondscreen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.text.TextLayoutResult
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.FakeAppIcons
import com.plumemu.home.LocalHomeAppIcons
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A small capital label on an entry card never wraps: a one-word label ("ACHIEVEMENTS") broken after its eighth letter reads as a typo. */
abstract class SecondScreenEntryLabelsBase(
    private val size: SecondSize,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var settings by mutableStateOf(PlumThemeSettings.Default)
    private var content by mutableStateOf<SecondScreenContent>(SecondScreenContent.Calm)

    @Before
    fun hostTheCard() {
        RuntimeEnvironment.setQualifiers("w${size.widthDp}dp-h${size.heightDp}dp-land-xhdpi")
        compose.setContent { PlumTheme(settings) { CompositionLocalProvider(LocalHomeAppIcons provides FakeAppIcons(missing = setOf(APP_WITHOUT_ICON_KEY))) { SecondScreenCard(content) } } }
    }

    @Test
    fun everyLabelIsOneLineAtEveryTextSize() {
        for (textSize in TextSize.entries) {
            for ((name, sample) in ALL_SAMPLES.filter { it.second is EntryContent }) {
                settings = PlumThemeSettings.Default.copy(textSize = textSize)
                content = sample
                compose.waitForIdle()
                for (node in compose.onAllNodesWithTag(EntryTags.LABEL).fetchSemanticsNodes()) {
                    val layouts = mutableListOf<TextLayoutResult>()
                    node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                    layouts.forEach { assertEquals("$name $size $textSize: '${node.config[SemanticsProperties.Text].joinToString("")}' wraps", 1, it.lineCount) }
                }
            }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenEntryLabelsRoomyTest : SecondScreenEntryLabelsBase(SecondSize.Roomy)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenEntryLabelsCompactTest : SecondScreenEntryLabelsBase(SecondSize.Compact)
