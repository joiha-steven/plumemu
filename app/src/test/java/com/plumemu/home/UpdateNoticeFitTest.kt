package com.plumemu.home

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpRect
import com.plumemu.coreui.components.TopBarTags
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.settings.ButtonLetters
import com.plumemu.update.UpdateManifest
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The update pill in the top bar and the "What's new" card at every text size in all three fonts, at 576 and 768 wide: the
 * pill holds its whole text (width = text + two paddings, as tall as the bar), stands `topBarItemGap` before the clock, never
 * pushes the clock or battery off the screen; the card, its title, notes and OK stay inside the screen, OK above the hint bar,
 * and OK is the setup button at its full height. Prints the numbers.
 */
abstract class UpdateNoticeFitBase(
    private val label: String,
) {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun bounds(text: String): DpRect = compose.onNode(hasText(text), useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun tagged(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun near(
        a: Float,
        b: Float,
    ) = abs(a - b) <= 0.5f

    private fun eachSize(
        where: String,
        content: @androidx.compose.runtime.Composable () -> Unit,
        check: (at: String, problems: MutableList<String>) -> Unit,
    ) {
        var size by mutableStateOf(TextSize.Small)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) { content() } }
        val problems = mutableListOf<String>()
        for (eachFont in listOf(PlumFont.Jost)) {
            for (each in TextSize.entries) {
                size = each
                compose.waitForIdle()
                check("$label $where ${eachFont.name} ${each.name}", problems)
            }
        }
        assertTrue(problems.size.toString() + " problems:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    protected fun pill() =
        eachSize(
            "pill",
            {
                CompositionLocalProvider(LocalUpdateFound provides MutableStateFlow("0.1.1")) {
                    HomeAt(FakeHomeContent.board(), BoardSystemsFocused, wifi = { true }, charging = { true })
                }
            },
        ) { at, problems ->
            val root = compose.onRoot().getUnclippedBoundsInRoot()
            val pill = tagged(TopBarTags.PILL)
            val text = bounds("Update 0.1.1")
            val clock = bounds(BOARD_CLOCK)
            val battery = bounds(BOARD_BATTERY)
            val place = compose.onNode(hasText("systems", substring = true) and hasAnyAncestor(hasTestTag(HomeTags.TOP_BAR)), useUnmergedTree = true).getUnclippedBoundsInRoot()
            val padding = text.left.value - pill.left.value
            val gap = clock.left.value - pill.right.value
            println("PILL $at: pill ${pill.left.value}..${pill.right.value} x ${pill.top.value}..${pill.bottom.value}, text ${text.left.value}..${text.right.value}, padding $padding, gap-to-clock $gap")
            if (!near(padding, PlumDimens.topBarPillPadding.value)) problems += "$at: text starts $padding inside the pill, not ${PlumDimens.topBarPillPadding.value}"
            if (!near(pill.right.value - text.right.value, PlumDimens.topBarPillPadding.value)) problems += "$at: text ends ${pill.right.value - text.right.value} inside the pill's right edge (cut or loose)"
            if (!near(gap, PlumDimens.topBarItemGap.value)) problems += "$at: pill-clock gap $gap, not ${PlumDimens.topBarItemGap.value}"
            if (text.bottom.value - text.top.value > pill.bottom.value - pill.top.value + 0.5f) problems += "$at: the text is taller than the pill"
            if (pill.left.value < PlumDimens.stripMarginSide.value) problems += "$at: the pill reaches the side margin"
            if (battery.right.value > root.right.value - PlumDimens.stripMarginSide.value + 0.5f) problems += "$at: the battery is pushed past the side margin"
            if (place.right.value > pill.left.value + 0.5f) problems += "$at: the place text runs under the pill"
        }

    protected fun card(release: UpdateManifest, name: String) =
        eachSize(
            name,
            {
                Box {
                    HomeAt(FakeHomeContent.board(), BoardSystemsFocused)
                    WhatsNewCardLayout(remember { WhatsNewState(release) }, ButtonLetters.Default) {}
                }
            },
        ) { at, problems ->
            val root = compose.onRoot().getUnclippedBoundsInRoot()
            val card = tagged(WhatsNewTags.CARD)
            val title = tagged(WhatsNewTags.TITLE)
            val ok = tagged(WhatsNewTags.OK)
            val notes = tagged(WhatsNewTags.NOTES)
            val hint = tagged(WhatsNewTags.HINT_OK)
            println("CARD $at: title ${title.top.value}..${title.bottom.value}, notes ${notes.top.value}..${notes.bottom.value}, ok ${ok.top.value}..${ok.bottom.value}, hint top ${hint.top.value}")
            for ((what, r) in listOf("title" to title, "notes" to notes, "ok" to ok)) {
                if (r.left.value < 0f || r.right.value > root.right.value || r.top.value < 0f || r.bottom.value > root.bottom.value) problems += "$at: $what leaves the screen"
            }
            if (ok.bottom.value > hint.top.value) problems += "$at: OK reaches the hint bar"
            // The repo's one setup button (40 dp, growing with the text size), as the update panel's buttons.
            if (ok.bottom.value - ok.top.value < PlumDimens.setupButtonHeight.value) problems += "$at: OK is ${ok.bottom.value - ok.top.value} dp tall, under the setup button's ${PlumDimens.setupButtonHeight.value}"
            if (title.left.value < PlumDimens.stripMarginSide.value || title.right.value > root.right.value - PlumDimens.stripMarginSide.value) problems += "$at: the title is outside the side margins"
            if (notes.bottom.value > ok.top.value) problems += "$at: the notes run into OK"
            if (card.top.value < 0f) problems += "$at: the card starts above the screen"
        }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class UpdateNoticeFit576Test : UpdateNoticeFitBase("576x432") {
    @Test fun pill_() = pill()

    @Test fun cardGrouped_() = card(WhatsNewFixtures.grouped, "card-grouped")

    @Test fun cardNotesOnly_() = card(WhatsNewFixtures.notesOnly, "card-notes")
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class UpdateNoticeFit768Test : UpdateNoticeFitBase("768x432") {
    @Test fun pill_() = pill()

    @Test fun cardGrouped_() = card(WhatsNewFixtures.grouped, "card-grouped")

    @Test fun cardNotesOnly_() = card(WhatsNewFixtures.notesOnly, "card-notes")
}
