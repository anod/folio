# Android stock widget plan

This is the original planning and visual reference. The native implementation now exists; see the repository README for build instructions, implementation choices, and publication limitations.

## Confirmed scope

- Standalone native Android app; direct market-provider access, no expenses server, Microsoft sign-in, or VPN dependency.
- Stock watchlist: prices, daily changes, and small charts. No holdings, profit/loss, trading, or ESOP workbook access.
- The app includes an interactive widget-configuration preview and an easy-to-review screen showing the last refreshed values, refresh date/time, and data freshness.
- Original planning scope, retained as the implementation reference.

## What the expenses project provides

Reviewed the separate `expenses-app` repository during planning:

- `apps\api\src\esop\marketData.ts:1`: `EsopMarketQuote` contains `symbol`, `price`, nullable `currency`, and `fetchedAt`.
- `apps\api\src\esop\marketData.ts:19`: `fetchYahooQuote` calls `https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?range=1d&interval=1d`. It trims/encodes symbols, uses a 10-second timeout, and reports HTTP, parsing, and timeout errors.
- `apps\api\src\esop\routes.ts:37`: `GET /api/esop/market?stockSymbol=MSFT&fxSymbol=USDILS=X` fetches a stock and FX pair. API Bearer authentication depends on server configuration; this read-only route does not require the workbook's Graph token.
- `apps\api\src\esop\marketData.test.ts`: existing cases cover normalization, previous-close fallback, missing prices, HTTP/network failures, and timeout.

Reuse the provider knowledge and error-handling pattern, not the TypeScript service or workbook-writing endpoints. The existing price fallback must not become an unlabeled live quote. Its `fetchedAt` is the request completion time, not the exchange's quote time.

A read-only live probe on 2026-11-26 succeeded for `MSFT` and `USDILS=X` using `range=1d&interval=5m`. Responses included price, currency, previous-close metadata, market timestamp, exchange timezone, and timestamped close arrays. This establishes current endpoint availability, not freshness guarantees or a supported API contract.

**Provider risk:** Yahoo's chart endpoint is unofficial. It may throttle, change, or stop working; availability does not establish usage rights. Review applicable terms before implementation/distribution. Keep one provider interface so a documented, appropriately licensed source can replace it. Do not promise real-time prices or silently substitute another provider.

## Visual direction: quiet financial instrument

- Material 3 configuration app; widget-first design with large tabular prices, restrained typography, generous spacing, and rounded cards.
- Default: dark graphite surface, high-contrast text, muted emerald/coral accents. Offer light and wallpaper-derived themes, plus a user-controlled background opacity.
- Always show signed daily change and percentage, not color alone. Label quote age and stale/closed states.
- Responsive layouts based on available dimensions; launcher grid sizes are approximate:
  - Compact, approximately 2x2: one symbol, dominant price, change, sparkline, timestamp.
  - Wide, approximately 4x2: one featured symbol with a larger chart and session context.
  - Tall, approximately 4x4: watchlist rows with price, daily percentage, and small charts.
- Tap a symbol for its in-app detail screen; separate refresh and configuration actions.
- Different widget instances can select different symbols, order, and appearance.
- Include empty, loading, offline/stale, unavailable-symbol, and rate-limited designs. Preserve cached content during refresh; never replace it with a spinner-only widget.

Glance has its own supported composables, not arbitrary Compose UI. Draw sparklines into small, size-bounded bitmaps; avoid animation, interactive widget charts, custom live views, and expensive visual effects.

### Interactive visual proposal

The standalone browser prototype is saved at `design/index.html` beside this plan. **Folio** is a proposed working name, not a confirmed product name.

- Graphite and sage widgets; warm-paper light app and matching dark app, with restrained green/coral market accents.
- Three widget layouts and seven app screens: watchlist, widget studio, stock detail, add/verify stock, values and refresh review, preferences, and first launch.
- Explicit empty, offline/cached, and partial-refresh failure designs.
- Working concept controls for app themes, widget sizes/colors/opacity, sample symbol lookup, and simulated refresh timestamps.
- All prices, dates, session states, and charts in the prototype are illustrative. It makes no market-data requests, installs no widgets, and does not implement native Android behavior.

## In-app preview and refresh review

- Make **Widgets & data** the app's main review screen: widget preview first, then a readable list of the underlying stock values and refresh status. Reviewing data must not require pinning a widget or opening developer diagnostics.
- Preview compact/wide/tall layouts and light/dark themes. Symbol selection, ordering, appearance, and size changes update the preview immediately using cached data, without a network request on every adjustment.
- Keep the preview faithful to the production widget layouts and the same persisted quote snapshots; share presentation rules and formatting rather than maintaining an unrelated mock design. Clearly label sample data if no real snapshot exists yet.
- Each stock's review row shows symbol/name, last successful price and currency, previous close, signed daily change and percentage, quote date/time, and last successful refresh date/time. Show readable local dates with timezone and relative age, for example `26 Nov 2026, 09:00 IST · 2 min ago`.
- Distinguish **Quote time** (provider/exchange timestamp), **Last successful refresh** (last usable response persisted), and **Last attempt** (including a failed attempt). Refreshing an old market quote must not make its quote time appear new.
- Show per-symbol refresh results for partial failures, plus an overall summary such as `4 of 5 symbols refreshed`. Never imply all values share one successful timestamp when they do not.
- Provide **Refresh now** with visible progress, completion/error state, and preserved last-good values. Opening this screen shows persisted values immediately without clearing them or mislabeling the visit as a refresh.
- Preview and review work offline and after process death. This is a latest-snapshot review, not a historical refresh log.

