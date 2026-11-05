package com.plumemu.report

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.plumemu.telemetry.ReportResult

/**
 * What was typed into Report a problem and not yet sent or saved, kept for the life of the process
 * (one per graph) so B, Cancel or Home do not lose it. Memory only: nothing is written to disk.
 * It also knows a send is on its way, so a form opened meanwhile waits for it instead of sending
 * the same words again. Main thread only.
 */
internal class ReportDraft {
    var text: String = ""

    var email: String = ""

    /** A send is on its way; a form opened now waits for its answer. */
    var isSending: Boolean by mutableStateOf(false)
        private set

    /** The latest form opened while a send runs; it gets the answer the sending form may no longer be there for. */
    var watcher: ((ReportResult) -> Unit)? = null

    private var sentText = ""
    private var sentEmail = ""

    fun clear() {
        text = ""
        email = ""
    }

    fun beginSend(
        sentWords: String,
        sentAddress: String,
    ) {
        sentText = sentWords
        sentEmail = sentAddress
        isSending = true
    }

    /**
     * The send ended. Only the words that were sent are cleared (sent or saved); anything else typed
     * since stays. A watching form gets the answer once.
     */
    fun finishSend(answer: ReportResult) {
        if (!isSending) return
        isSending = false
        if ((answer == ReportResult.Sent || answer == ReportResult.Saved) && text == sentText && email == sentEmail) clear()
        val waiting = watcher
        watcher = null
        waiting?.invoke(answer)
    }

    // Never in a log.
    override fun toString(): String = "ReportDraft(***)"
}
