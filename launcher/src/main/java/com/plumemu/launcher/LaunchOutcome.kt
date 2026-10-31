package com.plumemu.launcher

import com.plumemu.launcher.choice.ChoiceSource

/**
 * An emulator a message can name and offer from its store page (`market://details?id=<packageName>`).
 * [name] and [packageName] are null for a chosen emulator the platform data no longer lists.
 * [isLegalRisk]: nothing starts before the person chooses; such an emulator is never offered from a store.
 */
data class EmulatorOffer(val emulatorId: String, val name: String?, val packageName: String?, val isLegalRisk: Boolean = false)

/** Why the game's file could not be reached. */
enum class FileProblem {
    /** The file is no longer in the folder. */
    Gone,

    /** The folder cannot be read: access was revoked, or the SD card or drive is out. */
    FolderUnreachable,

    /** The file is on a storage volume (SD card, USB drive) that is not mounted; putting it in is the cure, a rescan is not. */
    StorageNotFound,
}

/** Why a PS Vita game file gave no title ID. */
enum class TitleIdProblem {
    /** The file is empty or holds only spaces and line ends. */
    Empty,

    /** The file holds something else: not nine capital letters or digits, or more than one line of text. */
    NotATitleId,
}

/** Why a Steam game file gave no app id. */
enum class SteamAppIdProblem {
    /** The file is empty or holds only spaces and line ends. */
    Empty,

    /** The file holds something else: not only digits, 0, more than an Int, or more than [com.plumemu.platforms.SteamAppId.MAX_FILE_BYTES]. */
    NotAnAppId,
}

/** Why Android did not start a pinned shortcut. */
enum class ShortcutProblem {
    /** Android starts other apps' shortcuts only for the default home app, and Plumemu is not it. */
    NotHomeApp,

    /** The app disabled or removed the shortcut, or the app itself (or its user) is gone. */
    Unavailable,

    /** Android listed the shortcut but could not start it (no activity, a locked user, a system error). */
    StartFailed,
}

/** Why Android did not start an installed app listed as a game (Android games, Ports apps). */
enum class AppGameProblem {
    /** The app was uninstalled or disabled, or has no launcher activity any more. */
    NotInstalled,

    /** Android refused the start, or `startActivity` threw anything else. */
    StartFailed,
}

/** Why a launch failed for a reason that is not the person's to fix with an emulator or a file. */
enum class FailureReason {
    /** The game id is not in the library (removed by a rescan since the list was drawn). */
    GameNotInLibrary,

    /** The game's system is not in the platform data (a data update dropped it). */
    UnknownSystem,

    /** The emulator is installed but Android found no activity for the Intent. */
    ActivityNotFound,

    /** Android refused to start the emulator (activity not exported, grant refused). */
    NotAllowed,

    /** Android failed to start the emulator for another reason (a bad Intent, a system error). */
    StartFailed,

    /** The library database could not be read (disk full, locked, damaged). */
    DatabaseError,

    /** The platform data and the location disagree in a way that cannot happen with good data. */
    Internal,
}

/** The result of [GameLauncher.launch]; every case except [Started] needs a message on screen. */
sealed interface LaunchOutcome {
    val gameId: Long

    data class Started(
        override val gameId: Long,
        val emulatorId: String,
        val packageName: String,
        val source: ChoiceSource,
        /** RetroArch core the launch asked for, when the person has not yet said it starts (`ConfirmedCores`); null otherwise. */
        val coreToConfirm: String?,
    ) : LaunchOutcome

    /** The emulator the person chose for this game or system is not installed. */
    data class EmulatorMissing(override val gameId: Long, val emulator: EmulatorOffer, val source: ChoiceSource) :
        LaunchOutcome

    /** [fileName] is for the message on screen only; it never goes into a log line. */
    data class FileMissing(override val gameId: Long, val fileName: String, val problem: FileProblem) : LaunchOutcome

    /**
     * A PS Vita game is a small `.psvita` text file holding the game's title ID (PCSE00000), ES-DE's
     * convention, and this one does not. [fileName] is for the message on screen only; never logged.
     */
    data class BadTitleIdFile(override val gameId: Long, val fileName: String, val problem: TitleIdProblem) : LaunchOutcome

    /**
     * A Steam game is a small `.steam` text file holding the game's Steam app id (274190), ES-DE's
     * convention, and this one does not. [fileName] is for the message on screen only; never logged.
     */
    data class BadSteamAppIdFile(override val gameId: Long, val fileName: String, val problem: SteamAppIdProblem) : LaunchOutcome

    /** The emulator opens files by path and this folder has none (a USB drive through a provider). */
    data class NeedsFilePath(override val gameId: Long, val emulator: EmulatorOffer) : LaunchOutcome

    /**
     * Nothing is chosen for the game or its system and [installed] (recommended first) are: the person
     * picks one, kept for the system. [recommendedId] is null when only legal-risk ones are installed.
     * [systemName] is the system's display name, so the question needs no second detection.
     */
    data class EmulatorNotChosen(
        override val gameId: Long,
        val systemId: String,
        val systemName: String,
        val installed: List<EmulatorOffer>,
        val recommendedId: String?,
    ) : LaunchOutcome

    /** No emulator of the system is installed and none was chosen; [candidates] best first, never legal-risk. */
    data class NoEmulatorForSystem(override val gameId: Long, val systemId: String, val candidates: List<EmulatorOffer>) :
        LaunchOutcome

    /**
     * Android did not start a pinned shortcut (Steam, Windows, Shortcuts). [appName] is the label of the app that published it, null when that app is gone or its
     * label cannot be read.
     */
    data class ShortcutRefused(override val gameId: Long, val appName: String?, val problem: ShortcutProblem) : LaunchOutcome

    /**
     * Android did not start an installed app listed as a game.
     * [appName] is the row's title (the app's label when it was last listed): the app may be gone.
     */
    data class AppGameRefused(override val gameId: Long, val appName: String, val problem: AppGameProblem) : LaunchOutcome

    data class Failed(override val gameId: Long, val reason: FailureReason, val error: Throwable? = null) : LaunchOutcome
}
