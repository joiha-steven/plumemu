package com.plumemu.identify.arcade

import com.plumemu.identify.pack.ArcadeFlags
import com.plumemu.identify.pack.ArcadePackBuilder
import com.plumemu.identify.pack.TestPacks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArcadeLookupTest {
    private val lookup = ArcadeLookup(TestPacks.open(TestPacks.arcade()))

    @Test fun parentHasNoParentAndShows() {
        val parent = lookup.find("zorkfght.zip")
        assertEquals("Zork Fighters", parent?.title)
        assertEquals(1996, parent?.year)
        assertEquals("Testsoft", parent?.maker)
        assertNull(parent?.parent)
        assertFalse(parent?.hide ?: true)
        assertFalse(parent?.isClone ?: true)
    }

    @Test fun cloneNamesItsParent() {
        val clone = lookup.find("ZORKFGHTJ.ZIP")
        assertEquals("zorkfght", clone?.parent)
        assertTrue(clone?.isClone == true)
        assertFalse(clone?.hide ?: true)
    }

    @Test fun biosAndDeviceAreHiddenMechanicalIsNot() {
        assertTrue(lookup.find("testbios.zip")?.hide == true)
        assertTrue(lookup.find("testbios.7z")?.isBios == true)
        assertTrue(lookup.find("testdev.zip")?.hide == true)
        assertTrue(lookup.find("testslot.zip")?.isMechanical == true)
        assertFalse(lookup.find("testslot.zip")?.hide ?: true)
    }

    @Test fun notAGameFlagHides() {
        val b = ArcadePackBuilder("arcade", 1)
        b.set("zorkdemo", "Zork Test Rig", flags = ArcadeFlags.NOT_A_GAME)
        val other = ArcadeLookup(TestPacks.open(b.build())).find("zorkdemo.zip")
        assertTrue(other?.hide == true)
        assertFalse(other?.isBios ?: true)
        assertFalse(other?.isDevice ?: true)
    }

    @Test fun unknownAndOddNames() {
        assertNull(lookup.find("nosuchset.zip"))
        assertNull(lookup.find(".zip"))
        assertNull(lookup.find(""))
        assertEquals("zorkfght", lookup.find("roms/mame/zorkfght.zip")?.shortName)
        assertEquals("zorkfght", lookup.find("zorkfght")?.shortName)
    }

    @Test fun systemsAndKeys() {
        listOf("arcade", "mame", "fba", "fbneo", "neogeo", "cps1", "cps2", "cps3", "naomi", "atomiswave", "stv", "model2", "model3", "MAME").forEach {
            assertTrue(it, ArcadeSystems.isArcade(it))
        }
        listOf("neogeocd", "consolearcade", "pcarcade", "snes").forEach { assertFalse(it, ArcadeSystems.isArcade(it)) }
        assertEquals("arcade:zorkfght", ArcadeSystems.fixKey("ZorkFght"))
        // A file name may hold a line break or another control character: no key (row-id fallback).
        assertEquals(null, ArcadeSystems.fixKey(ArcadeSystems.shortNameOf("sf2\nce.zip")))
        assertEquals(null, ArcadeSystems.fixKey("sf2\tce"))
        assertEquals("zork.v2", ArcadeSystems.shortNameOf("Zork.V2.zip"))
    }
}
