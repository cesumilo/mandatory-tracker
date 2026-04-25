# Mandatory Valorant Tracker

A cross-platform home screen widget application for Android and iOS that displays upcoming Valorant esports matches for team **Mandatory** (ID: 7967).

## Features

### Widget
- **Small widget**: Next match with opponent logo, name, time until, event
- **Medium widget**: Next 2-3 matches
- Tap to open companion app
- Resilient offline display (shows cached data when offline)

### Companion App (Android & iOS)
- **Home screen** with upcoming matches and latest results
- **Pull-to-refresh** to sync latest data
- **Shimmer loading** animations
- **Dark theme** (`#0D0D0D`) with Mandatory branding

### Notifications
- **Android**: AlarmManager schedules exact alarms 1 hour before each match
- **iOS**: UNCalendarNotificationTrigger for 1-hour-before alerts
- Permission request on first launch

### Background Sync
- **Android**: WorkManager with 30-minute periodic sync
- **iOS**: BGTaskScheduler with BGAppRefreshTask
- Cache-first design ensures widgets render within 100ms

## Tech Stack

| Layer | Android | iOS |
|-------|---------|-----|
| Widget UI | Jetpack Glance | WidgetKit (SwiftUI) |
| Home Screen UI | Jetpack Compose | SwiftUI |
| Background Sync | WorkManager | BGTaskScheduler |
| Notifications | AlarmManager + BroadcastReceiver | UNUserNotificationCenter |
| Networking | HttpURLConnection | URLSession |
| Data Layer | Kotlin Multiplatform (KMP) shared module | |

## Project Structure

```
.
├── androidApp/                    # Android app + widget
│   ├── app/src/main/kotlin/
│   │   ├── MainActivity.kt       # Home screen UI (Compose)
│   │   ├── ValorantTrackerApp.kt # Application class
│   │   └── widget/
│   │       ├── MatchWidget.kt    # Glance widget
│   │       ├── MatchSyncWorker.kt    # WorkManager sync
│   │       ├── MatchNotificationWorker.kt  # 1h notification
│   │       └── MatchInfo.kt      # Widget data provider
│   └── widget/                   # Widget module
│
├── iosApp/                       # iOS app + widget
│   ├── App/Sources/
│   │   ├── ValorantTrackerApp.swift  # App entry point
│   │   ├── ContentView.swift     # Home screen UI
│   │   ├── NotificationScheduler.swift  # iOS notifications
│   │   └── BackgroundTaskManager.swift  # Background refresh
│   └── WidgetExtension/Sources/
│       └── MatchWidget.swift     # WidgetKit widget
│
├── shared/                       # KMP shared module
│   └── commonMain/kotlin/
│       └── com/valoranttracker/shared/
│           ├── domain/model/     # Domain models
│           ├── data/api/         # API client
│           └── data/cache/        # Caching
│
├── .github/workflows/
│   ├── build.yml       # CI: build on push/PR
│   ├── pre-commit.yml  # CI: lint & format checks
│   └── release.yml     # CD: build APK/IPA on release
│
└── gradle/
    └── libs.versions.toml   # Version catalog
```

## Building

### Prerequisites
- **Android**: JDK 17+, Android SDK
- **iOS**: Xcode 15+, XcodeGen

### Android
```bash
./gradlew :androidApp:app:assembleDebug
```
APK: `androidApp/app/build/outputs/apk/debug/app-debug.apk`

### iOS
```bash
cd iosApp
xcodegen generate
xcodebuild -project iosApp.xcodeproj -scheme ValorantTracker -configuration Debug -destination 'platform=iOS Simulator,name=iPhone 16' build
```

### Running Pre-commit Locally
```bash
# Install dependencies
pip install pre-commit

# Install git hook
pre-commit install

# Run checks manually
pre-commit run --all-files
```

## CI/CD

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `build.yml` | Push/PR to master | Verify compilation |
| `pre-commit.yml` | PR to master | Lint & format checks (ktlint, swiftlint, swiftformat) |
| `release.yml` | Release published | Build APK & IPA, attach to GitHub release |

## Theme

| Element | Color |
|---------|-------|
| Background | `#0D0D0D` |
| Accent (Mandatory Red) | `#FF2C2C` |
| Win Green | `#4CAF50` |
| Loss Red | `#E53935` |
| Card Background | `#1A1A1A` |

## API

Uses **[vlr.orlandomm.net](https://vlr.orlandomm.net)** — an unofficial vlr.gg scraper API.

- Base URL: `https://vlr.orlandomm.net`
- Endpoints:
  - `/api/v1/matches` — upcoming matches
  - `/api/v1/results?page=N` — past results (paginated)
- No authentication required
- Respectful rate limiting via cache-first design

## Contributing

1. Fork the repository
2. Create a feature branch
3. Run `pre-commit run --all-files` before pushing
4. Submit a pull request

## License

This is an unofficial third-party app. Not affiliated with Mandatory or Riot Games.

Match data powered by **[vlresports](https://github.com/Orloxx23/vlresports)** (Apache 2.0).
