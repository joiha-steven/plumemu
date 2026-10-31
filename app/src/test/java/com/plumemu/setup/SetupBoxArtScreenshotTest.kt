package com.plumemu.setup

import com.plumemu.account.AccountShots
import com.plumemu.account.UtcTimeZone
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Setup's "Box art from ScreenScraper" step (built from setup's parts and
 * tokens): the question first, then every state of its sign-in: empty, checking,
 * each failure, signed in.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SetupBoxArtScreenshotTest {
    @get:Rule
    val utc = UtcTimeZone()

    private fun shot(
        name: String,
        state: AccountShots.State,
    ) = ReferenceShots.capture(name) { SetupAt(SetupFakes.boxArt(AccountShots.form(state))) }

    @Test fun question() = ReferenceShots.capture("setup_box_art_question") { SetupAt(SetupFakes.boxArtQuestion) }

    @Test fun empty() = shot("setup_box_art_empty", AccountShots.State.Empty)

    @Test fun checking() = shot("setup_box_art_checking", AccountShots.State.Checking)

    @Test fun wrongPassword() = shot("setup_box_art_wrong", AccountShots.State.Wrong)

    @Test fun offline() = shot("setup_box_art_offline", AccountShots.State.Offline)

    @Test fun closed() = shot("setup_box_art_closed", AccountShots.State.Closed)

    @Test fun dailyLimit() = shot("setup_box_art_daily_limit", AccountShots.State.DailyLimit)

    @Test fun serverError() = shot("setup_box_art_server_error", AccountShots.State.ServerError)

    @Test fun signedIn() = shot("setup_box_art_signed_in", AccountShots.State.SignedIn)
}
