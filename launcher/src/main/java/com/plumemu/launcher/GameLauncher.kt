package com.plumemu.launcher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.database.sqlite.SQLiteException
import com.plumemu.coredata.db.GameDao
import com.plumemu.coredata.db.GameLaunchTarget
import com.plumemu.launcher.choice.ConfirmedCores
import com.plumemu.launcher.choice.CoreToConfirm
import com.plumemu.launcher.choice.EmulatorChoices
import com.plumemu.launcher.choice.EmulatorPick
import com.plumemu.launcher.intent.GameLocations
import com.plumemu.launcher.intent.LaunchIntents
import com.plumemu.launcher.play.PlayTimeTracker
import com.plumemu.library.storage.StorageRoots
import com.plumemu.platforms.Emulator
import com.plumemu.platforms.LaunchResult
import com.plumemu.platforms.LaunchSpecs
import com.plumemu.platforms.PassMode
import com.plumemu.platforms.PlatformCatalog
import com.plumemu.platforms.SteamAppId
import com.plumemu.platforms.VitaTitleId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Starts an activity; a seam so JVM tests can record the Intent or make the start fail. */
internal fun interface ActivityStarter {
    fun start(intent: Intent)
}

/**
 * Opens a game in its emulator: resolves the emulator (person's game choice, system choice,
 * automatic default), checks the file is still there, builds the Intent, notes the start for play
 * time and starts the emulator. Never throws for a failure: every outcome is a [LaunchOutcome] and
 * one log line with ids and exception classes only, never a title, file name or path.
 * Get it from [Launching].
 */
