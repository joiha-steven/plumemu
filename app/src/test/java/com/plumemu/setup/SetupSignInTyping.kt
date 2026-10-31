package com.plumemu.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.plumemu.account.SignInTypingShots

/** Setup's box art step with its sign-in form typing with Plumemu's keyboard, in [state]: the tray up. */
@Composable
internal fun SetupSignInTypingAt(state: SignInTypingShots.State) {
    val form = remember(state) { SignInTypingShots.form(state) }
    SetupAt(SetupFakes.boxArt(form))
}
