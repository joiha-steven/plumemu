package com.plumemu.platforms

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The bundled platforms-version.json says which platforms.json it belongs to. */
class BundledVersionTest {
    private val assets = File("src/main/assets")

    @Test
    fun theVersionFileMatchesThePlatformsFileBytes() {
        val version = PlatformDataVersions.parse(File(assets, PlatformDataVersions.BUNDLED_VERSION_ASSET).readText())
        assertNotNull("platforms-version.json is not a version file", version)
        val actual = MessageDigest.getInstance("SHA-256").digest(File(assets, "platforms.json").readBytes()).joinToString("") { "%02x".format(it) }
        assertEquals(
            "platforms.json changed without platforms-version.json: run tools/platforms/convert.py (it writes dataVersion + 1, the new sha256 and the date)",
            version!!.sha256,
            actual,
        )
    }

    @Test
    fun aVersionFileIsReadAndBadOnesAreRefused() {
        val sha = "ab".repeat(32)
        assertEquals(PlatformDataVersion(3, sha, "2026-11-01"), PlatformDataVersions.parse("""{"dataVersion": 3, "sha256": "$sha", "date": "2026-11-01"}"""))
        assertNull(PlatformDataVersions.parse("not json"))
        assertNull(PlatformDataVersions.parse("[]"))
        assertNull(PlatformDataVersions.parse("""{"dataVersion": 0, "sha256": "$sha", "date": "2026-11-01"}"""))
        assertNull(PlatformDataVersions.parse("""{"dataVersion": "3", "sha256": "$sha", "date": "2026-11-01"}"""))
        assertNull(PlatformDataVersions.parse("""{"dataVersion": 3, "sha256": "short", "date": "2026-11-01"}"""))
        assertNull(PlatformDataVersions.parse("""{"dataVersion": 3, "sha256": "$sha"}"""))
    }
}
