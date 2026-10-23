# Changelog

What changed in each version of Plumemu, newest first. Downloads are under
[Releases](../../releases).

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
