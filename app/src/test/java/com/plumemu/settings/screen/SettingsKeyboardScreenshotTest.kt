package com.plumemu.settings.screen

import com.plumemu.account.SignInTypingShots
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings' fields typing with Plumemu's keyboard (the
 * tray of Search and the game panel over Settings' panel): the accent's hex code on the digits page
 * with "Done", the same after a wrong code, and Unidentified games › Fix name… with its results.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsKeyboardScreenshotTest {
    @Test
    fun hexTyping() = ReferenceShots.capture("settings_hex_typing_tray") { HexTypingSettings() }

    @Test
    fun hexWrongCode() = ReferenceShots.capture("settings_hex_typing_tray_wrong") { HexTypingSettings(typed = "zz", tried = true) }

    @Test
    fun fixNameTyping() = ReferenceShots.capture("settings_fix_name_typing") { NameFixTypingSettings() }

    // Library › Sign in to ScreenScraper: "Next" on the username, "Sign in" on the password's dots.
    @Test
    fun signInUsernameTyping() = ReferenceShots.capture("settings_sign_in_typing_username") { SignInTypingSettings(SignInTypingShots.State.Username) }

    @Test
    fun signInPasswordTyping() = ReferenceShots.capture("settings_sign_in_typing_password") { SignInTypingSettings(SignInTypingShots.State.Password) }
}
