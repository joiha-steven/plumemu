package com.plumemu.graph

import com.plumemu.library.naming.NAMING_RULES_VERSION
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class NamingRulesMarkTest {
    private val calls = mutableListOf<String>()

    @Test
    fun libraryNamedByOlderRulesIsMarkedThenTheVersionRecorded() = runTest {
        val marked = markForNamingRules(0, { stored -> calls += "mark from $stored"; 3 }) { calls += "record $it" }
        assertEquals(true, marked)
        assertEquals(listOf("mark from 0", "record $NAMING_RULES_VERSION"), calls)
    }

    @Test
    fun libraryNamedByCurrentRulesIsLeftAlone() = runTest {
        val marked = markForNamingRules(NAMING_RULES_VERSION, { calls += "mark"; 0 }) { calls += "record" }
        assertEquals(false, marked)
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun settingsNotReadYetMarkNothing() = runTest {
        assertEquals(false, markForNamingRules(null, { calls += "mark"; 0 }) { calls += "record" })
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun aMarkThatFailsRecordsNothingSoTheNextScanTriesAgain() = runTest {
        val failure = runCatching { markForNamingRules(0, { throw IOException("disk") }) { calls += "record" } }.exceptionOrNull()
        assertTrue(failure.toString(), failure is IOException)
        assertEquals(emptyList<String>(), calls)
    }
}
