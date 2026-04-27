import Foundation
import SwiftSoup

/**
 * Web scraper for vlr.gg using SwiftSoup (proper HTML parsing)
 * Fetches match data directly from the website with clean HTML parsing
 * Similar to the Android VlrScraper implementation but using SwiftSoup instead of Jsoup
 */
actor VlrScraper {
    static let shared = VlrScraper()

    private let teamId = "7967"
    private let baseUrl = "https://www.vlr.gg"
    private let teamUrl = "https://www.vlr.gg/team/7967/mandatory"

    // Cache
    private var lastScrapedTime: TimeInterval = 0
    private var cachedUpcoming: [MatchData] = []
    private var cachedPast: [MatchData] = []
    private let cacheDuration: TimeInterval = 60 * 60 // 1 hour

    private init() {}

    private struct ScrapedMatch {
        let matchId: String
        let matchUrl: String
        let tournamentName: String
        let eventName: String
        let team1Name: String
        let team1Logo: String
        let team2Name: String
        let team2Logo: String
        let timeUntil: String?
        let score1: String?
        let score2: String?
        let matchDate: String? // Format: "2026/04/26"
        let matchTime: String? // Format: "9:00 pm"
        let isUpcoming: Bool
        let isCompleted: Bool
        let isWin: Bool?
    }

    func getUpcomingMatches(forceRefresh: Bool = false) async -> [MatchData] {
        let now = Date().timeIntervalSince1970

        // Return cached data if valid
        if !forceRefresh, !cachedUpcoming.isEmpty, (now - lastScrapedTime) < cacheDuration {
            print("VlrScraper: Returning cached upcoming matches")
            return cachedUpcoming
        }

        do {
            print("VlrScraper: Fetching fresh data from \(teamUrl)")

            guard let url = URL(string: teamUrl) else {
                print("VlrScraper: Invalid URL")
                return cachedUpcoming
            }

            var request = URLRequest(url: url)
            request.setValue("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36", forHTTPHeaderField: "User-Agent")
            request.setValue("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", forHTTPHeaderField: "Accept")
            request.setValue("en-US,en;q=0.5", forHTTPHeaderField: "Accept-Language")
            request.timeoutInterval = 15

            let (data, _) = try await URLSession.shared.data(for: request)
            guard let htmlString = String(data: data, encoding: .utf8) else {
                print("VlrScraper: Failed to decode HTML")
                return cachedUpcoming
            }

            let scrapedMatches = parseMatchItems(htmlString: htmlString)

            // Separate upcoming and completed
            let upcoming = scrapedMatches.filter(\.isUpcoming)
            let completed = scrapedMatches.filter(\.isCompleted)

            // Convert to MatchData
            cachedUpcoming = upcoming.map { scrapedMatchToMatchData($0) }
            cachedPast = completed.map { scrapedMatchToMatchData($0) }
            lastScrapedTime = now

            print("VlrScraper: Scraped \(upcoming.count) upcoming, \(completed.count) completed matches")

            return cachedUpcoming
        } catch {
            print("VlrScraper: Error scraping: \(error)")
            // Return cached data even if expired
            return cachedUpcoming
        }
    }

    func getPastMatches(forceRefresh: Bool = false) async -> [MatchData] {
        // Ensure we have data
        if cachedPast.isEmpty || forceRefresh || (Date().timeIntervalSince1970 - lastScrapedTime) >= cacheDuration {
            _ = await getUpcomingMatches(forceRefresh: forceRefresh)
        }
        return Array(cachedPast.prefix(5))
    }

    private func parseMatchItems(htmlString: String) -> [ScrapedMatch] {
        var matches: [ScrapedMatch] = []

        do {
            // Parse HTML with SwiftSoup
            let doc: Document = try SwiftSoup.parse(htmlString)

            // Find all match items using CSS selectors (same as Android!)
            let matchItems = try doc.select("div.m-item, a.m-item")

            print("VlrScraper: Found \(matchItems.count) match items")

            for item in matchItems {
                do {
                    if let match = try parseMatchItem(element: item) {
                        matches.append(match)
                    }
                } catch {
                    print("VlrScraper: Failed to parse match item: \(error)")
                }
            }
        } catch {
            print("VlrScraper: Failed to parse HTML with SwiftSoup: \(error)")
        }

        return matches
    }

    private func parseMatchItem(element: Element) throws -> ScrapedMatch? {
        // Get match link and ID using SwiftSoup
        let linkElement = try element.select("a[href]").first() ?? element
        let href = try linkElement.attr("href")
        if href.isEmpty { return nil }

        // Extract match ID from URL (e.g., "/649787/mandatory-vs-joblif")
        let matchId = String(href.split(separator: "/").dropLast().last ?? "")
        let fullUrl = href.hasPrefix("http") ? href : "\(baseUrl)\(href)"

        // Check if it's upcoming or completed using SwiftSoup class checking
        let resultDiv = try element.select("div.m-item-result").first()
        let isUpcoming = try resultDiv?.hasClass("mod-tbd") == true
        let isCompleted = try resultDiv?.hasClass("mod-win") == true || resultDiv?.hasClass("mod-loss") == true

        // Skip if neither upcoming nor completed (might be live)
        if !isUpcoming, !isCompleted {
            return nil
        }

        // Parse tournament info with clean SwiftSoup selectors
        let eventDiv = try element.select("div.m-item-event").first()
        let tournamentName = try eventDiv?.select("div[style*=\"font-weight: 700\"]").first()?.text() ?? "VCL"
        let eventName = try eventDiv?.select("div:not([style*=\"font-weight\"])").first()?.text() ?? ""

        // Parse team names - much cleaner than regex!
        let teamNameElements = try element.select("span.m-item-team-name")
        let team1Name = try teamNameElements.get(0).text()
        let team2Name = try teamNameElements.get(1).text()

        // Parse team logos
        let logoElements = try element.select("div.m-item-logo img")
        let team1LogoRaw = try logoElements.get(0).attr("src")
        let team2LogoRaw = try logoElements.get(1).attr("src")
        let team1Logo = fixUrl(team1LogoRaw)
        let team2Logo = fixUrl(team2LogoRaw)

        // Determine which team is Mandatory
        let team1IsMandatory = team1Name.lowercased().contains("mandatory")
        let mandatoryIsFirst = team1IsMandatory

        var timeUntil: String? = nil
        var score1: String? = nil
        var score2: String? = nil
        var isWinResult: Bool? = nil
        var matchDate: String? = nil
        var matchTime: String? = nil

        if isUpcoming {
            timeUntil = try element.select("span.rm-item-score-eta").first()?.text() ?? "TBD"

            // Parse date and time from m-item-date - SwiftSoup makes this clean!
            let dateContainer = try element.select("div.m-item-date").first()
            if let container = dateContainer {
                // Get all text from the date container
                let dateText = try container.text()

                // Extract date using regex (same as Android implementation)
                if let dateMatch = dateText.range(of: "(\\d{4}/\\d{2}/\\d{2})", options: .regularExpression) {
                    matchDate = String(dateText[dateMatch])
                }

                // Extract time (format: "9:00 pm")
                let timePattern = "(\\d{1,2}:\\d{2}\\s*(?:am|pm|AM|PM)?)"
                if let timeMatch = dateText.range(of: timePattern, options: .regularExpression) {
                    matchTime = String(dateText[timeMatch]).trimmingCharacters(in: .whitespacesAndNewlines)
                }

                print("VlrScraper: Parsed upcoming - date: '\(matchDate ?? "nil")', time: '\(matchTime ?? "nil")'")
            }
        } else if isCompleted {
            // Parse scores from score spans
            let scoreSpans = try resultDiv?.select("span")
            score1 = try scoreSpans?.get(0).text()
            score2 = try scoreSpans?.get(1).text()

            isWinResult = try resultDiv?.hasClass("mod-win") == true

            // Parse date for completed matches
            matchDate = try element.select("div.m-item-date > div").first()?.text()
        }

        // Reorder teams so Mandatory is always first
        let (finalTeam1Name, finalTeam1Logo, finalTeam2Name, finalTeam2Logo) = if !mandatoryIsFirst {
            // Swap teams
            (team2Name, team2Logo, team1Name, team1Logo)
        } else {
            (team1Name, team1Logo, team2Name, team2Logo)
        }

        return ScrapedMatch(
            matchId: matchId,
            matchUrl: fullUrl,
            tournamentName: tournamentName,
            eventName: eventName,
            team1Name: finalTeam1Name,
            team1Logo: finalTeam1Logo,
            team2Name: finalTeam2Name,
            team2Logo: finalTeam2Logo,
            timeUntil: timeUntil,
            score1: score1,
            score2: score2,
            matchDate: matchDate,
            matchTime: matchTime,
            isUpcoming: isUpcoming,
            isCompleted: isCompleted,
            isWin: isWinResult
        )
    }

    private func fixUrl(_ url: String) -> String {
        if url.hasPrefix("//") {
            "https:\(url)"
        } else if url.hasPrefix("http://") || url.hasPrefix("https://") {
            url
        } else {
            "https://\(url)"
        }
    }

    /// Method to convert ScrapedMatch to MatchData
    private func scrapedMatchToMatchData(_ scraped: ScrapedMatch) -> MatchData {
        let isMandatoryWin = scraped.isWin == true

        // Calculate "ago" text from match date for completed matches
        let agoText = scraped.isCompleted && scraped.matchDate != nil ? calculateAgo(from: scraped.matchDate!) : nil

        // For upcoming matches, format the time display
        let timeDisplay: String = if scraped.isUpcoming {
            if let time = scraped.matchTime, !time.isEmpty {
                "in \(scraped.timeUntil ?? "") at \(time)"
            } else {
                "in \(scraped.timeUntil ?? "")"
            }
        } else {
            scraped.timeUntil ?? ""
        }

        return MatchData(
            id: scraped.matchId,
            teams: [
                TeamData(
                    id: "7967",
                    name: scraped.team1Name,
                    country: "fr",
                    score: scraped.isCompleted ? scraped.score1 : nil,
                    logo: scraped.team1Logo,
                    won: scraped.isCompleted ? isMandatoryWin : nil
                ),
                TeamData(
                    id: "opponent-\(scraped.matchId)",
                    name: scraped.team2Name,
                    country: nil,
                    score: scraped.isCompleted ? scraped.score2 : nil,
                    logo: scraped.team2Logo,
                    won: scraped.isCompleted ? !isMandatoryWin : nil
                )
            ],
            status: scraped.isCompleted ? "Completed" : "Upcoming",
            event: scraped.eventName,
            tournament: scraped.tournamentName,
            timestamp: parseDateToTimestamp(dateStr: scraped.matchDate, timeStr: scraped.matchTime),
            timeUntil: timeDisplay,
            ago: agoText
        )
    }

    private func calculateAgo(from dateStr: String) -> String {
        do {
            // Clean up the date string - extract just YYYY/MM/DD
            let cleanDateStr = dateStr.trimmingCharacters(in: .whitespacesAndNewlines).components(separatedBy: .whitespacesAndNewlines).joined()

            let parts = cleanDateStr.split(separator: "/").map { String($0) }
            if parts.count == 3,
               let year = Int(parts[0]),
               let month = Int(parts[1]),
               let day = Int(parts[2])
            {
                // Create date components for match date
                var matchComponents = DateComponents()
                matchComponents.year = year
                matchComponents.month = month
                matchComponents.day = day
                matchComponents.hour = 0
                matchComponents.minute = 0
                matchComponents.second = 0

                // Get today's date components
                let calendar = Calendar.current
                let today = Date()
                let todayComponents = calendar.dateComponents([.year, .month, .day], from: today)

                // Create today's date at midnight
                var todayAtMidnightComponents = DateComponents()
                todayAtMidnightComponents.year = todayComponents.year
                todayAtMidnightComponents.month = todayComponents.month
                todayAtMidnightComponents.day = todayComponents.day
                todayAtMidnightComponents.hour = 0
                todayAtMidnightComponents.minute = 0
                todayAtMidnightComponents.second = 0

                if let matchDate = calendar.date(from: matchComponents),
                   let todayAtMidnight = calendar.date(from: todayAtMidnightComponents)
                {
                    let diff = calendar.dateComponents([.day], from: matchDate, to: todayAtMidnight)
                    let daysAgo = diff.day ?? 0

                    print("VlrScraper: Date: \(cleanDateStr) (y=\(year), m=\(month), d=\(day)), today components: \(todayComponents), days ago: \(daysAgo)")

                    switch daysAgo {
                    case let x where x < 0:
                        return "in \(-x)d"
                    case 0:
                        return "Today"
                    case 1:
                        return "1d ago"
                    case 2..<7:
                        return "\(daysAgo)d ago"
                    case 7..<30:
                        return "\(daysAgo / 7)w ago"
                    default:
                        return "\(daysAgo / 30)mo ago"
                    }
                }
            }
        } catch {
            print("VlrScraper: Failed to calculate ago from date: '\(dateStr)' - \(error)")
        }
        return "Recent"
    }

    private func parseDateToTimestamp(dateStr: String?, timeStr: String? = nil) -> Int {
        guard let dateStr else { return Int(Date().timeIntervalSince1970) }

        do {
            // Extract just the date part (YYYY/MM/DD) before any time
            let dateOnly = dateStr.trimmingCharacters(in: .whitespacesAndNewlines).components(separatedBy: .whitespacesAndNewlines).joined()
            let parts = dateOnly.split(separator: "/").map { String($0) }

            if parts.count == 3,
               let year = Int(parts[0]),
               let month = Int(parts[1]),
               let day = Int(parts[2])
            {
                var components = DateComponents()
                components.year = year
                components.month = month
                components.day = day

                // Parse time if available (format: "9:00 pm")
                if let timeStr, !timeStr.isEmpty {
                    let timeClean = timeStr.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
                    let timeParts = timeClean.split { $0 == ":" || $0 == " " }
                    if timeParts.count >= 2,
                       let hour = Int(timeParts[0]),
                       let minute = Int(timeParts[1])
                    {
                        var finalHour = hour
                        // Handle pm
                        if timeClean.contains("pm"), hour < 12 {
                            finalHour += 12
                        }
                        // Handle am
                        if timeClean.contains("am"), hour == 12 {
                            finalHour = 0
                        }

                        components.hour = finalHour
                        components.minute = minute
                    }
                }

                if let date = Calendar.current.date(from: components) {
                    return Int(date.timeIntervalSince1970)
                }
            }
        } catch {
            print("VlrScraper: Failed to parse date/time: '\(dateStr)' / '\(timeStr ?? "nil")'")
        }
        return Int(Date().timeIntervalSince1970)
    }
}
