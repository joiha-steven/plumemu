package com.plumemu.report

import com.plumemu.telemetry.ReportResult

/**
 * What the graph does for Settings › About › Report a problem. [submit] returns at once; [onDone]
 * gets the answer on the main thread.
 */
internal interface ProblemReportActions {
    fun submit(
        text: String,
        email: String?,
        includeDetails: Boolean,
        onDone: (ReportResult) -> Unit,
    )

    /** A build or test with nothing to send with: the report cannot be kept. */
    object None : ProblemReportActions {
        override fun submit(
            text: String,
            email: String?,
            includeDetails: Boolean,
            onDone: (ReportResult) -> Unit,
        ) = onDone(ReportResult.NotSaved)
    }
}
