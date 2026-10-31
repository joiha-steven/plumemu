package com.plumemu.game

import com.plumemu.R
import com.plumemu.coredata.db.AppKind

/** Stable ids of the game menu's entries (row keys, tests). */
internal enum class GameMenuEntryId { Play, PlayWith, OtherVersions, Favorite, Hide, RemoveFromRecent, Name, FixBoxArt, Info }

/** Stable ids of the app-only entries, in an installed app's game menu and the Apps entry's menu. */
internal enum class AppKindEntryId { ShowAsGame, ShowAsPort, NotAGame }

/** Stable ids of the Name… sub-panel's entries. */
internal enum class NameEntryId { FixName, OwnName, NotAGame, BackToAutomatic }

/**
 * The game menu's entries, assembled here and nowhere else: a later step adds entries by adding
 * rows to this list, without touching the panel. Name… is one such row, after Hide: the
 * naming entries live in its sub-panel ([nameMenuEntries]) so the menu fits a 432-high screen
 * Fix box art… follows
 * it, only while box art can be asked for ([GameFacts.canFixBoxArt]). A pinned shortcut
 * ([GameFacts.isShortcut]) has neither: it has no file to name or look up. Other
 * versions follows Play with only for a row that stands for two or more copies
 * ([GameFacts.versions]), its value the version A plays now. An installed app listed as a game
 * ([GameFacts.isAppGame]) has none of Play with…, Other versions or Fix box art…, and its
 * Name… holds only Use my own name (and Back to automatic once one is given). It adds the person's answer
 * answer: Show as port (an Android game) or Show as game (a Ports app), and Not a game; each sends the row away.
 */
internal fun gameMenuEntries(facts: GameFacts): List<PanelRow> {
    // No file to name, look up or open with an emulator (app games; pinned shortcuts keep Play with).
    val hasNoFile = facts.isShortcut || facts.isAppGame
    return listOfNotNull(
        PanelRow(GameMenuEntryId.Play.name, UiText.Res(R.string.game_menu_play), PanelCommand.Play),
        if (facts.isAppGame) null else PanelRow(GameMenuEntryId.PlayWith.name, UiText.Res(R.string.game_menu_play_with), PanelCommand.OpenPlayWith),
        facts.versions?.takeIf { it.hasOthers && !hasNoFile }?.let { versions ->
            PanelRow(GameMenuEntryId.OtherVersions.name, UiText.Res(R.string.game_menu_other_versions), PanelCommand.OpenVersions, value = versions.current?.label)
        },
        if (facts.isFavorite) {
            PanelRow(GameMenuEntryId.Favorite.name, UiText.Res(R.string.game_menu_unfavorite), PanelCommand.SetFavorite(false))
        } else {
            PanelRow(GameMenuEntryId.Favorite.name, UiText.Res(R.string.game_menu_favorite), PanelCommand.SetFavorite(true))
        },
        PanelRow(GameMenuEntryId.Hide.name, UiText.Res(R.string.game_menu_hide), PanelCommand.Hide),
        // Only a menu opened from Recent; right after Hide, the other way to take a game off a list.
        if (facts.canRemoveFromRecent) PanelRow(GameMenuEntryId.RemoveFromRecent.name, UiText.Res(R.string.game_menu_remove_from_recent), PanelCommand.RemoveFromRecent) else null,
        // An app game keeps Name… for Use my own name (nameMenuEntries); a pinned shortcut has none.
        if (facts.isShortcut) null else PanelRow(GameMenuEntryId.Name.name, UiText.Res(R.string.game_menu_name), PanelCommand.OpenNameMenu),
        if (facts.canFixBoxArt && !hasNoFile) PanelRow(GameMenuEntryId.FixBoxArt.name, UiText.Res(R.string.game_menu_fix_box_art), PanelCommand.OpenFixBoxArt) else null,
        // the person's answer for an installed app; the row leaves this list (the app is a port, or back in Apps).
        if (facts.isAppGame && facts.isPortApp) PanelRow(AppKindEntryId.ShowAsGame.name, UiText.Res(R.string.app_menu_show_as_game), PanelCommand.SetAppKind(AppKind.GAME)) else null,
        if (facts.isAppGame && !facts.isPortApp) PanelRow(AppKindEntryId.ShowAsPort.name, UiText.Res(R.string.app_menu_show_as_port), PanelCommand.SetAppKind(AppKind.PORT)) else null,
        if (facts.isAppGame) PanelRow(AppKindEntryId.NotAGame.name, UiText.Res(R.string.game_menu_not_a_game), PanelCommand.AskAppNotAGame) else null,
        PanelRow(GameMenuEntryId.Info.name, UiText.Res(R.string.game_menu_info), PanelCommand.OpenInfo),
    )
}

/** Stable ids of the app menu's entries (row keys, tests). */
internal enum class AppMenuEntryId { Info, Uninstall }

/**
 * The Apps entry's menu: the app's name, App info, Show as game and Show as port (not for Plumemu or
 * Android's Settings, [canChooseKind] false), and Uninstall only when it can be removed.
 */
