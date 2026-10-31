package com.plumemu.graph

import android.util.Log
import com.plumemu.coredata.db.HiddenGameRow
import com.plumemu.coredata.db.NameTrustNames
import com.plumemu.game.RenameBoxArtNotice
import com.plumemu.game.TitleChoice
import com.plumemu.game.TitleList
import com.plumemu.identify.LaunchFileSystems
import com.plumemu.library.naming.setHiddenWithArcadeCopies
import com.plumemu.settings.screen.SettingsGame
import com.plumemu.settings.screen.SettingsNameActions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings › Library's naming parts: Unidentified games and its Fix name…, and Hidden
 * games' "Not a game" rows, whose undo is Back to automatic. The writes go through
 * [GraphNameFixes] like the game menu's and run in the process scope, so leaving Settings does not
 * stop them. The flows are built by [GraphSettingsLibrary.content] on collection. Lines carry ids and exception classes only.
 */
internal class GraphSettingsNames(
    private val graph: AppGraph,
    private val io: CoroutineDispatcher,
    private val main: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val log: (String) -> Unit = { Log.w(TAG, it) },
) : SettingsNameActions {
    /** Hidden games: the ones the person hid, then the ones they said are not games. */
    fun hiddenGames(): Flow<List<SettingsGame>> =
        hiddenAndNotGames(
            graph.database.gameDao().observeHiddenGames().map { settingsGames(it, isNotAGame = false) },
            graph.database.identifyDao().observeNotAGame().map { settingsGames(it, isNotAGame = true) },
        )

    /** Games named from their file only; Steam games are named that way by design and never listed. */
    fun unidentifiedGames(): Flow<List<SettingsGame>> =
        graph.database.identifyDao().observeUnidentified(LaunchFileSystems.IDS).map { settingsGames(it, isNotAGame = false) }

    /**
     * Shows a hidden game again: a "Not a game" goes back to automatic (its fix deleted, named
     * again now); a hidden one is unhidden. [onDone] gets whether it was stored.
     */
    fun unhideGame(
        gameId: Long,
        onDone: (Boolean) -> Unit,
    ) = answer("unhide game $gameId", onDone) {
        val row = withContext(io) { graph.database.identifyDao().identificationOf(gameId) }
        if (row?.isNotGame == true && row.nameTrust == NameTrustNames.PERSON_FIX) {
            graph.nameFixes.backToAutomatic(gameId)
        } else {
            // Every copy of an arcade game comes back with it (hiding hid them all).
            withContext(io) { setHiddenWithArcadeCopies(graph.database, graph.naming, gameId, isHidden = false) }
        }
        true
    }

    override fun titleList(
        gameId: Long,
        onDone: (TitleList?) -> Unit,
    ) {
        graph.appScope.launch {
            val list = try {
                val game = withContext(io) { graph.database.gameDao().game(gameId) }
                if (game == null) {
                    log("Settings: game $gameId is no longer in the library")
                    TitleList.GameGone
                } else {
                    graph.nameFixes.titleList(game.systemId, graph.catalog()?.system(game.systemId)?.displayName ?: game.systemId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("Settings: titles of game $gameId failed (${e.javaClass.simpleName})")
                null
            }
            withContext(main) { onDone(list) }
        }
    }

    override fun setChosenTitle(
        gameId: Long,
        choice: TitleChoice,
        onDone: (stored: Boolean, boxArt: RenameBoxArtNotice?) -> Unit,
    ) = renamed("fix name of game $gameId", onDone) { graph.nameFixes.setChosenTitle(gameId, choice) }

    override fun setOwnName(
        gameId: Long,
        name: String,
        onDone: (stored: Boolean, boxArt: RenameBoxArtNotice?) -> Unit,
    ) = renamed("own name of game $gameId", onDone) { graph.nameFixes.setOwnName(gameId, name) }

    /** [answer] for a rename: also why box art could not be looked up by the new name ([GraphNameFixes]). */
    private fun renamed(
        what: String,
        onDone: (stored: Boolean, boxArt: RenameBoxArtNotice?) -> Unit,
        block: suspend () -> RenameBoxArtNotice?,
    ) {
        var boxArt: RenameBoxArtNotice? = null
        answer(what, { stored -> onDone(stored, boxArt.takeIf { stored }) }) {
            boxArt = block()
            true
        }
    }

    private suspend fun settingsGames(
        rows: List<HiddenGameRow>,
        isNotAGame: Boolean,
    ): List<SettingsGame> {
        val catalog = graph.catalog()
        return rows.map { SettingsGame(it.id, it.title, catalog?.system(it.systemId)?.displayName ?: it.systemId, isNotAGame) }
    }

    /** Runs [block] in the process scope and answers on the main thread; a failure is logged and answered false. */
    private fun answer(
        what: String,
        onDone: (Boolean) -> Unit,
        block: suspend () -> Boolean,
    ) {
        graph.appScope.launch {
            val stored = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("Settings: $what failed (${e.javaClass.simpleName})")
                false
            }
            withContext(main) { onDone(stored) }
        }
    }

    private companion object {
        const val TAG = "PlumSettings"
    }
}

/**
 * The two Room flows of Hidden games, joined. They emit out of step, so for one emission a game
 * can be in both lists; the list is keyed by id, so it is shown once, as "Not a game" (the newer fact).
 */
internal fun hiddenAndNotGames(
    hidden: Flow<List<SettingsGame>>,
    notGames: Flow<List<SettingsGame>>,
): Flow<List<SettingsGame>> =
    combine(hidden, notGames) { hiddenList, notList ->
        val notIds = notList.mapTo(HashSet()) { it.id }
        hiddenList.filter { it.id !in notIds } + notList.distinctBy { it.id }
    }
