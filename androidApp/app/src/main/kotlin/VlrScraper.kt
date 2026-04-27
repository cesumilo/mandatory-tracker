package com.valoranttracker.app

import android.util.Log
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Web scraper for vlr.gg using Jsoup
 * Fetches match data directly from the website with proper HTML parsing
 */
object VlrScraper {

    private const val TEAM_ID = "7967"
    private const val BASE_URL = "https://www.vlr.gg"
    private const val TEAM_URL = "$BASE_URL/team/$TEAM_ID/mandatory"

    // Cache
    private var lastScrapedTime: Long = 0
    private var cachedUpcoming: List<MatchData> = emptyList()
    private var cachedPast: List<MatchData> = emptyList()
    private const val CACHE_DURATION_MS = 60 * 60 * 1000 // 1 hour

    data class ScrapedMatch(
        val matchId: String,
        val matchUrl: String,
        val tournamentName: String,
        val eventName: String,
        val team1Name: String,
        val team1Logo: String,
        val team2Name: String,
        val team2Logo: String,
        val timeUntil: String?,
        val score1: String?,
        val score2: String?,
        val matchDate: String?, // Format: "2026/04/26"
        val matchTime: String?, // Format: "9:00 pm"
        val isUpcoming: Boolean,
        val isCompleted: Boolean,
        val isWin: Boolean?
    )

    fun getUpcomingMatches(forceRefresh: Boolean = false): List<MatchData> {
        val now = System.currentTimeMillis()

        // Return cached data if valid
        if (!forceRefresh && cachedUpcoming.isNotEmpty() && (now - lastScrapedTime) < CACHE_DURATION_MS) {
            Log.d("VlrScraper", "Returning cached upcoming matches")
            return cachedUpcoming
        }

        return try {
            Log.d("VlrScraper", "Fetching fresh data from $TEAM_URL")
            val doc = Jsoup.connect(TEAM_URL)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .timeout(15000)
                .get()

            val scrapedMatches = parseMatchItems(doc)

            // Separate upcoming and completed
            val upcoming = scrapedMatches.filter { !it.isCompleted }
            val completed = scrapedMatches.filter { it.isCompleted }

            // Convert to MatchData
            cachedUpcoming = upcoming.map { it.toMatchData() }
            cachedPast = completed.map { it.toMatchData() }
            lastScrapedTime = now

            Log.d("VlrScraper", "Scraped ${upcoming.size} upcoming, ${completed.size} completed matches")

            cachedUpcoming
        } catch (e: Exception) {
            Log.e("VlrScraper", "Error scraping: ${e.message}", e)
            // Return cached data even if expired
            cachedUpcoming
        }
    }

    fun getPastMatches(forceRefresh: Boolean = false): List<MatchData> {
        // Ensure we have data
        if (cachedPast.isEmpty() || forceRefresh || (System.currentTimeMillis() - lastScrapedTime) >= CACHE_DURATION_MS) {
            getUpcomingMatches(forceRefresh)
        }
        return cachedPast.take(5)
    }

    private fun parseMatchItems(doc: Document): List<ScrapedMatch> {
        val matches = mutableListOf<ScrapedMatch>()

        // Find all match items
        val matchItems = doc.select("div.m-item, a.m-item")

        Log.d("VlrScraper", "Found ${matchItems.size} match items")

        for (item in matchItems) {
            try {
                val match = parseMatchItem(item)
                if (match != null) {
                    matches.add(match)
                }
            } catch (e: Exception) {
                Log.w("VlrScraper", "Failed to parse match item: ${e.message}")
            }
        }

        return matches
    }

