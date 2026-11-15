package com.plumemu.identify

import com.plumemu.identify.pack.FakeBundled
import com.plumemu.identify.pack.PackStore
import com.plumemu.identify.pack.TestPacks
import com.plumemu.identify.source.RandomAccessSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LaunchFileSystemsTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `steam, epic and windows hold launcher files only`() {
        assertEquals(setOf("steam", "epic", "windows"), LaunchFileSystems.IDS)
        listOf("steam", "epic", "windows", "EPIC").forEach { assertTrue(it, LaunchFileSystems.isLaunchFile(it)) }
    }

    @Test fun `the DOSBox Windows systems and dos keep real game files`() {
        listOf("windows3x", "windows9x", "dos", "pc", "snes").forEach { assertFalse(it, LaunchFileSystems.isLaunchFile(it)) }
    }

    @Test fun `an epic or gog id file is never opened and is named by its file name`() {
        val identifier = Identifier(PackStore(temp.newFolder(), FakeBundled.of("arcade" to TestPacks.arcade())))
        for ((system, file) in listOf("epic" to "Fortnite.epic", "windows" to "Hades.gog", "windows" to "Celeste.epic")) {
            val source = object : RandomAccessSource {
                override val size = 9L
                override fun readAt(position: Long, dst: ByteArray, offset: Int, length: Int): Int =
                    throw AssertionError("$file was opened")
                override fun close() = Unit
            }
            val result = identifier.identify(IdentifyRequest(system, file, file.substringBeforeLast('.'), source = source))
            assertEquals(file, file.substringBeforeLast('.'), result.title)
        }
    }
}
