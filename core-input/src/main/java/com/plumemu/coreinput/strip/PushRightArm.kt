package com.plumemu.coreinput.strip

import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * "Push right to play" needs two rights on the same game: the first arms it, the second plays.
 * This remembers which game is armed. [armedKey] is Compose state, so the hint bar can say "Again
 * to play"; a screen disarms it on any other input ([disarm]) and it lapses by itself after
 * [TIMEOUT_MS]. The timeout is checked against [clock] on every press (the truth) and also clears
 * [armedKey] on [scope] (so the hint goes away without a press).
 *
 * The clock is `elapsedRealtime`, which keeps counting while the device sleeps (a coroutine
 * `delay` does not), so a Right after waking can never play on an old arming.
 *
 * 2 s: long enough for a thumb to push right twice without hurry, short enough that a stray
 * push is forgotten before the person looks at another game.
 */
@Stable
class PushRightArm(
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    /** The armed game, by a key that names the game itself (system id and game id), never its row: a list that changes under the arming must not move it to another game. */
    var armedKey: String? by mutableStateOf(null)
        private set

    private var armedAt = 0L
    private var lapse: Job? = null

    /** True when [key] was armed less than [TIMEOUT_MS] ago. */
    fun isArmedFor(key: String): Boolean = armedKey == key && clock() - armedAt <= TIMEOUT_MS

    fun arm(
        key: String,
        scope: CoroutineScope? = null,
    ) {
        armedKey = key
        armedAt = clock()
        lapse?.cancel()
        lapse = scope?.launch {
            delay(TIMEOUT_MS)
            if (armedKey == key) armedKey = null
        }
    }

    fun disarm() {
        if (armedKey == null) return
        armedKey = null
        lapse?.cancel()
        lapse = null
    }

    companion object {
        const val TIMEOUT_MS = 2_000L
    }
}
