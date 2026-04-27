package com.valoranttracker.shared.data.scraping

import com.valoranttracker.shared.domain.model.CompletedMatch
import com.valoranttracker.shared.domain.model.MatchStatus
import com.valoranttracker.shared.domain.model.TeamRef
import com.valoranttracker.shared.domain.model.UpcomingMatch
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Web scraper for vlr.gg that fetches match data directly from the website.
 * This implementation uses HTML scraping instead of the vlresports API.
 */
@Serializable
data class ScrapedMatch(
    val id: String,
    val teams: List<ScrapedTeam>,
    val status: String,
    val event: String,
    val tournament: String,
    val img: String? = null,
    val timeLabel: String,
    @SerialName("timestamp")
    val timestamp: Long = 0L,
)

@Serializable
data class ScrapedTeam(
    val id: String,
    val name: String,
    val country: String? = null,
    val score: String? = null,
    val logo: String? = null,
    val won: Boolean? = null,
)

class VlrScraper(
    private val httpClient: HttpClient,
    private val baseUrl: String = "https://www.vlr.gg",
) {
    private var lastScraped: Long = 0
    private var cachedUpcomingMatches: List<UpcomingMatch> = emptyList()
    private var cachedCompletedMatches: List<CompletedMatch> = emptyList()

    /**
     * Get upcoming matches for a specific team by scraping the team page.
     * Implements 1-hour caching as requested.
     */
    suspend fun getUpcomingMatches(teamId: String = "7967"): Result<List<UpcomingMatch>> {
        return try {
            // Check if we should use cached data (less than 1 hour old)
            val now = Clock.System.now().epochSeconds
            if (now - lastScraped < 3600 && cachedUpcomingMatches.isNotEmpty()) { // 1 hour in seconds
                return Result.success(cachedUpcomingMatches)
            }

            // Scrape new data
            val upcomingMatches = scrapeMatchesFromPage(teamId)
            val completedMatches = scrapeResultsFromPage(teamId)

            // Update cache
            lastScraped = now
            cachedUpcomingMatches = upcomingMatches
            cachedCompletedMatches = completedMatches

            Result.success(upcomingMatches)
        } catch (e: Exception) {
            // If scraping fails, return cached data if available
            if (cachedUpcomingMatches.isNotEmpty()) {
                Result.success(cachedUpcomingMatches)
            } else {
                Result.failure(e)
            }
        }
    }

    /**
     * Get completed matches for a specific team by scraping the team page.
     * Implements 1-hour caching as requested.
     */
    suspend fun getCompletedMatches(
        teamId: String = "7967",
        limit: Int = 10,
    ): Result<List<CompletedMatch>> {
        return try {
            // Check if we should use cached data (less than 1 hour old)
            val now = Clock.System.now().epochSeconds
            if (now - lastScraped < 3600 && cachedCompletedMatches.isNotEmpty()) { // 1 hour in seconds
                return Result.success(cachedCompletedMatches.take(limit))
            }

            // Scrape new data
            val upcomingMatches = scrapeMatchesFromPage(teamId)
            val completedMatches = scrapeResultsFromPage(teamId)

            // Update cache
            lastScraped = now
            cachedUpcomingMatches = upcomingMatches
            cachedCompletedMatches = completedMatches

            Result.success(completedMatches.take(limit))
        } catch (e: Exception) {
            // If scraping fails, return cached data if available
            if (cachedCompletedMatches.isNotEmpty()) {
                Result.success(cachedCompletedMatches.take(limit))
            } else {
                Result.failure(e)
            }
        }
    }

    private suspend fun scrapeMatchesFromPage(teamId: String): List<UpcomingMatch> {
        val teamPageUrl = "$baseUrl/team/$teamId/mandatory"

        val htmlContent: String = httpClient.get(teamPageUrl) {
            header("User-Agent", "ValorantWidget/1.0 (+https://github.com/cesumilo/mandatory-tracker)")
            header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            header("Accept-Language", "en-US,en;q=0.5")
        }.body()

        // Parse upcoming matches from HTML
        return parseUpcomingMatchesFromHtml(htmlContent, teamId)
    }

    private suspend fun scrapeResultsFromPage(teamId: String): List<CompletedMatch> {
        val teamPageUrl = "$baseUrl/team/$teamId/mandatory"

        val htmlContent: String = httpClient.get(teamPageUrl) {
            header("User-Agent", "ValorantWidget/1.0 (+https://github.com/cesumilo/mandatory-tracker)")
            header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            header("Accept-Language", "en-US,en;q=0.5")
        }.body()

        // Parse completed matches from HTML
        return parseCompletedMatchesFromHtml(htmlContent, teamId)
    }

    private fun parseUpcomingMatchesFromHtml(htmlContent: String, teamId: String): List<UpcomingMatch> {
        val matches = mutableListOf<UpcomingMatch>()

        // Find the upcoming matches section - this looks for "Upcoming matches" in the HTML
        val upcomingSectionStart = htmlContent.indexOf("Upcoming matches", ignoreCase = true)
        if (upcomingSectionStart == -1) {
            return emptyList()
        }

        // Try to find the next section after upcoming matches
        val upcomingSectionEnd = htmlContent.indexOf("Recent Results", upcomingSectionStart)
        val upcomingSection = if (upcomingSectionEnd != -1) {
            htmlContent.substring(upcomingSectionStart, upcomingSectionEnd)
        } else {
            // If we can't find "Recent Results", take a reasonable chunk of the page
            htmlContent.substring(upcomingSectionStart, minOf(upcomingSectionStart + 10000, htmlContent.length))
        }

        // Use a simplified regex pattern to extract match information
        // This regex tries to catch matches with tournament images, team names, logos, and times
        val matchPattern = Regex("""href="/\d+/[^"]*"[\s\S]*?<span[^>]*>([^<]+)</span>[\s\S]*?(?:\d+d \d+h)""", RegexOption.DOT_MATCHES_ALL)

        val matchResults = matchPattern.findAll(upcomingSection)

        // Create placeholder matches since we can't reliably parse all details from HTML
        var count = 0
        for (matchResult in matchResults) {
            count++
            try {
                // Parse opponent name (simplified)
                val opponentName = matchResult.groupValues[1].trim()

                // Create a placeholder match with minimal data
                val placeholderId = "$teamId-upcoming-$count-${System.currentTimeMillis()}"

                val match = UpcomingMatch(
                    id = placeholderId,
                    team1 = TeamRef(
                        id = teamId,
                        name = "Mandatory",
                        logoUrl = "https://owcdn.net/img/695c961fbab82.png",
                        country = "fr",
                        score = null
                    ),
                    team2 = TeamRef(
                        id = "$teamId-vs-$count",
                        name = opponentName,
                        logoUrl = "https://owcdn.net.img/example-logo.png", // Placeholder
                        country = null,
                        score = null
                    ),
                    eventName = "VCL Match",
                    tournament = "Challengers 2026",
                    status = MatchStatus.UPCOMING,
                    scheduledAtEpochSeconds = null,
                    timestamp = System.currentTimeMillis() / 1000 + count * 86400,
                    rawTimeLabel = "Soon",
                    matchImageUrl = "https://owcdn.net/img/example-tournament.png"
                )

                matches.add(match)
            } catch (e: Exception) {
                // Skip malformed matches
                continue
            }
        }

        return matches.sortedBy { it.timestamp }
    }

    private fun parseCompletedMatchesFromHtml(htmlContent: String, teamId: String): List<CompletedMatch> {
        val matches = mutableListOf<CompletedMatch>()

        // Find the recent results section
        val resultsSectionStart = htmlContent.indexOf("Recent Results", ignoreCase = true)
        if (resultsSectionStart == -1) {
            return emptyList()
        }

        // Try to find the next section after recent results
        val resultsSectionEnd = htmlContent.indexOf("<h2", resultsSectionStart + 1)
        val resultsSection = if (resultsSectionEnd != -1) {
            htmlContent.substring(resultsSectionStart, resultsSectionEnd)
        } else {
            // If we can't find the next section, take a reasonable chunk
            htmlContent.substring(resultsSectionStart, minOf(resultsSectionStart + 15000, htmlContent.length))
        }

        // Use a simplified regex pattern to extract match information
        val resultPattern = Regex("""href="/\d+/[^"]*"[\s\S]*?<span[^>]*>([^<]+)</span>[\s\S]*?(\d+)[\s\S]*?:[\s\S]*?(\d+)""", RegexOption.DOT_MATCHES_ALL)

        val resultResults = resultPattern.findAll(resultsSection)

        // Create placeholder matches for results
        var count = 0
        for (resultResult in resultResults) {
            count++
            try {
                val opponentName = resultResult.groupValues[1].trim()
                val ourScore = resultResult.groupValues[2]
                val oppScore = resultResult.groupValues[3]

                // Determine winner (simplified - assume higher score is winner)
                val isWin = ourScore.toIntOrNull() ?: 0 > oppScore.toIntOrNull() ?: 0
                val winnerTeamId = if (isWin) teamId else "$teamId-vs-$count"

                // Create a placeholder match
                val placeholderId = "$teamId-result-$count-${System.currentTimeMillis()}"

                val match = CompletedMatch(
                    id = placeholderId,
                    team1 = TeamRef(
                        id = teamId,
                        name = "Mandatory",
                        logoUrl = "https://owcdn.net/img/695c961fbab82.png",
                        country = "fr",
                        score = ourScore
                    ),
                    team2 = TeamRef(
                        id = "$teamId-vs-$count",
                        name = opponentName,
                        logoUrl = "https://owcdn.net.img/example-logo.png", // Placeholder
                        country = null,
                        score = oppScore
                    ),
                    eventName = "VCL Match",
                    tournament = "Challengers 2026",
                    status = MatchStatus.COMPLETED,
                    rawTimeLabel = "Recently",
                    matchImageUrl = "https://owcdn.net/img/example-tournament.png",
                    winnerTeamId = winnerTeamId
                )

                matches.add(match)
            } catch (e: Exception) {
                // Skip malformed matches
                continue
            }
        }

        return matches.take(10) // Limit to 10 most recent results
    }
}
