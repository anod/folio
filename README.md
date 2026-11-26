# Folio

Native Android stock watchlist and home-screen widget. Quotes are fetched directly from the unofficial Yahoo chart endpoint; no account or backend is required. The provider receives requested symbols and the device IP address. Data may be delayed or unavailable, and use of the endpoint is subject to the provider's terms.

## Download a PR APK

Every pull request runs the **Android APK** workflow: it builds a debug APK, runs unit tests and lint, and uploads `folio-debug-<PR number>`. Open the PR's **Checks**, select **Build debug APK and check**, then follow **Download APK artifact** in the run summary (or select the artifact under **Artifacts**). Sign in to GitHub to download the ZIP, extract `app-debug.apk`, and install it on Android 8.0 or newer. Artifacts expire after 30 days; a rerun creates a new download.

These APKs are debug-signed test builds, not production releases. Only install builds from PR authors and changes you trust. CI does not run device tests or request live quotes. It also runs on pushes to `main` and can be started manually from **Actions**.

## Build

Install JDK 17 or newer and Android SDK platform 36. Set `ANDROID_HOME` or create `local.properties` with `sdk.dir`. On Windows:

```powershell
android build '.?app:assembleDebug' -- --max-workers=2 --no-daemon
android build '.?app:testDebugUnitTest' -- --max-workers=2 --no-daemon
```

The debug APK is `app\build\outputs\apk\debug\app-debug.apk`. Alternatively use `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --max-workers=2`.

With a device connected, `android build '.?app:connectedDebugAndroidTest' -- --max-workers=2 --no-daemon` runs the screen tests. Their fake provider and separate test preferences are confined to the instrumentation test; they do not populate the production watchlist. Tests cover light/dark screens, 150% font scaling, adding and reopening a saved stock, navigation feedback, and production Glance RemoteViews in bounded compact/wide/tall hosts. Labeled screenshots are written to the app's cache as `fixture-*.png` and can be removed after review. A bounded test host is not a substitute for launcher pin/resize testing.

Add an exact symbol in Folio, then use the widget picker or the Widgets screen to request a home-screen widget. Each widget can select and order stocks independently, with the first featured in smaller sizes; its settings action reopens configuration. Preview and widget use the same presentation model and persisted snapshots. Native Compose previews adapt the presentation; actual Glance widgets omit less important details at smaller launcher sizes.

**Stock price time** is the provider's price timestamp. **Last successful refresh** records receipt of a usable response, even if its stock price time is older. **Last refresh attempt** includes failures. The watchlist and widgets use compact `Stock price` and `Refreshed` labels with dates and timezone; Detail and Data spell out all three. Failed requests retain the previous snapshot and both successful timestamps. Unknown price timestamps remain unknown; previous-close fallback is explicitly labeled.

WorkManager updates widgets on a best-effort 15, 30 or 60 minute schedule while at least one widget exists; Android can delay it. Recent provider-supplied regular-session boundaries slow automatic checks outside the reported session to at most hourly. Unknown or old boundaries are never treated as a holiday calendar. Manual refresh bypasses that session heuristic but not backoff or provider rate limits. Transient failures use capped exponential backoff; a worker performs at most three immediate retry attempts and the normal periodic schedule remains best effort. HTTP 429 pauses requests across symbols until the provider's `Retry-After`, or for 15 minutes when absent. These limits persist across process restarts.

## Limitations

Scheduled work waits for connectivity rather than recording an offline attempt; manual refresh reports network errors and preserves cached data. The provider does not supply a complete exchange calendar, so no market-open or real-time guarantee is implied. Launcher sizing, Android battery restrictions and transparency over a wallpaper affect appearance and timing. The Yahoo endpoint is unofficial and needs a terms review before distribution.

## Design and publication readiness

The original visual study is `docs\design\index.html`. The native app keeps its graphite/sage/paper palette, rounded surfaces, price-first hierarchy, list sparklines and separate data-review cards. It uses accessible native Material controls rather than copying the prototype's tiny web fonts or touch targets. The widget layout follows [Android widget guidance](https://developer.android.com/design/ui/mobile/guides/widgets), including distinct responsive layouts and 48 dp refresh/settings targets.

This small single-module implementation persists atomic JSON snapshots and widget preferences in private SharedPreferences, rather than the plan's proposed Room/DataStore split. There is no account, analytics SDK, embedded API credential, or backup/device-transfer of watchlist data. Widget-only periodic, manual and retry jobs are cancelled when the last widget is removed.

Before public distribution, review the market provider's terms, choose the repository license, replace the scaffold launcher icon with approved branding, and configure release signing outside Git. Live-provider acceptance remains separate from deterministic fixture tests: a prior HTTP 429 prevented live quote verification, and no bypass or silent fallback is implemented. Dependency/target-SDK upgrade warnings should be reviewed before a store release. Debug APKs, signing keys, local SDK paths and environment files are ignored. Repository publication and an app-store release are separate steps.
