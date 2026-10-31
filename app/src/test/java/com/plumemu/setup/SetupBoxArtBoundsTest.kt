package com.plumemu.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import com.plumemu.account.AccountShots
import com.plumemu.account.SignInItem
import com.plumemu.account.SignInTags
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The box art step, measured at all six text sizes: the form starts on the content column's line
 * (332 wide, the 24 margin narrow) and, on wide screens, level with the title like the second
 * screen's buttons; each field's label and the message start 14 in, like a list row's, and both
 * texts start the same way after their labels; Sign in starts
 * on the column's line, Skip one button gap after it; the step has its own dot.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SetupBoxArtBoundsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun textIn(
        field: String,
        text: String,
    ): DpRect = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(field)), useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun check(
        columnLeft: Float,
        isWide: Boolean,
    ) {
        var size by mutableStateOf(TextSize.Small)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) { SetupAt(SetupFakes.boxArt(remember { AccountShots.form(AccountShots.State.Wrong) })) }
        }
        val problems = mutableListOf<String>()
        for (each in TextSize.entries) {
            size = each
            compose.waitForIdle()
            fun expect(what: String, actual: Float, expected: Float) {
                if (kotlin.math.abs(actual - expected) > TOLERANCE) problems += "${each.name}: $what is $actual, not $expected"
            }
            val title = bounds(SetupTags.TITLE)
            val field = bounds(SignInTags.USERNAME)
            val password = bounds(SignInTags.PASSWORD)
            val fieldText = compose.onNode(hasText("plumtester") and hasAnyAncestor(hasTestTag(SignInTags.USERNAME)), useUnmergedTree = true).getUnclippedBoundsInRoot()
            val message = bounds(SignInTags.MESSAGE)
            val signIn = bounds(SignInTags.item(SignInItem.SignIn))
            val skip = bounds(SignInTags.item(SignInItem.Leave))
            expect("the field's left", field.left.value, columnLeft)
            expect("the password field's left", password.left.value, columnLeft)
            expect("the fields' gap", password.top.value - field.bottom.value, PlumDimens.listRowGap.value)
            val label = textIn(SignInTags.USERNAME, "Username")
            val passwordLabel = textIn(SignInTags.PASSWORD, "Password")
            val passwordText = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(SignInTags.PASSWORD)), useUnmergedTree = true).getUnclippedBoundsInRoot()
            val usernameText = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(SignInTags.USERNAME)), useUnmergedTree = true).getUnclippedBoundsInRoot()
            expect("the field label's inset", label.left.value - field.left.value, PlumDimens.gameRowPadding.value)
            // One column for both texts, whichever row has the focus dot.
            expect("the password's left (the name's)", passwordText.left.value, usernameText.left.value)
            if (passwordText.left.value <= passwordLabel.right.value) problems += "${each.name}: the password runs into its label"
            if (fieldText.left.value <= label.right.value) problems += "${each.name}: the name runs into its label"
            expect("the message's inset", message.left.value - field.left.value, PlumDimens.gameRowPadding.value)
            expect("Sign in's left", signIn.left.value, columnLeft)
            expect("Skip after Sign in", skip.left.value - signIn.right.value, PlumDimens.setupButtonGap.value)
            expect("Skip on Sign in's line", skip.top.value, signIn.top.value)
            expect("message to Sign in", signIn.top.value - message.bottom.value, PlumDimens.setupContentGap.value)
            if (isWide) expect("the form level with the title", field.top.value, title.top.value)
        }
        assertTrue(problems.size.toString() + " problems:\n" + problems.joinToString("\n"), problems.isEmpty())
        assertEquals("folder, box art, library", 3, compose.onAllNodesWithTag(SetupTags.DOT, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideFormSitsInTheContentColumnLevelWithTheTitle() = check(columnLeft = 332f, isWide = true)

    @Test
    @Config(qualifiers = NARROW)
    fun narrowFormSitsOnTheMargin() = check(columnLeft = 24f, isWide = false)

    private companion object {
        const val WIDE = "w768dp-h432dp-land-xhdpi"
        const val NARROW = "w576dp-h432dp-land-xhdpi"
        const val TOLERANCE = 0.6f
    }
}
