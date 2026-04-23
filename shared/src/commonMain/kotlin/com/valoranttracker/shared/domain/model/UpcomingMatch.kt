package com.valoranttracker.shared.domain.model

import kotlinx.datetime.Instant

data class TeamRef(
    val id: String,
    val name: String,
    val logoUrl: String?,
    val country: String?,
    val score: String?,
)

data class UpcomingMatch(
    val id: String,
    val team1: TeamRef,
    val team2: TeamRef,
    val eventName: String,
    val tournament: String,
    val status: MatchStatus,
    val scheduledAt: Instant?,
    val timestamp: Long,
    val rawTimeLabel: String,
    val matchImageUrl: String?,
)

enum class MatchStatus {
    LIVE,
    UPCOMING,
    COMPLETED,
    UNKNOWN
}

fun UpcomingMatch.opponentFor(teamId: String): TeamRef {
    return if (team1.id == teamId) team2 else team1
}