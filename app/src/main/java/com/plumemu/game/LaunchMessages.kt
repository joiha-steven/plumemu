package com.plumemu.game

import com.plumemu.R
import com.plumemu.graph.CoreQuestion
import com.plumemu.launcher.AppGameProblem
import com.plumemu.launcher.EmulatorOffer
import com.plumemu.launcher.FailureReason
import com.plumemu.launcher.FileProblem
import com.plumemu.launcher.LaunchOutcome
import com.plumemu.launcher.ShortcutProblem
import com.plumemu.launcher.SteamAppIdProblem
import com.plumemu.launcher.TitleIdProblem

/**
 * The message for every outcome but [LaunchOutcome.Started] (null): a title, one or two plain
 * sentences and what the person can do, shown in the panel's place, never a toast. The engine has
 * already written the log line with ids. [listing] is the system's emulators when
 * they could be read; the no-emulator message offers them, non-legal-risk first.
 */
internal fun launchMessage(
    outcome: LaunchOutcome,
    listing: EmulatorListing?,
): PanelContent? =
    when (outcome) {
        is LaunchOutcome.Started -> null
        is LaunchOutcome.EmulatorMissing -> emulatorMissing(outcome.emulator)
        is LaunchOutcome.EmulatorNotChosen -> chooseForSystem(outcome)
        is LaunchOutcome.NoEmulatorForSystem -> noEmulator(outcome, listing)
        is LaunchOutcome.FileMissing ->
            when (outcome.problem) {
                FileProblem.Gone ->
                    PanelContent(
                        title = UiText.Res(R.string.game_error_file_gone_title),
                        body = listOf(UiText.Res(R.string.game_error_file_gone_body, listOf(outcome.fileName))),
                        items = listOf(rescanRow()),
                    )
                FileProblem.FolderUnreachable ->
                    PanelContent(
                        title = UiText.Res(R.string.game_error_folder_title),
                        body = listOf(UiText.Res(R.string.game_error_folder_body)),
                        items = listOf(rescanRow()),
                    )
                // A rescan cannot bring a card back; the person puts it in and presses Play again.
                FileProblem.StorageNotFound ->
                    PanelContent(
                        title = UiText.Res(R.string.game_error_storage_title),
                        body = listOf(UiText.Res(R.string.game_error_storage_body)),
                        items = listOf(okRow()),
                    )
            }
        is LaunchOutcome.BadTitleIdFile ->
            PanelContent(
                title = UiText.Res(R.string.game_error_title_id_title),
                body = listOf(
                    UiText.Res(
                        when (outcome.problem) {
                            TitleIdProblem.Empty -> R.string.game_error_title_id_empty_body
                            TitleIdProblem.NotATitleId -> R.string.game_error_title_id_invalid_body
                        },
                        listOf(outcome.fileName),
                    ),
                ),
                items = listOf(okRow()),
            )
        is LaunchOutcome.BadSteamAppIdFile ->
            PanelContent(
                title = UiText.Res(R.string.game_error_steam_app_id_title),
                body = listOf(
                    UiText.Res(
                        when (outcome.problem) {
                            SteamAppIdProblem.Empty -> R.string.game_error_steam_app_id_empty_body
                            SteamAppIdProblem.NotAnAppId -> R.string.game_error_steam_app_id_invalid_body
                        },
                        listOf(outcome.fileName),
                    ),
                ),
                items = listOf(okRow()),
            )
        is LaunchOutcome.NeedsFilePath ->
            PanelContent(
                title = UiText.Res(R.string.game_error_needs_path_title, listOf(nameOf(outcome.emulator))),
                body = listOf(UiText.Res(R.string.game_error_needs_path_body)),
                items = listOf(playWithAnotherRow()),
            )
        is LaunchOutcome.ShortcutRefused -> shortcutRefused(outcome)
        is LaunchOutcome.AppGameRefused -> appGameRefused(outcome)
        is LaunchOutcome.Failed -> failed(outcome.reason)
    }

