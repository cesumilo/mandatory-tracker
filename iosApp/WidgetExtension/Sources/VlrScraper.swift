import Foundation
import SwiftSoup

/**
 * Web scraper for vlr.gg for widget extension
 * Simplified version that only fetches the next upcoming match
 */
actor VlrScraper {
    static let shared = VlrScraper()

    private let teamId = "7967"
    private let baseUrl = "https://www.vlr.gg"
    private let teamUrl = "https://www.vlr.gg/team/7967/mandatory"

    // Cache for widget (15 minute cache since widgets update less frequently)
    private var lastScrapedTime: TimeInterval = 0
    private var cachedNextMatch: NextMatchInfo?
    private let cacheDuration: TimeInterval = 15 * 60 // 15 minutes

    private init() {}

    struct NextMatchInfo {
        let opponentName: String
        let eventName: String
        let tournament: String
        let timeUntil: String
        let matchId: String
    }

    func getNextMatch() async -> NextMatchInfo? {
        let now = Date().timeIntervalSince1970

        // Return cached data if valid
        if let cached = cachedNextMatch, (now - lastScrapedTime) < cacheDuration {
            print("VlrScraper (Widget): Returning cached next match")
            return cached
        }

        do {
            print("VlrScraper (Widget): Fetching fresh data from \(teamUrl)")

            guard let url = URL(string: teamUrl) else {
                print("VlrScraper (Widget): Invalid URL")
                return cachedNextMatch
            }

            var request = URLRequest(url: url)
            request.setValue("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36", forHTTPHeaderField: "User-Agent")
            request.setValue("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", forHTTPHeaderField: "Accept")
            request.setValue("en-US,en;q=0.5", forHTTPHeaderField: "Accept-Language")
            request.timeoutInterval = 15

            let (data, _) = try await URLSession.shared.data(for: request)
            guard let htmlString = String(data: data, encoding: .utf8) else {
                print("VlrScraper (Widget): Failed to decode HTML")
                return cachedNextMatch
            }

            if let nextMatch = parseNextMatch(htmlString: htmlString) {
                cachedNextMatch = nextMatch
                lastScrapedTime = now
                return nextMatch
            }

        } catch {
            print("VlrScraper (Widget): Error scraping: \(error)")
        }

        return cachedNextMatch
    }

    private func parseNextMatch(htmlString: String) -> NextMatchInfo? {
        do {
            // Parse HTML with SwiftSoup
            let doc: Document = try SwiftSoup.parse(htmlString)

            // Find all match items using CSS selectors
            let matchItems = try doc.select("div.m-item, a.m-item")

            print("VlrScraper (Widget): Found \(matchItems.count) match items")

            for item in matchItems {
                if let match = try parseMatchItem(element: item) {
                    if match.isUpcoming {
                        // Found the first upcoming match
                        let timeDisplay = formatTimeUntil(match.timeUntil, matchTime: match.matchTime)
                        return NextMatchInfo(
                            opponentName: match.team2Name,
                            eventName: match.eventName,
                            tournament: match.tournamentName,
                            timeUntil: timeDisplay,
                            matchId: match.matchId
                        )
                    }
                }
            }
        } catch {
            print("VlrScraper (Widget): Failed to parse HTML with SwiftSoup: \(error)")
        }

        return nil
    }

    private func parseMatchItem(element: Element) throws -> MatchItem? {
        // Get match link and ID using SwiftSoup
        let linkElement = try element.select("a[href]").first() ?? element
        let href = try linkElement.attr("href")
        if href.isEmpty { return nil }

        // Extract match ID from URL
        let matchId = String(href.split(separator: "/").dropLast().last ?? "")

        // Check if it's upcoming or completed
        let resultDiv = try element.select("div.m-item-result").first()
        let isUpcoming = try resultDiv?.hasClass("mod-tbd") == true
        let isCompleted = try resultDiv?.hasClass("mod-win") == true || resultDiv?.hasClass("mod-loss") == true

        // Skip if neither upcoming nor completed (might be live)
        if !isUpcoming, !isCompleted {
            return nil
        }

        // Parse tournament info
        let eventDiv = try element.select("div.m-item-event").first()
        let tournamentName = try eventDiv?.select("div[style*=\"font-weight: 700\"]").first()?.text() ?? "VCL"
        let eventName = try eventDiv?.select("div:not([style*=\"font-weight\"])").first()?.text() ?? ""

        // Parse team names
        let teamNameElements = try element.select("span.m-item-team-name")
        let team1Name = try teamNameElements.get(0).text()
        let team2Name = try teamNameElements.get(1).text()

        var timeUntil: String? = nil
        var matchTime: String? = nil

        if isUpcoming {
            timeUntil = try element.select("span.rm-item-score-eta").first()?.text() ?? "TBD"

            // Parse date and time from m-item-date
            let dateContainer = try element.select("div.m-item-date").first()
            if let container = dateContainer {
                let dateText = try container.text()

                // Extract time (format: "9:00 pm")
                let timePattern = "(\\d{1,2}:\\d{2}\\s*(?:am|pm|AM|PM)?)"
                if let timeMatch = dateText.range(of: timePattern, options: .regularExpression) {
                    matchTime = String(dateText[timeMatch]).trimmingCharacters(in: .whitespacesAndNewlines)
                }
            }
        }

        return MatchItem(
            matchId: matchId,
            tournamentName: tournamentName,
            eventName: eventName,
            team1Name: team1Name,
            team2Name: team2Name,
            timeUntil: timeUntil,
            matchTime: matchTime,
            isUpcoming: isUpcoming,
            isCompleted: isCompleted
        )
    }

    private func formatTimeUntil(_ timeUntil: String?, matchTime: String?) -> String {
        guard let timeUntil, !timeUntil.isEmpty else { return "TBD" }

        if let matchTime, !matchTime.isEmpty {
            return "in \(timeUntil) at \(matchTime)"
        } else {
            return "in \(timeUntil)"
        }
    }
}

// MARK: - Data structures

private struct MatchItem {
    let matchId: String
    let tournamentName: String
    let eventName: String
    let team1Name: String
    let team2Name: String
    let timeUntil: String?
    let matchTime: String?
    let isUpcoming: Bool
    let isCompleted: Bool
}
