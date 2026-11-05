package com.plumemu.game

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Reads a system's emulators again for the open panel when packages may have changed (back from the store).
 * Only a Play with list or a message about emulators to get is rebuilt ([refreshedEmulators]); a panel with a
 * text field, the menu and every other message are left alone. One read at a time; the newest wins.
 * The menu behind the panel is brought along: its "Play with" value names the emulator the game starts now ([withMenuEmulator]).
 */
internal class EmulatorRefresh(
    private val scope: CoroutineScope,
    private val actions: GameActions,
    private val log: (String) -> Unit,
) {
    private var job: Job? = null

    fun cancel() {
        job?.cancel()
    }

    /**
     * [current] is the panel open when the read ends (null: leave everything as it is, e.g. a row's work is
     * running); the person may have moved the cursor meanwhile, so the answer is applied to it, not to [open],
     * when it is still the same game and screen. [show] gets the panel to show instead of it; null means nothing
     * is left to say. Never called for a panel that is unchanged, menu behind it included.
     */
    fun run(
        open: OpenPanel,
        current: () -> OpenPanel?,
        show: (OpenPanel?) -> Unit,
    ) {
        val screen = open.screen
        if (screen !is PanelScreen.PlayWith && (screen as? PanelScreen.Message)?.about == null) return
        job?.cancel()
        job =
            scope.launch {
                val listing = readLogged(log, "emulators", open.game) { actions.emulators(open.game.key) } ?: return@launch
                // Failing to read the facts leaves the menu as it was; the panel's own refresh does not wait on it.
                // A read that found no emulator (null) is an answer: the menu then names none.
                val facts = readLogged(log, "menu emulator", open.game) { actions.facts(open.game.key) }
                val now = current()?.takeIf { it.game == open.game && it.screen.sameKindAs(screen) } ?: return@launch
                val next = refreshedEmulators(now, listing)?.let { if (facts == null) it else it.withMenuEmulator(facts.emulator) }
                if (next !== now) show(next)
            }
    }
}

private fun PanelScreen.sameKindAs(other: PanelScreen): Boolean =
    when (this) {
        is PanelScreen.Message -> other is PanelScreen.Message && about == other.about
        else -> this::class == other::class
    }

/** [this] with the menu in its chain showing [emulator] (null: none) as what Play starts; the same panel when it already does. */
internal fun OpenPanel.withMenuEmulator(emulator: EmulatorToStart?): OpenPanel {
    val menu = screen as? PanelScreen.Menu
    if (menu != null) return if (menu.facts.emulator == emulator) this else copy(screen = menu.copy(facts = menu.facts.copy(emulator = emulator)))
    val up = parent ?: return this
    val newUp = up.withMenuEmulator(emulator)
    return if (newUp === up) this else copy(parent = newUp)
}