    private fun parseMatchItem(item: Element): ScrapedMatch? {
        // Get match link and ID
        val linkElement = item.select("a[href]").first() ?: item
        val href = linkElement.attr("href")
        if (href.isEmpty()) return null

        val matchId = href.substringAfterLast("/").substringBefore("-").take(20)
        val fullUrl = if (href.startsWith("http")) href else "$BASE_URL$href"

        // Check if it's upcoming or completed
        val resultDiv = item.select("div.m-item-result").first()
        val isUpcoming = resultDiv?.hasClass("mod-tbd") == true
        val isCompleted = resultDiv?.let {
            it.hasClass("mod-win") || it.hasClass("mod-loss")
        } == true

        // Skip if neither upcoming nor completed (might be live)
        if (!isUpcoming && !isCompleted) {
            return null
        }

        // Parse tournament info
        val eventDiv = item.select("div.m-item-event").first()
        val tournamentDiv = eventDiv?.select("div[style*=font-weight: 700]")?.first()
        val eventDetailDiv = eventDiv?.select("div:not([style*=font-weight])")?.first()

        val tournamentName = tournamentDiv?.text()?.trim() ?: "VCL"
        val eventName = eventDetailDiv?.text()?.replace("·", "•")?.trim() ?: ""

        // Parse team names
        val teamNameElements = item.select("span.m-item-team-name")
        val team1Name = teamNameElements.getOrNull(0)?.text()?.trim() ?: "TBD"
        val team2Name = teamNameElements.getOrNull(1)?.text()?.trim() ?: "TBD"

        // Parse team logos
        val logoElements = item.select("div.m-item-logo img")
        val team1Logo = logoElements.getOrNull(0)?.attr("src")?.let { fixUrl(it) }
            ?: "https://owcdn.net/img/695c961fbab82.png"
        val team2Logo = logoElements.getOrNull(1)?.attr("src")?.let { fixUrl(it) }
            ?: "https://owcdn.net/img/default-logo.png"

        // Determine which team is Mandatory
        val team1IsMandatory = team1Name.contains("Mandatory", ignoreCase = true)
        val mandatoryIsFirst = team1IsMandatory

// Parse scores, time, and date
        var timeUntil: String? = null
        var score1: String? = null
        var score2: String? = null
        var isWin: Boolean? = null
        var matchDate: String? = null
        var matchTime: String? = null

if (isUpcoming) {
            timeUntil = item.select("span.rm-item-score-eta").text().trim()
            if (timeUntil.isEmpty()) timeUntil = "TBD"

            // Parse date and time from m-item-date
            val dateContainer = item.select("div.m-item-date").first()
            if (dateContainer != null) {
                // Get the HTML content to parse date and time separately
                val html = dateContainer.html()

                // Extract date from between <div> and </div>
                val dateMatch = Regex("<div>\\s*(\\d{4}/\\d{2}/\\d{2})\\s*</div>").find(html)
                matchDate = dateMatch?.groupValues?.get(1)

                // Extract time from after </div> to end
                val timeMatch = Regex("</div>\\s*([\\d:]+\\s*(?:am|pm|AM|PM)?)").find(html)
                matchTime = timeMatch?.groupValues?.get(1)?.trim()

                Log.d("VlrScraper", "Parsed upcoming - raw html: ${html.replace("\n", "")}")
                Log.d("VlrScraper", "Parsed upcoming - date: '$matchDate', time: '$matchTime'")
            }
        } else if (isCompleted) {
            val scoreSpans = resultDiv?.select("span")
            score1 = scoreSpans?.getOrNull(0)?.text()
            score2 = scoreSpans?.getOrNull(1)?.text()

            isWin = resultDiv?.hasClass("mod-win") == true

            // Parse date for completed matches from inner div and clean it
            val dateDiv = item.select("div.m-item-date > div").first()
            matchDate = dateDiv?.text()?.trim()?.replace(Regex("\\s+"), "")
        }

        // Reorder teams so Mandatory is always first
        val (finalTeam1Name, finalTeam1Logo, finalTeam2Name, finalTeam2Logo) = if (!mandatoryIsFirst) {
            // Swap teams
            Quadruple(team2Name, team2Logo, team1Name, team1Logo)
        } else {
            Quadruple(team1Name, team1Logo, team2Name, team2Logo)
        }

        return ScrapedMatch(
            matchId = matchId,
            matchUrl = fullUrl,
            tournamentName = tournamentName,
            eventName = eventName,
            team1Name = finalTeam1Name,
            team1Logo = finalTeam1Logo,
            team2Name = finalTeam2Name,
            team2Logo = finalTeam2Logo,
            timeUntil = timeUntil,
            score1 = score1,
            score2 = score2,
            matchDate = matchDate,
            matchTime = matchTime,
            isUpcoming = isUpcoming,
            isCompleted = isCompleted,
            isWin = isWin
        )
    }

    private fun fixUrl(url: String): String {
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http") -> url
            else -> "https://$url"
        }
    }

