package com.plumemu.secondscreen

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ApplicationProvider
import com.plumemu.MainScreen
import com.plumemu.content.HomeState
import com.plumemu.coredata.backup.SettingsJson
import com.plumemu.coredata.settings.HomeScreenSettings
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeCallbacks
import com.plumemu.home.HomeTags
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.home.withSystemInfo
import com.plumemu.settings.AppearanceCache
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The band on the main screen: what the second screen says, whatever a stored choice (or a restored backup) holds. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SystemInfoBandOnMainScreenTest {
    private val compose = appComposeRule()

    @get:Rule val rules = rulesAround(compose)

    private lateinit var router: InputRouter
    private var inUse by mutableStateOf(false)

    private fun cache() = AppearanceCache(ApplicationProvider.getApplicationContext<Context>())

    private fun show(settings: PlumSettings) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            MainScreen(settings, HomeState.Ready(FakeHomeContent.board().withSystemInfo()), router, HomeCallbacks(), onRetry = {}, isSecondScreenInUse = inUse)
        }
        compose.runOnIdle { router.send(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX)) }
        compose.waitForIdle()
    }

    private fun bands() = compose.onAllNodesWithTag(HomeTags.SYSTEM_BAND, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun restored(settings: PlumSettings): PlumSettings = SettingsJson.decode(SettingsJson.encode(settings).toString()).settings

    private fun backup(stored: Boolean?): PlumSettings {
        val back = restored(PlumSettings.Defaults.copy(homeScreen = HomeScreenSettings(showSystemInfo = stored)))
        assertEquals("the backup keeps what was stored", stored, back.homeScreen.showSystemInfo)
        return back
    }

    @Test
    fun aSingleScreenShowsTheBandEvenWithAnOffFromABackup() {
        show(backup(false))
        assertEquals(1, bands())
    }

    @Test
    fun aSecondScreenInUseHidesTheBandEvenWithAnOnFromABackup() {
        inUse = true
        show(backup(true))
        assertEquals(0, bands())
    }

    @Test
    fun aSingleScreenShowsTheBandWithNothingStored() {
        show(PlumSettings.Defaults)
        assertEquals(1, bands())
    }

    @Test
    fun switchingTheSecondScreenOffBringsTheBandBackAtOnce() {
        inUse = true
        show(backup(null))
        assertEquals(0, bands())
        inUse = false
        compose.waitForIdle()
        assertEquals(1, bands())
    }

    /** The Activity starts the band from the cache's last answer: on a two-screen device the first frame has no band, so nothing drops out later. */
    @Test
    fun theFirstFrameOnADualScreenDeviceHasNoBandWhenTheCacheSaysInUse() {
        cache().writeSecondScreenInUse(true)
        inUse = cache().wasSecondScreenInUse() // a recreated Activity reads a new cache over the same file
        show(PlumSettings.Defaults)
        assertEquals(0, bands())
    }

    @Test
    fun aSingleScreenWithNothingCachedShowsTheBandInTheFirstFrame() {
        inUse = cache().wasSecondScreenInUse()
        show(PlumSettings.Defaults)
        assertEquals(1, bands())
    }
}

