# AppLock

Per-app lock for Android. Pick any installed app; the next time it comes to the
foreground, a full-screen gate asks for your fingerprint, face, or the phone's own
PIN/pattern/password before the app is revealed.

Kotlin + Jetpack Compose, no third-party services, nothing leaves the device.

## How it works

Android has no "lock this app" API, so this does what every app locker does:

1. **Watch** which package just came to the foreground.
2. **Cover** it with our own activity the instant a locked package appears.
3. **Authenticate** via the system biometric prompt. Pass → the gate finishes and the
   app is there. Back/cancel → the user is sent to the home screen.

The app is never actually blocked; the gate just lands on top of it faster than the
user can interact with it.

### Two watchers

| | `AppWatchService` (primary) | `ForegroundPollService` (fallback) |
|---|---|---|
| Mechanism | `AccessibilityService`, `TYPE_WINDOW_STATE_CHANGED` events | Polls `UsageStatsManager` every 300 ms |
| Latency | Effectively instant | 200–700 ms — the app flashes into view first |
| Battery | Negligible, event-driven | Noticeable; needs a foreground-service notification |
| Grant | Settings → Accessibility | Settings → Special access → Usage access |

The accessibility route is strongly preferred. The poller exists only for users who
would rather not enable an accessibility service; it starts automatically when usage
access is granted and the accessibility service is off.

The accessibility service declares `canRetrieveWindowContent="false"` — the package
name arrives on the event itself, so the app never reads screen contents.

### Why window events get filtered

`TYPE_WINDOW_STATE_CHANGED` fires for keyboards, toasts, the status bar and system
popups, not just app switches. Treating those as "the user left the app" would spend
the unlock and re-prompt constantly. `AppWatchService` therefore ignores any package
that does not own a launcher icon.

## Source map

```
app/src/main/java/com/turboo/applock/
├── AppLockApplication.kt        Application + repository singleton
├── Permissions.kt               Every special permission + the Settings screen that grants it
├── data/
│   ├── LockRepository.kt        DataStore: locked package set, re-lock delay
│   ├── SessionGate.kt           In-memory "already authenticated" state (never persisted)
│   └── InstalledApps.kt         Launchable-app list + icons for the picker
├── service/
│   ├── AppWatchService.kt       AccessibilityService watcher
│   └── ForegroundPollService.kt UsageStats polling fallback
├── receiver/BootReceiver.kt     Restarts the poller after a reboot
└── ui/
    ├── MainActivity.kt          Compose host, re-reads permission state on resume
    ├── AppListScreen.kt         Setup checklist, re-lock chips, search, app list
    ├── AppListViewModel.kt
    ├── LockScreenActivity.kt    The gate; FragmentActivity (BiometricPrompt requires it)
    └── LockScreen.kt            Gate UI
```

### Session rules

`SessionGate` holds unlocks in memory only — a reboot or process death means everything
is locked again. An unlock ends when:

- the screen turns off, or
- the user leaves the app (when re-lock is set to *Immediately*), or
- the configured delay elapses (1 or 5 minutes).

## Build

Requires the Android SDK and **JDK 21** — AGP does not run on JDK 26, so
`gradle.properties` pins `org.gradle.java.home` to `/usr/lib/jvm/java-21-openjdk`.
Change that line if your JDK lives elsewhere.

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### CI

`.github/workflows/build.yml` lints and builds the debug APK on every push to
`main`, on pull requests, and on demand via **Actions → Build APK → Run
workflow**. The APK lands as the `app-debug` artifact on the run page, with the
lint report beside it.

The runner strips the `org.gradle.java.home` line from `gradle.properties`
first, since that path is local to a developer machine.

## First-run setup

The app shows a checklist for whatever is missing:

1. **Accessibility → AppLock app watcher** — how the app knows what you opened.
2. **Display over other apps** — required, or Android 10+ blocks the gate from
   launching from the background.
3. **Battery optimisation off** — optional, but Android may otherwise kill the watcher.

## Known limits

These are inherent to doing this without system privileges:

- A user can disable the accessibility service in Settings, or uninstall the app.
  Blocking that needs Device Admin, which is heavily restricted.
- Aggressive OEM task-killers (Xiaomi, Oppo, Huawei, Samsung) kill background
  services; those devices need manual whitelisting.
- There is a brief window where the locked app is visible before the gate lands —
  short with the accessibility watcher, clearly visible with the poller.
- If no biometric is enrolled *and* no screen lock is set, the gate opens rather than
  refusing, since there would be nothing to check against.
- Recents-screen previews of locked apps are not suppressed; only the gate itself sets
  `FLAG_SECURE`.

## Play Store note

`QUERY_ALL_PACKAGES` and accessibility-service use are both restricted and need a
declared, justified use case. App-locking is an accepted category, but expect review
scrutiny. None of this matters for sideloading.
