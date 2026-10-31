package com.plumemu.settings.screen

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.plumemu.MainScreen
import com.plumemu.content.HomeState
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeCallbacks
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.sound.Heard
import com.plumemu.sound.UiSound
import com.plumemu.coredata.settings.SoundLevel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Sounds switch on the whole screen: opening Settings, turning sounds off (silent), turning them on (one confirm). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsScreenSoundsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val settings = mutableStateOf(PlumSettings(flags = SetupFlags(setupCompleted = true, homeRoleAsked = true)))
    private val heard = Heard()
    private lateinit var router: InputRouter

    private val host =
        SettingsHost(
            versionName = "test",
            update = { settings.value = it(settings.value) },
            resetAllSettings = { settings.value = settings.value.resetKeepingFlags() },
            resetLibrary = {},
        )

    private fun press(vararg actions: Action): List<UiSound> {
        for (action in actions) {
            heard.tick()
            compose.runOnIdle { router.send(action) }
            compose.waitForIdle()
        }
        return heard.drain()
    }

    @Test
    fun openingSettingsAddsNoSoundAndTheSwitchIsSilentOffAndOneConfirmOn() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            MainScreen(settings.value, HomeState.Ready(FakeHomeContent.board()), router, HomeCallbacks(), onRetry = {}, settingsHost = host, sounds = heard.uiSounds)
        }
        compose.waitForIdle()
        assertEquals("opening Settings: the press's own confirm and nothing more", listOf(UiSound.Confirm), press(Action.Menu))
        val down = Action.Move(Direction.Down)
        press(down, down, down, down, down, down, Action.Enter, down, down) // Advanced › Confirm with B, Animations, Sounds
        assertEquals("Loud to Off makes no sound", emptyList<UiSound>(), press(Action.Enter))
        assertEquals(SoundLevel.Off, settings.value.advanced.sounds)
        assertEquals("while off, nothing plays", emptyList<UiSound>(), press(down, Action.Move(Direction.Up)))
        assertEquals("Off to Quiet plays exactly one confirm", listOf(UiSound.Confirm), press(Action.Enter))
        assertEquals(SoundLevel.Quiet, settings.value.advanced.sounds)
        assertEquals("Quiet to Loud plays one confirm, louder", listOf(UiSound.Confirm), press(Action.Enter))
        assertEquals(SoundLevel.Loud, settings.value.advanced.sounds)
    }
}
