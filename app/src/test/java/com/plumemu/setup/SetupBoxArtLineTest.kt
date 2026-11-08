package com.plumemu.setup

import com.plumemu.account.AccountStatus
import com.plumemu.account.FakeScraperAccounts
import com.plumemu.coredata.settings.BoxArtSourceSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Ready's box art line says only what is true: own covers, no games, no network, a refused account. */
class SetupBoxArtLineTest {
    private val source = FakeSetupSource()

    /** Folder, Continue, look, Yes, Default source; then [chosen] and the account are set as the store would hold them. */
    private fun TestScope.readyWith(
        chosen: BoxArtSourceSetting,
        status: AccountStatus = AccountStatus.SignedOut,
        games: Int = 12,
    ): SetupModel {
        val accounts = FakeScraperAccounts()
        val model = SetupModel(backgroundScope, source, accounts) { }
        backgroundScope.launch { model.state.collect { } }
        runCurrent()
        model.onPicked(PickResult.Picked("content://x/tree/primary%3AROMs"))
        runCurrent()
        for (action in listOf(Action.Enter, Action.Enter, Action.Move(Direction.Down), Action.Enter, Action.Enter)) {
            model.onAction(action)
            runCurrent()
        }
        source.systems.value = if (games == 0) emptyList() else listOf(SystemCount("NES", games))
        source.endScan()
        runCurrent()
        assertEquals(SetupStep.Ready, model.state.value.step)
        source.boxArtSource.value = chosen
        accounts.status.value = status
        runCurrent()
        return model
    }

    @Test
    fun onlineLibretroOnlyIsComing() = runTest {
        assertEquals(BoxArtLine.Coming, readyWith(BoxArtSourceSetting.LibretroOnly).state.value.boxArtLine)
    }

    @Test
    fun ownCoversHasNoLine() = runTest {
        assertEquals(BoxArtLine.None, readyWith(BoxArtSourceSetting.OwnCovers).state.value.boxArtLine)
    }

    @Test
    fun noGamesHasNoLine() = runTest {
        assertEquals(BoxArtLine.None, readyWith(BoxArtSourceSetting.LibretroOnly, games = 0).state.value.boxArtLine)
    }

    @Test
    fun noNetworkWaits() = runTest {
        source.online = false
        assertEquals(BoxArtLine.WaitsForNetwork, readyWith(BoxArtSourceSetting.LibretroOnly).state.value.boxArtLine)
    }

    @Test
    fun aRefusedAccountSaysSignInAgain() = runTest {
        val refused = AccountStatus.SignedIn("name", isRefused = true)
        assertEquals(BoxArtLine.SignInAgain, readyWith(BoxArtSourceSetting.ScreenScraper, refused).state.value.boxArtLine)
    }

    @Test
    fun aRefusedAccountSaysSignInAgainOffline() = runTest {
        source.online = false
        val refused = AccountStatus.SignedIn("name", isRefused = true)
        assertEquals(BoxArtLine.SignInAgain, readyWith(BoxArtSourceSetting.ScreenScraper, refused).state.value.boxArtLine)
    }

    @Test
    fun aUsableAccountOnlineIsComing() = runTest {
        val ok = AccountStatus.SignedIn("name")
        assertEquals(BoxArtLine.Coming, readyWith(BoxArtSourceSetting.ScreenScraper, ok).state.value.boxArtLine)
    }
}
