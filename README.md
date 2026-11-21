<h1 align="center">Plumemu</h1>

<p align="center"><b>Drop in a messy ROM folder, get a tidy game shelf.</b><br>
Real names, one row per game, a cover for every game. No account, no Wi-Fi needed. A light, free
home screen for Android retro handhelds.</p>


<p align="center">
  <img src="media/hero.png" width="100%" alt="Plumemu in Spice cube: the Super Nintendo list, 248 games, with Super Metroid selected in a lifted card; its cover, year, maker, genre, rating and description on the right (covers are posters Plumemu draws). Below, three smaller views: Super Nintendo as a grid of covers in Daylight with Final Fantasy VI selected, the Dreamcast list with its system band in Banana, and Search with "super" typed and its keyboard in Pea green">
</p>

<p align="center">
  <a href="https://plumemu.stevensdesk.com/assets/media/demo-v018.mp4"><img src="media/demo.jpg" width="100%" alt="Play the one-minute demo: Plumemu on a 4:3 handheld screen driven by its buttons, here the Super Nintendo games as a grid of covers"></a>
  <br><sub>One minute with the buttons, on a 4:3 screen. The only sound is Plumemu's own clicks.</sub>
</p>

<p align="center">
  <b>0.1.8</b> · Android 6.0 or later · tested on the AYN Thor (earlier versions also on the Retroid Pocket 6, Nova and Classic) ·
  <a href="../../releases">Download</a> · <a href="https://plumemu.stevensdesk.com">Website</a>
</p>

<p align="center">
  New in 0.1.8: a calmer, more finished look with one lifted card for the focused row, a folder card on the Collections page and one shape for every app icon. The source is open: GPL-3.0, see <a href="#source">Source</a>.
</p>

---

> I made Plumemu for my own handheld. Every launcher I tried wanted an evening of setup, or felt
> heavy on a small chip. I wanted one that opens fast, knows my games and gets out of the way when
> it's time to play. So I built it, and now it's yours too.

## Why pick Plumemu

- **Build your library anywhere, even with no Wi-Fi.** Plumemu reads each game's own code inside
  zip, 7z, CHD and disc images, so `sf2.zip` becomes *Street Fighter II: The World Warrior*, not "sf2".
- **See each game once.** Three regional copies, a Rev 1 and a set in another folder are one row
  in your list, not five.
- **Play in two minutes, not an evening.** Pick your ROMs folder; Plumemu finds your emulators,
  picks one for each system and adds new games by itself.
- **Keep the power for your games.** A small download that runs on Android 6.0 and 2 GB handhelds,
  opens 10,000 games in about a quarter of a second and sits idle while it waits.
- **Put the second screen to work.** On the AYN Thor, the bottom screen shows the game's details
  and how long your battery lasts at this game's pace.
- **Use buttons or touch.** Every screen works fully with the gamepad and with your finger.
- **Keep your files safe.** It sees only the folder you pick and never deletes, moves or renames a
  game file.
