# PLAN: Caching Strategy for Upcoming Matches

## 🎯 Goal

Minimize requests to the `vlresports` API (an unofficial vlr.gg scraper) to avoid rate-limiting or bans, while keeping the widget responsive and useful offline. Target: **≤ 1 request per 30 minutes** under normal conditions, with adaptive cadence and graceful degradation.

---

## 📐 Guiding Principles

1. **The widget never hits the network directly.** It reads from a local cache. A separate, throttled sync job is the *only* thing that talks to vlr.gg.
2. **Stale data is better than no data.** A 6-hour-old match list is still useful; a failed fetch should never blank the widget.
3. **One fetch serves all widget instances.** Whether the user has 1 or 5 widgets, there should be exactly one network call per refresh window.
4. **Respect the upstream.** Adaptive cadence, exponential backoff on failure, conditional requests when possible.

---

## 🏗️ Architecture: Three-Layer Cache

```
┌─────────────────────────────────────────────────────┐
│  Widget UI (Glance / WidgetKit)                     │
│  → reads synchronously from Layer 1                 │
└─────────────────────────────────────────────────────┘
              ▲
              │ read-only
┌─────────────────────────────────────────────────────┐
│  Layer 1: Persistent Disk Cache (source of truth)   │
│  - JSON file (kotlinx.serialization)                │
│  - Holds last known good UpcomingMatch list         │
│  - Holds fetchedAt, ETag, failure counter           │
└─────────────────────────────────────────────────────┘
              ▲
              │ written by
┌─────────────────────────────────────────────────────┐
│  Layer 2: Sync Worker (throttled, platform-native)  │
│  - Android: WorkManager periodic (30 min)           │
│  - iOS: WidgetKit timeline + BGAppRefreshTask       │
└─────────────────────────────────────────────────────┘
              ▲
              │ calls
┌─────────────────────────────────────────────────────┐
│  Layer 3: HTTP Client (Ktor + HttpCache plugin)     │
│  - Conditional requests via If-None-Match           │
│  - Respectful User-Agent                            │
└─────────────────────────────────────────────────────┘
```

---

## 📏 Tiered Freshness Policy

| State | Age of cache | Behavior |
|---|---|---|
| **Fresh** | `< 30 min` | Skip network. Use cache. |
| **Stale but usable** | `30 min – 6 h` | Render from cache; trigger background refresh. |
| **Expired** | `> 6 h` | Render from cache with "⚠ outdated" indicator; force refresh. |
| **Match imminent** | Next match `< 2 h` away | Tighten refresh to **15 min**. |
| **No matches scheduled** | Cache says empty list | Loosen refresh to **6 h**. |

Adaptive cadence is the single biggest lever for avoiding bans.

---

## 🗄️ Cache Data Model

Store the **domain model**, not the raw API response, so upstream schema drift doesn't corrupt the cache.

```kotlin
@Serializable
data class CachedMatches(
    val teamId: String,
    val matches: List<UpcomingMatch>,
    val fetchedAt: Instant,
    val etag: String? = null,
    val lastModified: String? = null,
    val consecutiveFailures: Int = 0,
)
```

**Storage decision (v1)**: single JSON file `upcoming_matches_7967.json` in shared app storage, accessed via `expect/actual` for the file path.

_Alternatives considered: SQLDelight (overkill for one row), multiplatform-settings (awkward for lists). Record final choice as an ADR in `MEMORY.md`._

---

## 🛡️ Ban-Avoidance Safeguards

Baked into the shared `SyncService` in `commonMain`:

1. **Minimum interval guard**: refuse network if `now - fetchedAt < 15 min`, regardless of trigger.
2. **Exponential backoff**: `30 min → 1 h → 2 h → 4 h`, capped at 6 h. Reset on success.
3. **User-Agent**: identifiable and respectful, e.g. `"ValorantWidget/1.0 (+github.com/yourhandle/repo)"`.
4. **Jitter**: add `±2 min` random jitter to scheduled syncs to avoid synchronized thundering herd.
5. **Conditional requests**: send `If-None-Match` / `If-Modified-Since` when available.
6. **Single-flight**: use a `Mutex` in `SyncService` so concurrent triggers coalesce.
7. **Circuit breaker**: after **5 consecutive failures**, pause syncs for **1 hour**.

---

## 🧠 Request Budget (back-of-envelope)

Per user, per day:

- **~48 requests** under normal operation (every 30 min)
- **~96 requests** on match days (15-min tier near match time)
- **~4 requests** in pure-failure mode (backoff active)

Well within "polite scraper" territory.

---

## 🔧 Shared Module Shape (commonMain)

