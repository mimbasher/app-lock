# How AppLock works inside

The [README](../README.md) covers what AppLock does and the two watchers it can
run. This is the rest: how the process keeps itself alive, where the code lives,
and the two pieces of state that have to be read without suspending.

## Staying alive

An accessibility service alone is not enough: when Android, an OEM battery manager or
a recents swipe kills the process, nothing re-arms the watcher and every locked app
opens freely until AppLock is launched again.

`LockGuardService` exists to close that. It runs as a foreground service whenever
anything is locked, which keeps the process — and the accessibility service living in
it — near the back of the queue when Android reclaims memory. On a 5-second heartbeat
it checks whether a watcher is genuinely live, falls back to polling if the
accessibility service has gone away (including when its toggle still reads "on" but
Android never re-bound it), and names the mode actually in force in its notification.
`WatchdogJob`, a persisted periodic job, restarts the guard if the process is killed
anyway. When nothing is protecting anything, a high-priority notification and a card
on the home screen say so, rather than failing silently.

None of this survives a **force stop**: that cancels our jobs along with everything
else, and no unprivileged app can schedule its way out of it. Reaching the force-stop
button means reaching Settings, so locking Settings itself is the only defence AppLock
can offer there.

The accessibility service declares `canRetrieveWindowContent="false"` — the package
name arrives on the event itself, so the app never reads screen contents.

## Why window events get filtered

`TYPE_WINDOW_STATE_CHANGED` fires for keyboards, toasts, the status bar and system
popups, not just app switches. Treating those as "the user left the app" would spend
the unlock and re-prompt constantly. `AppWatchService` therefore ignores any package
that does not own a launcher icon.

## Source map

```
app/src/main/java/com/turboo/applock/
├── AppLockApplication.kt        Application + repository singleton; caches the theme for the gate
├── Permissions.kt               Every special permission, and the Settings screen that grants it
├── data/
│   ├── LockRepository.kt        DataStore: locked packages, re-lock delay, theme, self-lock
│   ├── SessionGate.kt           In-memory "already authenticated" state (never persisted)
│   ├── InstalledApps.kt         Launchable-app list + icons for the picker
│   └── ThemeMode.kt             System / Light / Dark
├── service/
│   ├── AppWatchService.kt       AccessibilityService watcher
│   ├── LockGuardService.kt      Keeps the process resident; polls if the watcher dies
│   └── WatchdogJob.kt           Persisted job that restarts the guard after a kill
├── receiver/BootReceiver.kt     Starts the guard after a reboot
├── update/AvailableUpdate.kt    A release newer than the installed one; shared by both flavours
└── ui/
    ├── MainActivity.kt          Compose host: onboarding/home/settings, and the self-lock gate
    ├── AppListViewModel.kt      App list, search, filter, update state
    ├── OnboardingScreen.kt      First-run screen for the grants the app cannot work without
    ├── HomeScreen.kt            Update banner, search, filter chips, the app list
    ├── SettingsScreen.kt        Re-lock delay, theme, lock-AppLock-itself, version
    ├── UpdateBanner.kt          Silent until there is something to say
    ├── LockScreenActivity.kt    The gate; FragmentActivity (BiometricPrompt requires it)
    ├── LockScreen.kt            Gate UI
    ├── BiometricAuth.kt         Shared system-prompt helper, including the API 28–29 quirk
    └── theme/Theme.kt           Light/dark schemes, Material You where the device has it
```

The two flavours ([in the README](../README.md#two-flavours)) add one package each, implementing
the same `update` API:

```
app/src/github/                  Sideload build: the real updater
├── AndroidManifest.xml          INTERNET, REQUEST_INSTALL_PACKAGES, the installer FileProvider
├── java/…/update/UpdateChecker.kt    Latest GitHub release, compared a version component at a time
├── java/…/update/UpdateInstaller.kt  Downloads the APK, hands it to the system installer
└── res/xml/update_paths.xml     The one cache directory the FileProvider may share

app/src/play/                    Play build: the same API, inert
└── java/…/update/               UpdateChecker returns null; UpdateInstaller does nothing
```

## Theme

**System**, **Light** or **Dark**, chosen in the app and stored with the other
settings; **System** is the default and follows the device.

The choice also applies to the gate, which has to colour itself the instant it
is created. A suspending read there would flash the wrong scheme over the app
being covered, so `AppLockApplication` keeps the last value in
`LockRepository.cachedThemeMode` for the life of the process and the gate reads
that synchronously.
