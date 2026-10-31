# Usage statistics

What users are told is the public PRIVACY.md and the text in Settings.
Self-hosted; off until the person switches it on; one anonymous event from people who switch it off after having it on; never
game titles. This file is the contract: the app's typed events and the server's allow-list
both follow it, and a server test fails when they differ.

## What the events answer

How many installs keep using the app (daily and monthly active, day 1 / 7 / 30 retention), in
which countries and on which devices, which features and emulators are used, where people leave
setup, how long they play, how many switch statistics off, and what crashes. Only the fields in
the table below are sent; what is under "Never sent" never is.

## Common fields (every batch while on)

`install_id` (random UUID made at first launch, reset by reinstalling or clearing data) and, in
`common`: `app_version`, `version_code`, `build_type` (release / debug), `device_manufacturer`,
`device_brand`, `device_model`, `device_name` (`Build.DEVICE`), `soc_model` (Android 12+),
`android_version`, `sdk_int`, `screen_dp` (width×height), `screen_density`, `second_screen`
(yes/no), `ram_bucket` (<2, 2–4, 4–8, 8+ GB), `app_language`, `system_language`. Each event has
its own `id` (UUID) and `time`. Country is read by the server from Cloudflare (`request.cf.country`);
the IP address is never read or stored.

## Events

Values in `a / b` are the only ones allowed. `*_bucket` values are ranges, never exact counts of
private things. `system_id` and `emulator_id` are the platform data's ids.

| Event | Extra fields |
|---|---|
| `first_open` | none; sent at once, once per install: at the first launch when statistics are on, otherwise together with the first switch-on event (a new install has them off) |
| `app_open` | `cold_start_ms` (process start to the first frame; only on a cold start, left out otherwise), `from` (home_key / icon / update / other) |
| `app_background` | `seconds_in_app`, `screens` (count), `input` (gamepad / touch / both / none) |
| `setup_step` | `step` (folders / second_screen / artwork / scan / ready), `seconds`, `action` (next / back / skip) |
| `setup_completed` | `seconds`, `artwork` (yes / no), `folders` (count), `systems_found` (count), `emulators_picked` (count); emulators_picked counts the systems with games for which an installed emulator was found (setup finds emulators, the person picks one on each system's first game) |
| `library_scanned` | `trigger` (setup / startup / manual / folder_added / change_check), `games_bucket` (0, 1–99, 100–999, 1k–5k, 5k–10k, 10k+), `systems` (count), `seconds`, `result` (ok / folder_lost / error) |
| `screen_opened` | `screen` (home / system / search / favorites / recent / apps / details / game_menu / settings_<section> / report / setup) |
| `view_changed` | `system_id`, `view` (list / grid / details), `grid_size` (small / medium / large, grid only) |
| `search_used` | `query_length_bucket` (1–2, 3–5, 6+), `results_bucket` (0, 1–9, 10+), `opened_result` (yes / no); never the words |
| `favorite_changed` | `system_id`, `change` (added / removed) |
| `game_launched` | `system_id`, `emulator_id`, `from` (list / grid / details / recent / favorites / search / second_screen / statistics), `result` (ok / emulator_missing / emulator_not_chosen / file_missing / error); emulator_id is left out when none was chosen or installed |
| `game_session` | `system_id`, `emulator_id`, `minutes` (whole, capped at 600; from the launch to Plumemu back in front, the same time Settings › Statistics counts; sessions under 10 seconds are not sent) |
| `emulator_picked` | `system_id`, `emulator_id`, `where` (setup / settings / per_game); setup is not sent today, as setup picks none |
| `setting_changed` | `key`, `value` (only fixed choices: background preset or "custom", text colour, accent preset, "by_system" or "custom", font, text size, text weight, left edge, grid size, button layout, keyboard, switch system with, sounds, animations, …; never a colour value or free text) |
| `controller_connected` | `kind` (built_in / xbox / playstation / nintendo / other) |
| `artwork_status` | `downloaded`, `failed`, `quota_hit` (counts) |
| `update_check` | `trigger` (daily / manual), `result` (none / available / error) |
| `update_installed` | `from_version`; sent when the version that ran last is known. The first update onto the build that starts storing it sends nothing, as does a fresh install; neither is counted as the other |
| `backup_made` / `restore_done` | `kind` (settings / everything), `result` (ok / error) |
| `error_shown` | `where` (screen id), `code` (the app's error code, never its message) |
| `queue_overflow` | `dropped` (count) |
| `stats_switched_on` | `where` (setup / settings) |

Events tied to a system or emulator (`view_changed`, `favorite_changed`, `game_launched`,
`game_session`, `emulator_picked`) are sent only for the platform data's systems and emulators:
Android apps of the Apps entry, pinned shortcuts and anything without a platform-data system send
none of them (`screen_opened` with `apps` still goes). A started pinned shortcut keeps its app's
package where an emulator id would be, for Recent and play time on the device; that package is never
sent (no `game_launched`, no `game_session`). A play whose return asks the RetroArch core question
sends its `game_session` only once the question is over (Yes or B); No takes the play back and sends
nothing. `screen_opened` goes on a real change of
screen only, never per focus move or per frame: a screen counts once it has stayed shown for one
second (walking the systems or Settings' sections with the shoulder keys passes several a second).
`view_changed` with `details` is not sent today (the details column comes with the screen's width,
not a choice), nor is `artwork_status` (the box-art run's counts are not wired yet); `error_shown`
is sent for setup's messages and the main screen's library error so far.

Sent through their own endpoints (plan › API):

| Endpoint | What |
|---|---|
| `/api/v1/declined` | `stats_declined`: sent **once** per install, **without** `install_id`, with `where` (setup / settings) and `common`; then nothing more |
| `/api/v1/crashes` | `kind` (exception / anr / native), exception type, message, full stack with causes, thread, screen, uptime; **may contain file paths** |
| `/api/v1/reports` | problem reports the person sends: text, optional e-mail, optional log and `common`; sent whether statistics are on or off |

## Never sent

Game titles, search text, the ScreenScraper username or password, other installed apps, the
advertising id, IP addresses. File and folder paths only inside crash and problem reports.

## Delivery

Only while the switch is on (new installs: off; an install that had it on keeps it): `first_open` at once at its first launch; then batched at most every 5 minutes while in use
and when the app goes to the background; kept on the device while offline (2,000 events at most);
HTTPS only; a failure never affects the person. Switching statistics off deletes the queue and
stops everything after the single `stats_declined` event; what was sent before stays on the
server, kept with no time limit.

Adding an event: add a row here first, then the server's allow-list, then the app code, then a test
that checks its fields.

## Crash reports

A crash report is an exception, an ANR or a native crash, with its stack. Android can end the
process for an ANR while the app is not in use: for example Android 13 raises "Application does
not have a focused window" against the home app when a key arrives with the screen off. Such an
exit (description starting `bg anr`) is reported as `BackgroundANR`, not `ANR`, so it groups apart
on the dashboard from ANRs a person met. Every ANR's stack carries the process's importance after
the build line (325 = top app with the screen off, 100 = on screen). Background ANRs are still
reported: one with importance 100, or with a Plumemu frame on the main thread, is Plumemu's own
fault and is treated as a bug.
