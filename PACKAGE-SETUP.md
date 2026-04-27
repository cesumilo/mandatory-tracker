# SwiftSoup Setup Instructions

To add SwiftSoup to the iOS project:

## Option A: Xcode GUI (Recommended)

1. Open `iosApp/iosApp.xcodeproj` in Xcode
2. Select the project in the Project Navigator
3. Select the "ValorantTracker" target
4. Go to "Package Dependencies" tab
5. Click "+" button
6. Enter package URL: `https://github.com/scinfu/SwiftSoup.git`
7. Select version rule: "Up to Next Major Version" → `2.6.1`
8. Click "Add Package"

## Option B: Command Line (If Xcode not available)

Create a `Package.swift` in the iosApp directory:

```swift
// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "ValorantTracker",
    platforms: [.iOS(.v17)],
    dependencies: [
        .package(url: "https://github.com/scinfu/SwiftSoup.git", from: "2.6.1"),
    ],
    targets: [
        .target(
            name: "ValorantTracker",
            dependencies: ["SwiftSoup"],
            path: "App/Sources"
        ),
        .target(
            name: "ValorantWidgetExtension",
            dependencies: ["SwiftSoup"],
            path: "WidgetExtension/Sources"
        ),
    ]
)
```

Then in Xcode: File → Add Packages → Add Local...

## After Adding SwiftSoup

1. Update `VlrScraper.swift` to use SwiftSoup (see example implementation)
2. Add SwiftSoup to widget extension target if needed
3. Remove regex parsing code