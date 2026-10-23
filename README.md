<h1 align="center">Plumemu</h1>

<p align="center">A calm, light launcher for Android retro handhelds.</p>


<p align="center">
  <img src="media/hero.png" width="100%" alt="Plumemu on the AYN Thor: the system list in the Plumemu colors, and four smaller screens in other color sets">
</p>

<p align="center">
  <b>0.0.3-beta</b> · Android 6.0 or later · tested on the AYN Thor only ·
  <a href="../../releases">Download</a> · <a href="https://plumemu.stevensdesk.com">Website</a>
</p>

---

## What it does

- **Two-minute setup.** Pick your ROMs folder in Android's own folder picker. No access to all
  your files, ever, and Plumemu never deletes, moves or renames a game file.
- **Emulators picked for you.** Plumemu finds the emulators you have installed and picks one per
  system. Any game or system can use another one.
- **Names your games itself, offline.** It reads the game's own code inside zip, 7z and CHD files
  and disc images, knows about 42,000 arcade sets by their short names, and cleans up messy file
  names. Arcade games get their clean name, with the region in its own column. No account, no
  internet. Wrong name? Fix it from the game menu; the fix sticks.
- **One row per game.** Copies of one game (regions, revisions, arcade sets) show as one row.
  Other versions in the game menu picks which one plays.
- **New games show up by themselves.** Copy games in or put in an SD card, and they appear when you
  come back to Plumemu.
- **One sliding view.** Systems, games and details move as one strip, laid out for 16:9 and 4:3
  screens.
- **Buttons and touch**, equally, everywhere. Plumemu has its own compact keyboard, made for the
  D-pad, so Search keeps its results in view while you type.
