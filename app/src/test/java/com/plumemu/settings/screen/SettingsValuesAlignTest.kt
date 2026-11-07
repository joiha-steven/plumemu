package com.plumemu.settings.screen

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
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
import kotlin.math.abs

/**
 * The values at the right of Settings rows, measured (decision 0011 point 4) in Graphite and Paper:
 * the chevrons sit on the middle of the value's text line, the gaps either side of a cycler's text are
 * equal, every value ends on one right line (cycler, sub-page and switch rows), the value does not move
 * when the cursor arrives, and a long value or name stays clear of its label.
 */
abstract class SettingsValuesAlignBase {
    protected open val signedInGoesUnder = false

    protected val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var theme by mutableStateOf(PlumThemeSettings.Graphite)
    private var look by mutableStateOf(OptionLook.Focused)

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    /** The row's label text, found by its words under the row: the label carries no tag of its own. */
    private fun labelBounds(row: ValueRow): DpRect {
        val words = ApplicationProvider.getApplicationContext<Context>().getString(row.option.label)
        return compose.onNode(hasText(words) and hasAnyAncestor(hasTestTag(row.rowTag)), useUnmergedTree = true).getUnclippedBoundsInRoot()
    }

    private fun show() {
        compose.setContent { PlumTheme(theme) { ValueRows(focusedLook = look) } }
        compose.waitForIdle()
    }

