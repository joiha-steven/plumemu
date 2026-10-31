<h1 align="center">Plumemu</h1>

<p align="center">A calm, light launcher for Android retro handhelds. Free and open source.</p>


<p align="center">
  <img src="media/hero.png" width="100%" alt="Plumemu in Indigo: the Super Nintendo list, 248 games, with Super Metroid selected in a soft accent pill; its cover, year, maker, genre, rating and description on the right (covers are drawn stand-ins). Below, three smaller views: Super Nintendo as a grid of covers in Pocket, the Dreamcast list with its system band in Banana, and Search with its keyboard in Super">
</p>

<p align="center">
  <b>0.1.0</b> · Android 6.0 or later · tested on the AYN Thor, the Retroid Pocket 6, the Retroid Pocket Nova and the Retroid Pocket Classic ·
  <a href="../../releases">Download</a> · <a href="https://plumemu.stevensdesk.com">Website</a>
</p>

<p align="center">
  The first stable release, and the first with its source open: GPL-3.0, see <a href="#source">Source</a>.
</p>

---

## Why Plumemu

1. **Your games have names from the first minute, offline.** No account, no scraping, no internet:
   Plumemu reads each game's own code and knows about 29,000 arcade sets, so a folder of cryptic files
   becomes a tidy list the moment setup ends.
2. **It works out of the box.** Pick your ROMs folder and you are playing: it finds the emulators
   you have installed, picks one for each system and adds new games by itself. Setup takes two
   minutes.
3. **It is light, even on old handhelds.** An 8.2 MB download that runs on Android 6.0, opens a
   library of 10,000 games in about a quarter of a second and uses no CPU while it waits.
4. **It never touches your files.** It sees only the folder you pick, and never deletes, moves or
   renames a game file.
5. **It uses everything your handheld has.** The second screen shows details and how long your
   battery lasts at this game's pace, and every screen works with both buttons and touch.
6. **It moves smoothly.** Lists and grids glide for a fifth of a second and the selection glides
   with them, so you can follow where you are.
