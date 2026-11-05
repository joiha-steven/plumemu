package com.plumemu.game

import com.plumemu.launcher.choice.ChoiceSource
import com.plumemu.launcher.choice.EmulatorPick
import com.plumemu.platforms.Emulator
import com.plumemu.platforms.GamePass
import com.plumemu.platforms.GameTarget
import com.plumemu.platforms.PackageVariant
import com.plumemu.platforms.PassMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The card's "RetroArch · Snes9x": the pick's emulator carries its core, other emulators none. */
internal class EmulatorToStartCoreTest {
    private fun emulator(id: String, name: String, core: String?) =
        Emulator(
            id = id,
            name = name,
            primary = PackageVariant("com.example", null, null),
            variants = emptyList(),
            core = core,
            action = null,
            category = null,
            mimeType = null,
            flags = emptySet(),
            game = GamePass(PassMode.Path, GameTarget.Data, null),
            extras = emptyMap(),
            legalRisk = false,
        )

    private val snes9x = emulator("retroarch-snes9x", "RetroArch: Snes9x", "snes9x_libretro_android.so")
    private val standalone = emulator("duckstation", "DuckStation", null)

    @Test
    fun aRetroArchCoreIsReadyWithItsFileAndItsOwnName() {
        val start = emulatorToStartOf(EmulatorPick.Ready(snes9x, "com.retroarch", ChoiceSource.PersonForSystem, "snes9x_libretro_android.so"))!!
        assertEquals("RetroArch: Snes9x", start.name)
        assertEquals("snes9x_libretro_android.so", start.core)
        assertEquals("Snes9x", start.coreName)
    }

    @Test
    fun anEmulatorWithoutCoresHasNoCore() {
        val start = emulatorToStartOf(EmulatorPick.Ready(standalone, "com.duck", ChoiceSource.PersonForGame, null))!!
        assertNull(start.core)
        assertNull(start.coreName)
    }

    @Test
    fun aChosenCoreThatIsNotInstalledStillNamesItsCoreAndAnUnknownOneHasNone() {
        val missing = emulatorToStartOf(EmulatorPick.ChosenMissing(snes9x, snes9x.id, ChoiceSource.PersonForGame))!!
        assertEquals("Snes9x", missing.coreName)
        assertEquals(false, missing.isInstalled)
        val gone = emulatorToStartOf(EmulatorPick.ChosenMissing(null, "old-emulator", ChoiceSource.PersonForGame))!!
        assertEquals("old-emulator", gone.name)
        assertNull(gone.core)
    }

    @Test
    fun notChosenYetAndNoneInstalledHaveNoCore() {
        val notChosen = emulatorToStartOf(EmulatorPick.NotChosen("snes", listOf(snes9x), snes9x))!!
        assertEquals(true, notChosen.isNotChosenYet)
        assertNull(notChosen.core)
        assertNull(emulatorToStartOf(EmulatorPick.NoneInstalled("snes", emptyList())))
    }
}
