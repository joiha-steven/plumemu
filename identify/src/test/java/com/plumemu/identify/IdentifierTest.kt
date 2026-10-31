package com.plumemu.identify

import com.plumemu.identify.fingerprint.CodeKind
import com.plumemu.identify.fingerprint.Container
import com.plumemu.identify.fingerprint.FakeRoms
import com.plumemu.identify.fingerprint.FingerprintReader
import com.plumemu.identify.fingerprint.disc.DiscImages
import com.plumemu.identify.fingerprint.Fingerprints
import com.plumemu.identify.fingerprint.TitleKind
import com.plumemu.identify.fingerprint.cart.N3dsReaderTest
import com.plumemu.identify.fingerprint.nx.SwitchReaderTest
import com.plumemu.identify.name.HackNames
import com.plumemu.identify.pack.ConsolePackBuilder
import com.plumemu.identify.pack.FakeBundled
import com.plumemu.identify.pack.PackFormat
import com.plumemu.identify.pack.PackStore
import com.plumemu.identify.pack.RecordingLog
import com.plumemu.identify.pack.TestPacks
import com.plumemu.identify.name.NameMatcher
import com.plumemu.identify.name.TitleCleaner
import com.plumemu.identify.source.ByteArraySource
import com.plumemu.identify.source.RandomAccessSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

class IdentifierTest {
    @get:Rule val temp = TemporaryFolder()

    private val romContent = FakeRoms.filler(4096, seed = 3)
    private val romCrc = FakeRoms.crcOf(romContent)

    /** A Mega Drive pack where one dump is listed for two regions, and a GBA pack with a code. */
    private fun store(log: IdentifyLog = RecordingLog(), extra: List<Pair<String, ByteArray>> = emptyList()): PackStore =
        storeAndBundle(log, extra).first

    private fun storeAndBundle(log: IdentifyLog = RecordingLog(), extra: List<Pair<String, ByteArray>> = emptyList()): Pair<PackStore, FakeBundled> {
        val md = ConsolePackBuilder("megadrive", 7)
        val zork = md.title("Zork Quest II", year = 1991, publisher = "Testsoft")
        md.release(zork, regions = listOf("USA"), crcs = listOf(romCrc to romContent.size.toLong()))
        md.release(zork, regions = listOf("Europe"), variant = "Rev 1", crcs = listOf(romCrc to romContent.size.toLong()))
        md.release(md.title("Moonlit Harbor"), regions = listOf("Japan"))
        val gba = ConsolePackBuilder("gba", 7)
        gba.release(gba.title("Pelvon's Revenge", year = 2003), regions = listOf("USA"), codes = listOf("ZZZE" to 1))
        val packs = listOf("megadrive" to md.build(), "gba" to gba.build(), "arcade" to TestPacks.arcade()) + extra
        val bundled = FakeBundled.of(*packs.toTypedArray(), extraIndex = "alias genesis megadrive\n")
        return PackStore(temp.newFolder(), bundled, log) to bundled
    }

    @Test fun zipWhoseDirectoryCrcIsInThePackIsExact() {
        val zip = FakeRoms.zip("zork.md" to romContent)
        val request = IdentifyRequest("genesis", "zork_final(1).zip", cleanedTitle = "zork final", regionsFromName = listOf("europe"), source = ByteArraySource(zip))
        val result = Identifier(store()).identify(request)
        assertEquals("Zork Quest II", result.title)
        assertEquals(Confidence.EXACT, result.confidence)
        assertEquals(Method.CRC, result.method)
        assertEquals("the region in the file name picks the release", listOf("Europe"), result.regions)
        assertEquals("Rev 1", result.variant)
        assertEquals(1991, result.year)
        assertEquals("Testsoft", result.publisher)
        assertEquals(romCrc, result.scrapeHints.crc32)
        assertEquals(romContent.size.toLong(), result.scrapeHints.size)
        assertEquals(zip.size.toLong(), result.scrapeHints.fileSize)
        assertEquals("zork_final(1).zip", result.scrapeHints.romFileName)
        assertEquals("Zork Quest II", result.scrapeHints.canonicalTitle)
        assertEquals(listOf(String.format("crc:%08x:%d", romCrc, romContent.size)), result.fixKeys)
        assertEquals(Container.ZIP, result.fingerprints?.container)
    }

