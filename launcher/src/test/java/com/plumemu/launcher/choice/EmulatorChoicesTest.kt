package com.plumemu.launcher.choice

import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.launcher.RecordingLog
import com.plumemu.launcher.TestCatalogs
import com.plumemu.launcher.addGame
import com.plumemu.launcher.detect.InstalledEmulators
import com.plumemu.launcher.inMemoryDatabase
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EmulatorChoicesTest {
    private lateinit var db: PlumDatabase
    private val installed = mutableSetOf<String>()
    private val catalog = TestCatalogs.small
    private val snes = checkNotNull(catalog.system("snes"))
    private lateinit var choices: EmulatorChoices
    private val dao get() = db.emulatorChoiceDao()

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        choices = EmulatorChoices(dao, catalog, { it.packageName in installed }, RecordingLog(), UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun withNothingChosenTheLaunchAsksAndTheRecommendationIsStoredButNeverStarts() = runTest {
        val game = db.addGame("snes", "primary:ROMs/snes/a.sfc")
        installed += "com.snes9x"
        assertEquals(catalog.emulator("snes9x"), (choices.resolve(game, snes) as EmulatorPick.NotChosen).recommended)
        assertEquals("snes9x", dao.systemChoice("snes")?.emulatorId)
        assertEquals(false, dao.systemChoice("snes")?.isPersonChoice)

        installed += "com.ra" // RetroArch comes before Snes9x EX+ in the priority list
        assertEquals(catalog.emulator("ra-snes9x"), (choices.resolve(game, snes) as EmulatorPick.NotChosen).recommended)
        assertEquals("ra-snes9x", dao.systemChoice("snes")?.emulatorId)

        installed.clear()
        assertTrue(choices.resolve(game, snes) is EmulatorPick.NoneInstalled)
        assertNull(dao.systemChoice("snes"))
    }

    @Test
    fun aPersonsUninstalledChoiceIsReportedAndKept() = runTest {
        val game = db.addGame("snes", "primary:ROMs/snes/a.sfc")
        installed += setOf("com.snes9x", "com.ra")
        choices.chooseForSystem(snes, "snes9x")
        installed -= "com.snes9x"
        val pick = choices.resolve(game, snes)
        assertEquals(EmulatorPick.ChosenMissing(catalog.emulator("snes9x"), "snes9x", ChoiceSource.PersonForSystem), pick)
        val row = dao.systemChoice("snes")
        assertEquals("snes9x", row?.emulatorId)
        assertEquals(true, row?.isPersonChoice)
    }

    @Test
    fun aGameChoiceOutranksTheSystemAndCanBeForgotten() = runTest {
        val game = db.addGame("snes", "primary:ROMs/snes/a.sfc")
        val other = db.addGame("snes", "primary:ROMs/snes/b.sfc")
        installed += setOf("com.snes9x", "com.ra")
        choices.chooseForSystem(snes, "ra-snes9x")
        choices.chooseForGame(game, snes, "ra-bsnes")
        val chosen = choices.resolve(game, snes) as EmulatorPick.Ready
        assertEquals(ChoiceSource.PersonForGame, chosen.source)
        assertEquals("ra-bsnes", chosen.emulator.id)
        assertEquals("ra-bsnes", dao.gameChoice(game)?.emulatorId)
        assertEquals("com.ra", (choices.resolve(other, snes) as EmulatorPick.Ready).packageName)
        choices.forgetGameChoice(game)
        assertEquals("com.ra", (choices.resolve(game, snes) as EmulatorPick.Ready).packageName)
    }

    @Test
    fun refreshingDefaultsAfterADetectionKeepsThePersonsChoice() = runTest {
        db.addGame("snes", "primary:ROMs/snes/a.sfc")
        db.addGame("usb", "primary:ROMs/usb/a.bin")
        choices.chooseForSystem(snes, "risky")
        val detected = InstalledEmulators(setOf("com.ra", "com.path.emu"), setOf("ra-snes9x", "ra-bsnes", "pathemu"))
        val inForce = choices.refreshDefaults(listOf("snes", "usb"), detected)
        assertEquals(mapOf("snes" to "risky", "usb" to "pathemu"), inForce)
        assertEquals(false, dao.systemChoice("usb")?.isPersonChoice)
        assertEquals(
            mapOf("snes" to "risky", "usb" to null),
            choices.refreshDefaults(listOf("snes", "usb"), InstalledEmulators.NONE),
        )
    }

    @Test
    fun aLegalRiskChoiceIsKeptUntilForgottenAndThenTheLaunchAsksAgain() = runTest {
        val game = db.addGame("snes", "primary:ROMs/snes/a.sfc")
        val detected = InstalledEmulators(setOf("com.ra"), setOf("ra-snes9x", "ra-bsnes"))
        choices.chooseForSystem(snes, "risky")
        assertEquals(true, dao.systemChoice("snes")?.isPersonChoice)
        choices.forgetSystemChoice(snes, detected)
        // The recommendation is stored at once (never the legal-risk one); the launch asks, it does not start it.
        assertEquals("ra-snes9x", dao.systemChoice("snes")?.emulatorId)
        assertEquals(false, dao.systemChoice("snes")?.isPersonChoice)
        installed += setOf("com.ra", "com.risky")
        val asked = choices.resolve(game, snes) as EmulatorPick.NotChosen
        assertEquals(listOf("ra-snes9x", "risky", "ra-bsnes"), asked.installed.map { it.id })
        choices.chooseForSystem(snes, "risky")
        assertEquals("risky", (choices.resolve(game, snes) as EmulatorPick.Ready).emulator.id)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aPackageThatIsNotAnEmulatorOfTheSystemIsRefused() = runTest {
        choices.chooseForSystem(snes, "pathemu")
    }
}
