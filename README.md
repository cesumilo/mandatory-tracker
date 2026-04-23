# Mandatory Tracker

A cross-platform home screen widget application showing upcoming Valorant matches for team **Mandatory** (team ID: 7967).

## Features

### Android
- Glance-based home screen widget showing next match
- Mandatory-themed dark UI (red accent `#FF2C2C`)
- Automatic refresh every 30 minutes
- Push notification 1 hour before match start

### iOS
- WidgetKit widget showing next match
- SwiftUI-based UI matching Android theme
- Timeline updates every 30 minutes

### Shared
- Fetches match data from [vlr.orlandomm.net](https://vlr.orlandomm.net) API
- Filters for Mandatory's upcoming matches

## Project Structure

```
.
├── androidApp/              # Android app with Glance widget
│   └── app/src/main/
│       ├── kotlin/com/valoranttracker/app/
│       │   ├── MainActivity.kt
│       │   ├── ValorantTrackerApp.kt
│       │   └── widget/
│       │       ├── MatchWidget.kt
│       │       ├── MatchSyncWorker.kt
│       │       └── MatchNotificationWorker.kt
│       └── res/
│           ├── drawable/
│           │   └── mandatory_logo.xml
│           └── mipmap-*/
│
├── iosApp/                  # iOS app with WidgetKit
│   ├── App/Sources/
│   │   └── ValorantTrackerApp.swift
│   └── WidgetExtension/Sources/
│       └── MatchWidget.swift
│
├── shared/                  # KMP module (future)
│   └── commonMain/
│
└── gradle/
    └── libs.versions.toml   # Version catalog
```

## Tech Stack

| Component | Technology |
|-----------|------------|
| Android Widget | Jetpack Glance |
| iOS Widget | WidgetKit (SwiftUI) |
| Networking | URLSession (iOS), HttpURLConnection (Android) |
| Background Work | WorkManager (Android) |
| Notifications | UserNotifications (iOS), NotificationManager (Android) |

## Building

### Android
```bash
./gradlew :androidApp:app:assembleDebug
```

APK location: `androidApp/app/build/outputs/apk/debug/app-debug.apk`

### iOS
Open `iosApp/iosApp.xcodeproj` in Xcode and build.

## Theme

- **Background**: `#0D0D0D` (near black)
- **Accent**: `#FF2C2C` (Mandatory red)

## API

Uses unofficial vlr.gg scraper API:
- Base URL: `https://vlr.orlandomm.net`
- Endpoint: `/api/v1/matches`
- No authentication required

## Team

- **Team ID**: 7967
- **Team Name**: Mandatory
- **Region**: France

## Screenshots

### Android Widget
Dark background with red "MANDATORY" header, showing:
- Opponent name
- Event name
- Time until match

### iOS Widget
Same theme as Android, updates every 30 minutes via TimelineProvider

## License

This is an unofficial third-party app. Not affiliated with Mandatory or Riot Games.