package com.valoranttracker.shared.data.api

import com.valoranttracker.shared.data.dto.MatchesResponse
import com.valoranttracker.shared.data.dto.ResultsResponse
import com.valoranttracker.shared.data.dto.toDomainList
import com.valoranttracker.shared.data.dto.toCompletedDomainList
import com.valoranttracker.shared.domain.model.CompletedMatch
import com.valoranttracker.shared.domain.model.UpcomingMatch
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class VlrApi(
    private val httpClient: HttpClient,
    private val baseUrl: String = "https://vlr.orlandomm.net",
) {
    suspend fun getMatches(teamId: String = "7967"): Result<List<UpcomingMatch>> {
        return try {
            val response: MatchesResponse = httpClient.get("$baseUrl/api/v1/matches") {
                header("User-Agent", "ValorantWidget/1.0 (+https://github.com/cesumilo/mandatory-tracker)")
            }.body()

            val matches = response.data.toDomainList()
                .filter { it.status == com.valoranttracker.shared.domain.model.MatchStatus.UPCOMING }
                .filter { match ->
                    match.team1.id == teamId || match.team2.id == teamId
                }
                .sortedBy { it.timestamp }

            Result.success(matches)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getResults(teamId: String = "7967", limit: Int = 10): Result<List<CompletedMatch>> {
        return try {
            val response: ResultsResponse = httpClient.get("$baseUrl/api/v1/results") {
                header("User-Agent", "ValorantWidget/1.0 (+https://github.com/cesumilo/mandatory-tracker)")
            }.body()

            val matches = response.data.toCompletedDomainList()
                .filter { match ->
                    match.team1.id == teamId || match.team2.id == teamId
                }
                .take(limit)

            Result.success(matches)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTeamById(teamId: String): Result<UpcomingMatch> {
        return try {
            val matches = getMatches(teamId).getOrThrow()
            matches.firstOrNull()?.let {
                Result.success(it)
            } ?: Result.failure(NoSuchElementException("No upcoming match found for team $teamId"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}