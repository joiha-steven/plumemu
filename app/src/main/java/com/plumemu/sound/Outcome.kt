package com.plumemu.sound

/**
 * What an action really did, as reported by the model that did it (docs/design/rules.md › Sound).
 * A press that changed nothing is [Nothing] and makes no sound.
 */
enum class Outcome(
    internal val rank: Int,
    internal val sound: UiSound?,
) {
    Nothing(0, null),

    /** The cursor moved to another row, column, section or system. */
    Moved(1, UiSound.Move),

    /** Opened, played, toggled or started something. */
    Activated(2, UiSound.Confirm),

    /** Went back or closed a panel. */
    WentBack(2, UiSound.Back),
}

/**
 * Where a model says what its action did, at the point it changes its state, so the answer never
 * depends on when an asynchronous store catches up. Wrap the handler in [act], call [report]
 * where something happens; the strongest report is sent to [sink] once when the handler returns.
 * Reports outside [act] (a slow answer arriving later) are ignored. Main thread only.
 */
class Feel {
    var sink: (Outcome) -> Unit = {}
    private var pending: Outcome? = null
    private var isHushed = false

    fun <T> act(block: () -> T): T {
        // A handler called from inside another one reports into the outer one.
        if (pending != null) return block()
        pending = Outcome.Nothing
        isHushed = false
        try {
            return block()
        } finally {
            val outcome = pending
            pending = null
            if (outcome != null && !isHushed) emit(outcome)
        }
    }

    fun report(outcome: Outcome) {
        val current = pending ?: return
        if (outcome.rank > current.rank) pending = outcome
    }

    /** This action makes no sound whatever else is reported (the Sounds switch itself). */
    fun hush() {
        if (pending != null) isHushed = true
    }

    /** Sends [outcome] now, outside [act]. */
    fun emit(outcome: Outcome) {
        if (outcome != Outcome.Nothing) sink(outcome)
    }
}
