import SwiftUI
import WidgetKit

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
}

struct MatchProvider: TimelineProvider {
    private let teamId = "7967"
    private let apiURL = "https://vlr.orlandomm.net/api/v1/matches"

    func placeholder(in _: Context) -> MatchEntry {
        MatchEntry.placeholder
    }

    func getSnapshot(in _: Context, completion: @escaping (MatchEntry) -> Void) {
        completion(MatchEntry.placeholder)
    }

    func getTimeline(in _: Context, completion: @escaping (Timeline<MatchEntry>) -> Void) {
        Task {
            let entry = await fetchMatchData()
            let refreshDate = Calendar.current.date(byAdding: .minute, value: 30, to: Date()) ?? Date()
            let timeline = Timeline(entries: [entry], policy: .after(refreshDate))
            completion(timeline)
        }
    }

    private func fetchMatchData() async -> MatchEntry {
        guard let url = URL(string: apiURL) else {
            return MatchEntry.placeholder
        }

        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let matches = json["data"] as? [[String: Any]]
            else {
                return MatchEntry.placeholder
            }

            for match in matches {
                guard let status = match["status"] as? String,
                      status == "Upcoming",
                      let teams = match["teams"] as? [[String: Any]] else { continue }

                if teams.contains(where: { ($0["id"] as? String) == teamId }) {
                    let opponent = teams.first { ($0["id"] as? String) != teamId }
                    let opponentName = opponent?["name"] as? String ?? "TBD"
                    let event = match["event"] as? String ?? ""
                    let tournament = match["tournament"] as? String ?? ""
                    let timeUntil = match["in"] as? String ?? ""

                    return MatchEntry(
                        date: Date(),
                        opponentName: opponentName,
                        eventName: event,
                        tournament: tournament,
                        timeUntil: timeUntil,
                        isPlaceholder: false
                    )
                }
            }
        } catch {
            // Return placeholder on error
        }

        return MatchEntry.placeholder
    }
}

struct MatchWidgetEntryView: View {
    var entry: MatchEntry

    private let mandatoryRed = Color(red: 1.0, green: 0.17, blue: 0.17)
    private let darkBackground = Color(red: 0.05, green: 0.05, blue: 0.05)

    var body: some View {
        Link(destination: URL(string: "valoranttracker://match/\(entry.opponentName)")!) {
            VStack(alignment: .leading, spacing: 4) {
                Text("MANDATORY")
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundColor(mandatoryRed)

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

                    if !entry.eventName.isEmpty {
                        Text(entry.eventName)
                            .font(.caption2)
                            .foregroundColor(.white.opacity(0.7))
                            .lineLimit(1)
                    }

                    if !entry.tournament.isEmpty {
                        Text(entry.tournament)
                            .font(.caption2)
                            .foregroundColor(.white.opacity(0.5))
                            .lineLimit(1)
                    }

                    Spacer()

                    Text("in \(entry.timeUntil)")
                        .font(.caption)
                        .fontWeight(.medium)
                        .foregroundColor(mandatoryRed)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .padding()
        }
        .containerBackground(for: .widget) {
            darkBackground
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
        .configurationDisplayName("Mandatory Match")
        .description("Shows upcoming Valorant matches for Mandatory")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}
