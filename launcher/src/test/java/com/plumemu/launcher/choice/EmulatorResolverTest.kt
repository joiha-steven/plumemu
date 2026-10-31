package com.plumemu.launcher.choice

import com.plumemu.launcher.TestCatalogs
import com.plumemu.platforms.PackageVariant
import com.plumemu.platforms.PlatformLoadResult
import com.plumemu.platforms.PlatformLoader
import org.junit.Assert.assertEquals
import org.junit.Test

class EmulatorResolverTest {
    private val catalog = TestCatalogs.small
    private val snes = checkNotNull(catalog.system("snes"))

    private fun installed(vararg packages: String): (PackageVariant) -> Boolean = { it.packageName in packages }

    private fun resolve(game: String? = null, system: String? = null, vararg packages: String) =
        EmulatorResolver.resolve(catalog, snes, game, system, installed(*packages))

    private fun ready(id: String, packageName: String, source: ChoiceSource, core: String? = null) =
        EmulatorPick.Ready(checkNotNull(catalog.emulator(id)), packageName, source, core)

    private fun emulators(vararg ids: String) = ids.map { checkNotNull(catalog.emulator(it)) }

    @Test
    fun withNothingChosenTheInstalledOnesAreOfferedTheRecommendedFirstAndNeverLegalRisk() {
        // com.risky is first in the list and installed, but a legal-risk emulator is never the recommendation.
        assertEquals(
            EmulatorPick.NotChosen("snes", emulators("ra-snes9x", "risky", "ra-bsnes", "snes9x"), catalog.emulator("ra-snes9x")),
            resolve(null, null, "com.risky", "com.ra", "com.snes9x"),
        )
        assertEquals(
            EmulatorPick.NotChosen("snes", emulators("snes9x", "risky"), catalog.emulator("snes9x")),
            resolve(null, null, "com.risky", "com.snes9x"),
        )
    }

    @Test
    fun theThirtyTwoBitBuildCountsAsInstalled() {
        assertEquals(
            EmulatorPick.NotChosen("snes", emulators("ra-snes9x", "ra-bsnes"), catalog.emulator("ra-snes9x")),
            resolve(null, null, "com.ra32"),
        )
        assertEquals(
            ready("ra-snes9x", "com.ra32", ChoiceSource.PersonForSystem, "snes9x_libretro_android.so"),
            resolve(null, "ra-snes9x", "com.ra32"),
        )
    }

    @Test
    fun onlyALegalRiskEmulatorInstalledIsOfferedWithoutARecommendation() {
        assertEquals(EmulatorPick.NotChosen("snes", emulators("risky"), null), resolve(null, null, "com.risky"))
    }

    /**
     * The system's choice cannot open this file and the only installed one that can is legal-risk: nothing
     * may be recommended, so the person is asked (as with no choice), never handed the legal-risk one.
     */
    @Test
    fun aSystemChoiceThatCannotOpenTheFileWithOnlyALegalRiskOneThatCanAsks() {
        val json =
            """
            {"formatVersion":2,
             "systems":[{"id":"snes","name":"Super Nintendo","shortName":"SNES","displayName":"Super Nintendo","order":0,
               "folders":["snes"],"extensions":["sfc","smc"],"cover":"landscape","tier":"tested","emulators":["risky","snes9x"]}],
             "emulators":[
              {"id":"risky","name":"Risky","package":"com.risky","activity":"com.risky.Main",
               "game":{"mode":"uri","target":"data"},"legalRisk":true},
              {"id":"snes9x","name":"Snes9x EX+","package":"com.snes9x","activity":"com.snes9x.Main","accepts":["sfc"],
               "game":{"mode":"uri","target":"extra","key":"uri"},"action":"android.intent.action.VIEW"}]}
            """.trimIndent()
        val narrow = (PlatformLoader.load(json, "narrow") as PlatformLoadResult.Loaded).catalog
        val system = checkNotNull(narrow.system("snes"))
        val pick = EmulatorResolver.resolve(narrow, system, null, "snes9x", installed("com.risky", "com.snes9x"), "Game.smc")
        assertEquals(EmulatorPick.NotChosen("snes", listOf(checkNotNull(narrow.emulator("risky"))), null), pick)
    }

