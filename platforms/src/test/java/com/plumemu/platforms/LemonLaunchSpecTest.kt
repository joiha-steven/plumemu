package com.plumemu.platforms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lemon (Switch) with the shipped data: its own activity, the Intent Eden and Daijisho use. */
class LemonLaunchSpecTest {
    private val catalog = loadShipped()
    private val activity = "dev.lemon.lemon_emu.activities.EmulationActivity"
    private val uri = "content://com.android.externalstorage.documents/document/primary%3AROMs%2Fswitch%2FGame.nsp"

    @Test
    fun lemonIsASwitchEmulatorStartedLikeEdenOnTheContentUri() {
        assertTrue(catalog.emulatorsOf(catalog.system("switch")!!).any { it.id == "lemon" })
        val built = LaunchSpecs.build(catalog.emulator("lemon")!!, GameLocation.Both("/ROMs/switch/Game.nsp", uri))
        val expected = LaunchSpec(
            "dev.lemon.lemon_emulator", activity, "android.nfc.action.TECH_DISCOVERED", null,
            uri, null, emptyMap(), emptySet(),
        )
        assertEquals(LaunchResult.Built(expected), built)
    }

    @Test
    fun everyLemonBuildNamesLemonsActivityAndTheMainBuildComesFirst() {
        val builds = catalog.emulator("lemon")!!.allPackages
        assertEquals("dev.lemon.lemon_emulator", builds.first().packageName)
        assertEquals(
            setOf(
                "dev.lemon.lemon_emulator", "dev.lemon.lemon_emulator.lite", "dev.legacy.lemon_emulator",
                "com.antutu.ABenchMark", "com.miHoYo.Yuanshen",
            ),
            builds.map { it.packageName }.toSet(),
        )
        assertTrue(builds.all { it.activity == activity })
    }
}
