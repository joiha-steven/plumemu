package com.plumemu.scraper

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SystemIdsTest {
    @Test
    fun theNameOnlySystemsAreTheOnesCoreDataScrapesPinnedShortcutsOf() {
        // Change PinnedShortcutDao.NOT_SKIPPED_BY_BOX_ART in core-data with this set.
        assertEquals(setOf("steam"), SystemIds.NAME_ONLY)
    }

    @Test fun `the 13 tested systems have their ScreenScraper ids`() {
        val expected = mapOf(
            "arcade" to 75, "gb" to 9, "gba" to 12, "gbc" to 10, "gc" to 13, "megadrive" to 1,
            "n64" to 14, "nds" to 15, "nes" to 3, "ps2" to 58, "psp" to 61, "psx" to 57, "snes" to 4,
        )
        expected.forEach { (system, id) -> assertEquals(system, id, SystemIds.screenScraperId(system)) }
    }

    @Test fun `an unknown system has no id`() {
        assertNull(SystemIds.screenScraperId("no-such-system"))
        assertNull(SystemIds.screenScraperId("SNES"))
    }

    @Test fun `132 systems are mapped, 35 checked plus 96 added on 2026-10-22 and steam on 2026-10-23`() {
        assertEquals(132, SystemIds.knownSystems.size)
        assertEquals(138, SystemIds.screenScraperId("steam"))
        assertTrue(SystemIds.isNameOnly("steam"))
        assertFalse(SystemIds.isNameOnly("windows"))
        assertEquals(29, SystemIds.screenScraperId("3do"))
        assertEquals(301, SystemIds.screenScraperId("genesismsu"))
        assertEquals(76, SystemIds.screenScraperId("zxspectrum"))
    }

    @Test fun `every mapped id exists in ScreenScraper's system list`() {
        // systemesListe.php, id and name per line.
        val listed = ScraperFixture.fixture("systems.tsv").lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.substringBefore('\t').trim().toInt() }
            .toSet()
        assertEquals(250, listed.size)
        val missing = SystemIds.knownSystems.filter { SystemIds.screenScraperId(it) !in listed }
        assertTrue("ids not in systemesListe.php: $missing", missing.isEmpty())
    }

    @Test fun `every mapped Plumemu system exists in platforms json`() {
        // Unit tests run in the module's folder; the platforms module ships the system list.
        val file = File("../platforms/src/main/assets/platforms.json")
        assertTrue("${file.absolutePath} not found", file.isFile)
        val ids = Json.parseToJsonElement(file.readText()).jsonObject.getValue("systems").jsonArray
            .map { it.jsonObject.getValue("id").jsonPrimitive.content }
            .toSet()
        val unknown = SystemIds.knownSystems - ids
        assertTrue("not Plumemu systems: $unknown", unknown.isEmpty())
    }
}
