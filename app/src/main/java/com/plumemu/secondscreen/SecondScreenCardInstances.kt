package com.plumemu.secondscreen

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The card activities of this process: exactly one may live. Main thread only.
 *
 * The card's launch mode is `standard` (Android refuses a `singleTask` or `singleInstance`
 * secondary home), so Android makes a new instance on each Home press on the bottom display, and a
 * start from Plumemu may race one Android makes. The newest instance wins: [created] finishes the
 * one before it, so one card task remains.
 */
internal object CardInstances {
    /** The card alive now: created, not destroyed, not finished by Plumemu. */
    var live: SecondScreenCardActivity? = null
        private set

    /** Cards on a display that is not the second screen (Android's secondary home on a third display): blank, never [live]. */
    private val strays = mutableListOf<SecondScreenCardActivity>()

    /**
     * Whether a card on [displayId] is the second screen's: the display the host shows on, or before any host (a boot)
     * the one the detection chose ([detected]); neither known: not.
     */
    fun isSecondScreen(
        displayId: Int,
        detected: Int?,
    ): Boolean = displayId == (SecondScreenHost.cardHolder.value?.shownDisplayId ?: detected)

    fun stray(card: SecondScreenCardActivity) {
        strays += card
    }

    /** Ends the blank cards on other displays (the alias went off: Android's own homes come back there). */
    fun finishStrays() {
        val ending = strays.toList()
        strays.clear()
        ending.forEach { it.finish() }
    }

    /** Told when [live] goes without Plumemu finishing it (its display removed, Android ending it). */
    var onGone: ((reason: String) -> Unit)? = null

    fun created(card: SecondScreenCardActivity) {
        val older = live
        live = card
        if (older != null && older !== card) older.finishAsOlder()
        // A blank one Android made on this display before the detection knew it (a boot) goes too.
        val id = card.displayIdNow()
        strays.filter { it.displayIdNow() == id }.forEach {
            strays.remove(it)
            it.finishAsOlder()
        }
    }

    fun destroyed(
        card: SecondScreenCardActivity,
        changingConfigurations: Boolean,
    ) {
        strays.remove(card)
        if (live !== card) return
        live = null
        // A relaunch for a new configuration makes the next instance at once: not gone.
        if (!changingConfigurations) onGone?.invoke("the card was closed by Android")
    }

    /** Plumemu ends the card; null when none lives. */
    fun finishLive(): SecondScreenCardActivity? {
        val card = live ?: return null
        live = null
        card.finish()
        return card
    }
}

/**
 * Starts the card on [displayId] in its own task (affinity `com.plumemu.card`). Throws what
 * `startActivity` throws (a display that refuses it, `SecurityException`): the host retries.
 */
internal fun startSecondScreenCard(
    context: Context,
    displayId: Int,
) {
    // The host wants no card before Android 10 (SecondScreenHost.wantFor); a call there is a refusal like any other.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) throw UnsupportedOperationException("the card needs Android 10")
    val intent = Intent(context, SecondScreenCardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent, ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle())
}

internal fun shorterSideDp(display: Display): Dp = DisplayMetrics().also { display.getMetrics(it) }.let { (minOf(it.widthPixels, it.heightPixels) / it.density).dp }
