# Project Memory

> Bootstrapped on 2026-04-23 from PLAN.md.
> This file is the agent's long-term memory. See AGENTS.md § Long-Term Memory Management.

## 📌 Current State Summary

- Project phase: **Fully functional** - Android and iOS apps with full home screen UI
- Implementation status: Android/iOS apps show upcoming matches, past results, shimmer loading, pull-to-refresh
- **New**: Replaced vlresports API with web scraping for better data freshness using Jsoup (Android) and SwiftSoup (iOS)
- **Feature**: 1-hour caching policy implemented with separate timestamps for upcoming/completed matches on both platforms
- **iOS Widget**: 15-minute cache for widget extension with optimized next-match parsing using SwiftSoup
- APK output: `androidApp/app/build/outputs/apk/debug/app-debug.apk` (~11MB)
- iOS project: `iosApp/iosApp.xcodeproj`
- Both platforms: Full home screen with pull-to-refresh, shimmer loading cards
- Android: MatchNotificationWorker schedules alarms 1h before match
- iOS: NotificationScheduler with BGTaskScheduler for background refresh
- Background color: Dark (`#0D0D0D`) consistent across safe/unsafe areas
- Widget: Time-until now correctly computed from `timestamp` (not stale API string)
- Android widget: Now properly displays actual match data instead of hardcoded values

## 🏛️ Architectural Decisions

- Use Kotlin Multiplatform (KMP) for shared logic layer (commonMain)
- Android widget: Jetpack Glance
- iOS widget: WidgetKit (SwiftUI)
- vlr.orlandomm.net API for match data
- Periodic WorkManager for sync (30min intervals)
- AlarmManager + BroadcastReceiver for exact notification timing (1h before match)
- iOS deployment target: 17.0 (for containerBackground)
- iOS: BGTaskScheduler for background refresh + UNCalendarNotificationTrigger for 1h-before notifications
- Background color: `#0D0D0D` (DarkBackground) used in both theme and MainActivity
- Pre-commit: `pre-commit` framework with swiftlint, swiftformat, and ktlint hooks
  - ktlint v12.1.1 with disabled rules: `no-wildcard-imports`, `function-naming`

## 📜 Task Log

- **2026-04-26** — Widget time-off bug fix — ✅ Done
  - Widget was showing stale `timeUntil` from API string (static at scrape time)
  - Both Android (MatchSyncWorker) and iOS (MatchWidget) now compute time remaining from `timestamp` using current time
  - Matches behavior of `getNextMatchInfo()` in MainActivity

- **2026-04-27** — Android widget display fix — ✅ Done
  - Fixed Android Glance widget to read actual match data from SharedPreferences instead of hardcoded values
  - Updated widget implementation in androidApp/widget module to match working app widget
  - Widget now properly displays upcoming match information, event details, and time-until

- **2026-04-27** — Web scraping implementation — ✅ Done
  - Created VlrScraper.kt with Jsoup implementation for proper HTML parsing
  - Parses match items using CSS selectors: `.m-item`, `.m-item-result.mod-tbd`, `.m-item-result.mod-win/.mod-loss`
  - Extracts date/time from `m-item-date > div` HTML structure with REGEX parsing
  - Implements 1-hour caching with separate timestamps for upcoming/completed matches
  - Fixed upcoming match time display: now shows "in 6d 0h at 9:00 pm" instead of "0:00"
  - Fixed completed match date calculation with proper Calendar logic
  - JobLife match on 2026-04-26 correctly shows "1d ago" instead of "Today"
  - Fixed pull-to-refresh to bypass cache with `forceRefresh = true`
  - Integrated VlrScraper with widget components:
    - MatchSyncWorker.kt uses VlrScraper.getUpcomingMatches(forceRefresh = true)
    - MatchInfo.kt uses VlrScraper for getNextMatchInfo()
    - MatchNotificationWorker.kt uses VlrScraper for scheduling notifications
  - MainActivity uses VlrScraper instead of API calls for all data fetching
  - Updated widget to not double-add "in" prefix (already included in scraped data)

