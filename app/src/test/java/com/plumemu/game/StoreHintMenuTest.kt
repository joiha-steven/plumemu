package com.plumemu.game

import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The muted line of GameNative and DroidDeck in the Apps entry's menu: only while their games are missing, never a row. */
@OptIn(ExperimentalCoroutinesApi::class)
class StoreHintMenuTest {
    private val gameNative = "app.gamenative"
    private val droidDeck = "com.droiddeck.launcher"

    @Test
    fun gameNativeHintNeedsBothSteamAndEpicEmpty() {
        assertEquals(StoreHint.GameNative, StoreHint.of(gameNative, emptySet()))
        assertEquals(StoreHint.GameNative, StoreHint.of(gameNative, setOf("snes")))
        assertNull(StoreHint.of(gameNative, setOf("steam")))
        assertNull(StoreHint.of(gameNative, setOf("epic")))
        assertNull(StoreHint.of(gameNative, setOf("steam", "epic")))
    }

    @Test
    fun droidDeckHintNeedsSteamEmptyOnly() {
        assertEquals(StoreHint.DroidDeck, StoreHint.of(droidDeck, setOf("epic")))
        assertNull(StoreHint.of(droidDeck, setOf("steam")))
    }

    @Test
    fun noHintForGameHubLiteOrAnyOtherApp() {
        assertNull(StoreHint.of("com.gamehub.lite", emptySet()))
        assertNull(StoreHint.of("com.example.alpha", emptySet()))
    }

    @Test
    fun theLineIsTheBodyNotARow() {
        val app = GameRef("$gameNative/.Main", "GameNative")
        val withHint = appMenuContent(app, canUninstall = true, canChooseKind = true, storeHint = StoreHint.GameNative)
        val without = appMenuContent(app, canUninstall = true, canChooseKind = true)
        assertEquals(listOf<UiText>(UiText.Res(R.string.app_menu_hint_gamenative)), withHint.body)
        assertTrue(without.body.isEmpty())
        assertEquals(without.items, withHint.items)
        assertEquals(listOf(UiText.Res(R.string.app_menu_hint_droiddeck)), appMenuContent(app, canUninstall = false, storeHint = StoreHint.DroidDeck).body)
    }

    private fun run(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = block)

    @Test
    fun theOpenMenuCarriesTheLineAndFocusAndTapsIgnoreIt() =
        run {
            val app = GameRef("$droidDeck/.Main", "DroidDeck")
            val apps = FakeAppActions().apply { hints[app.key] = StoreHint.DroidDeck; choosable = setOf(app.key) }
            val menu = GameMenuController(FakeGameActions(), this, apps) {}
            menu.openAppMenu(app)
            val content = checkNotNull(menu.panel).content
            assertNotNull(content.body.singleOrNull())
            assertEquals(listOf("Info", "ShowAsGame", "ShowAsPort"), content.focusableRows.map { it.key })
            menu.onAction(Action.Move(Direction.Down))
            assertEquals("ShowAsGame", content.focusableRows[checkNotNull(menu.panel).focus].key)
            // Item 0 is App info, not the line: a tap lands on the rows only.
            menu.onAction(Action.TapItem(Column.Details, 1))
            assertEquals(listOf(app.key to com.plumemu.coredata.db.AppKind.GAME), apps.kinds)
        }

    @Test
    fun anAppWithoutAHintHasNoBody() =
        run {
            val app = GameRef("com.example.alpha/.Main", "Alpha")
            val menu = GameMenuController(FakeGameActions(), this, FakeAppActions()) {}
            menu.openAppMenu(app)
            assertTrue(checkNotNull(menu.panel).content.body.isEmpty())
        }
}
