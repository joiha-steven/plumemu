package com.plumemu.library.catalog

import com.plumemu.identify.name.Region
import com.plumemu.library.TestRules
import com.plumemu.library.matching.SystemMatcher
import com.plumemu.library.scan.DocumentEntry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Switch base, update and DLC grouped by the title id in their names, through the
 * cataloguer. Made-up titles; the ids follow the Switch rule (base low 13 bits zero, update
 * base + 0x800, DLC base + 0x1000 + n) but name no real game.
 */
class SwitchGroupingTest {
    private val matcher = SystemMatcher(TestRules.ALL + TestRules.SWITCH)
    private val cataloguer = DirectoryCataloguer(matcher)

    private fun files(vararg names: String) = names.map { DocumentEntry("id:$it", it, 1, 1, isDirectory = false) }

    private fun switchFolder(vararg names: String) = cataloguer.catalogue(matcher.matchFolder("switch"), files(*names))

    private fun DirectoryCatalogue.shown() = games.filter { it.isPrimaryCopy }.map { it.entry.name }

    /** The file each of [names] is shown under, following the map to a file without an entry. */
    private fun shownUnder(folder: String, directory: String?, vararg names: String): Map<String, String> {
        val under = HashMap<DocumentEntry, DocumentEntry>()
        cataloguer.catalogue(matcher.matchPath("ROMs", folder), files(*names), directory, under)
        return names.associateWith { name ->
            var at = files(name).single()
            while (true) at = under[at] ?: break
            at.name
        }
    }

    @Test
    fun updateWithOnlyItsIdIsPartOfTheNamedBase() {
        val result = switchFolder("Star Rover [0100AAAA00010000].nsp", "0100AAAA00010800.nsp")
        assertEquals(listOf("Star Rover [0100AAAA00010000].nsp"), result.shown())
        assertEquals(mapOf("id:0100AAAA00010800.nsp" to "id:Star Rover [0100AAAA00010000].nsp"), result.partOf)
        val bracketed = switchFolder("Star Rover [0100AAAA00010000].nsp", "[0100AAAA00010800][v131072].nsp")
        assertEquals(listOf("Star Rover [0100AAAA00010000].nsp"), bracketed.shown())
    }

    @Test
    fun updateAndDlcWithTheirOwnNamesArePartOfTheBase() {
        val result = switchFolder(
            "Star Rover [0100AAAA00010000].nsp",
            "Star Rover Update 1.3.0 [0100AAAA00010800].nsp",
            "Star Rover Expansion Pass - The Ice Isle [0100AAAA00011001][DLC].nsp",
            "Star Rover Costume Set [0100AAAA00011002][DLC].nsp",
        )
        assertEquals(listOf("Star Rover [0100AAAA00010000].nsp"), result.shown())
        assertEquals(1, result.games.size)
        assertEquals(1, result.games.single().discCount)
        assertEquals(
            setOf(
                "id:Star Rover Update 1.3.0 [0100AAAA00010800].nsp",
                "id:Star Rover Expansion Pass - The Ice Isle [0100AAAA00011001][DLC].nsp",
                "id:Star Rover Costume Set [0100AAAA00011002][DLC].nsp",
            ),
            result.partOf.keys,
        )
        assertEquals(setOf("id:Star Rover [0100AAAA00010000].nsp"), result.partOf.values.toSet())
    }

    @Test
    fun theBaseRunsWhateverItsExtensionAndWhereItsNameSorts() {
        // The update's name sorts first ("(" and "0" before "["); the base is an .xci.
        val result = switchFolder(
            "0100AAAA00010800.nsp",
            "Star Rover (Update 2.1.0) [0100AAAA00010800].nsp",
            "Star Rover [0100AAAA00010000].xci",
        )
        assertEquals(listOf("Star Rover [0100AAAA00010000].xci"), result.shown())
        assertEquals("Star Rover", result.games.single().title)
    }

    @Test
    fun aBaseKeptAsXciAndNspIsOneGameOnThePreferredRegion() {
        val result = switchFolder(
            "Star Rover (Japan) [0100AAAA00010000].nsp",
            "Star Rover (USA, Europe) [0100AAAA00010000].xci",
            "0100AAAA00010800.nsp",
        )
        assertEquals(listOf("Star Rover (USA, Europe) [0100AAAA00010000].xci"), result.shown())
        assertEquals(1, result.games.size)
        assertEquals(
            mapOf(
                "id:Star Rover (Japan) [0100AAAA00010000].nsp" to "id:Star Rover (USA, Europe) [0100AAAA00010000].xci",
                "id:0100AAAA00010800.nsp" to "id:Star Rover (USA, Europe) [0100AAAA00010000].xci",
            ),
            result.partOf,
        )
        // Same name, two containers: one game, the name order decides.
        assertEquals(1, switchFolder("Moon Cart [0100BBBB00020000].xci", "Moon Cart [0100BBBB00020000].nsp").games.size)
    }

    @Test
    fun twoRegionalBasesOfOneIdShowThePersonsPreferredRegion() {
        val names = arrayOf("Star Rover (Japan) [0100AAAA00010000].nsp", "Star Rover (USA, Europe) [0100AAAA00010000].nsp")
        assertEquals(listOf("Star Rover (USA, Europe) [0100AAAA00010000].nsp"), switchFolder(*names).shown())
        val japanFirst = DirectoryCataloguer(matcher, CatalogSettings(preferredRegions = listOf(Region.JAPAN, Region.USA)))
        assertEquals(
            listOf("Star Rover (Japan) [0100AAAA00010000].nsp"),
            japanFirst.catalogue(matcher.matchFolder("switch"), files(*names)).games.map { it.entry.name },
        )
    }