- **2026-04-27** — iOS web scraping implementation — ✅ Done
  - Added SwiftSoup dependency for proper HTML parsing (Swift equivalent of Jsoup)
  - Created VlrScraper.swift with SwiftSoup HTML parsing (cleaner than regex)
  - Implements 1-hour caching with separate caches for upcoming/completed matches
  - Parses match items using CSS selectors: `.m-item`, `.m-item-result.mod-tbd`, `.m-item-result.mod-win/.mod-loss`
  - Extracts date/time from `m-item-date` element using SwiftSoup text parsing
  - Updated ContentView.swift to use VlrScraper instead of manual HTML parsing
  - Updated pull-to-refresh to use `forceRefresh = true` parameter
  - Created separate VlrScraper.swift for widget extension with 15-minute cache (also uses SwiftSoup)
  - Updated MatchWidget to use web scraping instead of vlresports API
  - Updated NotificationScheduler.swift to use VlrScraper for fetching match data
  - Fixed widget to not double-add "in" prefix (already included in scraped timeUntil)
  - Both platforms now use consistent web scraping approach with proper HTML parsing libraries

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

- **2026-04-25** — iOS Home Screen & Notification Implementation — ✅ Done
  - Created ContentView.swift matching Android MainActivity UI (upcoming matches, results, shimmer)
  - Created NotificationScheduler.swift (mirrors MatchNotificationWorker logic)
  - Created BackgroundTaskManager.swift (mirrors WorkManager periodic sync)
  - iOS notifications scheduled 1h before match using UNCalendarNotificationTrigger
  - Background refresh using BGTaskScheduler
  - Added BGTaskSchedulerPermittedIdentifiers and UIBackgroundModes to Info.plist
  - Pull-to-refresh implemented via .refreshable modifier

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
- iOS shimmer: LinearGradient with AnimatableOffset, .linear(duration: 1.2).repeatForever
- iOS pull-to-refresh: .refreshable modifier (built-in SwiftUI)
- iOS: UNCalendarNotificationTrigger for scheduled notifications (date-based)
- iOS: BGTaskScheduler.register + BGAppRefreshTask for background sync

- Pre-commit hooks: Uses pre-commit framework (.pre-commit-config.yaml)
  - Hooks: trailing-whitespace, end-of-file-fixer, swiftlint, swiftformat
  - Run manually: `venv/bin/pre-commit run --all-files`
  - Auto-installs git hook on `git commit`

- Widget `timeUntil` uses API's `in` field directly — this is a static string set at scrape time, not time-remaining
  - Must compute from `timestamp` using current time for accurate countdown

- Android widget has two implementations: app widget (working) and Glance widget (broken with hardcoded data)
  - Fixed by updating Glance widget to read from SharedPreferences like the app widget
  - Widget module must not reference app-specific components like MainActivity

- Web scraping vlr.gg provides more up-to-date data than vlresports API wrapper
  - Implemented direct HTML parsing with Jsoup for Android (SwiftSoup for iOS)
  - Added 1-hour caching policy with separate timestamps for upcoming/completed matches
  - Both Android and iOS now use web scraping instead of vlresports API
  - HTML structure: https://www.vlr.gg/team/7967/mandatory
    - Upcoming matches use `.m-item-result.mod-tbd` with `span.rm-item-score-eta` for time until
    - Completed matches use `.m-item-result.mod-win` or `.mod-loss` with score spans
    - Date/time in `div.m-item-date > div` (date) and container text after div (time)
  - Match dates are in 2026 (future), requiring special handling in "ago" calculations
  - Time parsing issues: had to extract both date and time separately with REGEX due to whitespace/newline formatting
  - REGEX used: `<div>\s*(\d{4}/\d{2}/\d{2})\s*</div>` for date extraction
  - Widget stores match data in SharedPreferences under "widget_prefs" for persistence
  - iOS implementation uses SwiftSoup for HTML parsing (Swift equivalent of Android's Jsoup)
  - Both platforms now use proper HTML parsing libraries (Jsoup/SwiftSoup) instead of regex
  - Widget extension has separate VlrScraper implementation (can't share frameworks with main app)
  - Both main app and widget extension use SwiftSoup for clean, maintainable HTML parsing
  - iOS notifications now use VlrScraper (with SwiftSoup) for match data instead of vlresports API

## ❓ Open Questions & Follow-ups

- Future: Multi-team support (team following configurable)
- Widget tap opens companion app (resolved)