- **Box art, if you want it.** Sign in with your own free [ScreenScraper](https://www.screenscraper.fr)
  account and Plumemu fetches box art and game details over Wi-Fi. Show any system, Recent or
  Favorites as a list or as a grid of covers in three sizes. Apps can be a grid of icons too. Wrong cover? Pick the right one from the game menu.
- **Second screen, and your battery.** On a handheld with two screens, like the AYN Thor, the lower
  screen shows the chosen game's details, your statistics while Settings is open, and while you
  play a clock with how long the battery lasts at this game's pace. A game you have played says
  "2 h on this charge" before you start it. Everything still works on one screen.
- **Colors.** Ten ready-made color sets: Plumemu, Paper, Pocket, Dot matrix, Super, Indigo,
  Charcoal, Ivory, Banana and Cream, each previewed before you pick it. Super, Charcoal and Ivory
  color the A, B, X and Y keys. Or choose your own background, text and accent colors.
- **Back up and restore.** Save your settings, or everything (library, box art, play time), into
  one file, checked after writing, and put it back on this device or a new one.
- **Updates from inside the app.** Once a day on Wi-Fi Plumemu looks for a new version; Settings ›
  About › Update installs it.

<table>
  <tr>
    <td><img src="media/battery-in-game.png" alt="The lower screen while a game runs: the clock, the time left on this charge and a chart of this play's battery use"></td>
    <td><img src="media/keyboard.png" alt="Search with Plumemu's own keyboard; the results stay in view above it"></td>
  </tr>
  <tr>
    <td align="center">How long your battery lasts, while you play</td>
    <td align="center">A keyboard made for the D-pad</td>
  </tr>
  <tr>
    <td><img src="media/colors.png" alt="Settings › Appearance › Colors: the ten ready-made color sets, each shown as a small preview"></td>
    <td><img src="media/button-colors.png" alt="Game Boy Advance games in the Super color set, with the A, B, X and Y keys in red, yellow, blue and green"></td>
  </tr>
  <tr>
    <td align="center">Ten ready-made color sets</td>
    <td align="center">A, B, X and Y in their classic colors</td>
  </tr>
  <tr>
    <td><img src="media/settings.png" alt="Settings › Library: ROM folders, Rescan library, Find new games automatically, Sort games, System order, Systems, ScreenScraper and Box art region"></td>
    <td><img src="media/update.png" alt="Settings › About: an update is available, with its notes and an Update button"></td>
  </tr>
  <tr>
    <td align="center">Settings that say what they do</td>
    <td align="center">Updates from inside the app</td>
  </tr>
</table>

## Light on the hardware

Many retro handhelds are slow and run old Android, so Plumemu is built to use as little as it
can. Measured with 0.0.3-beta on a slow Android 6.0 emulator (2 GB of memory) with a library of
10,000 games:

| | |
|---|---|
| Download | 8.2 MB |
| Start to your library | 0.24 s |
| Memory | 56 MB |
| CPU while it sits on the home screen | none; the clock redraws once a minute |
| Looking for new games when you come back to Plumemu | usually 12 to 22 ms when nothing changed |
| Running in the background | nothing, except box art downloads you asked for |

The battery figures read the battery only while the lower screen's clock shows and the screen is
on: once a second for two minutes, then every ten seconds. On a 24-minute recording of the AYN Thor
at rest, the time left was within 10% of the real one from the first minute, and within 1% after
fifteen minutes.

## Devices

**Tested on the AYN Thor only, the one device I have.** Plumemu is laid out for handheld screens
from 4.5 to 6 inches in 16:9 and 4:3 (Retroid Pocket 5, 4 Pro, Mini and Nova, AYN Odin, and
similar), but those are untested. If you try it on one, a short report under [Issues](../../issues)
helps a lot. A second screen is optional: on the Thor it shows details, a clock and your battery,
and nothing needs it.

## Install

1. Download `plumemu-0.0.3-beta.apk` from the latest [release](../../releases) or from the
   [website](https://plumemu.stevensdesk.com).
2. Open it on the device and allow the install when Android asks.
3. Open Plumemu and pick your ROMs folder. To make it the home screen, say yes when it asks, or
   press Home and choose Plumemu.

**Updating.** From 0.0.2-alpha, install this version the same way, over the old one; your library
and settings stay. From now on Plumemu finds new versions itself: START Settings shows a dot, and
Settings › About › Update downloads it, checks that it really is Plumemu, and installs it. On
Android 8 and later, Android asks once to let Plumemu install apps. Nothing installs while you
play. Settings › About › Check for updates daily turns the daily check off.

## Controls

| Button | Does |
|---|---|
| A | Open, play |
| B | Back |
| X | Search |
| Y | Add to or remove from Favorites |
| START | Game menu; Settings from the system column |
| SELECT | List or grid (covers, or app icons in Apps) |
| L1 / R1 | Previous / next system; in a grid, smaller / larger covers |
| Home | With Plumemu as the home screen: back to the game you played last |

The button printed A confirms. Settings › Advanced can make the bottom button (B, or ✕) confirm
instead. Touch works everywhere: tap to select, tap again to play. Settings › Controls › Push
right to play lets one hand do it: push right once to mark a game, again to start it.

On Plumemu's keyboard: A types, B deletes, X is a space, Y switches to numbers, L1 / R1 move the
cursor. Settings › Controls › Keyboard switches to Android's keyboard.

## Not in this beta

- Languages other than English.
- Screenshots and videos of games; box art and details only for now.
- Shortcuts added from other apps are listed but cannot be started yet.

## Not included, ever

No games, BIOS files, keys, firmware or emulators. Use files you own; emulators come from their
own developers.

## More

- Report a problem or an idea: [Issues](../../issues), with the Plumemu version (Settings ›
  About), your device and its Android version.
- [Changelog](CHANGELOG.md): what changed in each version.
- [Privacy](PRIVACY.md)
- [License](LICENSE.md): free to download and use; not open source, all rights reserved.
- [Third-party material](THIRD_PARTY.md): fonts, libraries and data inside the app.
