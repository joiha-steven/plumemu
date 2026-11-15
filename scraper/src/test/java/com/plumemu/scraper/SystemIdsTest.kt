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
    fun theNameOnlySystemsAreTheLauncherFileSystems() {
        // Pinned shortcuts are narrower: PinnedShortcutDao.NOT_SKIPPED_BY_BOX_ART lists steam only (ShortcutScrapeTest).
        assertEquals(setOf("steam", "epic", "windows"), SystemIds.NAME_ONLY)
        listOf("windows3x", "windows9x", "dos", "pc").forEach { assertFalse(it, SystemIds.isNameOnly(it)) }
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

    @Test fun `132 systems are mapped, 35 checked plus 96 added on 2026-10-22, steam on 2026-10-23 and epic on 2026-11-14, less samcoupe no longer shipped`() {
        assertEquals(132, SystemIds.knownSystems.size)
        assertNull("SAM Coupe is not a Plumemu system", SystemIds.screenScraperId("samcoupe"))
        assertEquals(138, SystemIds.screenScraperId("steam"))
        assertTrue(SystemIds.isNameOnly("steam"))
        assertTrue(SystemIds.isNameOnly("windows"))
        assertTrue(SystemIds.isNameOnly("epic"))
        assertEquals(138, SystemIds.screenScraperId("epic"))
        assertEquals(138, SystemIds.screenScraperId("windows"))
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