```kotlin
class UpcomingMatchesRepository(
    private val api: VlrApi,
    private val cache: MatchesCache,
    private val clock: Clock,
) {
    private val syncMutex = Mutex()

    suspend fun getCached(): CachedMatches = cache.read()

    suspend fun syncIfNeeded(teamId: String = "7967"): SyncResult = syncMutex.withLock {
        val current = cache.read()
        if (!shouldRefresh(current)) return SyncResult.Skipped(current)

        return try {
            val all = api.getUpcomingMatches(etag = current.etag)
            val filtered = all.filter { it.involves(teamId) }.toDomain()
            val updated = current.copy(
                matches = filtered,
                fetchedAt = clock.now(),
                consecutiveFailures = 0,
            )
            cache.write(updated)
            SyncResult.Refreshed(updated)
        } catch (e: Exception) {
            val failed = current.copy(consecutiveFailures = current.consecutiveFailures + 1)
            cache.write(failed)
            SyncResult.Failed(failed, e)
        }
    }

    private fun shouldRefresh(c: CachedMatches): Boolean { /* tier logic */ }
}
```

- Widgets call `getCached()` (instant, no I/O risk).
- Workers call `syncIfNeeded()`.

---

## 🔁 Platform Scheduling

### Android — WorkManager

```kotlin
val request = PeriodicWorkRequestBuilder<MatchSyncWorker>(
    repeatInterval = 30, TimeUnit.MINUTES
)
    .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
    .build()

WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    "match-sync-7967",
    ExistingPeriodicWorkPolicy.KEEP,
    request
)
```

Glance's `provideGlance` block reads the cache file. **Never fetch from inside `provideGlance`.**

### iOS — WidgetKit + BGAppRefreshTask

```swift
func getTimeline(in context: Context, completion: @escaping (Timeline<Entry>) -> ()) {
    let cached = CacheReader.read()
    let nextRefresh = RefreshPolicy.nextDate(for: cached)

    if cached.isStale {
        SyncTask.scheduleBackground()
    }

    let entry = MatchesEntry(date: Date(), matches: cached.matches)
    completion(Timeline(entries: [entry], policy: .after(nextRefresh)))
}
```

Register a `BGAppRefreshTask` in the main app for background syncs beyond WidgetKit's control.

---

## ✅ Task Breakdown

### Phase 1 — Shared Foundation

- [ ] **1.1** Add Ktor client, `HttpCache` plugin, and `kotlinx.serialization` to `shared` module.
- [ ] **1.2** Define `UpcomingMatch` domain model + DTO-to-domain mappers with defensive parsing (`ignoreUnknownKeys = true`, nullable fields).
- [ ] **1.3** Define `CachedMatches` serializable model.
- [ ] **1.4** Implement `MatchesCache` interface with `expect/actual` file-path resolution (Android: `context.filesDir`, iOS: `NSFileManager` documents dir).
- [ ] **1.5** Implement `VlrApi` client with respectful User-Agent and optional `If-None-Match` header support.

### Phase 2 — Sync Logic

- [ ] **2.1** Implement `UpcomingMatchesRepository` with `Mutex`-guarded single-flight `syncIfNeeded()`.
- [ ] **2.2** Implement tiered `shouldRefresh()` policy (fresh / stale / expired / imminent / empty).
- [ ] **2.3** Implement exponential backoff and circuit breaker based on `consecutiveFailures`.
- [ ] **2.4** Add jitter to scheduled refresh times.
- [ ] **2.5** Unit-test the refresh policy with a fake `Clock`.

### Phase 3 — Android Integration

- [ ] **3.1** Create `MatchSyncWorker` (CoroutineWorker) that calls `repository.syncIfNeeded()`.
- [ ] **3.2** Enqueue unique periodic work on app launch and on widget install.
- [ ] **3.3** Wire Glance widget's `provideGlance` to read `repository.getCached()` only.
- [ ] **3.4** Handle empty / stale / expired states in Glance UI.

### Phase 4 — iOS Integration

- [ ] **4.1** Implement `TimelineProvider` reading from shared cache.
- [ ] **4.2** Register `BGAppRefreshTask` in the main app for background syncs.
- [ ] **4.3** Compute next timeline refresh date from freshness tier.
- [ ] **4.4** Handle empty / stale / expired states in SwiftUI widget views.

### Phase 5 — Observability & Debug

- [ ] **5.1** Add a debug screen in the companion app showing: `fetchedAt`, `consecutiveFailures`, next scheduled sync, current freshness tier.
- [ ] **5.2** Add a "Force refresh" button in the debug screen (respects the 15-min minimum interval guard).
- [ ] **5.3** Log every network call with outcome to aid early tuning.

---

## ❓ Open Questions

- Does the `vlresports` API emit `ETag` or `Last-Modified` headers? **Probe before implementing conditional requests.**
- Is `Match.time` parseable to a real `Instant`, or is it always a relative string like `"2d 4h from now"`? Affects the "match imminent" tier logic.
- Should we persist a small history of sync outcomes (for the debug screen) or only the latest state?

---

## 🧭 Success Criteria

- Widget always renders *something* (cached or empty-state) within 100 ms of host query.
- Under normal use, no more than ~50 requests/day/user to the API.
- No crashes on schema drift — unknown fields are ignored, missing fields fall back gracefully.
- Network failures degrade quietly: widget shows last good data with a stale indicator.
