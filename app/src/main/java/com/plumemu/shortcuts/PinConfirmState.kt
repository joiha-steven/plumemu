package com.plumemu.shortcuts

import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.sound.Feel
import com.plumemu.sound.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the confirm screen needs: the offer of its intent, and the add. */
interface PinConfirmActions {
    suspend fun offer(): PinOffer

    suspend fun add(offer: PinOffer.Ask): PinAddOutcome
}

/** What the confirm screen shows. */
sealed interface PinScreen {
    /** The request is being read: nothing is drawn yet. */
    data object Reading : PinScreen

    /** "Add <label> to <system>?" with Add and Cancel; [isAdding] while the add runs (keys wait). */
    data class Asking(val label: String, val systemName: String, val isAdding: Boolean = false) : PinScreen

    /** A message with one OK; [label] fills the titles that name the game. */
    data class Message(val message: PinMessage, val label: String = "") : PinScreen
}

enum class PinMessage {
    Widget,
    Expired,
    NotReady,
    NotAccepted,
    NotSaved,
}

/**
 * The confirm screen of a pin request, like the home screen question: A or a tap
 * on Add adds, B or Cancel closes without pinning, Left/Right/Up/Down move between the two. A
 * message has one OK, which A, B or a tap closes. One add at a time: presses wait while it runs.
 */
@Stable
class PinConfirmState(
    private val actions: PinConfirmActions,
    private val scope: CoroutineScope,
) {
    var screen: PinScreen by mutableStateOf(PinScreen.Reading)
        private set

    /** 0 = Add (or OK under a message), 1 = Cancel. */
    var focus by mutableIntStateOf(0)
        private set

    private val closed = MutableStateFlow(false)

    /**
     * The screen is done: the Activity finishes. Also a flow ([closedFlow]), so the Activity can
     * follow it without a composition (it may close before the settings let anything draw).
     */
    var isClosed by mutableStateOf(false)
        private set

    val closedFlow: StateFlow<Boolean> = closed.asStateFlow()

    /** Says what each action did, for the sounds (set by the screen). */
    val feel = Feel()

    private var offer: PinOffer.Ask? = null

    /** Reads the request; call once. */
    fun start() {
        scope.launch {
            val read =
                try {
                    actions.offer()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: RuntimeException) {
                    // Whatever an app put in the intent must never crash the home app. Logged here: a
                    // failure outside the host's own read (the catalog, the filing) says nothing else.
                    Log.w(TAG, "Pin request could not be offered (${e.javaClass.simpleName})")
                    PinOffer.Refused(PinRefusal.Expired)
                }
            screen =
                when (read) {
                    is PinOffer.Ask -> PinScreen.Asking(read.label, read.systemName).also { offer = read }
                    is PinOffer.Refused -> PinScreen.Message(messageFor(read.reason))
                }
        }
    }

    fun onAction(action: Action) {
        val shown = screen
        if (isClosed || (shown is PinScreen.Asking && shown.isAdding)) return
        if (shown is PinScreen.Reading) {
            // Nothing is drawn yet; B still leaves.
            if (action == Action.Back) close()
            return
        }
        val buttons = if (shown is PinScreen.Asking) 2 else 1
        feel.act {
            when (action) {
                is Action.Move -> {
                    val before = focus
                    focus =
                        when (action.direction) {
                            Direction.Left, Direction.Up -> (focus - 1).coerceAtLeast(0)
                            Direction.Right, Direction.Down -> (focus + 1).coerceAtMost(buttons - 1)
                        }
                    if (focus != before) feel.report(Outcome.Moved)
                }
                Action.Enter -> {
                    activate(focus)
                    feel.report(Outcome.Activated)
                }
                Action.Back -> {
                    close()
                    feel.report(Outcome.WentBack)
                }
                is Action.TapItem ->
                    if (action.index in 0 until buttons) {
                        focus = action.index
                        activate(action.index)
                        feel.report(Outcome.Activated)
                    }
                else -> Unit
            }
        }
    }

    private fun activate(index: Int) {
        val shown = screen
        val asked = offer
        if (shown !is PinScreen.Asking || asked == null || index == CANCEL) {
            close()
            return
        }
        screen = shown.copy(isAdding = true)
        scope.launch {
            val outcome =
                try {
                    actions.add(asked)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: RuntimeException) {
                    Log.w(TAG, "Pinned shortcut could not be added (${e.javaClass.simpleName})")
                    PinAddOutcome.NotSaved
                }
            when (outcome) {
                is PinAddOutcome.Added -> close()
                PinAddOutcome.NotAccepted -> showMessage(PinMessage.NotAccepted, shown.label)
                PinAddOutcome.NotSaved -> showMessage(PinMessage.NotSaved, shown.label)
            }
        }
    }

    private fun close() {
        isClosed = true
        closed.value = true
    }

    private fun messageFor(reason: PinRefusal): PinMessage =
        when (reason) {
            PinRefusal.Widget -> PinMessage.Widget
            PinRefusal.Expired -> PinMessage.Expired
            PinRefusal.NotReady -> PinMessage.NotReady
        }

    private fun showMessage(
        message: PinMessage,
        label: String,
    ) {
        focus = 0
        screen = PinScreen.Message(message, label)
    }

    private companion object {
        const val CANCEL = 1
    }
}