    @Test fun withoutARegionReleasesOfOneTitleAreJoined() {
        val zip = FakeRoms.zip("zork.md" to romContent)
        val result = Identifier(store()).identify(IdentifyRequest("megadrive", "a.zip", null, source = ByteArraySource(zip)))
        assertEquals(Method.CRC, result.method)
        assertEquals("both releases' regions", listOf("USA", "Europe"), result.regions)
        assertNull("the variants differ, so none is claimed", result.variant)
        val otherRegion = Identifier(store()).identify(IdentifyRequest("megadrive", "a.zip", null, listOf("Brazil"), source = ByteArraySource(zip)))
        assertEquals("a region no release has picks nothing", listOf("USA", "Europe"), otherRegion.regions)
    }

    @Test fun crcOrCodeOfTwoTitlesIsNotExact() {
        val b = ConsolePackBuilder("pce", 1)
        b.release(b.title("Zork Quest II"), regions = listOf("USA"), crcs = listOf(romCrc to null), codes = listOf("ZZZE" to 0))
        b.release(b.title("Moonlit Harbor"), regions = listOf("Japan"), crcs = listOf(romCrc to null), codes = listOf("ZZZE" to 0))
        val identifier = Identifier(store(extra = listOf("pce" to b.build())))
        val known = Fingerprints(size = 1, container = Container.NONE, crc32 = romCrc, contentSize = 4096, productCode = "ZZZE", codeKind = CodeKind.GBA)
        val named = identifier.identify(IdentifyRequest("pce", "m.pce", "Moonlit Harbour", known = known))
        assertTrue("the pack that answered is named", named.packVersion != null)
        assertEquals("falls through to the name", Method.NAME_MATCH, named.method)
        assertEquals("Moonlit Harbor", named.title)
        val unnamed = identifier.identify(IdentifyRequest("pce", "m.pce", null, known = known))
        assertEquals(Confidence.UNKNOWN, unnamed.confidence)
    }

    @Test fun zippedNesIsFoundByItsHeaderlessCrcAtFullAndByItsStoredCrcAsAFallback() {
        val content = FakeRoms.filler(8192, seed = 31)
        val stored = FakeRoms.withHeader(FakeRoms.inesHeader(), content)
        val zip = FakeRoms.zip("game.nes" to stored)
        fun identify(listed: Pair<Long, Long>, depth: FingerprintReader.Depth): Identification {
            val nes = ConsolePackBuilder("nes", 1)
            nes.release(nes.title("Zork Quest II"), regions = listOf("USA"), crcs = listOf(listed))
            val request = IdentifyRequest("nes", "g.zip", null, source = ByteArraySource(zip), depth = depth)
            return Identifier(store(extra = listOf("nes" to nes.build()))).identify(request)
        }
        val headerless = FakeRoms.crcOf(content) to content.size.toLong()
        assertEquals(Method.FILE_NAME, identify(headerless, FingerprintReader.Depth.QUICK).method)
        val full = identify(headerless, FingerprintReader.Depth.FULL)
        assertEquals(Method.CRC, full.method)
        assertEquals("Zork Quest II", full.title)
        // A pack listing the dump with its header: the zip's directory CRC is tried second.
        val headered = FakeRoms.crcOf(stored) to stored.size.toLong()
        assertEquals(Method.CRC, identify(headered, FingerprintReader.Depth.QUICK).method)
        assertEquals(Method.CRC, identify(headered, FingerprintReader.Depth.FULL).method)
    }

    @Test fun dummyCodeIsNotLookedUp() {
        val b = ConsolePackBuilder("gbc", 1)
        b.release(b.title("Zork Quest II"), codes = listOf("0000" to 3))
        val identifier = Identifier(store(extra = listOf("gbc" to b.build())))
        val known = Fingerprints(size = 1, container = Container.NONE, productCode = "0000", codeKind = CodeKind.GB)
        val result = identifier.identify(IdentifyRequest("gbc", "z.gbc", null, known = known))
        assertEquals(Method.FILE_NAME, result.method)
        assertEquals("Z", result.title)
    }

    @Test fun gbaCodeInThePackIsExact() {
        val rom = FakeRoms.gba(code = "ZZZE")
        val result = Identifier(store()).identify(IdentifyRequest("gba", "pelvon.gba", "pelvon", source = ByteArraySource(rom)))
        assertEquals("Pelvon's Revenge", result.title)
        assertEquals(Confidence.EXACT, result.confidence)
        assertEquals(Method.PRODUCT_CODE, result.method)
        assertEquals("ZZZE", result.scrapeHints.serial)
        assertTrue(result.fixKeys.contains("code:${CodeKind.GBA.name}:ZZZE"))
    }

