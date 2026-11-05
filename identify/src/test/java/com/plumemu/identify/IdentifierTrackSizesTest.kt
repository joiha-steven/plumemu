package com.plumemu.identify

import com.plumemu.identify.fingerprint.CodeKind
import com.plumemu.identify.fingerprint.Container
import com.plumemu.identify.fingerprint.Fingerprints
import com.plumemu.identify.pack.ConsolePackBuilder
import com.plumemu.identify.pack.FakeBundled
import com.plumemu.identify.pack.PackStore
import com.plumemu.identify.pack.RecordingLog
import com.plumemu.identify.pack.TestPacks
import com.plumemu.identify.pack.TrackSizeKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Track sizes as a layer after the serial (`Identifier`, layer 3). */
class IdentifierTrackSizesTest {
    @get:Rule val temp = TemporaryFolder()

    private val log = RecordingLog()
    private val sizesA = listOf(2_352L * 4_000, 2_352L * 75, 2_352L * 9_000)
    private val sizesB = listOf(2_352L * 4_000, 2_352L * 75, 2_352L * 9_100)
    private val sizesC = listOf(2_352L * 7_777)

    private fun keys(sizes: List<Long>) = listOf(TrackSizeKeys.ordered(sizes), TrackSizeKeys.total(sizes.sum()))

    private fun disc(sizes: List<Long>, serial: String? = null) = Fingerprints(
        size = 100,
        container = Container.CUE,
        productCode = serial,
        codeKind = serial?.let { CodeKind.PS1 },
        trackKeys = keys(sizes),
    )

    private fun identify(known: Fingerprints, packBuilder: ConsolePackBuilder.() -> Unit) = identify(known, packBuilder, "game.cue", emptyList())

    private fun identify(known: Fingerprints, packBuilder: ConsolePackBuilder.() -> Unit, fileName: String = "game.cue", regions: List<String> = emptyList()): Identification {
        val psx = ConsolePackBuilder("psx", 1)
        psx.packBuilder()
        val bundled = FakeBundled.of("psx" to psx.build(), "arcade" to TestPacks.arcade())
        val request = IdentifyRequest("psx", fileName, fileName.substringBeforeLast('.'), regionsFromName = regions, known = known)
        return Identifier(PackStore(temp.newFolder(), bundled, log), log = log).identify(request)
    }

    @Test fun oneTitleWithoutASerialIsExact() {
        val result = identify(disc(sizesA)) {
            release(title("Harbor Lights"), regions = listOf("USA"), trackKeys = keys(sizesA))
            release(title("Other Game"), regions = listOf("USA"), trackKeys = keys(sizesB))
        }
        assertEquals("Harbor Lights", result.title)
        assertEquals(Confidence.EXACT, result.confidence)
        assertEquals(Method.TRACK_SIZES, result.method)
        assertEquals(listOf("USA"), result.regions)
        // The sizes only name it: another game can share them, so they are no fix key (none here).
        assertEquals(emptyList<String>(), result.fixKeys)
    }

    @Test fun trackKeysNeverBecomeAFixKeyOrChangeTheSerialKey() {
        val result = identify(disc(sizesA, "SLUS-99999")) {
            release(title("Harbor Lights"), regions = listOf("USA"), codes = listOf("SLUS-99999" to CodeKind.PS1.packByte), trackKeys = keys(sizesA))
        }
        assertEquals("the first fix key, the ScreenScraper key, is the serial as before", listOf("code:PS1:SLUS-99999"), result.fixKeys)
        // Two different discs with the same layout have the same fix keys as without track keys.
        val other = identify(disc(sizesA)) { release(title("Harbor Lights"), regions = listOf("USA"), trackKeys = keys(sizesA)) }
        assertEquals(emptyList<String>(), other.fixKeys)
    }

    @Test fun regionsOfAllReleasesOfOneTitleAreKeptAndTheNameRegionPicks() {
        val build: ConsolePackBuilder.() -> Unit = {
            val t = title("Harbor Lights")
            release(t, regions = listOf("USA"), trackKeys = keys(sizesA))
            release(t, regions = listOf("Europe"), variant = "Rev 1", trackKeys = keys(sizesA))
        }
        assertEquals(listOf("USA", "Europe"), identify(disc(sizesA), build).regions)
        assertEquals(listOf("Europe"), identify(disc(sizesA), build, regions = listOf("Europe")).regions)
    }

