package com.plumemu.settings.screen

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Every licence text About lists is in the APK's merged assets, the name packs' MAME texts included. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LicenseNoticesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun everyListedTextIsShippedAndReadable() {
        // Entries without an asset (public domain) show the licence's name instead: NotShipped.
        val failures = LicenseNotices.all.filter { it.asset != null }.mapNotNull { notice ->
            when (val text = readLicenseText(context.assets, notice) { }) {
                is LicenseText.Loaded -> if (text.text.isBlank()) "${notice.asset}: empty" else null
                else -> "${notice.asset}: $text"
            }
        }
        assertEquals(failures.joinToString("\n"), 0, failures.size)
    }

    @Test
    fun theNamePacksMameTextsAreListed() {
        val assets = LicenseNotices.all.mapNotNull { it.asset }
        assertTrue("licenses/mame-BSD-3-Clause.txt" in assets)
        assertTrue("licenses/mame-CC0.txt" in assets)
        val cc0 = readLicenseText(context.assets, LicenseNotices.all.single { it.asset == "licenses/mame-CC0.txt" }) { }
        assertTrue((cc0 as LicenseText.Loaded).text.contains("CC0"))
    }

    @Test
    fun theSwitchPacksTitledbTextIsListed() {
        val notice = LicenseNotices.all.single { it.asset == "licenses/titledb-MIT.txt" }
        assertEquals(com.plumemu.R.string.settings_licence_mit, notice.licence)
        val text = readLicenseText(context.assets, notice) { }
        assertTrue((text as LicenseText.Loaded).text.contains("Copyright (c) 2019 Blake Warner"))
    }

    /** Third-party credits list: Coil decodes the box art; OkHttp and Okio ship with it (and the scraper). */
    @Test
    fun coilAndWhatItBringsAreListedUnderApache() {
        val names = LicenseNotices.all.associateBy { it.name }
        listOf(com.plumemu.R.string.settings_license_coil, com.plumemu.R.string.settings_license_okhttp).forEach { name ->
            assertEquals(com.plumemu.R.string.settings_licence_apache, names.getValue(name).licence)
        }
    }

    @Test
    fun theLzmaSdkIsListedAsPublicDomainWithoutAText() {
        val lzma = LicenseNotices.all.single { it.name == com.plumemu.R.string.settings_license_lzma_sdk }
        assertEquals(com.plumemu.R.string.settings_licence_public_domain, lzma.licence)
        assertEquals(LicenseText.NotShipped, readLicenseText(context.assets, lzma) { })
    }
}
