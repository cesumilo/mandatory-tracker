import WidgetKit
import SwiftUI

struct MatchEntry: TimelineEntry {
    let date: Date
    let teamName: String
    let opponentName: String
    let eventName: String
    let timeUntil: String
}

struct MatchProvider: TimelineProvider {
    func placeholder(in context: Context) -> MatchEntry {
        MatchEntry(
            date: Date(),
            teamName: "Sentinels",
            opponentName: "Loading...",
            eventName: "Loading...",
            timeUntil: "--"
        )
    }

    func getSnapshot(in context: Context, completion: @escaping (MatchEntry) -> Void) {
        let entry = MatchEntry(
            date: Date(),
            teamName: "Sentinels",
            opponentName: "Loading...",
            eventName: "Loading...",
            timeUntil: "--"
        )
        completion(entry)
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<MatchEntry>) -> Void) {
        let entry = MatchEntry(
            date: Date(),
            teamName: "Sentinels",
            opponentName: "Loading...",
            eventName: "Loading...",
            timeUntil: "--"
        )
        let timeline = Timeline(entries: [entry], policy: .atEnd)
        completion(timeline)
    }
}

struct MatchWidgetEntryView: View {
    var entry: MatchProvider.Entry

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Valorant Match")
                .font(.caption)
                .fontWeight(.bold)
                .foregroundColor(.white)

            Spacer()

            Text("Loading...")
                .font(.caption2)
                .foregroundColor(.white.opacity(0.7))
        }
        .padding()
        .containerBackground(for: .widget) {
            Color(red: 0.1, green: 0.1, blue: 0.18)
        }
    }
}

@main
struct ValorantWidgetBundle: WidgetBundle {
    var body: some Widget {
        MatchWidget()
    }
}

struct MatchWidget: Widget {
    let kind: String = "MatchWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: MatchProvider()) { entry in
            MatchWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("Valorant Match")
        .description("Shows upcoming Valorant matches")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

#Preview(as: .systemSmall) {
    MatchWidget()
} timeline: {
    MatchEntry(
        date: Date(),
        teamName: "Sentinels",
        opponentName: "Cloud9",
        eventName: "VCT Masters",
        timeUntil: "2d 4h"
    )
}