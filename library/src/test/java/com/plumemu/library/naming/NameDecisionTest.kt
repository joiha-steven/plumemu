package com.plumemu.library.naming

import com.plumemu.coredata.db.NameFixEntity
import com.plumemu.coredata.db.NameFixKind
import com.plumemu.identify.Confidence
import com.plumemu.identify.Identification
import com.plumemu.identify.Method
import com.plumemu.identify.NameTrust
import com.plumemu.identify.ScrapeHints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Trust order: which name a game shows after the pass. */
class NameDecisionTest {
    private fun result(confidence: Confidence, title: String = "Zork Quest", regions: List<String> = listOf("USA"), hide: Boolean = false) =
        Identification(
            title = title,
            regions = regions,
            variant = null,
            year = null,
            publisher = null,
            confidence = confidence,
            method = when (confidence) {
                Confidence.EXACT -> Method.CRC
                Confidence.LIKELY -> Method.NAME_MATCH
                Confidence.UNKNOWN -> Method.FILE_NAME
            },
            hide = hide,
            parent = null,
            fingerprints = null,
            fixKeys = listOf("crc:00000001:4"),
            scrapeHints = ScrapeHints(null, null, null, null, "zq.sfc", null, "snes"),
        )

    private fun fix(kind: NameFixKind, title: String? = "My Zork") = NameFixEntity("crc:00000001:4", kind, title, null, null, 1)

    @Test
    fun aFixWinsOverEveryResult() {
        for (confidence in Confidence.entries) {
            for (stored in NameTrust.entries) {
                val shown = NameDecision.decide(stored.name, result(confidence), fix(NameFixKind.OWN_NAME), "")
                assertEquals("$stored + $confidence", "My Zork", shown?.title)
                assertEquals(NameTrust.PERSON_FIX, shown?.trust)
            }
        }
    }

    @Test
    fun notAGameKeepsTheNameAndHidesTheGame() {
        val shown = NameDecision.decide(NameTrust.FILE_NAME.name, result(Confidence.EXACT), fix(NameFixKind.NOT_A_GAME, null), "")!!
        assertNull(shown.title)
        assertNull(shown.sortName)
        assertTrue(shown.isNotGame)
        assertEquals(NameTrust.PERSON_FIX, shown.trust)
    }

    @Test
    fun theIdentifiersOwnEarlierAnswerIsReplaced() {
        for (stored in listOf(NameTrust.FILE_NAME, NameTrust.IDENTIFIER_LIKELY, NameTrust.IDENTIFIER_EXACT)) {
            for (confidence in Confidence.entries) {
                val shown = NameDecision.decide(stored.name, result(confidence, title = "New"), null, "")
                assertEquals("$stored + $confidence", "New", shown?.title)
                assertEquals(NameTrust.of(result(confidence)), shown?.trust)
            }
        }
    }

    @Test
    fun aPersonFixWhoseRowsAreGoneIsReplaced() {
        assertEquals("New", NameDecision.decide(NameTrust.PERSON_FIX.name, result(Confidence.LIKELY, title = "New"), null, "")?.title)
    }

    @Test
    fun aScraperNameIsReplacedOnlyByAStrongerResult() {
        assertNull(NameDecision.decide(NameTrust.SCRAPER_EXACT.name, result(Confidence.LIKELY), null, ""))
        assertNull(NameDecision.decide(NameTrust.SCRAPER_EXACT.name, result(Confidence.UNKNOWN), null, ""))
        assertEquals(NameTrust.IDENTIFIER_EXACT, NameDecision.decide(NameTrust.SCRAPER_EXACT.name, result(Confidence.EXACT), null, "")?.trust)
        assertNull(NameDecision.decide(NameTrust.SCRAPER_LIKELY.name, result(Confidence.UNKNOWN), null, ""))
        assertEquals(NameTrust.IDENTIFIER_LIKELY, NameDecision.decide(NameTrust.SCRAPER_LIKELY.name, result(Confidence.LIKELY), null, "")?.trust)
    }

