package com.plumemu.benchmark

import android.os.SystemClock
import android.view.KeyEvent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

/** The app under test and the extra that gives it 10,000 games (honoured only in benchmark builds). */
internal const val TARGET_PACKAGE = "com.plumemu"
internal const val EXTRA_LARGE_LIBRARY = "com.plumemu.benchmark.LARGE_LIBRARY"

/** Shows the second screen's window on an Unsure screen (the emulator's overlay); benchmark builds only (`SecondScreenHost`). */
internal const val EXTRA_SECOND_SCREEN = "com.plumemu.benchmark.SECOND_SCREEN"

/** A system name on the first screen of both contents: the main screen has drawn. */
private const val FIRST_SCREEN_TEXT = "Super Nintendo"
private const val FIRST_SCREEN_TIMEOUT_MS = 10_000L

/** Super Nintendo sits under Recent, Favorites and Apps. */
private const val MOVES_TO_FIRST_SYSTEM = 3

/** Cold start to the main screen; [largeLibrary] asks for the 10,000 games, [secondScreen] for the second screen's window. */
internal fun MacrobenchmarkScope.startHome(
    largeLibrary: Boolean = false,
    secondScreen: Boolean = false,
) {
    pressHome()
    startActivityAndWait { intent ->
        intent.putExtra(EXTRA_LARGE_LIBRARY, largeLibrary)
        intent.putExtra(EXTRA_SECOND_SCREEN, secondScreen)
    }
    check(device.wait(Until.hasObject(By.text(FIRST_SCREEN_TEXT)), FIRST_SCREEN_TIMEOUT_MS)) {
        "The main screen did not show \"$FIRST_SCREEN_TEXT\" within $FIRST_SCREEN_TIMEOUT_MS ms"
    }
}

/** From the system column: focus the first system and open its games list (about 555 games). */
internal fun MacrobenchmarkScope.openFirstSystemGames() {
    repeat(MOVES_TO_FIRST_SYSTEM) { device.pressDPadDown() }
    device.pressDPadRight()
    device.waitForIdle()
}

/**
 * Holds D-pad down for [holdMs]: one key-down, the wait, one key-up. Plumemu repeats on its own
 * timer while a direction is held (core-input's HoldRepeat ignores Android's key repeats), so
 * this is what a person holding the button sends.
 */
internal fun holdDpadDown(holdMs: Long) {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    val downTime = SystemClock.uptimeMillis()
    val down = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, 0)
    check(automation.injectInputEvent(down, true)) { "D-pad down was not injected" }
    // The measured action itself: the list scrolls during this time.
    SystemClock.sleep(holdMs)
    val up = KeyEvent(downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN, 0)
    check(automation.injectInputEvent(up, true)) { "D-pad up was not injected" }
}

/** [count] quick upward swipes over the games list (it starts at x 72 dp; aim right of centre). */
internal fun MacrobenchmarkScope.flingList(count: Int) {
    val x = device.displayWidth * FLING_X
    val from = (device.displayHeight * FLING_FROM_Y).toInt()
    val to = (device.displayHeight * FLING_TO_Y).toInt()
    repeat(count) {
        device.swipe(x.toInt(), from, x.toInt(), to, FLING_STEPS)
        device.waitForIdle()
    }
}

private const val FLING_X = 0.6f
private const val FLING_FROM_Y = 0.8f
private const val FLING_TO_Y = 0.2f

/** Few steps make a fast swipe (about 5 ms each), so the list keeps flinging after it. */
private const val FLING_STEPS = 5

/** From the system column: START opens Settings; every section in turn (R1) with its rows walked; Back out. */
internal fun MacrobenchmarkScope.walkSettings() {
    device.pressKeyCode(KeyEvent.KEYCODE_BUTTON_START)
    check(device.wait(Until.hasObject(By.textStartsWith(SETTINGS_TITLE)), FIRST_SCREEN_TIMEOUT_MS)) { "Settings did not open" }
    device.pressDPadRight()
    repeat(SETTINGS_SECTIONS) {
        repeat(ROWS_PER_SECTION) { device.pressDPadDown() }
        device.pressKeyCode(KeyEvent.KEYCODE_BUTTON_R1)
        device.waitForIdle()
    }
    // Back leaves the rows, then Settings; pressed only while the main screen is not back, since
    // Back on the main screen is not part of this journey.
    repeat(MAX_BACKS) {
        if (!device.hasObject(By.text(FIRST_SCREEN_TEXT))) {
            device.pressBack()
            device.waitForIdle()
        }
    }
    check(device.wait(Until.hasObject(By.text(FIRST_SCREEN_TEXT)), FIRST_SCREEN_TIMEOUT_MS)) { "Settings did not close" }
}

/** On a game: START opens the game menu, a move in it, Back closes it. */
internal fun MacrobenchmarkScope.openAndCloseGameMenu() {
    device.pressKeyCode(KeyEvent.KEYCODE_BUTTON_START)
    device.waitForIdle()
    device.pressDPadDown()
    device.pressBack()
    device.waitForIdle()
}

/** X (BUTTON_X: the Thor sends by letter) opens search and a few letters are typed; it stays open. */
internal fun MacrobenchmarkScope.typeASearch() {
    device.pressKeyCode(KeyEvent.KEYCODE_BUTTON_X)
    device.waitForIdle()
    SEARCH_KEYS.forEach { device.pressKeyCode(it) }
    device.waitForIdle()
}

/** In the games list: R1 through the next systems and L1 back, each list fading in. */
internal fun MacrobenchmarkScope.switchSystems() {
    repeat(SYSTEM_SWITCHES) {
        device.pressKeyCode(KeyEvent.KEYCODE_BUTTON_R1)
        device.waitForIdle()
    }
    repeat(SYSTEM_SWITCHES) {
        device.pressKeyCode(KeyEvent.KEYCODE_BUTTON_L1)
        device.waitForIdle()
    }
}

/**
 * On a game: START opens the game menu, A on "Play with…" opens the emulator choice (the same
 * list as "Choose an emulator"), a move in it, then Back twice. Nothing is launched.
 */
internal fun MacrobenchmarkScope.openGameMenuAndEmulatorChoice() {
    device.pressKeyCode(KeyEvent.KEYCODE_BUTTON_START)
    device.waitForIdle()
    device.pressDPadDown()
    device.pressKeyCode(KeyEvent.KEYCODE_BUTTON_A)
    device.waitForIdle()
    device.pressDPadDown()
    device.pressBack()
    device.waitForIdle()
    device.pressBack()
    device.waitForIdle()
}

/** X opens search (it fades in), a few letters are typed, Back closes it (the strip fades back). */
internal fun MacrobenchmarkScope.openAndCloseSearch() {
    typeASearch()
    // The first Back may only hide the keyboard; Back is pressed only while the strip is not back,
    // since Back on the main screen is not part of this journey.
    repeat(SEARCH_BACKS) {
        if (!device.hasObject(By.text(FIRST_SCREEN_TEXT))) {
            device.pressBack()
            device.waitForIdle()
        }
    }
    check(device.wait(Until.hasObject(By.text(FIRST_SCREEN_TEXT)), FIRST_SCREEN_TIMEOUT_MS)) { "Search did not close" }
}

private const val SYSTEM_SWITCHES = 4
private const val SEARCH_BACKS = 3

private const val SETTINGS_TITLE = "Settings"
private const val SETTINGS_SECTIONS = 6
private const val ROWS_PER_SECTION = 4
private const val MAX_BACKS = 3
private val SEARCH_KEYS = listOf(KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_O)