    @Test
    fun updateAndDlcWithoutTheirBaseAreOneGameOnTheUpdate() {
        val result = switchFolder(
            "Star Rover Ice Isle [0100AAAA00011001][DLC].nsp",
            "Star Rover [0100AAAA00010800][v262144][UPD].nsp",
            "Star Rover Costume Set [0100AAAA00011002][DLC].nsp",
        )
        assertEquals(listOf("Star Rover [0100AAAA00010800][v262144][UPD].nsp"), result.shown())
        val dlcOnly = switchFolder("Star Rover Costume Set [0100AAAA00011002][DLC].nsp", "Star Rover Ice Isle [0100AAAA00011001][DLC].nsp")
        assertEquals(listOf("Star Rover Ice Isle [0100AAAA00011001][DLC].nsp"), dlcOnly.shown())
    }

    @Test
    fun oneFolderPerGameAndOneSharedFolderGroupAlike() {
        val names = arrayOf("Star Rover [0100AAAA00010000].xci", "0100AAAA00010800.nsp", "Star Rover Ice Isle [0100AAAA00011001][DLC].nsp")
        val own = shownUnder("switch/Star Rover", "Star Rover", *names)
        val shared = shownUnder("switch", null, *names, "Moon Cart [0100BBBB00020000].nsp", "Moon Cart Update [0100BBBB00020800].nsp")
        for (name in names) {
            assertEquals(name, "Star Rover [0100AAAA00010000].xci", own[name])
            assertEquals(name, "Star Rover [0100AAAA00010000].xci", shared[name])
        }
        assertEquals("Moon Cart [0100BBBB00020000].nsp", shared["Moon Cart Update [0100BBBB00020800].nsp"])
    }

    @Test
    fun dlcOfAnotherGameBesideABaseStaysApart() {
        val result = switchFolder("Star Rover [0100AAAA00010000].nsp", "Moon Cart Last Lap [0100BBBB00021001][DLC].nsp")
        assertEquals(listOf("Moon Cart Last Lap [0100BBBB00021001][DLC].nsp", "Star Rover [0100AAAA00010000].nsp"), result.shown())
        assertEquals(emptyMap<String, String>(), result.partOf)
    }

    @Test
    fun twoGamesOfOneFolderWithDifferentlyNamedUpdatesDoNotMix() {
        val result = switchFolder(
            "Star Rover [0100AAAA00010000].nsp",
            "Moon Cart [0100BBBB00020000].nsp",
            "0100AAAA00010800.nsp",
            "0100BBBB00020800.nsp",
        )
        assertEquals(listOf("Moon Cart [0100BBBB00020000].nsp", "Star Rover [0100AAAA00010000].nsp"), result.shown())
        assertEquals(
            mapOf(
                "id:0100AAAA00010800.nsp" to "id:Star Rover [0100AAAA00010000].nsp",
                "id:0100BBBB00020800.nsp" to "id:Moon Cart [0100BBBB00020000].nsp",
            ),
            result.partOf,
        )
    }

    @Test
    fun hexDigitsThatAreNoTitleIdGroupNothing() {
        // Same high digits, but the low 13 bits are no base, update or DLC: not a title id.
        val result = switchFolder("Alpha Kit [0123456789AB0123].nsp", "Beta Kit [0123456789AB0456].nsp")
        assertEquals(listOf("Alpha Kit [0123456789AB0123].nsp", "Beta Kit [0123456789AB0456].nsp"), result.shown())
        assertEquals(listOf("Alpha Kit", "Beta Kit"), result.games.map { it.title })
        // Inside a longer run of hex digits (an NCA content id) it is no id either.
        val nca = switchFolder("Star Rover [0100AAAA00010000].nsp", "Star Rover deadbeef0100AAAA00010800.nsp")
        assertEquals(2, nca.games.size)
        assertEquals(emptyMap<String, String>(), nca.partOf)
    }

    @Test
    fun aSwitchIdInAnotherSystemsFileGroupsNothing() {
        val result = cataloguer.catalogue(
            matcher.matchFolder("Downloads"),
            files("Star Rover [0100AAAA00010000].nsp", "Star Rover Demake [0100AAAA00010800].gba"),
        )
        assertEquals(listOf("gba", "switch"), result.games.map { it.systemId })
        assertEquals(emptyMap<String, String>(), result.partOf)
    }

    @Test
    fun switchFilesWithoutAnIdKeepTheirFormerGrouping() {
        // A bare base and its update share a title: one game by the regional-copy rule, base shown.
        val result = switchFolder("Star Rover.nsp", "Star Rover [0100AAAA00010800][v65536][UPD].nsp", "Star Rover [0100AAAA00011001][DLC].nsp")
        assertEquals(listOf("Star Rover.nsp"), result.shown())
        val under = shownUnder("switch", null, "Star Rover.nsp", "Star Rover [0100AAAA00010800][v65536][UPD].nsp", "Star Rover [0100AAAA00011001][DLC].nsp")
        assertEquals(setOf("Star Rover.nsp"), under.values.toSet())
    }
}
