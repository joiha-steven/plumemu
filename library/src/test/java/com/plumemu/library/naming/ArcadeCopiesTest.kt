package com.plumemu.library.naming

import com.plumemu.identify.arcade.ArcadeLookup
import com.plumemu.coredata.db.ArcadeShownGame
import com.plumemu.identify.arcade.ArcadeSystems
import com.plumemu.library.catalog.RegionPreference
import com.plumemu.library.corpus.RealData
import com.plumemu.library.scan.GameNaming
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The arcade grouping rule ([ArcadeCopies]) and the clash rule after it ([ArcadeTitleClashes]) on
 * real MAME descriptions, read from the arcade pack the app ships (no file is opened: sets are
 * named by short name).
 */
class ArcadeCopiesTest {
    private fun set(shortName: String): ArcadeCopies.ArcadeSet {
        val found = checkNotNull(lookup.find(shortName)) { "$shortName is not in the shipped arcade pack" }
        val parent = found.parent?.let { lookup.find(it) }
        return ArcadeCopies.ArcadeSet(found.shortName, found.title, found.parent, parent?.title)
    }

    private var nextId = 1L

    private fun copy(
        shortName: String,
        hidden: Boolean = false,
        marked: Boolean = false,
        directory: String = "arcade",
        system: String = "arcade",
    ) = ArcadeCopies.Copy(nextId++, system, directory, "$shortName.zip", set(shortName), hidden, marked)

    /** The file names each shown row stands for, as sets of file names, and which one shows. */
    private fun rows(vararg copies: ArcadeCopies.Copy): Map<String, Set<String>> {
        val shown = ArcadeCopies.shownCopies(copies.toList(), RegionPreference.DEFAULT)
        assertEquals("every copy gets an answer", copies.map { it.id }.toSet(), shown.keys)
        val byGroup = copies.groupBy { Triple(it.systemId, it.directory, it.set?.let(ArcadeCopies::groupKey) ?: "id${it.id}") }
        return byGroup.values.associate { group ->
            val primary = group.filter { shown.getValue(it.id) }
            assertEquals("one shown copy per group: ${group.map { it.fileName }}", 1, primary.size)
            primary.single().fileName to group.mapTo(sortedSetOf()) { it.fileName }
        }
    }

    @Test
    fun descriptionsAreTheShippedOnes() {
        assertEquals("Cadillacs and Dinosaurs (World 930201)", set("dino").description)
        assertEquals("Cadillacs and Dinosaurs (USA 930201)", set("dinou").description)
        assertEquals("dino", set("dinou").parentShortName)
        assertEquals("Final Fight (USA, set 1)", set("ffightu").description)
        assertEquals("Final Fight (Japan)", set("ffightj").description)
        assertEquals("Street Smart / Final Fight (Japan, hack)", set("ffightjh").description)
    }

    @Test
    fun aParentAndItsRegionCloneAreOneRowAndTheUsaCopyShowsWithoutMarks() {
        assertEquals(mapOf("dinou.zip" to setOf("dino.zip", "dinou.zip")), rows(copy("dino"), copy("dinou")))
    }

    @Test
    fun theFavoriteCopyShows() {
        assertEquals(mapOf("dino.zip" to setOf("dino.zip", "dinou.zip")), rows(copy("dino", marked = true), copy("dinou")))
    }

    @Test
    fun aCloneWithAnotherShortTitleIsItsOwnRow() {
        // dinoj is "Cadillacs: Kyouryuu Shin Seiki (Japan 930201)".
        assertEquals(
            mapOf("dinou.zip" to setOf("dino.zip", "dinou.zip"), "dinoj.zip" to setOf("dinoj.zip")),
            rows(copy("dino"), copy("dinou"), copy("dinoj")),
        )
    }

    @Test
    fun clonesOfOneParentAreOneRowWithoutTheParentFile() {
        assertEquals(mapOf("ffightu.zip" to setOf("ffightj.zip", "ffightu.zip")), rows(copy("ffightu"), copy("ffightj")))
    }

