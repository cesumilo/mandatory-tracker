# Valorant Match Tracker

A home screen widget for Android and iOS that displays upcoming Valorant esports matches for your favorite team.

## Team ID: 7967 (Sentinels)

Displays upcoming matches for Sentinels - when they play next, who against, and in which tournament.

## Platform Support

| Platform | Widget Type | Min Version |
|---------|----------|----------|
| Android | Jetpack Glance | API 26+ |
| iOS | WidgetKit | iOS 16+ |

## Features

- Small widget: Next match (opponent, time until)
- Medium widget: Next 2-3 matches
- Offline-first: Shows cached data when offline
- Stale indicator when data is outdated

## Data Source

API: [vlresports](https://github.com/Orloxx23/vlresports) (Apache 2.0)
- Base URL: https://vlr.orlandomm.net
- Scrapes vlr.gg for match data
- No authentication required

## Architecture

- **Shared (KMP)**: Domain models, repository, API client, caching
- **Android**: Jetpack Glance widget
- **iOS**: WidgetKit SwiftUI widget

## Building

```bash
# Android
./gradlew :androidApp:app:assembleDebug

# iOS (requires Xcode)
cd iosApp && xcodegen generate && xcodebuild ...
```

## Attribution

- Match data: [vlr.gg](https://vlr.gg)
- API: [vlresports](https://github.com/Orloxx23/vlresports)
- Valorant content belongs to Riot Games