    @Test fun severalTitlesUnderOneKeyGiveNoAnswerAndTheNameMatchGoesOn() {
        val result = identify(disc(sizesA), {
            release(title("Harbor Lights"), regions = listOf("USA"), trackKeys = keys(sizesA))
            release(title("Twin Of Harbor"), regions = listOf("USA"), trackKeys = keys(sizesA))
        }, fileName = "Harbor Lights.cue")
        assertEquals(Method.NAME_MATCH, result.method)
        assertEquals("Harbor Lights", result.title)
        // With a name that matches neither, nothing is guessed.
        val unknown = identify(disc(sizesA), {
            release(title("Harbor Lights"), regions = listOf("USA"), trackKeys = keys(sizesA))
            release(title("Twin Of Harbor"), regions = listOf("USA"), trackKeys = keys(sizesA))
        }, fileName = "zzz.cue")
        assertEquals(Method.FILE_NAME, unknown.method)
    }

    @Test fun sizesPickAmongReleasesThatShareASerial() {
        val build: ConsolePackBuilder.() -> Unit = {
            val t = title("Harbor Lights")
            release(t, regions = listOf("USA"), codes = listOf("SLUS-99999" to CodeKind.PS1.packByte), trackKeys = keys(sizesA))
            release(t, regions = listOf("USA"), variant = "Rev 1", codes = listOf("SLUS-99999" to CodeKind.PS1.packByte), trackKeys = keys(sizesB))
        }
        val rev1 = identify(disc(sizesB, "SLUS-99999"), build)
        assertEquals(Method.PRODUCT_CODE, rev1.method)
        assertEquals("Rev 1", rev1.variant)
        assertEquals("Rev 1", identify(disc(sizesB, "SLUS-99999"), build).variant)
        assertEquals(null, identify(disc(sizesA, "SLUS-99999"), build).variant)
        // Sizes that match neither release leave the serial's answer as it is: both, no single variant.
        assertEquals(null, identify(disc(sizesC, "SLUS-99999"), build).variant)
    }

    @Test fun whenSerialAndSizesDisagreeTheSerialWinsAndOneLineIsLogged() {
        val result = identify(disc(sizesB, "SLUS-99999")) {
            release(title("Harbor Lights"), regions = listOf("USA"), codes = listOf("SLUS-99999" to CodeKind.PS1.packByte), trackKeys = keys(sizesA))
            release(title("Other Game"), regions = listOf("USA"), trackKeys = keys(sizesB))
        }
        assertEquals("Harbor Lights", result.title)
        assertEquals(Method.PRODUCT_CODE, result.method)
        val lines = log.lines.filter { it.contains("track sizes") }
        assertEquals(lines.toString(), 1, lines.size)
        assertTrue(lines.toString(), lines.single().contains("serial kept"))
    }

    @Test fun aSerialOfSeveralTitlesLetsTheSizesDecide() {
        val result = identify(disc(sizesA, "SLUS-99999")) {
            release(title("Harbor Lights"), regions = listOf("USA"), codes = listOf("SLUS-99999" to CodeKind.PS1.packByte), trackKeys = keys(sizesA))
            release(title("Twin Serial"), regions = listOf("USA"), codes = listOf("SLUS-99999" to CodeKind.PS1.packByte), trackKeys = keys(sizesB))
        }
        assertEquals("Harbor Lights", result.title)
        assertEquals(Method.TRACK_SIZES, result.method)
    }

    @Test fun anOldCacheWithoutKeysIsNamedAsBefore() {
        val old = Fingerprints(size = 100, container = Container.CUE, productCode = "SLUS-99999", codeKind = CodeKind.PS1)
        val result = identify(old) {
            release(title("Harbor Lights"), regions = listOf("USA"), codes = listOf("SLUS-99999" to CodeKind.PS1.packByte), trackKeys = keys(sizesA))
        }
        assertEquals(Method.PRODUCT_CODE, result.method)
        assertEquals("Harbor Lights", result.title)
        // No serial and no keys: the file name only.
        val none = identify(Fingerprints(size = 100, container = Container.CUE), { release(title("Harbor Lights"), trackKeys = keys(sizesA)) }, fileName = "zzz.cue")
        assertEquals(Method.FILE_NAME, none.method)
    }
}
