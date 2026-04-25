import SwiftUI
import WidgetKit

struct ContentView: View {
    @State private var isLoading = true
    @State private var isRefreshing = false
    @State private var upcomingMatches: [MatchData] = []
    @State private var pastMatches: [MatchData] = []
    @State private var notificationStatus: String?

    private let mandatoryRed = Color(red: 1.0, green: 0.17, blue: 0.17)
    private let darkBackground = Color(red: 0.05, green: 0.05, blue: 0.05)
    private let cardDark = Color(red: 0.1, green: 0.1, blue: 0.1)
    private let winGreen = Color(red: 0.3, green: 0.65, blue: 0.3)
    private let lossRed = Color(red: 0.9, green: 0.22, blue: 0.21)

    var body: some View {
        GeometryReader { _ in
            ZStack {
                darkBackground
                    .ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 16) {
                        HeaderSection(notificationStatus: notificationStatus)

                        if isLoading || isRefreshing {
                            SectionTitle("UPCOMING MATCHES")

                            ForEach(0..<3, id: \.self) { _ in
                                ShimmerUpcomingCard()
                            }

                            Spacer()
                                .frame(height: 8)
                            SectionTitle("LATEST RESULTS")

                            ForEach(0..<3, id: \.self) { _ in
                                ShimmerCompletedCard()
                            }
                        } else {
                            if !upcomingMatches.isEmpty {
                                SectionTitle("UPCOMING MATCHES (\(upcomingMatches.count))")

                                ForEach(upcomingMatches, id: \.id) { match in
                                    UpcomingMatchCard(match: match, teamId: "7967")
                                }
                            } else if upcomingMatches.isEmpty {
                                EmptyStateCard(message: "No upcoming matches found")
                            }

                            if !pastMatches.isEmpty {
                                Spacer()
                                    .frame(height: 8)
                                SectionTitle("LATEST RESULTS (\(pastMatches.count))")

                                ForEach(pastMatches, id: \.id) { match in
                                    CompletedMatchCard(match: match, teamId: "7967")
                                }
                            }
                        }

                        Spacer()
                            .frame(height: 16)
                    }
                    .padding(16)
                }
                .refreshable {
                    await refresh()
                }
            }
        }
        .onAppear {
            loadData()
        }
    }

    @MainActor
    private func loadData() {
        Task {
            await fetchUpcomingMatches()
            await fetchPastMatches()
            isLoading = false
            await loadNextMatchInfo()
        }
    }

    @MainActor
    private func refresh() async {
        isRefreshing = true
        await fetchUpcomingMatches()
        await fetchPastMatches()
        await loadNextMatchInfo()
        isRefreshing = false
    }

    private func fetchUpcomingMatches() async {
        guard let url = URL(string: "https://vlr.orlandomm.net/api/v1/matches") else { return }

        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let matches = json["data"] as? [[String: Any]] else { return }

            let teamId = "7967"
            let upcoming = matches.filter { match in
                guard let status = match["status"] as? String,
                      status == "Upcoming",
                      let teams = match["teams"] as? [[String: Any]],
                      teams.contains(where: { ($0["id"] as? String) == teamId }) else { return false }
                return true
            }.sorted { ($0["timestamp"] as? Int ?? 0) < ($1["timestamp"] as? Int ?? 0) }

            upcomingMatches = upcoming.compactMap { match in
                guard let id = match["id"] as? String,
                      let teams = match["teams"] as? [[String: Any]],
                      let event = match["event"] as? String,
                      let timestamp = match["timestamp"] as? Int else { return nil }

                let teamData = teams.compactMap { team -> TeamData? in
                    guard let id = team["id"] as? String,
                          let name = team["name"] as? String else { return nil }
                    return TeamData(
                        id: id,
                        name: name,
                        country: team["country"] as? String,
                        score: team["score"] as? String,
                        logo: team["logo"] as? String,
                        won: team["won"] as? Bool
                    )
                }

                return MatchData(
                    id: id,
                    teams: teamData,
                    status: match["status"] as? String ?? "",
                    event: event,
                    tournament: match["tournament"] as? String ?? "",
                    timestamp: Int(timestamp),
                    timeUntil: match["in"] as? String ?? "",
                    ago: nil
                )
            }
        } catch {
            print("Error fetching upcoming: \(error)")
        }
    }

    private func fetchPastMatches() async {
        var allMatches: [[String: Any]] = []

        for page in 1 ... 5 {
            guard let url = URL(string: "https://vlr.orlandomm.net/api/v1/results?page=\(page)") else { continue }

            do {
                let (data, _) = try await URLSession.shared.data(from: url)
                guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                      let matches = json["data"] as? [[String: Any]] else { break }

                if matches.isEmpty { break }
                allMatches.append(contentsOf: matches)
            } catch {
                break
            }
        }

        let teamId = "7967"
        let withTeam = allMatches.filter { match in
            guard let teams = match["teams"] as? [[String: Any]],
                  teams.contains(where: { ($0["id"] as? String) == teamId }) else { return false }
            return true
        }

        pastMatches = withTeam.prefix(10).compactMap { match in
            guard let id = match["id"] as? String,
                  let teams = match["teams"] as? [[String: Any]],
                  let event = match["event"] as? String,
                  let timestamp = match["timestamp"] as? Int else { return nil }

            let teamData = teams.compactMap { team -> TeamData? in
                guard let id = team["id"] as? String,
                      let name = team["name"] as? String else { return nil }
                return TeamData(
                    id: id,
                    name: name,
                    country: team["country"] as? String,
                    score: team["score"] as? String,
                    logo: team["logo"] as? String,
                    won: team["won"] as? Bool
                )
            }

            return MatchData(
                id: id,
                teams: teamData,
                status: match["status"] as? String ?? "",
                event: event,
                tournament: match["tournament"] as? String ?? "",
                timestamp: Int(timestamp),
                timeUntil: match["in"] as? String ?? "",
                ago: match["ago"] as? String
            )
        }
    }

    private func loadNextMatchInfo() async {
        guard let url = URL(string: "https://vlr.orlandomm.net/api/v1/matches") else {
            notificationStatus = nil
            return
        }

        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let matches = json["data"] as? [[String: Any]]
            else {
                notificationStatus = nil
                return
            }

            let teamId = "7967"
            for match in matches {
                guard let status = match["status"] as? String,
                      status == "Upcoming",
                      let teams = match["teams"] as? [[String: Any]],
                      teams.contains(where: { ($0["id"] as? String) == teamId }) else { continue }

                let opponent = teams.first { ($0["id"] as? String) != teamId }
                let opponentName = opponent?["name"] as? String ?? "TBD"
                let timeUntil = match["in"] as? String ?? ""

                notificationStatus = "Next: vs \(opponentName) in \(timeUntil)"
                return
            }
            notificationStatus = "No upcoming match"
        } catch {
            notificationStatus = nil
        }
    }
}