    @Test fun misspelledFileNameIsLikely() {
        val result = Identifier(store()).identify(IdentifyRequest("megadrive", "Moonlit Harbour (J).md", "Moonlit Harbour", listOf("Japan")))
        assertEquals("Moonlit Harbor", result.title)
        assertEquals(Confidence.LIKELY, result.confidence)
        assertEquals(Method.NAME_MATCH, result.method)
        assertEquals(listOf("Japan"), result.regions)
        assertEquals("Moonlit Harbor", result.scrapeHints.canonicalTitle)
    }

    /** People's file names, cleaned by TitleCleaner as library does, reach the pack title. */
    @Test fun peoplesFileNamesFindThePackTitle() {
        val identifier = Identifier(store())
        fun name(system: String, file: String): Identification {
            val cleaned = TitleCleaner.clean(file.substringBeforeLast('.'))
            return identifier.identify(IdentifyRequest(system, file, cleaned.title, cleaned.regions.map { it.noIntroName }))
        }
        for (file in listOf(
            "Zork.Quest.II.USA.PSP-GROUP.md", "zork_quest_ii_(usa).md", "ZORK QUEST II (USA).md", "ZorkQuestII.md",
            "Zork Quest II (USA)-romsfun.md", "emulatorgames.net - Zork Quest II (USA).md", "Zork Quest II v1.3.md",
            "Zork Quest II (U) [!].md", "Zork Quest II [0100ABCDEF012000][v0].md",
        )) {
            val result = name("megadrive", file)
            assertEquals(file, "Zork Quest II", result.title)
            assertEquals(file, Method.NAME_MATCH, result.method)
        }
        assertEquals("Pelvon's Revenge", name("gba", "1234 - Pelvon's Revenge (U).gba").title)
        // Not a numbered system: the number is part of the name, and nothing matches.
        val numbered = name("megadrive", "1234 - Moonlit Harbor (J).md")
        assertEquals("1234 - Moonlit Harbor", numbered.title)
        assertEquals(Method.FILE_NAME, numbered.method)
        // Nothing in the pack: the cleaned title in title case, never the raw name.
        val unknown = name("megadrive", "QWERTY_ASDF_(USA)-romsfun.md")
        assertEquals("Qwerty Asdf", unknown.title)
        assertEquals(Confidence.UNKNOWN, unknown.confidence)
    }

    @Test fun junkIsUnknownWithTheCleanedName() {
        val junk = FakeRoms.filler(2048, seed = 9)
        val result = Identifier(store()).identify(IdentifyRequest("megadrive", "qwerty.bin", "Qwerty", source = ByteArraySource(junk)))
        assertEquals("Qwerty", result.title)
        assertEquals(Confidence.UNKNOWN, result.confidence)
        assertEquals(Method.FILE_NAME, result.method)
        assertNull(result.scrapeHints.canonicalTitle)
        // Without a cleaned title the file's base name is cleaned here: never the raw name.
        val noClean = Identifier(store()).identify(IdentifyRequest("megadrive", "dir/qwerty_(USA)-romsfun.bin", null))
        assertEquals("Qwerty", noClean.title)
    }

    @Test fun arcadeBiosIsHiddenAndCloneNamesItsParent() {
        val identifier = Identifier(store())
        val bios = identifier.identify(IdentifyRequest("neogeo", "testbios.zip", "testbios"))
        assertTrue(bios.hide)
        assertEquals(Method.ARCADE_NAME, bios.method)
        assertEquals(listOf("arcade:testbios"), bios.fixKeys)
        val clone = identifier.identify(IdentifyRequest("mame", "zorkfghtj.zip", "zorkfghtj"))
        // The list shows the short title; the description stays for Info and ScreenScraper.
        assertEquals("Zork Fighters", clone.title)
        assertEquals("Zork Fighters (Japan)", clone.fullTitle)
        assertEquals("Zork Fighters (Japan)", clone.scrapeHints.canonicalTitle)
        assertEquals("zorkfght", clone.parent)
        assertEquals("the regions the description names, as No-Intro names", listOf("Japan"), clone.regions)
        assertEquals("a description without a region names none", emptyList<String>(), identifier.identify(IdentifyRequest("mame", "zorkfght.zip", "zorkfght")).regions)
        assertEquals("Testsoft", clone.publisher)
        assertEquals(Confidence.EXACT, clone.confidence)
        assertFalse(clone.hide)
        val unknown = identifier.identify(IdentifyRequest("fbneo", "nosuch.zip", null))
        assertEquals(Confidence.UNKNOWN, unknown.confidence)
        assertEquals("nosuch", unknown.title)
        assertEquals(listOf("arcade:nosuch"), unknown.fixKeys)
        assertNull(unknown.fullTitle)
        assertEquals(emptyList<String>(), unknown.regions)
    }

