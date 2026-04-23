# Project Memory

> Bootstrapped on 2026-04-23 from PLAN.md.
> This file is the agent's long-term memory. See AGENTS.md § Long-Term Memory Management.

## 📌 Current State Summary

- Project phase: **Phase 5 — Observability & Polish** (complete)
- Implementation status: All phases complete
- APK output: `androidApp/app/build/outputs/apk/debug/app-debug.apk` (~11MB)
- iOS project: `iosApp/iosApp.xcodeproj` (generated)

## 🏛️ Architectural Decisions

- Use Kotlin Multiplatform (KMP) for shared logic layer (commonMain)
- Android widget: Jetpack Glance
- iOS widget: WidgetKit (SwiftUI)
- vlr.orlandomm.net API for match data
- Periodic WorkManager for sync (30min intervals)
- Debug screen added for diagnostics

## 📜 Task Log

- **2026-04-23** — Phase 0: Scaffolding — ✅ Done
- **2026-04-23** — Phase 1: Shared Domain & Networking — ✅ Done
- **2026-04-23** — Phase 2: Caching & Sync — ✅ Done
- **2026-04-23** — Phase 3: Android Widget — ✅ Done
- **2026-04-23** — Phase 4: iOS Widget — ✅ Done
- **2026-04-23** — Phase 5: Observability & Polish — ✅ Done

## ❓ Open Questions & Follow-ups

- Tap target: companion app or vlr.gg match URL?
- Future: Multi-team support (team following configurable)
- Future: Match notifications (requires full app background stack)