struct HeaderSection: View {
    let notificationStatus: String?

    private let mandatoryRed = Color(red: 1.0, green: 0.17, blue: 0.17)

    var body: some View {
        VStack(spacing: 8) {
            Image("MandatoryLogo")
                .resizable()
                .scaledToFit()
                .frame(width: 80, height: 80)

            Text("MANDATORY")
                .font(.system(size: 28, weight: .bold))
                .foregroundColor(mandatoryRed)

            Text("Valorant Tracker")
                .font(.system(size: 14))
                .foregroundColor(.white.opacity(0.7))

            if let status = notificationStatus {
                Spacer()
                    .frame(height: 8)
                Text(status)
                    .font(.system(size: 12))
                    .foregroundColor(mandatoryRed.opacity(0.8))
            }
        }
        .frame(maxWidth: .infinity)
    }
}

struct SectionTitle: View {
    let title: String

    var body: some View {
        Text(title)
            .font(.system(size: 14, weight: .bold))
            .foregroundColor(.white.opacity(0.8))
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.vertical, 8)
    }
}

struct UpcomingMatchCard: View {
    let match: MatchData
    let teamId: String

    private let mandatoryRed = Color(red: 1.0, green: 0.17, blue: 0.17)
    private let cardDark = Color(red: 0.1, green: 0.1, blue: 0.1)

