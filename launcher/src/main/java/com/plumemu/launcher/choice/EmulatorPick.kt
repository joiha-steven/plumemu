package com.plumemu.launcher.choice

import com.plumemu.platforms.Emulator
import com.plumemu.platforms.GameSystem
import com.plumemu.platforms.PackageVariant
import com.plumemu.platforms.PlatformCatalog

/**
 * Where the emulator of a launch came from, strongest first. A launch with no choice stored asks the
 * person ([EmulatorPick.NotChosen]; nothing starts before the person chooses). [RecommendedForFileType]: the
 * system has a choice that cannot open this game's file type (WinNative for a `.steam` file), so it
 * is passed over for this game and the recommendation starts it, without asking.
 */
enum class ChoiceSource {
    PersonForGame,
    PersonForSystem,
    RecommendedForFileType,

    /** No emulator: a pinned shortcut the app that published it starts. */
    PinnedShortcut,

    /** No emulator: an installed app listed as a game (Android games, Ports apps), started by its package. */
    InstalledApp,
}

/** Which emulator starts a game, or why none can. */
sealed interface EmulatorPick {
    /**
     * [packageName] is the installed build of [emulator]. [coreToConfirm] is the RetroArch core the
     * launch asks for: RetroArch keeps cores in its private storage, so whether it has the core is
     * known only when the game starts (null for emulators without cores).
     */
    data class Ready(
        val emulator: Emulator,
        val packageName: String,
        val source: ChoiceSource,
        val coreToConfirm: String?,
    ) : EmulatorPick

    /**
     * The person chose [emulatorId] and no build of it is installed (or the platform data no longer
     * lists it for the system, then [emulator] is null). Reported, never silently replaced.
     */
    data class ChosenMissing(val emulator: Emulator?, val emulatorId: String, val source: ChoiceSource) : EmulatorPick

    /**
     * No choice is stored for the game or its system and at least one of the system's emulators is
     * installed: the person picks one, which is then kept for the system. [installed] is
     * in the data's priority order with [recommended] first; [recommended] is the first installed
     * default candidate, null when only legal-risk emulators are installed (never recommended).
     */
    data class NotChosen(val systemId: String, val installed: List<Emulator>, val recommended: Emulator?) : EmulatorPick

    /** No choice was made and no emulator of the system is installed; [candidates] (never legal-risk) may be offered. */
    data class NoneInstalled(val systemId: String, val candidates: List<Emulator>) : EmulatorPick
}

/**
 * The order of ARCHITECTURE > Launching: the person's choice for the game, then for the system;
 * with neither, nothing starts by itself: the installed emulators of the system are returned for
 * the person to choose from, the first installed default candidate (never a legal-risk one) as the
 * recommendation. Choices are emulator ids. Pure: the stored choices and an "is this
 * build launchable" check come in. With the game's [fileName], only emulators that can open it count
 * (the data's `accepts`: a `.steam` file never goes to WinNative), offered or recommended. A game
 * choice that cannot open it, or that the system no longer lists (the file moved to another system),
 * is passed over as if not made. A system choice that cannot open it is passed over for this game
 * only: the recommendation starts it, without asking ([ChoiceSource.RecommendedForFileType]); the
 * question is asked only when the system has no choice at all.
 */
internal object EmulatorResolver {
    fun resolve(
        catalog: PlatformCatalog,
        system: GameSystem,
        gameChoice: String?,
        personSystemChoice: String?,
        isLaunchable: (PackageVariant) -> Boolean,
        fileName: String? = null,
    ): EmulatorPick {
        // A system choice the data no longer lists still counts (reported as missing, never swapped).
        fun opensFile(id: String) = fileName == null || emulatorOf(catalog, system, id)?.acceptsFile(fileName) != false
        // A game choice the system does not list (its file moved to another system, or a data update
        // dropped it) is not made: the game follows its system.
        val game = gameChoice?.takeIf { id -> emulatorOf(catalog, system, id)?.let { fileName == null || it.acceptsFile(fileName) } == true }
        if (game != null) return chosen(catalog, system, game, ChoiceSource.PersonForGame, isLaunchable)
        if (personSystemChoice != null && opensFile(personSystemChoice)) {
            return chosen(catalog, system, personSystemChoice, ChoiceSource.PersonForSystem, isLaunchable)
        }
        val emulators = if (fileName == null) catalog.emulatorsOf(system) else catalog.emulatorsFor(system, fileName)
        val installed = emulators.filter { emulator -> emulator.allPackages.any(isLaunchable) }
        if (installed.isEmpty()) {
            val candidates = if (fileName == null) catalog.defaultCandidates(system.id) else catalog.defaultCandidatesFor(system.id, fileName)
            return EmulatorPick.NoneInstalled(system.id, candidates)
        }
        if (personSystemChoice != null) {
            // The person chose for the system, not for this file type: the recommendation starts it.
            // Only legal-risk ones installed (none is recommended): the question, as with no choice.
            val recommended = installed.firstOrNull { !it.legalRisk }
            if (recommended != null) {
                val variant = recommended.allPackages.first(isLaunchable)
                return EmulatorPick.Ready(recommended, variant.packageName, ChoiceSource.RecommendedForFileType, recommended.core)
            }
        }
        val recommended = installed.firstOrNull { !it.legalRisk }
        val ordered = listOfNotNull(recommended) + installed.filter { it != recommended }
        return EmulatorPick.NotChosen(system.id, ordered, recommended)
    }

    /** [emulatorId] when the system lists it (the person's list may include legal-risk emulators). */
    fun emulatorOf(catalog: PlatformCatalog, system: GameSystem, emulatorId: String): Emulator? =
        catalog.emulatorsOf(system).firstOrNull { it.id == emulatorId }

    private fun chosen(
        catalog: PlatformCatalog,
        system: GameSystem,
        emulatorId: String,
        source: ChoiceSource,
        isLaunchable: (PackageVariant) -> Boolean,
    ): EmulatorPick {
        val emulator = emulatorOf(catalog, system, emulatorId)
            ?: return EmulatorPick.ChosenMissing(null, emulatorId, source)
        val variant = emulator.allPackages.firstOrNull(isLaunchable)
            ?: return EmulatorPick.ChosenMissing(emulator, emulatorId, source)
        return EmulatorPick.Ready(emulator, variant.packageName, source, emulator.core)
    }
}
