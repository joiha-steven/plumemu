package com.plumemu.setup

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.test.core.app.ApplicationProvider
import com.plumemu.R
import com.plumemu.account.AccountShots
import com.plumemu.account.SignInTags
import com.plumemu.coreui.components.FieldTags
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.FieldDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The sign-in step's label column in every language at Largest text: a label ends the board gap before the field's box (collar included) begins.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
open class SetupSignInLabelClearTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun check() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Largest)) { SetupAt(SetupFakes.boxArt(remember { AccountShots.form(AccountShots.State.Empty) })) }
        }
        compose.waitForIdle()
        val problems = mutableListOf<String>()
        for ((tag, label) in listOf(SignInTags.USERNAME to R.string.account_username, SignInTags.PASSWORD to R.string.account_password)) {
            val name = context.getString(label)
            val text = compose.onNode(hasText(name) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).getUnclippedBoundsInRoot()
            val box = compose.onNode(hasTestTag(FieldTags.BOX) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).getUnclippedBoundsInRoot()
            if (text.right.value + FieldDimens.gap.value > box.left.value + 0.5f) problems += "\"$name\" ends at ${text.right.value}, the field's box starts at ${box.left.value} (gap ${FieldDimens.gap.value})"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun labelsEndBeforeTheFieldWell() = check()
}

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "fr-w576dp-h432dp-land-xhdpi")
class SetupSignInLabelClearFrTest : SetupSignInLabelClearTest()

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "vi-w576dp-h432dp-land-xhdpi")
class SetupSignInLabelClearViTest : SetupSignInLabelClearTest()

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "es-w576dp-h432dp-land-xhdpi")
class SetupSignInLabelClearEsTest : SetupSignInLabelClearTest()

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "ja-w576dp-h432dp-land-xhdpi")
class SetupSignInLabelClearJaTest : SetupSignInLabelClearTest()

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "ko-w576dp-h432dp-land-xhdpi")
class SetupSignInLabelClearKoTest : SetupSignInLabelClearTest()

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "zh-w576dp-h432dp-land-xhdpi")
class SetupSignInLabelClearZhTest : SetupSignInLabelClearTest()

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "zh-rTW-w576dp-h432dp-land-xhdpi")
class SetupSignInLabelClearZhTwTest : SetupSignInLabelClearTest()

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SetupSignInLabelClearWideTest : SetupSignInLabelClearTest()