class GameLauncher internal constructor(
    private val catalog: PlatformCatalog,
    private val gameDao: GameDao,
    private val choices: EmulatorChoices,
    private val probe: DocumentProbe,
    private val fileReader: SmallFileReader,
    private val starter: ActivityStarter,
    private val playTime: PlayTimeTracker,
    private val log: LauncherLog,
    private val io: CoroutineDispatcher,
    private val volumes: StorageVolumes = StorageVolumes.AllPresent,
    /** RetroArch cores the person said started: a launch with one of them asks nothing afterwards. */
    private val confirmedCores: ConfirmedCores = ConfirmedCores.None,
    /** Where internal storage and SD cards are on this device, for emulators that open a file path. */
    private val storageRoots: StorageRoots,
) {
    /**
     * Starts the row [gameId]. [fileOf] is the row whose file is opened: the row's own by default,
     * or another copy of its game the person chose (the game menu's Other versions, library's
     * `copyToPlay`). The file, its system and which emulators can open it are [fileOf]'s; the
     * person's emulator choice, the play time, Recent and every outcome are [gameId]'s, the row the
     * person launched from, so its marks never split between copies.
     */
    suspend fun launch(gameId: Long, fileOf: Long = gameId): LaunchOutcome {
        val outcome = try {
            attempt(gameId, fileOf)
        } catch (e: CancellationException) {
            e.rethrowIfCallerCancelled()
            LaunchOutcome.Failed(gameId, FailureReason.DatabaseError, e)
        } catch (e: SQLiteException) {
            LaunchOutcome.Failed(gameId, FailureReason.DatabaseError, e)
        } catch (e: IllegalStateException) {
            // Room reports a closed database this way.
            LaunchOutcome.Failed(gameId, FailureReason.DatabaseError, e)
        }
        if (fileOf != gameId) log.info("launch: game $gameId opens the file of its copy $fileOf")
        logOutcome(outcome)
        return outcome
    }

    private suspend fun attempt(gameId: Long, fileOf: Long): LaunchOutcome {
        // The row launched from must still be there: its play time and choices are recorded on it.
        if (fileOf != gameId && gameDao.launchTarget(gameId) == null) return LaunchOutcome.Failed(gameId, FailureReason.GameNotInLibrary)
        val target = gameDao.launchTarget(fileOf)?.copy(id = gameId) ?: return LaunchOutcome.Failed(gameId, FailureReason.GameNotInLibrary)
        val system = catalog.system(target.systemId) ?: return LaunchOutcome.Failed(gameId, FailureReason.UnknownSystem)
        val pick = when (val resolved = choices.resolve(gameId, system, target.fileName)) {
            is EmulatorPick.Ready -> resolved
            is EmulatorPick.ChosenMissing -> return LaunchOutcome.EmulatorMissing(
                gameId,
                EmulatorOffer(resolved.emulatorId, resolved.emulator?.name, resolved.emulator?.primary?.packageName, resolved.emulator?.legalRisk == true),
                resolved.source,
            )
            is EmulatorPick.NotChosen -> return LaunchOutcome.EmulatorNotChosen(
                gameId, system.id, system.displayName, resolved.installed.map { offerOf(it, it.primary.packageName) }, resolved.recommended?.id,
            )
            is EmulatorPick.NoneInstalled -> return LaunchOutcome.NoEmulatorForSystem(
                gameId, system.id, resolved.candidates.map { offerOf(it, it.primary.packageName) },
            )
        }
        val location = GameLocations.of(target, storageRoots)
        val documentUri = GameLocations.documentUriOf(location)
            ?: return LaunchOutcome.Failed(gameId, FailureReason.Internal)
        // A PS Vita or Steam emulator needs the id inside the game file before its Intent can be built,
        // so its file is checked and read first. The others are built first: a path emulator on a
        // folder without paths says so without asking the provider.
        val readsId = pick.emulator.game.mode.takeIf { it == PassMode.VitaTitleId || it == PassMode.SteamAppId }
        val early = if (readsId != null) checkFile(target, documentUri, readsId) else null
        if (early is FileCheck.Stop) return early.outcome
        val ready = early as? FileCheck.Ready
        val built = LaunchSpecs.build(pick.emulator, location, pick.packageName, ready?.vitaTitleId, ready?.steamAppId)
        val spec = when (built) {
            is LaunchResult.Built -> built.spec
            is LaunchResult.NeedsFilePath -> return LaunchOutcome.NeedsFilePath(gameId, offerOf(pick.emulator, pick.packageName))
            is LaunchResult.NeedsDocumentUri, is LaunchResult.UnknownPackage, is LaunchResult.NeedsVitaTitleId,
            is LaunchResult.NeedsSteamAppId,
            -> return LaunchOutcome.Failed(gameId, FailureReason.Internal)
        }
        val file = when (val checked = early ?: checkFile(target, documentUri, readsId = null)) {
            is FileCheck.Ready -> checked
            is FileCheck.Stop -> return checked.outcome
        }
        val grantUri = if (pick.emulator.game.mode == PassMode.Uri) documentUri else null
        val intent = LaunchIntents.build(spec, grantUri, file.mimeType)
        return start(gameId, intent, pick)
    }

    private sealed interface FileCheck {
        /** [vitaTitleId] or [steamAppId] only when it was asked for. */
        data class Ready(val mimeType: String?, val vitaTitleId: VitaTitleId? = null, val steamAppId: SteamAppId? = null) : FileCheck

        data class Stop(val outcome: LaunchOutcome) : FileCheck
    }

    /**
     * One provider query says the file is there; with [readsId] ([PassMode.VitaTitleId] or
     * [PassMode.SteamAppId]), its first bytes are read and must be that id. Never more than the id's
     * `MAX_FILE_BYTES` + 1 bytes are read.
     */
    private suspend fun checkFile(target: GameLaunchTarget, documentUri: String, readsId: PassMode?): FileCheck {
        val mimeType = when (val state = withContext(io) { probe.probe(documentUri) }) {
            is DocumentState.Present -> state.mimeType
            DocumentState.Missing ->
                return FileCheck.Stop(
                    if (volumes.isAbsent(documentUri)) {
                        storageNotFound(target)
                    } else {
                        LaunchOutcome.FileMissing(target.id, target.fileName, FileProblem.Gone)
                    },
                )
            is DocumentState.Unreachable ->
                return FileCheck.Stop(if (volumes.isAbsent(documentUri)) storageNotFound(target) else fileUnreachable(target, state.error))
        }
        if (readsId == null) return FileCheck.Ready(mimeType)
        val steam = readsId == PassMode.SteamAppId
        val maxBytes = if (steam) SteamAppId.MAX_FILE_BYTES else VitaTitleId.MAX_FILE_BYTES
        val read = when (val result = withContext(io) { fileReader.read(documentUri, maxBytes) }) {
            is SmallFileRead.Read -> result
            is SmallFileRead.Unreadable -> {
                log.warn("launch: game ${target.id} reading the game file failed (${result.error.kind()})")
                return FileCheck.Stop(LaunchOutcome.FileMissing(target.id, target.fileName, FileProblem.FolderUnreachable))
            }
        }
        return if (steam) steamAppIdOf(target, mimeType, read, maxBytes) else vitaTitleIdOf(target, mimeType, read)
    }

    private fun vitaTitleIdOf(target: GameLaunchTarget, mimeType: String?, read: SmallFileRead.Read): FileCheck {
        val titleId = if (read.complete) VitaTitleId.parse(read.bytes) else null
        if (titleId != null) return FileCheck.Ready(mimeType, vitaTitleId = titleId)
        val problem = if (read.complete && VitaTitleId.isEmptyFile(read.bytes)) TitleIdProblem.Empty else TitleIdProblem.NotATitleId
        return FileCheck.Stop(LaunchOutcome.BadTitleIdFile(target.id, target.fileName, problem))
    }

    private fun steamAppIdOf(target: GameLaunchTarget, mimeType: String?, read: SmallFileRead.Read, maxBytes: Int): FileCheck {
        val appId = if (read.complete) SteamAppId.parse(read.bytes) else null
        if (appId != null) return FileCheck.Ready(mimeType, steamAppId = appId)
        val problem = if (read.complete && SteamAppId.isEmptyFile(read.bytes)) SteamAppIdProblem.Empty else SteamAppIdProblem.NotAnAppId
        // Sizes only, never the content or the name: enough to tell an empty file, text
        // that is not digits and a large file (a game image renamed) apart.
        val size = if (read.complete) "${read.bytes.size} bytes" else "over $maxBytes bytes"
        log.warn("launch: game ${target.id}: the .steam file holds no Steam app id ($problem, $size)")
        return FileCheck.Stop(LaunchOutcome.BadSteamAppIdFile(target.id, target.fileName, problem))
    }

    private suspend fun start(gameId: Long, intent: Intent, pick: EmulatorPick.Ready): LaunchOutcome {
        // Noted before the start: Android may kill Plumemu as soon as the emulator takes the memory.
        val core = pick.coreToConfirm?.let { CoreToConfirm(pick.packageName, it) }
            ?.takeUnless { withContext(io) { confirmedCores.isConfirmed(it) } }
        val failure = try {
            // The note and the start are one step a cancel cannot split (a caller giving up on a slow
            // start must leave no half-recorded play): see PlayTimeTracker.handOff. Failures are mapped
            // inside it, so the outcome carries the very exception thrown.
            playTime.handOff(gameId, pick.emulator.id, core) {
                try {
                    withContext(io) { starter.start(intent) }
                    null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ActivityNotFoundException) {
                    LaunchOutcome.Failed(gameId, FailureReason.ActivityNotFound, e)
                } catch (e: SecurityException) {
                    LaunchOutcome.Failed(gameId, FailureReason.NotAllowed, e)
                } catch (e: RuntimeException) {
                    // startActivity can throw anything from a bad Intent to a dead system service; none may
                    // reach the screen as a crash, and none of them is a played session.
                    LaunchOutcome.Failed(gameId, FailureReason.StartFailed, e)
                }
            }
        } catch (e: CancellationException) {
            // Cancelled before the hand-off: the note is already taken back. After it: the game was
            // handed over and the note is right, so it is kept.
            e.rethrowIfCallerCancelled()
            LaunchOutcome.Failed(gameId, FailureReason.StartFailed, e)
        }
        if (failure != null) {
            playTime.cancelPending()
            return failure
        }
        return LaunchOutcome.Started(gameId, pick.emulator.id, pick.packageName, pick.source, core?.core)
    }

    /** The file's storage volume (SD card, USB drive) is not mounted: the message says insert it, no Rescan. */
    private fun storageNotFound(target: GameLaunchTarget) =
        LaunchOutcome.FileMissing(target.id, target.fileName, FileProblem.StorageNotFound)

    private fun fileUnreachable(target: GameLaunchTarget, error: Throwable): LaunchOutcome {
        log.warn("launch: game ${target.id} provider query failed (${error.kind()})")
        return LaunchOutcome.FileMissing(target.id, target.fileName, FileProblem.FolderUnreachable)
    }

    private fun offerOf(emulator: Emulator, packageName: String) = EmulatorOffer(emulator.id, emulator.name, packageName, emulator.legalRisk)

    private fun logOutcome(outcome: LaunchOutcome) {
        val game = "launch: game ${outcome.gameId}"
        when (outcome) {
            is LaunchOutcome.Started -> log.info(
                "$game started with ${outcome.emulatorId} (${outcome.packageName}, ${outcome.source}" +
                    (outcome.coreToConfirm?.let { ", core $it not yet confirmed" } ?: "") + ")",
            )
            is LaunchOutcome.EmulatorMissing -> log.warn(
                "$game: chosen emulator ${outcome.emulator.emulatorId} (${outcome.source}) is not installed",
            )
            is LaunchOutcome.FileMissing -> log.warn("$game: file not reachable (${outcome.problem})")
            is LaunchOutcome.BadTitleIdFile -> log.warn("$game: the file holds no PS Vita title ID (${outcome.problem})")
            is LaunchOutcome.BadSteamAppIdFile -> log.warn("$game: the file holds no Steam app id (${outcome.problem})")
            is LaunchOutcome.NeedsFilePath -> log.warn(
                "$game: ${outcome.emulator.emulatorId} needs a file path and the folder has none",
            )
            is LaunchOutcome.EmulatorNotChosen -> log.info(
                "$game: no emulator chosen for ${outcome.systemId} yet; asking among " +
                    "${outcome.installed.joinToString { it.emulatorId }} (recommended ${outcome.recommendedId})",
            )
            is LaunchOutcome.NoEmulatorForSystem -> log.warn(
                "$game: no emulator installed for ${outcome.systemId} (${outcome.candidates.size} candidates)",
            )
            // Never made here: the app starts pinned shortcuts itself (AppGameActions), with its own log line.
            is LaunchOutcome.ShortcutRefused -> log.warn("$game: pinned shortcut not started (${outcome.problem})")
            // Never made here either: the app starts app games itself (AppGameActions).
            is LaunchOutcome.AppGameRefused -> log.warn("$game: installed app not started (${outcome.problem})")
            is LaunchOutcome.Failed -> log.warn("$game failed: ${outcome.reason}" + (outcome.error?.let { " (${it.kind()})" } ?: ""))
        }
    }
}
