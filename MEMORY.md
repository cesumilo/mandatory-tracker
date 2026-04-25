# Project Memory

> Bootstrapped on 2026-04-23 from PLAN.md.
> This file is the agent's long-term memory. See AGENTS.md § Long-Term Memory Management.

## 📌 Current State Summary

- Project phase: **Fully functional** - Android and iOS apps with home screen
- Implementation status: Android app shows upcoming matches and past results with shimmer loading UI
- APK output: `androidApp/app/build/outputs/apk/debug/app-debug.apk` (~11MB)
- iOS project: `iosApp/iosApp.xcodeproj`
- Home screen: Shows upcoming matches + latest results with pull-to-refresh (no refresh button), shimmer cards during loading

## 🏛️ Architectural Decisions

- Use Kotlin Multiplatform (KMP) for shared logic layer (commonMain)
- Android widget: Jetpack Glance
- iOS widget: WidgetKit (SwiftUI)
- vlr.orlandomm.net API for match data
- Periodic WorkManager for sync (30min intervals)
- AlarmManager + BroadcastReceiver for exact notification timing (1h before match)
- iOS deployment target: 17.0 (for containerBackground)

## 📜 Task Log

- **2026-04-23** — Phase 0: Scaffolding — ✅ Done
- **2026-04-23** — Phase 1: Shared Domain & Networking — ✅ Done
- **2026-04-23** — Phase 2: Caching & Sync — ✅ Done
- **2026-04-23** — Phase 3: Android Widget — ✅ Done
- **2026-04-23** — Phase 4: iOS Widget — ✅ Done
- **2026-04-23** — Phase 5: Observability & Polish — ✅ Done
- **2026-04-24** — Phase 6: Polish & UX — ✅ Done
  - iOS: Fixed black screen (added launch screen background color)
  - iOS: Added Xcode scheme files
  - iOS: Fixed red screen only (added GeometryReader, spacer fixes)
  - iOS: Fixed notification not showing (added @UIApplicationDelegateAdaptor)
  - Fixed notification scheduling (only 1h before match)
  - Added POST_NOTIFICATIONS permission request
  - Added visual feedback on widget refresh
  - Added test notification button (shows immediate toast)
  - Widget now clickable (opens app)
  - Removed unused widget picker button
  - iOS: Added visual feedback on refresh button
  - iOS: Added widget tap handler (opens app via deep link)
  - iOS: Added URL scheme "valoranttracker://"
  - iOS: Now using MandatoryLogo from assets (cleaner implementation)

- **2026-04-25** — Home Screen: Upcoming + Results — ✅ Done
  - Added home screen showing upcoming matches and latest results
  - Added match date/time display (converted from timestamp)
  - Fixed score ordering (opponent - our team)
  - Results endpoint is paginated (pages 2-5 have Mandatory matches)
  - Added @Serializable to app data classes for JSON parsing

- **2026-04-25** — Home Screen UX Improvements — ✅ Done
  - Added shimmer loading cards (3 for upcoming + 3 for results sections)
  - Removed debug text "Upcoming: X, Past: Y"
  - Replaced refresh button with pull-to-refresh (PullToRefreshBox)
  - Updated Compose BOM from 2024.02.00 to 2024.09.00 (for PullToRefreshBox support)

## 🔍 Discoveries & Learnings

- vlr.orlandomm.net /results endpoint is paginated - page 1 has no matches, pages 2+ have data
- Match.score displayed as "opponentScore - ourScore" to match visual left-right layout
- Must use @kotlinx.serialization.Serializable annotation for data classes in app module
- iOS: Must use @UIApplicationDelegateAdaptor for UNUserNotificationCenterDelegate to work
- iOS: AuthorizationStatus 2 = denied, 1 = authorized, 0 = notDetermined
- iOS: setDelegate() in init() may be too late - use @UIApplicationDelegateAdaptor
- Android 13+ requires POST_NOTIFICATIONS runtime permission for notifications
- SCHEDULE_EXACT_ALARM and USE_EXACT_ALARM permissions needed for precise notification timing on Android 12+
- AlarmManager.setExactAndAllowWhileIdle for exact alarm scheduling
- AppWidgetManager.ACTION_APPWIDGET_PICK opens widget picker
- PullToRefreshBox requires Compose BOM 2024.06.00+ (2024.09.00 used)
- Shimmer effect implemented with Brush.linearGradient + infiniteRepeatable animation

## ❓ Open Questions & Follow-ups

- Future: Multi-team support (team following configurable)
- Widget tap opens companion app (resolved)