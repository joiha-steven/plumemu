package com.plumemu.setup

import com.plumemu.coredata.settings.BoxArtSourceSetting
import com.plumemu.coreinput.Direction
import com.plumemu.library.scan.AddFolderResult
import kotlinx.coroutines.CancellationException

/*
 * The order of setup's steps and two rules the model leans on. The scan runs behind the questions;
 * Scan and Ready go back to the last question asked (the scan goes on), every question to the one before it.
 */

/** The step after [from]: the confirm step only with a gamepad, the second screen only when it is asked. */
internal fun stepAfter(
    from: SetupStep,
    hasConfirmStep: Boolean,
    asksSecondScreen: Boolean,
): SetupStep =
    when (from) {
        SetupStep.Folders -> if (hasConfirmStep) SetupStep.Confirm else SetupStep.Look
        SetupStep.Confirm -> SetupStep.Look
        SetupStep.Look -> SetupStep.BoxArt
        SetupStep.BoxArt -> if (asksSecondScreen) SetupStep.SecondScreen else SetupStep.Scan
        SetupStep.SecondScreen -> SetupStep.Scan
        SetupStep.Scan, SetupStep.Ready -> from
    }

/**
 * Where Back goes from [step]; null on the folders. Scan and Ready go to [lastQuestion] (the question
 * answered just before them), the folders when none was asked (Set up later).
 */
internal fun previousStepOf(
    step: SetupStep,
    hasConfirmStep: Boolean,
    lastQuestion: SetupStep? = null,
): SetupStep? =
    when (step) {
        SetupStep.Folders -> null
        SetupStep.Confirm -> SetupStep.Folders
        SetupStep.Look -> if (hasConfirmStep) SetupStep.Confirm else SetupStep.Folders
        SetupStep.BoxArt -> SetupStep.Look
        SetupStep.SecondScreen -> SetupStep.BoxArt
        SetupStep.Scan, SetupStep.Ready -> lastQuestion ?: SetupStep.Folders
    }

/** The index of the look tile a move reaches from [at] (a grid of [SetupLooks.COLUMNS] columns); -1 at an edge. */
internal fun lookNeighbour(
    at: Int,
    direction: Direction,
    count: Int,
): Int {
    val columns = SetupLooks.COLUMNS
    val next =
        when (direction) {
            Direction.Left -> if (at % columns == 0) -1 else at - 1
            Direction.Right -> if (at % columns == columns - 1) -1 else at + 1
            Direction.Up -> at - columns
            Direction.Down -> at + columns
        }
    return next.takeIf { it in 0 until count } ?: -1
}

/** The control a move from the focus of [now] reaches, or null at an edge. */
internal fun focusTargetAfter(
    now: SetupUiState,
    direction: Direction,
): SetupControl? {
    val controls = now.controls
    val at = controls.indexOf(now.focus)
    if (at < 0) return null
    if (now.step == SetupStep.Look) return controls.getOrNull(lookNeighbour(at, direction, controls.size))
    // A pair of buttons side by side (wrapped one under the other at large text, then Left and Right still walk it); elsewhere setup is one column.
    val isRow = now.step.hasSideBySideButtons || now.hasLoadFailed || SetupControl.ChooseFolder in controls
    val next =
        when (direction) {
            Direction.Up -> at - 1
            Direction.Down -> at + 1
            Direction.Left -> if (isRow) at - 1 else return null
            Direction.Right -> if (isRow) at + 1 else return null
        }
    return controls.getOrNull(next)
}

/**
 * Records a picked folder and asks for its scan; the message to show when it could not be done
 * (or was there already), null when it worked.
 */
internal suspend fun addPickedFolder(
    source: SetupSource,
    before: List<SetupFolder>,
    treeUri: String,
    log: (String) -> Unit,
): SetupMessage? =
    try {
        when (val added = source.addFolder(treeUri)) {
            is AddFolderResult.Added -> {
                // Counting starts at once: the card fills in while the person decides.
                source.requestScan()
                null
            }
            is AddFolderResult.AlreadyAdded -> {
                val card = before.firstOrNull { it.id == added.folderId }
                if (card != null && !card.isUnavailable) {
                    SetupMessage.FolderAlreadyAdded
                } else {
                    // Picked again as its card asked (unreadable, or its grant was revoked
                    // and is held again now): read it again instead of "already there".
                    source.requestScan()
                    null
                }
            }
            AddFolderResult.PermissionNotGranted -> SetupMessage.FolderNotKept
            AddFolderResult.NotRecorded -> SetupMessage.FolderCouldNotOpen
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Class only: provider exceptions carry the folder's URI.
        log("Picked folder could not be added (${e.javaClass.simpleName})")
        SetupMessage.FolderCouldNotOpen
    }

/** The emulator check; null when it could not be done (logged by class only, then Ready leaves the line out). */
internal suspend fun SetupSource.coverageOrNull(log: (String) -> Unit): EmulatorCoverage? =
    try {
        emulatorCoverage()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log("Emulators could not be checked (${e.javaClass.simpleName})")
        null
    }

/** Page 1 opens on "No", or on "Yes" when box art downloads (libretro or ScreenScraper is in force). */
internal fun boxArtFocus(now: SetupUiState): SetupControl =
    when (now.boxArtSource) {
        BoxArtSourceSetting.ScreenScraper, BoxArtSourceSetting.LibretroOnly -> SetupControl.BoxArtYes
        else -> SetupControl.BoxArtOwnCovers
    }

/** Page 2 opens on the source in force: "ScreenScraper account", else "Default source". */
internal fun boxArtSourceFocus(now: SetupUiState): SetupControl =
    if (now.boxArtSource == BoxArtSourceSetting.ScreenScraper) SetupControl.UseBoxArt else SetupControl.BoxArtLibretro
