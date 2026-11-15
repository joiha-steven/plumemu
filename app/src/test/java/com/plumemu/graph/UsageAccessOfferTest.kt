package com.plumemu.graph

import com.plumemu.coredata.settings.AdvancedSettings
import com.plumemu.launcher.choice.CoreToConfirm
import com.plumemu.launcher.play.PlaySessionOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The one-time Usage access offer: when a play raises it, when it stays quiet, what Allow and the next resume do. */
class UsageAccessOfferTest {
    @get:Rule
    val timeout = graphTestTimeout()

    private var stored = AdvancedSettings()
    private var access = false
    private var screen = true
    private val lines = mutableListOf<String>()

    private fun offer() =
        UsageAccessOffer(
            readAdvanced = { stored },
            writeAdvanced = { change -> stored = change(stored) },
            hasAccess = { access },
            canOpen = { screen },
            log = { lines += it },
        )

    private fun UsageAccessOffer.play(outcome: PlaySessionOutcome) = runBlocking { afterPlay(outcome) }

    private fun played(seconds: Long = 60, core: CoreToConfirm? = null) = PlaySessionOutcome.Recorded(1, seconds, "retroarch", core)

    @Test
    fun aPlayOfAMinuteRaisesIt() {
        val o = offer()
        o.play(played(60))
        assertEquals(true, o.isDue.value)
    }

    @Test
    fun fiftyNineSecondsDoNot() {
        val o = offer()
        o.play(played(59))
        assertEquals(false, o.isDue.value)
    }

    @Test
    fun aTooShortOrUnmeasurablePlayDoesNot() {
        val o = offer()
        o.play(PlaySessionOutcome.TooShort(1, 5, "retroarch"))
        o.play(PlaySessionOutcome.Unmeasurable(1))
        o.play(PlaySessionOutcome.Failed(1, IllegalStateException("x")))
        assertEquals(false, o.isDue.value)
    }

    @Test
    fun aPlayWithACoreQuestionWaitsAndTheNextOneOffers() {
        val o = offer()
        o.play(played(600, CoreToConfirm("com.retroarch", "snes9x")))
        assertEquals(false, o.isDue.value)
        o.play(played(600))
        assertEquals(true, o.isDue.value)
    }

    @Test
    fun accessAlreadyGrantedDoesNot() {
        access = true
        val o = offer()
        o.play(played())
        assertEquals(false, o.isDue.value)
    }

    @Test
    fun exactPlayTimeAlreadyOnDoesNot() {
        stored = AdvancedSettings(exactPlayTime = true)
        val o = offer()
        o.play(played())
        assertEquals(false, o.isDue.value)
    }

    @Test
    fun aDeviceWithoutTheScreenDoesNot() {
        screen = false
        val o = offer()
        o.play(played())
        assertEquals(false, o.isDue.value)
    }

    @Test
    fun anOfferAlreadyShownNeverComesBackEvenForAFreshObject() {
        val first = offer()
        first.play(played())
        first.shown()
        assertEquals(false, first.isDue.value)
        assertEquals(true, stored.usageAccessOffered)
        val fresh = offer()
        fresh.play(played())
        assertEquals(false, fresh.isDue.value)
    }

    @Test
    fun aFailedSettingsReadIsLoggedAndOffersNothing() {
        val o =
            UsageAccessOffer({ throw IllegalStateException("secret") }, { }, { false }, { true }, { lines += it })
        o.play(played())
        assertEquals(false, o.isDue.value)
        assertEquals(true, lines.single().contains("IllegalStateException"))
        assertEquals(false, lines.single().contains("secret"))
    }

    @Test
    fun allowThenAccessGrantedTurnsExactPlayTimeOn() {
        val o = offer()
        o.allowed()
        access = true
        o.afterResume()
        assertEquals(true, stored.exactPlayTime)
    }

    @Test
    fun allowThenNoAccessLeavesItOffAndForgetsTheWait() {
        val o = offer()
        o.allowed()
        o.afterResume()
        assertEquals(false, stored.exactPlayTime)
        // A later grant, made in Android's settings on its own, is not this offer's answer.
        access = true
        o.afterResume()
        assertEquals(false, stored.exactPlayTime)
    }

    @Test
    fun aResumeWithoutAllowChangesNothing() {
        access = true
        offer().afterResume()
        assertEquals(false, stored.exactPlayTime)
    }

    @Test
    fun aScreenThatDidNotOpenWaitsForNothing() {
        val o = offer()
        o.allowed()
        o.couldNotOpen()
        access = true
        o.afterResume()
        assertEquals(false, stored.exactPlayTime)
        assertEquals(1, lines.size)
    }
}
