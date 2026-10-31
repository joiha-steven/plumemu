package com.plumemu.game

import com.plumemu.telemetry.LaunchFrom
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.TextEntry
import com.plumemu.coreui.components.keyboard.KeyboardEvent
import com.plumemu.keyboard.KeyboardChoice
import com.plumemu.sound.Feel
import com.plumemu.sound.Outcome
import com.plumemu.launcher.FailureReason
import com.plumemu.launcher.LaunchOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration

/**
 * Play and the game menu. Owns which panel is open, its focus,
 * and the one launch at a time: a second Play while a launch runs is ignored, and "Starting game…"
 * shows; a start that has not handed over within [startLimit] is given up with a message. While a panel is open
 * (or the menu is being read) the main screen hands it every action ([onAction]); Back steps out (to
 * the menu, then to the list, focus unchanged), START closes. Slow answers apply only to the panel
 * that asked for them: a Back meanwhile wins. Runs on the main thread; [actions] does the slow work.
 * Log lines carry the row id only, never a title or file name.
 */
@Stable
internal class GameMenuController(
    private val actions: GameActions,
    private val scope: CoroutineScope,
    /** The Apps entry's rows: opened through [openApp], which shares the one-launch-at-a-time rule and the messages. */
    private val apps: AppActions = AppActions.None,
    /** How long a start may take before the person is told it did not start and A works again. */
    private val startLimit: Duration = GameStarts.START_LIMIT,
    private val log: (String) -> Unit = { Log.w(TAG, it) },
) {
    /**
     * The open panel, null when the list has the focus. Compared by identity: slow answers check
     * that the panel is still the very one that asked ([showIfStill]).
     */
    var panel: OpenPanel? by mutableStateOf(null, referentialEqualityPolicy())
        internal set

    val isOpen: Boolean get() = panel != null

    private val appMenus = AppMenuWork(apps, scope)

    /** Reading the menu's details: actions wait for it, Back or START cancels it. */
    private var opening: Job? = null

    /** A read or write a panel row started; cancelled when the person leaves that panel. */
    private var work: Job? = null

    /** An arcade set's Other versions, read once its menu shows; cancelled when the menu closes. */
    private var versionsRead: Job? = null

    /** The versions that read found, for the menu of that game key (Back returns to a menu opened before them). */
    private var arrivedVersions: Pair<String, VersionListing>? = null

    /** The open Fix box art… panel's search, thumbnails and pick; cancelled when it is left. */
    private var boxArt: FixBoxArtWork? = null

    /** Favorite writes one after the other, so two quick presses land in the order they were made. */
    private val favoriteWrites = Mutex()

    /** Says what each action did, for the sounds (set by the screen). */
    val feel = Feel()

    /** Told when a favorite is asked for (the lists show it at once) and when its write failed. */
    var favoriteListener: FavoriteListener? = null

    /** Which keyboard the panels' fields type with (Settings › Controls › Keyboard); set by the screen, read when a field's panel opens. */
    var keyboardKind: KeyboardChoice = KeyboardChoice.Plumemu

    /** What the input router must send for the open panel: its field's typing keys, else none. */
    val textEntry: TextEntry get() = (panel?.screen as? TypingScreen)?.typing?.textEntry ?: TextEntry.None

    private val starts = GameStarts(scope, startLimit, log, panel = { panel }, show = { panel = it }, ignored = { feel.hush() })

    /** Whether a launch is running (Play is ignored until it ends, or until [startLimit] gives up on it). */
    val isLaunching: Boolean get() = starts.isLaunching

    /**
     * Starts [game] ([GameStarts.play]): a start closes the panel Play came from, a failure replaces
     * it; "Starting game…" shows when the start takes a moment or on a second Play.
     */
    fun play(
        game: GameRef,
        from: LaunchFrom = LaunchFrom.Details,
    ): Boolean = starts.play(game, { actions.launch(game.key, from) }) { readOrNull("emulators", game) { actions.emulators(game.key) } }

    /**
     * Opens [app], a row of the Apps entry ([GameRef.key] is its row id; [GameStarts.openApp]). Like
     * Play, a second press while a start runs is ignored with "Opening …" shown; one after it starts
     * the app again (Android brings its task forward).
     */
    fun openApp(app: GameRef): Boolean = starts.openApp(app) { apps.open(app.key) }

    /** START or a long press on an app: its menu (App info, and Uninstall when Android can remove it), unless a panel is open or opening. */
    fun openAppMenu(app: GameRef): Boolean = (!isOpen && opening?.isActive != true).also { if (it) opening = appMenus.read(app) { menu -> showIfStill(null, menu) } }

    /**
     * Adds [game] to Favorites or takes it out: Y on the list and the menu's Favorite share this.
     * A failed write shows the save message (over the list, or in place of [origin]).
     */
    fun setFavorite(
        game: GameRef,
        isFavorite: Boolean,
        origin: OpenPanel? = null,
    ): Job {
        favoriteListener?.requested(game, isFavorite)
        return scope.launch {
            // The list shows the new mark from now on, so leaving the menu (B, START, a tap outside
            // cancel the panel's work) must not stop the write: a lost write would leave a star the
            // library does not hold.
            withContext(NonCancellable) {
                favoriteWrites.withLock {
                    val stored = write("favorite", game, origin) { actions.setFavorite(game.key, isFavorite) }
                    if (!stored) favoriteListener?.failed(game, isFavorite)
                }
            }
        }
    }

    /** Opens the menu of [game] once its details are read. Ignored while a panel is open or opening. */
    fun openMenu(game: GameRef, fromRecent: Boolean = false): Boolean {
        if (isOpen || opening?.isActive == true) return false
        forgetVersions()
        opening =
            scope.launch {
                val read = readOrFail("menu", game, origin = null) { actions.facts(game.key) } ?: return@launch
                val facts = if (fromRecent) read.copy(canRemoveFromRecent = true) else read
                showIfStill(null, OpenPanel(game, PanelScreen.Menu(facts)))
                if (facts.versionsPending && panel != null) readVersions(game)
            }
        return true
    }

    /**
     * Other versions of an arcade set: its sets are found in the arcade pack, which the menu does
     * not wait for. The entry is added when they arrive, on the menu if the person is on it and
     * nothing is running there (a row's read compares panels by identity), else when Back returns to it.
     */
    private fun readVersions(game: GameRef) {
        forgetVersions()
        versionsRead =
            scope.launch {
                val listing = readOrNull("versions", game) { actions.versions(game.key) } ?: return@launch
                arrivedVersions = game.key to listing
                val open = panel ?: return@launch
                if (work?.isActive == true || isLaunching) return@launch
                val updated = open.withVersions(game.key, listing)
                if (updated !== open) panel = updated
            }
    }

    /** One action while a panel is open or opening; false (not consumed) when neither. */
    fun onAction(action: Action): Boolean = feel.act { handle(action) }

    private fun handle(action: Action): Boolean {
        val open = panel
        if (open == null) {
            if (opening?.isActive != true) return false
            // The menu is a moment away: nothing reaches the strip meanwhile; B or START gives up on it.
            if (action == Action.Back || action == Action.Menu || action == Action.Move(Direction.Left)) close().also { feel.report(Outcome.WentBack) }
            return true
        }
        // A panel with a text field (Fix name…, Use my own name, Fix box art…) takes the actions first.
        (open.screen as? TypingScreen)?.typing?.let { typing ->
            if (typed(open, typing) { typing.onAction(action) }) return true
        }
        when (action) {
            is Action.Move ->
                when (action.direction) {
                    // Info has one row to stand on (Back): Up and Down scroll its facts instead (InfoFacts), through the
                    // screen's own InfoScroll: the panel stays the very same one, so a read still running finds it
                    // (showIfStill), and nothing changes when nothing can scroll. Silent, as Up and Down on Back alone were.
                    Direction.Up, Direction.Down ->
                        if (open.screen is PanelScreen.Info) {
                            open.screen.scroll.press(isDown = action.direction == Direction.Down)
                        } else {
                            moveFocus(open, action.direction == Direction.Down, action.isRepeat)
                        }
                    Direction.Left -> back()
                    Direction.Right -> Unit
                }
            Action.Enter, Action.Play -> choose(open, open.focus)
            Action.Back -> back()
            Action.Menu -> close().also { feel.report(Outcome.WentBack) }
            // Panel rows are the panel column (core-input's Details, Settings' "panel").
            is Action.TapItem -> if (action.column == Column.Details) choose(open, action.index)
            is Action.LongPressItem -> if (action.column == Column.Details) choose(open, action.index)
            else -> Unit
        }
        return true
    }

    /** A tap on a key of Plumemu's keyboard over the open panel's field (the tray's own taps; buttons come through [onAction]). */
    fun onKeyboardEvent(event: KeyboardEvent) {
        feel.act<Unit> {
            val open = panel ?: return@act
            val typing = (open.screen as? TypingScreen)?.typing ?: return@act
            typed(open, typing) { typing.onKeyboardEvent(event) }
        }
    }

    /**
     * What a field made of an action or key: a command runs (once; not while a row's work runs), a
     * move of the cursor or of the keys' focus is heard. False when the field left it to the panel.
     */
    private inline fun typed(
        open: OpenPanel,
        typing: TypingState,
        outcomeOf: () -> TypingOutcome,
    ): Boolean {
        val cursor = typing.cursor
        val key = typing.fieldKeys.keys.focus
        return when (val outcome = outcomeOf()) {
            TypingOutcome.Handled -> true.also { if (typing.cursor != cursor || typing.fieldKeys.keys.focus != key) feel.report(Outcome.Moved) }
            is TypingOutcome.Run -> {
                if (work?.isActive != true) run(open, outcome.command).also { feel.report(Outcome.Activated) }
                true
            }
            TypingOutcome.NotHandled -> false
        }
    }

    /** Back: from Play with or Info to the menu (its focus kept), from the menu or a message to the list. */
    fun back() {
        work?.cancel()
        endBoxArt()
        val parent = panel?.parent
        panel = arrivedVersions?.let { (key, listing) -> parent?.withVersions(key, listing) } ?: parent
        // Back out of the menu: a late answer for this game must never reach the next game's menu.
        if (parent == null) forgetVersions()
        feel.report(Outcome.WentBack)
    }

    fun close() {
        work?.cancel()
        opening?.cancel()
        forgetVersions()
        endBoxArt()
        panel = null
    }

    private fun forgetVersions() {
        versionsRead?.cancel()
        versionsRead = null
        arrivedVersions = null
    }

    private fun endBoxArt() {
        boxArt?.cancel()
        boxArt = null
    }

    private fun moveFocus(
        open: OpenPanel,
        forward: Boolean,
        isRepeat: Boolean,
    ) {
        val size = open.content.focusableRows.size
        // While a row's read or write runs the panel stays exactly as it was, so the answer still
        // finds it ([showIfStill] compares by identity).
        if (size == 0 || work?.isActive == true) return
        val next = if (forward) open.focus + 1 else open.focus - 1
        val focus =
            when {
                next in 0 until size -> next
                isRepeat -> open.focus
                else -> Math.floorMod(next, size)
            }
        panel = open.copy(focus = focus)
        if (focus != open.focus) feel.report(Outcome.Moved)
    }

    private fun choose(
        open: OpenPanel,
        index: Int,
    ) {
        val row = open.content.focusableRows.getOrNull(index) ?: return
        if (work?.isActive == true) return
        val focused = open.copy(focus = index)
        panel = focused
        run(focused, row.command)
        feel.report(Outcome.Activated) // a row that goes back said so first (back())
    }

    private fun run(
        open: OpenPanel,
        command: PanelCommand,
    ) {
        val game = open.game
        when (command) {
            PanelCommand.Play -> play(game)
            PanelCommand.OpenPlayWith ->
                work =
                    scope.launch {
                        val listing = readOrFail("emulators", game, open) { actions.emulators(game.key) } ?: return@launch
                        showIfStill(open, OpenPanel(game, PanelScreen.PlayWith(listing), parent = open))
                    }
            PanelCommand.OpenVersions -> (open.screen as? PanelScreen.Menu)?.facts?.versions?.let { panel = versionsPanel(game, it, open) }
            is PanelCommand.PlayVersion -> playVersion(open, command.copyKey)
            is PanelCommand.SetFavorite -> work = setFavorite(game, command.isFavorite, open)
            PanelCommand.Hide ->
                work =
                    scope.launch {
                        // The message says where the game went; it replaces the menu instead of closing it.
                        if (write("hide", game, open, closeAfter = false) { actions.hide(game.key) }) {
                            favoriteListener?.hidden(game).also { showIfStill(open, OpenPanel(game, PanelScreen.Message(hiddenMessage(game.title)))) }
                        }
                    }
            // Once asked, finished even if the person leaves the menu (as Favorite); the list follows the database.
            PanelCommand.RemoveFromRecent -> work = keptWrite { write("remove from recent", game, open) { actions.removeFromRecent(game.key) } }
            PanelCommand.OpenInfo -> {
                val facts = (open.screen as? PanelScreen.Menu)?.facts
                if (facts != null) {
                    showInfo(open, facts)
                } else {
                    work = scope.launch { readOrFail("info", game, open) { actions.facts(game.key) }?.let { if (panel === open) showInfo(open, it) } }
                }
            }
            PanelCommand.Back -> back()
            is PanelCommand.PlayWith ->
                work =
                    scope.launch {
                        val stored =
                            write("emulator choice", game, open, closeAfter = false) {
                                val emulatorId = command.emulatorId
                                if (command.forSystem && emulatorId != null) {
                                    actions.chooseEmulatorForSystem(game.key, emulatorId)
                                } else {
                                    actions.chooseEmulator(game.key, emulatorId)
                                }
                            }
                        if (stored && panel === open) play(game)
                    }
            is PanelCommand.GetEmulator ->
                if (!actions.openStorePage(command.packageName)) {
                    log("store page: no store or browser for an emulator of game ${game.key}")
                    panel = OpenPanel(game, PanelScreen.Message(storeUnavailableMessage(command.name)), parent = open)
                }
            PanelCommand.RescanLibrary ->
                if (actions.rescanLibrary()) {
                    close()
                } else {
                    log("rescan: game ${game.key} asked while a scan runs")
                    panel = OpenPanel(game, PanelScreen.Message(scanBusyMessage()), parent = open)
                }
            PanelCommand.Close -> close()
            is PanelCommand.AnswerCore -> work = answerCore(actions, scope, open, command, log) { next -> showIfStill(open, next) }
            PanelCommand.AppInfo, PanelCommand.AppUninstall -> appMenus.run(open, command) { next -> if (panel === open) panel = next }
            is PanelCommand.SetAppKind, PanelCommand.AskAppNotAGame -> work = answerAppKind(open, command, actions, apps)
            PanelCommand.OpenFixBoxArt -> openFixBoxArt(open)
            is PanelCommand.SearchBoxArt -> boxArt?.search(command.query)
            is PanelCommand.PickBoxArt -> boxArt?.pick(command.hit)
            PanelCommand.StoreNoBoxArt -> boxArt?.noBoxArt()
            else -> runNaming(open, command)
        }
    }

    /**
     * Other versions: stores [copyKey] as the copy A plays on this row, then plays it, like Play
     * with. A copy gone since the list was read (a rescan removed or renamed its file) is not
     * played: the list and the menu are read again and "That version is gone" shows over the new
     * list (OK or B return to it).
     */
    private fun playVersion(
        open: OpenPanel,
        copyKey: String,
    ) {
        val game = open.game
        work =
            scope.launch {
                var isCopy = true
                val stored = write("version", game, open, closeAfter = false) { isCopy = actions.chooseVersion(game.key, copyKey) }
                if (!stored || panel !== open) return@launch
                if (isCopy) {
                    play(game)
                    return@launch
                }
                log("version: game ${game.key}: the chosen copy is no copy of it any more")
                val listing = readOrNull("versions", game) { actions.versions(game.key) }
                if (panel === open) panel = OpenPanel(game, PanelScreen.Message(versionGoneMessage()), parent = refreshedVersions(open, listing))
            }
    }

    /** The naming entries ; every write closes the panel, the list shows the new name. */
    private fun runNaming(
        open: OpenPanel,
        command: PanelCommand,
    ) {
        val game = open.game
        when (command) {
            PanelCommand.OpenNameMenu -> (open.screen as? PanelScreen.Menu)?.let { panel = OpenPanel(game, PanelScreen.NameMenu(it.facts), parent = open) }
            PanelCommand.OpenFixName -> {
                // Opens at once, "Reading the names…" until the list is built (the first time per system).
                val fixName = OpenPanel(game, PanelScreen.FixName(FixNameState(game.title, finder = null, keyboardKind)), parent = open)
                panel = fixName
                work =
                    scope.launch {
                        val list = readOrFail("titles", game, fixName) { actions.titleList(game.key) } ?: return@launch
                        if (list is TitleList.Ready) {
                            if (panel === fixName) (fixName.screen as PanelScreen.FixName).typing.listReady(list.finder)
                        } else {
                            showIfStill(fixName, OpenPanel(game, PanelScreen.Message(noTitleListContent(list)), parent = open))
                        }
                    }
            }
            PanelCommand.OpenOwnName -> panel = OpenPanel(game, PanelScreen.OwnName(OwnNameState(game.title, keyboardKind)), parent = open)
            PanelCommand.AskNotAGame -> panel = OpenPanel(game, PanelScreen.Message(notAGameConfirmContent(game)), parent = open)
            // A naming write, once asked, is finished even if the person leaves the panel (B, START,
            // the screen going away): as for Favorite, a lost write would show a name not stored.
            PanelCommand.ConfirmNotAGame ->
                work = keptWrite { if (write("not a game", game, open) { actions.markNotAGame(game.key) }) favoriteListener?.hidden(game) }
            PanelCommand.BackToAutomatic -> work = keptWrite { write("back to automatic", game, open) { actions.backToAutomatic(game.key) } }
            is PanelCommand.PickTitle -> work = keptWrite { rename("fix name", game, open) { actions.setChosenTitle(game.key, command.choice) } }
            is PanelCommand.SaveOwnName -> work = keptWrite { rename("own name", game, open) { actions.setOwnName(game.key, command.name) } }
            else -> log("panel: command ${command.javaClass.simpleName} has no handler")
        }
    }

    /**
     * Fix box art… from the menu: opens at once with the field holding the shown
     * title and nothing asked (the person presses Search). Once stored, the confirmation takes the
     * panel's place and OK or B return to the menu.
     */
    private fun openFixBoxArt(open: OpenPanel) {
        val facts = (open.screen as? PanelScreen.Menu)?.facts ?: return
        val game = open.game
        val state = FixBoxArtState(game.title, facts.systemName, keyboardKind)
        val fixBoxArt = OpenPanel(game, PanelScreen.FixBoxArt(state), parent = open)
        endBoxArt()
        panel = fixBoxArt
        boxArt =
            FixBoxArtWork(game, state, actions, scope, log) { hit ->
                if (panel === fixBoxArt) {
                    endBoxArt()
                    panel = OpenPanel(game, PanelScreen.Message(boxArtStoredContent(hit)), parent = open)
                }
            }
    }

    /**
     * Info at once; what opens a name pack (the name's source, an arcade game's set) is read only
     * now, not for the menu, and shown when it arrives.
     */
    private fun showInfo(
        open: OpenPanel,
        facts: GameFacts,
    ) {
        val screen = PanelScreen.Info(facts)
        val info = OpenPanel(open.game, screen, parent = open)
        panel = info
        val name = facts.name?.takeIf { it.sourceInPack && it.source == null }
        val wantsSet = facts.isArcade && facts.arcadeSet == null
        if (name == null && !wantsSet) return
        work =
            scope.launch {
                val source = name?.let { readOrNull("name source", open.game) { actions.nameSource(open.game.key) } }
                val set = if (wantsSet) readOrNull("arcade set", open.game) { actions.arcadeSet(open.game.key) } else null
                if (source == null && set == null) return@launch
                val read = facts.copy(name = source?.let { name?.copy(source = it) } ?: facts.name, arcadeSet = set ?: facts.arcadeSet)
                // The same InfoScroll: the facts stay where Up and Down left them.
                showIfStill(info, info.copy(screen = screen.copy(facts = read)))
            }
    }

    /** Started at once (not cancellable before it begins), then not cancellable at all. */
    internal fun keptWrite(block: suspend () -> Unit): Job = scope.launch(start = CoroutineStart.UNDISPATCHED) { withContext(NonCancellable) { block() } }

    /**
     * A new name, written like any [write]; then, when box art could not be looked up by it (no
     * Wi-Fi, sign-in, pause or limit), why, over the list if nothing opened meanwhile.
     */
    private suspend fun rename(
        what: String,
        game: GameRef,
        origin: OpenPanel?,
        block: suspend () -> RenameBoxArtNotice?,
    ) {
        var notice: RenameBoxArtNotice? = null
        if (!write(what, game, origin) { notice = block() }) return
        val told = notice ?: return
        if (panel == null) panel = OpenPanel(game, PanelScreen.Message(renameBoxArtMessage(told)))
    }

    /** A slow answer for [origin]: shown only if the person is still on it. */
    private fun showIfStill(
        origin: OpenPanel?,
        next: OpenPanel?,
    ) {
        if (panel === origin) panel = next
    }

    /**
     * Writes; then closes [origin] (the list shows the change), or shows that it failed in its
     * place. With no [origin] (Y on the list) a failure shows over the list if nothing opened meanwhile.
     */
    internal suspend fun write(
        what: String,
        game: GameRef,
        origin: OpenPanel?,
        closeAfter: Boolean = true,
        block: suspend () -> Unit,
    ): Boolean =
        try {
            block()
            if (closeAfter && origin != null) showIfStill(origin, null)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Database or listing errors: class only, their messages may hold paths.
            log("$what: game ${game.key} could not be saved (${e.javaClass.simpleName})")
            // Over the panel it came from, or over the list when the person has left the menu since
            // (a write that outlives its menu, such as Favorite, must not fail unseen).
            val failed = OpenPanel(game, PanelScreen.Message(saveFailedMessage()))
            if (panel == null) panel = failed else showIfStill(origin, failed)
            false
        }

    /** Reads; a game no longer in the library or a failed read shows its message (null returned). */
    private suspend fun <T : Any> readOrFail(
        what: String,
        game: GameRef,
        origin: OpenPanel?,
        block: suspend () -> T?,
    ): T? {
        val result = try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("$what: game ${game.key} could not be read (${e.javaClass.simpleName})")
            showIfStill(origin, OpenPanel(game, PanelScreen.Message(readFailedMessage())))
            return null
        }
        if (result == null) {
            log("$what: game ${game.key} is not in the library")
            launchMessage(LaunchOutcome.Failed(UNKNOWN_GAME_ID, FailureReason.GameNotInLibrary), null)?.let {
                showIfStill(origin, OpenPanel(game, PanelScreen.Message(it)))
            }
        }
        return result
    }

    private suspend fun <T : Any> readOrNull(
        what: String,
        game: GameRef,
        block: suspend () -> T?,
    ): T? = readLogged(log, what, game, block)

    private companion object {
        const val TAG = "PlumGame"
    }
}
