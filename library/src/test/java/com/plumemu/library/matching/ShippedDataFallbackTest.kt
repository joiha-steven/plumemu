package com.plumemu.library.matching

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The extension fallback against the shipped platforms.json (Robolectric only for a working org.json). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShippedDataFallbackTest {
    private val rules: List<SystemRule> = JSONObject(File("../platforms/src/main/assets/platforms.json").readText())
        .getJSONArray("systems").let { array ->
            (0 until array.length()).map { i ->
                val s = array.getJSONObject(i)
                fun strings(key: String) = s.getJSONArray(key).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                SystemRule(
                    s.getString("id"), strings("folders"), strings("extensions"),
                    setOf(s.getString("name"), s.getString("shortName"), s.getString("displayName")), strings("emulators"),
                )
            }
        }
    private val matcher = SystemMatcher(rules)

    @Test
    fun strayFilesInADosFolderAddNothing() {
        val dos = matcher.matchFolder("DOS")
        assertTrue(dos is FolderMatch.System)
        for (file in listOf("README.DOC", "LEVEL1.MAP", "INTRO.AVI", "init.lua", "DATA.HEX", "x.int", "x.info", "x.o", "x.p", "x.z")) {
            assertFalse(file, matcher.matchFile(dos, file) is FileMatch.Matched && file.substringAfterLast('.').lowercase() !in rules.first { it.id == "dos" }.extensions)
        }
    }

    @Test
    fun gbaInAGameBoyFolderIsStillFoundAsGba() {
        assertEquals(FileMatch.Matched("gba"), matcher.matchFile(matcher.matchFolder("Nintendo - Game Boy"), "Pokemon Ruby (USA).gba"))
    }

    @Test
    fun listsWhichSystemPairsAllowTheFallback() {
        val pairs = sortedSetOf<String>()
        for (folder in rules) for (ext in rules.flatMap { it.extensions }.toSet()) {
            val claimants = rules.filter { ext in it.extensions }
            if (folder in claimants) continue
            val result = matcher.matchFile(FolderMatch.System(folder), "x.$ext")
            if (result is FileMatch.Matched) pairs += "${folder.id} -> ${result.systemId} (.$ext)"
        }
        println("Fallback pairs (${pairs.size}):\n" + pairs.joinToString("\n"))
        assertTrue(pairs.any { it.startsWith("gb -> gba") })
        assertFalse(pairs.any { it.startsWith("dos -> ebook") || it.startsWith("dos -> videos") })
    }
}
