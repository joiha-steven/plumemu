package com.plumemu.graph

import android.content.Context
import com.plumemu.coredata.settings.AdvancedSettings
import com.plumemu.coredata.settings.SettingsRepository
import com.plumemu.launcher.play.PlaySessionOutcome
import com.plumemu.settings.UsageAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The home's one-time offer of Android's Usage access (Exact play time). [afterPlay] raises it after the first real play
 * (a minute or more) when access is not granted, the device has the screen to open, Exact play time is off and the offer
 * was never shown; a play with a core question waits, so the two never stack. The home shows the card when it is idle and
 * calls [shown], which stores the flag: the offer never comes back whatever the answer. Allow calls [allowed]; the next
 * [afterResume] switches Exact play time on only if access was granted meanwhile.
 */
internal class UsageAccessOffer(
    private val readAdvanced: suspend () -> AdvancedSettings,
    private val writeAdvanced: ((AdvancedSettings) -> AdvancedSettings) -> Unit,
    private val hasAccess: () -> Boolean,
    private val canOpen: () -> Boolean,
    private val log: (String) -> Unit,
) {
    private val raised = MutableStateFlow(false)

    /** True while the card is due: the home shows it as soon as it is idle. */
    val isDue: StateFlow<Boolean> = raised

    @Volatile
    private var awaitingAnswer = false

    suspend fun afterPlay(outcome: PlaySessionOutcome) {
        if (outcome !is PlaySessionOutcome.Recorded || outcome.coreToConfirm != null) return
        if (outcome.seconds < MIN_SECONDS || raised.value) return
        try {
            val advanced = readAdvanced()
            if (advanced.usageAccessOffered || advanced.exactPlayTime) return
            if (hasAccess() || !canOpen()) return
            raised.value = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Usage access offer not checked (${e.javaClass.simpleName})")
        }
    }

    /** The card is on the screen: remembered for good. */
    fun shown() {
        raised.value = false
        writeAdvanced { it.copy(usageAccessOffered = true) }
    }

    /** Allow was pressed and Android's screen is opening; [afterResume] checks what came of it. */
    fun allowed() {
        awaitingAnswer = true
    }

    /** Allow could not open the screen: nothing waits for an answer. */
    fun couldNotOpen() {
        awaitingAnswer = false
        log("Usage access screen did not open from the offer")
    }

    /** Back in front: after Allow, access now granted turns Exact play time on; either way nothing waits any more. */
    fun afterResume() {
        if (!awaitingAnswer) return
        awaitingAnswer = false
        try {
            if (hasAccess()) writeAdvanced { it.copy(exactPlayTime = true) }
        } catch (e: Exception) {
            log("Usage access answer not read (${e.javaClass.simpleName})")
        }
    }

    private companion object {
        /** A play of a minute or more is a real one. */
        const val MIN_SECONDS = 60L
    }
}

/** The graph's offer: settings read and written through the store and its writer, access asked of Android. */
internal fun usageAccessOfferFor(
    app: Context,
    store: SettingsRepository,
    writer: SettingsWriter,
    log: (String) -> Unit,
) = UsageAccessOffer(
    readAdvanced = { store.readOnce().advanced },
    writeAdvanced = { change -> writer.submit { it.copy(advanced = change(it.advanced)) } },
    hasAccess = { UsageAccess.isGranted(app) },
    canOpen = { UsageAccess.canOpen(app) },
    log = { log("UsageAccessOffer: $it") },
)