/** After a rename: why box art was not looked up by the new name; the name itself is saved. */
internal fun renameBoxArtMessage(notice: RenameBoxArtNotice): PanelContent =
    PanelContent(
        title = UiText.Res(R.string.game_rename_box_art_title),
        body =
            listOf(
                when (notice) {
                    RenameBoxArtNotice.NoWifi -> UiText.Res(R.string.game_rename_box_art_no_wifi)
                    RenameBoxArtNotice.SignInNeeded -> UiText.Res(R.string.game_rename_box_art_sign_in)
                    RenameBoxArtNotice.Paused -> UiText.Res(R.string.game_rename_box_art_paused)
                    is RenameBoxArtNotice.DailyLimit -> UiText.Format(R.string.game_rename_box_art_daily_limit, listOf(UiText.ClockTime(notice.untilMillis)))
                    is RenameBoxArtNotice.ServerClosed -> UiText.Format(R.string.game_rename_box_art_server_closed, listOf(UiText.ClockTime(notice.untilMillis)))
                },
            ),
        items = listOf(okRow()),
    )

/** The emulator came back in under ten seconds: it crashed, showed its own error or could not open the game. */
internal fun closedAtOnceMessage(emulatorName: String): PanelContent =
    PanelContent(
        title = UiText.Res(R.string.game_closed_at_once_title, listOf(emulatorName)),
        body = listOf(UiText.Res(R.string.game_closed_at_once_body)),
        items = listOf(okRow()),
    )

/**
 * "Did <game> start?" after a launch with a RetroArch core nobody has confirmed (Plumemu cannot list
 * RetroArch's cores). Yes is focused first; B closes and
 * answers nothing, so the next launch with that core asks again.
 */
internal fun coreQuestionMessage(question: CoreQuestion): PanelContent =
    PanelContent(
        title = UiText.Res(R.string.game_core_question_title, listOf(question.gameTitle)),
        body = listOf(UiText.Res(R.string.game_core_question_body, listOf(question.emulatorName))),
        items =
            listOf(
                PanelItem.Row(PanelRow(CORE_YES_KEY, UiText.Res(R.string.game_action_yes), PanelCommand.AnswerCore(question, started = true))),
                PanelItem.Row(PanelRow(CORE_NO_KEY, UiText.Res(R.string.game_action_no), PanelCommand.AnswerCore(question, started = false))),
            ),
    )

internal const val CORE_YES_KEY = "core_yes"
internal const val CORE_NO_KEY = "core_no"

/**
 * After No to the core question: the line that the core may not be installed, then the system's
 * other installed emulators ([failedEmulatorId] left out, legal-risk ones last with their legal-risk line),
 * then those to get. The pick is stored as the launch's choice was made: for the whole system when
 * the game has no choice of its own (title and body of the first game's question), else for the game
 * (Play with's title); then the game plays. With nothing to pick or get, OK closes.
 */
internal fun coreMissingMessage(
    listing: EmulatorListing,
    failedEmulatorId: String,
    emulatorName: String,
): PanelContent {
    val forSystem = listing.currentEmulatorId == null
    val others = listing.installed.filter { it.id != failedEmulatorId }.sortedBy { it.isLegalRisk }
    val rows =
        others.map { emulator ->
            PanelItem.Row(
                PanelRow(
                    key = emulator.id,
                    label = UiText.Plain(emulator.name),
                    command = PanelCommand.PlayWith(emulator.id, forSystem),
                    note = if (emulator.isLegalRisk) UiText.Res(R.string.game_legal_risk_note) else null,
                ),
            )
        } + notInstalledItems(listing.notInstalled.filter { it.id != failedEmulatorId })
    val body =
        listOf(UiText.Res(R.string.game_core_missing_body, listOf(emulatorName))) +
            if (forSystem && others.isNotEmpty()) listOf(UiText.Res(R.string.game_choose_emulator_body, listOf(listing.systemName))) else emptyList()
    val title = if (forSystem) UiText.Res(R.string.game_choose_emulator_title, listOf(listing.systemName)) else UiText.Res(R.string.game_play_with_title)
    val content = PanelContent(title = title, body = body, items = rows)
    return if (content.focusableRows.isEmpty()) content.copy(items = rows + okRow()) else content
}

