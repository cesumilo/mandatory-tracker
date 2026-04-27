import Foundation
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
                        HeaderSection(notificationStatus: notificationStatus) {
                            Task {
                                NotificationScheduler.shared.scheduleNotificationsIfNeeded()
                                WidgetCenter.shared.reloadAllTimelines()
                            }
                        }

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

    // Cache variables for 1-hour caching
    private var lastScrapedTimestamp: TimeInterval = 0
    private var cachedUpcomingMatches: [MatchData] = []
    private var cachedPastMatches: [MatchData] = []

    private func fetchUpcomingMatches() async {
        let now = Date().timeIntervalSince1970

        // Use cached data if less than 1 hour old
        if now - lastScrapedTimestamp < 3600, !cachedUpcomingMatches.isEmpty {
            print("Using cached upcoming matches (less than 1 hour old)")
            upcomingMatches = cachedUpcomingMatches
            return
        }

        let teamId = "7967"
        guard let url = URL(string: "https://www.vlr.gg/team/\(teamId)/mandatory") else { return }

        do {
            var request = URLRequest(url: url)
            request.setValue("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36", forHTTPHeaderField: "User-Agent")
            request.setValue("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", forHTTPHeaderField: "Accept")
            request.setValue("en-US,en;q=0.5", forHTTPHeaderField: "Accept-Language")

            let (data, _) = try await URLSession.shared.data(for: request)
            guard let htmlString = String(data: data, encoding: .utf8) else { return }

            print("Scraped team page, parsing upcoming matches...")
            let upcoming = await parseUpcomingMatchesFromHTML(htmlString, teamId: teamId)

            // Update cache
            lastScrapedTimestamp = now
            cachedUpcomingMatches = upcoming
            upcomingMatches = upcoming

        } catch {
            print("Error scraping upcoming matches: \(error)")

            // Fall back to cached data if available
            if !cachedUpcomingMatches.isEmpty {
                upcomingMatches = cachedUpcomingMatches
            }
        }
    }

    private func fetchPastMatches() async {
        let now = Date().timeIntervalSince1970

        // Use cached data if less than 1 hour old (use same timestamp)
        if now - lastScrapedTimestamp < 3600, !cachedPastMatches.isEmpty {
            print("Using cached past matches (less than 1 hour old)")
            pastMatches = cachedPastMatches
            return
        }

        let teamId = "7967"
        guard let url = URL(string: "https://www.vlr.gg/team/\(teamId)/mandatory") else { return }

        do {
            var request = URLRequest(url: url)
            request.setValue("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36", forHTTPHeaderField: "User-Agent")
            request.setValue("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", forHTTPHeaderField: "Accept")
            request.setValue("en-US,en;q=0.5", forHTTPHeaderField: "Accept-Language")

            let (data, _) = try await URLSession.shared.data(for: request)
            guard let htmlString = String(data: data, encoding: .utf8) else { return }

            print("Scraped team page, parsing past matches...")
            let past = await parsePastMatchesFromHTML(htmlString, teamId: teamId)

            // Update cache
            cachedPastMatches = past
            pastMatches = past

        } catch {
            print("Error scraping past matches: \(error)")

            // Fall back to cached data if available
            if !cachedPastMatches.isEmpty {
                pastMatches = cachedPastMatches
            }
        }
    }

    private func parseUpcomingMatchesFromHTML(_ htmlString: String, teamId: String) async -> [MatchData] {
        var matches: [MatchData] = []

        // Look for "Upcoming matches" section
        if let upcomingRange = htmlString.range(of: "Upcoming matches") {
            let startIndex = upcomingRange.upperBound
            let remaining = String(htmlString[startIndex...])

            // Find the next section to limit our parsing
            let endIndex = remaining.range(of: "<h2") ?? remaining.startIndex..<remaining.startIndex
            let upcomingSection = String(remaining[..<endIndex.lowerBound])

            // Extract match links - look for "/649787/" pattern in href attributes
            let linkPattern = "href=\"/(\\d+)/"
            guard let regex = try? NSRegularExpression(pattern: linkPattern, options: []) else {
                return []
            }

            let matchesInText = regex.matches(in: upcomingSection, options: [], range: NSRange(upcomingSection.startIndex..., in: upcomingSection))

            var count = 0
            for match in matchesInText.prefix(3) {
                if let range = Range(match.range(at: 1), in: upcomingSection) {
                    let matchId = String(upcomingSection[range])

                    let matchData = MatchData(
                        id: matchId,
                        teams: [
                            TeamData(
                                id: teamId,
                                name: "Mandatory",
                                country: "fr",
                                score: nil,
                                logo: "https://owcdn.net/img/695c961fbab82.png",
                                won: nil
                            ),
                            TeamData(
                                id: "opponent-\(matchId)",
                                name: "Opponent Team",
                                country: nil,
                                score: nil,
                                logo: "https://owcdn.net/img/default-logo.png",
                                won: nil
                            )
                        ],
                        status: "Upcoming",
                        event: "VCL Match",
                        tournament: "Challengers 2026",
                        timestamp: Int(Date().timeIntervalSince1970) + (count + 1) * 86400,
                        timeUntil: "Soon",
                        ago: nil
                    )

                    matches.append(matchData)
                    count += 1
                }
            }
        }

        print("Parsed \(matches.count) upcoming matches from HTML")
        return matches
    }

    private func parsePastMatchesFromHTML(_ htmlString: String, teamId: String) async -> [MatchData] {
        var matches: [MatchData] = []

        // Look for "Recent Results" section
        if let resultsRange = htmlString.range(of: "Recent Results") {
            let startIndex = resultsRange.upperBound
            let remaining = String(htmlString[startIndex...])

            // Find the next section to limit our parsing
            let endIndex = remaining.range(of: "<h2") ?? remaining.startIndex..<remaining.startIndex
            let resultsSection = String(remaining[..<endIndex.lowerBound])

            // Extract match links with scores - look for "m-item-result mod-win" or "mod-loss" patterns
            let scorePattern = "m-item-result mod-(win|loss).*?>(\\d+).*?:.*?(\\d+)"
            guard let regex = try? NSRegularExpression(pattern: scorePattern, options: .dotMatchesLineSeparators) else {
                return []
            }

            let matchesInText = regex.matches(in: resultsSection, options: [], range: NSRange(resultsSection.startIndex..., in: resultsSection))

            var count = 0
            for match in matchesInText.prefix(5) {
                guard let matchIdRange = Range(match.range(at: 1), in: resultsSection),
                      let score1Range = Range(match.range(at: 2), in: resultsSection),
                      let score2Range = Range(match.range(at: 3), in: resultsSection)
                else {
                    continue
                }

                let matchId = String(resultsSection[matchIdRange])
                let score1 = String(resultsSection[score1Range])
                let score2 = String(resultsSection[score2Range])

                // Simplified win/loss determination
                let ourScore = Int(score1) ?? 0
                let oppScore = Int(score2) ?? 0
                let isWin = ourScore > oppScore

                let matchData = MatchData(
                    id: matchId,
                    teams: [
                        TeamData(
                            id: teamId,
                            name: "Mandatory",
                            country: "fr",
                            score: "\(isWin ? 2 : 1)",
                            logo: "https://owcdn.net/img/695c961fbab82.png",
                            won: isWin
                        ),
                        TeamData(
                            id: "opponent-\(matchId)",
                            name: "Opponent Team",
                            country: nil,
                            score: "\(isWin ? 1 : 2)",
                            logo: "https://owcdn.net/img/default-logo.png",
                            won: !isWin
                        )
                    ],
                    status: "Completed",
                    event: "VCL Match",
                    tournament: "Challengers 2026",
                    timestamp: Int(Date().timeIntervalSince1970) - (count + 1) * 86400,
                    timeUntil: "",
                    ago: "\(count + 1)d ago"
                )

                matches.append(matchData)
                count += 1
            }
        }

        print("Parsed \(matches.count) past matches from HTML")
        return matches.prefix(5).map { $0 }
    }

    private func loadNextMatchInfo() async {
        let teamId = "7967"

        // If we have cached upcoming matches, use the first one
        if let firstMatch = cachedUpcomingMatches.first {
            let opponent = firstMatch.teams.first { $0.id != teamId }
            let opponentName = opponent?.name ?? "TBD"
            notificationStatus = "Next: vs \(opponentName) soon"
            return
        }

        // Otherwise scrape fresh data
        guard let url = URL(string: "https://www.vlr.gg/team/\(teamId)/mandatory") else {
            notificationStatus = nil
            return
        }

        do {
            var request = URLRequest(url: url)
            request.setValue("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36", forHTTPHeaderField: "User-Agent")

            let (data, _) = try await URLSession.shared.data(for: request)
            guard let htmlString = String(data: data, encoding: .utf8) else {
                notificationStatus = nil
                return
            }

            // Parse for upcoming matches
            let upcoming = await parseUpcomingMatchesFromHTML(htmlString, teamId: teamId)

            if let firstMatch = upcoming.first {
                let opponent = firstMatch.teams.first { $0.id != teamId }
                let opponentName = opponent?.name ?? "TBD"
                notificationStatus = "Next: vs \(opponentName) soon"
            } else {
                notificationStatus = "No upcoming match"
            }
        } catch {
            notificationStatus = nil
        }
    }
}

struct HeaderSection: View {
    let notificationStatus: String?
    let onRefresh: () -> Void

    @State private var isRefreshing = false
    private let mandatoryRed = Color(red: 1.0, green: 0.17, blue: 0.17)

    var body: some View {
        VStack(spacing: 8) {
            HStack {
                Spacer()
                Button(action: {
                    isRefreshing = true
                    onRefresh()
                    DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                        isRefreshing = false
                    }
                }) {
                    Image(systemName: "arrow.clockwise")
                        .foregroundColor(isRefreshing ? mandatoryRed : .white.opacity(0.7))
                        .font(.system(size: 20))
                }
            }
            .padding(.horizontal, 16)

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

            if isRefreshing {
                Spacer()
                    .frame(height: 4)
                Text("Refreshing...")
                    .font(.system(size: 10))
                    .foregroundColor(.white.opacity(0.5))
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
