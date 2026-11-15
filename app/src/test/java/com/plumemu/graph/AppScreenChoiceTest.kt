package com.plumemu.graph

import android.os.Build
import com.plumemu.coredata.settings.AppsScreen
import com.plumemu.coredata.settings.SecondScreenSettings
import com.plumemu.secondscreen.ScreenKind
import com.plumemu.secondscreen.SecondScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which display an app of the Apps entry opens on: the second screen only when found, on and wanted. */
class AppScreenChoiceTest {
    private val screen = SecondScreen(displayId = 4, name = "bottom", kind = ScreenKind.BuiltIn)
    private val on = SecondScreenSettings(enabled = true, appsOn = AppsScreen.Bottom)

    /** Plain JUnit has no SDK level: the choice is asked for API 34 unless a test says otherwise. */
    private fun choose(
        settings: SecondScreenSettings,
        screen: SecondScreen?,
        otherScreen: Boolean,
        sdk: Int = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
    ) = appDisplayId(settings, screen, otherScreen, sdk)

    @Test
    fun bottomWhenFoundOnAndWanted() = assertEquals(4, choose(on, screen, otherScreen = false))

    @Test
    fun noScreenFoundIsThisScreen() = assertNull(choose(on, null, otherScreen = false))

    @Test
    fun aScreenSwitchedOffIsThisScreen() = assertNull(choose(on.copy(enabled = false), screen, otherScreen = false))

    @Test
    fun theSettingTopIsThisScreen() = assertNull(choose(on.copy(appsOn = AppsScreen.Top), screen, otherScreen = false))

    @Test
    fun aOneOffFlipsTopToBottom() = assertEquals(4, choose(on.copy(appsOn = AppsScreen.Top), screen, otherScreen = true))

    @Test
    fun aOneOffFlipsBottomToTop() = assertNull(choose(on, screen, otherScreen = true))

    @Test
    fun aOneOffToTheBottomNeedsTheScreenToo() {
        val top = on.copy(appsOn = AppsScreen.Top)
        assertNull(choose(top, null, otherScreen = true))
        assertNull(choose(top.copy(enabled = false), screen, otherScreen = true))
    }

    @Test
    fun anUnchosenSwitchFollowsTheScreenKind() {
        val unchosen = on.copy(enabled = null)
        assertEquals(4, choose(unchosen, screen, otherScreen = false))
        assertNull(choose(unchosen, screen.copy(kind = ScreenKind.Unsure), otherScreen = false))
    }

    @Test
    fun belowApi26ThereIsNoDisplayOption() {
        assertNull(choose(on, screen, otherScreen = false, sdk = Build.VERSION_CODES.N))
        assertEquals(4, choose(on, screen, otherScreen = false, sdk = Build.VERSION_CODES.O))
    }

    private fun offered(
        settings: SecondScreenSettings,
        screen: SecondScreen?,
        sdk: Int = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
    ) = otherAppsScreen(settings, screen, sdk)

    @Test
    fun theMenuOffersTheScreenTheSettingDoesNotName() {
        assertEquals(AppsScreen.Top, offered(on, screen))
        assertEquals(AppsScreen.Bottom, offered(on.copy(appsOn = AppsScreen.Top), screen))
    }

    @Test
    fun theMenuOffersNothingWithoutAnOnSecondScreenOrBelowApi26() {
        assertNull(offered(on, null))
        assertNull(offered(on.copy(enabled = false), screen))
        assertNull(offered(on, screen, sdk = Build.VERSION_CODES.N))
    }
}
