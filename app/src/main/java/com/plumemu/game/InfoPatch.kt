package com.plumemu.game

/**
 * Changes the facts of the Info panel [info] stands for, if it is still on screen: several reads (the name's
 * source, the arcade set, the achievements) land on the same panel, each on what the others left. The same
 * InfoScroll stays, so the facts stay where Up and Down left them.
 */
internal fun GameMenuController.patchInfo(
    info: OpenPanel,
    change: (GameFacts) -> GameFacts,
) {
    val shown = panel ?: return
    val screen = shown.screen as? PanelScreen.Info ?: return
    if (shown.parent !== info.parent || shown.game != info.game) return
    panel = shown.copy(screen = screen.copy(facts = change(screen.facts)))
}
