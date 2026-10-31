package com.plumemu.platforms

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The platforms.json that ships in the APK. Unit tests run with the module folder as working directory. */
internal fun loadShipped(): PlatformCatalog {
    val file = File("src/main/assets/platforms.json")
    val log = RecordingLog()
    val result = PlatformLoader.load(file.readText(), "assets/platforms.json", log)
    if (result !is PlatformLoadResult.Loaded) {
        fail("shipped platforms.json rejected: ${log.lines}")
    }
    return (result as PlatformLoadResult.Loaded).catalog
}

class ShippedDataTest {
    private val testedSystemIds =
        listOf("nes", "snes", "gb", "gbc", "gba", "n64", "nds", "megadrive", "psx", "psp", "ps2", "gc", "arcade")

    @Test
    fun theShippedFileIsAccepted() {
        val catalog = loadShipped()
        println("systems=${catalog.systems.size} emulators=${catalog.emulators.size}")
        assertTrue("expected at least 100 systems, got ${catalog.systems.size}", catalog.systems.size >= 100)
    }

    @Test
    fun theTwelveTestedSystemsAreMarkedTestedAndHaveEmulators() {
        val catalog = loadShipped()
        for (id in testedSystemIds) {
            val system = assertNotNull_(catalog.system(id), id)
            assertEquals("tier of $id", Tier.Tested, system.tier)
            assertTrue("$id has no emulator", catalog.emulatorsOf(system).isNotEmpty())
        }
        val tested = catalog.systems.filter { it.tier == Tier.Tested }.map { it.id }.toSet()
        assertEquals(testedSystemIds.toSet(), tested)
    }

    @Test
    fun hardwareHasItsMakerAndYearAndEnginesHaveNone() {
        val catalog = loadShipped()
        val snes = assertNotNull_(catalog.system("snes"), "snes")
        assertEquals("Nintendo", snes.maker)
        assertEquals(1990, snes.year)
        val scummvm = assertNotNull_(catalog.system("scummvm"), "scummvm")
        assertEquals(null, scummvm.maker)
        assertEquals(null, scummvm.year)
        // Maker & year needs both: one without the other would sort neither with its maker nor last.
        for (system in catalog.systems) assertEquals("${system.id} has a maker and a year, or neither", system.maker == null, system.year == null)
    }

    @Test
    fun hardwareHasItsKindAndEndYearWhereOneIsKnown() {
        val catalog = loadShipped()
        val snes = assertNotNull_(catalog.system("snes"), "snes")
        assertEquals(SystemKind.Console, snes.kind)
        assertEquals(2003, snes.endYear)
        // Switch is still made: no end year, so the line reads "2017-present".
        val switch = assertNotNull_(catalog.system("switch"), "switch")
        assertEquals(SystemKind.Console, switch.kind)
        assertEquals(null, switch.endYear)
        assertTrue(switch.inProduction)
        val nds = assertNotNull_(catalog.system("nds"), "nds")
        assertEquals(null, nds.endYear)
        assertTrue("nds is unknown, not in production", !nds.inProduction)
        assertEquals(setOf("arduboy", "macintosh", "switch", "windows"), catalog.systems.filter { it.inProduction }.map { it.id }.toSet())
        assertEquals(SystemKind.Arcade, assertNotNull_(catalog.system("naomi"), "naomi").kind)
        assertEquals(SystemKind.AddOn, assertNotNull_(catalog.system("megacd"), "megacd").kind)
        assertEquals(SystemKind.Os, assertNotNull_(catalog.system("dos"), "dos").kind)
        assertEquals(null, assertNotNull_(catalog.system("scummvm"), "scummvm").kind)
        for (system in catalog.systems) {
            // The download stores are not hardware: they keep maker and year and have no kind.
            val downloadStore = system.id in setOf("wiiware", "pspminis")
            assertEquals("${system.id} has a kind exactly when it has a maker", system.maker == null || downloadStore, system.kind == null)
            val end = system.endYear ?: continue
            assertTrue("${system.id} ends in $end, before it started in ${system.year}", end >= system.year!!)
        }
    }

    @Test
    fun everySystemHasAUniqueCodeOfAtMostThreeCharacters() {
        val catalog = loadShipped()
        for (system in catalog.systems) {
            assertTrue("${system.id} code '${system.code}' is 1 to 3 characters", system.code.length in 1..3)
        }
        val codes = catalog.systems.map { it.code.lowercase() }
        assertEquals("codes are unique", codes.size, codes.toSet().size)
        assertEquals("SNS", catalog.system("snes")?.code)
        assertEquals("PS1", catalog.system("psx")?.code)
    }

    @Test
    fun everySystemHasAUniqueAbbreviationOfAtMostFiveCharacters() {
        val catalog = loadShipped()
        for (system in catalog.systems) {
            assertTrue("${system.id} abbr '${system.abbr}' is 1 to 5 characters", system.abbr.length in 1..5)
        }
        val abbrs = catalog.systems.map { it.abbr.lowercase() }
        assertEquals("abbreviations are unique", abbrs.size, abbrs.toSet().size)
        assertEquals("SNES", catalog.system("snes")?.abbr)
        assertEquals("PS1", catalog.system("psx")?.abbr)
    }

