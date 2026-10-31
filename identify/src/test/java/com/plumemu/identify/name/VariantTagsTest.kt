package com.plumemu.identify.name

import org.junit.Assert.assertEquals
import org.junit.Test

class VariantTagsTest {
    private fun tags(name: String) = VariantTags.of(name)

    @Test fun aPlainNameHasNoTag() = assertEquals(emptyList<VariantTag>(), tags("Pokemon Emerald (USA)"))

    @Test fun aRevisionKeepsItsNumberOrLetter() {
        assertEquals(listOf(VariantTag.Revision("1")), tags("Pokemon Emerald (USA) (Rev 1)"))
        assertEquals(listOf(VariantTag.Revision("A")), tags("Game (Europe) (Rev A)"))
    }

    @Test fun aWordThatOnlyStartsWithRevIsNotARevision() {
        assertEquals(emptyList<VariantTag>(), tags("Game (Reverse)"))
        assertEquals(emptyList<VariantTag>(), tags("Game (Revenge)"))
        assertEquals(listOf(VariantTag.Revision("2")), tags("Game (Rev2)"))
        assertEquals(listOf(VariantTag.Revision("A")), tags("Game (Rev. A)"))
    }

    @Test fun betaPrototypeAndDemo() {
        assertEquals(listOf(VariantTag.Beta), tags("Game (Beta)"))
        assertEquals(listOf(VariantTag.Beta), tags("Game (USA) (Beta 2)"))
        assertEquals(listOf(VariantTag.Prototype), tags("Game (Proto)"))
        assertEquals(listOf(VariantTag.Prototype), tags("Game (Prototype)"))
        assertEquals(listOf(VariantTag.Demo), tags("Game (Demo)"))
    }

    @Test fun hacksInEveryWritingThatIsKnown() {
        assertEquals(listOf(VariantTag.Hack), tags("Game [Hack]"))
        assertEquals(listOf(VariantTag.Hack), tags("Game (Hack)"))
        assertEquals(listOf(VariantTag.Hack), tags("Game (U) [h1C]"))
    }

    @Test fun aTranslationNamesItsLanguage() {
        assertEquals(listOf(VariantTag.Translation("en")), tags("Mother 3 (Japan) [T+Eng]"))
        assertEquals(listOf(VariantTag.Translation("es")), tags("Mother 3 (Japan) [T-Spa]"))
        assertEquals(listOf(VariantTag.Translation("en")), tags("Mother 3 (Japan) [T+Eng v1.1]"))
    }

    @Test fun aTranslationOfAnUnknownLanguageHasNoLanguage() {
        assertEquals(listOf(VariantTag.Translation(null)), tags("Game [T+Xyz]"))
        assertEquals(listOf(VariantTag.Translation(null)), tags("Game (Translated)"))
    }

    @Test fun unlicensedAndPirate() {
        assertEquals(listOf(VariantTag.Unlicensed), tags("Game (USA) (Unl)"))
        assertEquals(listOf(VariantTag.Pirate), tags("Game (Pirate)"))
        assertEquals(listOf(VariantTag.Pirate), tags("Game [p1]"))
    }

    @Test fun severalTagsKeepTheirOrderAndRepeatsCountOnce() {
        assertEquals(listOf(VariantTag.Revision("1"), VariantTag.Hack), tags("Game (USA) (Rev 1) [Hack]"))
        assertEquals(listOf(VariantTag.Beta), tags("Game (Beta) [Beta]"))
    }

    @Test fun anUnknownTagGivesNoLabel() {
        assertEquals(emptyList<VariantTag>(), tags("Game (USA) (Collector's Edition)"))
        assertEquals(emptyList<VariantTag>(), tags("Game (USA) [t1]"))
    }

    @Test fun regionLanguageDiscVersionAndDumpMarksAreNotVariants() {
        assertEquals(emptyList<VariantTag>(), tags("Game (USA, Europe) (En,Fr) (Disc 1) (v1.1) [!] [b1]"))
    }
}
