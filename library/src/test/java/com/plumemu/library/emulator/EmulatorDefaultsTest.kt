package com.plumemu.library.emulator

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.PlumDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EmulatorDefaultsTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java).build()
    private val defaults = EmulatorDefaults(db.emulatorChoiceDao())
    private val priorities = mapOf(
        "snes" to listOf("com.retroarch", "com.snes9x"),
        "psx" to listOf("com.duckstation", "com.retroarch"),
        "n64" to listOf("org.mupen64plus"),
    )

    @After
    fun tearDown() = db.close()

    @Test
    fun eachSystemGetsItsFirstInstalledEmulatorOrNone() = runTest {
        val result = defaults.apply(priorities, setOf("com.snes9x", "com.retroarch"))
        assertEquals(mapOf("snes" to "com.retroarch", "psx" to "com.retroarch", "n64" to null), result)
        assertEquals("com.retroarch", db.emulatorChoiceDao().systemChoice("psx")?.emulatorId)
    }

    @Test
    fun aPersonsChoiceIsKeptWhenDefaultsAreAppliedAgain() = runTest {
        defaults.apply(priorities, setOf("com.retroarch"))
        db.emulatorChoiceDao().setPersonSystemChoice("snes", "com.snes9x")
        val result = defaults.apply(priorities, setOf("com.retroarch", "com.snes9x", "com.duckstation"))
        assertEquals("com.snes9x", result["snes"])
        assertEquals("com.duckstation", result["psx"])
        assertEquals(true, db.emulatorChoiceDao().systemChoice("snes")?.isPersonChoice)
    }

    @Test
    fun uninstalledDefaultIsClearedButAPersonsChoiceIsNot() = runTest {
        defaults.apply(priorities, setOf("com.retroarch"))
        db.emulatorChoiceDao().setPersonSystemChoice("psx", "com.duckstation")
        val result = defaults.apply(priorities, emptySet())
        assertEquals(null, result["snes"])
        assertEquals("com.duckstation", result["psx"])
    }
}
