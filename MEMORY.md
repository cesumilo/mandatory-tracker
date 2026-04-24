# Project Memory

> Bootstrapped on 2026-04-23 from PLAN.md.
> This file is the agent's long-term memory. See AGENTS.md § Long-Term Memory Management.

## 📌 Current State Summary

- Project phase: **Phase 6 — Polish & UX** (complete)
- Implementation status: Android app fully functional with widget, notifications, and companion UI
- APK output: `androidApp/app/build/outputs/apk/debug/app-debug.apk` (~11MB)
- iOS project: `iosApp/iosApp.xcodeproj` (generated)

## 🏛️ Architectural Decisions

- Use Kotlin Multiplatform (KMP) for shared logic layer (commonMain)
- Android widget: Jetpack Glance
- iOS widget: WidgetKit (SwiftUI)
- vlr.orlandomm.net API for match data
- Periodic WorkManager for sync (30min intervals)
- AlarmManager + BroadcastReceiver for exact notification timing (1h before match)

## 📜 Task Log

- **2026-04-23** — Phase 0: Scaffolding — ✅ Done
- **2026-04-23** — Phase 1: Shared Domain & Networking — ✅ Done
- **2026-04-23** — Phase 2: Caching & Sync — ✅ Done
- **2026-04-23** — Phase 3: Android Widget — ✅ Done
- **2026-04-23** — Phase 4: iOS Widget — ✅ Done
- **2026-04-23** — Phase 5: Observability & Polish — ✅ Done
- **2026-04-24** — Phase 6: Polish & UX — ✅ Done
  - Fixed notification scheduling (only 1h before match)
  - Added POST_NOTIFICATIONS permission request
  - Added visual feedback on widget refresh
  - Added test notification button (shows immediate toast)
  - Widget now clickable (opens app)
  - Removed unused widget picker button

## 🔍 Discoveries & Learnings

- Android 13+ requires POST_NOTIFICATIONS runtime permission for notifications
- SCHEDULE_EXACT_ALARM and USE_EXACT_ALARM permissions needed for precise notification timing on Android 12+
- AlarmManager.setExactAndAllowWhileIdle for exact alarm scheduling
- AppWidgetManager.ACTION_APPWIDGET_PICK opens widget picker

## ❓ Open Questions & Follow-ups

- Future: Multi-team support (team following configurable)
- Widget tap opens companion app (resolved)