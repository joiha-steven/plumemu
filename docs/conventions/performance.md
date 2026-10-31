# Performance

Plumemu runs on low-end handhelds with small batteries, so it wastes no resource of any kind. These
budgets are measured with the benchmarks in [testing.md](testing.md) and a change that breaks one is
a defect.

## Idle

- On the home screen at rest the app does no CPU work: no polling, no animated backgrounds. The
  clock redraws once a minute, on the minute; the battery level comes from the system's broadcast.
- One exception: the second screen's in-game clock card, with Show battery on and the screen on,
  reads the battery on a wait of its own (every second for the first 2 minutes of a stretch, then
  every 10 s). Nothing is read otherwise, and no thread, alarm or wake lock is added for it
 .

## Background work

- No background service. Scanning and box-art downloads run only when asked, or on a schedule the
  person set, through WorkManager with constraints (Wi-Fi, battery not low).
- A person's own action (Fix box art…, the lookup after a rename) fetches directly: the person is
  waiting and asked for it. It needs Wi-Fi and a usable account; when either is missing, or the
  scraper's limit or a pause holds it, the app says why. The battery is not checked.
- The content pass (a whole-file CRC of loose cartridge files, no network) runs by itself after
  every scan that leaves such files unread, through WorkManager with battery not low, without a
  setting. It also reads loose files of cartridge systems that have no header reader but do have a
  bundled CRC pack.

## Memory and size

- With 10,000 games: under 100 MB on the device. Covers are decoded at cell size, and as `RGB_565`
  on low-RAM devices (`isLowRamDevice`, or under 3 GB).
- Release APK under 10 MB without the name data; the name data is as small as it can be made and
  is measured on its own.

## Time

- Cold start under 1 s on the AYN Thor and under 2.5 s on a weak device.
- Scrolling drops no frames at the screen's refresh rate, with 10,000 games, in list and grid.

## Motion

Transform and alpha only; the durations and the glide are in [ui.md](ui.md) › Motion.
