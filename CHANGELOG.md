# Changelog

What changed in each version of Plumemu, newest first. Downloads are under
[Releases](../../releases).

## 0.0.3-beta (24 October 2026)

The first beta.

### New

- **Colors.** Ten ready-made color sets: Plumemu, Paper, Pocket, Dot matrix, Super, Indigo,
  Charcoal, Ivory, Banana and Cream, each shown as a small preview in Settings › Appearance › Colors.
  Super, Charcoal and Ivory color the A, B, X and Y keys. Your own colors stay under Custom.
- **Battery use on the second screen.** While you play: the time left ("About 4 h 15 min left",
  "empty at 22:32"), this play on a three-hour chart, and its pace against the game's usual one.
  While picking, a game measured before says "2 h on this charge · 30% an hour". With Show battery on.
- **Updates from inside Plumemu.** Once a day on Wi-Fi it checks for a new version; START Settings
  shows a dot, and Settings › About › Update installs it after checking it really is Plumemu.
- **Plumemu's own keyboard everywhere you type:** Search, game names, Fix box art, color codes and
  the ScreenScraper sign-in. Made for the D-pad; results stay in view. Settings › Controls ›
  Keyboard switches back to Android's.
- **Arcade games by their clean name**, the region in its own column, and the sets of one game as
  one row. **Other versions** in the game menu picks which copy plays (regions, revisions, arcade
  sets), and Plumemu remembers it.
- **Safer backups.** A backup is read back and checked before it says Saved; a damaged file is
  deleted and you are told. A restore skips a damaged picture, says how many it left out, and goes on.
- **Cleaner text:** one set of text sizes on every screen, focused rows in a real medium weight, and
  Info scrolls with Up and Down, or a finger, instead of leaving rows out.
- **New games show up by themselves.** Copy games into a ROM folder or put in an SD card, and they
  appear when you come back to Plumemu. Only folders that changed are read.
- **Push right twice to play** (off by default; setup asks): play with one hand. The first push marks
  the game, the second starts it.
- **Rename a game, get its box art.** After Fix name, Plumemu looks the new name up on ScreenScraper
  and takes the cover only when the match is exact.
- **Grid:** smaller covers by default, a thin edge so dark covers no longer melt into the
  background, covers no longer vanish under the cursor, and each system stays a list or a grid
  after a restart or an update.
- **Grid view for Recent, Favorites and Apps.** SELECT switches, and each remembers it. Recent and
  Favorites keep every cover's own shape and name its system; Apps shows app icons at one size,
  with or without box art.
- **Details** show real play facts; on wide screens games without box art get a text column. Game
  rows show year and region where the title still fits.
- **Settings reordered and reworded.** Statistics comes right after Home screen; labels say what
  they do (Find new games automatically, Show clock, Send usage statistics); rows that open a page
  say Open. Privacy says exactly what goes to ScreenScraper.
- **The buttons stay with Plumemu.** After the AYN button or a touch on the lower screen, the
  controller comes back to the upper screen by itself.
- Anything you ask for yourself says why it cannot run (no Wi-Fi, sign-in needed, daily limit), and a
  low battery no longer stops it. Apps open with A or a second tap, or say why they cannot.

### Not in this version

- Shortcuts added from other apps are listed but cannot be started yet. No screenshots or videos of
  games yet. English only.

### Known issues

- Updating from 0.0.2-alpha is a manual install; updates from inside Plumemu start with this version.
- Some covers belong to another game (seen on Game Boy Advance). Game menu › Fix box art… picks
  the right one.
- The second screen is tested on the AYN Thor only. A game never measured shows "Measuring battery
  use…" for its first minute. In a game, after the AYN button's panel, tap the upper screen once if
  the game misses the buttons.

## 0.0.2-alpha (23 October 2026)

### New

- **Box art and game details** from ScreenScraper, with your own account: setup asks, or
  Settings › Library › ScreenScraper. Box fronts come first; the panel shows progress, time left
  and the account's daily limit.
- **Cover grid.** SELECT switches a system between list and grid; L1 / R1 change the cover size
  (Small, Medium, Large). Each system's covers keep their real shape.
- **Details** of the chosen game: cover, year, maker, genre, rating and description.
- **Fix box art…** in the game menu: search ScreenScraper and pick the right cover, or none.
- **Second screen** (AYN Thor): the lower screen shows the chosen game's details, your statistics
  while Settings is open, and a clock with the battery, or black, while you play.
- **Settings › Statistics:** play time in total and this week, most-played games and system,
  games with box art.
- **Settings › Advanced › Back up… and Restore…:** your settings, or everything (library, box art,
  play time), in one file. Restore keeps a copy of the device first, to undo it.
- **Home button** brings you back to the game you played last.
- **Better names:** more consoles named from No-Intro, Redump and GameTDB lists; Switch and 3DS
  games by their title id; a game split over several files shown once; Switch updates and DLC
  grouped with their game.
- **System order:** A–Z, maker and year, or your own; hide systems you do not want.
- Recent, Favorites and Search show each game's system (PS1, GBA…).
- Six text sizes, soft click sounds (Settings › Advanced › Sounds), a Wi-Fi icon next to the
  battery, a denser game list.
- PS Vita games through Vita3K. Steam games from `.steam` files through GameNative, GameHub Lite
  or DroidDeck (not yet tried on a device).

### Not in this version

- Shortcuts added from other apps (for example a Steam game from GameNative) are listed but
  cannot be started yet.
- Screenshots and videos of games. English only.

### Known issues

- Some covers belong to another game (seen on Game Boy Advance and Super Nintendo). Game menu ›
  Fix box art… picks the right one.
- The second screen is tested on the AYN Thor only.

## 0.0.1-alpha (22 October 2026)

First alpha.

### New

- **Setup:** pick your ROMs folder in Android's own folder picker. Plumemu scans it, finds the
  emulators you have installed and picks one per system.
- **Your library** in a text list: systems on the left, games beside them. Games are named by
  Plumemu itself and offline, from the file's own code or checksum, arcade short names and the
  file name.
- **Play** with the chosen emulator. **Play with…** picks another emulator for one game or a
  whole system.
- **Favorites (Y)**, **Recent** with play time, **search (X)**, sorting, and hiding games.
- **Fixing a name:** game menu (START) › Name…: pick the right title from the list, type your
  own, mark a file as not a game, or go back to automatic.
- **Settings:** library folders and rescans, emulators per system, controls (including confirm
  with the bottom button), colours and text size, home screen.
- **Home screen:** Plumemu can be the device's home app, with an Apps entry for everything else.
- **Gamepad and touch** both work everywhere.

### Known issues

- A long system name can be cut short when the system has 1,000 or more games ("Game Boy
  Adva…").
