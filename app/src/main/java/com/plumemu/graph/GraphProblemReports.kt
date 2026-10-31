package com.plumemu.graph

import com.plumemu.report.ProblemReportActions
import com.plumemu.telemetry.ProblemReports
import com.plumemu.telemetry.ReportResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings › About › Report a problem: the report goes to the telemetry module's [ProblemReports]
 * (written to disk, sent at once or when online). Answers on [main], so the form is told on the
 * main thread. No module (a test's Application): the report cannot be kept.
 */
internal fun problemReportActionsOf(
    reports: ProblemReports?,
    scope: CoroutineScope,
    main: CoroutineDispatcher,
): ProblemReportActions =
    object : ProblemReportActions {
        override fun submit(
            text: String,
            email: String?,
            includeDetails: Boolean,
            onDone: (ReportResult) -> Unit,
        ) {
            scope.launch(main) {
                val answer = reports?.submit(text, email, includeDetails) ?: ReportResult.NotSaved
                withContext(main) { onDone(answer) }
            }
        }
    }
