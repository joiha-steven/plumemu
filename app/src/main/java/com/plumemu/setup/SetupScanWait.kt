package com.plumemu.setup

import com.plumemu.graph.ScanReport
import com.plumemu.graph.ScanState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** A scan state with what the scan is doing, read together so one change of either is one state. */
internal class Scanning(val state: ScanState, val report: ScanReport)

/**
 * Waits until setup may show the library: the walk is over in every shown folder and the systems flow holds
 * games, or the scan has ended (it failed, or there was nothing to name). Returns the scan's state then.
 *
 * The walk is enough: the games are in the database and the counts are final. Naming goes on in the
 * background (it opens every file once, a minute for 1,800 games over SAF). The count comes from a database
 * flow that lags the walk by a moment, so Ready also waits for the first games to show in it, or it would say
 * "No games were found yet" for a frame. Non-empty is all it checks: the last batch may still be on its way, so
 * the count on Ready can rise a moment later. A library with no games has nothing to name, so its scan ends and
 * the first condition lets it through. Known gap: a folder that was unreadable earlier in the same scan counts as
 * walked, so picking it again mid-scan can open Ready before its games are read.
 */
internal suspend fun SetupSource.untilTheWalkEnds(): ScanState =
    combine(scan, scanReport, folders, systems) { s, r, f, sys -> s to (walkEnded(r, f.map { it.id }) && sys.isNotEmpty()) }
        .first { (s, walked) -> !s.isBusy || walked }
        .first
