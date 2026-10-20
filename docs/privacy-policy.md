# AppLock Privacy Policy

_Last updated: 20 October 2026_

**AppLock collects nothing.** There is no account, no analytics, no crash
reporting, no advertising, and no server belonging to this app. Nothing you do
in AppLock is transmitted anywhere, and the developer cannot see any of it.

## What AppLock stores, and where

All of it stays in the app's own private storage on your device, and all of it is
deleted when you uninstall the app:

| Stored | What it is |
|---|---|
| Locked apps | The package names of the apps you chose to lock |
| Re-lock delay | Immediately, 1 minute, or 5 minutes |
| Theme | System, Light or Dark |
| Lock AppLock itself | On or off |

Unlocks are held **in memory only** and are never written to disk — closing the
app or restarting the phone locks everything again.

AppLock never stores, and never has access to, your fingerprint, face or
PIN/pattern/password. Authentication is performed by Android itself, which tells
the app only whether it succeeded.

## Network access

- **The Google Play version makes no network requests at all.** It does not hold
  the `INTERNET` permission, so it is incapable of sending anything anywhere.
- **The version downloaded from GitHub** checks `api.github.com` for a newer
  release — on launch, and when you tap "Check for updates". That request sends
  no identifiers; GitHub sees only what any HTTP request reveals, such as your IP
  address. If an update exists, the APK is downloaded from GitHub and handed to
  Android's installer, which always asks you before installing.

## Permissions, and why each one exists

| Permission | Why |
|---|---|
| Accessibility service | To know which app just came to the foreground, so a locked one can be covered. It is declared with `canRetrieveWindowContent="false"` — AppLock receives the package name of the window and **never reads the contents of your screen**. |
| Usage access | An optional fallback for the same purpose, if you would rather not enable an accessibility service. |
| Display over other apps | Required for the lock screen to appear over the app being opened. |
| Query all packages | To list your installed apps so you can choose which to lock. The list is read on your device and never leaves it. |
| Foreground service, notifications, boot completed | To keep the fallback watcher alive and restart it after a reboot. |
| Biometric | To ask Android for a fingerprint, face or device-credential check. |
| Battery optimisation exemption | Optional; stops Android killing the watcher. |
| Internet, install packages | **GitHub version only** — the update check and install described above. |

## Data sharing

None. No data is sold, shared, or sent to any third party, because no data is
collected in the first place.

## Children

AppLock is a utility with no content, no accounts and no data collection, and is
not directed at children specifically.

## Changes

If this policy changes, the updated version will be published at this address
and the date above will change.

## Contact

Questions about this policy, or about AppLock's privacy behaviour generally, can
be raised as an issue at <https://github.com/mimbasher/app-lock/issues>.
