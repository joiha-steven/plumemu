package com.plumemu.launcher.apps

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.plumemu.launcher.RecordingLog
import com.plumemu.launcher.play.battery.PlayBatteryMeter
import com.plumemu.launcher.play.battery.SyntheticPlay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * An app opened from the Apps entry switches the second screen to its in-game card like a game
 *, so the meter starts a new play for it: no usual pace, no level points of the game
 * before, and a fresh first minute ("Measuring battery use…"), never the previous game's figures.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InstalledAppsBatteryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    // A heavy draw, so the level moves several times during the game.
    private val play = SyntheticPlay(minutes = 60, startCharge = 3_000_000.0) { 2_500_000.0 }
    private val reader = play.reader()
    private val meter = PlayBatteryMeter(reader)
    private val apps = InstalledApps(context.packageManager, "com.plumemu", { }, RecordingLog(), UnconfinedTestDispatcher(), meter)
    private val youTube = LaunchableApp("YouTube", "com.google.android.youtube", "com.google.android.youtube.Main")
    private val maps = LaunchableApp("Maps", "com.google.android.apps.maps", "com.google.android.apps.maps.Main")

    /** Game A, measured for 20 minutes with a usual pace of its own, then back to Plumemu. */
    private fun playAGame() {
        meter.onPlayStarting(usualDrawMicroamps = 2_000_000)
        play.play(meter, reader, untilMs = 20 * MINUTE)
        val game = meter.snapshot()!!
        assertEquals(2_000_000L, game.usualDrawMicroamps)
        assertTrue("the game's line has several points: ${game.levels}", game.levels.size >= 3)
        // Back in Plumemu: the launcher takes the game's figures for its session.
        meter.takeMeasured()
    }

    @Test
    fun anAppAfterAGameHasNoUsualPaceNoOldPointsAndMeasuresAfresh() {
        playAGame()
        val launchedAt = reader.playMs
        assertEquals(AppLaunchOutcome.Started, apps.launch(youTube))
        assertNull("nothing until the app's first read", meter.snapshot())

        play.play(meter, reader, untilMs = launchedAt + 30_000)
        val firstMinute = meter.snapshot()!!
        assertNull("no usual pace for an app", firstMinute.usualDrawMicroamps)
        assertNull("its first minute says Measuring battery use…", firstMinute.estimate)
        assertEquals("one point, from the app's own first read", 1, firstMinute.levels.size)
        assertTrue("no point of the game before", firstMinute.levels.all { it.atMs >= SyntheticPlay.START_MS + launchedAt })

        play.play(meter, reader, untilMs = launchedAt + 3 * MINUTE)
        val measured = meter.snapshot()!!.estimate
        assertNotNull("after its first minute, its own figure", measured)
        assertTrue("measured over the app only: ${measured!!.measuredMs} ms", measured.measuredMs <= 3 * MINUTE)
    }

    @Test
    fun anAppRightAfterAnotherStartsItsOwnStretch() {
        playAGame()
        apps.launch(youTube)
        val launchedAt = reader.playMs
        play.play(meter, reader, untilMs = launchedAt + 5 * MINUTE)
        assertNotNull(meter.snapshot()!!.estimate)
        // Back to Plumemu (no session to record for an app), then straight into another app.
        apps.launch(maps)
        val secondAt = reader.playMs
        play.play(meter, reader, untilMs = secondAt + 30_000)
        val second = meter.snapshot()!!
        assertNull("the first app's stretch does not carry on", second.estimate)
        assertNull(second.usualDrawMicroamps)
        assertTrue("only the second app's points", second.levels.all { it.atMs >= SyntheticPlay.START_MS + secondAt })
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