    @Test
    fun aHackIsItsOwnRow() {
        assertEquals(
            mapOf("ffightu.zip" to setOf("ffight.zip", "ffightu.zip"), "ffightjh.zip" to setOf("ffightjh.zip")),
            rows(copy("ffight"), copy("ffightu"), copy("ffightjh")),
        )
    }

    @Test
    fun aCloneWithAnotherTitleOrABootlegIsItsOwnRow() {
        assertEquals(mapOf("pang.zip" to setOf("pang.zip"), "bbros.zip" to setOf("bbros.zip")), rows(copy("pang"), copy("bbros")))
        assertEquals(mapOf("nemesis.zip" to setOf("nemesis.zip"), "gradius.zip" to setOf("gradius.zip")), rows(copy("nemesis"), copy("gradius")))
        assertEquals(mapOf("outrun.zip" to setOf("outrun.zip"), "outrunb.zip" to setOf("outrunb.zip")), rows(copy("outrun"), copy("outrunb")))
    }

    @Test
    fun aSetThePackDoesNotKnowOrWhoseParentItDoesNotKnowIsItsOwnRow() {
        val unknown = ArcadeCopies.Copy(nextId++, "arcade", "arcade", "notaset.zip", null, isHidden = false, isMarked = false)
        val orphan = ArcadeCopies.Copy(
            nextId++, "arcade", "arcade", "dinox.zip",
            ArcadeCopies.ArcadeSet("dinox", "Cadillacs and Dinosaurs (Asia 930201)", "notaparent", null),
            isHidden = false, isMarked = false,
        )
        val shown = ArcadeCopies.shownCopies(listOf(copy("dino"), copy("dinou"), unknown, orphan), RegionPreference.DEFAULT)
        assertEquals(true, shown[unknown.id])
        assertEquals(true, shown[orphan.id])
        assertEquals("dinox", ArcadeCopies.groupKey(orphan.set!!))
    }

    @Test
    fun copiesInAnotherDirectoryOrSystemAreAnotherRow() {
        assertEquals(2, rows(copy("dino"), copy("dinou", directory = "arcade/more")).size)
        assertEquals(2, rows(copy("dino"), copy("dinou", system = "fbneo")).size)
    }

    @Test
    fun aHiddenCopyDoesNotShowWhileAnotherIsNotHidden() {
        assertEquals(mapOf("dino.zip" to setOf("dino.zip", "dinou.zip")), rows(copy("dino"), copy("dinou", hidden = true, marked = true)))
    }

    @Test
    fun everyCopyHiddenShowsAHiddenCopySoTheGameLeavesTheLists() {
        val dino = copy("dino", hidden = true)
        val dinou = copy("dinou", hidden = true)
        val shown = ArcadeCopies.shownCopies(listOf(dino, dinou), RegionPreference.DEFAULT)
        assertEquals(mapOf(dino.id to false, dinou.id to true), shown)
    }

    @Test
    fun aCopyWithAPersonsNameFixShows() {
        // The pass marks a row whose name is the person's fix (PERSON_FIX) like a favourite.
        assertEquals(mapOf("dino.zip" to setOf("dino.zip", "dinou.zip")), rows(copy("dino", marked = true), copy("dinou")))
    }

    @Test
    fun theParentShowsBeforeACloneOfTheSameRegionThenTheShorterFileName() {
        // Made-up sets: same region, the clone's file name is shorter.
        fun made(id: Long, file: String, set: ArcadeCopies.ArcadeSet) = ArcadeCopies.Copy(id, "arcade", "arcade", file, set, isHidden = false, isMarked = false)
        val parent = made(1, "zorkfght.zip", ArcadeCopies.ArcadeSet("zorkfght", "Zork Fighters (World, set 1)", null, null))
        val clone = made(2, "zf2.zip", ArcadeCopies.ArcadeSet("zf2", "Zork Fighters (World, set 2)", "zorkfght", "Zork Fighters (World, set 1)"))
        val copy = made(3, "zf2.7z", ArcadeCopies.ArcadeSet("zf2", "Zork Fighters (World, set 2)", "zorkfght", "Zork Fighters (World, set 1)"))
        assertEquals(mapOf(1L to true, 2L to false, 3L to false), ArcadeCopies.shownCopies(listOf(clone, parent, copy), RegionPreference.DEFAULT))
        assertEquals("the shorter file name", mapOf(2L to false, 3L to true), ArcadeCopies.shownCopies(listOf(clone, copy), RegionPreference.DEFAULT))
    }

