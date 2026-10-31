package com.plumemu.library.corpus

import com.plumemu.identify.Identifier
import com.plumemu.identify.IdentifyLog
import com.plumemu.identify.fingerprint.FingerprintReader
import com.plumemu.identify.pack.BundledPacks
import com.plumemu.identify.pack.PackStore
import com.plumemu.library.matching.SystemRule
import com.plumemu.library.scan.GameNaming
import org.json.JSONObject
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Collections

/**
 * The data the app ships, read from the source tree: the system rules
 * of `platforms.json` and identify's bundled name packs.
 */
internal object RealData {
    /**
     * The repository root: `plumemu.root` (set by library/build.gradle.kts), else the first
     * folder above the working directory holding settings.gradle.kts (a test run from an IDE).
     */
    val ROOT: File = System.getProperty("plumemu.root")?.let(::File)
        ?: generateSequence(File("").absoluteFile) { it.parentFile }.firstOrNull { File(it, "settings.gradle.kts").isFile }
        ?: error("cannot find the repository root from ${File("").absolutePath}; set -Dplumemu.root")
    val CORPUS_DIR = File(ROOT, "library/src/test/resources/corpus")
    val SAMPLE = File(ROOT, "library/src/test/resources/corpus-sample/sample.tsv")
    val REPORT_DIR = File(ROOT, "library/build/corpus")
    private val PLATFORMS = File(ROOT, "platforms/src/main/assets/platforms.json")
    val PACK_ASSETS = File(ROOT, "identify/src/main/assets/identify")

    /**
     * `platforms.json` as the app turns it into rules (`app` `AppGraph.systemRulesOf` over
     * `PlatformLoader`, which keeps the file's order and values): org.json instead of the
     * platforms module, which library must not depend on. Needs Robolectric
     * for a working org.json.
     */
    fun systemRules(): List<SystemRule> {
        val systems = JSONObject(PLATFORMS.readText()).getJSONArray("systems")
        return (0 until systems.length()).map { i ->
            val s = systems.getJSONObject(i)
            fun strings(key: String) = s.getJSONArray(key).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
            SystemRule(
                s.getString("id"), strings("folders"), strings("extensions"),
                setOf(s.getString("name"), s.getString("shortName"), s.getString("displayName")), strings("emulators"),
            )
        }
    }

    /** Lines the store and identifier log (pack install failures, corrupt packs); a corpus run prints them. */
    class Naming(val naming: GameNaming, val log: List<String>)

    /**
     * Skips the calling test when the bundled name packs are absent: the public source copy does
     * not carry them (private data).
     */
    fun assumePacks() = require(
        File(PACK_ASSETS, "packs.txt").isFile,
        "no bundled name packs in this build (identify/src/main/assets/identify is private data, absent from the public source)",
    )

    /**
     * Where the file listing the private paths exists (the private repository), missing data means a file
     * was moved or renamed, so the test fails instead of skipping.
     */
    private fun require(found: Boolean, reason: String) {
        if (!found && File(ROOT, "scripts/public-paths.txt").isFile) {
            fail("$reason; this is the private repository, so the file was moved or renamed")
        }
        assumeTrue(reason, found)
    }

    /** Skips the calling test when [path] (under the repository root, in a private folder) is absent. */
    fun assumePrivateFile(path: String) =
        require(File(ROOT, path).isFile, "$path is absent (private data, not in the public source)")

    /**
     * The real bundled packs through a [PackStore] in [packDirectory], and the real [Identifier].
     * Skips the calling test when the packs are absent ([assumePacks]).
     */
    fun naming(packDirectory: File): Naming {
        assumePacks()
        val lines = Collections.synchronizedList(ArrayList<String>())
        val log = IdentifyLog { lines += it }
        val bundled = object : BundledPacks {
            override fun readIndex(): String = File(PACK_ASSETS, "packs.txt").readText()

            override fun open(fileName: String): InputStream = FileInputStream(File(PACK_ASSETS, fileName))
        }
        val packs = PackStore(packDirectory, bundled, log)
        return Naming(GameNaming(packs, Identifier(packs, FingerprintReader(), log)), lines)
    }
}