private fun ScrapedMatch.toMatchData(): MatchData {
        val isMandatoryWin = isWin == true

        // Calculate "ago" text from match date for completed matches
        val agoText = if (isCompleted && matchDate != null) {
            calculateAgoFromDate(matchDate)
        } else null

        // For upcoming matches, format the time display
        val timeDisplay = if (isUpcoming) {
            if (!matchTime.isNullOrEmpty()) {
                "in $timeUntil at $matchTime"
            } else {
                "in $timeUntil"
            }
        } else {
            timeUntil ?: ""
        }

        return MatchData(
            id = matchId,
            teams = listOf(
                TeamData(
                    id = TEAM_ID,
                    name = team1Name,
                    country = "fr",
                    score = if (isCompleted) score1 else null,
                    logo = team1Logo,
                    won = if (isCompleted) isMandatoryWin else null
                ),
                TeamData(
                    id = "opponent-$matchId",
                    name = team2Name,
                    country = null,
                    score = if (isCompleted) score2 else null,
                    logo = team2Logo,
                    won = if (isCompleted) !isMandatoryWin else null
                )
            ),
            status = if (isCompleted) "Completed" else "Upcoming",
            event = eventName,
            tournament = tournamentName,
            img = null,
            timeUntil = timeDisplay,
            timestamp = parseDateToTimestamp(matchDate, matchTime), // Parse actual date with time
            ago = agoText
        )
    }

    private fun calculateAgoFromDate(dateStr: String): String {
        try {
            // Clean up the date string - extract just YYYY/MM/DD
            val cleanDateStr = dateStr.trim().substringBefore(" ").replace(Regex("\\s+"), "")

            // Parse date format: "2026/04/26"
            val parts = cleanDateStr.split("/")
            if (parts.size == 3) {
                val year = parts[0].toInt()
                val month = parts[1].toInt()
                val day = parts[2].toInt().coerceIn(1, 31) // Ensure valid day

                // Create calendar for match date at midnight
                val matchCal = java.util.Calendar.getInstance()
                matchCal.set(year, month - 1, day, 0, 0, 0)
                matchCal.set(java.util.Calendar.MILLISECOND, 0)

                // Get today's date at midnight
                val todayCal = java.util.Calendar.getInstance()
                val currentYear = todayCal.get(java.util.Calendar.YEAR)
                val currentMonth = todayCal.get(java.util.Calendar.MONTH)
                val currentDay = todayCal.get(java.util.Calendar.DAY_OF_MONTH)
                todayCal.set(currentYear, currentMonth, currentDay, 0, 0, 0)
                todayCal.set(java.util.Calendar.MILLISECOND, 0)

                // Calculate days difference (today - match = days ago)
                val diffMillis = todayCal.timeInMillis - matchCal.timeInMillis
                val daysAgo = (diffMillis / (1000 * 60 * 60 * 24)).toInt()

                Log.d("VlrScraper", "Date: $cleanDateStr (y=$year, m=$month, d=$day), today: $currentYear/$currentMonth/$currentDay, days ago: $daysAgo")

                return when {
                    daysAgo < 0 -> "in ${-daysAgo}d" // Future date
                    daysAgo == 0 -> "Today"
                    daysAgo == 1 -> "1d ago"
                    daysAgo < 7 -> "${daysAgo}d ago"
                    daysAgo < 30 -> "${daysAgo / 7}w ago"
                    else -> "${daysAgo / 30}mo ago"
                }
            }
        } catch (e: Exception) {
            Log.w("VlrScraper", "Failed to calculate ago from date: '$dateStr' - ${e.message}")
        }
        return "Recent"
    }

    private fun parseDateToTimestamp(dateStr: String?, timeStr: String? = null): Long {
        if (dateStr == null) return System.currentTimeMillis() / 1000

        try {
            // Extract just the date part (YYYY/MM/DD) before any time
            val dateOnly = dateStr.trim().substringBefore(" ").replace(Regex("\\s+"), "")
            val parts = dateOnly.split("/")

            if (parts.size == 3) {
                val year = parts[0].toInt()
                val month = parts[1].toInt()
                val day = parts[2].toInt()

                val cal = java.util.Calendar.getInstance()

                // Parse time if available (format: "9:00 pm")
                var hour = 0
                var minute = 0
                if (!timeStr.isNullOrEmpty()) {
                    val timeClean = timeStr.trim().lowercase()
                    val timeParts = timeClean.split(":", " ")
                    if (timeParts.size >= 2) {
                        hour = timeParts[0].toInt()
                        minute = timeParts[1].toInt()
                        // Handle pm
                        if (timeClean.contains("pm") && hour < 12) {
                            hour += 12
                        }
                        // Handle am
                        if (timeClean.contains("am") && hour == 12) {
                            hour = 0
                        }
                    }
                }

                cal.set(year, month - 1, day, hour, minute, 0)
                return cal.timeInMillis / 1000
            }
        } catch (e: Exception) {
            Log.w("VlrScraper", "Failed to parse date/time: '$dateStr' / '$timeStr'")
        }
        return System.currentTimeMillis() / 1000
    }

    // Helper data class for swapping
    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
