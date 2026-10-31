package com.plumemu.secondscreen

import android.util.Log
import com.plumemu.coreui.theme.PlumMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the second screen's window shows, handed from the top screen's effects to the window's
 * composition. The top screen [offer]s each new
 * content; [content] is what the window draws, after the settle rule. One per Activity, main
 * thread only ([offer], [setFollowing]); [content] and [isFollowing] may be read from anywhere.
 *
 * The settle rule: every move,
 * a single press or a held direction, reaches the window only once the cursor has rested
 * [SETTLE_MS], so the second screen adds no work to the top screen's frames while it moves. Then
 * the play facts and cover record are read off the main thread ([facts]) and every card (Calm too)
 * shows after the read, at most [FILL_WAIT_MS] later, at once (no fade) and in one change; a
 * slower read shows the card without them and the answer replaces it, unless the cursor has moved.
 */
internal class SecondScreenFeed(
    private val scope: CoroutineScope,
    /** Fills in a card's play facts ([SecondScreenPlayFacts.fill]); null: cards are shown as offered. */
    private val facts: (suspend (SecondScreenContent) -> SecondScreenContent?)? = null,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val warn: (String) -> Unit = { Log.w(SecondScreenHost.TAG, it) },
) {
    private val shown = MutableStateFlow<SecondScreenContent>(SecondScreenContent.Calm)
    private val following = MutableStateFlow(false)
    private val windowShown = MutableStateFlow(false)

    /** The offer [shown] was made from (before its facts were filled in); null while Calm from a reset. */
    private var shownFrom: SecondScreenContent? = null
    private var pending: Job? = null

    /** What the window draws now. */
    val content: StateFlow<SecondScreenContent> = shown.asStateFlow()

    /**
     * Whether a window is up. The top screen derives and offers content only while it is, so with
     * the second screen off or absent nothing runs for it.
     */
    val isFollowing: StateFlow<Boolean> = following.asStateFlow()

    /**
     * Set by the host when its window shows or goes. When it goes, the feed drops what was waiting
     * and goes back to Calm, so a window shown again never flashes the card of before.
     */
    fun setFollowing(isOn: Boolean) {
        if (!isOn) {
            pending?.cancel()
            pending = null
            shownFrom = null
            shown.value = SecondScreenContent.Calm
        }
        following.value = isOn
    }

    /**
     * Whether the window is on a display now, in any mode (picking, or the in-game card while a
     * game runs): true from a `show()` that succeeded until the window is dismissed (switch off,
     * display removed or changed, Plumemu left, the Activity destroyed); never for a refused show.
     * The top screen reads it once at its layout level (layout C, `HomeScreen`): its lists drop
     * the details column while this is true. Kept through a game, so coming back from one does
     * not change the top screen's layout.
     */
    val isWindowShown: StateFlow<Boolean> = windowShown.asStateFlow()

    /** Set by the host (main thread) when its window has been shown or has gone. */
    fun setWindowShown(isShown: Boolean) {
        windowShown.value = isShown
    }

    /**
     * Whether the window has room for box art (set by the host when it shows; both sizes have
     * since layout C). A window without never opens the art read ([SecondScreenPlayFacts]).
     */
    @Volatile
    var hasArtRoom: Boolean = true

    /** What [facts] answered: [content] null when it had nothing to read or failed (the card stays as offered). */
    private class Answer(
        val content: SecondScreenContent?,
    )

    /** [next] is under the cursor now (main thread). */
    fun offer(next: SecondScreenContent) {
        pending?.cancel()
        pending =
            scope.launch {
                delay(SETTLE_MS)
                val read = facts
                val isSameOffer = shownFrom == next
                shownFrom = next
                if (read == null) {
                    if (!isSameOffer) shown.value = next.withKnownFactsOf(shown.value)
                    return@launch
                }
                coroutineScope {
                    val answer =
                        async(io) {
                            try {
                                Answer(read(next))
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                // The card stays without its facts line; never a crash (Plumemu may be the home app).
                                warn("Second screen facts: could not be read (${e.javaClass.simpleName})")
                                Answer(null)
                            }
                        }
                    // One change, not two: the facts, the title's size and the cover's place come together
                    // when the read is quick. A slow read never holds the text back.
                    val quick = withTimeoutOrNull(FILL_WAIT_MS) { answer.await() }
                    if (quick != null) {
                        val filled = quick.content
                        if (filled != null) shown.value = filled else if (!isSameOffer) shown.value = next.withKnownFactsOf(shown.value)
                    } else {
                        if (!isSameOffer) shown.value = next.withKnownFactsOf(shown.value)
                        answer.await().content?.let { shown.value = it }
                    }
                }
            }
    }

    /** [this] with [previous]'s play facts when both are the same game or list (by its play key) and they were read. */
    private fun SecondScreenContent.withKnownFactsOf(previous: SecondScreenContent): SecondScreenContent =
        when {
            this is SecondScreenContent.Game && previous is SecondScreenContent.Game &&
                gameId != null && gameId == previous.gameId && !isPlayKnown && previous.isPlayKnown ->
                copy(
                    isPlayKnown = true,
                    lastPlayedDaysAgo = previous.lastPlayedDaysAgo,
                    playTimeMinutes = previous.playTimeMinutes,
                    emulator = previous.emulator,
                    fileName = previous.fileName,
                    // Only the cover of the same result: another key (signed out and in again) never inherits it.
                    art = art ?: previous.art.takeIf { scrapeKey != null && scrapeKey == previous.scrapeKey },
                    details = details ?: previous.details.takeIf { scrapeKey != null && scrapeKey == previous.scrapeKey },
                )
            this is SecondScreenContent.System && previous is SecondScreenContent.System &&
                lastPlayFrom != null && lastPlayFrom == previous.lastPlayFrom && lastGameTitle == null ->
                copy(lastGameTitle = previous.lastGameTitle, lastPlayedDaysAgo = previous.lastPlayedDaysAgo)
            else -> this
        }

    companion object {
        /**
         * How long the cursor rests before the second screen follows it. Longer than the top
         * screen's own motion after a move, the strip's slide ([PlumMotion.DURATION_MS], 180 ms)
         * and the focus glide ([PlumMotion.GLIDE_MS], 200 ms), so the card's composition and text
         * layout never share a frame with it. Held directions repeat every 80 ms and faster, so
         * nothing reaches the window until the person stops.
         */
        const val SETTLE_MS = 250L

        /**
         * How long the settled card waits for its facts and cover record ([facts]) before it shows
         * without them. A read that comes back in time (a Room point query, usually a few ms) lets the
         * card appear once, with the title's size, the cover's place and the facts line together;
         * the card changing twice a game would flicker on a screen the person watches at rest.
         */
        const val FILL_WAIT_MS = 100L
    }
}