internal fun appMenuContent(
    app: GameRef,
    canUninstall: Boolean,
    canChooseKind: Boolean = false,
): PanelContent =
    PanelContent(
        title = UiText.Plain(app.title),
        items =
            listOfNotNull(
                PanelRow(AppMenuEntryId.Info.name, UiText.Res(R.string.app_menu_info), PanelCommand.AppInfo),
                if (canChooseKind) PanelRow(AppKindEntryId.ShowAsGame.name, UiText.Res(R.string.app_menu_show_as_game), PanelCommand.SetAppKind(AppKind.GAME)) else null,
                if (canChooseKind) PanelRow(AppKindEntryId.ShowAsPort.name, UiText.Res(R.string.app_menu_show_as_port), PanelCommand.SetAppKind(AppKind.PORT)) else null,
                if (canUninstall) PanelRow(AppMenuEntryId.Uninstall.name, UiText.Res(R.string.app_menu_uninstall), PanelCommand.AppUninstall) else null,
            ).map { PanelItem.Row(it) },
    )

/** The menu panel: the game's title, then its entries. */
internal fun gameMenuContent(
    game: GameRef,
    facts: GameFacts,
): PanelContent = PanelContent(title = UiText.Plain(game.title), items = gameMenuEntries(facts).map { PanelItem.Row(it) })

/**
 * Name…: Fix name…, Use my own name, Not a game, and Back to automatic only while the
 * person's fix is stored. Opened in the menu's place; Back returns to the menu on Name….
 */
internal fun nameMenuEntries(facts: GameFacts): List<PanelRow> =
    listOfNotNull(
        // An installed app listed as a game is named by its app: no name list to fix from, and
        // "not a game" is the app menu's answer, not a naming one. The person's own name is still theirs to give.
        if (facts.isAppGame) null else PanelRow(NameEntryId.FixName.name, UiText.Res(R.string.game_menu_fix_name), PanelCommand.OpenFixName),
        PanelRow(NameEntryId.OwnName.name, UiText.Res(R.string.game_menu_own_name), PanelCommand.OpenOwnName),
        if (facts.isAppGame) null else PanelRow(NameEntryId.NotAGame.name, UiText.Res(R.string.game_menu_not_a_game), PanelCommand.AskNotAGame),
        if (facts.name?.hasFix == true) {
            PanelRow(NameEntryId.BackToAutomatic.name, UiText.Res(R.string.game_menu_back_to_automatic), PanelCommand.BackToAutomatic)
        } else {
            null
        },
    )

internal fun nameMenuContent(
    game: GameRef,
    facts: GameFacts,
): PanelContent = PanelContent(title = UiText.Plain(game.title), items = nameMenuEntries(facts).map { PanelItem.Row(it) })

/**
 * Not a game's confirm step: Cancel first, so it has the focus and a stray A changes nothing. The
 * body says where the game comes back (Settings › Library › Hidden games).
 */
internal fun notAGameConfirmContent(game: GameRef): PanelContent =
    PanelContent(
        title = UiText.Res(R.string.game_not_a_game_title),
        body = listOf(UiText.Res(R.string.game_not_a_game_body, listOf(game.title))),
        items = listOf(
            PanelItem.Row(PanelRow(CANCEL_KEY, UiText.Res(R.string.game_action_cancel), PanelCommand.Back)),
            PanelItem.Row(PanelRow(CONFIRM_NOT_A_GAME_KEY, UiText.Res(R.string.game_not_a_game_confirm), PanelCommand.ConfirmNotAGame)),
        ),
    )

/**
 * Not a game on an installed app: taking it out of the games deletes its row, with its play time,
 * favorite and hidden mark. Cancel first, so a stray A changes nothing.
 */
internal fun appNotAGameConfirmContent(game: GameRef): PanelContent =
    PanelContent(
        title = UiText.Res(R.string.game_not_a_game_title),
        body = listOf(UiText.Res(R.string.game_app_not_a_game_body, listOf(game.title))),
        items = listOf(
            PanelItem.Row(PanelRow(CANCEL_KEY, UiText.Res(R.string.game_action_cancel), PanelCommand.Back)),
            PanelItem.Row(PanelRow(CONFIRM_NOT_A_GAME_KEY, UiText.Res(R.string.game_not_a_game_confirm), PanelCommand.SetAppKind(AppKind.APP))),
        ),
    )

/**
 * Fix name… for a system without a usable name list: says so and offers Use my own name (and
 * Back). [list] is [TitleList.NoList] or [TitleList.Unavailable].
 */
