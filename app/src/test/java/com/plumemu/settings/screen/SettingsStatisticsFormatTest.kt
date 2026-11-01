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

    private fun show() =
        compose.setContent {
            Column {
                val values = listOf(statisticTime(1_234 * 3_600L + 5 * 60 + 59), statisticTime(59), statisticTime(45 * 60L), countOf(1_234, 10_000), countOf(0, 10_000), statisticTime(0))
                values.forEachIndexed { at, text -> BasicText(text, Modifier.testTag("value_$at")) }
            }
        }

    private fun assertValues(vararg expected: String) = expected.forEachIndexed { at, text -> compose.onNodeWithTag("value_$at").assertTextEquals(text) }

    @Test
    fun englishNumbersAndWholeMinutes() {
        show()
        assertValues("1,234\u00A0h 5\u00A0min", "Less than a minute", "45\u00A0min", "1,234 of 10,000", "0 of 10,000", "Less than a minute")
    }

    /** German groups thousands with a dot; the words stay English until the translations step. */
    @Test
    @Config(qualifiers = "de-rDE")
    fun germanNumbers() {
        show()
        assertValues("1.234\u00A0h 5\u00A0min", "Less than a minute", "45\u00A0min", "1.234 of 10.000", "0 of 10.000", "Less than a minute")
    }
}
