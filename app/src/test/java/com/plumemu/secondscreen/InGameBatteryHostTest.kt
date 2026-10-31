package com.plumemu.secondscreen

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.plumemu.R
import com.plumemu.coredata.settings.InGameSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SecondScreenSettings
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.launcher.play.battery.BatteryEstimate
import com.plumemu.launcher.play.battery.BatteryReads
import com.plumemu.launcher.play.battery.BatterySnapshot
import com.plumemu.launcher.play.battery.LevelPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A meter with fixed figures: reads every second as at the start of a stretch; counts reads and snapshots. */
private class FixedReads(var estimate: BatteryEstimate?) : BatteryReads {
    var reads = 0
    var snapshots = 0

    override fun read(): Long {
        reads++
        return 1_000
    }

    override fun stopReading() = Unit

    override fun snapshot(): BatterySnapshot {
        snapshots++
        val now = SystemClock.elapsedRealtime()
        return BatterySnapshot(estimate, null, now, listOf(LevelPoint(now - 10 * 60_000L, 70, false)))
    }
}

/**
 * The battery part through the real window: it shows under the clock card with Show
 * battery on, takes its figures without a timer of its own (after the reads, at most every 10 s once
 * there are figures), and the once-ever drift note shows for one minute and is stored.
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class InGameBatteryHostTest : SecondScreenHostTestBase() {
    private val estimate = BatteryEstimate(isCharging = false, drawMicroamps = 3_000_000, minutesLeft = 45.0, measuredMs = 600_000, fullChargeMicroampHours = 6_254_000.0)
    private val fixed = FixedReads(estimate)

    // Passed typed as the interface: a test class has no Compose stability field ($stable).
    private val reads: BatteryReads get() = fixed

    private val driftNote = RuntimeEnvironment.getApplication().getString(R.string.in_game_drift_note)
    private val measuring = RuntimeEnvironment.getApplication().getString(R.string.in_game_battery_measuring)

    private fun startAGame() {
        presence.value = Presence.Playing
        controller.pause().stop()
        idle()
    }

    private fun backToPlumemu() {
        controller.restart().resume()
        presence.value = Presence.InPlumemu
        idle()
    }

    /** What the window draws now: it is drawn first (Robolectric draws no frame by itself). */
    private fun shownTexts(host: SecondScreenHost): List<String> {
        pixelsOf(host.shownWindow!!)
        return textsOf(host.shownWindow!!)
    }

    private fun shownTags(host: SecondScreenHost): List<String> {
        pixelsOf(host.shownWindow!!)
        return nodesOf(host.shownWindow!!).mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }
    }

    private fun screen(on: Boolean) {
        shadowOf(activity.getSystemService(Context.POWER_SERVICE) as PowerManager).setIsInteractive(on)
        activity.applicationContext.sendBroadcast(Intent(if (on) Intent.ACTION_SCREEN_ON else Intent.ACTION_SCREEN_OFF))
        idle()
    }

    /** The system's minute tick, the test clock a minute on; no time passes for the reads. */
    private fun minuteTick() {
        now += 60_000L
        activity.applicationContext.sendBroadcast(Intent(Intent.ACTION_TIME_TICK))
        idle()
    }

    private fun showBattery(on: Boolean) {
        settings.value = settings.value!!.let { it.copy(homeScreen = it.homeScreen.copy(showBattery = on)) }
        idle()
    }

    /** PlumTheme's observer of Android's animator scale (core-ui `rememberSystemAnimatorScale`). */
    private fun animatorScaleObservers(): Int =
        shadowOf(RuntimeEnvironment.getApplication().contentResolver)
            .getContentObservers(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)).size

    private fun noteAlreadyShown() {
        settings.value = settings.value!!.let { it.copy(flags = it.flags.copy(inGameDriftNoteShown = true)) }
    }

    @Test
    fun theFiguresShowUnderTheClockCard() {
        noteAlreadyShown()
        val host = startHost(battery = reads)
        startAGame()
        idle(2_000)
        val texts = shownTexts(host)
        assertTrue(texts.toString(), texts.any { it.endsWith("min left") })
        assertTrue(texts.toString(), texts.any { it.startsWith("empty at ") })
        assertTrue(shownTags(host).contains(InGameBatteryTags.CHART))
    }

    @Test
    fun aFirstPlaysFirstMinuteSaysMeasuringThenTheFiguresCome() {
        noteAlreadyShown()
        fixed.estimate = null
        val host = startHost(battery = reads)
        startAGame()
        idle(2_000)
        assertTrue(shownTexts(host).contains(measuring))
        assertFalse(shownTags(host).contains(InGameBatteryTags.CHART))
        fixed.estimate = estimate
        // The next read takes them: within a second, no wait for the 10 s.
        idle(1_500)
        assertFalse(shownTexts(host).contains(measuring))
        assertTrue(shownTags(host).contains(InGameBatteryTags.CHART))
    }

    @Test
    fun showBatteryOffIsTheClockCardAloneAsBefore() {
        noteAlreadyShown()
        settings.value = settings.value!!.let { it.copy(homeScreen = it.homeScreen.copy(showBattery = false)) }
        val host = startHost(battery = reads)
        startAGame()
        idle(5_000)
        val tags = shownTags(host)
        assertTrue(tags.contains(InGameTags.BLOCK))
        assertFalse("no part, no note: $tags", tags.any { it.startsWith("in_game_battery_") })
        assertEquals("nothing read", 0, fixed.reads)
    }

    @Test
    fun blackShowsNoPart() {
        noteAlreadyShown()
        settings.value = PlumSettings(secondScreen = SecondScreenSettings(inGame = InGameSetting.Black))
        val host = startHost(battery = reads)
        startAGame()
        idle(5_000)
        assertFalse(shownTags(host).any { it.startsWith("in_game_battery_") })
        assertEquals(0, fixed.snapshots)
    }

    @Test
    fun theFiguresAreTakenAtMostEveryTenSecondsWhileReadsComeEverySecond() {
        noteAlreadyShown()
        val host = startHost(battery = reads)
        startAGame()
        idle(1_000)
        val atStart = fixed.snapshots
        val readsAtStart = fixed.reads
        idle(120_000)
        val reads = fixed.reads - readsAtStart
        val taken = fixed.snapshots - atStart
        assertTrue("reads every second: $reads", reads >= 100)
        // 12 in 2 minutes, give or take one at each end; the minute tick is the system's broadcast, not sent here.
        assertTrue("taken $taken times in 2 minutes of $reads reads", taken in 11..14)
        assertEquals(WindowMode.InGameClock, host.shownMode)
    }

    @Test
    fun theDriftNoteShowsForAMinuteTheFirstTimeAndIsStored() {
        var stored = 0
        val host =
            startHost(battery = reads, onDriftNoteShown = {
                stored++
                // As MainActivity's write does: the stored flag comes back through the settings.
                settings.value = settings.value!!.let { it.copy(flags = it.flags.copy(inGameDriftNoteShown = true)) }
            })
        startAGame()
        idle(2_000)
        assertEquals(1, stored)
        assertTrue("shown although the flag is now stored", shownTexts(host).contains(driftNote))
        idle(50_000)
        assertTrue(shownTexts(host).contains(driftNote))
        idle(10_000)
        assertFalse("gone after a minute", shownTexts(host).contains(driftNote))
        assertTrue("the key back in its place", shownTags(host).contains(InGameBatteryTags.KEY))
        backToPlumemu()
        startAGame()
        idle(2_000)
        assertFalse("never again", shownTexts(host).contains(driftNote))
        assertEquals(1, stored)
    }

    @Test
    fun theMinuteTickTakesNoFigureWhileTheScreenIsOff() {
        noteAlreadyShown()
        startHost(battery = reads)
        startAGame()
        idle(2_000)
        val beforeTick = fixed.snapshots
        minuteTick()
        assertEquals("the screen on: the tick takes the figures", beforeTick + 1, fixed.snapshots)
        // The window stays up with the screen off and the tick still comes while the CPU is awake.
        screen(on = false)
        val atOff = fixed.snapshots
        repeat(5) { minuteTick() }
        assertEquals("screen off: no figure taken on the tick", atOff, fixed.snapshots)
        screen(on = true)
        assertTrue("on again: the figures at once", fixed.snapshots > atOff)
    }

    @Test
    fun inGameBuildsNoThemeObserverWithShowBatteryOffOrOn() {
        noteAlreadyShown()
        val before = animatorScaleObservers()
        startHost(battery = reads)
        assertEquals("picking is themed (the check sees the observer)", before + 1, animatorScaleObservers())
        showBattery(false)
        startAGame()
        assertEquals("Show battery off: the clock card alone, no theme", before, animatorScaleObservers())
        showBattery(true)
        idle(2_000)
        assertTrue(shownTags(host!!).contains(InGameBatteryTags.CHART))
        assertEquals("the part takes the person's type only", before, animatorScaleObservers())
    }
}
