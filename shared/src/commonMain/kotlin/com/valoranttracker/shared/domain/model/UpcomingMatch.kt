package com.valoranttracker.shared.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class CachedMatches(
    val teamId: String,
    val matches: List<UpcomingMatch>,
    val fetchedAtEpochSeconds: Long,
    val etag: String? = null,
    val lastModifiedEpochSeconds: Long? = null,
    val consecutiveFailures: Int = 0,
)

@Serializable
data class UpcomingMatch(
    val id: String,
    val team1: TeamRef,
    val team2: TeamRef,
    val eventName: String,
    val tournament: String,
    val status: MatchStatus,
    val scheduledAtEpochSeconds: Long? = null,
    val timestamp: Long,
    val rawTimeLabel: String,
    val matchImageUrl: String?,
)

@Serializable
data class TeamRef(
    val id: String,
    val name: String,
    val logoUrl: String?,
    val country: String?,
    val score: String?,
)

@Serializable
enum class MatchStatus {
    LIVE,
    UPCOMING,
    COMPLETED,
    UNKNOWN
}

fun UpcomingMatch.opponentFor(teamId: String): TeamRef {
    return if (team1.id == teamId) team2 else team1
}