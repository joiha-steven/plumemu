package com.plumemu.restart

/**
 * What the restart for compiled code looks at, read once per evaluation.
 *
 * @property isScreenOff the screen is off now.
 * @property isProcessInFront the process's importance says its activity is on top (foreground, or
 *   top while the device sleeps); a game, an emulator or another app in front makes it lower.
 * @property wasHomeInFront the main screen was the last of the app's own screens in front when the
 *   screen went off, and has been stopped since (its saved state is with Android).
 * @property isPlayPending a game started from the app has not come back yet.
 * @property busyWork the first background work found running (scan, box art, content read,
 *   statistics send, backup, restore, update), or null when none is.
 * @property isSettingsIdle no Settings write is waiting or running.
 * @property code whether the compiled code mapped is still the one on disk.
 * @property installKey this install (version code and last update time).
 * @property restartedFor the install the process was last restarted for (null: never).
 */
internal data class RestartInputs(
    val isScreenOff: Boolean,
    val isProcessInFront: Boolean,
    val wasHomeInFront: Boolean,
    val isPlayPending: Boolean,
    val busyWork: String?,
    val isSettingsIdle: Boolean,
    val code: StaleCode,
    val installKey: String,
    val restartedFor: String?,
)

/** What to do after one evaluation. */
internal sealed interface RestartVerdict {
    /** End the process: [stale] names the mapping that was replaced. */
    data class Restart(val stale: StaleCode.Stale) : RestartVerdict

    /** Leave the process alone, for [why] (logged in one line, never shown). */
    data class Hold(val why: String) : RestartVerdict
}

/**
 * Restart only when every condition holds; any doubt holds. The once-per-install key comes first, so
 * a process started after a restart for this install never ends itself again (no loop, even if the
 * maps went on looking stale).
 */
internal fun decideRestart(inputs: RestartInputs): RestartVerdict =
    with(inputs) {
        when {
            restartedFor == installKey -> RestartVerdict.Hold("already restarted for this install")
            !isScreenOff -> RestartVerdict.Hold("screen on")
            !isProcessInFront -> RestartVerdict.Hold("another app in front")
            !wasHomeInFront -> RestartVerdict.Hold("the home was not in front")
            isPlayPending -> RestartVerdict.Hold("a play is pending")
            busyWork != null -> RestartVerdict.Hold("$busyWork running")
            !isSettingsIdle -> RestartVerdict.Hold("a Settings write is pending")
            else ->
                when (val now = code) {
                    is StaleCode.Unknown -> RestartVerdict.Hold("code state unknown (${now.why})")
                    StaleCode.Fresh -> RestartVerdict.Hold("code is current")
                    is StaleCode.Stale -> RestartVerdict.Restart(now)
                }
        }
    }
