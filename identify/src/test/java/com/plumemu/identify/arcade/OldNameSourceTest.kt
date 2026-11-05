package com.plumemu.identify.arcade

import com.plumemu.identify.pack.PackAssets
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** The pack carries only the source's display name, so the constant must stay equal to the build's `sources.json`. */
class OldNameSourceTest {
    @Test fun theConstantIsTheNameOfTheArcadeAliasesSource() {
        val path = "../tools/names/sources.json"
        PackAssets.assumeNamesTool(path)
        val text = File(path).readText()
        val entry = Regex("""\{[^{}]*"kind"\s*:\s*"arcade-aliases"[^{}]*\{[^{}]*\}[^{}]*\}""").find(text)?.value
        val name = Regex(""""name"\s*:\s*"([^"]+)"""").find(checkNotNull(entry) { "no arcade-aliases entry in sources.json" })?.groupValues?.get(1)
        assertEquals(ArcadeSystems.OLD_NAME_SOURCE, name)
    }
}
