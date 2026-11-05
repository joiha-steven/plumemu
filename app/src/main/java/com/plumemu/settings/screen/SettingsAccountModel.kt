package com.plumemu.settings.screen

import com.plumemu.account.AccountStatus
import com.plumemu.coredata.settings.BoxArtSourceSetting
import com.plumemu.library.scrape.AskAgain
import com.plumemu.sound.Outcome

/**
 * What the panel column does on Settings › Accounts: the box art source list, the systems picker
 * and the confirms ([SettingsFolderModel] and [SettingsPanelModel] build on it).
 */
internal abstract class SettingsAccountModel(
    initialFocus: SettingsFocus,
    work: SettingsWork,
) : SettingsNavigation(initialFocus, work) {
    /** A applies the choice; ScreenScraper without a usable account opens its sign-in form first (the choice is stored once it signs in). */
    protected fun onBoxArtSourceEnter(choice: BoxArtSourceSetting?) {
        when {
            choice == null -> closePanel()
            choice == BoxArtSourceSetting.ScreenScraper && !isScraperUsable(content.account) ->
                openSignIn((content.account as? AccountStatus.SignedIn)?.username.orEmpty())
            else -> {
                work.setBoxArtSource(choice)
                cause = Cause.Enter
                closePanel()
            }
        }
    }

    /** A system's checkmark flips; the two actions work on the systems checked. */
    protected fun onSystemsEnter(index: Int) {
        val chosen = chosenSystemsIn(content, focus)
        when (systemsPickActionAt(content, index)) {
            null -> {
                val system = content.systems.getOrNull(index) ?: return
                val next = if (system.systemId in chosen) chosen - system.systemId else chosen + system.systemId
                moveTo(focus.copy(chosenSystems = next))
            }
            SystemsPickAction.DownloadMissing -> {
                if (chosen.isEmpty()) return
                work.askAgain(AskAgain.Missing, chosen)
                cause = Cause.Enter
                backToAccountPage(AccountPageItem.ChooseSystems)
            }
            SystemsPickAction.Redownload -> if (chosen.isNotEmpty()) openBoxArtConfirm(chosen)
        }
    }

    protected fun openBoxArtConfirm(systems: Set<String>?) =
        moveTo(focus.copy(page = PanelPage.ScraperConfirm, panelIndex = BoxArtConfirmItem.Cancel.ordinal, redownloadSystems = systems))

    protected fun onBoxArtConfirmEnter(item: BoxArtConfirmItem?) {
        if (item == BoxArtConfirmItem.Redownload) {
            work.askAgain(AskAgain.All, focus.redownloadSystems)
            cause = Cause.Enter
            backToAccountPage(if (focus.redownloadSystems == null) AccountPageItem.RedownloadAll else AccountPageItem.ChooseSystems)
        } else {
            backFromBoxArtConfirm()
        }
    }

    protected fun onClearConfirmEnter(item: ClearConfirmItem?) {
        if (item == ClearConfirmItem.Clear) {
            work.clearBoxArt()
            cause = Cause.Enter
        }
        backToAccountPage(AccountPageItem.ClearBoxArt)
    }

    protected fun onSignOutConfirmEnter(item: SignOutConfirmItem?) {
        if (item == SignOutConfirmItem.SignOut) {
            work.signOut()
            closePanel()
        } else {
            backToAccountPage(AccountPageItem.SignOut)
        }
    }

    /** RetroAchievements › Sign out opens its confirm, Cancel focused first: the key and the progress go at once. */
    protected fun askRaSignOut() {
        moveTo(focus.copy(page = PanelPage.RaSignOutConfirm, panelIndex = SignOutConfirmItem.Cancel.ordinal))
    }

    protected fun onRaSignOutConfirmEnter(item: SignOutConfirmItem?) {
        if (item == SignOutConfirmItem.SignOut) {
            work.signOutRa {}
            closePanel()
        } else {
            backToRaPage()
        }
    }

    /** Back or Cancel from the confirm: to the Sign out button. */
    protected fun backToRaPage() {
        val index = raPageItems(content).indexOf(RaPageItem.SignOut).coerceAtLeast(0)
        moveTo(focus.copy(page = PanelPage.RaAccount, panelIndex = index))
    }

    /** Back or Cancel: to the button that opened it (Re-download all, or the picker's Re-download). */
    protected fun backFromBoxArtConfirm() {
        val systems = focus.redownloadSystems ?: return backToAccountPage(AccountPageItem.RedownloadAll)
        val at = content.systems.size + SystemsPickAction.Redownload.ordinal
        moveTo(focus.copy(page = PanelPage.ScraperSystems, panelIndex = at, chosenSystems = systems, redownloadSystems = null))
    }

    protected fun backToAccountPage(on: AccountPageItem) {
        val index = accountPageItems(content).indexOf(on).coerceAtLeast(0)
        moveTo(focus.copy(page = PanelPage.ScraperAccount, panelIndex = index, chosenSystems = emptySet(), redownloadSystems = null))
    }
}
