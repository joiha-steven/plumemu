<h1 align="center">Plumemu</h1>

<p align="center">A calm, light launcher for Android retro handhelds.</p>

<p align="center">
  <img src="media/hero.png" width="100%" alt="Six Plumemu screens from the AYN Thor in four colour schemes: the system list in Plum on top; Paper and Sand below it; then search across every system, the ROM folder settings, and Back up and Restore">
</p>

<p align="center">
  <b>0.0.2-alpha</b> · Android 6.0 or later · tested on the AYN Thor only ·
  <a href="../../releases">Download</a>
</p>

---

## What it does

- **Two-minute setup.** Pick your ROMs folder in Android's own folder picker. No access to all
  your files, ever.
- **Emulators picked for you.** Plumemu finds the emulators you have installed and picks one per
  system. Any game or system can use another one.
- **Names your games itself, offline.** It reads the game's own code inside zip, 7z and CHD files
  and disc images, knows about 42,000 arcade sets by their short names, and cleans up messy file
  names. No account, no internet. Wrong name? Fix it from the game menu; the fix sticks.
- **One sliding view.** Systems, games and details move as one strip, laid out for 16:9 and 4:3
  screens.
- **Buttons and touch**, equally, everywhere.
- **Box art, if you want it.** Sign in with your own free [ScreenScraper](https://www.screenscraper.fr)
  account and Plumemu fetches box art and game details over Wi-Fi. Show any system as a list or
  as a grid of covers in three sizes. Wrong cover? Pick the right one from the game menu.
- **Second screen.** On a handheld with two screens, like the AYN Thor, the lower screen shows
  the chosen game's cover and details, your statistics while Settings is open, and a clock or
  nothing at all while you play. Everything still works on one screen.
- **Back up and restore.** Save your settings, or everything (library, box art, play time), into
  one file, and put it back on this device or a new one.
- **Your colours.** A background, light or dark text and an accent, used exactly as you pick them.

<table>
  <tr>
    <td><img src="media/setup.png" alt="Setup: the chosen ROMs folder and Continue"></td>
    <td><img src="media/game-info.png" alt="Game info, including where the game's name came from"></td>
  </tr>
  <tr>
    <td align="center">Setup in two steps</td>
    <td align="center">Every name says where it came from</td>
  </tr>
  <tr>
    <td><img src="media/appearance.png" alt="Appearance settings: background colours and a live preview"></td>
    <td><img src="media/systems-light.png" alt="The main screen with a light background"></td>
  </tr>
  <tr>
    <td align="center">Your background, text and accent</td>
    <td align="center">Light or dark, your choice</td>
  </tr>
</table>

## Light on the hardware

Many retro handhelds are slow and run old Android, so Plumemu is built to use as little as it
can. Measured with 0.0.2-alpha on a slow Android 6.0 emulator (2 GB of memory) with a library of
10,000 games:

| | |
|---|---|
| Download | 7.8 MB |
| Start to your library | 0.25 s |
| Memory | 54 MB |
| CPU while it sits on the home screen | none; the clock redraws once a minute |
| Running in the background | nothing, except box art downloads you asked for |

## Devices

**Tested on the AYN Thor (upper screen) only, the one device I have.** Plumemu is laid out for
handheld screens from 4.5 to 6 inches in 16:9 and 4:3 (Retroid Pocket 5, 4 Pro, Mini and Nova,
AYN Odin, and similar), but those are untested. If you try it on one, a short report under
[Issues](../../issues) helps a lot. A second screen is optional: on the Thor it shows details and
a clock, and nothing needs it.

## Install

1. Download `plumemu-0.0.2-alpha.apk` from the latest [release](../../releases).
2. Open it on the device and allow the install when Android asks.
3. Open Plumemu and pick your ROMs folder. To make it the home screen, say yes when it asks, or
   press Home and choose Plumemu.

## Controls

| Button | Does |
|---|---|
| A | Open, play |
| B | Back |
| X | Search |
| Y | Add to or remove from Favorites |
| START | Game menu; Settings from the system column |
| SELECT | List or grid of covers |
| L1 / R1 | Previous / next system; in a grid, smaller / larger covers |
| Home | With Plumemu as the home screen: back to the game you played last |

The button printed A confirms. Settings › Advanced can make the bottom button (B, or ✕) confirm
instead. Touch works everywhere: tap to select, tap again to play.

## Not in this alpha

- Languages other than English.
- Screenshots and videos of games; box art and details only for now.

## Not included, ever

No games, BIOS files, keys, firmware or emulators. Use files you own; emulators come from their
own developers.

## More

- Report a problem or an idea: [Issues](../../issues), with the Plumemu version (Settings ›
  About), your device and its Android version.
- [Changelog](CHANGELOG.md): what changed in each version.
- [Privacy](PRIVACY.md)
- [Licence](LICENSE.md): free to download and use; not open source, all rights reserved.
- [Third-party material](THIRD_PARTY.md): fonts, libraries and data inside the app.
