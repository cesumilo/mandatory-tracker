# Project Memory

> Bootstrapped on 2026-04-23 from PLAN.md.
> This file is the agent's long-term memory. See AGENTS.md § Long-Term Memory Management.

## 📌 Current State Summary

- Project phase: **Phase 2 — Caching & Sync** (complete)
- Implementation status: Repository with tiered refresh working
- APK output: `androidApp/app/build/outputs/apk/debug/app-debug.apk` (~11MB)

## 🏛️ Architectural Decisions

- Use Kotlin Multiplatform (KMP) for shared logic layer (commonMain)
- Android widget: Jetpack Glance
- iOS widget: WidgetKit (SwiftUI)
- vlr.orlandomm.net API for match data
- Ktor 2.x for HTTP client (compatible with Kotlin 1.9.x)
- In-memory cache (simple implementation for v1)

## 🔍 Discoveries & Learnings

- **API responses**: ETag present, no Last-Modified header
- **Match.time**: Unix timestamp (epoch seconds) + relative string ("1d 17h")
- **Team ID 7967**: Mandatory (French team) - confirmed in API response
- Tiered refresh: <30min skip, 30min-6h background, >6h force refresh

## 📜 Task Log

- **2026-04-23** — Phase 0 complete — ✅ Done
- **2026-04-23** — Phase 1: Shared Domain & Networking — ✅ Done
- **2026-04-23** — Phase 2: Caching & Sync — ✅ Done — `MatchesCache`, `UpcomingMatchesRepository`

## ❓ Open Questions & Follow-ups

- Tap target: companion app or vlr.gg match URL?
- Persist cache to file system in future phase