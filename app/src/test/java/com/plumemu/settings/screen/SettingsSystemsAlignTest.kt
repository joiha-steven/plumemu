package com.plumemu.settings.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
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
 * Library › Systems measured at all six text sizes: every row spans from the
 * title's left line to one right line inside the panel, 2 apart; the names and their game counts
 * start on one line (the focused one after the focus dot); every handle is one width and ends the
 * row; every "Hidden" ends where the handle starts. The System order and Systems values end where
 * ROM folders' value ends.
 */
abstract class SettingsSystemsAlignBase {
    protected val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun all(tag: String): List<DpRect> = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().indices.map {
        compose.onAllNodesWithTag(tag, useUnmergedTree = true)[it].getUnclippedBoundsInRoot()
    }

    private fun eachSize(
        label: String,
        option: SettingsOption,
        enter: Boolean,
        check: (at: String, problems: MutableList<String>) -> Unit,
    ) {
        var size by mutableStateOf(TextSize.Small)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) {
                // Arcade and PSP hidden; the cursor on Game Boy Advance, the second row.
                SystemsShot(option, enter = enter, hidden = setOf("arcade", "psp")) { if (enter) move(Direction.Down) }
            }
        }
        val problems = mutableListOf<String>()
        for (each in TextSize.entries) {
            size = each
            compose.waitForIdle()
            check("$label ${each.name}", problems)
        }
        assertTrue(problems.size.toString() + " problems:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    protected fun theValuesEndWithTheOthers(label: String) {
        eachSize(label, SettingsOption.RomFolders, enter = false) { at, problems ->
            val folders = compose.onNode(hasText("2 folders\u00A0»") and hasAnyAncestor(hasTestTag(SettingsTags.option(SettingsOption.RomFolders))), useUnmergedTree = true).getUnclippedBoundsInRoot()
            for ((option, text) in listOf(SettingsOption.SystemOrder to "‹ A–Z ›", SettingsOption.Systems to "6 of 8 shown\u00A0»")) {
                val value = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(SettingsTags.option(option))), useUnmergedTree = true).getUnclippedBoundsInRoot()
                if (!near(value.right.value, folders.right.value)) problems += "$at: $option's value ends at ${value.right}, ROM folders' at ${folders.right}"
            }
        }
    }

    protected fun theRowsLineUp(label: String) {
        eachSize(label, SettingsOption.Systems, enter = true) { at, problems ->
            val panel = bounds(SettingsTags.PANEL)
            val title = compose.onNode(hasText("Systems") and hasAnyAncestor(hasTestTag(SettingsTags.SYSTEMS_PANEL)), useUnmergedTree = true).getUnclippedBoundsInRoot()
            val rows = SystemsShots.systems.indices.map { bounds(SettingsTags.panelItem(it)) }
            rows.forEachIndexed { index, row ->
                if (!near(row.left.value, panel.left.value)) problems += "$at: row $index starts at ${row.left}, the panel at ${panel.left}"
                if (!near(row.right.value, rows.first().right.value)) problems += "$at: row $index ends at ${row.right}, the first at ${rows.first().right}"
            }
            if (rows.first().right.value > panel.right.value + TOLERANCE) problems += "$at: rows end at ${rows.first().right}, past the panel's ${panel.right}"
            val gaps = rows.zipWithNext().map { (a, b) -> b.top.value - a.bottom.value }
            if (gaps.any { !near(it, PlumDimens.listRowGap.value) }) problems += "$at: gaps between rows $gaps, not ${PlumDimens.listRowGap}"

            val names = all(SettingsTags.SYSTEM_NAME)
            val focusedName = names[1]
            val others = names.filterIndexed { i, _ -> i != 1 }
            if (others.any { !near(it.left.value, others.first().left.value) }) problems += "$at: names start at ${others.map { it.left }}"
            if (!near(focusedName.left.value, others.first().left.value)) problems += "$at: the focused name starts at ${focusedName.left}, the others' at ${others.first().left} (focus moves no text)"
            if (!near(others.first().left.value, panel.left.value + PlumDimens.settingsPanelRowPadding.value)) problems += "$at: names start at ${others.first().left}, not the row padding inside the panel ${panel.left}"
            if (!near(others.first().left.value, title.left.value)) problems += "$at: names start at ${others.first().left}, the title at ${title.left} (one row-text line)"
            all(SettingsTags.SYSTEM_COUNT).forEachIndexed { index, count ->
                if (!near(count.left.value, names[index].left.value)) problems += "$at: row $index's count starts at ${count.left}, its name at ${names[index].left}"
            }

            val handles = all(SettingsTags.SYSTEM_HANDLE)
            handles.forEachIndexed { index, handle ->
                if (!near(handle.right.value, rows[index].right.value)) problems += "$at: handle $index ends at ${handle.right}, its row at ${rows[index].right}"
                if (!near((handle.right - handle.left).value, PlumDimens.settingsHandleWidth.value)) problems += "$at: handle $index is ${handle.right - handle.left} wide"
                if (!near(handle.top.value, rows[index].top.value) || !near(handle.bottom.value, rows[index].bottom.value)) problems += "$at: handle $index is not as high as its row"
            }
            val hidden = all(SettingsTags.SYSTEM_HIDDEN)
            if (hidden.size != 2) problems += "$at: ${hidden.size} Hidden labels, not 2"
            for (label in hidden) if (!near(label.right.value, handles.first().left.value)) problems += "$at: Hidden ends at ${label.right}, the handles start at ${handles.first().left}"
        }
    }

    private fun near(
        a: Float,
        b: Float,
    ) = kotlin.math.abs(a - b) <= TOLERANCE

    private companion object {
        const val TOLERANCE = 0.5f
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsSystemsAlign768Test : SettingsSystemsAlignBase() {
    @Test fun theValuesEndWithTheOthers() = theValuesEndWithTheOthers("768x432")

    @Test fun theRowsLineUp() = theRowsLineUp("768x432")
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsSystemsAlign576Test : SettingsSystemsAlignBase() {
    @Test fun theValuesEndWithTheOthers() = theValuesEndWithTheOthers("576x432")

    @Test fun theRowsLineUp() = theRowsLineUp("576x432")
}
