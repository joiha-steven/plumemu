package com.plumemu.platforms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchSpecTest {
    private val catalog = loadShipped()

    @Test
    fun everyEmulatorOfTheTestedSystemsLaunchesWithTheExpectedSpec() {
        val problems = mutableListOf<String>()
        var checked = 0
        for (expected in EXPECTED_SYSTEMS) {
            val system = catalog.system(expected.id)
            if (system == null) {
                problems += "no system ${expected.id}"
                continue
            }
            val actual = catalog.emulatorsOf(system)
            val actualKeys = actual.map { it.core ?: it.id }
            val expectedKeys = expected.entries.map { it.key }
            if (actualKeys != expectedKeys) {
                problems += "${expected.id}: emulators/order differ\n  actual:   $actualKeys\n  expected: $expectedKeys"
            }
            for (entry in expected.entries) {
                val emulator = actual.firstOrNull { (it.core ?: it.id) == entry.key } ?: continue
                val result = LaunchSpecs.build(emulator, GameLocation.Both(expected.path, expected.uri))
                checked++
                if (result != LaunchResult.Built(entry.spec)) {
                    problems += "${expected.id}/${entry.key}:\n  actual:   $result\n  expected: ${entry.spec}"
                }
            }
        }
        println("checked $checked launch specs")
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
        assertTrue("suspiciously few specs: $checked", checked >= 90)
    }

    @Test
    fun retroArch32BitBuildFillsItsOwnPackageIntoThePaths() {
        val emulator = catalog.emulator("retroarch-snes9x")!!
        val location = GameLocation.Path("/storage/emulated/0/ROMs/snes/G.sfc")
        val result = LaunchSpecs.build(emulator, location, "com.retroarch.ra32")
        val spec = (result as LaunchResult.Built).spec
        assertEquals("com.retroarch.ra32", spec.packageName)
        assertEquals(
            ExtraValue.Text("/data/data/com.retroarch.ra32/cores/snes9x_libretro_android.so"),
            spec.extras["LIBRETRO"],
        )
        assertEquals(
            ExtraValue.Text("/storage/emulated/0/Android/data/com.retroarch.ra32/files/retroarch.cfg"),
            spec.extras["CONFIGFILE"],
        )
    }

    @Test
    fun aPackageThatIsNotTheEmulatorsIsRefused() {
        val emulator = catalog.emulator("duckstation")!!
        val location = GameLocation.Both("/a/b.cue", "content://x")
        val result = LaunchSpecs.build(emulator, location, "com.other.app")
        assertEquals(LaunchResult.UnknownPackage("duckstation", "com.other.app"), result)
    }

    @Test
    fun anEmulatorThatNeedsAPathRefusesAGameWithOnlyAUri() {
        val retroArch = catalog.emulator("retroarch-snes9x")!!
        val uriOnly = GameLocation.Uri("content://x/y")
        assertEquals(LaunchResult.NeedsFilePath("retroarch-snes9x"), LaunchSpecs.build(retroArch, uriOnly))
        val nesoid = catalog.emulator("nesoid")!!
        assertEquals(LaunchResult.NeedsFilePath("nesoid"), LaunchSpecs.build(nesoid, uriOnly))
    }

    @Test
    fun anEmulatorThatNeedsAUriRefusesAGameWithOnlyAPath() {
        val duckStation = catalog.emulator("duckstation")!!
        val pathOnly = GameLocation.Path("/a/b.cue")
        assertEquals(LaunchResult.NeedsDocumentUri("duckstation"), LaunchSpecs.build(duckStation, pathOnly))
    }

    // DuckStation's playlist loader cannot open an .m3u by content URI ("Failed to open CD image ... errno 2", seen on an AYN Thor):
    // the path boots it, and the m3u must stay the target so discs can be switched.
    @Test
    fun duckStationGetsAnM3uByPathWhenThePathIsKnown() {
        val duckStation = catalog.emulator("duckstation")!!
        val built = LaunchSpecs.build(duckStation, GameLocation.Both("/storage/AAAA-BBBB/PS1/G/G.m3u", "content://x/G%2EG.m3u"))
        assertEquals(ExtraValue.Text("/storage/AAAA-BBBB/PS1/G/G.m3u"), (built as LaunchResult.Built).spec.extras["bootPath"])
    }

    @Test
    fun duckStationStillGetsOtherFilesByUri() {
        val duckStation = catalog.emulator("duckstation")!!
        val built = LaunchSpecs.build(duckStation, GameLocation.Both("/storage/AAAA-BBBB/PS1/G.chd", "content://x/G.chd"))
        assertEquals(ExtraValue.Text("content://x/G.chd"), (built as LaunchResult.Built).spec.extras["bootPath"])
    }

    @Test
    fun duckStationGetsAnM3uByUriWhenThePathIsUnknown() {
        val duckStation = catalog.emulator("duckstation")!!
        val built = LaunchSpecs.build(duckStation, GameLocation.Uri("content://x/G.M3U"))
        assertEquals(ExtraValue.Text("content://x/G.M3U"), (built as LaunchResult.Built).spec.extras["bootPath"])
    }

    @Test
    fun aUriEmulatorLaunchesFromAUriAloneAndAPathEmulatorFromAPathAlone() {
        val duckStation = catalog.emulator("duckstation")!!
        val built = LaunchSpecs.build(duckStation, GameLocation.Uri("content://x/y")) as LaunchResult.Built
        assertEquals(ExtraValue.Text("content://x/y"), built.spec.extras["bootPath"])
        assertEquals(ExtraValue.Flag(false), built.spec.extras["resumeState"])
        val retroArch = catalog.emulator("retroarch-snes9x")!!
        assertTrue(LaunchSpecs.build(retroArch, GameLocation.Path("/a/b.sfc")) is LaunchResult.Built)
    }

    @Test
    fun vita3kGetsTheTitleIdLastInItsArrayExtraAndNothingElse() {
        val vita3k = catalog.emulator("vita3k")!!
        val titleId = VitaTitleId.parse("PCSE00000\n".toByteArray())!!
        // Neither a path nor a URI is passed on: the game is installed inside Vita3K.
        val built = LaunchSpecs.build(vita3k, GameLocation.Uri("content://x/y.psvita"), vitaTitleId = titleId)
        val expected = LaunchSpec(
            "org.vita3k.emulator", "org.vita3k.emulator.Emulator", null, null, null, null,
            mapOf("AppStartParameters" to ExtraValue.TextArray(listOf("-r", "PCSE00000"))), emptySet(),
        )
        assertEquals(LaunchResult.Built(expected), built)
        val variant = LaunchSpecs.build(vita3k, GameLocation.Uri("content://x/y.psvita"), "org.vita3k.emulator.ikhoeyZX", titleId)
        assertEquals("org.vita3k.emulator.ikhoeyZX", (variant as LaunchResult.Built).spec.packageName)
    }

    @Test
    fun aVitaEmulatorWithoutATitleIdIsRefused() {
        val vita3k = catalog.emulator("vita3k")!!
        assertEquals(LaunchResult.NeedsVitaTitleId("vita3k"), LaunchSpecs.build(vita3k, GameLocation.Both("/a/b.psvita", "content://x")))
    }

    @Test
    fun aTitleIdIsNineCapitalsOrDigitsAndNothingElse() {
        fun utf8(text: String) = text.toByteArray(Charsets.UTF_8)
        assertEquals("PCSE00000", VitaTitleId.parse(utf8("PCSE00000"))?.value)
        assertEquals("PCSB00560", VitaTitleId.parse(utf8("\uFEFF  PCSB00560\r\n"))?.value)
        assertEquals("VITASHELL", VitaTitleId.parse(utf8("VITASHELL"))?.value)
        assertEquals("PCSE00000", VitaTitleId.parse(utf8("PCSE00000\u0000"))?.value)
        for (bad in listOf("", "   \n", "pcse00000", "PCSE0000", "PCSE000000", "PCSE 0000", "PCSE00000\nPCSE00001", "../PCSE000")) {
            assertEquals("'$bad'", null, VitaTitleId.parse(utf8(bad)))
        }
    }

    @Test
    fun aUtf16FileWithItsByteOrderMarkIsRead() {
        val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        assertEquals("PCSE00000", VitaTitleId.parse(bom + "PCSE00000\r\n".toByteArray(Charsets.UTF_16LE))?.value)
        val bigEndian = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + "PCSE00000".toByteArray(Charsets.UTF_16BE)
        assertEquals("PCSE00000", VitaTitleId.parse(bigEndian)?.value)
        assertEquals("PCSE00000", VitaTitleId.parse(bom + "PCSE00000\u0000".toByteArray(Charsets.UTF_16LE))?.value)
        // The format check still applies: blank is empty, lowercase is refused.
        assertTrue(VitaTitleId.isEmptyFile(bom + "\r\n".toByteArray(Charsets.UTF_16LE)))
        assertEquals(null, VitaTitleId.parse(bom + "pcse00000".toByteArray(Charsets.UTF_16LE)))
    }
}
