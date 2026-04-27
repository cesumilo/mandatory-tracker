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

        // Parse scores and time
        var timeUntil: String? = null
        var score1: String? = null
        var score2: String? = null
        var isWin: Boolean? = null

        if (isUpcoming) {
            timeUntil = item.select("span.rm-item-score-eta").text().trim()
            if (timeUntil.isEmpty()) timeUntil = "TBD"
        } else if (isCompleted) {
            val scoreSpans = resultDiv?.select("span")
            score1 = scoreSpans?.getOrNull(0)?.text()
            score2 = scoreSpans?.getOrNull(1)?.text()

            isWin = resultDiv?.hasClass("mod-win") == true
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
            timeUntil = timeUntil ?: "",
            timestamp = System.currentTimeMillis() / 1000, // Placeholder - would parse actual date
            ago = if (isCompleted) "Recent" else null
        )
    }

    // Helper data class for swapping
    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
