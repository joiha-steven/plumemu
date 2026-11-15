package com.plumemu.launcher.choice

import android.database.sqlite.SQLiteException
import com.plumemu.coredata.db.EmulatorChoiceDao
import com.plumemu.launcher.LauncherLog
import com.plumemu.launcher.detect.InstalledEmulators
import com.plumemu.launcher.kind
import com.plumemu.launcher.rethrowIfCallerCancelled
import com.plumemu.library.emulator.EmulatorDefaults
import com.plumemu.platforms.GameSystem
import com.plumemu.platforms.PackageVariant
import com.plumemu.platforms.PlatformCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The emulator choices kept in core-data's `emulator_choice`, by emulator id: a person's choice (per
 * game or per system) is kept until the person changes it. With none, a launch asks
 * ([EmulatorPick.NotChosen]) and the person's answer is stored with [chooseForSystem]. The automatic
 * default row follows the installed emulators and is only the recommendation that question puts
 * first (and what Settings and setup show); it never starts a game by itself. It is
 * written only through library's [EmulatorDefaults], so a person's choice is never overwritten.
 */
class EmulatorChoices internal constructor(
    private val dao: EmulatorChoiceDao,
    private val catalog: PlatformCatalog,
    private val isLaunchable: (PackageVariant) -> Boolean,
    private val log: LauncherLog,
    private val io: CoroutineDispatcher,
) {
    private val defaults = EmulatorDefaults(dao)

    /**
     * Which emulator starts [gameId] of [system], checking now which builds are installed. [fileName]
     * is the game's file: only emulators that can open it are used, offered or recommended (null:
     * every emulator of the system). With no person's choice, the stored automatic default (the
     * system's recommendation, whatever the file) is brought up to date as well.
     */
    suspend fun resolve(gameId: Long, system: GameSystem, fileName: String? = null): EmulatorPick {
        val gameChoice = dao.gameChoice(gameId)?.emulatorId
        val systemRow = dao.systemChoice(system.id)
        val personSystemChoice = systemRow?.takeIf { it.isPersonChoice }?.emulatorId
        val pick = withContext(io) {
            EmulatorResolver.resolve(catalog, system, gameChoice, personSystemChoice, isLaunchable, fileName)
        }
        if (gameChoice == null && personSystemChoice == null) {
            // The system's row, as refreshDefaults computes it: not the one file's (Steam's .desktop and
            // .steam games would rewrite it in turn).
            val automatic = withContext(io) { catalog.defaultCandidates(system.id).firstOrNull { it.allPackages.any(isLaunchable) }?.id }
            if (automatic != systemRow?.emulatorId) storeAutomaticDefault(system.id, automatic)
        }
        return pick
    }

    /**
     * Whether [gameId]'s own choice changes what a launch starts: the emulator [resolve] picks differs from the one it would pick with the game's
     * choice left out (the system's choice; with none, Play asks, so any usable game choice counts). False with no game choice, with one the game's file cannot use (a launch
     * passes it over), or with the one the system starts anyway. Reads only: it stores nothing. The details column names the emulator only when true.
     */
    suspend fun hasOwnChoice(gameId: Long, system: GameSystem, fileName: String? = null): Boolean {
        val gameChoice = dao.gameChoice(gameId)?.emulatorId ?: return false
        val personSystemChoice = dao.systemChoice(system.id)?.takeIf { it.isPersonChoice }?.emulatorId
        return withContext(io) {
            val with = EmulatorResolver.resolve(catalog, system, gameChoice, personSystemChoice, isLaunchable, fileName)
            val without = EmulatorResolver.resolve(catalog, system, null, personSystemChoice, isLaunchable, fileName)
            startedIdOf(with) != startedIdOf(without)
        }
    }

    private fun startedIdOf(pick: EmulatorPick): String? =
        when (pick) {
            is EmulatorPick.Ready -> pick.emulator.id
            is EmulatorPick.ChosenMissing -> pick.emulatorId
            // Nothing starts by itself: Play would ask. A game choice that starts something is therefore the game's own, the recommendation included.
            is EmulatorPick.NotChosen -> null
            is EmulatorPick.NoneInstalled -> null
        }

    /**
     * Recomputes the automatic default of each of [systemIds] after a detection; a person's choice
     * stays. Returns the emulator id in force per system (null when none is installed).
     */
    suspend fun refreshDefaults(systemIds: Collection<String>, installed: InstalledEmulators): Map<String, String?> {
        val priorities = systemIds.associateWith { id -> catalog.defaultCandidates(id).map { it.id } }
        return defaults.apply(priorities, installed.emulatorIds)
    }

    /** The person picked [emulatorId] for every game of [system] (Settings > Emulators, or the first launch's question). */
    suspend fun chooseForSystem(system: GameSystem, emulatorId: String) {
        requireListed(system, emulatorId)
        dao.setPersonSystemChoice(system.id, emulatorId)
    }

    /**
     * Settings › Emulators › Automatic: the person's choice for [system] is forgotten and the
     * automatic default of [installed] is stored at once, so the next launch and Settings agree.
     */
    suspend fun forgetSystemChoice(system: GameSystem, installed: InstalledEmulators?) {
        dao.clearPersonSystemChoice(system.id)
        if (installed != null) refreshDefaults(listOf(system.id), installed)
    }

    /** The person picked [emulatorId] for one game ("Play with"), RetroArch core included. */
    suspend fun chooseForGame(gameId: Long, system: GameSystem, emulatorId: String) {
        requireListed(system, emulatorId)
        dao.setPersonGameChoice(gameId, system.id, emulatorId)
    }

    /** The game goes back to its system's emulator. */
    suspend fun forgetGameChoice(gameId: Long) = dao.clearGameChoice(gameId)

    /**
     * The core that never started ([emulatorId]) is forgotten wherever the person had chosen it: for
     * [gameId] and for [system]. A choice of any other emulator stays. The next launch asks again.
     */
    suspend fun forgetChoicesOf(gameId: Long, system: GameSystem, emulatorId: String) {
        if (dao.gameChoice(gameId)?.emulatorId == emulatorId) dao.clearGameChoice(gameId)
        val systemRow = dao.systemChoice(system.id)
        if (systemRow?.isPersonChoice == true && systemRow.emulatorId == emulatorId) dao.clearPersonSystemChoice(system.id)
    }

    /** Secondary to the launch: a failed write is logged and the launch goes on with the answer. */
    private suspend fun storeAutomaticDefault(systemId: String, emulatorId: String?) {
        try {
            dao.setAutomaticSystemDefault(systemId, emulatorId)
        } catch (e: CancellationException) {
            e.rethrowIfCallerCancelled()
            log.warn("emulator choice: could not store the automatic default of $systemId (${e.kind()})")
        } catch (e: SQLiteException) {
            log.warn("emulator choice: could not store the automatic default of $systemId (${e.kind()})")
        } catch (e: IllegalStateException) {
            log.warn("emulator choice: could not store the automatic default of $systemId (${e.kind()})")
        }
    }

    private fun requireListed(system: GameSystem, emulatorId: String) {
        require(EmulatorResolver.emulatorOf(catalog, system, emulatorId) != null) {
            "emulator $emulatorId is not listed for system ${system.id}"
        }
    }
}