    var body: some View {
        HStack {
            let team1 = match.teams.first
            let team2 = match.teams.count > 1 ? match.teams[1] : nil
            let opponent = team1?.id == teamId ? team2 : team1
            let ourTeam = team1?.id == teamId ? team1 : team2

            HStack(spacing: 12) {
                VStack(alignment: .center) {
                    AsyncImage(url: URL(string: opponent?.logo ?? "")) { image in
                        image.resizable().scaledToFit()
                    } placeholder: {
                        Circle().fill(Color.gray.opacity(0.3))
                    }
                    .frame(width: 40, height: 40)

                    Text(opponent?.name ?? "")
                        .font(.system(size: 10))
                        .foregroundColor(.white.opacity(0.8))
                        .lineLimit(1)
                        .frame(width: 60)
                        .multilineTextAlignment(.center)
                }

                VStack(alignment: .center, spacing: 2) {
                    Text("vs")
                        .font(.system(size: 12))
                        .foregroundColor(mandatoryRed)

                    Text(match.event)
                        .font(.system(size: 11))
                        .foregroundColor(.white.opacity(0.6))
                        .lineLimit(1)

                    Text(match.tournament)
                        .font(.system(size: 10))
                        .foregroundColor(.white.opacity(0.5))
                        .lineLimit(1)
                }

                VStack(alignment: .center) {
                    AsyncImage(url: URL(string: ourTeam?.logo ?? "")) { image in
                        image.resizable().scaledToFit()
                    } placeholder: {
                        Circle().fill(Color.gray.opacity(0.3))
                    }
                    .frame(width: 40, height: 40)

                    Text(ourTeam?.name ?? "")
                        .font(.system(size: 10))
                        .foregroundColor(.white.opacity(0.8))
                        .lineLimit(1)
                        .frame(width: 60)
                        .multilineTextAlignment(.center)
                }
            }
            .frame(maxWidth: .infinity)

            VStack(alignment: .trailing, spacing: 2) {
                Text(formatDate(timestamp: match.timestamp))
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(mandatoryRed)

                Text(match.timeUntil)
                    .font(.system(size: 9))
                    .foregroundColor(.white.opacity(0.5))
            }
        }
        .padding(16)
        .background(cardDark)
        .cornerRadius(12)
    }

    private func formatDate(timestamp: Int) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(timestamp))
        let formatter = DateFormatter()
        formatter.dateFormat = "d/M @ H:mm"
        return formatter.string(from: date)
    }
}

struct CompletedMatchCard: View {
    let match: MatchData
    let teamId: String

    private let mandatoryRed = Color(red: 1.0, green: 0.17, blue: 0.17)
    private let cardDark = Color(red: 0.1, green: 0.1, blue: 0.1)
    private let winGreen = Color(red: 0.3, green: 0.65, blue: 0.3)
    private let lossRed = Color(red: 0.9, green: 0.22, blue: 0.21)

    var body: some View {
        HStack {
            let team1 = match.teams.first
            let team2 = match.teams.count > 1 ? match.teams[1] : nil
            let opponent = team1?.id == teamId ? team2 : team1
            let ourTeam = team1?.id == teamId ? team1 : team2
            let isWin = ourTeam?.won == true

            let ourScore = ourTeam?.score ?? "0"
            let oppScore = opponent?.score ?? "0"
            let scoreText = "\(oppScore) - \(ourScore)"

            HStack(spacing: 12) {
                VStack(alignment: .center) {
                    AsyncImage(url: URL(string: opponent?.logo ?? "")) { image in
                        image.resizable().scaledToFit()
                    } placeholder: {
                        Circle().fill(Color.gray.opacity(0.3))
                    }
                    .frame(width: 40, height: 40)

                    Text(opponent?.name ?? "")
                        .font(.system(size: 10))
                        .foregroundColor(.white.opacity(0.8))
                        .lineLimit(1)
                        .frame(width: 60)
                        .multilineTextAlignment(.center)
                }

                VStack(alignment: .center, spacing: 2) {
                    Text(isWin ? "WIN" : "LOSS")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(isWin ? winGreen : lossRed)

                    Text(scoreText)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)

                    Text(match.event)
                        .font(.system(size: 10))
                        .foregroundColor(.white.opacity(0.6))
                        .lineLimit(1)

                    Text(match.tournament)
                        .font(.system(size: 9))
                        .foregroundColor(.white.opacity(0.5))
                        .lineLimit(1)
                }

                VStack(alignment: .center) {
                    AsyncImage(url: URL(string: ourTeam?.logo ?? "")) { image in
                        image.resizable().scaledToFit()
                    } placeholder: {
                        Circle().fill(Color.gray.opacity(0.3))
                    }
                    .frame(width: 40, height: 40)

                    Text(ourTeam?.name ?? "")
                        .font(.system(size: 10))
                        .foregroundColor(.white.opacity(0.8))
                        .lineLimit(1)
                        .frame(width: 60)
                        .multilineTextAlignment(.center)
                }
            }
            .frame(maxWidth: .infinity)

            VStack(alignment: .trailing, spacing: 2) {
                Text(match.ago ?? "")
                    .font(.system(size: 10))
                    .foregroundColor(.white.opacity(0.6))

                Text("ago")
                    .font(.system(size: 9))
                    .foregroundColor(.white.opacity(0.5))
            }
        }
        .padding(16)
        .background((isWin ? winGreen : lossRed).opacity(0.15))
        .cornerRadius(12)
    }
}