- **Pay nothing, check everything.** No price, no account, no ads. The source is public under
  GPL-3.0, and [what Plumemu sends](#what-plumemu-sends) is a short table you can check against the code.

### The numbers

| | |
|---|---|
| Download | 9.5 MB |
| Runs on | Android 6.0 and later, 2 GB handhelds, 16:9 and 4:3 screens |
| Start to your library, 10,000 games | 0.25 s on a slow Android 6.0 emulator; 0.31 s on the AYN Thor with 2,058 games |
| Memory, 10,000 games | 63 MB |
| CPU on the home screen | almost none: one CPU tick in 60 s |
| Arcade sets it knows by name | about 29,000 |
| Price, source | Free, GPL-3.0 |

How each figure was measured: [Light on the hardware](#light-on-the-hardware).

### Quick install

```sh
# On the handheld: download the APK from the latest release and open it. Or, from a computer:
adb install -r plumemu-<version>.apk
adb shell am start -n com.plumemu/.MainActivity
```

Pick your ROMs folder, say whether you want box art, and you're playing. To make Plumemu your home
screen, say yes when it asks, or press Home and choose Plumemu. More in [Install](#install).

## What it does

### Your games, named and in order

- **Names your games itself, offline.** It reads the game's own code, knows about 29,000 arcade
  sets by their short names and cleans up messy file names. A wrong name is fixed from the game
  menu, and the fix sticks.
- **One row per game.** Regions, revisions and arcade sets fold into one row, even from separate
  folders; Other versions in the game menu picks which one plays. Same-name variants carry a label
  (Rev 1, Beta, translation, hack), and `.cue`, `.gdi` and `.m3u` parts never show as rows.
- **New games show up by themselves.** Copy games in or put in an SD card: they're there when you
  come back, and Home tells you. A folder Plumemu cannot place is listed in Settings › Library ›
  Files not added, where you pick its system.
- **Collections, made by you or for you.** Recent, Most played and Recently added fill themselves,
  between Favorites and Apps. Make your own from any game's menu, with games from any system, and
  rename, move or delete them in Settings › Library › Collections. On the folders page the focused
  folder shows a row of its covers, its game and system counts and its first titles.
- **Android games and Ports.** Installed Android games get a system of their own, and native port
  apps join Ports. Show as game, Show as port and Not a game in the menus correct Plumemu's choice,
  and App info opens Android's own page for the app.
- **A cover for every game.** A game without box art gets a card in its system's color with its
  title, so every system and collection can be a grid of covers with no account. Android games and
  Ports show their app icon, every icon in the same rounded square.
- **Real box art, if you want it.** Setup asks first, No by default. The default source fetches box
  fronts over Wi-Fi from libretro's public server with no account, or your own free
  [ScreenScraper](https://www.screenscraper.fr) account (Settings › Library › ScreenScraper) adds
  game details too.
- **Achievements, if you use RetroAchievements.** Sign in under Settings › Library ›
  RetroAchievements with your user name and Web API key, never your password. A game's Info lists
  its badges, points and dates, and a mastered game carries a small trophy in the list.
- **Find the right game fast.** Sort by name, last played, year, most played, recently added or
  rating, right from the list. Info shows the year, publisher, developer, genre, players and the
  whole description.
- **Nothing goes missing quietly.** Settings › Library › Files not added lists every file left out,
  with the reason, and Unidentified games suggests the closest official titles.
- **Skip a sub-folder.** Settings › Library › Game folders can leave out a folder of demos, say,
  so its games never show up. Nothing on disk is touched.

### Playing

- **Emulators picked for you.** Plumemu finds the emulators you have and picks one per system; any
  game or system can use another. Each emulator is offered only for the file types it opens.
- **No silent dead ends.** If a RetroArch core may be missing, Plumemu asks "Did the game start?"
  and offers other emulators. If an emulator hasn't opened after 20 seconds, Plumemu says so
  instead of waiting forever.
- **Quiet while you play.** Plumemu holds its background work, like reading new files and box art
  downloads, until you're back.
- **Your second screen, and your battery.** On a handheld with two screens, like the AYN Thor, the
  bottom screen shows the selected game, its achievements, and your statistics while Settings is open. While you play it
  shows a clock, a battery ring, how long the battery lasts at this game's pace and a chart of the
  session; its options are in Settings › Screens.
- **Smooth.** Every list and grid glides over 200 ms and slows into place. Plumemu builds the next
  page before you reach it, so a step never stalls to draw.

### Make it yours

- **Colors.** Pick one of seventeen ready-made sets, each previewed first: Plumemu (a deep blue, the
  default), Daylight, Ultramarine, Magenta, Peach, Matcha, Mocha, Front loader, Spice cube, Pea
  green, Pocket, Lavender, Red and gold, Ivory, Clear grape, Cold grey and Banana. Or mix your own
  background, text and accent with sliders that repaint the screen as you drag.
- **True black and classic keys.** On an OLED screen setup offers true black, and eight of the sets
  color the A, B, X and Y keys.
- **Lists or covers.** Show any system or collection as a list or as a grid of covers in three
  sizes (L1 / R1). Apps can be a grid of larger icons too.
- **One sliding view.** Systems, games and details move as one strip, laid out for 16:9 and 4:3
  screens. A narrow strip at the edge shows a short code for each system that you can tap.
- **Text you can read.** Text size starts at Large, and every screen uses one typeface, Jost. The
  clock, battery and years use a monospaced face so their figures line up.
- **Sounds, vibration and language.** Button sounds can be Off, Quiet or Loud, and vibration is on
  by default (Settings › Controls). Language is in Settings › Appearance: English, Vietnamese,
  Spanish, French, Japanese, Korean and Chinese.
- **A keyboard made for the D-pad.** Search keeps its results in view while you type, and the
  keyboard has a Paste key.

### Your files and your data

- **Your files stay yours.** You pick your ROMs folder in Android's own folder picker. Plumemu never
  asks for all your files, and never deletes, moves or renames a game file.
- **Move to a new handheld with one file.** Save your settings, or everything (library, box art,
  play time), into one file that is checked after writing. Put it back on this handheld or a new
  one from Settings › About › Back up and restore.
- **Updates from inside the app** (official builds). Once a day on Wi-Fi Plumemu looks for a new
  version, and START opens it. Afterwards a card tells you what's new, once.
- **Anonymous usage statistics and crash reports, only if you want them** (official builds). They
  stay off until you switch them on in setup or in Settings › About › Send usage statistics.

<p align="center">
  <img src="media/color-strip.png" width="100%" alt="The same Super Nintendo screen in six color sets: Plumemu, Daylight, Ivory, Banana, Pea green and Ultramarine">
  <br><sub>The same screen in six of the color sets</sub>
</p>

<p align="center">
  <img src="media/color-sets.png" width="100%" alt="The same Super Nintendo screen in all seventeen ready-made color sets, each named below: Plumemu, Daylight, Ultramarine, Magenta, Peach, Matcha, Mocha, Front loader, Spice cube, Pea green, Pocket, Lavender, Red and gold, Ivory, Clear grape, Cold grey and Banana">
</p>

<table>
  <tr>
    <td width="50%"><img src="media/collection-grid.png" alt="An own collection called RPG in Mocha, a grid of twelve covers from the Super Nintendo, PlayStation and Dreamcast, Final Fantasy VII selected (the covers are drawn posters)"></td>
    <td width="50%"><img src="media/game-menu.png" alt="Super Metroid's game menu in Magenta over the Super Nintendo list: Play, Play with Snes9x, Add to Favorites, Add to collection, Name, Info and Hide game"></td>
  </tr>
  <tr>
    <td align="center">Your own collections, mixing every system</td>
    <td align="center">START on any game opens its menu</td>
  </tr>
  <tr>
    <td colspan="2"><img src="media/collections-folders.png" alt="Collections in Ivory: the folders page with the own collection RPG selected, one row of covers on the right with its name, 12 games in 3 systems and the games it holds (the covers are drawn posters)"></td>
  </tr>
  <tr>
    <td colspan="2" align="center">Collections: the focused folder shows its covers, counts and first titles</td>
  </tr>
  <tr>
    <td colspan="2"><img src="media/android-games.png" alt="Android games as a grid in Peach: twelve made-up apps, every icon in the same rounded shape under a one-line name"></td>
  </tr>
  <tr>
    <td colspan="2" align="center">Android games, every icon in one shape</td>
  </tr>
  <tr>
    <td width="50%"><img src="media/battery-in-game.png" alt="The lower screen while a game runs: a large clock at 21:47, a ring at 38%, \"45 min left\" and \"empty at about 22:32\", a chart of the session with a dotted line for the usual pace, and \"Played 0:40 · Used 32% · 48% an hour · usually 40%\""></td>
    <td width="50%"><img src="media/system-card.png" alt="The lower screen's card for Super Nintendo: the four games played last as cards, play time, games played and achievements, then Nintendo, home console, 1990 to 2003, and a line about it"></td>
  </tr>
  <tr>
    <td align="center">How long your battery lasts, while you play</td>
    <td align="center">Each system's facts on the second screen</td>
  </tr>
  <tr>
    <td width="50%"><img src="media/grid.png" alt="Super Nintendo as a grid of covers in the dark Plumemu colors, F-Zero selected inside a thin white outline (the covers are drawn posters)"></td>
    <td width="50%"><img src="media/grid-light.png" alt="PlayStation as a smaller grid of covers, five to a row, in the light Daylight colors, Final Fantasy VII selected in the top-right corner inside a thin dark outline"></td>
  </tr>
  <tr>
    <td align="center">Any system as a grid of covers</td>
    <td align="center">In three sizes, light or dark</td>
  </tr>
  <tr>
    <td colspan="2"><img src="media/apps-grid.png" alt="Apps in Cold grey as a grid of larger icons with one-line names, Gallery selected inside a thin white outline; every icon is the same rounded square; the left strip shows a code for each system"></td>
  </tr>
  <tr>
    <td colspan="2" align="center">Apps as a grid of larger icons</td>
  </tr>
  <tr>
    <td width="50%"><img src="media/system-info.png" alt="Super Nintendo's games in Ivory with a short band above them: the SNES badge, Nintendo, home console, 1990 to 2003; Chrono Trigger selected, its cover and description on the right, and a charging bolt before 82% in the top bar"></td>
    <td width="50%"><img src="media/keyboard.png" alt="Search in Spice cube with Plumemu's own keyboard: \"zelda\" typed, 8 games found, and the first results stay in view above the keys"></td>
  </tr>
  <tr>
    <td align="center">A few lines about each system</td>
    <td align="center">A keyboard made for the D-pad</td>
  </tr>
  <tr>
    <td width="50%"><img src="media/four-three.png" alt="Plumemu on the Retroid Pocket Nova's 4:3 screen in a custom wine color: the Super Nintendo list with Super Metroid selected, and its cover and details on the right"></td>
    <td width="50%"><img src="media/system-codes.png" alt="Super Nintendo's games with a short code for each system in the strip on the left: REC, FAV, APP, SNS, GBA, PS1, PSP and NDS"></td>
  </tr>
  <tr>
    <td align="center">4:3 screens, like the Retroid Pocket Nova</td>
    <td align="center">A code for each system at the edge</td>
  </tr>
  <tr>
    <td width="50%"><img src="media/colors.png" alt="Settings › Appearance › Colors in Ultramarine: a short help text and small previews of the first color sets, Ultramarine selected"></td>
    <td width="50%"><img src="media/button-colors.png" alt="Super Nintendo in the Banana color set, a yellow screen with the A, B, X and Y keys in red, teal and dark yellow; Zelda: A Link to the Past selected, its cover and description on the right"></td>
  </tr>
  <tr>
    <td align="center">Seventeen ready-made color sets</td>
    <td align="center">A, B, X and Y in their classic colors</td>
  </tr>
  <tr>
    <td width="50%"><img src="media/settings.png" alt="Settings › Library in Pea green, a green screen: the six sections on the left, then Game folders, Rescan library, Files not added, Emulators, Box art and both accounts; the Game folders panel on the right lists Internal storage and SD card with their game counts"></td>
    <td width="50%"><img src="media/update.png" alt="Settings › About in Daylight with the update panel: Plumemu 0.1.7 → 0.1.8, a short list of release notes and an Update button (an example screen)"></td>
  </tr>
  <tr>
    <td align="center">Settings that say what they do</td>
    <td align="center">Updates from inside the app</td>
  </tr>
  <tr>
    <td colspan="2"><img src="media/statistics.png" alt="Settings › Statistics in Clear grape: total play time, this week by day, games played and box art, then the most played games"></td>
  </tr>
  <tr>
    <td colspan="2" align="center">Your play time, day by day this week</td>
  </tr>
</table>

## Light on the hardware

Plenty of retro handhelds have modest chips and old Android, so I measure instead of guessing. These figures were measured with 0.0.7-beta (the download size is the current one), on a slow Android 6.0 emulator (2 GB of memory) with a library of
10,000 games:

| | |
|---|---|
| Download | 9.5 MB |
| Start to your library | 0.25 s |
| Memory | 63 MB |
| CPU while it sits on the home screen | almost none (one CPU tick in 60 s); the clock redraws once a minute |
| Looking for new games when you come back to Plumemu | usually 12 to 22 ms when nothing changed |
| Running in the background | nothing, except box art downloads you asked for and one read of new game files after a scan |

And on the handhelds, with their owner's own library (median of 11 cold starts):

| Handheld | Library | Start to your library |
|---|---|---|
| AYN Thor (Android 13) | 2,058 games | 0.31 s |
| Retroid Pocket Nova (Android 13) | 1,865 games | 0.31 s |
| Retroid Pocket Classic (Android 14) | the owner's library | first frame in 0.28 s |

The download and start-up figures are from earlier builds and are measured again for each release
that changes how Plumemu starts.

**Smoothness.** On a Retroid Pocket 6 (120 Hz screen, release build), I counted the frames that took
longer than one refresh (16.6 ms) among the last 120 drawn while stepping through the system list.
Before the 0.1.0 motion work it was 31 of 120 when tapping through the systems and 25 when
switching systems with L2 and R2; after it, 6 to 8 and 9 to 16. Tapping through the systems went from
42% slow frames to about 4%.

The battery figures read the battery only while the lower screen's clock shows and the screen is
on: once a second for two minutes, then every ten seconds. On a 24-minute recording of the AYN Thor
at rest, the time left was within 10% of the real one from the first minute, and within 1% after
fifteen minutes.

## Devices

**Tested on** the AYN Thor (both of its screens), the Retroid Pocket 6, the Retroid Pocket Nova
(4:3) and the Retroid Pocket Classic. Plumemu is laid out for handheld screens from 4.5 to 6 inches
in 16:9 and 4:3 (Retroid Pocket 5, 4 Pro and Mini, AYN Odin, and similar), but those are untested.
If you try it on one, a short report under [Issues](../../issues) helps a lot. A second screen is
optional: on the Thor it shows details, a clock and your battery, and nothing needs it.

## Install

1. Download the APK from the latest [release](../../releases) or from the
   [website](https://plumemu.stevensdesk.com).
2. Open it on the handheld and allow the install when Android asks.
3. Open Plumemu and pick your ROMs folder. To make it the home screen, say yes when it asks, or
   press Home and choose Plumemu.

**Updating.** Install the new version the same way, over the old one; your library and settings
stay. The official build also finds new versions itself: START Settings shows a dot, and
Settings › About › Version downloads it, checks that it really is Plumemu, and installs it. On
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
| L2 / R2 | Switch list: previous / next system or collection |
| L1 / R1 | In a list sorted by name, previous / next letter; in Recent and in lists sorted by Last played or Year, one page up / down; in a game grid, smaller / larger covers |
| Home | With Plumemu as the home screen: back to the game you played last |

The button printed A confirms. Settings › Controls › Confirm with B can make the bottom button (B,
or ✕) confirm instead, and Settings › Controls › Switch list with can move it to L1 / R1. Touch
works everywhere: tap to select, tap again to play. Settings › Controls › Push right twice to play lets
one hand do it: push right once to mark a game, again to start it. Pinned shortcuts (Steam,
Windows) start from Plumemu.

On Plumemu's keyboard: A types, B deletes, X is a space, Y switches to numbers, L1 / R1 move the
cursor. Settings › Controls › Keyboard switches to Android's keyboard.

## Not in this version

- Screenshots and videos of games; box art and details only for now.

## Not included, ever

No games, BIOS files, keys, firmware or emulators. Use files you own; emulators come from their
own developers.

## What Plumemu sends

Nothing leaves your device unless you switch it on or press a button for it. Everything the
official build can send, and to whom:

| What | Sent to | When | Turn it off |
|---|---|---|---|
| Usage statistics and crash reports: device model, Android and Plumemu versions, a random install number, which screens and settings are used, play time by system. For a game Plumemu could not name, once: its system, size, checksum and any code read from the game. Never game titles, file names or search words. | The developer (`plumemu.stevensdesk.com`) | Only after you switch it on | Settings › About › Send usage statistics |
| A problem report: what you wrote, an e-mail if you give one, the log unless you untick it | The developer | Only when you press Send | Do not press Send |
| An update check: a request for one file, nothing about you | The developer (`plumemu-app.stevensdesk.com`) | Once a day on Wi-Fi, or when you press Check for updates | Settings › About › Version |
| Box art lookups: your ScreenScraper account, and each game's file name, size, checksum and title | [ScreenScraper](https://www.screenscraper.fr) | Only while you are signed in, on Wi-Fi | Settings › Library › ScreenScraper: Sign out or Pause |
| Box art ScreenScraper lacks: a Steam game's title, or a game's file name | Steam (Valve) and the libretro thumbnail server | Only while you are signed in to ScreenScraper, on Wi-Fi | The same |
| Box art with no account: a game's name, in the picture's address | The libretro thumbnail server | Only if you choose the default source, on Wi-Fi | Settings › Library › Box art: Plumemu's own covers |
| Achievements: your RetroAchievements user name, your Web API key and the RetroAchievements number of a game, found from a hash of the game file that Plumemu computes on the handheld (no file leaves it). Badge pictures are plain downloads with no account data | [RetroAchievements](https://retroachievements.org) (`retroachievements.org`, badges from `media.retroachievements.org`) | Only while you are signed in: when a game's Info opens, after you play, and once a day on Wi-Fi | Settings › Library › RetroAchievements: Sign out deletes your name, key and progress |
| Platform data (the list of systems and emulators): a request for one file, nothing about you | The developer (`plumemu-app.stevensdesk.com`) | In the same daily update check | Settings › About › Version |

A build you make yourself from the source sends no statistics and checks no updates, and has no
ScreenScraper or RetroAchievements account built in (see [Source](#source)). Your IP address reaches each server with
any connection, as for any web request. The full detail is in [Privacy](PRIVACY.md).

## Source

Plumemu's code is open under the [GPL-3.0](LICENSE). This repository holds the source of each
release, copied as one commit named after the version and tagged `v<version>`. The day-to-day work
happens in a private repository, so comments in the code that point to project documents point
there, and you may not find them here.

**Build it.** You need a JDK (17 or newer) and the Android SDK with platform 37 and build-tools
37.0.0. Put the SDK's path in `local.properties` (`sdk.dir=...`), then:

```
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. On a Mac, `scripts/setup-toolchain.sh` installs
the SDK and two test emulators. `bash scripts/verify.sh --quick` runs the checks described below;
`bash scripts/verify.sh --ci` is what the build workflow runs on each tag.

**What differs from the official APK.**

- No usage statistics: the switch is not even shown, and nothing is sent.
- No update check: a build you made would not install over the official one anyway, because it is
  signed with a different key.
- No ScreenScraper account is built in, so no box art download unless you add your own developer
  credentials. Achievements need no built-in account: you sign in with your own key.
- No bundled name packs. The packs that turn a game's own code into its title are data I keep
  out of this repository, so a source build names games from their cleaned file names. The code
  that reads files and looks names up is here.
- The name Plumemu and the icon are not licensed (see [License](LICENSE.md)): if you publish a
  fork, give it another name and another icon.

**Issues, yes. Code pull requests, no.** Bug reports, ideas and questions under
[Issues](../../issues) are welcome and read. I do not take code pull requests: they are closed
without being read. If you send a good fix in an issue, I rewrite it from your description rather
than paste it, so that I can keep the right to change the licence later. Please do not take it
personally; it is a one-person project and I want it to stay that way. Translations are the
exception: a pull request that changes a file under [translations](translations/) is welcome.

## How it is made

I write Plumemu together with AI coding assistants, and I want to say so plainly. I do not read
every line of code myself, so the project cannot rest on my trusting the code. It rests on checks
that anyone can run:

- **Tests first for bugs.** A bug gets a test that fails before the fix and passes after, so it
  cannot quietly come back. There are more than 800 test files.
- **Screenshot tests at fixed screen sizes.** Every screen is rendered at the sizes of real
  handhelds (768 by 432 and 576 by 432) in a dark and a light color set and compared with the
  last accepted picture, so a layout that moves by a pixel is caught. (The accepted pictures are large and stay in my private repository.)
- **Layout measured in numbers**, not judged by eye: margins, gaps, alignment, touch size and text
  that would be cut off are measured, and the design rules in `docs/design` fix them.
- **Guards that fail the build.** A dozen small checks refuse, for example, text typed straight
  into the screen instead of a translatable string, colors and sizes written outside the theme,
  a module depending on one it should not, a hidden coroutine-cancellation bug, a new permission
  or a library under a copyleft licence. A guard that fails is fixed at the cause, never switched
  off.
- **A second pair of eyes.** A separate reviewing assistant, with no stake in the first one's work,
  reads each change that touches logic or data before it is merged.
- **Measured, not guessed, performance.** Start-up, memory, idle CPU and frame times are measured
  on a slow emulator and on real handhelds, and the numbers are in this page.
- **Real devices.** I install each release on my handhelds and use it before it goes out.
- **Errors are never silent.** The person sees a clear message and the log says enough to find the
  cause.

These are in [`docs/conventions`](docs/conventions) and `scripts/` in the source. If you find
something wrong, an issue is the way to tell me.

## Translations

Plumemu speaks English, Vietnamese, Spanish, French, Japanese, Korean and Chinese (Simplified and
Traditional). The French text comes from a reader of this project, [@oin](https://github.com/oin),
whose translation was checked by a native speaker; thank you. Most of the other translations have
not been read by a native speaker yet. The texts are in [translations](translations/): report a
wrong one with the
[translation template](https://github.com/joiha-steven/plumemu/issues/new?template=translation.yml),
or fix it in a pull request.

## More

- Report a problem or an idea: Settings › About › Report a problem inside the app, or
  [Issues](../../issues) with the Plumemu version (Settings › About), your device and its Android
  version.
- [Guide](GUIDE.md): how to lay out your games folder, BIOS files, emulators, names, settings.
- [Colors](COLORS.md): the ready-made color sets, and how to suggest one.
- [Changelog](CHANGELOG.md): what changed in each version.
- [Privacy](PRIVACY.md)
- [License](LICENSE.md): the code is GPL-3.0 ([LICENSE](LICENSE)); the name and icon are not licensed.
- [Third-party material](THIRD_PARTY.md): fonts, libraries and data inside the app.
