package com.plumemu.scraper

import org.junit.Assert.assertEquals
import org.junit.Test

/** [StoredAnswers.serialAnswerFitsSystem]: a stored answer judged by today's serial check. */
class StoredAnswersTest {
    private fun stored(systemId: String?) = GameDetails(system = systemId?.let { IdName(it, "System $it") }).toJson()

    @Test fun `an answer on the asked system fits`() {
        assertEquals(true, StoredAnswers.serialAnswerFitsSystem(stored("4"), "snes", "snes"))
    }

    @Test fun `a 3DS answer for a SNES game does not fit, its format known or not`() {
        assertEquals(false, StoredAnswers.serialAnswerFitsSystem(stored("17"), "snes", "snes"))
        assertEquals(false, StoredAnswers.serialAnswerFitsSystem(stored("17"), "snes", null))
    }

    @Test fun `Game Boy and Game Boy Color answers fit each other`() {
        assertEquals(true, StoredAnswers.serialAnswerFitsSystem(stored("10"), "gb", null))
        assertEquals(true, StoredAnswers.serialAnswerFitsSystem(stored("9"), "gbc", null))
    }

    /** Folders that hold another system's files. */
    @Test fun `an answer on the system of the serial's own format fits`() {
        assertEquals(true, StoredAnswers.serialAnswerFitsSystem(stored("10"), "gba", "gb"))
        assertEquals(true, StoredAnswers.serialAnswerFitsSystem(stored("9"), "gba", "gb"))
        assertEquals(true, StoredAnswers.serialAnswerFitsSystem(stored("9"), "sgb", "gb"))
        assertEquals(true, StoredAnswers.serialAnswerFitsSystem(stored("4"), "snesmsu1", "snes"))
        assertEquals(true, StoredAnswers.serialAnswerFitsSystem(stored("1"), "genesismsu", "megadrive"))
        assertEquals("the format's system, not any system", false, StoredAnswers.serialAnswerFitsSystem(stored("17"), "gba", "gb"))
    }

    @Test fun `an answer with no system, or text that does not read, cannot be judged`() {
        assertEquals(null, StoredAnswers.serialAnswerFitsSystem(stored(null), "snes", "snes"))
        assertEquals(null, StoredAnswers.serialAnswerFitsSystem(stored(" "), "snes", "snes"))
        assertEquals(null, StoredAnswers.serialAnswerFitsSystem("{}", "snes", "snes"))
        assertEquals(null, StoredAnswers.serialAnswerFitsSystem("not json", "snes", "snes"))
    }

    @Test fun `a system ScreenScraper has no id for was never checked, so it fits`() {
        assertEquals(true, StoredAnswers.serialAnswerFitsSystem(stored("17"), "no-such-system", null))
    }
}
