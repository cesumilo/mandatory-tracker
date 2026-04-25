# PLAN

## 🧭 North Star

Ship a **home-screen widget** for **Android** and **iOS** that displays upcoming Valorant esports matches for **team ID `7967`**, backed by a Kotlin Multiplatform shared module that handles networking, caching, and domain logic. The widget must be glanceable, resilient offline, and respectful of the unofficial `vlresports` API (≤ ~50 requests/user/day).

---

## 🏗️ High-Level Architecture

```
┌──────────────────────────┐   ┌──────────────────────────┐
│  Android App + Glance    │   │  iOS App + WidgetKit     │
│  Widget                  │   │  Extension               │
└────────────┬─────────────┘   └────────────┬─────────────┘
             │                              │
             └──────────────┬───────────────┘
                            ▼
             ┌──────────────────────────────┐
             │  shared (KMP, commonMain)    │
             │  - Domain model              │
             │  - VlrApi (Ktor)             │
             │  - MatchesCache              │
             │  - UpcomingMatchesRepository │
             │  - Refresh policy            │
             └──────────────┬───────────────┘
                            ▼
                   vlr.orlandomm.net
                   (vlresports API)
```

- **Shared module** is the single source of truth for data, caching, and sync logic.
- **Widgets read cache only.** They never hit the network directly.
- **Platform-native workers** (WorkManager / BGAppRefreshTask) are the only components that trigger syncs.

---

## 📅 Phased Roadmap

### Phase 0 — Project Scaffolding
Establish the repository, tooling, and build setup.

- [ ] **0.1** Initialize KMP project with `shared`, `androidApp`, and `iosApp` targets.
- [ ] **0.2** Configure Gradle version catalog (`libs.versions.toml`) with Kotlin, Ktor, kotlinx-serialization, kotlinx-datetime, kotlinx-coroutines.
- [ ] **0.3** Set up CI (GitHub Actions): build shared, build Android, build iOS framework.
- [ ] **0.4** Create `PRODUCT.md`, `PLAN.md`, `MEMORY.md` at repo root.
- [ ] **0.5** Add `.editorconfig`, ktlint/detekt, SwiftLint.
- [ ] **0.6** Decide package naming and bundle IDs. Record in `MEMORY.md`.

**Exit criteria**: empty app launches on both platforms; shared module compiles and is consumed by both.

---

### Phase 1 — Shared Domain & Networking
Build the data layer in `commonMain`.

- [ ] **1.1** Define domain model:
  ```kotlin
  data class TeamRef(val id: String, val name: String, val logoUrl: String?)
  data class UpcomingMatch(
      val id: String,
      val team1: TeamRef,
      val team2: TeamRef,
      val eventName: String,
      val scheduledAt: Instant?,
      val rawTimeLabel: String,
  )
  ```
- [ ] **1.2** Define DTOs mirroring the vlresports OpenAPI schema, with `ignoreUnknownKeys = true` and nullable fields for defensive parsing.
- [ ] **1.3** Write DTO → domain mappers with unit tests covering malformed/missing fields.
- [ ] **1.4** Implement `VlrApi` using Ktor client with a respectful User-Agent (e.g. `"ValorantWidget/1.0 (+github.com/…)"`).
- [ ] **1.5** Add `expect class HttpClientProvider` with `actual` per platform (OkHttp on Android, Darwin on iOS).
- [ ] **1.6** Probe the API for `ETag` / `Last-Modified` headers; record findings in `MEMORY.md`.
- [ ] **1.7** Probe the `Match.time` field; decide parsing strategy (absolute vs. relative). Record in `MEMORY.md`.

**Exit criteria**: `VlrApi.getUpcomingMatches()` returns a typed `List<UpcomingMatch>` filtered for team `7967` in integration test.

---

### Phase 2 — Caching & Sync
Implement the caching strategy defined in the caching sub-plan `PLAN_CACHING.md`.

- [ ] **2.1** Define `CachedMatches` serializable model (teamId, matches, fetchedAt, etag, lastModified, consecutiveFailures).
- [ ] **2.2** Implement `MatchesCache` interface with `expect/actual` file-path resolution.
  - Android: `context.filesDir`
  - iOS: `NSFileManager` documents directory
- [ ] **2.3** Implement `UpcomingMatchesRepository` with `Mutex`-guarded single-flight `syncIfNeeded()`.
- [ ] **2.4** Implement tiered refresh policy:
  - Fresh: `< 30 min` → skip
  - Stale: `30 min – 6 h` → serve + background refresh
  - Expired: `> 6 h` → serve + force refresh, show stale indicator
  - Match imminent: next match `< 2 h` → tighten to 15 min
  - No matches: loosen to 6 h
- [ ] **2.5** Implement exponential backoff (`30 min → 1 h → 2 h → 4 h`, capped at 6 h).
- [ ] **2.6** Implement circuit breaker (5 failures → 1 h pause).
- [ ] **2.7** Add ±2 min jitter to scheduled refreshes.
- [ ] **2.8** Unit-test the policy with a fake `Clock`.

