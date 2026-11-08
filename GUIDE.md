# Using Plumemu

How to lay out your games, what Plumemu does with them, and where to change things. Buttons are
in the [README](README.md#controls).

## Your games folder

Plumemu needs one folder that holds your games, with **one subfolder per system**. You pick it
in setup with Android's folder picker; Plumemu reads only what you pick and asks for no storage
permission. More folders (an SD card, a second drive) can be added in setup with "+ Add another
folder", or later in Settings › Library › Game folders.

```
ROMs/
├── snes/
│   ├── Super Metroid (USA).sfc
│   └── Chrono Trigger (USA).zip
├── gba/
├── psx/
│   ├── Final Fantasy VII (USA).m3u
│   ├── Final Fantasy VII (USA) (Disc 1).chd
│   ├── Final Fantasy VII (USA) (Disc 2).chd
│   └── Final Fantasy VII (USA) (Disc 3).chd
├── ps2/
├── megadrive/
├── arcade/
│   ├── sf2.zip
│   └── dino.zip
└── bios/            ← skipped: never shown as games
```

### Folder names

Plumemu knows 161 systems. A folder is matched by its name; upper or lower case, spaces, dashes
and other punctuation do not matter, and a number in front (`01 - SNES`) is ignored. Any of
these work:

- a short name, as used by most frontends: `snes`, `gba`, `psx`, `ps2`, `megadrive`;
- the system's name: `Super Nintendo`, `Game Boy Advance`, `PlayStation 2`;
- the name used by No-Intro, Redump and RetroArch: `Nintendo - Super Nintendo Entertainment
  System`, `Sony - PlayStation 2`, `Sega - Mega Drive - Genesis`;
- a maker and a system: `Sony Playstation 2`, `Nintendo Switch`.

Common short names:

| System | Folder names |
|---|---|
| NES / Famicom | `nes`, `famicom`, `fc` |
| SNES / Super Famicom | `snes`, `sfc`, `superfamicom` |
| Game Boy, Color, Advance | `gb`, `gbc`, `gba` |
| Nintendo 64 | `n64` |
| Nintendo DS, 3DS | `nds`, `3ds` |
| GameCube, Wii | `gc`, `gamecube`, `wii` |
| Switch | `switch` |
| PlayStation | `psx`, `ps1`, `playstation` |
| PlayStation 2 | `ps2` |
| PSP | `psp` |
| Master System, Game Gear | `mastersystem`, `sms`, `gamegear`, `gg` |
| Mega Drive / Genesis | `megadrive`, `genesis`, `md` |
| Saturn | `saturn` |
| Dreamcast | `dreamcast` |
| PC Engine / TurboGrafx-16 | `pcengine`, `pce`, `tg16` |
| Arcade | `arcade` (and `mame`, `fbneo` for those emulators' own sets) |

Folders inside a system folder are fine: `snes/RPG/Chrono Trigger.sfc` is a SNES game. A file
whose type only one system uses goes to that system wherever it sits: a `.gba` in the `gb` folder
is a Game Boy Advance game. Types many systems share (`.iso`, `.bin`, `.zip`) need the right
folder.

### Leaving out a sub-folder

To keep a sub-folder out of your library (demos, backups, a half-sorted pile), open Settings ›
Library › Game folders, choose the folder, then "Skip a sub-folder…". Its games leave the library
on the next scan, with their favorites and play time. Choose it under "Skipped" to read it again.
Nothing on disk changes.

### A folder Plumemu does not recognise

If a folder's name matches no system but holds files an emulator can open, Settings › Library
lists it as "Unknown folder" with its file count, and Home tells you when a new one appears. Open
it and pick its system, or choose Ignore this folder. Plumemu never renames or moves your files.

### A folder Plumemu could not read

If the last scan could not read a folder (an SD card taken out, say), Settings › Library shows
"Folder could not be read". Its games stay in your library until it reads again. Choose Scan again,
or Skip this folder to leave it out.

## What it reads

- **File types**: each system's usual ones, compressed or not, for example `.sfc` `.smc` and
  `.zip` for SNES; `.chd` `.cue`/`.bin` `.iso` `.pbp` `.m3u` for PlayStation; `.iso` `.chd` `.cso`
  for PS2 and PSP; `.rvz` `.iso` `.wbfs` for GameCube and Wii; `.nsp` `.xci` for Switch; `.zip`
  `.7z` `.chd` for arcade.
- **Games on several discs** show as one game: `(Disc 1)`, `(CD1)` and the like are read, a `.cue`
  hides its `.bin` tracks, and an `.m3u` stands for the discs it lists.
- **Copies of one game** (regions, revisions, arcade clones) show as one row. Game menu › Other
  versions picks which one plays.
- **Skipped**: hidden files; folders named `bios`, `firmware`, `media`, `images`, `videos`,
  `manuals`; saves and save states; images, videos, text files and readmes; known BIOS files
  (`scph1001.bin`, `gba_bios.bin`, `neogeo.zip` and many more). Arcade BIOS sets are not listed as
  games.

New files show up by themselves when you come back to Plumemu (Settings › Library › Find new games
automatically); Settings › Library › Rescan library reads everything again.

## BIOS files

Plumemu does not use BIOS files and never ships or downloads any. The emulator that needs one
looks for it in its own place: follow that emulator's guide (for RetroArch, its `system`
folder). Keeping your BIOS files in a `bios` folder next to your system folders is fine; Plumemu
skips it. Dump BIOS files from consoles you own.

## Emulators

Install emulators from their own developers. Plumemu finds the ones installed (it knows more than
350, RetroArch cores included) and starts none by itself:

- The first time you play a game of a system, it asks which emulator to use, the recommended one
  first, and remembers the answer.
- Settings › Emulators changes the emulator of a system.
- Game menu › Play with picks one for a single game; that choice wins.
- Emulators that come with legal risk are listed but never recommended. Plumemu ships no keys or
  firmware.

## Game names and box art

- **Names** come from the game itself, offline, without an account: Plumemu reads the game's own
  code, knows arcade sets by their short names (`sf2.zip` is Street Fighter II), and cleans up
  file names. Wrong name? Game menu › Fix name; your fix stays.
- **Source builds** have no name packs, so games are named from their cleaned file names; Fix name
  still works.
- **Covers**: a game without box art gets a card in its system's color with its title, so the
  grid view works for every system with no account. Android games and Ports show their app icon.
- **Box art** is optional. Setup asks "Download box art?" (No to begin with). Default source
  fetches box fronts from libretro's public server with no account; or sign in with your own free
  [ScreenScraper](https://www.screenscraper.fr) account for box art and game details. Change it
  later in Settings › Box art & accounts. It downloads on Wi-Fi only. Wrong cover? Game menu › Fix
  box art. Box art region, for games with no box in their own region, is in the ScreenScraper panel.

## Achievements

To see a game's achievements, sign in to [RetroAchievements](https://retroachievements.org):

1. On retroachievements.org, open your settings and copy your user name and your Web API key. Use
   the key, not your password.
2. In Plumemu open Settings › Box art & accounts › RetroAchievements and type both (Paste in Plumemu's
   keyboard helps). Sign in checks them.
3. Open a game's Info: its achievements show with badges, points and dates, and a game you have
   mastered has a small trophy in the list.

Plumemu only shows achievements; your emulator awards them, signed in there on its own. Some
`.chd`, `.rvz` and `.wia` files may not match a game yet, and 3DS, MS-DOS and Arduboy games show
none. Settings › Box art & accounts › RetroAchievements › Sign out removes your name, key and progress.

## Lists and keeping things tidy

- **Recent, Favorites and Apps** sit at the top of the system column. Y adds or removes a favorite.
- **Recent** keeps 10, 20 or 50 games (Settings › Library › Recent size; 20 to begin with). In a
  game's menu, "Remove from Recent" takes one out (Undo brings it back for four seconds);
  Settings › Library › Clear Recent… empties it.
  Play time and statistics stay either way.
- **Search**: X.
- **Hide a game**: game menu › Hide; or Not a game for a file that is not one. Both come back in
  Settings › Library › Hidden games.
- **Hide a system**: Settings › Library › Systems, Y. Its games stay in Recent, Favorites and
  Search.
- **System order**: A–Z, by maker and year, or your own, in Settings › Library › System order.

## Home screen

Settings › Home screen › Use as home screen makes Plumemu what the Home button opens. Home then
brings you back to the game you played last. On a handheld with a second screen, such as the AYN
Thor, Settings › Second screen sets what it shows (that section appears only on a handheld that has
one).

## Typing

Plumemu's keyboard has a Paste key. Sign-in steps open with the keyboard ready in the first empty
field. Settings › Controls › Keyboard switches to Android's keyboard.

## Look

Settings › Appearance: seventeen ready-made [color sets](COLORS.md) or your own colors, text size,
Fade list edges and Language. Every screen uses Jost in one weight. The focused cover or app lifts a
little inside its own space with a thin light rim; buttons and switches are flat, and a switch that
is off is outlined. Settings › Home screen: grid size and the left edge strip.
Settings › Controls: Confirm with B, sounds and vibration. List or grid is SELECT on the main
screen.

## Back up, restore, update

- Settings › Advanced › Back up saves your settings, or everything (library, box art, play time),
  to a file; Restore puts it back.
- When a new version is out, the top bar shows "Update" and the version; START opens it. You can
  also use Settings › About › Update. It installs the new version, keeping your library and
  settings. Afterwards Plumemu opens itself again, or shows a notification to tap if Android keeps it
  from coming to the front (Settings › About › Updates can turn notifications on), and a card shows
  what is new, once. Only the official build has this; if you built Plumemu from the source, install new
  versions yourself.

## Something wrong?

Settings › About › Report a problem, or an [issue](https://github.com/joiha-steven/plumemu/issues)
with your Plumemu version (Settings › About), your device and its Android version. A wrong
translation has its [own template](translations/README.md), and so does a
[color set idea](COLORS.md#suggest-a-color-set).