/** A pinned shortcut: Plumemu lists it but cannot start it yet. [appName] is the app that published it, when known. */
/**
 * Android did not start a pinned shortcut: not the home app, the shortcut is
 * gone or disabled, or another failure (then: open it from its app). OK closes.
 */
private fun shortcutRefused(outcome: LaunchOutcome.ShortcutRefused): PanelContent {
    val app = outcome.appName
    val (title, body) =
        when (outcome.problem) {
            ShortcutProblem.NotHomeApp ->
                UiText.Res(R.string.game_error_shortcut_not_home_title) to UiText.Res(R.string.game_error_shortcut_not_home_body)
            ShortcutProblem.Unavailable ->
                (if (app != null) UiText.Res(R.string.game_error_shortcut_unavailable_title_app, listOf(app)) else UiText.Res(R.string.game_error_shortcut_unavailable_title)) to
                    UiText.Res(R.string.game_error_shortcut_unavailable_body)
            ShortcutProblem.StartFailed ->
                UiText.Res(R.string.game_error_shortcut_title) to
                    (if (app != null) UiText.Res(R.string.game_error_shortcut_body_app, listOf(app)) else UiText.Res(R.string.game_error_shortcut_body))
        }
    return PanelContent(title = title, body = listOf(body), items = listOf(okRow()))
}

/** Android did not start an installed app listed as a game: it is gone, or the start failed. OK closes. */
private fun appGameRefused(outcome: LaunchOutcome.AppGameRefused): PanelContent {
    val name = listOf(outcome.appName)
    val (title, body) =
        when (outcome.problem) {
            AppGameProblem.NotInstalled -> UiText.Res(R.string.app_error_missing_title, name) to UiText.Res(R.string.game_error_app_game_missing_body)
            AppGameProblem.StartFailed -> UiText.Res(R.string.app_error_start_title, name) to UiText.Res(R.string.app_error_refused_body)
        }
    return PanelContent(title = title, body = listOf(body), items = listOf(okRow()))
}

/** Hide: where the game went and where to bring it back. */
internal fun hiddenMessage(title: String): PanelContent =
    PanelContent(
        title = UiText.Res(R.string.game_hidden_title),
        body = listOf(UiText.Res(R.string.game_hidden_body, listOf(title))),
        items = listOf(okRow()),
    )

/** A change to the library (favorite, hide, emulator choice) could not be written. */
internal fun saveFailedMessage(): PanelContent =
    PanelContent(
        title = UiText.Res(R.string.game_error_save_title),
        body = listOf(UiText.Res(R.string.game_error_database_body)),
        items = listOf(okRow()),
    )

/** The game's details could not be read for the menu, Play with or Info. */
internal fun readFailedMessage(): PanelContent = failed(FailureReason.DatabaseError)

/** "Get it" found no store and no browser. */
internal fun storeUnavailableMessage(emulatorName: String): PanelContent =
    PanelContent(
        title = UiText.Res(R.string.game_error_store_title),
        body = listOf(UiText.Res(R.string.game_error_store_body, listOf(emulatorName))),
        items = listOf(okRow()),
    )

private fun emulatorMissing(offer: EmulatorOffer): PanelContent {
    val name = offer.name
    // Legal-risk: never from a store; the legal-risk line instead.
    val get = if (name != null && offer.packageName != null && !offer.isLegalRisk) {
        listOf(
            PanelItem.Row(
                PanelRow(
                    key = "get:${offer.emulatorId}",
                    label = UiText.Res(R.string.game_error_get_named, listOf(name)),
                    command = PanelCommand.GetEmulator(offer.packageName.orEmpty(), name),
                ),
            ),
        )
    } else {
        emptyList()
    }
    return PanelContent(
        title = if (name != null) {
            UiText.Res(R.string.game_error_missing_title, listOf(name))
        } else {
            UiText.Res(R.string.game_error_missing_title_unnamed)
        },
        body = listOfNotNull(UiText.Res(R.string.game_error_missing_body), if (offer.isLegalRisk) UiText.Res(R.string.game_legal_risk_note) else null),
        items = get + playWithAnotherRow(),
    )
}

