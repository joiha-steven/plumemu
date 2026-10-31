package com.plumemu.account

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.R
import com.plumemu.coreui.components.LocalConfirmCap
import com.plumemu.coreui.components.LocalHeldKeys
import com.plumemu.coreui.components.keyboard.KeyboardEvent
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The sign-in field rows and Plumemu's own keyboard (design/rules.md > Shapes and motion > Pressed): while the
 * tray is up A types a key, so the focused field row does not dip; with the tray put away it answers A again.
 * Read from the drawn pixels of the row.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SignInPressFeelTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val held = mutableStateOf<Set<String>>(emptySet())

    private fun pixels(tag: String): List<Int> {
        val map = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        return buildList { for (y in 0 until map.height) for (x in 0 until map.width) add(map[x, y].hashCode()) }
    }

    private fun setHeld(
        keys: Set<String>,
        advanceMs: Long,
    ) {
        compose.runOnIdle {
            held.value = keys
            Snapshot.sendApplyNotifications()
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(advanceMs)
    }

    @Test
    fun aSignInFieldRowDoesNotDipWhileTheOwnKeyboardIsUpAndDipsAgainWhenItCloses() {
        val form = SignInTypingShots.form(SignInTypingShots.State.Username)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                CompositionLocalProvider(LocalHeldKeys provides held, LocalConfirmCap provides "A") {
                    SignInBody(form, R.string.setup_box_art_skip, onTap = {}, onAction = {}, gap = PlumDimens.listRowGap)
                }
            }
        }
        compose.waitForIdle()
        check(form.isTrayUp) { "the fixture's tray is not up" }
        val rest = pixels(SignInTags.USERNAME)
        setHeld(setOf("A"), 300)
        assertEquals("A belongs to the key: the row does not dip", rest, pixels(SignInTags.USERNAME))
        setHeld(emptySet(), 500)
        compose.runOnIdle {
            form.onKeyboardKey(KeyboardEvent.Hide)
            Snapshot.sendApplyNotifications()
        }
        compose.waitForIdle()
        check(!form.isTrayUp) { "the tray did not close" }
        val closedRest = pixels(SignInTags.USERNAME)
        setHeld(setOf("A"), 300)
        assertNotEquals("with the tray closed the row dips again", closedRest, pixels(SignInTags.USERNAME))
    }
}