    @Test fun knownFingerprintsAreNotReadAgain() {
        val known = Fingerprints(size = 10, container = Container.NONE, productCode = "ZZZE", codeKind = CodeKind.GBA)
        val result = Identifier(store()).identify(IdentifyRequest("gba", "x.gba", null, source = FailingSource(), known = known))
        assertEquals(Method.PRODUCT_CODE, result.method)
    }

    /** A Switch pack with one game, listed under its base id as the tool writes it (code kind 15). */
    private fun switchStore(): PackStore {
        val b = ConsolePackBuilder("switch", 1)
        b.release(b.title("Zork Saga Deluxe", year = 2019, publisher = "Testsoft"), codes = listOf(SwitchReaderTest.BASE to 15))
        return store(extra = listOf("switch" to b.build()))
    }

    @Test fun renamedNspIsNamedByItsTicketId() {
        val nsp = SwitchReaderTest.pfs0(listOf(SwitchReaderTest.rights(SwitchReaderTest.BASE) + ".tik"))
        val result = Identifier(switchStore()).identify(IdentifyRequest("switch", "netta.nsp", "Netta", source = ByteArraySource(nsp)))
        assertEquals("Zork Saga Deluxe", result.title)
        assertEquals(Confidence.EXACT, result.confidence)
        assertEquals(Method.PRODUCT_CODE, result.method)
        assertEquals(2019, result.year)
        assertEquals(SwitchReaderTest.BASE, result.scrapeHints.serial)
    }

    /** The pack the tool writes with GameTDB's switchtdb: titledb's game with its id, and a name-only title (no code row). */
    private fun switchStoreWithGameTdbName(): PackStore {
        val b = ConsolePackBuilder("switch", 1)
        b.release(b.title("Zork Saga Deluxe", year = 2019, publisher = "Testsoft"), codes = listOf(SwitchReaderTest.BASE to 15))
        b.release(b.title("Harvest Moon: Echoes of Teradea"), regions = listOf("USA"))
        return store(extra = listOf("switch" to b.build()))
    }

    @Test fun switchGameUnknownToTitledbIsNamedByItsGameTdbName() {
        val unknownId = "0100839022642000"
        val nsp = SwitchReaderTest.pfs0(listOf(SwitchReaderTest.rights(unknownId) + ".tik"))
        val result = Identifier(switchStoreWithGameTdbName()).identify(
            IdentifyRequest("switch", "harvest_moon_echoes_of_teradea.nsp", "Harvest Moon Echoes Of Teradea", source = ByteArraySource(nsp)),
        )
        assertEquals("Harvest Moon: Echoes of Teradea", result.title)
        assertEquals(Confidence.LIKELY, result.confidence)
        assertEquals(Method.NAME_MATCH, result.method)
        assertEquals(unknownId, result.fingerprints?.titleId)
    }

    @Test fun switchTitleIdKnownToTitledbWinsOverAGameTdbName() {
        val nsp = SwitchReaderTest.pfs0(listOf(SwitchReaderTest.rights(SwitchReaderTest.BASE) + ".tik"))
        val result = Identifier(switchStoreWithGameTdbName()).identify(
            IdentifyRequest("switch", "harvest_moon_echoes_of_teradea.nsp", "Harvest Moon Echoes Of Teradea", source = ByteArraySource(nsp)),
        )
        assertEquals("Zork Saga Deluxe", result.title)
        assertEquals(Method.PRODUCT_CODE, result.method)
    }

    @Test fun switchUpdatesAndDlcAreNamedAfterTheirGame() {
        val identifier = Identifier(switchStore())
        for (id in listOf(SwitchReaderTest.UPDATE, SwitchReaderTest.DLC)) {
            val name = "Zork Sga [$id][UPD].nsp"
            val result = identifier.identify(IdentifyRequest("switch", name, "Zork Sga", source = ByteArraySource(ByteArray(0))))
            assertEquals(name, "Zork Saga Deluxe", result.title)
            assertEquals(name, Method.PRODUCT_CODE, result.method)
            assertEquals(name, id, result.fingerprints?.titleId)
        }
    }

