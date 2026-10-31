package com.plumemu.home

import androidx.test.core.app.ApplicationProvider
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.identify.name.VariantTag
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Two rows of one system with one title get the words that tell them apart; a unique title gets none. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class RowVariantsTest {
    private fun row(id: String, title: String, file: String, system: String = "gba") = GameRowModel(id, title, "USA", systemId = system, fileName = file)

    @Test fun uniqueTitlesAreLeftAsTheyAre() {
        val rows = listOf(row("1", "Pokemon Emerald", "Pokemon Emerald (USA).gba"), row("2", "Metroid Fusion", "Metroid Fusion (USA) (Rev 1).gba"))
        assertSame(rows, withVariantTags(rows))
    }

    @Test fun collidingTitlesGetTheirTags() {
        val rows = withVariantTags(
            listOf(row("1", "Pokemon Emerald", "Pokemon Emerald (USA).gba"), row("2", "Pokemon Emerald", "Pokemon Emerald (USA) (Rev 1).gba"), row("3", "Metroid", "Metroid (USA).gba")),
        )
        assertEquals(emptyList<VariantTag>(), rows[0].variantTags)
        assertEquals(listOf(VariantTag.Revision("1")), rows[1].variantTags)
        assertEquals(emptyList<VariantTag>(), rows[2].variantTags)
    }

    @Test fun copiesThatDifferOnlyByRegionCarryTheirRegion() {
        fun regional(id: String, region: String, file: String) = GameRowModel(id, "Crash of the Titans", region, systemId = "ps2", fileName = file)
        val rows = withVariantTags(listOf(regional("1", "USA", "Crash of the Titans (USA).iso"), regional("2", "EUR", "Crash of the Titans (Europe).iso")))
        assertEquals(listOf("USA", "EUR"), rows.map { it.variantRegion })
        assertEquals(listOf("\u00B7 USA", "\u00B7 EUR"), rows.map { variantSubtitle(resources, it.variantTags, it.variantRegion) })
    }

    @Test fun aRegionIsAddedOnlyToRowsWhoseTagsCollide() {
        fun regional(id: String, region: String, file: String) = GameRowModel(id, "Game", region, systemId = "gba", fileName = file)
        val rows = withVariantTags(
            listOf(regional("1", "USA", "Game (USA).gba"), regional("2", "EUR", "Game (Europe).gba"), regional("3", "USA", "Game (USA) (Rev 1).gba")),
        )
        assertEquals(listOf("USA", "EUR", ""), rows.map { it.variantRegion })
        assertEquals("\u00B7 Rev 1", variantSubtitle(resources, rows[2].variantTags, rows[2].variantRegion))
    }

    @Test fun aCollidingRowWithNoRegionIsLeftAsItIs() {
        val rows = withVariantTags(listOf(GameRowModel("1", "G", "", systemId = "gba", fileName = "G.gba"), GameRowModel("2", "G", "", systemId = "gba", fileName = "G.gba")))
        assertEquals(listOf("", ""), rows.map { it.variantRegion })
    }

    @Test fun theSameTitleInAnotherSystemIsNotACollision() {
        val rows = listOf(row("1", "Tetris", "Tetris (World) (Rev 1).gb", "gb"), row("2", "Tetris", "Tetris (USA).nes", "nes"))
        assertSame(rows, withVariantTags(rows))
    }

    @Test fun anUnknownTagOnACollidingRowGivesNoTag() {
        val rows = withVariantTags(listOf(row("1", "Game", "Game (USA).gba"), row("2", "Game", "Game (USA) (Collector's Edition).gba")))
        assertEquals(emptyList<VariantTag>(), rows[1].variantTags)
    }

    @Test fun rowsWithoutAFileNameAreNeverTagged() {
        val rows = listOf(GameRowModel("a", "Browser", ""), GameRowModel("b", "Browser", ""))
        assertSame(rows, withVariantTags(rows))
    }

    private val resources get() = ApplicationProvider.getApplicationContext<android.content.Context>().resources

    @Test fun labelsArePlainWords() {
        val en = Locale.ENGLISH
        fun label(vararg tags: VariantTag) = variantLabel(resources, tags.toList(), en)
        assertEquals("Rev 1", label(VariantTag.Revision("1")))
        assertEquals("Beta", label(VariantTag.Beta))
        assertEquals("Prototype", label(VariantTag.Prototype))
        assertEquals("Demo", label(VariantTag.Demo))
        assertEquals("Hack", label(VariantTag.Hack))
        assertEquals("Unlicensed", label(VariantTag.Unlicensed))
        assertEquals("Pirate", label(VariantTag.Pirate))
        assertEquals("English translation", label(VariantTag.Translation("en")))
        assertEquals("Translation", label(VariantTag.Translation(null)))
        assertEquals("Rev 1, Hack", label(VariantTag.Revision("1"), VariantTag.Hack))
        assertEquals("", label())
    }

    @Test fun languageNamesAreLowerCaseWhereTheSentenceNeedsIt() {
        assertEquals("ti\u1EBFng Anh", languageName("en", Locale.forLanguageTag("vi")))
        assertEquals("ingl\u00E9s", languageName("en", Locale.forLanguageTag("es")))
        assertEquals("anglais", languageName("en", Locale.forLanguageTag("fr")))
        assertEquals("English", languageName("en", Locale.ENGLISH))
    }
}