    @Test
    fun aBootlegSharingTheTitleShowsItsDescriptionAndTheOriginalKeepsTheShortTitle() {
        val outrun = set("outrun")
        val outrunb = set("outrunb")
        val games = listOf(
            ArcadeShownGame(1, "arcade", "outrun.zip", "Out Run"),
            ArcadeShownGame(2, "arcade", "outrunb.zip", "Out Run"),
        )
        val full = mapOf(1L to outrun.description, 2L to outrunb.description)
        assertEquals(mapOf(2L to outrunb.description), ArcadeTitleClashes.changes(games) { full[it.id] })
    }

    @Test
    fun copiesTheListsLeaveOutShowTheShortTitleAndDoNotClash() {
        val games = listOf(
            ArcadeShownGame(1, "arcade", "dinou.zip", "Cadillacs and Dinosaurs (USA 930201)", isPrimaryCopy = true),
            ArcadeShownGame(2, "arcade", "dino.zip", "Cadillacs and Dinosaurs (World 930201)", isPrimaryCopy = false),
        )
        assertEquals(
            mapOf(1L to "Cadillacs and Dinosaurs", 2L to "Cadillacs and Dinosaurs"),
            ArcadeTitleClashes.changes(games) { error("a lone shown row needs no description") },
        )
    }

    @Test
    fun arcadeGameOfNamesTheGroupStatisticsCountsBy() {
        assertEquals("dino", arcadeGameOf(naming, "arcade", "dinou.zip"))
        assertEquals("dino", arcadeGameOf(naming, "fbneo", "dino.zip"))
        assertEquals("another short title", "dinoj", arcadeGameOf(naming, "arcade", "dinoj.zip"))
        assertEquals("a bootleg", "outrunb", arcadeGameOf(naming, "arcade", "outrunb.zip"))
        assertEquals("not an arcade system", null, arcadeGameOf(naming, "snes", "dino.zip"))
        assertEquals("not in the pack", null, arcadeGameOf(naming, "arcade", "notaset.zip"))
    }

    @Test
    fun arcadeClonesOfListsEachParentsClonesAsTheirOwnParentLinkSays() {
        val clones = checkNotNull(arcadeClonesOf(naming, setOf("dino", "ffight", "notaset")))
        assertEquals("only parents the pack knows", setOf("dino", "ffight"), clones.keys)
        assertTrue("dinou" in clones.getValue("dino") && "dinoj" in clones.getValue("dino"))
        assertTrue("ffightu" in clones.getValue("ffight") && "ffightj" in clones.getValue("ffight"))
        assertFalse("a parent is not its own clone", "dino" in clones.getValue("dino"))
        for ((parent, sets) in clones) {
            for (set in sets) assertEquals(set, parent, arcadeParentOf(naming, "arcade", "$set.zip"))
        }
        assertEquals(emptyMap<String, List<String>>(), arcadeClonesOf(naming, emptySet()))
    }

    companion object {
        private lateinit var packs: File
        private lateinit var lookup: ArcadeLookup
        private lateinit var naming: GameNaming

        @BeforeClass
        @JvmStatic
        fun openPack() {
            packs = Files.createTempDirectory("arcade-copies").toFile()
            naming = RealData.naming(packs).naming
            val pack = naming.packs.packFor(ArcadeSystems.PACK_ID)
            lookup = ArcadeLookup(checkNotNull(pack) { "the shipped arcade pack did not open" })
        }

        @AfterClass
        @JvmStatic
        fun deletePack() {
            packs.deleteRecursively()
        }
    }
}
