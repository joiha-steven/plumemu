package com.plumemu.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.components.GameRow
import com.plumemu.coreui.components.GameRowLook
import com.plumemu.coreui.components.SystemRow
import com.plumemu.coreui.components.SystemRowLook
import com.plumemu.coreui.components.SystemRowTags
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.LeftColumnDimens
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SlabDimens
import com.plumemu.setup.ChoiceLook
import com.plumemu.setup.SetupChoice
import com.plumemu.setup.SetupInfoRow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.platform.testTag

/**
 * Every row that wears the focus slab starts its text clear of the accent bar: the bar's right edge + [MIN_GAP] <= the text's left edge,
 * for game rows, the systems rows (Home and its empty state), Settings' sections, setup's choice cards and its focusable info rows. Rows
 * whose bar stands the recipe's distance in (game rows, sections, setup cards) keep the game row's 10.67 dp; the systems rows keep 8 or
 * more with the bar in the slab's overhang, [LeftColumnDimens.systemBarInset] clear of the slab's edge and so of its rounded ends. The systems
 * slab starts no nearer than 16 dp to the screen edge.
 */
abstract class SlabStripeGapBase {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    /** What a game row leaves between its bar and its title (the recipe: text start minus the bar's right edge), in dp. */
    private val recipeGap = PlumDimens.gameRowPadding - (SlabDimens.barInset + SlabDimens.barWidth)

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { PlumTheme(PlumThemeSettings()) { Column(Modifier.width(320.dp)) { content() } } }
        compose.waitForIdle()
    }

    /** The gap in dp between the bar (drawn [barInset] in from the left of the node tagged [pillTag]) and the text [label]. */
    private fun assertGap(
        what: String,
        pillTag: String,
        label: String,
        barInset: Dp = SlabDimens.barInset,
        wanted: Float = recipeGap.value,
    ) {
        val d = compose.density.density
        val pill = compose.onNodeWithTag(pillTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val text = compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val gap = (text.left - (pill.left + (barInset + SlabDimens.barWidth).value * d)) / d
        assertTrue("$what: the text starts $gap dp after the bar, at least $MIN_GAP wanted", gap >= MIN_GAP - 0.01f)
        assertTrue("$what: the text starts $gap dp after the bar, a game row leaves $wanted", gap >= wanted - 0.5f)
    }

    @Test
    fun gameRow() {
        show { GameRow("Chrono Trigger", "USA", GameRowLook.Focused, Modifier.testTag("row")) }
        assertGap("game row", "row", "Chrono Trigger")
    }

    @Test
    fun systemsRow() {
        show { SystemRow("Super Nintendo", "248", SystemRowLook.Focused, showName = true, bleedStart = LeftColumnDimens.systemLabelStart) }
        assertGap("systems row", SystemRowTags.PILL, "Super Nintendo", LeftColumnDimens.systemBarInset, wanted = MIN_GAP)
    }

    @Test
    fun systemsBarStaysClearOfTheSlabEdgeAndTheScreenEdge() {
        show { SystemRow("Super Nintendo", "248", SystemRowLook.Focused, showName = true, bleedStart = LeftColumnDimens.systemLabelStart) }
        val d = compose.density.density
        val pillLeft = compose.onNodeWithTag(SystemRowTags.PILL, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left / d
        assertTrue("the systems pill starts $pillLeft dp from the screen edge, at least 16 wanted", pillLeft >= 16f - 0.01f)
        assertTrue("the bar is ${LeftColumnDimens.systemBarInset} from the slab's edge, at least 3 wanted", LeftColumnDimens.systemBarInset.value >= 3f)
    }

    @Test
    fun settingsSectionRow() {
        show { SystemRow("Library", "", SystemRowLook.Focused, showName = true, bleedStart = LeftColumnDimens.sectionLabelStart, showCount = false, barInset = SlabDimens.barInset) }
        assertGap("settings section", SystemRowTags.PILL, "Library")
    }

    @Test
    fun setupChoiceCard() {
        show { SetupChoice("Box art only", "Box fronts over Wi-Fi.", ChoiceLook.Focused, Modifier.testTag("card")) }
        assertGap("setup choice", "card", "Box art only")
    }

    @Test
    fun setupInfoRow() {
        show { SetupInfoRow("Emulators", "Found on this device.", isFocused = true, modifier = Modifier.testTag("info")) }
        assertGap("setup info row", "info", "Emulators")
    }

    private companion object {
        /** The least gap between any slab's bar and its text, dp. */
        const val MIN_GAP = 8f
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SlabStripeGap768Test : SlabStripeGapBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SlabStripeGap576Test : SlabStripeGapBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w832dp-h468dp-land-xhdpi")
class SlabStripeGap832Test : SlabStripeGapBase()