struct EmptyStateCard: View {
    let message: String

    private let cardDark = Color(red: 0.1, green: 0.1, blue: 0.1)

    var body: some View {
        HStack {
            Spacer()
            Text(message)
                .font(.system(size: 14))
                .foregroundColor(.white.opacity(0.6))
            Spacer()
        }
        .padding(32)
        .background(cardDark)
        .cornerRadius(12)
    }
}

struct ShimmerUpcomingCard: View {
    @State private var shimmerOffset: CGFloat = -200

    private let cardDark = Color(red: 0.1, green: 0.1, blue: 0.1)

    var body: some View {
        HStack {
            HStack(spacing: 12) {
                VStack(alignment: .center) {
                    ShimmerBox(width: 40, height: 40)
                    ShimmerBox(width: 60, height: 10)
                }

                VStack(alignment: .center, spacing: 4) {
                    ShimmerBox(width: 30, height: 12)
                    ShimmerBox(width: 80, height: 10)
                    ShimmerBox(width: 60, height: 8)
                }

                VStack(alignment: .center) {
                    ShimmerBox(width: 40, height: 40)
                    ShimmerBox(width: 60, height: 10)
                }
            }
            .frame(maxWidth: .infinity)

            VStack(alignment: .trailing, spacing: 4) {
                ShimmerBox(width: 50, height: 12)
                ShimmerBox(width: 30, height: 8)
            }
        }
        .padding(16)
        .background(cardDark)
        .cornerRadius(12)
        .onAppear {
            withAnimation(.linear(duration: 1.2).repeatForever(autoreverses: false)) {
                shimmerOffset = 1000
            }
        }
    }
}

struct ShimmerCompletedCard: View {
    @State private var shimmerOffset: CGFloat = -200

    private let cardDark = Color(red: 0.1, green: 0.1, blue: 0.1)

    var body: some View {
        HStack {
            HStack(spacing: 12) {
                VStack(alignment: .center) {
                    ShimmerBox(width: 40, height: 40)
                    ShimmerBox(width: 60, height: 10)
                }

                VStack(alignment: .center, spacing: 4) {
                    ShimmerBox(width: 40, height: 12)
                    ShimmerBox(width: 50, height: 16)
                    ShimmerBox(width: 80, height: 10)
                    ShimmerBox(width: 60, height: 8)
                }

                VStack(alignment: .center) {
                    ShimmerBox(width: 40, height: 40)
                    ShimmerBox(width: 60, height: 10)
                }
            }
            .frame(maxWidth: .infinity)

            VStack(alignment: .trailing, spacing: 4) {
                ShimmerBox(width: 40, height: 10)
                ShimmerBox(width: 20, height: 8)
            }
        }
        .padding(16)
        .background(cardDark)
        .cornerRadius(12)
        .onAppear {
            withAnimation(.linear(duration: 1.2).repeatForever(autoreverses: false)) {
                shimmerOffset = 1000
            }
        }
    }
}

struct ShimmerBox: View {
    let width: CGFloat
    let height: CGFloat

    @State private var shimmerOffset: CGFloat = -200

    private let cardDark = Color(red: 0.1, green: 0.1, blue: 0.1)

    var body: some View {
        RoundedRectangle(cornerRadius: 2)
            .fill(
                LinearGradient(
                    colors: [
                        cardDark,
                        cardDark.opacity(0.5),
                        cardDark
                    ],
                    startPoint: .leading,
                    endPoint: .trailing
                )
            )
            .frame(width: width, height: height)
            .offset(x: shimmerOffset)
            .onAppear {
                withAnimation(.linear(duration: 1.2).repeatForever(autoreverses: false)) {
                    shimmerOffset = 1000
                }
            }
    }
}

struct MatchData {
    let id: String
    let teams: [TeamData]
    let status: String
    let event: String
    let tournament: String
    let timestamp: Int
    let timeUntil: String
    let ago: String?
}

struct TeamData {
    let id: String
    let name: String
    let country: String?
    let score: String?
    let logo: String?
    let won: Bool?
}
