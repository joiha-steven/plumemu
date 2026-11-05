package com.plumemu.game

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The answer to "Did <game> start?", for
 * [GameMenuController]: a write the person asked for, so it is not cancelled by B. Yes stores the core
 * as started and closes the question (`show(null)`). No takes back the play session the return
 * recorded, then forgets the person's choices of the failed core (so the next game asks again), then offers the next RetroArch core and the system's other emulators in the question's place. A failure says so in
 * its place (save or read message), never silently. [show] applies a panel only if the question is
 * still the open one (the controller's `showIfStill`). Logs ids and class names only.
 */
internal fun answerCore(
    actions: GameActions,
    scope: CoroutineScope,
    open: OpenPanel,
    command: PanelCommand.AnswerCore,
    log: (String) -> Unit,
    show: (OpenPanel?) -> Unit,
): Job =
    scope.launch(start = CoroutineStart.UNDISPATCHED) {
        withContext(NonCancellable) {
            val game = open.game
            val question = command.question
            try {
                if (command.started) {
                    actions.confirmCore(question.core)
                    show(null)
                    return@withContext
                }
                question.plays.forEach { if (!actions.takeBackPlay(it)) log("core check: game ${game.key}'s play was not taken back") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("core check: game ${game.key}'s answer could not be saved (${e.javaClass.simpleName})")
                show(OpenPanel(game, PanelScreen.Message(saveFailedMessage())))
                return@withContext
            }
            // After the plays, and on its own: a choice that cannot be forgotten (the game left the
            // library meanwhile) must not keep the plays of a game that never ran.
            try {
                actions.forgetFailedCore(game.key, question.emulatorId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("core check: game ${game.key}'s choice of the failed core was not forgotten (${e.javaClass.simpleName})")
            }
            val listing =
                try {
                    actions.emulators(game.key)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log("core check: game ${game.key}'s emulators could not be read (${e.javaClass.simpleName})")
                    null
                }
            val tried = question.tried + question.emulatorId
            // The core offered will ask again; its question must not offer the ones answered No.
            val offered = listing?.let { (coreFollowUp(it, question.emulatorId, question.tried) as? CoreFollowUp.Next)?.core }
            if (offered != null) {
                try {
                    actions.expectCoreTry(game.key, offered.id, tried)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log("core check: game ${game.key}'s next core could not be noted (${e.javaClass.simpleName})")
                }
            }
            show(OpenPanel(game, PanelScreen.Message(listing?.let { coreMissingMessage(it, question.emulatorId, question.emulatorName, question.tried) } ?: readFailedMessage())))
        }
    }