**Exit criteria**: repository correctly skips/fetches/backs-off under simulated time and failure scenarios.

---

### Phase 3 — Android Widget
Integrate the shared layer with a Glance widget.

- [ ] **3.1** Create `MatchSyncWorker` (CoroutineWorker) that invokes `repository.syncIfNeeded()`.
- [ ] **3.2** Enqueue unique periodic work (30 min) on app launch and on widget install.
- [ ] **3.3** Implement Glance widget reading from `repository.getCached()` inside `provideGlance`. **Never** call `syncIfNeeded` from the widget.
- [ ] **3.4** Design small layout: opponent logo, opponent name, time-until, event.
- [ ] **3.5** Design medium layout: next 2–3 matches.
- [ ] **3.6** Handle empty, stale, expired, and error states.
- [ ] **3.7** Tap action opens companion app (or vlr.gg match page — record decision in `MEMORY.md`).
- [ ] **3.8** Test on API 26+ across screen densities.

**Exit criteria**: widget shows next match within 100 ms of host query; updates every 30 min without draining battery.

---

### Phase 4 — iOS Widget
Integrate the shared layer with WidgetKit.

- [ ] **4.1** Export shared module as XCFramework via `embedAndSignAppleFrameworkForXcode` or SPM.
- [ ] **4.2** Implement `TimelineProvider` reading from `repository.getCached()`.
- [ ] **4.3** Compute `Timeline.policy = .after(nextRefreshDate)` based on freshness tier.
- [ ] **4.4** Register `BGAppRefreshTask` in the main app; call `repository.syncIfNeeded()`.
- [ ] **4.5** Design SwiftUI views for small and medium families, matching Android information density.
- [ ] **4.6** Handle empty, stale, expired, and error states.
- [ ] **4.7** Tap action opens companion app via deep link.
- [ ] **4.8** Test on iOS 16+ across device sizes.

**Exit criteria**: widget renders on lock screen and home screen; background refresh observed via Console logs.

---

### Phase 5 — Observability & Polish
Make the system debuggable and shippable.

- [ ] **5.1** Add a debug screen in both companion apps showing:
  - `fetchedAt`, age, current tier
  - `consecutiveFailures`, circuit-breaker state
  - Next scheduled sync
  - Last error (if any)
- [ ] **5.2** Add a "Force refresh" button (respects 15-min minimum guard).
- [ ] **5.3** Log every network call with outcome; gate behind a debug flag.
- [ ] **5.4** Add crash reporting (optional: Sentry or platform-native).
- [ ] **5.5** Write README with setup, architecture diagram, and attribution to `vlresports` / vlr.gg.
- [ ] **5.6** Prepare store listings (Play Store, App Store) if distributing.

**Exit criteria**: on-device debug screen exposes all sync state; README complete.

---

## 🧪 Testing Strategy

- **commonTest**: domain mappers, refresh policy (with fake `Clock`), repository state transitions, single-flight behavior.
- **androidTest**: `MatchSyncWorker` integration with `WorkManagerTestInitHelper`; Glance preview snapshots.
- **iosTest**: `TimelineProvider` under mocked cache; XCTest for background task registration.
- **manual**: airplane-mode test (cache still renders), long-stale test (warps clock), ban-simulation (mock 429 responses).

---

## 🔒 Risks & Mitigations

| Risk | Mitigation |
|---|---|
| `vlresports` API goes down or changes schema | Defensive parsing, cache-first rendering, stale indicator, circuit breaker. |
| Rate-limiting / ban | Tiered refresh, jitter, single-flight, conditional requests, respectful UA, exponential backoff. |
| Widget refresh throttled by OS | Accept it; cache-first design ensures UX remains acceptable even with infrequent syncs. |
| `Match.time` unparseable | Keep `rawTimeLabel` as fallback display; parse best-effort for `scheduledAt`. |
| iOS background tasks unreliable | Also refresh on timeline next-date and on companion-app foreground events. |

---

## ❓ Open Questions

- Does `vlresports` emit `ETag` / `Last-Modified`? (Phase 1 probe.)
- Is `Match.time` absolute or relative? (Phase 1 probe.)
- Tap target: companion app or vlr.gg match URL?
- Will team following stay hardcoded to `7967`, or become configurable later?
- Persistence choice: JSON file (current plan) vs. SQLDelight — revisit if we add multi-team support.
- Distribution: private sideload or public stores?

---

## 🧭 Success Criteria (project-level)

- Widget always renders *something* within 100 ms of host query.
- ≤ ~50 requests/user/day to the API under normal use.
- No crashes on schema drift.
- Network failures degrade quietly with a stale indicator.
- Shared module is the *only* place domain logic lives — platform code is thin glue.

---

## 📎 Sub-Plans

- **Caching Strategy** — see dedicated plan document `PLAN_CACHING.md`; Phase 2 tasks mirror it.
- _(Future)_ Multi-team support
- _(Future)_ Match notifications (would require full app background stack — out of scope for v1)
