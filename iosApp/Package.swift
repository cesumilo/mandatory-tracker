// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "ValorantTracker",
    platforms: [
        .iOS(.v17)
    ],
    products: [
        .library(
            name: "ValorantTracker",
            targets: ["ValorantTracker"]
        ),
        .library(
            name: "ValorantWidgetExtension",
            targets: ["ValorantWidgetExtension"]
        ),
    ],
    dependencies: [
        .package(url: "https://github.com/scinfu/SwiftSoup.git", from: "2.6.1")
    ],
    targets: [
        .target(
            name: "ValorantTracker",
            dependencies: [
                "SwiftSoup"
            ],
            path: "App/Sources",
            exclude: []
        ),
        .target(
            name: "ValorantWidgetExtension",
            dependencies: [
                "SwiftSoup"
            ],
            path: "WidgetExtension/Sources",
            exclude: []
        ),
    ]
)
