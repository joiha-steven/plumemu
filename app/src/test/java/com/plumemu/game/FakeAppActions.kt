package com.plumemu.game

import com.plumemu.coredata.db.AppKind
import kotlinx.coroutines.CompletableDeferred

/** [AppActions] for the screen's and the controller's tests: records the rows asked to open. */
internal class FakeAppActions(
    var outcome: (rowId: String) -> AppOpenOutcome = { AppOpenOutcome.Started },
) : AppActions {
    val opened = mutableListOf<String>()

    /** When set, an open waits for it (a start that takes a moment). */
    var gate: CompletableDeferred<Unit>? = null

    /** Rows the app menu offers Uninstall for (a system app or Plumemu is not in it). */
    var uninstallable: Set<String> = emptySet()
    val infoOpened = mutableListOf<String>()
    val uninstalled = mutableListOf<String>()
    var toolResult: AppToolResult = AppToolResult.Started

    /** When set, reading whether Uninstall is offered waits for it. */
    var readGate: CompletableDeferred<Unit>? = null

    /** When set, an uninstall waits for it (Android's dialog taking a moment to start). */
    var uninstallGate: CompletableDeferred<Unit>? = null

    override suspend fun canUninstall(rowId: String): Boolean {
        readGate?.await()
        return rowId in uninstallable
    }

    /** Rows whose app can be shown as a game or a port (Plumemu and Android's Settings are not in it). */
    var choosable: Set<String> = emptySet()

    /** The answers asked of the app menu, in order. */
    val kinds = mutableListOf<Pair<String, AppKind>>()

    /** When set, storing an answer throws it (the library could not be written). */
    var kindError: Exception? = null

    override suspend fun canChooseKind(rowId: String): Boolean = rowId in choosable

    /** What setKind answers: false when the library could not follow yet (a skipped sync). */
    var followed: Boolean = true

    override suspend fun setKind(rowId: String, kind: AppKind): Boolean {
        kindError?.let { throw it }
        kinds += rowId to kind
        return followed
    }

    override suspend fun showInfo(rowId: String): AppToolResult {
        infoOpened += rowId
        return toolResult
    }

    override suspend fun uninstall(rowId: String): AppToolResult {
        uninstalled += rowId
        uninstallGate?.await()
        return toolResult
    }

    override suspend fun open(rowId: String): AppOpenOutcome {
        opened += rowId
        gate?.await()
        return outcome(rowId)
    }
}
