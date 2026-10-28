# Releasing and publishing

Maintainer notes. None of this is needed to build, run or patch AppLock — see
[CONTRIBUTING.md](../CONTRIBUTING.md) for that.

## Signing

All four secrets below are configured on this repository, so CI has signed every
release since **v1.8**. They are recorded here for rotating the key, and for
anyone running a fork.


| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | the base64 above |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | `applock` |
| `KEY_PASSWORD` | key password |

Keep `applock.jks` safe and out of the repo; losing it means never being able
to update an installed copy again.


## Publishing to Play

The [`play` flavour](../README.md#two-flavours) is the build that may be uploaded; the rest is Console
work. None of this matters for sideloading.

1. **Developer account.** $25 once, plus identity verification. A *personal*
   account created after 13 November 2023 has to run a closed test with **12
   testers opted in continuously for 14 days** before production access
   unlocks — and if the count drops below 12, the 14 days start again.
   Organisation accounts skip this but need a D-U-N-S number.
2. **Upload a bundle, not an APK.** `./gradlew bundlePlayRelease`, signed, with
   Play App Signing; CI builds exactly this when the keystore secrets exist.
   Every upload needs a `versionCode` higher than the last one Play has seen.
3. **Declarations.** The accessibility service needs a permissions declaration
   *and* a prominent in-app disclosure of what it does; `QUERY_ALL_PACKAGES`
   needs its own declaration; `FOREGROUND_SERVICE_SPECIAL_USE` needs a
   justification. App-locking is an accepted use of all three, but accessibility
   is where app lockers actually get rejected, so be explicit rather than
   minimal.
4. **Privacy policy and Data safety.**
   [privacy-policy.md](privacy-policy.md) is the policy; Play wants it
   at a public URL, which GitHub Pages will serve from `docs/`. The Data safety
   form has to agree with it: nothing collected, nothing shared, no network
   access in this flavour.
5. **Listing.** 512×512 icon, 1024×500 feature graphic, at least two phone
   screenshots, short and full descriptions, content rating questionnaire. The
   gate sets `FLAG_SECURE`, so screenshotting it needs a temporary build with
   that flag removed — the same trick as the screenshots at the top of the
   README.
6. **`targetSdk`.** 36, as required of new apps since 31 August 2026 and already
   set here. Android 16 also stops apps on screens 600dp and wider locking
   orientation or opting out of edge-to-edge, which is worth checking on a
   tablet before upload.

One decision to make before the first upload: both flavours use the application
ID `com.turboo.applock`, so a phone holding the sideloaded build cannot install
the Play build over it, or the reverse — different signing keys. Giving `play`
its own ID avoids that, at the price of them being two separate apps.
