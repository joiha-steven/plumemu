# Input

Buttons and touch are equals. Every handheld has both.

- Screens react to **actions** from `core-input` (Move, Enter, Back, NextSystem, PreviousSystem,
  JumpLetter, Play, Menu, ToggleView, OpenSettings), never to raw `KeyEvent`s or pointers.
- core-input's bindings stay generic (L1 / R1 are `PreviousSystem` / `NextSystem`); where a screen
  gives them another meaning it decides from its own state before the strip sees them: a grid's
  games focus reads them as grid size (`home/GridSizeKeys.kt`, hint "L1 R1 Size"). The main screen
  swaps the shoulder keys: L2 / R2 (`JumpLetter*`) switch the system and in a list
  L1 / R1 jump a letter (`homeAction` in `home/HomeActions.kt`); Settings, Search and the keyboard
  read the generic names as before. Settings › Controls › Switch system with
  can trade the two pairs back: every shoulder key on Home goes through `ShoulderKeys.read`
  (`settings/ShoulderKeys.kt`) before `homeAction` or the grid size keys see it, and the hints come
  from `hintsFor(shoulders)`. A new reading of a shoulder key on Home goes through `read` too.
- Each action has a gamepad binding and a touch gesture; the table is in
  `core-input/README.md`. Adding an action means adding both, and a test for both.
- The app owns focus: current column and row are state. A tap moves focus to the tapped item;
  a second tap on the focused game plays it. A long press opens the game menu.
- Touch targets: a list row's touch area at least 40 dp (game rows draw at least 38 dp and their 2 dp gap is part of the tap area), standalone buttons at least 44 dp. Every hint in
  the bottom bar is tappable and does what its button does.
- Scrolling: holding a direction repeats with acceleration ("Hold to scroll faster"); a fling
  scrolls with momentum. Neither may drop frames.
- Nintendo or Xbox layout and the A/B swap are resolved in `core-input` only.
- **Search types with Plumemu's own keyboard** (see
  `core-ui/README.md`): no text field, no input connection, so Android's
  keyboard never shows there. While its tray is up Search asks the router for `TextEntry.Keyboard`
  (B erases and repeats while held, a hardware keyboard types into the query), with the tray down on the
  query `TextEntry.Field` (only the hardware keyboard), and `TextEntry.None` everywhere else; every other
  screen gets exactly the actions it always had. The system's Back and Escape stay `Action.Back`
  (Search reads it as "put the keyboard away"). With Settings › Controls › Keyboard on Android
  (`KeyboardChoice.Android`) Search takes the old path below.
- **The game panel's fields type with Plumemu's keyboard too**: Fix name…, Use my own name,
  Fix box art…. Rules 1, 3 and 4 below hold for them as for Android's keyboard: the field never opens the
  keyboard by itself (A or a tap does), it keeps the hint naming it (as the row's accessible name), and the
  action key (START, the tray's key, Enter) does the field's action exactly once (in Fix name… with no
  results START does nothing and the tray stays). Rule 2 becomes: the D-pad
  stays on the keys; the hide key, a fresh B on an empty field, the system's Back or Escape put the keyboard
  away with the cursor on the field (A brings it back). The router hears the open panel's `textEntry` first
  (`HomeScreen.textEntryFor`). With the setting on Android they keep the old path below.
- The sign-in form, the hex colour field and Settings' Unidentified games › Fix name… still open the Android
  keyboard until they move to Plumemu's keyboard; the screen must stay usable with it open.
  Keyboard rules for **every** field that uses Android's keyboard:
  1. No field opens the keyboard by itself. The cursor can rest on a field with the keyboard
     down; A or a tap on it opens the keyboard. Only entering Search opens it (that is the "I want to type");
     coming back to Search's field from its results opens nothing.
  2. Moving the cursor off the field puts the keyboard away. The system's Back also closes it (the
     app hears nothing) and the cursor stays on the field's row; A there brings it back (a counter
     of asks, not a flag, drives the show: `KeyboardAsk.asks`, `SignInForm.keyboardAsks`).
  3. Every field uses `FieldRow(keyboardHint = …)` / `InlineKeyboard(hint)`: no full-screen editor
     (`IME_FLAG_NO_EXTRACT_UI`) and a hint naming the field ("Game name", "Search games", "Colour (hex)").
  4. The IME action key does the screen's natural action exactly once, and each field does it its
     own way: sign-in and Use my own name move the cursor off the field and route A through the
     router (Next goes to the password; Done signs in / saves); Fix name's search key moves the
     cursor to the results and picks nothing (`onKeyboardAction` returns null); Search's search key
     calls `toResults` directly; the hex field's Done calls `onHexEntered` directly, and its "Use this colour" row (Down from the field, or a tap) applies the code for a gamepad or hardware keyboard. A on a text
     field never does the action itself: it opens the keyboard, and the hint bar says "A Type"
     there. Test with `ImeProbe` (Robolectric sdk 28).

## Which button confirms

The confirm button is the one printed "A", on every device. Handhelds send keys by the letter
printed on the button, so the button printed A sends Android's `KEYCODE_BUTTON_A`, printed B
`BUTTON_B`, X `BUTTON_X`, Y `BUTTON_Y` (a pad's key layout maps its Linux `BTN_SOUTH`, `EAST`,
`NORTH`, `WEST` to them). Plumemu reads the key codes as sent and never assumes the by-position
mode, which would put confirm on the button printed B.

- A confirms, B goes back, X searches, Y toggles favorite. D-pad centre and Enter always confirm;
  Back and Escape always go back.
- On a PlayStation-style pad Android sends the cross as `BUTTON_A` and the circle as `BUTTON_B`,
  so the cross confirms by default.
- Settings › Advanced › "Confirm with B" (circle on PlayStation-style pads) is for people who
  prefer it. When on, B confirms and A goes back, and the hints follow. It is stored as
  `controls.confirm_button` (`Right` default, `Bottom` when B confirms); X and Y never swap.
- Hints name buttons by their printed letters: "A" for confirm and "B" for back by default,
  swapped when the option is on.