private fun noEmulator(
    outcome: LaunchOutcome.NoEmulatorForSystem,
    listing: EmulatorListing?,
): PanelContent {
    val systemName = listing?.systemName ?: outcome.systemId
    // Installed since the launch looked (a race): the same question as a first launch.
    if (listing != null && listing.installed.isNotEmpty()) return chooseAmong(listing.installed, listing.automatic?.id, systemName)
    val candidates =
        listing?.notInstalled
            ?: outcome.candidates.mapNotNull { offer ->
                val name = offer.name ?: return@mapNotNull null
                val packageName = offer.packageName ?: return@mapNotNull null
                EmulatorItem(offer.emulatorId, name, packageName, offer.isLegalRisk)
            }
    // Only legal-risk candidates (Switch): nothing can be offered; say why, with one OK.
    if (candidates.isNotEmpty() && candidates.all { it.isLegalRisk }) {
        return PanelContent(
            title = UiText.Res(R.string.game_no_emulator_title, listOf(systemName)),
            body = listOf(UiText.Res(R.string.game_error_no_emulator_offered_body)),
            items = listOf(okRow()),
        )
    }
    val items = notInstalledItems(candidates, withHeading = false)
    return PanelContent(
        title = UiText.Res(R.string.game_no_emulator_title, listOf(systemName)),
        body = listOf(UiText.Res(if (items.isEmpty()) R.string.game_error_no_emulator_none_body else R.string.game_error_no_emulator_body)),
        items = items.ifEmpty { listOf(okRow()) },
    )
}

/**
 * The first launch of a system with nothing chosen: only the installed
 * emulators, the recommended one first (so the focus, and A, take it) with "Recommended"; the pick
 * is kept for the whole system. Legal-risk ones carry their legal-risk line and are never recommended;
 * nothing to get or download is offered here.
 */
private fun chooseForSystem(outcome: LaunchOutcome.EmulatorNotChosen): PanelContent {
    val emulators = outcome.installed.map { offer -> EmulatorItem(offer.emulatorId, offer.name ?: offer.emulatorId, offer.packageName.orEmpty(), offer.isLegalRisk) }
    return chooseAmong(emulators, outcome.recommendedId, outcome.systemName)
}

private fun chooseAmong(
    emulators: List<EmulatorItem>,
    recommendedId: String?,
    systemName: String,
): PanelContent {
    val recommended = emulators.filter { it.id == recommendedId && !it.isLegalRisk }
    val rows =
        (recommended + emulators.filterNot { it in recommended }).map { emulator ->
            PanelItem.Row(
                PanelRow(
                    key = emulator.id,
                    label = UiText.Plain(emulator.name),
                    command = PanelCommand.PlayWith(emulator.id, forSystem = true),
                    value = if (emulator in recommended) UiText.Res(R.string.game_choose_emulator_recommended) else null,
                    note = if (emulator.isLegalRisk) UiText.Res(R.string.game_legal_risk_note) else null,
                ),
            )
        }
    return PanelContent(
        title = UiText.Res(R.string.game_choose_emulator_title, listOf(systemName)),
        body = listOf(UiText.Res(R.string.game_choose_emulator_body, listOf(systemName))),
        items = rows,
    )
}

/**
 * The message for opening an app of the Apps entry, null for [AppOpenOutcome.Started]; [appName]
 * is the row's label (for the screen only). [AppActions] has written the log line.
 */
internal fun appOpenMessage(
    outcome: AppOpenOutcome,
    appName: String,
): PanelContent? {
    val (title, body) =
        when (outcome) {
            AppOpenOutcome.Started -> return null
            AppOpenOutcome.Missing -> R.string.app_error_missing_title to R.string.app_error_missing_body
            AppOpenOutcome.Refused -> R.string.app_error_start_title to R.string.app_error_refused_body
            AppOpenOutcome.NotListed -> R.string.app_error_start_title to R.string.app_error_not_listed_body
            AppOpenOutcome.Unavailable -> R.string.app_error_start_title to R.string.app_error_unavailable_body
        }
    return PanelContent(
        title = UiText.Res(title, listOf(appName)),
        body = listOf(UiText.Res(body)),
        items = listOf(okRow()),
    )
}