    @Test
    fun aNewNamingAlwaysReplacesAPicksTitleWhichThePassOffersAgainWhileThePickIsStored() {
        for (confidence in Confidence.entries) {
            val shown = NameDecision.decide(NameTrust.SCRAPER_PERSON_CHOICE.name, result(confidence, title = "From the file"), null, "")
            assertEquals("$confidence", "From the file" to NameTrust.of(result(confidence)), shown?.title to shown?.trust)
        }
        assertEquals("their own fix still wins", NameTrust.PERSON_FIX, NameDecision.decide(NameTrust.SCRAPER_PERSON_CHOICE.name, result(Confidence.EXACT), fix(NameFixKind.OWN_NAME), "")?.trust)
    }

    @Test
    fun thePersonsPickReplacesEveryAutomaticNameAndAnEarlierPickButNoFix() {
        for (stored in listOf("FILE_NAME", "SCRAPER_LIKELY", "IDENTIFIER_LIKELY", "SCRAPER_EXACT", "IDENTIFIER_EXACT", "SCRAPER_PERSON_CHOICE", "PERSON_FIX")) {
            assertTrue(stored, NameDecision.takesScraperName(stored, NameTrust.SCRAPER_PERSON_CHOICE, fix = null))
        }
        for (kind in NameFixKind.entries) {
            assertEquals("a fix ($kind) always wins", false, NameDecision.takesScraperName("FILE_NAME", NameTrust.SCRAPER_PERSON_CHOICE, fix(kind)))
        }
    }

    @Test
    fun anArcadeBiosIsHidden() {
        assertTrue(NameDecision.decide(NameTrust.FILE_NAME.name, result(Confidence.EXACT, hide = true), null, "")!!.isNotGame)
    }

    @Test
    fun regionsComeBackAsCodesAndUnknownOnesFallBackToTheFileName() {
        assertEquals("USA,HKG", NameDecision.decide("FILE_NAME", result(Confidence.EXACT, regions = listOf("USA", "Greece", "Hong Kong")), null, "JPN")?.regions)
        assertEquals("JPN", NameDecision.decide("FILE_NAME", result(Confidence.EXACT, regions = listOf("Greece")), null, "JPN")?.regions)
        assertEquals("JPN", NameDecision.decide("FILE_NAME", result(Confidence.EXACT, regions = emptyList()), null, "JPN")?.regions)
    }

    @Test
    fun sortNameFollowsTheShownTitle() {
        assertEquals("legend of zork", NameDecision.decide("FILE_NAME", result(Confidence.EXACT, title = "The Legend of Zork"), null, "")?.sortName)
    }

    @Test
    fun anUnknownStoredTrustCountsAsTheFileName() {
        assertEquals(NameTrust.FILE_NAME, NameDecision.trustOf("SOMETHING_LATER"))
        assertEquals("New", NameDecision.decide("SOMETHING_LATER", result(Confidence.UNKNOWN, title = "New"), null, "")?.title)
    }

    @Test
    fun theTrustNamesSqlComparesWithAreRealTrustNames() {
        assertEquals(NameTrust.FILE_NAME.name, com.plumemu.coredata.db.NameTrustNames.FILE_NAME)
        assertEquals(NameTrust.PERSON_FIX.name, com.plumemu.coredata.db.NameTrustNames.PERSON_FIX)
    }

    @Test
    fun aScreenScraperTitleOnlyGoesUpTheTrustOrder() {
        val exact = NameTrust.SCRAPER_EXACT
        val likely = NameTrust.SCRAPER_LIKELY
        val cases = listOf(
            Triple("FILE_NAME", exact, true),
            Triple("IDENTIFIER_LIKELY", exact, true),
            Triple("SCRAPER_LIKELY", exact, true),
            Triple("SCRAPER_EXACT", exact, false),
            Triple("IDENTIFIER_EXACT", exact, false),
            Triple("FILE_NAME", likely, true),
            Triple("SCRAPER_LIKELY", likely, false),
            Triple("IDENTIFIER_LIKELY", likely, false),
            Triple("IDENTIFIER_EXACT", likely, false),
            // A person's fix whose rows are gone counts as the file name, as in decide().
            Triple("PERSON_FIX", exact, true),
            Triple("SOMETHING_NEWER", likely, true),
        )
        for ((stored, candidate, takes) in cases) {
            assertEquals("$stored vs $candidate", takes, NameDecision.takesScraperName(stored, candidate, fix = null))
        }
        for (kind in NameFixKind.entries) {
            assertEquals("a fix ($kind) always wins", false, NameDecision.takesScraperName("FILE_NAME", exact, fix(kind)))
        }
    }
}
