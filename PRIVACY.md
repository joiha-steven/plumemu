# Privacy

Last updated: 21 October 2026.

## Alpha builds send nothing

Builds marked alpha (tags `alpha.*`) have no account, no usage statistics and no crash reports.
Nothing Plumemu sees leaves the device.

## What Plumemu reads on the device

- **The folders you pick** in Android's folder picker. Plumemu cannot see any other folder, and
  you can remove a folder at any time in Settings.
- **Which emulators are installed**, by asking Android about a fixed list of known emulator apps.
- **The apps you can open**, to show them under Apps.

All of it stays on the device, in Plumemu's own storage, and is removed when Plumemu is
uninstalled.

## Later versions

Later versions are planned to send anonymous usage statistics, with a switch in setup and in
Settings to turn them off. Before any version sends anything, this page will say exactly what is
sent, where to, and how long it is kept.

## Questions

Open an issue under [Issues](../../issues).
