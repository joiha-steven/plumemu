# Privacy

Last updated: 23 October 2026.

## Alpha builds send nothing to Plumemu

Builds marked alpha (tags `alpha.*`) have no Plumemu account, no usage statistics and no crash
reports. Nothing Plumemu sees is sent to Plumemu or to anyone else, with one exception you choose
yourself: box art from ScreenScraper, below.

## Box art from ScreenScraper (only if you sign in)

Box art and game details come from [ScreenScraper](https://www.screenscraper.fr), using your own
ScreenScraper account. Without an account Plumemu does not contact ScreenScraper at all.

- **What is sent to ScreenScraper**: your ScreenScraper user name and password, and for each
  game its system, its file name and size, and, when Plumemu knows them, the file's checksum and
  the game's serial code. When you search for a cover from the game menu, your search words are
  sent too. Box art is fetched over Wi-Fi only. ScreenScraper's own privacy terms apply to what
  it receives.
- **What stays on the device**: the box art and details it sends back, and your password,
  encrypted with a key that never leaves the device.
- **Sign out** in Settings › Library › ScreenScraper removes the account from the device.
- Your ScreenScraper account is never written to Plumemu's logs.

## Backups

Settings › Advanced › Back up… saves a file where you choose. It holds your settings and, while
you are signed in, your ScreenScraper user name and password in plain text, so keep it private. A
backup of everything also holds your library, box art and play time. Plumemu never sends a
backup anywhere; where the file goes is up to you.

## What Plumemu reads on the device

- **The folders you pick** in Android's folder picker. Plumemu cannot see any other folder, and
  you can remove a folder at any time in Settings.
- **Which emulators are installed**, by asking Android about a fixed list of known emulator apps.
- **The apps you can open**, to show them under Apps.
- **Shortcuts you add to Plumemu** from other apps (for example a Steam game from GameNative),
  kept as games.
- **When and how long you play**, to show play time under Settings › Statistics.

All of it stays on the device, in Plumemu's own storage, and is removed when Plumemu is
uninstalled.

## Later versions

Later versions are planned to send anonymous usage statistics, with a switch in setup and in
Settings to turn them off. Before any version sends anything, this page will say exactly what is
sent, where to, and how long it is kept.

## Questions

Open an issue under [Issues](../../issues).
