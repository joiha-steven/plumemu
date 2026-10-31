package com.plumemu.usage

import com.plumemu.telemetry.ControllerKind
import com.plumemu.telemetry.OpenFrom
import com.plumemu.telemetry.ScreenId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** app_open, app_background, screen_opened and controller_connected from the Activity's calls. */
class UsageSessionTest {
    private val sink = RecordingSink()
    private var now = 10_000L
    private val session = UsageSession(sink, clock = { now }, processStartMs = 9_000L, processStartedInForeground = true)

    @Test
    fun aProcessStartedInTheBackgroundHasNoColdStartTime() {
        // MY_PACKAGE_REPLACED or a box-art job started the process; the person taps the icon two hours later.
        val background = UsageSession(sink, clock = { now }, processStartMs = 9_000L, processStartedInForeground = false)
        now = 9_000L + 7_200_000L
        background.onActivityCreated(OpenFrom.Icon, isRecreated = false)
        background.onStart()
        background.onFirstFrame()
        assertEquals(listOf("app_open" to mapOf("from" to "icon")), sink.sent)
    }

    @Test
    fun anActivityCreatedLongAfterAForegroundProcessStartHasNoColdStartTime() {
        now = 9_000L + 15_000L
        session.onActivityCreated(OpenFrom.Icon, isRecreated = false)
        session.onStart()
        session.onFirstFrame()
        assertEquals(listOf("app_open" to mapOf("from" to "icon")), sink.sent)
    }

    @Test
    fun leavingBeforeTheFirstFrameSendsTheOpenWithoutAColdStartTime() {
        session.onActivityCreated(OpenFrom.Icon, isRecreated = false)
        session.onStart()
        now += 60_000
        session.onStop()
        assertEquals("app_open" to mapOf("from" to "icon"), sink.sent.first())
    }

    @Test
    fun aRecreatedActivityDoesNotCountItsOldIntentAgain() {
        session.onActivityCreated(OpenFrom.Icon, isRecreated = false)
        session.onStart()
        session.onFirstFrame()
        session.onResume()
        // A rotation or a theme change: the same Activity intent comes back in onCreate.
        session.onStop(isChangingConfigurations = true)
        session.onActivityCreated(OpenFrom.Icon, isRecreated = true)
        session.onStart()
        session.onResume()
        session.onStop()
        sink.events.clear()
        session.onStart() // back from a game: no new intent
        session.onResume()
        assertEquals(listOf("app_open" to mapOf("from" to "other")), sink.sent)
    }

    @Test
    fun aColdStartIsSentAtTheFirstFrameWithItsTime() {
        session.onActivityCreated(OpenFrom.HomeKey, isRecreated = false)
        session.onStart()
        session.onResume()
        assertEquals("nothing before the first frame", emptyList<Any>(), sink.sent)
        now = 10_500L
        session.onFirstFrame()
        assertEquals(listOf("app_open" to mapOf("cold_start_ms" to "1500", "from" to "home_key")), sink.sent)
        session.onFirstFrame()
        assertEquals("once", 1, sink.sent.size)
    }

    @Test
    fun aWarmStartIsSentAtTheResumeWithTheIntentThatCameBetween() {
        coldStartAndLeave()
        sink.events.clear()
        session.onStart()
        // onNewIntent comes between onStart and onResume (the Home key).
        session.openedBy(OpenFrom.HomeKey)
        session.onResume()
        assertEquals(listOf("app_open" to mapOf("from" to "home_key")), sink.sent)
        session.onResume()
        assertEquals("a second resume (a dialog) is no open", 1, sink.sent.size)
    }

    @Test
    fun theBackgroundCarriesTimeScreensAndInput() {
        session.onStart()
        session.onFirstFrame()
        session.onScreen(ScreenId.Home)
        session.onScreen(ScreenId.Home)
        session.onScreen(ScreenId.System)
        session.onGamepadInput()
        session.onTouchInput()
        now += 95_400
        session.onStop()
        assertEquals("app_background" to mapOf("seconds_in_app" to "95", "screens" to "2", "input" to "both"), sink.sent.last())
        assertEquals(
            listOf("screen_opened" to mapOf("screen" to "home"), "screen_opened" to mapOf("screen" to "system")),
            sink.sent.filter { it.first == "screen_opened" },
        )
        assertNoFreeText(sink.events)
    }

    @Test
    fun noInputIsNone() {
        session.onStart()
        session.onStop()
        assertEquals("none", sink.sent.last().second["input"])
        assertEquals("opened and left before a frame still counts the open", "app_open", sink.sent.first().first)
    }

    @Test
    fun aConfigurationChangeIsNeitherALeaveNorAnOpen() {
        coldStartAndLeave()
        sink.events.clear()
        session.onStart()
        session.onResume()
        session.onStop(isChangingConfigurations = true)
        session.onStart()
        session.onResume()
        assertEquals(listOf("app_open"), sink.sent.map { it.first })
    }

    @Test
    fun aControllerIsCountedOncePerProcess() {
        assertTrue(session.isNewController("pad-1"))
        assertFalse(session.isNewController("pad-1"))
        session.onController(ControllerKind.Xbox)
        assertEquals("controller_connected" to mapOf("kind" to "xbox"), sink.sent.single())
    }

    private enum class SetupMessageLike { FolderNotKept }

    @Test
    fun errorsAndSearchesAreSentAsCodesAndRanges() {
        session.onError(ScreenId.Setup, SetupMessageLike.FolderNotKept)
        session.onSearchUsed(queryLength = 7, results = 3, openedResult = false)
        assertEquals(
            listOf(
                "error_shown" to mapOf("where" to "setup", "code" to "folder_not_kept"),
                "search_used" to mapOf("query_length_bucket" to "6+", "results_bucket" to "1–9", "opened_result" to "no"),
            ),
            sink.sent,
        )
        assertNoFreeText(sink.events)
    }

    @Test
    fun withStatisticsAbsentNothingBreaks() {
        val quiet = UsageSession(null, { now }, 0, processStartedInForeground = true)
        quiet.onStart()
        quiet.onFirstFrame()
        quiet.onScreen(ScreenId.Apps)
        quiet.onStop()
    }

    private fun coldStartAndLeave() {
        session.onStart()
        session.onFirstFrame()
        session.onResume()
        session.onStop()
    }
}
