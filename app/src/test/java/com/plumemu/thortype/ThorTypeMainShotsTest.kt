package com.plumemu.thortype

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.BoardGamesFocused
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeAt
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The home list (top bar, year and region columns) as the Thor's top screen and the two small references draw it: review crops. */
abstract class ThorTypeMainShots(
    private val prefix: String,
    private val size: TextSize,
) {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var settings by mutableStateOf(PlumThemeSettings.Graphite)

    @Test
    fun theList() {
        compose.setContent { PlumTheme(settings) { HomeAt(FakeHomeContent.withArtwork(), BoardGamesFocused) } }
        for ((look, base) in listOf("graphite" to PlumThemeSettings.Graphite, "paper" to PlumThemeSettings.Paper)) {
            settings = base.copy(textSize = size)
            compose.waitForIdle()
            val (w, h) = ThorTypeShots.save(compose, "spec_thortype_main_${prefix}_$look")
            assertTrue("blank shot $w x $h", w > 100 && h > 100)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-367dpi")
class ThorTypeMain1920ShotsTest : ThorTypeMainShots("1920", TextSize.Large)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class ThorTypeMain768ShotsTest : ThorTypeMainShots("768", TextSize.Small)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class ThorTypeMain576ShotsTest : ThorTypeMainShots("576", TextSize.Largest)
