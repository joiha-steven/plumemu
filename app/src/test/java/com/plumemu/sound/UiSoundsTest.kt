package com.plumemu.sound

import com.plumemu.coredata.settings.AdvancedSettings
import com.plumemu.coredata.settings.SoundLevel
import org.junit.Assert.assertEquals
import org.junit.Test

/** The player's rules (docs/design/rules.md › Sound) and the one place that says what an action did. */
internal class UiSoundsTest {
    private val heard = Heard()
    private val sounds = heard.uiSounds

    @Test
    fun heldRepeatPlaysAtMostOneMoveEveryFiftyMilliseconds() {
        repeat(10) {
            sounds.playFor(Outcome.Moved)
            heard.now += 20
        }
        // 10 presses 20 ms apart: the ticks land at 0, 60, 120 and 180 ms.
        assertEquals(List(4) { UiSound.Move }, heard.drain())
        heard.now += UiSounds.MOVE_GAP_MS
        sounds.playFor(Outcome.Moved)
        assertEquals(listOf(UiSound.Move), heard.drain())
    }

    @Test
    fun outcomesMapToTheirSoundAndNothingIsSilent() {
        sounds.playFor(Outcome.Nothing)
        sounds.playFor(Outcome.Activated)
        sounds.playFor(Outcome.WentBack)
        assertEquals(listOf(UiSound.Confirm, UiSound.Back), heard.drain())
    }

    @Test
    fun nothingPlaysWhenSoundsAreOff() {
        sounds.level = SoundLevel.Off
        sounds.playFor(Outcome.Moved)
        sounds.playFor(Outcome.Activated)
        sounds.playFor(Outcome.WentBack)
        assertEquals(emptyList<UiSound>(), heard.drain())
        sounds.level = SoundLevel.Loud
        sounds.playFor(Outcome.Activated)
        assertEquals(listOf(UiSound.Confirm), heard.drain())
    }

    @Test
    fun eachLevelPlaysAtItsOwnShareOfTheVolume() {
        sounds.level = SoundLevel.Quiet
        sounds.playFor(Outcome.Activated)
        assertEquals(listOf(0.35f), heard.volumes)
        heard.drain()
        sounds.level = SoundLevel.Loud
        sounds.playFor(Outcome.Activated)
        assertEquals(listOf(1f), heard.volumes)
    }

    @Test
    fun followTakesTheLevelAndTheVibrationSwitchFromTheSettings() {
        sounds.follow(AdvancedSettings(sounds = SoundLevel.Quiet, vibration = false))
        assertEquals(SoundLevel.Quiet, sounds.level)
        assertEquals(false, sounds.vibration)
    }

    @Test
    fun aMoveTicksAConfirmClicksAndBackIsSilentToTheHand() {
        sounds.playFor(Outcome.Moved)
        sounds.playFor(Outcome.Activated)
        sounds.playFor(Outcome.WentBack)
        assertEquals(listOf("tick", "click"), heard.drainFelt())
    }

    @Test
    fun aHeldDpadTicksAtMostOncePerThirtyFiveMilliseconds() {
        repeat(10) {
            sounds.playFor(Outcome.Moved)
            heard.now += 20
        }
        // Presses 20 ms apart: a tick at 0, 40, 80, 120, 160 ms (the one at 20 is inside the gap).
        assertEquals(List(5) { "tick" }, heard.drainFelt())
        sounds.playFor(Outcome.Activated)
        sounds.playFor(Outcome.Activated)
        assertEquals("a confirm is never throttled by moves", listOf("click", "click"), heard.drainFelt())
    }

    @Test
    fun vibrationOffFeelsNothingButStillSoundsAndSoundsOffStillVibrates() {
        sounds.vibration = false
        sounds.playFor(Outcome.Moved)
        sounds.playFor(Outcome.Activated)
        assertEquals(emptyList<String>(), heard.drainFelt())
        assertEquals(listOf(UiSound.Move, UiSound.Confirm), heard.drain())
        sounds.vibration = true
        sounds.level = SoundLevel.Off
        heard.tick()
        sounds.playFor(Outcome.Activated)
        assertEquals(listOf("click"), heard.drainFelt())
        assertEquals(emptyList<UiSound>(), heard.drain())
    }

    @Test
    fun soundsAndVibrationAreOnByDefault() {
        assertEquals(SoundLevel.Loud, AdvancedSettings().sounds)
        assertEquals(true, AdvancedSettings().vibration)
    }

    @Test
    fun stopReleasesThePlayerAndLaterCallsAreSilent() {
        sounds.stop()
        assertEquals(1, heard.released)
        sounds.playFor(Outcome.Activated)
        assertEquals(emptyList<UiSound>(), heard.drain())
    }

    @Test
    fun feelSendsTheStrongestReportOnceWhenTheHandlerReturns() {
        val reported = Reported()
        val feel = Feel().also(reported::listenTo)
        feel.report(Outcome.Activated) // outside a handler: a slow answer, ignored
        feel.act {
            feel.report(Outcome.Moved)
            feel.report(Outcome.Activated)
            feel.report(Outcome.WentBack) // same rank as Activated: the first one stays
            feel.report(Outcome.Moved)
        }
        feel.act { }
        assertEquals(listOf(Outcome.Activated), reported.drain())
    }

    @Test
    fun hushSilencesTheWholeAction() {
        val reported = Reported()
        val feel = Feel().also(reported::listenTo)
        feel.act {
            feel.report(Outcome.Activated)
            feel.hush()
        }
        assertEquals(emptyList<Outcome>(), reported.drain())
    }
}
