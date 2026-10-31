package com.plumemu.scraper.api

import com.plumemu.scraper.DeveloperCredentials
import com.plumemu.scraper.UserCredentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which media URLs may get the credentials: the configured API, or https on a screenscraper.fr host. */
class CredentialHostTest {
    private val api = ScreenScraperApi(
        OkHttpClient(),
        "http://localhost:8080/api2/",
        DeveloperCredentials("test-dev-id", "test-dev-password"),
        UserCredentials("test-user", "test-user-password"),
        "Plumemu",
        {},
    )

    private fun may(url: String) = api.mayCarryCredentials(url.toHttpUrl())

    @Test fun `https on screenscraper dot fr and its subdomains may carry credentials`() {
        assertEquals(true, may("https://neoclone.screenscraper.fr/image.png"))
        assertEquals(true, may("https://www.screenscraper.fr/image.png"))
        assertEquals(true, may("https://screenscraper.fr/image.png"))
    }

    @Test fun `look-alike hosts and cleartext do not`() {
        assertEquals(false, may("https://evilscreenscraper.fr/"))
        assertEquals(false, may("https://screenscraper.fr.evil.com/"))
        assertEquals(false, may("http://neoclone.screenscraper.fr/"))
        assertEquals(false, may("https://screenscraper.fr@evil.com/"))
    }

    @Test fun `the configured API's own scheme host and port may`() {
        assertEquals(true, may("http://localhost:8080/api2/mediaJeu.php"))
        assertEquals(false, may("http://localhost:9090/api2/mediaJeu.php"))
    }
}
