package com.plumemu.launcher

import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.launcher.apps.AppLaunchOutcome
import com.plumemu.launcher.apps.LaunchableApp
import com.plumemu.launcher.choice.ChoiceSource
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The app gets every part from one [Launching]; a choice made through it is what the launcher uses. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LaunchingTest {
    private lateinit var db: PlumDatabase

    @Before
    fun setUp() {
        db = inMemoryDatabase()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun aChoiceMadeThroughTheFacadeIsUsedByItsLauncher() = runTest {
        val catalog = TestCatalogs.small
        val launching = Launching.create(ApplicationProvider.getApplicationContext(), db, catalog)
        val id = db.addGame("snes", "primary:ROMs/snes/a.sfc")
        launching.choices.chooseForGame(id, checkNotNull(catalog.system("snes")), "snes9x")
        assertEquals(
            LaunchOutcome.EmulatorMissing(id, EmulatorOffer("snes9x", "Snes9x EX+", "com.snes9x"), ChoiceSource.PersonForGame),
            launching.launcher.launch(id),
        )
        launching.choices.forgetGameChoice(id)
        assertEquals(LaunchOutcome.NoEmulatorForSystem::class, launching.launcher.launch(id)::class)
    }

    /**
     * The observer given to [Launching.create] is the one the real starter tells: right before the
     * emulator's Intent goes out (the app's second screen depends on it; GameStartObserverTest
     * covers the wrap itself on a fake starter).
     */
    @Test
    fun theStartObserverGivenToCreateHearsTheRealStart() = runTest {
        val authority = "com.plumemu.test.documents"
        Robolectric.setupContentProvider(ScriptedDocumentsProvider::class.java, authority)
        FakePackages.install("com.snes9x", "com.snes9x.Main")
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val heard = mutableListOf<String>()
        val observer =
            object : GameStartObserver {
                override fun beforeStart() {
                    heard += "before start, ${shadowOf(app).peekNextStartedActivity()?.`package` ?: "nothing started yet"}"
                }

                override fun startFailed() {
                    heard += "start failed"
                }
            }
        val launching = Launching.create(app, db, TestCatalogs.small, observer)
        val tree = DocumentsContract.buildTreeDocumentUri(authority, "root-ok").toString()
        val id = db.addGame("snes", "present", tree)
        launching.choices.chooseForGame(id, checkNotNull(TestCatalogs.small.system("snes")), "snes9x")
        val outcome = launching.launcher.launch(id)
        assertTrue(outcome.toString(), outcome is LaunchOutcome.Started)
        val started = shadowOf(app).nextStartedActivity
        assertEquals("the emulator's Intent went out", "com.snes9x", started?.component?.packageName ?: started?.`package`)
        assertEquals(listOf("before start, nothing started yet"), heard)
    }

    /**
     * An app of the Apps entry tells the same observer (the second screen treats it like a game):
     * before its start, and that it failed when Android found nothing to start.
     */
    @Test
    fun theStartObserverGivenToCreateHearsAnAppStartAndItsFailure() {
        FakePackages.install("com.alpha", "com.alpha.Main", label = "Alpha", launcher = true)
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val heard = mutableListOf<String>()
        val observer =
            object : GameStartObserver {
                override fun beforeStart() {
                    heard += "before start, ${shadowOf(app).peekNextStartedActivity()?.component?.packageName ?: "nothing started yet"}"
                }

                override fun startFailed() {
                    heard += "start failed"
                }
            }
        val launching = Launching.create(app, db, TestCatalogs.small, observer)

        assertEquals(AppLaunchOutcome.Started, launching.apps.launch(LaunchableApp("Alpha", "com.alpha", "com.alpha.Main")))
        assertEquals("com.alpha", shadowOf(app).nextStartedActivity?.component?.packageName)
        assertEquals(listOf("before start, nothing started yet"), heard)

        heard.clear()
        // Robolectric then throws ActivityNotFoundException for an activity no package declares.
        shadowOf(app).checkActivities(true)
        assertEquals(AppLaunchOutcome.Missing, launching.apps.launch(LaunchableApp("Gone", "com.gone", "com.gone.Main")))
        assertEquals(listOf("before start, nothing started yet", "start failed"), heard)
    }

    @Test
    fun detectionPublishesToTheSharedDetector() = runTest {
        val launching = Launching.create(ApplicationProvider.getApplicationContext(), db, TestCatalogs.small)
        FakePackages.install("com.ra32", "com.ra.Retro")
        val found = launching.detector.detect(TestCatalogs.small)
        assertSame(found, launching.detector.installed.value)
        assertEquals(setOf("ra-snes9x", "ra-bsnes"), found.emulatorIds)
    }
}
