package com.plumemu.platforms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The order of a system's RetroArch cores is the order of the data, written by the converter from
 * `RETROARCH_CORE_ORDER`: the app offers the next core in it after a "No", so a data update that
 * changes the order changes the offer with no app release.
 */
class CoreOrderTest {
    private fun coresOf(catalog: PlatformCatalog, systemId: String) =
        catalog.emulatorsOf(catalog.system(systemId)!!).filter { it.core != null }.map { it.id }

    @Test
    fun theShippedOrderPutsTheCoresPlayersHaveFirst() {
        val catalog = loadShipped()
        assertEquals(listOf("retroarch-mgba", "retroarch-gpsp", "retroarch-vbam"), coresOf(catalog, "gba").take(3))
        val snes = coresOf(catalog, "snes")
        assertTrue(snes.indexOf("retroarch-snes9x") < snes.indexOf("retroarch-bsnes"))
        // The default core stays what it was; the order applies to the cores after it.
        assertEquals(listOf("retroarch-mednafen-psx", "retroarch-swanstation", "retroarch-pcsx-rearmed"), coresOf(catalog, "psx").take(3))
        assertEquals("retroarch-mamearcade", coresOf(catalog, "arcade").first())
        assertEquals("retroarch-gambatte", coresOf(catalog, "gb").first())
    }

    private fun twoCores(first: String, second: String) = """
        {"formatVersion":2,
         "systems":[{"id":"gba","name":"GBA","shortName":"GBA","displayName":"GBA","order":0,"folders":["gba"],"extensions":["gba"],
                     "cover":"landscape","tier":"tested","emulators":["$first","$second"]}],
         "emulators":[
           {"id":"retroarch-a","name":"RetroArch: A","package":"com.retroarch","core":"a_libretro_android.so","game":{"mode":"path","target":"data"}},
           {"id":"retroarch-b","name":"RetroArch: B","package":"com.retroarch","core":"b_libretro_android.so","game":{"mode":"path","target":"data"}}]}
    """.trimIndent()

    @Test
    fun aDataUpdateThatSwapsTwoCoresSwapsTheOrder() {
        for ((first, second) in listOf("retroarch-a" to "retroarch-b", "retroarch-b" to "retroarch-a")) {
            val result = PlatformLoader.load(twoCores(first, second), "test.json", RecordingLog())
            val catalog = (result as PlatformLoadResult.Loaded).catalog
            assertEquals(listOf(first, second), coresOf(catalog, "gba"))
        }
    }
}
