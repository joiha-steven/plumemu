package com.plumemu.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.account.AccountShots
import com.plumemu.account.SignInTags
import com.plumemu.account.SignInItem
import com.plumemu.account.SignInTypingShots
import com.plumemu.coreui.components.FieldTags
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.FieldDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.screen.AccountPasswordTypingSettings
import com.plumemu.account.SignInKind
import com.plumemu.settings.screen.SignInSettingsWith
import com.plumemu.settings.screen.SignInTypingSettings
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The sign-in fields measured at the reference sizes, Graphite and Paper, keyboard (Plumemu's tray) up and down, in setup and in
 * Settings' panel: the field's box (collar included) lies inside its row; the label ends the board gap before it; the two rows
 * are at least the board gap apart; no button shows as a strip (it is whole or not shown at all).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h480dp-land-xhdpi")
open class FieldRowsBoundsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun box(tag: String) = compose.onNode(hasTestTag(FieldTags.BOX) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun check(
        scene: String,
        content: @Composable () -> Unit,
    ) {
        var settings by mutableStateOf(PlumThemeSettings.Graphite)
        compose.setContent { PlumTheme(settings) { content() } }
        val problems = mutableListOf<String>()
        for ((theme, each) in listOf("Graphite" to PlumThemeSettings.Graphite, "Paper" to PlumThemeSettings.Paper)) {
            settings = each
            compose.waitForIdle()
            val at = "$scene $theme"
            val rows = listOf(SignInTags.USERNAME, SignInTags.PASSWORD).map { compose.onNodeWithTag(it, useUnmergedTree = true).getUnclippedBoundsInRoot() }
            val boxes = listOf(SignInTags.USERNAME, SignInTags.PASSWORD).map(::box)
            for (i in 0..1) {
                val r = rows[i]
                val b = boxes[i]
                if (b.left < r.left || b.right > r.right || b.top < r.top || b.bottom > r.bottom) problems += "$at: field $i $b is not inside its row $r"
            }
            val gap = rows[1].top.value - rows[0].bottom.value
            if (gap < FieldDimens.gap.value - ONE_PIXEL) problems += "$at: rows are $gap apart, the board gap is ${FieldDimens.gap.value}"
            for ((i, tag) in listOf(SignInTags.USERNAME, SignInTags.PASSWORD).withIndex()) {
                val label = compose.onNode(hasTestTag(FieldTags.LABEL) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).getUnclippedBoundsInRoot()
                if (label.right.value + FieldDimens.gap.value > boxes[i].left.value + ONE_PIXEL) problems += "$at: label $i ends at ${label.right.value}, its box starts at ${boxes[i].left.value} (gap ${FieldDimens.gap.value})"
            }
            for (item in listOf(SignInItem.SignIn, SignInItem.Leave)) {
                val node = compose.onAllNodes(hasTestTag(SignInTags.item(item)), useUnmergedTree = true)
                if (node.fetchSemanticsNodes().isEmpty()) continue
                val shown = node[0].visibleShare()
                if (shown > 0.001f && shown < 0.999f) problems += "$at: button $item is ${(shown * 100).toInt()} % visible, a strip"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /** How much of the node lies inside every clip above it: 0 when not placed. */
    private fun SemanticsNodeInteraction.visibleShare(): Float {
        val node = fetchSemanticsNode()
        if (!node.layoutInfo.isPlaced || node.size.height == 0) return 0f
        return (node.boundsInRoot.height / node.size.height).coerceIn(0f, 1f)
    }

    private companion object {
        /** One device pixel at the test density (xhdpi): layout rounds each edge to a whole pixel. */
        const val ONE_PIXEL = 0.5f
    }

    @Test
    fun setupKeyboardDown() = check("setup down") { SetupAt(SetupFakes.boxArt(androidx.compose.runtime.remember { AccountShots.form(AccountShots.State.Empty) })) }

    @Test
    fun setupKeyboardUpUsername() = check("setup up") { SetupSignInTypingAt(SignInTypingShots.State.Username) }

    @Test
    fun setupKeyboardUpPassword() = check("setup up password") { SetupSignInTypingAt(SignInTypingShots.State.Password) }

    @Test
    fun settingsKeyboardDown() = check("settings down") { SignInSettingsWith(androidx.compose.runtime.remember { AccountShots.form(AccountShots.State.Empty, stacked = true) }) }

    @Test
    fun settingsKeyboardUpPassword() = check("settings up password") { SignInTypingSettings(SignInTypingShots.State.Password) }

    @Test
    fun raSettingsKeyboardUpPassword() = check("ra settings up password") { AccountPasswordTypingSettings(SignInKind.RetroAchievements) }

    @Test
    fun settingsKeyboardUp() = check("settings up") { SignInTypingSettings(SignInTypingShots.State.Username) }
}

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class FieldRowsBoundsNarrowTest : FieldRowsBoundsTest()

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class FieldRowsBoundsWideTest : FieldRowsBoundsTest()
