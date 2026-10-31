package com.plumemu.settings.screen

import androidx.compose.runtime.CompositionLocalProvider
import com.plumemu.coredata.settings.ConfirmButtonSetting
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.ButtonLetters
import com.plumemu.settings.LocalButtonLetters
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The emulators panel names the confirm button by its letter, so it follows "Confirm with B". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class EmulatorsBodyTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun bodyWith(letters: ButtonLetters): String {
        var text = ""
        compose.setContent { CompositionLocalProvider(LocalButtonLetters provides letters) { text = emulatorsBody("SNES") } }
        compose.waitForIdle()
        return text
    }

    private val tail = " on a SNES game. You can still pick another emulator for a single game from its menu › Play with…"

    @Test
    fun namesAByDefault() {
        assertEquals("Used when you press A$tail", bodyWith(ButtonLetters.of(ConfirmButtonSetting.Right)))
    }

    @Test
    fun namesBWhenBConfirms() {
        assertEquals("Used when you press B$tail", bodyWith(ButtonLetters.of(ConfirmButtonSetting.Bottom)))
    }
}
