package com.plumemu.game

import androidx.compose.runtime.Immutable
import com.plumemu.R
import com.plumemu.identify.name.Region
import com.plumemu.library.naming.GameCopies
import com.plumemu.library.naming.GameCopy

/**
 * The game menu's Other versions:
 * the copies of the game a row stands for (regional copies, arcade sets of one game), each
 * labelled by its version, [isCurrent] on the one A plays now. [key] is the copy's row id as text.
 */
@Immutable
internal data class VersionItem(
    val key: String,
    val label: UiText,
    val isCurrent: Boolean,
)

/** Every copy of a row's game, by file name; the menu lists Other versions only with two or more. */
@Immutable
internal data class VersionListing(val versions: List<VersionItem>) {
    val current: VersionItem? get() = versions.firstOrNull { it.isCurrent }

    val hasOthers: Boolean get() = versions.size >= 2
}

/**
 * Labels [copies] by version: an arcade set by its description's bracket details ("USA 930201"),
 * any other copy by its region names ("USA", "USA, Europe"), else by its file name. When two
 * copies get the same label, both carry their file name after it ("USA · Zork (USA).sfc").
 */
internal fun versionListingOf(copies: GameCopies): VersionListing {
    val labels = copies.copies.associate { it.id to versionLabelOf(it) }
    val counts = labels.values.groupingBy { it }.eachCount()
    return VersionListing(
        copies.copies.map { copy ->
            val label = labels.getValue(copy.id)
            val shown =
                when {
                    label.isEmpty() -> UiText.Plain(copy.fileName)
                    (counts[label] ?: 0) > 1 -> UiText.Format(R.string.game_version_with_file, listOf(UiText.Plain(label), UiText.Plain(copy.fileName)))
                    else -> UiText.Plain(label)
                }
            VersionItem(copy.id.toString(), shown, isCurrent = copy.id == copies.playedId)
        },
    )
}

/**
 * The words that tell [copy] apart; empty when nothing does. The details and region names are the
 * data's own (MAME descriptions, No-Intro region names), shown as they are written there.
 */
internal fun versionLabelOf(copy: GameCopy): String =
    if (copy.arcadeDetails.isNotEmpty()) {
        copy.arcadeDetails.joinToString(", ")
    } else {
        copy.regions.joinToString(", ") { code -> Region.fromCode(code)?.noIntroName ?: code }
    }

/** Other versions: one row per copy, the one A plays now with the "Current" badge. */
internal fun versionsContent(listing: VersionListing): PanelContent =
    PanelContent(
        title = UiText.Res(R.string.game_versions_title),
        items = listing.versions.map { version ->
            PanelItem.Row(
                PanelRow(
                    key = versionRowKey(version.key),
                    label = version.label,
                    command = PanelCommand.PlayVersion(version.key),
                    isCurrent = version.isCurrent,
                ),
            )
        },
    )

/** The panel opens on the copy A plays now. */
internal fun versionsPanel(
    game: GameRef,
    listing: VersionListing,
    parent: OpenPanel,
): OpenPanel = OpenPanel(game, PanelScreen.Versions(listing), focus = listing.versions.indexOfFirst { it.isCurrent }.coerceAtLeast(0), parent = parent)

/**
 * [menu] with the versions that arrived after it opened (an arcade set's are read from the pack,
 * which the menu never waits for), the focus kept on the row it was on; [menu] itself when it is no
 * menu of [gameKey] waiting for them.
 */
internal fun OpenPanel.withVersions(
    gameKey: String,
    listing: VersionListing,
): OpenPanel {
    val menu = screen as? PanelScreen.Menu ?: return this
    if (game.key != gameKey || !menu.facts.versionsPending) return this
    val focusedKey = content.focusableRows.getOrNull(focus)?.key
    val updated = copy(screen = PanelScreen.Menu(menu.facts.copy(versions = listing, versionsPending = false)))
    val kept = updated.content.focusableRows.indexOfFirst { it.key == focusedKey }
    return updated.copy(focus = if (kept >= 0) kept else focus)
}

internal fun versionRowKey(copyKey: String) = "version:$copyKey"

/**
 * The Other versions panel [open] read again ([listing]; null when the read failed: as it was), on
 * a menu whose value follows: after a version turned out to be gone.
 */
internal fun refreshedVersions(
    open: OpenPanel,
    listing: VersionListing?,
): OpenPanel {
    if (listing == null) return open
    val parent = open.parent
    val menu = (parent?.screen as? PanelScreen.Menu)?.let { parent.copy(screen = PanelScreen.Menu(it.facts.copy(versions = listing, versionsPending = false))) } ?: parent
    return versionsPanel(open.game, listing, menu ?: open)
}

/** A version chosen in a list read before a rescan removed or renamed its file. OK returns to the list read again. */
internal fun versionGoneMessage(): PanelContent =
    PanelContent(
        title = UiText.Res(R.string.game_version_gone_title),
        body = listOf(UiText.Res(R.string.game_version_gone_body)),
        items = listOf(PanelItem.Row(PanelRow(VERSION_GONE_OK_KEY, UiText.Res(R.string.game_action_ok), PanelCommand.Back))),
    )

internal const val VERSION_GONE_OK_KEY = "version_gone_ok"
