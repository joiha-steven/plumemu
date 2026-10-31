package com.plumemu.launcher.intent

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import com.plumemu.launcher.TestCatalogs
import com.plumemu.platforms.GameLocation
import com.plumemu.platforms.LaunchResult
import com.plumemu.platforms.LaunchSpecs
import com.plumemu.platforms.PassMode
import com.plumemu.platforms.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Intent of the first emulator of each of the twelve tested systems, field by field. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LaunchIntentsTest {
    private val catalog = TestCatalogs.shipped

    private data class Expected(
        val component: String,
        val action: String? = null,
        val category: String? = null,
        val dataIsUri: Boolean = false,
        val type: String? = null,
        val extras: Map<String, Any>,
        val flags: Int,
        val grants: Boolean,
    )

    private fun path(system: String) = "/storage/emulated/0/ROMs/$system/Game #1 (USA).bin"
    private fun uri(system: String) =
        "content://com.android.externalstorage.documents/tree/primary%3AROMs/document/primary%3AROMs%2F$system%2FGame%20%231%20(USA).bin"

    private val retroArch = "com.retroarch.aarch64/com.retroarch.browser.retroactivity.RetroActivityFuture"
    private fun retroArchExtras(system: String, core: String) = mapOf(
        "ROM" to path(system),
        "CONFIGFILE" to "/storage/emulated/0/Android/data/com.retroarch.aarch64/files/retroarch.cfg",
        "LIBRETRO" to "/data/data/com.retroarch.aarch64/cores/${core}_libretro_android.so",
    )
    private val newTask = Intent.FLAG_ACTIVITY_NEW_TASK
    private val grant = Intent.FLAG_GRANT_READ_URI_PERMISSION
    private val clear = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP

    private val expected = mapOf(
        "nes" to Expected(retroArch, extras = retroArchExtras("nes", "mesen"), flags = newTask, grants = false),
        "snes" to Expected(retroArch, extras = retroArchExtras("snes", "snes9x"), flags = newTask, grants = false),
        "gb" to Expected(retroArch, extras = retroArchExtras("gb", "gambatte"), flags = newTask, grants = false),
        "gbc" to Expected(retroArch, extras = retroArchExtras("gbc", "gambatte"), flags = newTask, grants = false),
        "gba" to Expected(retroArch, extras = retroArchExtras("gba", "mgba"), flags = newTask, grants = false),
        "n64" to Expected(
            "org.mupen64plusae.v3.fzurita.amazon/paulscode.android.mupen64plusae.SplashActivity",
            action = Intent.ACTION_VIEW, dataIsUri = true, extras = emptyMap(), flags = newTask or grant, grants = true,
        ),
        "nds" to Expected(
            "me.magnum.melonds/me.magnum.melonds.ui.emulator.EmulatorActivity",
            action = "me.magnum.melonds.LAUNCH_ROM", extras = mapOf("uri" to uri("nds")), flags = newTask or grant, grants = true,
        ),
        "megadrive" to Expected(retroArch, extras = retroArchExtras("megadrive", "genesis_plus_gx"), flags = newTask, grants = false),
        "arcade" to Expected(retroArch, extras = retroArchExtras("arcade", "mamearcade"), flags = newTask, grants = false),
        "psx" to Expected(
            "com.github.stenzek.duckstation/com.github.stenzek.duckstation.EmulationActivity",
            extras = mapOf("bootPath" to uri("psx"), "resumeState" to false),
            flags = newTask or clear or grant, grants = true,
        ),
        "psp" to Expected(
            "org.ppsspp.ppssppgold/org.ppsspp.ppsspp.PpssppActivity",
            action = Intent.ACTION_VIEW, category = Intent.CATEGORY_DEFAULT, dataIsUri = true,
            extras = emptyMap(), flags = newTask or grant, grants = true,
        ),
        "ps2" to Expected(
            "xyz.aethersx2.android/xyz.aethersx2.android.EmulationActivity",
            action = Intent.ACTION_MAIN, extras = mapOf("bootPath" to uri("ps2")),
            flags = newTask or clear or grant, grants = true,
        ),
        "gc" to Expected(
            "org.dolphinemu.dolphinemu/org.dolphinemu.dolphinemu.ui.main.TvMainActivity",
            action = Intent.ACTION_MAIN, category = Intent.CATEGORY_LEANBACK_LAUNCHER,
            extras = mapOf("AutoStartFile" to uri("gc")), flags = newTask or grant, grants = true,
        ),
    )

    @Test
    fun theTableCoversExactlyTheTestedSystems() {
        assertEquals(catalog.systems.filter { it.tier == Tier.Tested }.map { it.id }.toSet(), expected.keys)
    }

    @Test
    fun internalStorageGamesGetTheExpectedIntentInPathAndUriModes() {
        for ((systemId, want) in expected) {
            val intent = intentFor(systemId, GameLocation.Both(path(systemId), uri(systemId)))
            assertIntent(systemId, want, intent)
        }
    }

    @Test
    fun aFolderWithoutPathStillStartsUriEmulatorsAndRefusesPathOnes() {
        for ((systemId, want) in expected) {
            val emulator = catalog.emulatorsOf(checkNotNull(catalog.system(systemId))).first()
            val result = LaunchSpecs.build(emulator, GameLocation.Uri(uri(systemId)))
            if (emulator.game.mode == PassMode.Path) {
                assertEquals(systemId, LaunchResult.NeedsFilePath(emulator.id), result)
            } else {
                val spec = (result as LaunchResult.Built).spec
                assertIntent(systemId, want, LaunchIntents.build(spec, uri(systemId), null))
            }
        }
    }

    @Test
    fun theThirtyTwoBitRetroArchBuildFillsItsOwnPackage() {
        val emulator = checkNotNull(catalog.emulator("retroarch-snes9x"))
        val spec = (LaunchSpecs.build(emulator, GameLocation.Both(path("snes"), uri("snes")), "com.retroarch.ra32") as LaunchResult.Built).spec
        val intent = LaunchIntents.build(spec, null, null)
        assertEquals("com.retroarch.ra32", intent.component?.packageName)
        assertEquals("/data/data/com.retroarch.ra32/cores/snes9x_libretro_android.so", intent.getStringExtra("LIBRETRO"))
    }

    @Test
    fun aPathInTheDataFieldStaysABarePathWithoutGrant() {
        val emulator = checkNotNull(catalog.emulator("nesoid"))
        val spec = (LaunchSpecs.build(emulator, GameLocation.Both(path("nes"), uri("nes"))) as LaunchResult.Built).spec
        val intent = LaunchIntents.build(spec, null, null)
        assertEquals(path("nes"), intent.dataString)
        assertNull(intent.data?.scheme)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(0, intent.flags and grant)
        assertNull(intent.clipData)
    }

    @Test
    fun aMimeTypeLeftToTheFileComesFromTheProvider() {
        val emulator = checkNotNull(catalog.emulator("org-koreader-launcher"))
        val spec = (LaunchSpecs.build(emulator, GameLocation.Uri(uri("pdf"))) as LaunchResult.Built).spec
        assertEquals(LaunchIntents.FILE_MIME_PLACEHOLDER, spec.mimeType)
        assertEquals("application/pdf", LaunchIntents.build(spec, uri("pdf"), "application/pdf").type)
        assertNull(LaunchIntents.build(spec, uri("pdf"), null).type)
    }

    @Test
    fun aFixedMimeTypeIsKept() {
        val emulator = checkNotNull(catalog.emulator("org-ppsspp-ppsspplegacy"))
        val spec = (LaunchSpecs.build(emulator, GameLocation.Uri(uri("psp"))) as LaunchResult.Built).spec
        val intent = LaunchIntents.build(spec, uri("psp"), "application/x-whatever")
        assertEquals("application/octet-stream", intent.type)
        assertEquals(Uri.parse(uri("psp")), intent.data)
    }

    @Test
    fun noHistoryFlagIsCarried() {
        val emulator = catalog.emulators.first { it.flags.size == 3 }
        val spec = (LaunchSpecs.build(emulator, GameLocation.Both(path("x"), uri("x"))) as LaunchResult.Built).spec
        val intent = LaunchIntents.build(spec, null, null)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NO_HISTORY != 0)
        assertTrue(intent.flags and clear == clear)
    }

    private fun intentFor(systemId: String, location: GameLocation): Intent {
        val emulator = catalog.emulatorsOf(checkNotNull(catalog.system(systemId))).first()
        val spec = (LaunchSpecs.build(emulator, location) as LaunchResult.Built).spec
        val grantUri = if (emulator.game.mode == PassMode.Uri) uri(systemId) else null
        return LaunchIntents.build(spec, grantUri, "application/octet-stream")
    }

    private fun assertIntent(systemId: String, want: Expected, intent: Intent) {
        assertEquals("$systemId component", ComponentName.unflattenFromString(want.component), intent.component)
        assertEquals("$systemId action", want.action, intent.action)
        assertEquals("$systemId categories", want.category?.let { setOf(it) }, intent.categories)
        assertEquals("$systemId data", if (want.dataIsUri) Uri.parse(uri(systemId)) else null, intent.data)
        assertEquals("$systemId type", want.type, intent.type)
        assertEquals("$systemId extras", want.extras, extrasOf(intent))
        assertEquals("$systemId flags", want.flags, intent.flags)
        val clip = intent.clipData
        if (want.grants) {
            assertEquals("$systemId clip", Uri.parse(uri(systemId)), clip?.getItemAt(0)?.uri)
        } else {
            assertNull("$systemId clip", clip)
        }
    }

    private fun extrasOf(intent: Intent): Map<String, Any?> {
        val bundle = intent.extras ?: return emptyMap()
        // Bundle.get(String) is deprecated for typed getters; a test that compares every kind needs it.
        @Suppress("DEPRECATION")
        return bundle.keySet().associateWith { bundle.get(it) }
    }
}