    @Test fun switchFingerprintsCachedBeforeIdsWereReadGetTheFileNamesId() {
        val cached = Fingerprints(size = 0, container = Container.NONE)
        val request = IdentifyRequest("switch", "zork [${SwitchReaderTest.DLC}].nsp", "zork", source = FailingSource(), known = cached)
        val result = Identifier(switchStore()).identify(request)
        assertEquals("nothing is read", "Zork Saga Deluxe", result.title)
        assertEquals(TitleKind.DLC, result.fingerprints?.titleKind)
        val other = Identifier(switchStore()).identify(IdentifyRequest("gba", "zork [${SwitchReaderTest.BASE}].gba", "zork", known = cached))
        assertNull("only Switch files", other.fingerprints?.titleId)
    }

    @Test fun threeDsCardImageIsNamedByItsGameCode() {
        val b = ConsolePackBuilder("n3ds", 1)
        b.release(b.title("Zork Pocket 3D", year = 2012), regions = listOf("USA"), codes = listOf("ZZZE" to 16))
        val card = N3dsReaderTest.ncsd(N3dsReaderTest.ncch(N3dsReaderTest.GAME, "CTR-P-ZZZE"))
        val result = Identifier(store(extra = listOf("n3ds" to b.build()))).identify(
            IdentifyRequest("n3ds", "zp.3ds", "zp", source = ByteArraySource(card)),
        )
        assertEquals("Zork Pocket 3D", result.title)
        assertEquals(Method.PRODUCT_CODE, result.method)
        assertEquals(N3dsReaderTest.GAME, result.fingerprints?.titleId)
    }

    @Test(expected = IOException::class)
    fun sourceErrorsReachTheCaller() {
        Identifier(store()).identify(IdentifyRequest("gba", "x.gba", null, source = FailingSource()))
    }

    @Test fun systemWithoutAPackFallsBackAndLogsOnce() {
        val log = RecordingLog()
        val identifier = Identifier(store(log))
        repeat(3) {
            val result = identifier.identify(IdentifyRequest("snes", "zork.sfc", "Zork Quest II"))
            assertEquals(Confidence.UNKNOWN, result.confidence)
        }
        assertEquals(1, log.lines.count { it.contains("snes") })
    }

    @Test fun canonicalHeaderTitleIsExact() {
        val disc = DiscImages.nintendoHeader("GZZE01", "Pelvon: Cube Edition").copyOf(1 shl 20)
        val result = Identifier(store()).identify(IdentifyRequest("gc", "p.iso", "p", source = ByteArraySource(disc)))
        assertEquals(Method.HEADER_TITLE, result.method)
        assertEquals(Confidence.EXACT, result.confidence)
        assertEquals("Pelvon: Cube Edition", result.title)
        val rom = FakeRoms.gba(code = "ABCE", title = "PELVON")
        val gba = Identifier(store()).identify(IdentifyRequest("gba", "p.gba", "p", source = ByteArraySource(rom)))
        assertEquals("a GBA header title is cut to 12 capitals, not the official title", Method.FILE_NAME, gba.method)
    }

    @Test fun packDamagedAfterInstallIsLoggedAndSkipped() {
        val damaged = ConsolePackBuilder("nes", 1).let { b ->
            b.release(b.title("Zork Quest"), crcs = listOf(romCrc to null))
            b.build()
        }.let(::breakFirstTitleRef)
        val log = RecordingLog()
        val (packs, bundled) = storeAndBundle(log, extra = listOf("nes" to damaged))
        val identifier = Identifier(packs, FingerprintReader(), log)
        val known = Fingerprints(size = 1, container = Container.NONE, crc32 = romCrc, contentSize = 4096)
        repeat(20) {
            val result = identifier.identify(IdentifyRequest("nes", "z.nes", "Zork Quest", known = known))
            assertEquals(Confidence.UNKNOWN, result.confidence)
            assertEquals("no pack answered, so the caller can leave the game pending", null, result.packVersion)
        }
        assertTrue(log.lines.toString(), log.lines.any { it.contains("lookup failed: CorruptPackException") })
        val nesOpens = bundled.opensOf("nes.pack.gz")
        assertEquals("installed once, reinstalled once, then given up: no loop per file", 2, nesOpens)
        assertEquals(1, log.lines.count { it.contains("pack nes: still broken") })
        assertEquals(2, log.lines.count { it.contains("pack nes: lookup failed") })
    }

