# core-input

Gamepad keys, sticks and touch turned into one set of actions, and the main screen's focus and
strip position. Screens listen to actions, never to raw keys or pointers, and read the focus
state; they never know the button layout.

Namespace: `com.plumemu.coreinput`. Depends on Compose ui and foundation only (not on `core-ui`:
sizes and motion come in as parameters).

## How a screen uses it

```kotlin
val state = rememberStripState(content, geometry, PlumTheme.motion.isOn, PlumTheme.motion.slideSpec())
LaunchedEffect(router, state) {            // collected only while the screen is composed
    router.actions.collect { action ->
        state.dispatch(action).forEach { intent ->
            when (intent) {
                is StripIntent.Play -> launch(intent.systemId, intent.index)
                is StripIntent.JumpLetter -> state.focusRow(nextLetterRow(intent))
                else -> handle(intent)
            }
        }
    }
}
Box(Modifier.stripDrag(state)) {
    Row(Modifier.graphicsLayer { translationX = state.translationX }) { /* the three columns */ }
}
// A row: Modifier.tapItem(Column.Games, index, router::send); a hint: Modifier.hint(Action.Play, router::send)
// Focus: state.focus.column, state.focus.systemIndex, state.focus.row, state.focus.rowOf(systemId)
```

## Actions

"A" and "B" are the letters on the
buttons after the confirm setting (below).

| Action | Gamepad | Touch | Main screen | Settings |
|---|---|---|---|---|
| `Move(direction)` | D-pad, left stick, hat; held: repeats, faster with "Hold to scroll faster" | Tap an item (`TapItem`) moves focus there; swipes and flings are the list's own scrolling | Move in a column; Left/Right change column | Move in a column |
| `Enter` | A, D-pad centre, Enter | Tap the focused system again; a hint | Open the games; on a game: play | Open the option |
| `Back` | B, Back, Escape | System back gesture; "B Back" hint | One column left | One column left |
| `NextSystem` / `PreviousSystem` | R1 / L1 | Tap another system in the system column's visible edge | Next / previous system (wraps) | Next / previous section |
| `JumpLetterNext` / `JumpLetterPrevious` | R2 / L2 (key or trigger axis) | A hint; the letter rail may follow after v0.1 | Asks the screen for the next / previous first letter | — |
| `Play` | A on a game (arrives as `Enter`) | Tap the focused game again; "A Play" hint | Play | — |
| `Menu` | START | Long press a game (`LongPressItem`) | Game menu; on the system column: Settings | — |
| `ToggleView` | SELECT | "SELECT" hint | List or grid for the system, Recent or Favorites (only with box art), or for Apps (always: its icons need no box art) | — |
| `OpenSettings` | START on the system column (arrives as `Menu`) | "START Settings" hint, or a long press on the system column | Settings | — |
| `ToggleFavorite` | Y (the button printed Y, whichever confirms) | "Y Favorite" / "Y Unfavorite" hint | On a game (games column or details stop): add it to Favorites or take it out; nothing on the system column or on Apps (no hint there) | — |
| `Search` | X (the button printed X, whichever confirms) | "X Search" hint | Opens search; the strip's focus waits for it to close | — |
| `TapItem(column, index)` | — | Tap a row, cover or column item | Focus it; tapped again: act (open, play) | Same |
| `LongPressItem(column, index)` | — | Long press | On a game: focus it and open its menu; on the system column: Settings (works with the hint bar hidden) | — |
| `Erase(isRepeat)` | B (after the confirm setting) while a screen's own keyboard is up (`TextEntry.Keyboard`); held, it repeats like a move | "B Delete" hint | Search: delete before the caret; a fresh one on an empty query closes Search | — |
| `Typed(key)` | A hardware keyboard while a screen takes text (`TextEntry.Field` or `Keyboard`): `TypedKey.Text`, `Backspace`, `ForwardDelete`, `CaretLeft` / `CaretRight` (arrows, alphabetic keyboards only), `Enter` | — | Search: edit the query; Enter goes to the results | — |

