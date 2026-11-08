package com.plumemu.update

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.graph.settingsUpdateActions
import com.plumemu.settings.screen.SettingsActivityActions
import com.plumemu.settings.screen.SettingsUpdateActions
import com.plumemu.update.UpdateFixtures.INSTALLED
import com.plumemu.update.UpdateFixtures.MANIFEST_URL
import com.plumemu.update.UpdateFixtures.apkBytes
import com.plumemu.update.UpdateFixtures.apkUrl
import com.plumemu.update.UpdateFixtures.manifestJson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The Update press asks for notifications (Android 13+) so "Plumemu updated, tap to open" can show; the update never waits for
 * the answer, and the panel's line says why Android asks, or that Plumemu may not open by itself when notifications are off.
 * Same fakes as [UpdatesTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class UpdatesNoticeTest {
    @get:Rule val folder = TemporaryFolder()

    private val http = FakeUpdateHttp().apply { texts[MANIFEST_URL] = manifestJson(); files[apkUrl] = apkBytes }
    private val installer = FakeInstaller()

    private var notificationsOn = false
    private var network = NetworkKind.Wifi

    /** Android's question: [answers] says what the person does; null leaves it open, so a test can look at the panel meanwhile. */
    private class Activity(val answers: (() -> Unit) -> Unit) : SettingsActivityActions by SettingsActivityActions.None {
        var asked = 0
        var screenShown = true

        override fun askNotifications(onAnswered: () -> Unit): Boolean {
            asked++
            if (screenShown) answers(onAnswered)
            return screenShown
        }
    }

    private fun TestScope.updates(sdk: Int): Updates {
        val io = StandardTestDispatcher(testScheduler)
        return Updates(
            scope = this,
            io = io,
            checkDaily = { true },
            network = { network },
            http = http,
            manifestUrl = MANIFEST_URL,
            installedVersionCode = { INSTALLED },
            deviceSdk = sdk,
            store = MemoryUpdateStore(),
            downloader = ApkDownloader(http, File(folder.root, "updates"), io),
            inspector = FakeInspector(),
            installer = installer,
            wallClock = { 1_800_000_000_000L },
            notificationsAllowed = { notificationsOn },
            info = {},
            log = {},
        )
    }

    private suspend fun TestScope.found(sdk: Int): Updates =
        updates(sdk).also {
            it.onFront().join()
            assertEquals(CheckOutcome.Found, it.view.value.lastCheck?.outcome)
        }

    private fun Updates.press(
        activity: SettingsActivityActions,
        metered: Boolean = false,
    ): SettingsUpdateActions = settingsUpdateActions(this, activity).also { it.startUpdate(metered) }

    @Test
    fun onAndroid13TheUpdatePressAsksOnceAndGrantedSaysNothing() =
        runTest {
            val updates = found(sdk = 34)
            val activity = Activity { answered -> notificationsOn = true; answered() }
            updates.press(activity)
            advanceUntilIdle()
            assertEquals(1, activity.asked)
            assertEquals("the install started", 1, installer.committed.size)
            assertEquals(NoticeLine.None, updates.view.value.notice)
        }

    @Test
    fun theLineSaysWhyWhileAndroidsQuestionIsOpen() =
        runTest {
            val updates = found(sdk = 34)
            var answer: () -> Unit = {}
            updates.press(Activity { answer = it })
            assertEquals(NoticeLine.Reason, updates.view.value.notice)
            advanceUntilIdle()
            assertEquals("the install waits under the open question", 0, installer.committed.size)
            answer()
            assertEquals(NoticeLine.NotificationsOff, updates.view.value.notice)
            advanceUntilIdle()
            assertEquals(1, installer.committed.size)
        }

    @Test
    fun refusedTheInstallStillStartsAndTheHomeLineShows() =
        runTest {
            val updates = found(sdk = 34)
            val activity = Activity { answered -> answered() }
            updates.press(activity)
            advanceUntilIdle()
            assertEquals(1, installer.committed.size)
            assertEquals(NoticeLine.NotificationsOff, updates.view.value.notice)
        }

    @Test
    fun whenAndroidShowsNoQuestionAnyMoreTheHomeLineShowsAtOnceAndNothingWaits() =
        runTest {
            val updates = found(sdk = 34)
            val activity = Activity { }.apply { screenShown = false }
            updates.press(activity)
            assertEquals(NoticeLine.NotificationsOff, updates.view.value.notice)
            advanceUntilIdle()
            assertEquals(1, installer.committed.size)
        }

    @Test
    fun theYesToMobileDataIsTheSamePressAndAsksNothingMore() =
        runTest {
            val updates = found(sdk = 34)
            val activity = Activity { answered -> answered() }
            val actions = settingsUpdateActions(updates, activity)
            actions.startUpdate(false)
            actions.startUpdate(true)
            assertEquals(1, activity.asked)
        }

    @Test
    fun belowAndroid13NothingIsAskedAndTheLineShowsOnlyWhenNotificationsAreOff() =
        runTest {
            for (sdk in listOf(28, 23)) {
                val updates = found(sdk)
                val activity = Activity { error("asked below Android 13") }
                notificationsOn = false
                updates.press(activity)
                assertEquals("sdk $sdk", 0, activity.asked)
                assertEquals("sdk $sdk", NoticeLine.NotificationsOff, updates.view.value.notice)
                notificationsOn = true
                updates.onFront().join()
                assertEquals("sdk $sdk", NoticeLine.None, updates.view.value.notice)
            }
        }

    @Test
    fun nothingShowsBeforeTheFirstPress() =
        runTest {
            val updates = found(sdk = 34)
            assertEquals(NoticeLine.None, updates.view.value.notice)
        }

    @Test
    fun aQuestionThatFailsToOpenNeverStopsTheUpdate() =
        runTest {
            val updates = found(sdk = 34)
            updates.press(object : SettingsActivityActions by SettingsActivityActions.None {
                override fun askNotifications(onAnswered: () -> Unit): Boolean = throw IllegalStateException("no screen")
            })
            advanceUntilIdle()
            assertEquals(1, installer.committed.size)
            assertEquals(NoticeLine.NotificationsOff, updates.view.value.notice)
        }

    @Test
    fun offlineAsksNothing() =
        runTest {
            val updates = found(sdk = 34)
            network = NetworkKind.None
            val activity = Activity { it() }
            updates.press(activity)
            advanceUntilIdle()
            assertEquals(0, activity.asked)
            assertEquals(NoticeLine.None, updates.view.value.notice)
        }

    @Test
    fun aPressWhileAnUpdateRunsAsksNoSecondQuestion() =
        runTest {
            val updates = found(sdk = 34)
            val activity = Activity { it() }
            updates.press(activity)
            updates.press(activity)
            advanceUntilIdle()
            assertEquals(1, activity.asked)
        }

    @Test
    fun stoppingAtInstallUnknownAppsAsksNothing() =
        runTest {
            val updates = found(sdk = 34)
            installer.canInstall = false
            val activity = Activity { it() }
            updates.press(activity)
            assertEquals(UpdateStep.NeedsPermission, updates.view.value.step)
            assertEquals("two system questions are never stacked", 0, activity.asked)
        }

    @Test
    fun onMobileDataTheQuestionAsksOnceAndTheYesAsksNoMore() =
        runTest {
            val updates = found(sdk = 34)
            network = NetworkKind.Metered
            val activity = Activity { it() }
            val actions = settingsUpdateActions(updates, activity)
            actions.startUpdate(false)
            assertEquals(UpdateStep.AskMetered, updates.view.value.step)
            assertEquals(1, activity.asked)
            actions.startUpdate(true)
            advanceUntilIdle()
            assertEquals(1, activity.asked)
            assertEquals(1, installer.committed.size)
        }

    @Test
    fun comingBackToTheFrontEndsAQuestionNoAnswerEverReaches() =
        runTest {
            val updates = found(sdk = 34)
            updates.press(Activity { })
            assertEquals(NoticeLine.Reason, updates.view.value.notice)
            updates.onFront().join()
            assertEquals(NoticeLine.NotificationsOff, updates.view.value.notice)
        }

    /** A fast download under an open question: the answer comes later, [grant] says what the person chose. */
    private suspend fun TestScope.fastDownloadUnderOpenQuestion(grant: Boolean) {
        val updates = found(sdk = 34)
        var answer: () -> Unit = {}
        updates.press(Activity { answer = it })
        advanceUntilIdle()
        // (3) The download ran meanwhile: it is done and checked, not delayed by the question.
        assertEquals(UpdateStep.Verifying, updates.view.value.step)
        // (1) The install commit waits for the answer, so Android's confirm screen cannot stack on the question.
        assertEquals(0, installer.committed.size)
        assertEquals(NoticeLine.Reason, updates.view.value.notice)
        notificationsOn = grant
        val before = testScheduler.currentTime
        answer()
        advanceUntilIdle()
        // (2) Granted or refused, the commit follows at once.
        assertEquals(1, installer.committed.size)
        assertEquals("no delay after the answer", before, testScheduler.currentTime)
    }

    @Test
    fun aFastDownloadWaitsForAGrantedAnswerThenCommits() = runTest { fastDownloadUnderOpenQuestion(grant = true) }

    @Test
    fun aFastDownloadWaitsForARefusalThenCommitsToo() = runTest { fastDownloadUnderOpenQuestion(grant = false) }

    @Test
    fun aQuestionNobodyAnswersEndsWhenPlumemuComesBackAndTheInstallGoesOn() =
        runTest {
            val updates = found(sdk = 34)
            updates.press(Activity { })
            advanceUntilIdle()
            assertEquals(0, installer.committed.size)
            updates.onFront().join()
            advanceUntilIdle()
            assertEquals(1, installer.committed.size)
        }

    @Test
    fun theLineGoesWhenNotificationsAreTurnedOnAndPlumemuResumes() =
        runTest {
            val updates = found(sdk = 34)
            updates.press(Activity { answered -> answered() })
            assertEquals(NoticeLine.NotificationsOff, updates.view.value.notice)
            notificationsOn = true
            updates.onFront().join()
            assertEquals(NoticeLine.None, updates.view.value.notice)
        }
}
