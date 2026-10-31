# Privacy

Last updated: 31 October 2026.

## Builds you make yourself

Plumemu's source is public. A build made from it is made without the addresses of my servers: it
sends **no usage statistics and no crash reports** (the switch is not shown), **checks for no
updates**, and has no ScreenScraper account built in, so it contacts none of the servers named
below. Everything below describes the official build.

## Usage statistics and crash reports

Plumemu can send anonymous usage statistics and crash reports to the developer, to decide what to
fix first. They are **off unless you switch them on**, in the setup or later in Settings ›
Advanced › Send usage statistics. (Copies installed before 0.0.8-beta build 850, when the setup
had it on, keep the choice they have; the same switch turns it off.)

- **What is sent**: your country, your device model, the Android and Plumemu versions, a random
  install number that is not linked to you, which screens and settings are used, how often and how
  long Plumemu is used, and how long you play, by system and emulator. Crash reports hold what
  went wrong and where in the app, and may contain file and folder names.
- **What is never sent**: game titles, the words you search for, your ScreenScraper account, the
  other apps on your device, your advertising ID. Your IP address reaches the server with any
  connection; it is used only to tell the country and is not kept.
- **Switching it off** after it was on sends one anonymous "switched off" count, with your country and device
  details but no install number, and then nothing more. Crash reports stop too.
- **Kept** by the developer, with no time limit.

## Problem reports

Settings › About › Report a problem sends a report only when you press Send. It holds what you
wrote, an e-mail address if you give one, and, unless you untick it, Plumemu's log and device
details. The log may contain file and folder names. You see what will be sent before you press
Send. Settings › Advanced › Export logs only shares the log file where you choose; Plumemu sends
nothing itself. Reports are kept by the developer, with no time limit.

Two other things go over the network: the update check, below, which carries nothing about you,
and box art, only if you sign in to ScreenScraper.

## Update check

Once a day, on Wi-Fi or Ethernet that is not metered, when Plumemu comes to the front, it asks
`plumemu-app.stevensdesk.com` whether there is a newer version. It also asks when you press
Settings › About › Check for updates.

- **What is sent**: a plain HTTPS request for one file, `update/latest.json`. No identifier, no
  account, nothing about your library, your device or the version you have.
- **Who sees it**: the server is hosted by Cloudflare, which sees your IP address and that the
  request comes from an app (`okhttp/<version>`), as for any web request.
- **Downloading an update** happens only after you press Update (on mobile data only after a
  second yes), from the same server.
- **What stays on the device**: when it last checked and what it found, in storage that is never
  backed up.
- **Turn it off** with Settings › About › Check for updates daily. Check for updates then runs only
  when you press it.

## Box art (only if you sign in to ScreenScraper)

Box art and game details come from [ScreenScraper](https://www.screenscraper.fr), using your own
ScreenScraper account. Without an account Plumemu does not contact ScreenScraper at all.

- **What is sent to ScreenScraper**: your ScreenScraper user name and password, and for each
  game its system, its file name and size, and, when Plumemu knows them, the file's checksum
  (CRC32) and the game's serial code; if those find nothing, the game's title, to search by name.
  When you rename a game with Fix name, the new name is sent
  once to look up its box art. When you search for a cover from the game menu, your search words
  are sent too. Box art is fetched over Wi-Fi only. ScreenScraper's own privacy terms apply to
  what it receives.
- **When ScreenScraper has no box art** for a game, Plumemu also asks two public image servers,
  during the same signed-in fetch on Wi-Fi: for a Steam game without its Steam number, the game's
  title is sent to Steam's store search (Valve), and its pictures come from Steam's image server
  by that number; for other games, the game's file name is sent to the libretro thumbnail server
  (`thumbnails.libretro.com`). Nothing else, and no account, goes to either.
- **What stays on the device**: the box art and details it sends back, and your password,
  encrypted with a key that never leaves the device.
- **Sign out** in Settings › Library › ScreenScraper removes the account from the device.
- Your ScreenScraper account is never written to Plumemu's logs.

## Battery use

On a handheld with two screens, while you play with the lower screen's clock and Show battery on,
Plumemu reads the battery level and current to say how long the charge lasts. For each play it
keeps how long it measured, the charge at the start and the end, and the average draw, so it can
tell a game's usual pace.

- **Recorded on the device only, never sent** to Plumemu or anyone else.
- It is read only while that clock shows and the screen is on.
- **Turn it off** with Show battery off, or the lower screen set to black while you play.

## Backups

Settings › Advanced › Back up… saves a file where you choose. It holds your settings and, while
you are signed in, your ScreenScraper user name and password in plain text, so keep it private. A
backup of everything also holds your library, box art, play time and battery use. Plumemu never
sends a backup anywhere; where the file goes is up to you.

## What Plumemu reads on the device

- **The folders you pick** in Android's folder picker. Plumemu cannot see any other folder, and
  you can remove a folder at any time in Settings. It never deletes, moves or renames your files.
- **Which emulators are installed**, by asking Android about a fixed list of known emulator apps.
- **The apps you can open**, to show them under Apps.
- **Shortcuts you add to Plumemu** from other apps (for example a Steam game from GameNative),
  kept as games.
- **When and how long you play**, to show play time under Settings › Statistics.
- **The battery**, while you play on two screens, as above.

All of it stays on the device, in Plumemu's own storage, and is removed when Plumemu is
uninstalled.

## Questions

Open an issue under [Issues](../../issues).

