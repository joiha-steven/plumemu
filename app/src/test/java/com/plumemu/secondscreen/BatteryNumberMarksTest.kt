package com.plumemu.secondscreen

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The in-game battery's numbers keep their places when a translation reorders the placeholders
 * (`InGameBatteryPart.timeLeftText`, docs/conventions/translation.md › Placeholders).
 */
class BatteryNumberMarksTest {
    private val number = SpanStyle(fontWeight = FontWeight.Bold)
    private val word = SpanStyle(fontWeight = FontWeight.Light)
    private val hoursAndMinutes = listOf("4", "30")

    private fun numbersIn(text: AnnotatedString): List<String> =
        text.spanStyles.filter { it.item == number }.map { text.text.substring(it.start, it.end) }

    @Test
    fun englishOrderPutsHoursThenMinutes() {
        val formatted = String.format("%1\$s h %2\$s min", numberMark(0), numberMark(1))
        val styled = styledNumbers(formatted, hoursAndMinutes, number, word)
        assertEquals("4 h 30 min", styled.text)
        assertEquals(listOf("4", "30"), numbersIn(styled))
    }

    @Test
    fun aTranslationThatPutsMinutesFirstStillGetsEachNumberInItsPlace() {
        val formatted = String.format("%2\$s min / %1\$s h", numberMark(0), numberMark(1))
        val styled = styledNumbers(formatted, hoursAndMinutes, number, word)
        assertEquals("30 min / 4 h", styled.text)
        assertEquals(listOf("30", "4"), numbersIn(styled))
    }

    @Test
    fun aDurationInsideASentenceKeepsItsMarks() {
        val duration = String.format("%2\$s min %1\$s h", numberMark(0), numberMark(1))
        val styled = styledNumbers(String.format("About %1\$s left", duration), hoursAndMinutes, number, word)
        assertEquals("About 30 min 4 h left", styled.text)
        assertEquals(listOf("30", "4"), numbersIn(styled))
    }
}
