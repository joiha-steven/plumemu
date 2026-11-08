package com.plumemu.library.scan

import com.plumemu.library.corpus.RealData
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A folder named the way other frontends name it (`vb`, `32x`, `a2600`) is scanned as its system. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FolderAliasScanTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val fixture by lazy { ScanFixture(temp.root, copyFixtures = false) }

    @After
    fun tearDown() = fixture.close()

    private fun file(path: String) = File(fixture.roms, path).also { it.parentFile?.mkdirs() }.writeBytes(ByteArray(16))

    @Test
    fun aFolderNamedByAnotherFrontendBecomesItsSystem() = runTest {
        file("vb/Red Alarm.vb")
        file("32x/Knuckles Chaotix.32x")
        file("a2600/Pitfall.a26")
        fixture.roms.walkTopDown().filter { it.isDirectory }.forEach { it.setLastModified(ScanFixture.BASE_TIME) }
        fixture.scan(fixture.scanner(rules = RealData.systemRules()))
        val systems = fixture.games().associate { it.relativePath to it.systemId }
        assertEquals(
            mapOf("vb/Red Alarm.vb" to "virtualboy", "32x/Knuckles Chaotix.32x" to "sega32x", "a2600/Pitfall.a26" to "atari2600"),
            systems,
        )
    }
}
