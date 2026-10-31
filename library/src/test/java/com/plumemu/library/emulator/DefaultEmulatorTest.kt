package com.plumemu.library.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultEmulatorTest {
    private val priority = listOf("com.retroarch", "com.explusalpha.Snes9xPlus", "com.github.stenzek.duckstation")

    @Test
    fun firstInstalledInPriorityOrderWins() {
        assertEquals("com.retroarch", DefaultEmulator.pick(priority, setOf("com.github.stenzek.duckstation", "com.retroarch")))
        assertEquals("com.explusalpha.Snes9xPlus", DefaultEmulator.pick(priority, setOf("com.explusalpha.Snes9xPlus", "x")))
    }

    @Test
    fun noneInstalledOrEmptyPriorityGivesNone() {
        assertNull(DefaultEmulator.pick(priority, emptySet()))
        assertNull(DefaultEmulator.pick(priority, setOf("com.other")))
        assertNull(DefaultEmulator.pick(emptyList(), setOf("com.retroarch")))
    }
}
