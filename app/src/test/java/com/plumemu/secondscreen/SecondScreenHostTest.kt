package com.plumemu.secondscreen

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.SecondScreenGameCardDimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDisplayManager
import kotlin.math.abs

/**
 * The second screen's card on a Robolectric display: when it is started and closed, one instance,
 * Plumemu as the default home or not, a start that throws, its own lifecycle (the card keeps drawing
 * while the top screen's Activity is stopped), and the size class at the Thor's 538x468 dp.
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenHostTest : SecondScreenHostTestBase() {
    /** Android's alias setting as the fake [SecondScreenHome] writes it. */
    private val aliasWrites = mutableListOf<Boolean>()
    private var isDefaultHome = true

    private fun fakeHome(enabledAtStart: Boolean = false) =
        SecondScreenHome(
            activity.lifecycleScope,
            isDefaultHome = { isDefaultHome },
            readEnabled = { enabledAtStart },
            writeEnabled = { aliasWrites += it },
            io = Dispatchers.Main,
            afterDisabled = SecondScreenHome::endUnshownCard,
            main = Dispatchers.Main,
        )

    @Test
    fun startsTheCardOnTheScreenWhileItIsOn() {
        val host = startHost()
        assertEquals(displayId, host.shownDisplayId)
        assertEquals("one start, on that display", listOf(displayId), starts)
        val card = host.shownWindow
        assertNotNull("the card was made: $logs", card)
        assertEquals("on the second display", displayId, card!!.displayIdNow())
        assertTrue("the feed follows", feed.isFollowing.value)
        assertEquals("Second screen card: started on display $displayId (switched on)", logs.first())
    }

    @Test
    fun noCardForAnUnsureScreenUntilSwitchedOn() {
        val host = startHost(kind = ScreenKind.Unsure)
        assertNull(host.shownDisplayId)
        assertTrue("nothing started", starts.isEmpty())
        assertFalse(feed.isFollowing.value)
        settings.value = on(true)
        idle()
        assertEquals(displayId, host.shownDisplayId)
    }

    @Test
    fun benchmarkBuildsShowItOnAnUnsureScreen() {
        val host = startHost(kind = ScreenKind.Unsure, forcedOn = true)
        assertEquals(displayId, host.shownDisplayId)
        assertTrue(logs.first(), logs.first().endsWith("(benchmark)"))
    }

    @Test
    fun noCardBeforeTheSettingsArrive() {
        settings.value = null
        val host = startHost()
        assertNull(host.shownDisplayId)
        settings.value = PlumSettings()
        idle()
        assertEquals(displayId, host.shownDisplayId)
    }

    @Test
    fun aSingleScreenDeviceStartsNothingAndEnablesNothing() {
        val home = fakeHome()
        val made =
            SecondScreenHost(activity, activity.lifecycleScope, MutableStateFlow(null), settings, feed, presence, 0, startCard = makeCard, home = home)
        host = made
        made.start()
        idle()
        assertNull(made.shownDisplayId)
        assertTrue("no start", starts.isEmpty())
        assertTrue("the alias left as the manifest has it", aliasWrites.isEmpty())
        assertFalse(home.isOn)
    }

    @Test
    @Config(sdk = [28])
    fun beforeAndroid10NothingIsStartedOrEnabled() {
        isDefaultHome = true
        val home = fakeHome()
        val host = startHost(home = home)
        assertNull(host.shownDisplayId)
        assertTrue("no card: MainActivity would be paused and never get the controller back", starts.isEmpty())
        assertTrue("the alias stays off", aliasWrites.isEmpty())
        assertFalse(home.isOn)
    }

    @Test
    fun aCardOnAThirdDisplayStaysBlankAndNeverReplacesTheBottomOne() {
        isDefaultHome = true
        val host = startHost(home = fakeHome())
        val bottom = host.shownWindow!!
        val third = ShadowDisplayManager.addDisplay("w400dp-h300dp-mdpi")
        val other = buildCard(id = third)
        idle()
        assertFalse("the bottom card stays", bottom.isFinishing)
        assertSame(bottom, CardInstances.live)
        assertFalse("the other one is not closed in a loop either", other.isFinishing)
        assertTrue("blank: no Compose content", findView(other.window.decorView) { it is androidx.compose.ui.platform.ComposeView } == null)
        // Android makes it again on that display: still nothing replaced.
        val again = buildCard(id = third)
        idle()
        assertSame(bottom, CardInstances.live)
        assertFalse(again.isFinishing)
        // The alias goes off: Android's own homes come back there.
        settings.value = on(false)
        idle()
        assertTrue(other.isFinishing)
        assertTrue(again.isFinishing)
    }

    @Test
    fun switchedOffClosesIt() {
        val host = startHost()
        val card = host.shownWindow!!
        settings.value = on(false)
        idle()
        assertNull(host.shownDisplayId)
        assertTrue("closed", card.isFinishing)
        assertNull(CardInstances.live)
        assertFalse(feed.isFollowing.value)
        assertEquals("Second screen card: closed on display $displayId (switched off)", logs.last())
    }

    @Test
    fun noSecondScreenClosesIt() {
        val host = startHost()
        val card = host.shownWindow!!
        screens.value = null
        idle()
        assertNull(host.shownDisplayId)
        assertTrue(card.isFinishing)
        assertEquals("Second screen card: closed on display $displayId (no second screen)", logs.last())
    }

    @Test
    fun stopClosesItWhenPlumemuIsNotTheHome() {
        val host = startHost()
        val card = host.shownWindow!!
        host.stop()
        idle()
        assertTrue(card.isFinishing)
        assertEquals("Second screen card: closed on display $displayId (activity destroyed)", logs.last())
        // Stopped: changes no longer bring it back.
        settings.value = on(true)
        idle()
        assertNull(host.shownDisplayId)
        assertEquals(1, starts.size)
    }

    @Test
    fun repeatedChangesNeverStartASecondCard() {
        val host = startHost()
        presence.value = Presence.Playing
        idle()
        presence.value = Presence.InPlumemu
        idle()
        settings.value = on(true)
        idle()
        assertEquals("one start: $logs", listOf(displayId), starts)
        assertEquals(1, cards.count { !it.get().isFinishing })
        assertSame(CardInstances.live, host.shownWindow)
    }

    @Test
    fun aCardAndroidMakesAsHomeReplacesTheOlderOneSoOneRemains() {
        val host = startHost()
        val first = host.shownWindow!!
        // Home pressed on the bottom display, twice: the mode is standard, so Android makes new instances.
        val second = buildCard()
        val third = buildCard()
        idle()
        assertTrue("the first closes", first.isFinishing)
        assertTrue("the second closes", second.isFinishing)
        assertFalse(third.isFinishing)
        assertSame(third, CardInstances.live)
        assertSame("the host's content goes on in the newest", third, host.shownWindow)
        assertEquals("no start from Plumemu for those", 1, starts.size)
    }

    @Test
    fun aRecreatedTopScreenFindsTheLiveCardAndStartsNoOther() {
        isDefaultHome = true
        val old = startHost(home = fakeHome())
        val card = old.shownWindow!!
        assertEquals("enabled once", listOf(true), aliasWrites)
        // MainActivity recreated: the old host stops, the new one starts.
        old.stop()
        idle()
        assertFalse("kept while it is the bottom display's home: $logs", card.isFinishing)
        val newer =
            SecondScreenHost(activity, activity.lifecycleScope, screens, settings, feed, presence, 0, startCard = makeCard, home = fakeHome(enabledAtStart = true), info = { logs += "new: $it" })
        others += newer
        newer.start()
        idle()
        assertEquals(displayId, newer.shownDisplayId)
        assertSame("the same card", card, newer.shownWindow)
        assertEquals("no second start: $logs", 1, starts.size)
    }

    @Test
    fun aRecreatedTopScreenStillLeavesOneCardWhenPlumemuIsNotTheHome() {
        val old = startHost()
        old.stop()
        idle()
        val newer = SecondScreenHost(activity, activity.lifecycleScope, screens, settings, feed, presence, 0, startCard = makeCard)
        others += newer
        newer.start()
        idle()
        assertEquals(2, starts.size)
        assertEquals("one live card", 1, cards.count { !it.get().isFinishing })
        assertSame(CardInstances.live, newer.shownWindow)
    }

    @Test
    fun theDefaultHomeEnablesTheAliasAndKeepsTheCardBlankWhenPlumemuIsLeft() {
        isDefaultHome = true
        val host = startHost(home = fakeHome())
        val card = host.shownWindow!!
        assertEquals(listOf(true), aliasWrites)
        presence.value = Presence.Away
        idle()
        assertFalse("Android would only make it again: kept", card.isFinishing)
        assertNull("nothing of Plumemu's", SecondScreenHost.cardHolder.value)
        assertNull(host.shownDisplayId)
        assertTrue(logs.toString(), "Second screen card: blank on display $displayId (Plumemu left)" in logs)
        presence.value = Presence.InPlumemu
        idle()
        assertSame("back on the same card", card, host.shownWindow)
        assertEquals(1, starts.size)
        assertEquals("written only when it changed", listOf(true), aliasWrites)
    }

    @Test
    fun notTheDefaultHomeLeavesTheAliasOffAndClosesTheCardWhenPlumemuIsLeft() {
        isDefaultHome = false
        val host = startHost(home = fakeHome())
        val card = host.shownWindow!!
        assertTrue("nothing registers as a home", aliasWrites.isEmpty())
        presence.value = Presence.Away
        idle()
        assertTrue("closed as the window was dismissed", card.isFinishing)
        assertEquals("Second screen card: closed on display $displayId (Plumemu left)", logs.last())
        presence.value = Presence.InPlumemu
        idle()
        assertEquals("started again with Plumemu", 2, starts.size)
        assertNotNull(host.shownWindow)
    }

    @Test
    fun switchedOffDisablesTheAliasAndClosesTheCard() {
        isDefaultHome = true
        val host = startHost(home = fakeHome())
        val card = host.shownWindow!!
        settings.value = on(false)
        idle()
        assertEquals(listOf(true, false), aliasWrites)
        assertTrue(card.isFinishing)
        assertNull(CardInstances.live)
    }

    @Test
    fun aCardAndroidMadeAsHomeJustBeforeTheAliasWentOffIsClosed() {
        isDefaultHome = true
        startHost(home = fakeHome())
        settings.value = on(false)
        // Android made the card again before the write landed; no host shows it.
        val late = buildCard()
        idle()
        assertTrue("closed once the alias is off: $logs", late.isFinishing)
    }

    @Test
    fun aCardOpenedOnTheTopScreenClosesItself() {
        val card = buildCard(id = android.view.Display.DEFAULT_DISPLAY)
        assertTrue(card.isFinishing)
        assertNull(CardInstances.live)
    }

    @Test
    fun aRemovedDisplayTakesTheCardAndNoneComesBack() {
        val host = startHost()
        val card = cards.single()
        ShadowDisplayManager.removeDisplay(displayId)
        // Android ends an activity whose display went.
        card.pause().stop().destroy()
        idle()
        assertNull(host.shownDisplayId)
        assertTrue(logs.toString(), "Second screen card: gone from display $displayId (the card was closed by Android)" in logs)
        assertEquals("the new attempt finds the display gone", "Second screen card: none, display $displayId is gone (started again after: the card was closed by Android)", logs.last())
        assertEquals(1, starts.size)
    }

    @Test
    fun aCardClosedByAndroidIsStartedAgainAFewTimesAtMost() {
        val host = startHost()
        repeat(SecondScreenHost.SHOW_ATTEMPTS + 2) {
            cards.last().takeIf { !it.get().isDestroyed }?.pause()?.stop()?.destroy()
            idle()
        }
        assertEquals("the first and ${SecondScreenHost.SHOW_ATTEMPTS} more: $logs", SecondScreenHost.SHOW_ATTEMPTS + 1, starts.size)
        assertTrue(logs.toString(), logs.any { it.startsWith("W: Second screen card: gone") })
        assertNull(host.shownDisplayId)
    }

    @Test
    fun aNewActivitysHostWaitsForTheOldCardToBeLetGo() {
        val old = startHost()
        val oldLogs = logs.toList()
        val newer =
            SecondScreenHost(activity, activity.lifecycleScope, screens, settings, SecondScreenFeed(activity.lifecycleScope), presence, 0, startCard = makeCard, info = { logs += "new: $it" })
        others += newer
        newer.start()
        idle()
        assertEquals("the old one keeps it", displayId, old.shownDisplayId)
        assertNull("the new one waits", newer.shownDisplayId)
        assertEquals(oldLogs, logs)
        old.stop()
        idle()
        assertEquals("the new one shows once the old one let go", displayId, newer.shownDisplayId)
        assertEquals("new: Second screen card: started on display $displayId (switched on)", logs.last { it.startsWith("new: Second screen card: started") || it.startsWith("new: Second screen card: shown") })
        newer.stop()
    }

    @Test
    fun anActivityInFrontTakesTheCardFromOneLeftInGame() {
        // Two Activities at once: one opened from the launcher icon left a game running, then Home made another.
        val old = startHost()
        presence.value = Presence.Playing
        idle()
        assertEquals(WindowMode.InGameClock, old.shownMode)
        val front = MutableStateFlow(Presence.InPlumemu)
        val newer = SecondScreenHost(activity, activity.lifecycleScope, screens, settings, SecondScreenFeed(activity.lifecycleScope), front, 0, startCard = makeCard)
        others += newer
        newer.start()
        idle()
        assertNull("the one left behind lets go", old.shownDisplayId)
        assertEquals("the one in front picks", WindowMode.Picking, newer.shownMode)
        front.value = Presence.Away
        presence.value = Presence.InPlumemu
        idle()
        assertNull("the other way round too", newer.shownDisplayId)
        assertEquals(WindowMode.Picking, old.shownMode)
        newer.stop()
    }

    @Test
    fun aTapIsTakenAndDoesNothing() {
        val host = startHost()
        val card = host.shownWindow!!
        val down = android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_DOWN, 200f, 200f, 0)
        assertTrue("taken", card.dispatchTouchEvent(down))
        assertEquals(displayId, host.shownDisplayId)
        assertFalse(card.isFinishing)
    }

    @Test
    fun aStartThatAlwaysThrowsIsTriedAFewTimesThenLeavesNoCard() {
        val host = startHost(startCard = { throw SecurityException("test") })
        assertNull(host.shownDisplayId)
        assertFalse(feed.isFollowing.value)
        idle(SecondScreenHost.SHOW_RETRY_MS * SecondScreenHost.SHOW_ATTEMPTS * 2)
        val tries = "W: Second screen card: could not start on display $displayId (SecurityException)"
        assertEquals(List(SecondScreenHost.SHOW_ATTEMPTS - 1) { "$tries, trying again" } + tries, logs)
        assertNull(host.shownDisplayId)
        assertNull("nothing held", SecondScreenHost.cardHolder.value)
    }

    @Test
    fun aRefusedStartSucceedsOnALaterTry() {
        var refusals = 2
        val host =
            startHost(startCard = {
                if (refusals-- > 0) throw IllegalArgumentException("test")
                makeCard(it)
            })
        assertNull(host.shownDisplayId)
        idle(SecondScreenHost.SHOW_RETRY_MS * 3)
        assertEquals("shown on the third try: $logs", displayId, host.shownDisplayId)
        assertEquals("Second screen card: started on display $displayId (switched on)", logs.last())
    }

    @Test
    fun afterTheQuickTriesAreUsedUpADisplayChangeTriesAgain() {
        var refuse = true
        val host =
            startHost(startCard = {
                if (refuse) throw SecurityException("test")
                makeCard(it)
            })
        idle(SecondScreenHost.SHOW_RETRY_MS * SecondScreenHost.SHOW_ATTEMPTS * 2)
        assertNull("quick tries used up: $logs", host.shownDisplayId)
        refuse = false
        ShadowDisplayManager.changeDisplay(displayId, "w600dp-h468dp-xhdpi")
        idle()
        assertEquals("shown on the display change: $logs", displayId, host.shownDisplayId)
        assertTrue(logs.toString(), logs.last().startsWith("Second screen card: started on display $displayId (late try"))
    }

    @Test
    fun afterTheQuickTriesAreUsedUpASlowTimerTriesAgainWithNoDisplayEvent() {
        var refusals = SecondScreenHost.SHOW_ATTEMPTS + 1
        val host =
            startHost(startCard = {
                if (refusals-- > 0) throw SecurityException("test")
                makeCard(it)
            })
        idle(SecondScreenHost.SHOW_RETRY_MS * SecondScreenHost.SHOW_ATTEMPTS * 2)
        assertNull(host.shownDisplayId)
        idle(SecondScreenHost.LATE_RETRY_MS * 3)
        assertEquals("shown by the timer: $logs", displayId, host.shownDisplayId)
        assertEquals("one card, no more tries once shown", listOf(displayId), starts)
    }

    @Test
    fun theLateTriesStopWhenTheWantedStateChanges() {
        var tries = 0
        val host = startHost(startCard = { tries++; throw SecurityException("test") })
        idle(SecondScreenHost.SHOW_RETRY_MS * SecondScreenHost.SHOW_ATTEMPTS * 2)
        settings.value = on(false)
        idle()
        val before = tries
        idle(SecondScreenHost.LATE_RETRY_MS * 3)
        ShadowDisplayManager.changeDisplay(displayId, "w600dp-h468dp-xhdpi")
        idle()
        assertEquals("no try while switched off", before, tries)
        assertNull(host.shownDisplayId)
    }

    @Test
    fun aCardShownAgainStartsCalmNotWithTheCardOfBefore() {
        val host = startHost()
        feed.offer(SecondScreenContent.Game("Super Metroid", "Super Nintendo"))
        idle(SETTLED_MS)
        assertTrue("Super Metroid" in textsOf(host.shownWindow!!).joinToString())
        settings.value = on(false)
        idle()
        assertNull(host.shownDisplayId)
        settings.value = on(true)
        idle()
        val texts = textsOf(host.shownWindow!!).joinToString()
        assertFalse("no old card: $texts", "Super Metroid" in texts)
        assertEquals(SecondScreenContent.Calm, feed.content.value)
    }

    @Test
    fun theCardStaysResumedAndRecomposesWhileTheTopScreenIsStopped() {
        val host = startHost()
        val card = host.shownWindow!!
        feed.offer(SecondScreenContent.Game("Super Metroid", "Super Nintendo"))
        idle(SETTLED_MS)
        assertTrue(textsOf(card).toString(), "Super Metroid" in textsOf(card).joinToString())
        controller.pause().stop()
        idle()
        assertEquals(Lifecycle.State.CREATED, activity.lifecycle.currentState)
        assertEquals("the card's own lifecycle", Lifecycle.State.RESUMED, lifecycleOf(card))
        assertEquals("the card does not follow the Activity's stop by itself", displayId, host.shownDisplayId)
        feed.offer(SystemEntry("md", "Mega Drive", 12))
        idle(SETTLED_MS)
        val texts = textsOf(card).joinToString()
        assertTrue("recomposed while stopped: $texts", "Mega Drive" in texts)
        assertFalse(texts, "Super Metroid" in texts)
        assertTrue("the new card is drawn: ${brightest(card)}", brightest(card) > 0.5f)
    }

    /** The card draws at its own display's size and density (Robolectric's display context keeps the application's density). */
    @Test
    @Config(qualifiers = "w768dp-h432dp-land-369dpi")
    fun theThorsBottomScreenIsRoomy() {
        val card = startHost().shownWindow!!
        feed.offer(SecondScreenContent.Game("Super Metroid", "Super Nintendo"))
        idle(SETTLED_MS)
        val view = composeViewOf(card)
        val density = view.resources.displayMetrics.density
        assertEquals("the second display's density (369 dpi)", 369f / 160f, density, 0.01f)
        assertEquals("538 dp wide", 538f, view.width / density, 1f)
        assertEquals("468 dp high", 468f, view.height / density, 1f)
        val tile = nodesOf(card).first { it.config.getOrNull(SemanticsProperties.TestTag) == GameCardTags.CARD }
        val leftDp = tile.boundsInRoot.left / density
        assertTrue("the game card's 24 dp side margin: $leftDp", abs(leftDp - SecondScreenGameCardDimens.marginSide.value) < 1f)
    }
}