/** The message for App info or Uninstall that did not start; null for [AppToolResult.Started]. [AppActions] has written the log line. */
internal fun appToolMessage(
    result: AppToolResult,
    appName: String,
): PanelContent? {
    val body =
        when (result) {
            AppToolResult.Started -> return null
            AppToolResult.Failed -> R.string.app_tool_error_body
            AppToolResult.NotListed -> R.string.app_error_not_listed_body
        }
    return PanelContent(
        title = UiText.Res(R.string.app_error_start_title, listOf(appName)),
        body = listOf(UiText.Res(body)),
        items = listOf(okRow()),
    )
}

/** A rescan is already running: said, not silently dropped. */
internal fun scanBusyMessage(): PanelContent =
    PanelContent(
        title = UiText.Res(R.string.game_scan_busy_title),
        body = listOf(UiText.Res(R.string.game_scan_busy_body)),
        items = listOf(okRow()),
    )

private fun failed(reason: FailureReason): PanelContent {
    val (title, body) =
        when (reason) {
            FailureReason.GameNotInLibrary -> R.string.game_error_not_in_library_title to R.string.game_error_not_in_library_body
            FailureReason.UnknownSystem -> R.string.game_error_unknown_system_title to R.string.game_error_unknown_system_body
            FailureReason.ActivityNotFound -> R.string.game_error_start_title to R.string.game_error_activity_not_found_body
            FailureReason.NotAllowed -> R.string.game_error_start_title to R.string.game_error_not_allowed_body
            FailureReason.StartFailed -> R.string.game_error_start_title to R.string.game_error_start_failed_body
            FailureReason.DatabaseError -> R.string.game_error_database_title to R.string.game_error_database_body
            FailureReason.Internal -> R.string.game_error_internal_title to R.string.game_error_internal_body
        }
    val action =
        when (reason) {
            FailureReason.ActivityNotFound, FailureReason.NotAllowed, FailureReason.StartFailed -> playWithAnotherRow()
            FailureReason.GameNotInLibrary -> rescanRow()
            else -> okRow()
        }
    return PanelContent(title = UiText.Res(title), body = listOf(UiText.Res(body)), items = listOf(action))
}

private fun nameOf(offer: EmulatorOffer): String = offer.name ?: offer.emulatorId

private fun rescanRow() = PanelItem.Row(PanelRow("rescan", UiText.Res(R.string.game_action_rescan), PanelCommand.RescanLibrary))

private fun playWithAnotherRow() =
    PanelItem.Row(PanelRow("play_with_another", UiText.Res(R.string.game_action_play_with_another), PanelCommand.OpenPlayWith))

/** The answer for an app is stored, but the library could not follow yet (a skipped sync): the app moves at the next read. */
internal fun appKindLaterMessage(): PanelContent =
    PanelContent(
        title = UiText.Res(R.string.game_app_kind_later_title),
        body = listOf(UiText.Res(R.string.game_app_kind_later_body)),
        items = listOf(okRow()),
    )

private fun okRow() = PanelItem.Row(PanelRow("ok", UiText.Res(R.string.game_action_ok), PanelCommand.Close))

/** While a start runs (shown after a moment, or at once on a second Play): no rows, A does nothing, B or START hide it. */
internal fun startingMessage(): PanelContent = PanelContent(title = UiText.Res(R.string.game_starting))

/** The start did not hand over within the limit; A works again. */
internal fun noStartMessage(): PanelContent =
    PanelContent(
        title = UiText.Res(R.string.game_error_no_start_title),
        body = listOf(UiText.Res(R.string.game_error_no_start_body)),
        items = listOf(okRow()),
    )

/** The Apps entry's form of [startingMessage]. */
internal fun appOpeningMessage(appName: String): PanelContent = PanelContent(title = UiText.Res(R.string.app_opening, listOf(appName)))

/** The Apps entry's form of [noStartMessage]. */
internal fun appNoStartMessage(appName: String): PanelContent =
    PanelContent(
        title = UiText.Res(R.string.app_error_no_start_title, listOf(appName)),
        body = listOf(UiText.Res(R.string.game_error_no_start_body)),
        items = listOf(okRow()),
    )