    @Test
    fun nothingInstalledOffersTheCandidatesWithoutLegalRisk() {
        assertEquals(EmulatorPick.NoneInstalled("snes", emulators("ra-snes9x", "ra-bsnes", "snes9x")), resolve(null, null))
    }

    @Test
    fun aSwitchGameNeverResolvesToRetroArchWhateverIsStoredOrInstalled() {
        val shipped = TestCatalogs.shipped
        val switch = checkNotNull(shipped.system("switch"))
        val retroArch = setOf("com.retroarch", "com.retroarch.aarch64", "com.retroarch.ra32")
        val all: (PackageVariant) -> Boolean = { it.packageName in retroArch || it.packageName == "dev.eden.eden_emulator" }
        val nothingChosen = EmulatorResolver.resolve(shipped, switch, null, null, all) as EmulatorPick.NotChosen
        assertEquals(listOf("eden"), nothingChosen.installed.map { it.id })
        assertEquals(null, nothingChosen.recommended)
        // A RetroArch id stored by mistake is not listed for Switch: never started. For one game it is
        // passed over (the game follows its system, here: the question); for the system, reported.
        assertEquals(nothingChosen, EmulatorResolver.resolve(shipped, switch, "retroarch-snes9x", null, all))
        assertEquals(
            EmulatorPick.ChosenMissing(null, "retroarch-snes9x", ChoiceSource.PersonForSystem),
            EmulatorResolver.resolve(shipped, switch, null, "retroarch-snes9x", all),
        )
        val onlyRetroArch: (PackageVariant) -> Boolean = { it.packageName in retroArch }
        assertEquals(EmulatorPick.NoneInstalled("switch", emptyList()), EmulatorResolver.resolve(shipped, switch, null, null, onlyRetroArch))
    }

    @Test
    fun thePersonsSystemChoiceBeatsTheDefaultAndMayBeLegalRisk() {
        assertEquals(ready("risky", "com.risky", ChoiceSource.PersonForSystem), resolve(null, "risky", "com.risky", "com.ra"))
    }

    @Test
    fun thePersonsGameChoiceBeatsTheSystemChoice() {
        assertEquals(
            ready("snes9x", "com.snes9x", ChoiceSource.PersonForGame),
            resolve("snes9x", "risky", "com.risky", "com.snes9x"),
        )
    }

    @Test
    fun aChosenRetroArchCoreOtherThanTheFirstIsHonoured() {
        assertEquals(
            ready("ra-bsnes", "com.ra", ChoiceSource.PersonForGame, "bsnes_libretro_android.so"),
            resolve("ra-bsnes", null, "com.ra"),
        )
        assertEquals(
            ready("ra-bsnes", "com.ra32", ChoiceSource.PersonForSystem, "bsnes_libretro_android.so"),
            resolve(null, "ra-bsnes", "com.ra32"),
        )
    }

    @Test
    fun anUninstalledChoiceIsReportedNotSwitched() {
        val snes9x = checkNotNull(catalog.emulator("snes9x"))
        assertEquals(EmulatorPick.ChosenMissing(snes9x, "snes9x", ChoiceSource.PersonForGame), resolve("snes9x", null, "com.ra"))
        assertEquals(EmulatorPick.ChosenMissing(snes9x, "snes9x", ChoiceSource.PersonForSystem), resolve(null, "snes9x", "com.ra"))
    }

    @Test
    fun aChoiceTheDataNoLongerListsIsReportedToo() {
        assertEquals(EmulatorPick.ChosenMissing(null, "gone", ChoiceSource.PersonForSystem), resolve(null, "gone", "com.ra"))
        // A game's choice the system does not list (pathemu exists, but not for snes; or the file moved
        // to another system) is passed over: the game follows its system.
        assertEquals(resolve(null, null, "com.path.emu"), resolve("pathemu", null, "com.path.emu"))
        assertEquals(EmulatorPick.ChosenMissing(null, "gone", ChoiceSource.PersonForSystem), resolve("pathemu", "gone", "com.path.emu"))
    }

    @Test
    fun aSystemTheDataDoesNotKnowHasNoCandidates() {
        val shipped = TestCatalogs.shipped
        assertEquals(emptyList<Any>(), shipped.defaultCandidates("nosuchsystem"))
    }
}
