package com.plumemu.thortype

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.game.LocalBadgeImages
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.secondscreen.BOARD_GAME_NO_RA
import com.plumemu.secondscreen.CacheOnlyBadges
import com.plumemu.secondscreen.PlayWeeks
import com.plumemu.secondscreen.SecondScreenCard
import com.plumemu.secondscreen.SecondScreenContent
import com.plumemu.secondscreen.WithPhysicalTextScale
import com.plumemu.coreui.theme.SecondScreenPhysical
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The game card on the Thor's bottom screen, 1240x1080 px: review crops of the reported case (never played, a description, a file name). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w538dp-h468dp-land-369dpi")
class ThorTypeSecondShotsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var content by mutableStateOf<SecondScreenContent>(OWNER_CASE)

    @Test
    fun theCard() {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                WithPhysicalTextScale(SecondScreenPhysical.thor) { CompositionLocalProvider(LocalBadgeImages provides CacheOnlyBadges()) { SecondScreenCard(content) } }
            }
        }
        compose.waitForIdle()
        val (w, h) = ThorTypeShots.save(compose, "spec_thortype_second_owner_case")
        assertTrue("blank shot $w x $h", w > 100 && h > 100)
    }

    private companion object {
        val OWNER_CASE: SecondScreenContent =
            BOARD_GAME_NO_RA.copy(
                title = "Grand Theft Auto - San Andreas",
                systemName = "PlayStation 2",
                systemShort = "PS2",
                lastPlayedDaysAgo = null,
                playTimeMinutes = 0,
                fileName = "Grand Theft Auto - San Andreas (USA) (v3.00).iso",
                weeks = PlayWeeks(List(8) { 0L }, 0, 0),
            )
    }
}
