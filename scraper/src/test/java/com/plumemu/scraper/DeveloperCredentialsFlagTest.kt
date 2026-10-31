package com.plumemu.scraper

import org.junit.Assert.assertEquals
import org.junit.Test

/** The build flag, the credentials the build carries and `isBlank` all follow one rule. */
class DeveloperCredentialsFlagTest {
    @Test fun `the build flag matches the credentials the build carries`() {
        val fromBuild = DeveloperCredentials.fromBuildConfig()
        val bothFilled = BuildConfig.SCREENSCRAPER_DEV_ID.isNotBlank() &&
            BuildConfig.SCREENSCRAPER_DEV_PASSWORD.isNotBlank()

        assertEquals(bothFilled, BuildConfig.HAS_DEVELOPER_CREDENTIALS)
        assertEquals(bothFilled, DeveloperCredentials.buildHasCredentials)
        assertEquals(!fromBuild.isBlank, DeveloperCredentials.buildHasCredentials)
    }
}