## Native architecture

Start with one app module rather than a multi-module framework:

- Kotlin; Jetpack Compose + Material 3 for watchlist, detail, and widget settings.
- Jetpack Glance for responsive home-screen widgets.
- WorkManager for background refresh and user-triggered refresh jobs.
- OkHttp + Kotlin serialization for provider access.
- Room for watchlist entries and last-good quote/chart snapshots; DataStore for global and per-widget preferences.

Data flow:

`Yahoo adapter -> repository -> local snapshot -> Glance widget / Compose screens`

Define `MarketDataProvider` with quote-and-intraday-history retrieval. A snapshot needs provider symbol, display name, currency, exchange timezone, price, price source/session, previous close, absolute/percentage change, quote timestamp, fetch timestamp, chart samples, and availability/error state.

Persist each symbol's last refresh attempt time/outcome separately from its last-good snapshot and successful refresh time. A failed refresh updates attempt/error metadata, never the successful values or their timestamps.

Fetch one intraday response per distinct symbol and reuse it across widgets. Preserve timestamps alongside prices; handle null candles and gaps, invalid numbers, missing currencies, mismatched arrays, zero change denominators, and unsupported symbols explicitly. Compute day change against the correct previous-session close, not the first displayed candle. Never invent zero prices or present previous close as a fresh trade.

MVP symbol entry uses exact provider symbols with a quote-backed preview before saving. Defer autocomplete until a supported search API is selected. Display native trading currency; automatic FX conversion is out of MVP scope.

## Refresh, reliability, and privacy

- Default to a best-effort 15-minute refresh while relevant markets are open; make 30/60-minute settings available.
- WorkManager's minimum periodic interval is 15 minutes, not a delivery guarantee. Doze, connectivity, launcher, and OEM restrictions can delay updates.
- Set widget `updatePeriodMillis` to zero; use one unique scheduler and deduplicate work across instances. Do network work outside widget rendering.
- Use provider session/calendar metadata where available to reduce closed-market fetches. Do not derive all market hours from the phone timezone or weekday alone; handle holidays, overnight sessions, and DST conservatively.
- Manual refresh uses the same repository with a short cooldown and concurrent-request coalescing.
- Bound concurrency and timeouts; back off on 429/5xx/network errors and respect `Retry-After`. A failing symbol must not erase other successful symbols.
- Preserve last-good snapshots, show quote time, and distinguish refresh failures from an old-but-valid closed-session quote.
- No foreground service, exact alarms, permanent socket, or background rapid polling.
- No account or analytics required. Direct requests disclose requested symbols and IP to the provider. Any future API credential must be user-supplied or protected server-side, never embedded as an APK secret.

## Delivery sequence

1. **Provider and design spike:** confirm terms and desired exchange/symbol coverage; capture sanitized response fixtures; implement provider parsing; produce compact/wide/tall previews covering fresh and stale states.
2. **Useful vertical slice:** add/remove/reorder symbols, cached snapshots, one compact widget with in-app preview, manual refresh, and the values/refresh review screen.
3. **Polished widget set:** responsive wide/tall layouts with matching in-app previews, per-instance configuration, sparklines, themes, accessibility, and pin-widget onboarding.
4. **Reliability pass:** market-aware refresh policy, lifecycle/reboot behavior, partial failures, rate limits, offline recovery, and launcher/device verification.

Use the official Android CLI during implementation. Prefer a connected Android device for widget behavior; check available RAM/CPU before local builds and offload heavy validation when needed.

## Acceptance criteria

- A stock can be added, previewed, pinned, and viewed without any expenses backend or Microsoft authentication.
- Users can review widget appearance and last refreshed values directly in the app before pinning; preview configuration changes do not trigger unnecessary fetches.
- Preview and widget use the same snapshot and formatting. Sample data is explicitly labeled, and persisted real values remain visible offline and after relaunch.
- A failed/partial refresh shows the affected symbols and attempt times without changing last-success timestamps or discarding cached values. Quote time and successful fetch time remain visibly distinct.
- Layouts remain readable across resizing, large font settings, light/dark wallpapers, and multiple widget instances.
- Test day-change math, quote-vs-fetch timestamps, previous-close fallback labeling, currency formatting, null/gapped charts, invalid symbols, 429 responses, and partial success.
- Verify US and Israeli symbols if required coverage is confirmed; include different timezones, DST, weekends, and a holiday scenario.
- Offline mode retains meaningful cached content and clearly reports stale/error status.
- Removing the final widget stops widget-only refresh work; reboot, app update, process death, and force-stop/relaunch behavior are checked on-device.
- Refreshes are deduplicated and bounded. No claim of guaranteed 15-minute or real-time background updates.

## References

- [Jetpack Glance](https://developer.android.com/develop/ui/compose/glance)
- [Widget update limitations and WorkManager guidance](https://developer.android.com/develop/ui/views/appwidgets/advanced)
- [PeriodicWorkRequest timing guarantees and limitations](https://developer.android.com/reference/androidx/work/PeriodicWorkRequest)
