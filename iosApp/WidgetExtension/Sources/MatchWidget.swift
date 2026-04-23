import WidgetKit
import SwiftUI

struct MatchEntry: TimelineEntry {
    let date: Date
    let opponentName: String
    let eventName: String
    let tournament: String
    let timeUntil: String
    let isPlaceholder: Bool

    static let placeholder = MatchEntry(
        date: Date(),
        opponentName: "Loading...",
        eventName: "Loading...",
        tournament: "",
        timeUntil: "--",
        isPlaceholder: true
    )

    static let sample = MatchEntry(
        date: Date(),
        opponentName: "Mandatory",
        eventName: "Challengers France",
        tournament: "Group Stage–Week 4",
        timeUntil: "1d 17h",
        isPlaceholder: false
    )
}

struct MatchProvider: TimelineProvider {
    private let teamId = "7967"

    func placeholder(in context: Context) -> MatchEntry {
        MatchEntry.placeholder
    }

    func getSnapshot(in context: Context, completion: @escaping (MatchEntry) -> Void) {
        let entry = MatchEntry.placeholder
        completion(entry)
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<MatchEntry>) -> Void) {
        let entry = loadMatchData()
        
        let refreshDate = Calendar.current.date(byAdding: .minute, value: 30, to: Date()) ?? Date()
        let timeline = Timeline(entries: [entry], policy: .after(refreshDate))
        completion(timeline)
    }

    private func loadMatchData() -> MatchEntry {
        let defaults = UserDefaults(suiteName: "group.com.valoranttracker.app")
        
        guard let opponent = defaults?.string(forKey: "nextOpponent"),
              let event = defaults?.string(forKey: "nextEvent"),
              let tournament = defaults?.string(forKey: "nextTournament"),
              let timeUntil = defaults?.string(forKey: "nextTimeUntil") else {
            return MatchEntry.placeholder
        }

        return MatchEntry(
            date: Date(),
            opponentName: opponent,
            eventName: event,
            tournament: tournament,
            timeUntil: timeUntil,
            isPlaceholder: false
        )
    }
}

struct MatchWidgetEntryView: View {
    var entry: MatchEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Valorant Match")
                .font(.caption)
                .fontWeight(.bold)
                .foregroundColor(.white)

            Spacer()

            if entry.isPlaceholder {
                Text("Loading...")
                    .font(.caption2)
                    .foregroundColor(.white.opacity(0.7))
            } else {
                Text("vs \(entry.opponentName)")
                    .font(.headline)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .lineLimit(1)

                Text(entry.eventName)
                    .font(.caption2)
                    .foregroundColor(.white.opacity(0.7))
                    .lineLimit(1)

                if !entry.tournament.isEmpty {
                    Text(entry.tournament)
                        .font(.caption2)
                        .foregroundColor(.white.opacity(0.5))
                        .lineLimit(1)
                }

                Spacer()

                Text(entry.timeUntil)
                    .font(.caption)
                    .fontWeight(.medium)
                    .foregroundColor(Color(red: 0.81, green: 0.17, blue: 0.17))
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
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
        .description("Shows upcoming Valorant matches for Mandatory")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

#Preview(as: .systemSmall) {
    MatchWidget()
} timeline: {
    MatchEntry.sample
}

#Preview(as: .systemMedium) {
    MatchWidget()
} timeline: {
    MatchEntry.sample
}