# Contributing to AppLock

All pull requests, feedback and contributions are welcome — issues and bug
reports just as much as code.

There is no hidden configuration here, and nothing needs an account, an API key
or a paid service. If you can build an Android app, you can build this one.

## Requirements

- **JDK 21.** AGP does not run on JDK 26, so `gradle.properties` pins
  `org.gradle.java.home` to `/usr/lib/jvm/java-21-openjdk`. Change that line if
  your JDK lives elsewhere — but do not commit the change.
- **Android SDK**, API 36 (`compileSdk`/`targetSdk`). Android Studio or a bare
  `cmdline-tools` install both work.
- **A device or emulator on API 26+** (`minSdk = 26`).

## Build and run

```sh
./gradlew assembleGithubDebug
adb install -r app/build/outputs/apk/github/debug/app-github-debug.apk
```

`github` is the default of two flavours — it is the sideloaded build, and the
one to develop against. `play` is the same app with the self-updater stripped
out, because Google Play does not allow one; see
[Two flavours](README.md#two-flavours). Build it with `assemblePlayDebug` if you
are touching anything in `update/`.

Lint, which CI runs over both flavours on every push and pull request:

```sh
./gradlew lintGithubDebug lintPlayDebug
```

A local build has no version passed to it, so it reports itself as `1.0-dev`.
CI passes `-PappVersionName` and `-PbuildNumber`.

After installing, grant the permissions the app's own checklist asks for —
accessibility, display over other apps, and optionally battery optimisation.
Without the first two, nothing locks, and that is not a bug.

## Where things live

The [source map](docs/architecture.md#source-map) lists every file and what it
does. Two things are worth knowing before you change behaviour:

- **The watchers.** `AppWatchService` (accessibility events) is the primary
  path; `LockGuardService` (usage-stats polling) is the fallback, and it also
  keeps the process alive. A change to lock behaviour usually has to hold for
  both.
- **The updater is flavour-split.** The real `UpdateChecker`/`UpdateInstaller`
  live in `src/github`, inert stubs with the same signatures live in `src/play`,
  and `AvailableUpdate` is shared from `src/main`. If you change one side's
  signatures, change the other, or the Play build stops compiling — which is
  why CI lints both.
- **`SessionGate` is in-memory only.** Unlocks are deliberately never
  persisted. Writing them to disk would survive a reboot, which defeats the
  point.

## Good places to start

Roughly in order of how much they would help:

- **A PIN of AppLock's own.** The gate currently accepts the *device*
  credential, so anyone who has watched you unlock your phone can already open
  every locked app. A separate PIN closes the most realistic hole in the app.
- **Recents protection.** Locked apps still show their last screen in the app
  switcher, with no gate involved (see
  [Known limits](README.md#known-limits)).
- **Auto-lock newly installed apps.** The `PACKAGE_ADDED` receiver already
  exists for the launchable-package cache, so this is a small change.
- **Real-device reports.** The fingerprint path and OEM background-killing
  behaviour are the least-tested parts of this app — the fingerprint path has
  only ever run against an emulator's PIN. Telling us what your phone does is
  genuinely useful, especially Xiaomi, Oppo, Huawei and Samsung. This needs no
  code at all, just an issue.

The README's [planned features](README.md#planned-features-) list is the best
guide to what else is wanted.

## Pull requests

- Branch off `main`; CI builds and lints every pull request against it.
- Keep a pull request to one change. A redesign and a bug fix in one branch are
  hard to review and harder to revert.
- Match the surrounding code. Kotlin official style, as
  `kotlin.code.style=official` already declares.
- **Say what you tested it on.** If you are changing behaviour rather than
  fixing a typo, name the device or emulator and the Android version. This is a
  security tool, and "it compiles" is not the same as "it locks".