    @Test
    fun everySystemEmulatorReferenceResolvesAndFoldersAreUnique() {
        val catalog = loadShipped()
        for (system in catalog.systems) {
            assertEquals("emulators of ${system.id}", system.emulatorIds.size, catalog.emulatorsOf(system).size)
        }
        val folders = catalog.systems.flatMap { it.folders }
        assertEquals(folders.size, folders.toSet().size)
    }

    @Test
    fun systemsCarryTheirOwnColourAndEnginesHaveNone() {
        val catalog = loadShipped()
        assertEquals(0x7B5EA7, catalog.system("snes")!!.color)
        assertEquals(0x107C10, catalog.system("xbox")!!.color)
        // Engines, frontends and generic folders have no identity colour: the accent falls back.
        for (id in listOf("doom", "quake", "scummvm", "ports", "videos", "arcade", "mame")) {
            assertEquals(id, null, catalog.system(id)!!.color)
        }
        assertTrue(catalog.systems.count { it.color != null } > 100)
    }

    @Test
    fun measuredSystemsCarryTheirBoxFrontRatioAndTheOthersTheirClasssDefault() {
        val catalog = loadShipped()
        fun shape(id: String) = catalog.system(id)!!.let { it.coverRatio to it.cover }
        // One US box front each, measured (the build tools' cover ratio table).
        assertEquals(1.37 to CoverShape.Landscape, shape("snes"))
        assertEquals(1.12 to CoverShape.Portrait, shape("nds"))
        // Was landscape by hand until measured: the US Genesis box is tall.
        assertEquals(0.71 to CoverShape.Portrait, shape("megadrive"))
        assertEquals(0.575 to CoverShape.Portrait, shape("psp"))
        // Not measured: the class's default.
        assertEquals(0.71 to CoverShape.Portrait, shape("3do"))
        assertEquals(1.4 to CoverShape.Landscape, shape("atomiswave"))
    }

    @Test
    fun shortNamesAreShortAndNotJustTheId() {
        for (system in loadShipped().systems) {
            assertTrue("${system.id}: '${system.shortName}'", system.shortName.length <= system.name.length)
            assertTrue("${system.id}: '${system.shortName}'", system.shortName.length <= 16)
        }
        val catalog = loadShipped()
        assertEquals("Wii", catalog.system("wii")?.shortName)
        assertEquals("Switch", catalog.system("switch")?.shortName)
        assertEquals("Apple II", catalog.system("apple2")?.shortName)
        assertEquals("Atomiswave", catalog.system("atomiswave")?.shortName)
    }

    @Test
    fun testedSystemsHaveTheDisplayNameAndOrderTheMainScreenWants() {
        // Written by hand from the design board, not read from the data.
        val expected = listOf(
            "snes" to "Super Nintendo", "gba" to "Game Boy Advance", "psx" to "PlayStation", "psp" to "PSP",
            "nds" to "Nintendo DS", "gc" to "GameCube", "megadrive" to "Mega Drive", "nes" to "NES",
            "gb" to "Game Boy", "gbc" to "Game Boy Color", "n64" to "Nintendo 64", "ps2" to "PlayStation 2",
            "arcade" to "Arcade",
        )
        val catalog = loadShipped()
        expected.forEachIndexed { index, (id, displayName) ->
            val system = catalog.system(id)!!
            assertEquals("displayName of $id", displayName, system.displayName)
            assertEquals("order of $id", index, system.order)
        }
        val rest = catalog.systems.filter { it.id !in expected.map { pair -> pair.first } }
        assertTrue(rest.all { it.order >= expected.size })
        assertEquals(rest.map { it.displayName.lowercase() }.sorted(), rest.sortedBy { it.order }.map { it.displayName.lowercase() })
    }

    @Test
    fun displayNamesAndOrdersAreUniqueAndShortEnoughForTheColumn() {
        val systems = loadShipped().systems
        assertEquals(systems.size, systems.map { it.displayName }.toSet().size)
        assertEquals(systems.size, systems.map { it.order }.toSet().size)
        for (system in systems) assertTrue("${system.id}: '${system.displayName}'", system.displayName.length <= 22)
    }

    @Test
    fun switchEmulatorsAreLegalRiskAndNeverADefaultCandidate() {
        val catalog = loadShipped()
        val switchEmulators = catalog.emulatorsOf(catalog.system("switch")!!)
        assertTrue(switchEmulators.isNotEmpty())
        assertTrue(switchEmulators.all { it.legalRisk })
        assertTrue(catalog.defaultCandidates("switch").isEmpty())
        assertTrue(catalog.emulators.filter { it.legalRisk }.all { it.id in switchEmulators.map { e -> e.id } })
        assertTrue(catalog.defaultCandidates("snes").isNotEmpty())
    }