    @Test fun matcherIndexThatFailsIsNotRebuiltPerFile() {
        val damaged = ConsolePackBuilder("nes", 1).let { b ->
            b.release(b.title("Zork Quest"))
            b.build()
        }.let(::breakFirstTitleRef)
        val log = RecordingLog()
        val (packs, bundled) = storeAndBundle(log, extra = listOf("nes" to damaged))
        val identifier = Identifier(packs, FingerprintReader(), log)
        repeat(20) { identifier.identify(IdentifyRequest("nes", "z.nes", "Zork Quest")) }
        assertEquals(2, bundled.opensOf("nes.pack.gz"))
        assertEquals(2, log.lines.count { it.contains("pack nes: lookup failed") })
    }

    @Test fun oneMatcherPerPackUnderConcurrency() {
        val packs = store()
        val identifier = Identifier(packs)
        val pack = checkNotNull(packs.packFor("megadrive"))
        val pool = java.util.concurrent.Executors.newFixedThreadPool(8)
        val start = java.util.concurrent.CountDownLatch(1)
        val futures = (0 until 32).map {
            pool.submit<Any> {
                start.await()
                identifier.matcherFor(pack).also { m -> m.match("Moonlit Harbour") }
            }
        }
        start.countDown()
        val matchers = futures.map { it.get(30, java.util.concurrent.TimeUnit.SECONDS) }
        pool.shutdown()
        assertEquals(1, matchers.map { System.identityHashCode(it) }.distinct().size)
    }

    @Test fun nameTrustOrder() {
        assertEquals(NameTrust.SCRAPER_EXACT, NameTrust.winner(NameTrust.IDENTIFIER_LIKELY, NameTrust.SCRAPER_EXACT))
        assertEquals(NameTrust.SCRAPER_EXACT, NameTrust.winner(NameTrust.FILE_NAME, NameTrust.SCRAPER_EXACT))
        assertEquals(NameTrust.IDENTIFIER_EXACT, NameTrust.winner(NameTrust.IDENTIFIER_EXACT, NameTrust.SCRAPER_EXACT))
        assertEquals(NameTrust.PERSON_FIX, NameTrust.winner(NameTrust.PERSON_FIX, NameTrust.SCRAPER_EXACT))
        assertEquals(NameTrust.IDENTIFIER_LIKELY, NameTrust.winner(NameTrust.IDENTIFIER_LIKELY, NameTrust.SCRAPER_LIKELY))
        val likely = Identifier(store()).identify(IdentifyRequest("megadrive", "m.md", "Moonlit Harbour"))
        assertEquals(NameTrust.IDENTIFIER_LIKELY, NameTrust.of(likely))
    }

    @Test fun aPersonsScreenScraperPickBeatsEveryAutomaticNameButNotTheirOwnFix() {
        for (automatic in listOf(NameTrust.FILE_NAME, NameTrust.SCRAPER_LIKELY, NameTrust.IDENTIFIER_LIKELY, NameTrust.SCRAPER_EXACT, NameTrust.IDENTIFIER_EXACT)) {
            assertEquals(NameTrust.SCRAPER_PERSON_CHOICE, NameTrust.winner(automatic, NameTrust.SCRAPER_PERSON_CHOICE))
            assertEquals(NameTrust.SCRAPER_PERSON_CHOICE, NameTrust.winner(NameTrust.SCRAPER_PERSON_CHOICE, automatic))
        }
        assertEquals(NameTrust.PERSON_FIX, NameTrust.winner(NameTrust.PERSON_FIX, NameTrust.SCRAPER_PERSON_CHOICE))
    }

    @Test fun cueWithItsTrackFindsTheSerialInThePackAndWithoutItFallsBack() {
        val psx = ConsolePackBuilder("psx", 1)
        psx.release(psx.title("Pelvon Racing"), regions = listOf("USA"), codes = listOf("SLUS-99999" to CodeKind.PS1.packByte))
        val identifier = Identifier(store(extra = listOf("psx" to psx.build())))
        val cue = "FILE \"Test Game (Track 1).bin\" BINARY\n  TRACK 01 MODE2/2352\n    INDEX 01 00:00:00\n"
        val bin = DiscImages.toRaw(DiscImages.ps1Iso(), mode = 2)
        val siblings = DiscImages.Siblings(mapOf("Test Game (Track 1).bin" to bin))
        val found = identifier.identify(IdentifyRequest("psx", "Test Game.cue", "test game", source = ByteArraySource(cue.toByteArray()), siblings = siblings))
        assertEquals(Method.PRODUCT_CODE, found.method)
        assertEquals(Confidence.EXACT, found.confidence)
        assertEquals("Pelvon Racing", found.title)
        assertTrue(found.fixKeys.toString(), found.fixKeys.contains("code:PS1:SLUS-99999"))
        val without = identifier.identify(IdentifyRequest("psx", "Test Game.cue", "test game", source = ByteArraySource(cue.toByteArray())))
        assertEquals(Method.FILE_NAME, without.method)
        assertEquals(Confidence.UNKNOWN, without.confidence)
        // The fallback is title-cased for a console system.
        assertEquals("Test Game", without.title)
    }

