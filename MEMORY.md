# Project Memory

> Bootstrapped on 2026-04-23 from PLAN.md.
> This file is the agent's long-term memory. See AGENTS.md § Long-Term Memory Management.

## 📌 Current State Summary

- Project phase: **Phase 1 — Shared Domain & Networking** (complete)
- Implementation status: Domain model, DTOs, VlrApi client working
- APK output: `androidApp/app/build/outputs/apk/debug/app-debug.apk` (~11MB)

## 🏛️ Architectural Decisions

- Use Kotlin Multiplatform (KMP) for shared logic layer (commonMain)
- Android widget: Jetpack Glance
- iOS widget: WidgetKit (SwiftUI)
- vlr.orlandomm.net API for match data
- Ktor 2.x for HTTP client (downgraded from 3.x for Kotlin 1.9.x compatibility)
- kotlinx-serialization 1.6.3 (compatible with Kotlin 1.9.x)

## 🔍 Discoveries & Learnings

- **API responses**: ETag present, no Last-Modified header
- **Match.time**: Unix timestamp (epoch seconds) + relative string ("1d 17h")
- **Team ID 7967**: Mandatory (French team) - confirmed in API response
- `timestamp` is Unix epoch, `in` is relative time label
- `status` in API: "LIVE", "Upcoming", or empty/completed

## 📜 Task Log

- **2026-04-23** — Phase 0 complete — ✅ Done
- **2026-04-23** — Phase 1.1-1.3: Domain model + DTOs — ✅ Done — `shared/src/commonMain/kotlin/.../domain/model/`, `data/dto/`
- **2026-04-23** — Phase 1.4-1.7: VlrApi + HTTP client — ✅ Done — `shared/src/commonMain/kotlin/.../data/api/`, `data/`
- **2026-04-23** — API probe: ETag present, timestamp=Unix epoch — ✅ Done

## ❓ Open Questions & Follow-ups

- Tap target: companion app or vlr.gg match URL?