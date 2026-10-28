# AppLock

[![Build APK](https://github.com/mimbasher/app-lock/actions/workflows/build.yml/badge.svg)](https://github.com/mimbasher/app-lock/actions/workflows/build.yml)

Per-app lock for Android. Pick any installed app; the next time it comes to the
foreground, a full-screen gate asks for your fingerprint, face, or the phone's own
PIN/pattern/password before the app is revealed.

Kotlin + Jetpack Compose, no third-party services, nothing leaves the device.

If AppLock is useful to you, a ⭐ helps other people find it.

| Pick what to lock | Finish setup | The gate |
|---|---|---|
| ![App picker](docs/screenshots/applist.png) | ![Setup checklist](docs/screenshots/setup.png) | ![Lock screen](docs/screenshots/gate.png) |
| Locked apps float to the top. | Only missing grants are listed. | Shown the instant a locked app opens. |

The gate normally raises the system biometric prompt over itself immediately. It
is absent above because the real build sets `FLAG_SECURE`, and both it and the
system prompt refuse to be screenshotted — so these were taken from a temporary
build with that flag and the auto-prompt removed.

## How it works

Android has no "lock this app" API, so this does what every app locker does:

1. **Watch** which package just came to the foreground.
2. **Cover** it with our own activity the instant a locked package appears.
3. **Authenticate** via the system biometric prompt. Pass → the gate finishes and the
   app is there. Back/cancel → the user is sent to the home screen.

The app is never actually blocked; the gate just lands on top of it faster than the
user can interact with it.

### Two watchers

| | `AppWatchService` (primary) | `LockGuardService` (fallback mode) |
|---|---|---|
| Mechanism | `AccessibilityService`, `TYPE_WINDOW_STATE_CHANGED` events | Polls `UsageStatsManager` every 300 ms |
| Latency | Effectively instant | 200–700 ms — the app flashes into view first |
| Battery | Negligible, event-driven | Noticeable; needs a foreground-service notification |
| Grant | Settings → Accessibility | Settings → Special access → Usage access |

The accessibility route is strongly preferred. Polling exists for users who would
rather not enable an accessibility service, and as the takeover path when the
accessibility service dies.

### Staying alive

An accessibility service alone is not enough: when Android, an OEM battery manager or
a recents swipe kills the process, nothing re-arms the watcher and every locked app
opens freely until AppLock is launched again.

`LockGuardService` closes that. It runs as a foreground service whenever anything is
locked, checks on a 5-second heartbeat that a watcher is genuinely live, falls back to
polling if the accessibility service has gone away, and names the mode actually in
force in its notification. `WatchdogJob`, a persisted periodic job, restarts the guard
if the process is killed anyway. When nothing is protecting anything, a notification
and a card on the home screen say so, rather than failing silently.

None of this survives a **force stop**: that cancels our jobs along with everything
else, and no unprivileged app can schedule its way out of it. Reaching the force-stop
button means reaching Settings, so locking Settings itself is the only defence AppLock
can offer there.

Full mechanism, and a map of the source, in
[docs/architecture.md](docs/architecture.md).

### Session rules

`SessionGate` holds unlocks in memory only — a reboot or process death means everything
is locked again. An unlock ends when:

- the screen turns off, or
- the user leaves the app (when re-lock is set to *Immediately*), or
- the configured delay elapses (1 or 5 minutes).

### Pausing

A Quick Settings tile suspends locking for 15 minutes — for handing the phone to
someone, or for testing something without the gate in the way. It comes back by
itself, and tapping the tile again brings it back early.

Pausing asks for the same authentication the gate does. A tile that switched
locking off on one tap would be a bypass of the entire app, reachable from the
shade over a locked phone, so the tap only opens a prompt and the pause begins
once that prompt passes. Resuming never asks: turning protection back on cannot
need permission.

Like an unlock, the pause is held in memory only, so a process death comes back
locking rather than paused. While it is running, the guard notification says
AppLock is paused rather than claiming to watch.

## Build

Requires the Android SDK and **JDK 21** — AGP does not run on JDK 26, so
`gradle.properties` pins `org.gradle.java.home` to `/usr/lib/jvm/java-21-openjdk`.
Change that line if your JDK lives elsewhere.

```sh
./gradlew assembleGithubDebug
adb install -r app/build/outputs/apk/github/debug/app-github-debug.apk
```

### Two flavours

| | `github` (default) | `play` |
|---|---|---|
| Shipped as | APK on the Releases page | App Bundle on Google Play |
| Self-updater | Checks GitHub, downloads, offers to install | Absent |
| `INTERNET` | Declared | Not declared — no network access at all |
| `REQUEST_INSTALL_PACKAGES` | Declared | Not declared |

Uploading the `play` bundle is Console work; see
[docs/releasing.md](docs/releasing.md).

Google Play forbids an app that downloads and installs its own APK, so the
updater cannot ship in a Play build. Both flavours compile against the same
`update` API: `src/play` supplies stubs whose `check()` returns null, which
leaves the banner and the launch check inert, and the permissions and
`FileProvider` the real updater needs are declared only in
`src/github/AndroidManifest.xml`. So the Play build contains no update code at
all, rather than update code that is switched off.

### CI

`.github/workflows/build.yml` lints both flavours and builds the `github` APK on
every push to `main`, on pull requests, and on demand via **Actions → Build APK
→ Run workflow**. The APK lands as the `applock-v<run number>` artifact on the
run page, with the lint report beside it, and a signed Play bundle is built
alongside it.

The runner strips the `org.gradle.java.home` line from `gradle.properties`
first, since that path is local to a developer machine.

### Releases

Every push to `main` publishes a GitHub Release by itself — `build.yml` creates
the tag `v1.<run number>` and attaches the APK, giving a permanent public
download link (Actions artifacts expire and need a login; release assets do
not). There is no tag to push by hand, and `run_number` only ever increases, so
the version never goes backwards either.

Pull requests build and lint but publish nothing.

Releases are signed from **v1.8** on; earlier ones were debug-signed. Keystore
and secret setup is in [docs/releasing.md](docs/releasing.md).

## First-run setup

The app shows a checklist for whatever is missing:

1. **Accessibility → AppLock app watcher** — how the app knows what you opened.
2. **Display over other apps** — required, or Android 10+ blocks the gate from
   launching from the background.
3. **Battery optimisation off** — optional, but Android may otherwise kill the watcher.

The home screen also recommends locking the Settings app once, dismissibly. Force
stop, the accessibility toggle and uninstall all live in Settings, so locking it means
each of those needs authentication first — the closest an unprivileged app gets to
tamper protection.

## Known limits

These are inherent to doing this without system privileges:

- A user can disable the accessibility service in Settings, force stop the app, or
  uninstall it. Blocking that needs Device Admin, which is heavily restricted. Locking
  the Settings app raises the bar, since all three routes go through it.
- Aggressive OEM task-killers (Xiaomi, Oppo, Huawei, Samsung) kill background
  services; `LockGuardService` and `WatchdogJob` recover from that within a job tick,
  but those devices still want manual whitelisting to avoid the gap entirely.
- There is a brief window where the locked app is visible before the gate lands —
  short with the accessibility watcher, clearly visible with the poller.
- If no biometric is enrolled *and* no screen lock is set, the gate opens rather than
  refusing, since there would be nothing to check against.
- Recents-screen previews of locked apps are not suppressed; only the gate itself sets
  `FLAG_SECURE`.

## Planned features 🚧

- AppLock's own PIN, separate from the phone's
- Intruder photo after failed attempts
- Auto-lock newly installed apps
- Hide locked apps from the recents switcher
- Per-app re-lock delay
- Backup and restore the locked list
- Uninstall protection (Device Admin)
- Publishing the `play` flavour on Google Play

## Open risks

Unresolved, as opposed to the inherent limits above:

- Releases before v1.8 are debug-signed, so the signed builds since cannot
  upgrade one of those in place — Android refuses an update signed by a
  different key, so anyone who installed early has to uninstall first.
- The fingerprint path has never run on real hardware — only the emulator's PIN.
- OEM task-killers may stop the watcher; untested.
- CI versions are `1.<run_number>`, so a counter reset would send versions
  backward.

## Contribution & Support

All pull requests, feedback and contributions are welcome! Please read the
[Contributing guidelines](CONTRIBUTING.md) for more about how to contribute and
how to build and run the project.

The most useful contribution needs no code at all: a **real-device report**.
The fingerprint path has only ever run against an emulator's PIN, and OEM
background-killing is untested — if you run AppLock on a real phone,
especially a Xiaomi, Oppo, Huawei or Samsung,
[open a device report](https://github.com/mimbasher/app-lock/issues/new?template=device-report.yml)
saying what it did.

If AppLock is useful to you, a ⭐ helps other people find it.

## Docs

- [Architecture](docs/architecture.md) — staying alive, the source map, theme
  and session internals.
- [Contributing](CONTRIBUTING.md) — requirements, build, run, and what CI
  checks.
- [Releasing](docs/releasing.md) — signing keys, and publishing the `play`
  build to Google Play.
- [Privacy policy](docs/privacy-policy.md)
