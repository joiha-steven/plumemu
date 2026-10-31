package com.plumemu.home

import android.content.Intent
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.plumemu.battery.BatteryPace
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.TextSize
import com.plumemu.game.EmulatorToStart
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.game.sampleFacts
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The picking line on one screen: the top screen's details column and the game
 * menu's Info, for a game measured before (30% an hour, the battery at 60%). The same screens without
 * a usual pace are `home_details_text` and `game_info` (HomeTextDetailsScreenshotTest, GamePanelScreenshotTest).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class BatteryLineScreenshotTest {
    // The system's battery broadcast is sticky; a test can only send one with this deprecated call.
    @Suppress("DEPRECATION")
    @Before
    fun batteryIs60Percent() {
        RuntimeEnvironment.getApplication().sendStickyBroadcast(
            Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_LEVEL, 60).putExtra(BatteryManager.EXTRA_SCALE, 100),
        )
    }

    @Composable
    private fun WithPace(content: @Composable () -> Unit) = CompositionLocalProvider(LocalHomePlayFacts provides FakePlayFacts(pace = BatteryPace(minutesOnCharge = 120.0, percentPerHour = 30.0)), content = content)

    @Test
    fun textOnlyColumn() =
        ReferenceShots.capture("home_details_text_battery") { WithPace { HomeAt(LibraryLikeContent.of(), LibraryLikeContent.gamesFocused(LibraryLikeContent.SUPER_METROID)) } }

    @Test
    fun textOnlyColumnLargest() =
        ReferenceShots.capture("home_details_text_battery", textSize = TextSize.Largest) {
            WithPace { HomeAt(LibraryLikeContent.of(), LibraryLikeContent.gamesFocused(LibraryLikeContent.SUPER_METROID)) }
        }

    @Test
    fun boxArtColumn() =
        ReferenceShots.capture("home_details_read_facts_battery") {
            WithPace { HomeAt(LibraryLikeContent.of(hasBoxArt = true), LibraryLikeContent.gamesFocused(LibraryLikeContent.SUPER_METROID)) }
        }

    @Test
    fun info() {
        val facts =
            sampleFacts(title = "Super Metroid", lastPlayedAt = System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(2)).copy(
                systemName = "Super Nintendo",
                regions = "USA,EUR",
                fileName = "Super Metroid (Japan, USA) (En) (Rev 1) [!].sfc",
                folderName = "ROMs/snes",
                sizeBytes = 3L * 1024 * 1024,
                emulator = EmulatorToStart("RetroArch (Snes9x)", isInstalled = true),
                batteryPace = BatteryPace(minutesOnCharge = 120.0, percentPerHour = 30.0),
            )
        ReferenceShots.capture("game_info_battery") {
            HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = OpenPanel(GameRef("snes-6", "Super Metroid"), PanelScreen.Info(facts)))
        }
    }
}