    private fun inBothThemes(check: (name: String, problems: MutableList<String>) -> Unit) {
        show()
        val problems = mutableListOf<String>()
        for ((name, each) in listOf("Graphite" to PlumThemeSettings.Graphite, "Paper" to PlumThemeSettings.Paper)) {
            theme = each
            compose.waitForIdle()
            check(name, problems)
        }
        assertTrue("${problems.size} problems:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    private fun near(
        a: Float,
        b: Float,
    ) = abs(a - b) <= TOLERANCE

    protected fun chevronsAreCentredOnTheText() =
        inBothThemes { name, problems ->
            for (row in listOf(ValueRow.CycleFocused, ValueRow.CycleResting, ValueRow.CycleLong, ValueRow.SubPage, ValueRow.SignedIn)) {
                val text = bounds(SettingsTags.valueText(row.valueTag))
                val middle = (text.top.value + text.bottom.value) / 2
                val sides = if (row == ValueRow.SubPage || row == ValueRow.SignedIn) listOf(SettingsTags.chevronRight(row.valueTag)) else listOf(SettingsTags.chevronLeft(row.valueTag), SettingsTags.chevronRight(row.valueTag))
                for (tag in sides) {
                    val chevron = bounds(tag)
                    val at = (chevron.top.value + chevron.bottom.value) / 2
                    if (!near(at, middle)) problems += "$name $row: $tag is centred at $at, its text line at $middle"
                }
            }
        }

    protected fun theGapsEitherSideOfTheTextAreEqual() =
        inBothThemes { name, problems ->
            for (row in listOf(ValueRow.CycleFocused, ValueRow.CycleResting)) {
                val left = bounds(SettingsTags.chevronLeft(row.valueTag))
                val text = bounds(SettingsTags.valueText(row.valueTag))
                val right = bounds(SettingsTags.chevronRight(row.valueTag))
                val before = text.left.value - left.right.value
                val after = right.left.value - text.right.value
                if (!near(before, after)) problems += "$name $row: $before before the text, $after after it"
                if (!near((left.right - left.left).value, (right.right - right.left).value)) problems += "$name $row: the chevrons are not one width"
            }
        }

    protected fun everyValueEndsOnOneLine() =
        inBothThemes { name, problems ->
            // "Signed in as …" does not fit beside ScreenScraper in three columns: it goes whole under the label.
            val ends = listOfNotNull(ValueRow.CycleFocused, ValueRow.CycleResting, ValueRow.CycleLong, ValueRow.SubPage, ValueRow.SubPageBare, ValueRow.SignedIn.takeUnless { signedInGoesUnder }, ValueRow.SignedInLongName, ValueRow.Toggle)
            val first = bounds(ValueRow.CycleFocused.valueTag).right.value
            for (row in ends) {
                val end = bounds(row.valueTag).right.value
                if (!near(end, first)) problems += "$name $row: the value ends at $end, the cycler's at $first"
            }
            // The chevron ends the value in the rows that have one.
            for (row in listOf(ValueRow.CycleFocused, ValueRow.SubPage, ValueRow.SubPageBare, ValueRow.SignedIn)) {
                val chevron = bounds(SettingsTags.chevronRight(row.valueTag)).right.value
                if (!near(chevron, bounds(row.valueTag).right.value)) problems += "$name $row: the chevron ends at $chevron, the value at ${bounds(row.valueTag).right.value}"
            }
        }

    protected fun theValueDoesNotMoveWhenTheCursorArrives() {
        show()
        val tags = listOf(ValueRow.CycleFocused, ValueRow.CycleLong).flatMap { listOf(it.valueTag, SettingsTags.valueText(it.valueTag), SettingsTags.chevronLeft(it.valueTag), SettingsTags.chevronRight(it.valueTag)) }
        val focused = tags.associateWith { bounds(it) }
        look = OptionLook.Normal
        compose.waitForIdle()
        for (tag in tags) assertEquals("$tag moved when focus left", focused.getValue(tag), bounds(tag))
    }

    protected fun aLongValueStaysClearOfItsLabel() =
        inBothThemes { name, problems ->
            for (row in listOf(ValueRow.SignedInLongName, ValueRow.CycleLong)) {
                val value = bounds(row.valueTag)
                val label = labelBounds(row)
                val line = bounds(row.rowTag)
                if (label.right.value > value.left.value + TOLERANCE) problems += "$name $row: the label ends at ${label.right}, the value starts at ${value.left}"
                if (value.left.value < line.left.value + LABEL_ROOM) problems += "$name $row: the value starts at ${value.left}, leaving the label less than $LABEL_ROOM from ${line.left}"
                if (value.right.value > line.right.value + TOLERANCE) problems += "$name $row: the value ends at ${value.right}, past its row's ${line.right}"
                val check = compose.onAllNodesWithTag(SettingsTags.check(row.valueTag), useUnmergedTree = true).fetchSemanticsNodes().size
                if (row == ValueRow.SignedInLongName && check != 1) problems += "$name $row: $check check marks"
            }
        }

    protected fun onlyASignedInStatusHasACheck() {
        show()
        for (row in ValueRow.entries) {
            val expected = if (row == ValueRow.SignedIn || row == ValueRow.SignedInNoName || row == ValueRow.SignedInLongName) 1 else 0
            compose.onAllNodesWithTag(SettingsTags.check(row.valueTag), useUnmergedTree = true).assertCountEquals(expected)
        }
    }

    private companion object {
        /** Half a pixel at xhdpi plus a little: the rule is 1 px. */
        const val TOLERANCE = 0.5f

        /** A label keeps at least this much (dp) of its row before the value starts. */
        const val LABEL_ROOM = 40f
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsValuesAlign768Test : SettingsValuesAlignBase() {
    override val signedInGoesUnder = true

    @Test fun chevronsAreCentred() = chevronsAreCentredOnTheText()

    @Test fun gapsAreEqual() = theGapsEitherSideOfTheTextAreEqual()

    @Test fun valuesEndOnOneLine() = everyValueEndsOnOneLine()

    @Test fun valueDoesNotMove() = theValueDoesNotMoveWhenTheCursorArrives()

    @Test fun longValuesStayClear() = aLongValueStaysClearOfItsLabel()

    @Test fun checkOnlyWhenSignedIn() = onlyASignedInStatusHasACheck()
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsValuesAlign576Test : SettingsValuesAlignBase() {
    @Test fun chevronsAreCentred() = chevronsAreCentredOnTheText()

    @Test fun gapsAreEqual() = theGapsEitherSideOfTheTextAreEqual()

    @Test fun valuesEndOnOneLine() = everyValueEndsOnOneLine()

    @Test fun valueDoesNotMove() = theValueDoesNotMoveWhenTheCursorArrives()

    @Test fun longValuesStayClear() = aLongValueStaysClearOfItsLabel()

    @Test fun checkOnlyWhenSignedIn() = onlyASignedInStatusHasACheck()
}
