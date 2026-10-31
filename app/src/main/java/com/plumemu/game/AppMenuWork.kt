package com.plumemu.game

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The work behind the Apps entry's menu, kept out of [GameMenuController]: reading whether
 * Uninstall and Show as game / port are offered, then running App info or Uninstall. The controller owns the open panel; this class is
 * told what to show through the callbacks it is given.
 */
internal class AppMenuWork(
    private val apps: AppActions,
    private val scope: CoroutineScope,
) {
    private var tool: Job? = null

    /**
     * Reads whether [app] can be uninstalled, then hands the menu to [show]. The caller keeps the job as its
     * "opening" one, so actions wait for it and START or B cancel it, as for a game's menu.
     */
    fun read(
        app: GameRef,
        show: (OpenPanel) -> Unit,
    ): Job = scope.launch { show(OpenPanel(app, PanelScreen.AppMenu(apps.canUninstall(app.key), apps.canChooseKind(app.key)))) }

    /**
     * Runs [open]'s App info or Uninstall ([command]); [done] gets what the screen shows next: null when Android's
     * screen is coming up (the menu closes), else the message over the menu (B returns to it).
     */
    fun run(
        open: OpenPanel,
        command: PanelCommand,
        done: (OpenPanel?) -> Unit,
    ) {
        if (tool?.isActive == true) return // a quick second A must not start a second uninstall
        val app = open.game
        tool = scope.launch {
            val result = if (command == PanelCommand.AppInfo) apps.showInfo(app.key) else apps.uninstall(app.key)
            val message = appToolMessage(result, app.title)
            done(message?.let { OpenPanel(app, PanelScreen.Message(it), parent = open) })
        }
    }
}