    @Test
    fun standaloneEmulatorsComeBeforeRetroArchWhereNoFirstChoiceSaysOtherwise() {
        // 3DS games opened in RetroArch with Azahar installed. Hand-written firsts.
        val firsts = mapOf("n3ds" to "azahar", "wii" to "dolphin", "dreamcast" to "flycast", "n64" to "m64plus-fz", "nds" to "melonds")
        val catalog = loadShipped()
        for ((systemId, first) in firsts) {
            val emulators = catalog.emulatorsOf(catalog.system(systemId)!!)
            assertEquals("first emulator of $systemId", first, emulators.first().id)
            val firstCore = emulators.indexOfFirst { it.core != null }
            val lastStandalone = emulators.indexOfLast { it.core == null }
            assertTrue("$systemId: a RetroArch core before a standalone: ${emulators.map { it.id }}", firstCore > lastStandalone)
        }
    }

    @Test
    fun aBuildUnderAnotherAppsPackageNameAlwaysNamesItsOwnActivity() {
        // ES-DE lists emulator builds that install under a game's or a benchmark's package name (to get
        // the GPU driver's game profile). Detection tells them from the real app only by the activity.
        val borrowed = setOf(
            "com.miHoYo.Yuanshen", "com.miHoYo.Yuanshen.nightly", "com.antutu.ABenchMark", "com.antutu.benchmark.full",
            "com.tencent.ig", "com.ludashi.aibench", "com.ludashi.benchmark",
        )
        val builds = loadShipped().emulators.flatMap { emulator -> emulator.allPackages.map { emulator to it } }
        val onBorrowedNames = builds.filter { (_, build) -> build.packageName in borrowed }
        assertTrue("expected some, the source lists them", onBorrowedNames.isNotEmpty())
        for ((emulator, build) in onBorrowedNames) {
            val activity = build.activity
            assertNotNull("${emulator.id} ${build.packageName} has no activity", activity)
            assertTrue("${emulator.id}: $activity", !activity!!.startsWith(build.packageName))
        }
    }

    @Test
    fun switchListsNoRetroArchCore() {
        val catalog = loadShipped()
        val switchEmulators = catalog.emulatorsOf(catalog.system("switch")!!)
        assertTrue(switchEmulators.none { it.core != null })
        assertTrue(switchEmulators.flatMap { it.allPackages }.none { it.packageName.startsWith("com.retroarch") })
    }

    @Test
    fun psVitaShipsWithVita3kAndItsFolderNames() {
        // An AYN Thor has Vita3K. Hand-written from ES-DE's es_systems.xml and es_find_rules.xml.
        val catalog = loadShipped()
        val psvita = assertNotNull_(catalog.system("psvita"), "psvita")
        assertEquals(listOf("psvita"), psvita.extensions)
        assertTrue(psvita.folders.containsAll(listOf("psvita", "vita", "ps vita", "playstation vita")))
        val vita3k = catalog.emulatorsOf(psvita).first()
        assertEquals("vita3k", vita3k.id)
        assertEquals(PackageVariant("org.vita3k.emulator", "org.vita3k.emulator.Emulator", null), vita3k.primary)
        assertEquals(GamePass(PassMode.VitaTitleId, GameTarget.ExtraArray, "AppStartParameters", listOf("-r")), vita3k.game)
        assertTrue(vita3k.extras.isEmpty() && vita3k.action == null && vita3k.flags.isEmpty())
        // Only the PS Vita emulators take a title ID.
        assertEquals(
            setOf("vita3k", "emucorev"),
            catalog.emulators.filter { it.game.mode == PassMode.VitaTitleId }.map { it.id }.toSet(),
        )
    }

    private fun <T : Any> assertNotNull_(value: T?, what: String): T {
        assertNotNull("missing $what", value)
        return value!!
    }

    @Test
    fun androidGamesIsAnInstalledAppsSystemWithNoFilesOrEmulators() {
        val catalog = loadShipped()
        val android = assertNotNull_(catalog.system("android"), "android")
        assertEquals("Android games", android.displayName)
        assertTrue(android.fromInstalledApps)
        assertTrue(android.folders.isEmpty() && android.extensions.isEmpty() && android.emulatorIds.isEmpty())
        assertEquals(1, catalog.systems.count { it.code == android.code })
        assertEquals(CoverShape.Portrait, android.cover)
        assertEquals(1.0, android.coverRatio, 0.0)
        // Only the apps system may be empty; every other one still has files and an emulator.
        for (system in catalog.systems.filter { it.id != "android" }) {
            assertTrue(system.id, !system.fromInstalledApps && system.folders.isNotEmpty() && system.emulatorIds.isNotEmpty())
        }
    }

    @Test
    fun knownAppsPointAtSystemsThatExist() {
        val catalog = loadShipped()
        assertEquals("ports", catalog.knownApps["com.opentouchgaming.deltatouch"])
        for ((packageName, systemId) in catalog.knownApps) {
            assertNotNull_(catalog.system(systemId), "$packageName -> $systemId")
        }
    }
}
