package com.plumemu.settings.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Statistics' figures in the locale's number format, with the play-time words the Info panel uses. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsStatisticsFormatTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val rows =
        listOf(
            StatisticRow.Total(1_234 * 3_600L + 5 * 60 + 59),
            StatisticRow.System(TopSystem("snes", "Super Nintendo", 59)),
            StatisticRow.Game(1, TopGame(1, "Super Metroid", "Super Nintendo", 45 * 60L), showsCover = false),
            StatisticRow.Played(1_234, 10_000),
            StatisticRow.BoxArt(0, 10_000),
            StatisticRow.Version,
            StatisticRow.NothingPlayed,
            StatisticRow.ThisWeek(2 * 3_600L + 10 * 60),
            StatisticRow.ThisWeek(59),
            StatisticRow.ThisWeek(0),
        )

    private fun show() =
        compose.setContent {
            Column {
                rows.forEachIndexed { at, row -> BasicText(statisticValue(row, "0.1.0 · build 7"), Modifier.testTag("value_$at")) }
            }
        }

    private fun assertValues(vararg expected: String) = expected.forEachIndexed { at, text -> compose.onNodeWithTag("value_$at").assertTextEquals(text) }

    @Test
    fun englishNumbersAndWholeMinutes() {
        show()
        assertValues("1,234 h 5 min", "Less than a minute", "45 min", "1,234 of 10,000", "0 of 10,000", "0.1.0 · build 7", "", "2 h 10 min", "Less than a minute", "None yet")
    }

    /** German groups thousands with a dot; the words stay English until the translations step. */
    @Test
    @Config(qualifiers = "de-rDE")
    fun germanNumbers() {
        show()
        assertValues("1.234 h 5 min", "Less than a minute", "45 min", "1.234 of 10.000", "0 of 10.000", "0.1.0 · build 7", "", "2 h 10 min", "Less than a minute", "None yet")
    }
}