X and Y never swap: only A and B follow the confirm setting. Volume, Home, Power and
keyboard letters are left to Android (letters reach a focused text field, such as the sign-in form's),
except while a screen asks for text with `InputRouter.textEntry` (Search with Plumemu's keyboard): then a
hardware keyboard's letters, digits, symbols, space, Backspace, Delete, Enter and (from an alphabetic
keyboard) the left and right arrows arrive as `Action.Typed` (`TypedKeys.kt`; a gamepad's keys never do), and
with `TextEntry.Keyboard` the B button sends `Erase` instead of `Back`. Back, Escape and the back gesture are
always `Back`.

## Which button confirms

Android names gamepad keys by **position** on its standard gamepad: `BUTTON_A` is the bottom
button (south), `BUTTON_B` right (east), `BUTTON_X` left (west), `BUTTON_Y` top (north), and its
default key maps fall back from `BUTTON_A` to D-pad centre and from `BUTTON_B` to Back, so the
system's own menus confirm with the south button. This module resolves a layout and an A/B swap:

| Button layout | Swap A and B | South (`BUTTON_A`) | East (`BUTTON_B`) | West (`BUTTON_X`) | North (`BUTTON_Y`) | Confirms |
|---|---|---|---|---|---|---|
| Xbox (app default: keys read as the printed letters) | off | A, Enter | B, Back | X | Y | south |
| Xbox | on | B, Back | A, Enter | X | Y | east |
| Nintendo (not used by the app) | off | B, Back | A, Enter | Y | X | east |
| Nintendo | on | A, Enter | B, Back | Y | X | south |

Back/Escape and D-pad centre/Enter ignore both settings.

The person sees one option, not two: the button printed "A" confirms, and
Settings › Advanced › "Confirm with B" swaps confirm and back. Measured on the
AYN Thor: it sends keys by letter (the button printed A sends `BUTTON_A`), so the app always uses
the Xbox layout (Android's key names read as the printed letters): off (default) → Xbox, swap off
(`BUTTON_A` confirms, `BUTTON_B` goes back, `BUTTON_X` searches, `BUTTON_Y` toggles favorite); on →
Xbox, swap on (`BUTTON_B` confirms), for a person who prefers B. X and Y never move. The Nintendo layout (by position) is not used: it put
confirm on the button printed B on the Thor. The Nintendo rows stay for tests and are not reachable
from Settings. Hints say "A" for confirm and "B" for back by default and swap their letters when
the option is on. ES-DE has one similar setting, "Swap the A/B and X/Y buttons".

Sources: Android developer guide "Handle controller actions" (standard gamepad diagram and
key codes); AOSP `frameworks/base/data/keyboards/Generic.kcm` (fallbacks); ES-DE `USERGUIDE.md`
(gitlab.com/es-de/emulationstation-de), section "Swap the A/B and X/Y buttons".

## Files

| File | What it holds |
|---|---|
| `build.gradle.kts` | Android library, Compose on; tests use `core-ui` for the real layout tokens |
| `src/main/java/com/plumemu/coreinput/Action.kt` | `Action` (every action), `Direction`, `Column` |
| `src/main/java/com/plumemu/coreinput/GamepadMapping.kt` | `ButtonLayout`, `InputSettings`; key code to control, layout and swap to action |
| `src/main/java/com/plumemu/coreinput/AxisEdges.kt` | Stick, hat and trigger axes to held controls (deadzone, hysteresis) |
| `src/main/java/com/plumemu/coreinput/HoldRepeat.kt` | Hold-to-repeat timing constants and intervals; `RepeatPacer` puts repeats on whole display frames |
| `src/main/java/com/plumemu/coreinput/TimerFrameClock.kt` | Frames from a timer, for a router without a display frame clock (unit tests) |
| `src/main/java/com/plumemu/coreinput/InputRouter.kt` | The door: Activity events and touch in, `actions` flow out; hold-to-repeat (moves, and B's erase while typing); `textEntry`; `held` (the key caps whose button is down, named by the letter printed on the button, for the hints' pressed look; test `HeldKeysTest`) |
| `src/main/java/com/plumemu/coreinput/TypedKeys.kt` | A hardware keyboard's keys while a screen takes text |
| `src/main/java/com/plumemu/coreinput/TouchActions.kt` | `Modifier.tapItem`, `Modifier.hint`, `Modifier.stripDrag` |
| `src/main/java/com/plumemu/coreinput/strip/StripFocus.kt` | `SystemEntry` (with the items' ids), `StripContent`, `StripFocus` (column, system, rows per system kept by game id, index as fallback) |
| `src/main/java/com/plumemu/coreinput/strip/StripReducer.kt` | The focus rules (pure Kotlin) and `StripIntent`; `landOn` (the Home button's landing) |
| `src/main/java/com/plumemu/coreinput/strip/StripGeometry.kt` | Stop offsets from the layout sizes; where a released drag settles |
| `src/main/java/com/plumemu/coreinput/strip/StripState.kt` | `rememberStripState`: focus + animated strip offset, saved in a Bundle |
| `src/test/java/com/plumemu/coreinput/GamepadMappingTest.kt` | Every key under both layouts and swap |
| `src/test/java/com/plumemu/coreinput/GamepadPathTest.kt` | Every action's gamepad path into the focus rules |
| `src/test/java/com/plumemu/coreinput/InputRouterTest.kt` | Hold-to-repeat on virtual time, axes, buffering, nothing running while idle |
| `src/test/java/com/plumemu/coreinput/InputRouterAndroidEventsTest.kt` | Real `KeyEvent`s and `MotionEvent`s: what is taken, what is left |
| `src/test/java/com/plumemu/coreinput/TextEntryRouterTest.kt` | B per text-entry mode and confirm setting; a held B's repeats; leaving the mode stops them |
| `src/test/java/com/plumemu/coreinput/TypedKeysTest.kt` | Real keyboard and gamepad KeyEvents while a screen takes text |
| `src/test/java/com/plumemu/coreinput/TouchActionsTest.kt` | Every action's touch path (Compose, Robolectric) |
| `src/test/java/com/plumemu/coreinput/strip/StripReducerTest.kt` | Wrap, edges, empty and single systems, restore per system, grid, taps, `landOn` |
| `src/test/java/com/plumemu/coreinput/strip/StripGeometryTest.kt` | Stops at 768×432 and 576×432 against design/rules.md; settle rules |
| `src/test/java/com/plumemu/coreinput/strip/StripStateTest.kt` | Saved state, motion on/off, drag and settle (Compose, Robolectric) |