    @Test fun ndsBannerWithASubtitleIsNotTakenAsTheExactTitle() {
        val rom = DiscImages.ndsWithBanner("Zork Quest\nThe Lost Isle\nTestsoft")
        val result = Identifier(store()).identify(IdentifyRequest("nds", "z.nds", "z", source = ByteArraySource(rom)))
        assertTrue("not the truncated first line", result.method != Method.HEADER_TITLE)
        assertEquals(Confidence.UNKNOWN, result.confidence)
        assertEquals("Zork Quest - The Lost Isle", result.fingerprints?.internalTitle)
        val two = DiscImages.ndsWithBanner("Zork Quest\nTestsoft")
        val exact = Identifier(store()).identify(IdentifyRequest("nds", "z.nds", "z", source = ByteArraySource(two)))
        assertEquals(Method.HEADER_TITLE, exact.method)
        assertEquals("Zork Quest", exact.title)
    }

    @Test fun lookupFailureThatIsNotACorruptPackSkipsTheLayerButKeepsThePack() {
        val log = RecordingLog()
        val (packs, bundled) = storeAndBundle(log)
        val identifier = Identifier(packs, FingerprintReader(), log)
        identifier.matcherFactory = { throw IllegalArgumentException("test: matcher bug") }
        repeat(5) {
            val result = identifier.identify(IdentifyRequest("megadrive", "m.md", "Moonlit Harbour"))
            assertEquals(Confidence.UNKNOWN, result.confidence)
        }
        assertEquals("the pack is not reinstalled", 1, bundled.opensOf("megadrive.pack.gz"))
        assertTrue(log.lines.toString(), log.lines.any { it.contains("lookup failed, pack kept: IllegalArgumentException") })
        assertTrue(log.lines.toString(), log.lines.none { it.contains("broken") || it.contains("reinstalling") })
        identifier.matcherFactory = { NameMatcher(it) }
        assertEquals("a later call uses the pack again", Method.NAME_MATCH, identifier.identify(IdentifyRequest("megadrive", "m.md", "Moonlit Harbour")).method)
    }

    /** Points the first title's name outside the string pool and signs the pack again. */
    private fun breakFirstTitleRef(pack: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(pack).order(ByteOrder.LITTLE_ENDIAN)
        val titles = buffer.getInt(PackFormat.HEADER_SIZE + 2 * PackFormat.SECTION_ENTRY_SIZE + 4)
        buffer.putInt(titles, 1_000_000)
        val body = pack.copyOf(pack.size - PackFormat.TRAILER_SIZE)
        return body + MessageDigest.getInstance("SHA-256").digest(body)
    }

    private class FailingSource : RandomAccessSource {
        override val size: Long = 1 shl 20
        override fun readAt(position: Long, dst: ByteArray, offset: Int, length: Int): Int = throw IOException("test: card removed")
        override fun close() = Unit
    }

    /** Packs for the hack tests: a PS1 pack with two serials and a GBA pack with FireRed's code. */
    private fun hackStore(): PackStore {
        val ps1 = ConsolePackBuilder("psx", 1)
        ps1.release(ps1.title("Crash Bandicoot"), regions = listOf("USA"), codes = listOf("SCUS-94900" to 3))
        ps1.release(ps1.title("Final Fantasy VII"), regions = listOf("USA"), codes = listOf("SCUS-94163" to 3))
        ps1.release(ps1.title("Grand Theft Auto - San Andreas"), regions = listOf("USA"), codes = listOf("SLUS-20946" to 3))
        ps1.release(ps1.title("Metal Gear Solid 3 - Snake Eater"), regions = listOf("USA"), codes = listOf("SLUS-21359" to 3))
        val gba = ConsolePackBuilder("gba", 1)
        gba.release(gba.title("Pokemon - FireRed Version"), regions = listOf("USA"), codes = listOf("BPRE" to 1))
        return store(extra = listOf("psx" to ps1.build(), "gba" to gba.build()))
    }