internal fun noTitleListContent(list: TitleList): PanelContent {
    val body = when (list) {
        is TitleList.NoList -> UiText.Res(R.string.game_fix_name_no_list, listOf(list.systemName))
        else -> UiText.Res(R.string.game_fix_name_list_unavailable)
    }
    return PanelContent(
        title = UiText.Res(R.string.game_fix_name_title),
        body = listOf(body),
        items = listOf(
            PanelItem.Row(PanelRow(NameEntryId.OwnName.name, UiText.Res(R.string.game_menu_own_name), PanelCommand.OpenOwnName)),
            PanelItem.Row(PanelRow(INFO_BACK_KEY, UiText.Res(R.string.game_action_back), PanelCommand.Back)),
        ),
    )
}

/**
 * "Play with": Automatic (naming what it starts, or saying Play asks while nothing is chosen for
 * the system) first, then each installed emulator, the
 * person's current choice with the "Current" badge; after the "Not installed" heading, the others
 * with "Get it". Legal-risk emulators carry the legal-risk line wherever they are listed. When
 * Automatic would start the person's system choice and that is not installed, Automatic cannot be
 * picked (it would fail the same way) and the body says so.
 */
internal fun playWithContent(listing: EmulatorListing): PanelContent {
    val current = listing.currentEmulatorId
    val rows = buildList {
        automaticRow(listing)?.let(::add)
        listing.installed.forEach { emulator ->
            add(
                PanelItem.Row(
                    PanelRow(
                        key = emulator.id,
                        label = UiText.Plain(emulator.name),
                        command = PanelCommand.PlayWith(emulator.id),
                        isCurrent = emulator.id == current,
                        note = legalRiskNote(emulator),
                    ),
                ),
            )
        }
        addAll(notInstalledItems(listing.notInstalled, current))
    }
    val automatic = listing.automatic
    val body = when {
        automatic != null && !listing.isAutomaticInstalled ->
            listOf(UiText.Res(R.string.game_play_with_system_choice_missing, listOf(automatic.name, listing.systemName)))
        automatic == null && listing.installed.isEmpty() -> listOf(UiText.Res(R.string.game_no_emulator_title, listOf(listing.systemName)))
        else -> emptyList()
    }
    val content = PanelContent(title = UiText.Res(R.string.game_play_with_title), body = body, items = rows)
    if (content.focusableRows.isNotEmpty()) return content
    // Nothing to pick or get (only legal-risk emulators, none installed): say why, and keep one
    // focused row, Back to the menu.
    return content.copy(
        body = body + UiText.Res(R.string.game_error_no_emulator_offered_body),
        items = rows + PanelItem.Row(PanelRow(INFO_BACK_KEY, UiText.Res(R.string.game_action_back), PanelCommand.Back)),
    )
}

/**
 * Play with's Automatic (the game's choice forgotten): the system's emulator, or "ask which
 * emulator" while nothing is chosen for the system (Play then asks); none when nothing
 * would start or be asked about.
 */
private fun automaticRow(listing: EmulatorListing): PanelItem? {
    val label: UiText
    var isEnabled = true
    if (!listing.isSystemChosen) {
        if (listing.installed.isEmpty()) return null
        label = UiText.Res(R.string.game_play_with_ask)
    } else {
        val automatic = listing.automatic ?: return null
        isEnabled = listing.isAutomaticInstalled
        label = UiText.Res(if (isEnabled) R.string.game_play_with_automatic else R.string.game_play_with_automatic_missing, listOf(automatic.name))
    }
    return PanelItem.Row(
        PanelRow(key = AUTOMATIC_KEY, label = label, command = PanelCommand.PlayWith(null), isEnabled = isEnabled, isCurrent = listing.currentEmulatorId == null),
    )
}

/**
 * Emulators to get: the "Not installed" heading, then each with "Get it" (a chosen one marked
 * current). Legal-risk ones get no "Get it": they are not on Google Play, and Plumemu downloads only
 * from a project's own page, which the platform data does not hold yet; they show
 * with the legal-risk line only and cannot be chosen.
 */
internal fun notInstalledItems(
    emulators: List<EmulatorItem>,
    currentEmulatorId: String? = null,
    withHeading: Boolean = true,
): List<PanelItem> {
    if (emulators.isEmpty()) return emptyList()
    val heading = if (withHeading) listOf(PanelItem.Heading(UiText.Res(R.string.game_play_with_not_installed))) else emptyList()
    return heading +
        emulators.map { emulator ->
            PanelItem.Row(
                PanelRow(
                    key = "get:${emulator.id}",
                    label = UiText.Plain(emulator.name),
                    command = PanelCommand.GetEmulator(emulator.packageName, emulator.name),
                    isEnabled = !emulator.isLegalRisk,
                    value = if (emulator.isLegalRisk) null else UiText.Res(R.string.game_get_it),
                    isCurrent = emulator.id == currentEmulatorId,
                    note = legalRiskNote(emulator),
                ),
            )
        }
}

private fun legalRiskNote(emulator: EmulatorItem): UiText? = if (emulator.isLegalRisk) UiText.Res(R.string.game_legal_risk_note) else null

internal const val AUTOMATIC_KEY = "automatic"
internal const val CANCEL_KEY = "cancel"
internal const val CONFIRM_NOT_A_GAME_KEY = "confirm_not_a_game"
