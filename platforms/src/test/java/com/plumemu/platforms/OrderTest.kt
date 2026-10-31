package com.plumemu.platforms

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The first emulator of each tested system, written here by hand from a chosen list of first emulators and, where there is none, the first standalone
 * emulator of ES-DE's list for that system (a standalone comes before any RetroArch core). Not generated from the data.
 */
class OrderTest {
    private val firstEmulator = mapOf(
        "nes" to "retroarch-mesen",
        "snes" to "retroarch-snes9x",
        "gb" to "retroarch-gambatte",
        "gbc" to "retroarch-gambatte",
        "gba" to "retroarch-mgba",
        "n64" to "m64plus-fz",
        "nds" to "melonds",
        "megadrive" to "retroarch-genesis-plus-gx",
        "psx" to "duckstation",
        "psp" to "ppsspp",
        "ps2" to "nethersx2",
        "gc" to "dolphin",
        "arcade" to "retroarch-mamearcade",
    )

    @Test
    fun theFirstEmulatorOfEachTestedSystemIsTheChosenOne() {
        val catalog = loadShipped()
        for ((systemId, expected) in firstEmulator) {
            val first = catalog.emulatorsOf(catalog.system(systemId)!!).first()
            assertEquals("first emulator of $systemId", expected, first.id)
        }
    }

    @Test
    fun theFirstEmulatorIsAlsoTheDefaultCandidate() {
        val catalog = loadShipped()
        for ((systemId, expected) in firstEmulator) {
            assertEquals(expected, catalog.defaultCandidates(systemId).first().id)
        }
    }
}