7. **It is open.** The source is public under GPL-3.0, and [what Plumemu sends](#what-plumemu-sends)
   is a table you can check against the code.

## What it does

### Your games, named and in order

- **Names your games itself, offline.** It reads the game's own code inside zip, 7z and CHD files
  and disc images, knows about 29,000 arcade sets by their short names, takes names for newer
  Switch games from GameTDB and cleans up messy file names. Arcade games get a clean name with the
  region in its own column. No account, no internet. A wrong name is fixed from the game menu, and
  the fix sticks.
- **One row per game.** Copies of one game (regions, revisions, arcade sets) show as one row, even
  from separate folders; Other versions in the game menu picks which one plays. Same-name variants
  carry a label (Rev 1, Beta, translation, hack), and `.cue`, `.gdi` and `.m3u` parts are not rows
  of their own.
- **New games show up by themselves.** Copy games in or put in an SD card: they appear when you
  come back to Plumemu, and Home tells you about them. A folder Plumemu cannot place in a system is
  listed in Settings › Library, where you pick its system.
- **Android games and Ports.** Installed Android games get a system of their own, and native port
  apps (Delta Touch and others) join Ports. Show as game, Show as port and Not a game in the menus
  correct Plumemu's choice.
- **Box art, if you want it.** Sign in with your own free
  [ScreenScraper](https://www.screenscraper.fr) account and Plumemu fetches box art and game
  details over Wi-Fi. A wrong cover is replaced from the game menu. The box art panel shows the
  space used and has Clear box art; a daily cleanup removes orphaned images, and games that keep
  failing on the server stop retrying after 3 days.
- **Details and sorting.** Info shows the year, publisher, developer, genre, players and the whole
  description. Sort games has a Year order.
- **Recent that you control.** Take one game out of Recent from its menu, choose how many it keeps
  (10, 20 or 50) and clear it, all in Settings › Library. Your play time and statistics stay.
- **Skip a sub-folder.** Settings › Library › ROM folders can leave out a sub-folder (a folder of
  demos, say) so its games never show up. Nothing on disk is touched.

### Playing

- **Emulators picked for you.** Plumemu finds the emulators you have installed and picks one per
  system; any game or system can use another. Dual-screen builds such as Eden Duo and PPSSPP Duo
  count as the emulators they are. Each emulator is offered only for the file types it
  opens. If a RetroArch core may be missing, Plumemu asks "Did the game start?" and offers the
  other emulators. A game that is slow to start shows "Starting game…", and if the emulator has not
  opened after 20 seconds Plumemu says so instead of waiting for ever. While you play, Plumemu holds
  its background work (reading new files, box art downloads) until you are back.
- **Second screen, and your battery.** On a handheld with two screens, like the AYN Thor, the lower
  screen shows the chosen game's details, your statistics while Settings is open, and, while you
  play, a clock with how long the battery lasts at this game's pace. The top bar shows a small bolt
  before the battery percentage while the device is on its charger. A game you have played says
  "2 h on this charge" before you start it. Everything still works on one screen.
- **Light enough for old handhelds.** An 8.2 MB download that runs on Android 6.0, opens your
  library in about a quarter of a second and uses no CPU while it sits on the home screen.
  [Measured](#light-on-the-hardware) on a slow emulator with 10,000 games.
- **Smooth.** Every list and grid glides over 200 ms and slows into place, and the selection pill
  glides with it. Plumemu builds the next page before you reach it, so a step never stalls to draw.

### Make it yours

- **Colors.** Ten ready-made sets, each previewed before you pick it: Plumemu, Paper, Pocket, Dot
  matrix, Super, Indigo, Charcoal, Ivory, Banana and Cream. Super, Charcoal, Ivory, Banana and
  Pocket color the A, B, X and Y keys. Or make your own background, text and accent colors with
  sliders that show the whole screen in the new color as you drag.
- **Lists or covers.** Show any system, Recent or Favorites as a list or as a grid of covers in
  three sizes. Apps can be a grid of icons too.
- **One sliding view.** Systems, games and details move as one strip, laid out for 16:9 and 4:3
  screens. On 4:3 and narrow screens the game's details peek in beside the list. Beside the games,
  a narrow strip shows game counts, a code per system or a letter rail you can tap or drag.
- **About each system.** Above a system's games, a short band with its badge, the years it was
  made and a few lines about it. Settings › Home screen › System info turns it off.
- **Text you can set.** Text size starts at Large, and Text weight is Thin, Medium or Bold.
- **Sounds and vibration.** Settings › Advanced sets sounds Off, Quiet or Loud (they follow the
  media volume) and vibration, which is on by default. A Language list is there too: English,
  Vietnamese, Spanish, French, Japanese, Korean and Chinese.
- **Buttons and touch**, equally, everywhere. Plumemu has its own compact keyboard, made for the
  D-pad, so Search keeps its results in view while you type.

### Your files and your data

- **Your files stay yours.** Setup takes two minutes: pick your ROMs folder in Android's own folder
  picker. No access to all your files, ever, and Plumemu never deletes, moves or renames a game
  file.
- **Back up and restore.** Save your settings, or everything (library, box art, play time), into
  one file, checked after writing, and put it back on this device or a new one.
- **Updates from inside the app** (official builds). Once a day on Wi-Fi Plumemu looks for a new
  version; Settings › About › Update installs it.
- **Anonymous usage statistics and crash reports, only if you want them** (official builds). They
  are off until you switch them on in the setup or in Settings › Advanced › Send usage statistics.
  [What Plumemu sends](#what-plumemu-sends) lists every connection.

<p align="center">
  <img src="media/color-sets.png" width="100%" alt="The same Super Nintendo screen in all ten ready-made color sets, each named below: Plumemu, Paper, Pocket, Dot matrix, Super, Indigo, Charcoal, Ivory, Banana and Cream">
</p>

<table>
  <tr>
    <td width="50%"><img src="media/battery-in-game.png" alt="The lower screen while a game runs: a large clock, Wi-Fi and 38% battery, \"45 min left\" with \"empty at 22:32\", and a chart of the battery falling at 48% an hour against a dotted usual pace of 40%"></td>
    <td width="50%"><img src="media/system-card.png" alt="The lower screen's card for Super Nintendo: the SNES badge, 248 games, Nintendo, home console, 1990 to 2003, and the game played last, Super Metroid, yesterday"></td>
  </tr>
  <tr>
    <td align="center">How long your battery lasts, while you play</td>
    <td align="center">Each system's facts on the second screen</td>
  </tr>
  <tr>
    <td width="50%"><img src="media/grid.png" alt="Super Nintendo as a grid of covers on a dark background, with the selected cover raised and ringed in the accent color (the covers are drawn stand-ins)"></td>
    <td width="50%"><img src="media/grid-light.png" alt="PlayStation as a smaller grid of covers, five to a row, in the light Paper colors, Final Fantasy VII selected and ringed"></td>
  </tr>
  <tr>
    <td align="center">Any system as a grid of covers</td>
    <td align="center">In three sizes, light or dark</td>
  </tr>
  <tr>
    <td width="50%"><img src="media/system-info.png" alt="Super Nintendo's games in Ivory with a short band above them: the SNES badge, Nintendo, home console, 1990 to 2003; Chrono Trigger selected, its cover and description on the right, and a charging bolt before 82% in the top bar"></td>
    <td width="50%"><img src="media/keyboard.png" alt="Search in Indigo with Plumemu's own keyboard: \"zelda\" typed, 8 games found, and the first results stay in view above the keys"></td>
  </tr>
  <tr>
    <td align="center">A few lines about each system</td>
    <td align="center">A keyboard made for the D-pad</td>
  </tr>
  <tr>
    <td width="50%"><img src="media/four-three.png" alt="Plumemu on the Retroid Pocket Nova's 4:3 screen in a custom wine color: the Super Nintendo list with Super Metroid selected, and its details peeking in from the right edge"></td>
    <td width="50%"><img src="media/system-codes.png" alt="Super Nintendo's games with a short code for each system in the strip on the left: REC, FAV, APP, SNS, GBA, PS1, PSP, NDS and GC"></td>
  </tr>
  <tr>
    <td align="center">4:3 screens, like the Retroid Pocket Nova</td>
    <td align="center">Counts, system codes or a letter rail at the edge</td>
  </tr>
  <tr>
    <td width="50%"><img src="media/colors.png" alt="Settings › Appearance › Colors: the ten ready-made color sets, each shown as a small preview"></td>
    <td width="50%"><img src="media/button-colors.png" alt="Super Nintendo in the Banana color set, a yellow screen with the A, B, X and Y keys in red, teal and dark yellow; Zelda: A Link to the Past selected, its cover and description on the right"></td>
  </tr>
  <tr>
    <td align="center">Ten ready-made color sets</td>
    <td align="center">A, B, X and Y in their classic colors</td>
  </tr>
  <tr>
    <td width="50%"><img src="media/settings.png" alt="Settings › Library in Dot matrix, a green screen: ROM folders (2 folders), Rescan library, Find new games automatically, Sort games, System order, ScreenScraper, Box art region and Recent size; the two ROM folders and their game counts on the right"></td>
    <td width="50%"><img src="media/update.png" alt="Settings › About in Paper: version 0.1.0 installed, a newer version offered with its release notes and an Update button (an example screen)"></td>
  </tr>
  <tr>
    <td align="center">Settings that say what they do</td>
    <td align="center">Updates from inside the app</td>
  </tr>
</table>

## Light on the hardware

Many retro handhelds are slow and run old Android, so Plumemu is built to use as little as it
can. Measured with 0.0.7-beta on a slow Android 6.0 emulator (2 GB of memory) with a library of
10,000 games:

| | |
|---|---|
| Download | 8.2 MB |
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
2. Open it on the device and allow the install when Android asks.
3. Open Plumemu and pick your ROMs folder. To make it the home screen, say yes when it asks, or
   press Home and choose Plumemu.

**Updating.** Install the new version the same way, over the old one; your library and settings
stay. The official build also finds new versions itself: START Settings shows a dot, and
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
| L2 / R2 | Previous / next system |
| L1 / R1 | In a list sorted by name, previous / next letter; in Recent and in lists sorted by Last played or Year, one page up / down; in a game grid, smaller / larger covers |
| Home | With Plumemu as the home screen: back to the game you played last |

The button printed A confirms. Settings › Advanced can make the bottom button (B, or ✕) confirm
instead, and Settings › Controls › Switch system with can move system switching to L1 / R1. Touch
works everywhere: tap to select, tap again to play. Settings › Controls › Push right to play lets
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
| Usage statistics and crash reports: device model, Android and Plumemu versions, a random install number, which screens and settings are used, play time by system. Never game titles or search words. | The developer (`plumemu.stevensdesk.com`) | Only after you switch it on | Settings › Advanced › Send usage statistics |
| A problem report: what you wrote, an e-mail if you give one, the log unless you untick it | The developer | Only when you press Send | Do not press Send |
| An update check: a request for one file, nothing about you | The developer (`plumemu-app.stevensdesk.com`) | Once a day on Wi-Fi, or when you press Check for updates | Settings › About › Check for updates daily |
| Box art lookups: your ScreenScraper account, and each game's file name, size, checksum and title | [ScreenScraper](https://www.screenscraper.fr) | Only while you are signed in, on Wi-Fi | Settings › Library › ScreenScraper: Sign out or Pause |
| Box art ScreenScraper lacks: a Steam game's title, or a game's file name | Steam (Valve) and the libretro thumbnail server | Only while you are signed in to ScreenScraper, on Wi-Fi | The same |

A build you make yourself from the source sends no statistics and checks no updates, and has no
ScreenScraper account built in (see [Source](#source)). Your IP address reaches each server with
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
  credentials.
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
