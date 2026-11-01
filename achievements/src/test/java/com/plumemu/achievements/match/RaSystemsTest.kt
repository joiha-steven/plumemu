package com.plumemu.achievements.match

import com.plumemu.achievements.hash.RaConsoles
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

class RaSystemsTest {
    /** Plumemu's system ids, read from the platforms module's file in this repository. */
    private val plumemuSystems: Set<String> by lazy {
        val file = listOf("../platforms/src/main/assets/platforms.json", "platforms/src/main/assets/platforms.json")
            .map(::File).firstOrNull { it.isFile }
            ?: error("platforms.json not found from ${File(".").absolutePath}")
        Json.parseToJsonElement(file.readText()).jsonObject.getValue("systems").jsonArray
            .map { it.jsonObject.getValue("id").jsonPrimitive.content }.toSet()
    }

    /** Every console id [RaConsoles] names, by constant name. */
    private val raConsoles: Map<String, Int> =
        RaConsoles::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType }
            .associate { it.name to it.getInt(null) }

    @Test fun `every mapped system is a Plumemu system`() {
        val unknown = RaSystems.systemIds - plumemuSystems
        assertTrue("not in platforms.json: $unknown", unknown.isEmpty())
    }

    @Test fun `every mapped console is a RetroAchievements console`() {
        val consoles = raConsoles.values.toSet()
        val unknown = RaSystems.systemIds.filter { RaSystems.consoleOf(it) !in consoles }
        assertTrue("console ids not in RaConsoles: $unknown", unknown.isEmpty())
    }

    /**
     * RetroAchievements consoles with no Plumemu system. A console added to [RaConsoles] fails here
     * until it is mapped in [RaSystems] or added to this list with a Plumemu system missing.
     */
    @Test fun `the RetroAchievements consoles Plumemu has no system for are the known ones`() {
        val mapped = RaSystems.systemIds.mapNotNull(RaSystems::consoleOf).toSet()
        val unmapped = raConsoles.filterValues { it !in mapped }.keys
        assertEquals(setOf("ORIC", "FM_TOWNS", "SUPER_CASSETTEVISION", "ELEKTOR_TV_GAMES_COMPUTER", "TI83"), unmapped)
    }

    @Test fun `systems RetroAchievements does not have give no console`() {
        for (id in listOf("switch", "psvita", "android", "steam", "atari5200", "amiga", "")) assertNull(id, RaSystems.consoleOf(id))
    }

    @Test fun `shared consoles`() {
        assertEquals(RaConsoles.NEOGEO_POCKET, RaSystems.consoleOf("ngpc"))
        assertEquals(RaConsoles.PC_ENGINE_CD, RaSystems.consoleOf("tg-cd"))
        assertEquals(RaConsoles.ARCADE, RaSystems.consoleOf("fbneo"))
        assertEquals(RaConsoles.MSX, RaSystems.consoleOf("msx2"))
    }
}
