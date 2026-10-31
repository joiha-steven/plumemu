package com.plumemu.scraper

import com.plumemu.scraper.api.Redact
import org.junit.Assert.assertEquals
import org.junit.Test

class RedactTest {
    private val url = "https://api.screenscraper.fr/api2/jeuInfos.php?devid=dev1&devpassword=p%40ss&" +
        "softname=Plumemu&output=json&ssid=player&sspassword=hunter22&crc=0D4A1185"

    @Test fun `a logged URL shows no credential value`() {
        assertEquals(
            "https://api.screenscraper.fr/api2/jeuInfos.php?devid=***&devpassword=***&" +
                "softname=***&output=json&ssid=***&sspassword=***&crc=0D4A1185",
            Redact.text(url),
        )
    }

    @Test fun `credentials echoed in JSON text or a sentence are hidden too`() {
        assertEquals(
            "\"commandRequested\": \"x.php?devid=***\\u0026sspassword=***\"",
            Redact.text("\"commandRequested\": \"x.php?devid=dev1\\u0026sspassword=hunter22\""),
        )
        assertEquals("bad DevPassword=*** given", Redact.text("bad DevPassword=hunter22 given"))
        assertEquals("not a credential: xssid=1", Redact.text("not a credential: xssid=1"))
    }

    @Test fun `known secret values are blanked wherever they appear`() {
        assertEquals("password *** is wrong", Redact.text("password hunter22 is wrong", listOf("hunter22", "")))
        assertEquals("id 7 stays", Redact.text("id 7 stays", listOf("7")))
    }

    @Test fun `a stored media URL loses every credential parameter`() {
        assertEquals(
            "https://neoclone.screenscraper.fr/api2/mediaJeu.php?systemeid=1&jeuid=3&media=box-2D(us)",
            Redact.stripCredentials(
                "https://neoclone.screenscraper.fr/api2/mediaJeu.php?devid=a&devpassword=b&softname=c&" +
                    "ssid=d&sspassword=e&systemeid=1&jeuid=3&media=box-2D(us)",
            ),
        )
        assertEquals("https://x.fr/m.php", Redact.stripCredentials("https://x.fr/m.php?devid=a&SSPASSWORD=b"))
    }
}
