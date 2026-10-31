package com.plumemu.settings.screen

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.update.CheckFailure
import com.plumemu.update.CheckOutcome
import com.plumemu.update.LastCheck
import com.plumemu.update.UpdateManifest
import com.plumemu.update.UpdateStep
import com.plumemu.update.UpdateView
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A press is never met with nothing: what the About rows and panels say while a check
 * or a download runs, when Android has no permission screen, and when a check fails for no network reason.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsUpdateTextsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val manifest =
        UpdateManifest(41, "0.0.3-alpha", "https://plumemu-app.stevensdesk.com/update/a.apk", 3, "0".repeat(64), 23, "2026-10-23", emptyList(), null)

    private fun texts(view: UpdateView): Triple<String, String?, String?> {
        var result: Triple<String, String?, String?>? = null
        compose.setContent { result = Triple(checkRowValue(view), checkLineText(view), updateStepText(view)) }
        compose.waitForIdle()
        return result!!
    }

    @Test
    fun checkPressedDuringADownloadSaysAnUpdateIsDownloading() {
        val (row, line, _) = texts(UpdateView(found = manifest, step = UpdateStep.Downloading(1, 3)))
        assertEquals("Update downloading", row)
        assertEquals("An update is downloading. Check again when it is done.", line)
    }

    @Test
    fun theUpdatePanelSaysCheckingWhileACheckRuns() {
        val (_, _, step) = texts(UpdateView(found = manifest, isChecking = true))
        assertEquals("Checking for updates…", step)
    }

    @Test
    fun withNoPermissionScreenThePanelSaysWhatToDo() {
        val (_, _, step) = texts(UpdateView(found = manifest, step = UpdateStep.NoPermissionScreen))
        assertEquals(true, step?.startsWith("This device has no screen Plumemu can open for this."))
    }

    @Test
    fun aFoundUpdateIsNamedWithItsBuildNumber() {
        // A release can be rebuilt under the same version name; the build number tells them apart.
        val (_, line, _) = texts(UpdateView(found = manifest, lastCheck = LastCheck(1_768_471_200_000, CheckOutcome.Found)))
        assertEquals(true, line?.endsWith("Plumemu 0.0.3-alpha (41) is available."))
    }

    @Test
    fun updateAvailableShowsTheBuildNumberToo() {
        var value: String? = null
        compose.setContent { value = updateRowValue(UpdateView(found = manifest)) }
        compose.waitForIdle()
        assertEquals(true, value?.startsWith("0.0.3-alpha (41)"))
    }

    @Test
    fun anUnexpectedFailureHasItsOwnLine() {
        val (_, line, _) = texts(UpdateView(lastCheck = LastCheck(1_768_471_200_000, CheckOutcome.Failed(CheckFailure.Unexpected))))
        assertEquals(true, line?.endsWith("The update check failed. Try again later."))
    }
}
