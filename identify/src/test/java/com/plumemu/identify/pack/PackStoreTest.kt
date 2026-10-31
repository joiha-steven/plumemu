package com.plumemu.identify.pack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PackStoreTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun firstUseUnpacksTheBundledPackOnce() {
        val dir = temp.newFolder("packs")
        val bundled = FakeBundled.of("megadrive" to TestPacks.console())
        val store = PackStore(dir, bundled, RecordingLog())
        val pack = store.packFor("megadrive")
        assertNotNull(pack)
        assertEquals("Zork Quest II", pack?.byCrc(TestPacks.CRC_ZORK)?.single()?.title)
        assertSame(pack, store.packFor("MegaDrive"))
        assertEquals(1, bundled.opens.get())
        assertEquals(listOf("megadrive.pack"), dir.list()?.toList())

        // A new store (the next app start) opens the installed file without unpacking again.
        val again = PackStore(dir, bundled, RecordingLog())
        assertNotNull(again.packFor("megadrive"))
        assertEquals(1, bundled.opens.get())
    }

    @Test fun aPackTheBuildUnGzippedInstallsWithTheSameChecks() {
        // The Android Gradle plugin un-gzips .gz assets: the APK holds the plain pack.
        val bytes = TestPacks.console()
        val pack = TestPacks.open(bytes)
        val index = "megadrive megadrive.pack.gz ${pack.dataVersion} ${pack.checksum}\n"
        val plain = FakeBundled(index, mapOf("megadrive.pack.gz" to bytes))
        assertEquals("Zork Quest II", PackStore(temp.newFolder(), plain, RecordingLog()).packFor("megadrive")?.byCrc(TestPacks.CRC_ZORK)?.single()?.title)

        val damaged = bytes.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
        val log = RecordingLog()
        assertNull(PackStore(temp.newFolder(), FakeBundled(index, mapOf("megadrive.pack.gz" to damaged)), log).packFor("megadrive"))
        assertTrue(log.lines.toString(), log.lines.any { "install failed" in it })
    }

    @Test fun aBundledPackThatFailsToInstallIsToldApartFromNoPack() {
        val bytes = TestPacks.console()
        val pack = TestPacks.open(bytes)
        val damaged = TestPacks.gzip(bytes.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() })
        val bundled = FakeBundled("megadrive megadrive.pack.gz ${pack.dataVersion} ${pack.checksum}\nalias genesis megadrive\n", mapOf("megadrive.pack.gz" to damaged))
        val store = PackStore(temp.newFolder(), bundled, RecordingLog())
        assertNull(store.packFor("megadrive"))
        assertTrue(store.hasBundled("megadrive"))
        assertTrue(store.hasBundled("Genesis"))
        assertFalse(store.hasBundled("psp"))
    }

    @Test fun aliasesShareAPack() {
        val bundled = FakeBundled.of("megadrive" to TestPacks.console(), extraIndex = "alias genesis megadrive\n")
        val store = PackStore(temp.newFolder(), bundled, RecordingLog())
        assertSame(store.packFor("genesis"), store.packFor("megadrive"))
        assertEquals("megadrive", store.packIdFor("Genesis"))
    }

    @Test fun missingPackIsNullAndLoggedOnce() {
        val log = RecordingLog()
        val store = PackStore(temp.newFolder(), FakeBundled.of(), log)
        assertNull(store.packFor("snes"))
        assertNull(store.packFor("snes"))
        assertEquals(1, log.lines.count { it.contains("snes") })
    }

    @Test fun noIndexAtAllIsNotAnError() {
        val store = PackStore(temp.newFolder(), BundledPacks.NONE, RecordingLog())
        assertNull(store.packFor("nes"))
        assertEquals(emptySet<String>(), store.prepare(listOf("nes", "snes")))
    }

    @Test fun aBuildWithoutPacksLogsOneLineNoMatterHowManySystemsAsk() {
        val log = RecordingLog()
        val store = PackStore(temp.newFolder(), BundledPacks.NONE, log)
        for (id in listOf("nes", "snes", "gba", "psx", "arcade")) assertNull(store.packFor(id))
        assertEquals(log.lines.toString(), 1, log.lines.size)
        assertTrue(log.lines.single(), log.lines.single().contains("no name packs are bundled"))
    }

    @Test fun newerBundledVersionReplacesTheInstalledOne() {
        val dir = temp.newFolder()
        PackStore(dir, FakeBundled.of("megadrive" to TestPacks.console(dataVersion = 1)), RecordingLog()).packFor("megadrive")
        val store = PackStore(dir, FakeBundled.of("megadrive" to TestPacks.console(dataVersion = 2)), RecordingLog())
        assertEquals(2L, store.packFor("megadrive")?.dataVersion)
    }

    @Test fun newerInstalledPackWinsOverAnOlderBundledOne() {
        val dir = temp.newFolder()
        val first = PackStore(dir, FakeBundled.of("megadrive" to TestPacks.console(dataVersion = 1)), RecordingLog())
        val update = TestPacks.console(dataVersion = 5)
        val result = first.install("megadrive", ByteArrayInputStream(TestPacks.gzip(update)), TestPacks.open(update).checksum)
        assertTrue(result is InstallResult.Installed)
        assertEquals(5L, first.packFor("megadrive")?.dataVersion)

        val bundled = FakeBundled.of("megadrive" to TestPacks.console(dataVersion = 3))
        val later = PackStore(dir, bundled, RecordingLog())
        assertEquals(5L, later.packFor("megadrive")?.dataVersion)
        assertEquals("the older bundled pack is not even unpacked", 0, bundled.opens.get())
    }

    @Test fun corruptDownloadLeavesTheOldPack() {
        val dir = temp.newFolder()
        val log = RecordingLog()
        val store = PackStore(dir, FakeBundled.of("megadrive" to TestPacks.console(dataVersion = 1)), log)
        val old = store.packFor("megadrive")
        val update = TestPacks.console(dataVersion = 2)
        val expected = TestPacks.open(update).checksum
        val damaged = update.copyOf().also { it[200] = (it[200] + 1).toByte() }

        val bad = store.install("megadrive", ByteArrayInputStream(TestPacks.gzip(damaged)), expected)
        assertTrue(bad is InstallResult.Failed)
        val truncated = TestPacks.gzip(update).let { it.copyOf(it.size / 2) }
        assertTrue(store.install("megadrive", ByteArrayInputStream(truncated), expected) is InstallResult.Failed)
        val wrongSha = store.install("megadrive", ByteArrayInputStream(TestPacks.gzip(update)), "0".repeat(64))
        assertTrue(wrongSha is InstallResult.Failed)
        val notGzip = store.install("megadrive", ByteArrayInputStream(update), expected)
        assertTrue(notGzip is InstallResult.Failed)

        assertSame(old, store.packFor("megadrive"))
        assertEquals(listOf("megadrive.pack"), dir.list()?.toList())
        assertEquals(1L, PackStore(dir, BundledPacks.NONE, RecordingLog()).packFor("megadrive")?.dataVersion)
        assertTrue(log.lines.any { it.contains("install failed") })
    }

    @Test fun olderOrForeignUpdateIsRefused() {
        val dir = temp.newFolder()
        val store = PackStore(dir, FakeBundled.of("megadrive" to TestPacks.console(dataVersion = 5)), RecordingLog())
        store.packFor("megadrive")
        val older = TestPacks.console(dataVersion = 4)
        val result = store.install("megadrive", ByteArrayInputStream(TestPacks.gzip(older)), TestPacks.open(older).checksum)
        assertTrue((result as InstallResult.Failed).reason.contains("older"))
        val snes = TestPacks.console(systemId = "snes", dataVersion = 9)
        val foreign = store.install("megadrive", ByteArrayInputStream(TestPacks.gzip(snes)), TestPacks.open(snes).checksum)
        assertTrue((foreign as InstallResult.Failed).reason.contains("system snes"))
        assertEquals(5L, store.packFor("megadrive")?.dataVersion)
    }

    @Test fun bundledPackWithWrongIndexChecksumIsNotInstalled() {
        val bytes = TestPacks.console()
        val index = "megadrive megadrive.pack.gz 20261021 ${"a".repeat(64)}\n"
        val bundled = FakeBundled(index, mapOf("megadrive.pack.gz" to TestPacks.gzip(bytes)))
        val log = RecordingLog()
        val store = PackStore(temp.newFolder(), bundled, log)
        assertNull(store.packFor("megadrive"))
        assertTrue(log.lines.any { it.contains("SHA-256") })
        assertEquals(0, store.prepare(listOf("megadrive")).size)
    }

    @Test fun bundledPackWhoseVersionDiffersFromTheIndexIsNotInstalled() {
        val bytes = TestPacks.console(dataVersion = 20261021)
        val index = "megadrive megadrive.pack.gz 20261022 ${TestPacks.open(bytes).checksum}\n"
        val log = RecordingLog()
        val dir = temp.newFolder()
        val store = PackStore(dir, FakeBundled(index, mapOf("megadrive.pack.gz" to TestPacks.gzip(bytes))), log)
        assertNull(store.packFor("megadrive"))
        assertTrue(log.lines.toString(), log.lines.any { it.contains("differs from the index") })
        assertEquals(emptyList<String>(), dir.list()?.toList())
    }

    @Test fun onlyStaleTempFilesAreRemovedOnLoad() {
        val dir = temp.newFolder()
        val stale = java.io.File(dir, "${PackStore.TEMP_PREFIX}megadrive.111${PackStore.TEMP_SUFFIX}").also {
            it.writeBytes(ByteArray(8))
            it.setLastModified(System.currentTimeMillis() - PackStore.STALE_TEMP_MILLIS - 60_000)
        }
        val inProgress = java.io.File(dir, "${PackStore.TEMP_PREFIX}megadrive.222${PackStore.TEMP_SUFFIX}").also { it.writeBytes(ByteArray(8)) }
        val otherSystem = java.io.File(dir, "${PackStore.TEMP_PREFIX}snes.333${PackStore.TEMP_SUFFIX}").also {
            it.writeBytes(ByteArray(8))
            it.setLastModified(System.currentTimeMillis() - PackStore.STALE_TEMP_MILLIS - 60_000)
        }
        // `pico` is a prefix of `pico-8`: loading one must not touch the other's temp files.
        val pico = java.io.File(dir, "${PackStore.TEMP_PREFIX}pico.111${PackStore.TEMP_SUFFIX}").also {
            it.writeBytes(ByteArray(8))
            it.setLastModified(System.currentTimeMillis() - PackStore.STALE_TEMP_MILLIS - 60_000)
        }
        val pico8 = java.io.File(dir, "${PackStore.TEMP_PREFIX}pico-8.222${PackStore.TEMP_SUFFIX}").also {
            it.writeBytes(ByteArray(8))
            it.setLastModified(System.currentTimeMillis() - PackStore.STALE_TEMP_MILLIS - 60_000)
        }
        val bundled = FakeBundled.of("megadrive" to TestPacks.console(), "pico" to TestPacks.console("pico"), "pico-8" to TestPacks.console("pico-8"))
        val store = PackStore(dir, bundled, RecordingLog())
        assertNotNull(store.packFor("megadrive"))
        assertFalse("a dead install's temp is removed", stale.exists())
        assertTrue("another store's install in progress is left alone", inProgress.exists())
        assertTrue("only the loaded system's temps are touched", otherSystem.exists())
        assertTrue("pico-8's temp is not taken for megadrive's", pico8.exists())
        assertNotNull(store.packFor("pico"))
        assertFalse("pico's own stale temp is removed", pico.exists())
        assertTrue("pico-8's stale temp is not taken for pico's", pico8.exists())
        assertNotNull(store.packFor("pico-8"))
        assertFalse("pico-8 removes its own", pico8.exists())
    }

    @Test fun brokenPackIsReinstalledOnceThenGivenUp() {
        val dir = temp.newFolder()
        val bundled = FakeBundled.of("megadrive" to TestPacks.console())
        val log = RecordingLog()
        val store = PackStore(dir, bundled, log)
        val first = checkNotNull(store.packFor("megadrive"))
        store.reportBroken(first, CorruptPackException("test"))
        store.reportBroken(first, CorruptPackException("a late report of the same pack is ignored"))
        val second = checkNotNull(store.packFor("megadrive"))
        assertEquals(2, bundled.opens.get())
        store.reportBroken(second, CorruptPackException("test"))
        assertNull(store.packFor("megadrive"))
        assertEquals(2, bundled.opens.get())
        assertEquals(1, log.lines.count { it.contains("reinstalling") })
        assertEquals(1, log.lines.count { it.contains("skipped this session") })
    }

    @Test fun unreadableBundledPackIsNullNotACrash() {
        val bytes = TestPacks.console()
        val index = "megadrive megadrive.pack.gz 1 ${TestPacks.open(bytes).checksum}\n"
        val store = PackStore(temp.newFolder(), FakeBundled(index, emptyMap(), failOpen = true), RecordingLog())
        assertNull(store.packFor("megadrive"))
    }

    @Test fun damagedInstalledFileIsReplacedFromTheBundle() {
        val dir = temp.newFolder()
        val bundled = FakeBundled.of("megadrive" to TestPacks.console())
        PackStore(dir, bundled, RecordingLog()).packFor("megadrive")
        java.io.File(dir, "megadrive.pack").writeBytes(ByteArray(10))
        val log = RecordingLog()
        assertNotNull(PackStore(dir, bundled, log).packFor("megadrive"))
        assertTrue(log.lines.any { it.contains("rejected") })
    }

    @Test fun malformedIndexLinesAreSkipped() {
        val log = RecordingLog()
        val index = BundledIndex.parse("# c\n\nnes nes.pack.gz notanumber abc\nalias famicom nes\nsnes snes.pack.gz 3 ${"b".repeat(64)}\n", log)
        assertEquals(setOf("snes"), index.packs.keys)
        assertEquals(mapOf("famicom" to "nes"), index.aliases)
        assertEquals(1, log.lines.size)
    }

    @Test fun twoThreadsAskingAtOnceInstallOnce() {
        val dir = temp.newFolder()
        val bundled = FakeBundled.of("megadrive" to TestPacks.console(), "snes" to TestPacks.console(systemId = "snes"))
        val store = PackStore(dir, bundled, RecordingLog())
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val results = (0 until 16).map { i ->
            pool.submit<NamePack?> {
                start.await()
                store.packFor(if (i % 2 == 0) "megadrive" else "snes")
            }
        }
        start.countDown()
        val packs = results.map { it.get(30, TimeUnit.SECONDS) }
        pool.shutdown()
        assertTrue(packs.all { it != null })
        assertEquals(1, packs.filter { it?.systemId == "megadrive" }.distinct().size)
        assertEquals(2, bundled.opens.get())
        assertEquals(setOf("megadrive.pack", "snes.pack"), dir.list()?.toSet())
    }

    @Test fun prepareUnpacksOnlyTheSystemsPresent() {
        val bundled = FakeBundled.of(
            "megadrive" to TestPacks.console(),
            "snes" to TestPacks.console(systemId = "snes"),
            extraIndex = "alias genesis megadrive\n",
        )
        val dir = temp.newFolder()
        val ready = PackStore(dir, bundled, RecordingLog()).prepare(listOf("genesis", "megadrive", "gba"))
        assertEquals(setOf("megadrive"), ready)
        assertEquals(listOf("megadrive.pack"), dir.list()?.toList())
    }
}
