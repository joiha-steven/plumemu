package com.plumemu.setup

import com.plumemu.account.SignInTypingShots
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Setup's box art sign-in typing with Plumemu's keyboard (the tray of Search, the game panel and Settings over setup): on the username with "Next", and on
 * the password, its dots, with "Sign in". A made-up name only.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SetupSignInTypingScreenshotTest {
    @Test
    fun username() = ReferenceShots.capture("setup_sign_in_typing_username") { SetupSignInTypingAt(SignInTypingShots.State.Username) }

    @Test
    fun password() = ReferenceShots.capture("setup_sign_in_typing_password") { SetupSignInTypingAt(SignInTypingShots.State.Password) }
}
