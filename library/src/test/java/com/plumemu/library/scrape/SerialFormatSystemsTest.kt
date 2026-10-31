package com.plumemu.library.scrape

import com.plumemu.identify.fingerprint.CodeKind
import com.plumemu.library.corpus.RealData
import com.plumemu.scraper.GameDetails
import com.plumemu.scraper.IdName
import com.plumemu.scraper.StoredAnswers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every system of the shipped `platforms.json` and every extension it accepts: a cartridge whose
 * reader gives a code (`CodeKind.ofCartridge`) has an answer on its format's own system accepted,
 * live and in the scrape-rules repair (`StoredAnswers`, the same check). A new folder system that
 * holds another system's files (`gba/` with `.gbc`, `snesmsu1/` with `.sfc`) is covered without a
 * hand-written list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SerialFormatSystemsTest {
    @Test
    fun anAnswerOnTheFormatsOwnSystemIsAcceptedInEveryFolderThatHoldsIt() {
        val covered = mutableSetOf<Pair<String, String>>()
        for (rule in RealData.systemRules()) {
            for (extension in rule.extensions) {
                val kind = CodeKind.ofCartridge(rule.id, extension) ?: continue
                val native = kind.nativeSystemId
                val answered = StoredAnswers.screenScraperSystemId(native) ?: error("$kind's system $native has no ScreenScraper id")
                val stored = GameDetails(system = IdName(answered.toString(), native)).toJson()
                assertEquals("${rule.id} .$extension answered on $native ($answered)", true, StoredAnswers.serialAnswerFitsSystem(stored, rule.id, native))
                covered += rule.id to native
            }
        }
        for (case in listOf("gba" to "gb", "sgb" to "gb", "snesmsu1" to "snes", "genesismsu" to "megadrive")) {
            assertTrue("$case is among the folders checked", case in covered)
        }
    }

    @Test
    fun aSnesCodeAnsweredAsA3dsGameStaysRefused() {
        val stored = GameDetails(system = IdName("17", "3DS")).toJson()
        assertEquals(false, StoredAnswers.serialAnswerFitsSystem(stored, "snes", CodeKind.SNES.nativeSystemId))
    }

    @Test
    fun aMegaDriveCodeAnsweredOn32xIsAcceptedAndTheOtherWayRound() {
        val on32x = GameDetails(system = IdName("19", "32X")).toJson()
        val onMegaDrive = GameDetails(system = IdName("1", "Mega Drive")).toJson()
        assertEquals(true, StoredAnswers.serialAnswerFitsSystem(on32x, "megadrive", CodeKind.MEGADRIVE.nativeSystemId))
        assertEquals(true, StoredAnswers.serialAnswerFitsSystem(onMegaDrive, "sega32x", CodeKind.MEGADRIVE.nativeSystemId))
    }
}