    private fun byCode(system: String, file: String, code: String, kind: CodeKind): Identification {
        val known = Fingerprints(size = 1, container = Container.NONE, productCode = code, codeKind = kind)
        val cleaned = TitleCleaner.clean(file.substringBeforeLast('.'))
        return Identifier(hackStore()).identify(
            IdentifyRequest(system, file, cleaned.title, cleaned.regions.map { it.noIntroName }, known = known),
        )
    }

    @Test fun hackKeepingTheOriginalsSerialIsNamedByItsFile() {
        val r = byCode("psx", "Crash Bandicoot - N. Sane Trilogy (USA) [Hack].PBP", "SCUS-94900", CodeKind.PS1)
        assertEquals("Crash Bandicoot - N. Sane Trilogy", r.title)
        assertEquals(Method.FILE_NAME, r.method)
        assertEquals("no serial for ScreenScraper", null, r.scrapeHints.serial)
        assertTrue("no code fix key", r.fixKeys.none { it.startsWith("code:") })
        assertEquals(null, r.fingerprints?.productCode)
    }

    @Test fun hackWithAnotherNameWithoutMarkerIsNamedByItsFile() {
        val r = byCode("gba", "Pokemon Radical Red.gba", "BPRE", CodeKind.GBA)
        assertEquals("Pokemon Radical Red", r.title)
        assertEquals(null, r.scrapeHints.serial)
        val original = byCode("gba", "Pokemon - FireRed Version (USA).gba", "BPRE", CodeKind.GBA)
        assertEquals("Pokemon - FireRed Version", original.title)
        assertEquals(Method.PRODUCT_CODE, original.method)
        assertEquals("BPRE", original.scrapeHints.serial)
    }

    @Test fun badFileNamesAreStillRescuedBySerial() {
        for (file in listOf("Final Fantasy VII (USA) (Disc 1).bin", "FF7 Disc1.bin", "SLUS_012.34.bin", "game.iso", "Final Fantasy VII International.bin")) {
            val r = byCode("psx", file, "SCUS-94163", CodeKind.PS1)
            assertEquals(file, "Final Fantasy VII", r.title)
            assertEquals(file, Method.PRODUCT_CODE, r.method)
            assertEquals(file, "SCUS-94163", r.scrapeHints.serial)
        }
    }

    @Test fun abbreviatedOrMisspeltDiscNamesAreStillRescuedBySerial() {
        val cases = listOf(
            Triple("GTA San Andreas.iso", "SLUS-20946", "Grand Theft Auto - San Andreas"),
            Triple("MGS3 Snake Eater.iso", "SLUS-21359", "Metal Gear Solid 3 - Snake Eater"),
            Triple("Crash Bandicot (USA).bin", "SCUS-94900", "Crash Bandicoot"),
            Triple("Crash Bandicoot (USA) [HD].bin", "SCUS-94900", "Crash Bandicoot"),
            Triple("Crash Bandicoot [Hebrew].bin", "SCUS-94900", "Crash Bandicoot"),
        )
        for ((file, serial, name) in cases) {
            val r = byCode("psx", file, serial, CodeKind.PS1)
            assertEquals(file, name, r.title)
            assertEquals(file, Method.PRODUCT_CODE, r.method)
        }
    }

    @Test fun onlyLowerCaseHackTagsAreMarkers() {
        for (file in listOf("x [HD].gba", "x [HQ].gba", "x [Hebrew].gba", "x [hd].gba")) assertTrue(file, !HackNames.hasHackMarker(file))
        for (file in listOf("x [h].gba", "x [h1].gba", "x [h1C].gba", "x [h2+3].gba")) assertTrue(file, HackNames.hasHackMarker(file))
    }

    @Test fun translationTagsAreHackMarkers() {
        for (file in listOf("Mother 3 (Japan) [T+Eng].gba", "x [T-Spa].gba", "x [h1].gba", "x [h2C].gba", "x (Hack).gba", "x (translated).gba", "x [Translation].gba")) {
            assertTrue(file, HackNames.hasHackMarker(file))
        }
        for (file in listOf("Hack and Slash (USA).gba", "Pokemon - FireRed Version (USA).gba", "Thing [!].gba")) {
            assertTrue(file, !HackNames.hasHackMarker(file))
        }
        val r = byCode("gba", "Mother 3 (Japan) [T+Eng].gba", "BPRE", CodeKind.GBA)
        assertEquals("Mother 3", r.title)
        assertEquals(Method.FILE_NAME, r.method)
    }
}
