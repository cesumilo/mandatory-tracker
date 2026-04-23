package com.valoranttracker.shared.data.dto

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.valoranttracker.shared.domain.model.MatchStatus

@Serializable
data class MatchDto(
    @SerialName("id") val id: String = "",
    @SerialName("teams") val teams: List<TeamDto> = emptyList(),
    @SerialName("status") val status: String = "",
    @SerialName("event") val event: String = "",
    @SerialName("tournament") val tournament: String = "",
    @SerialName("img") val img: String? = null,
    @SerialName("in") val timeLabel: String = "",
    @SerialName("timestamp") val timestamp: Long = 0L,
    @SerialName("utcDate") val utcDate: String? = null,
    @SerialName("utc") val utc: String? = null,
)

@Serializable
data class TeamDto(
    @SerialName("id") val id: String? = null,
    @SerialName("name") val name: String = "",
    @SerialName("country") val country: String? = null,
    @SerialName("score") val score: String? = null,
    @SerialName("logo") val logo: String? = null,
)

@Serializable
data class MatchesResponse(
    @SerialName("status") val status: String = "",
    @SerialName("size") val size: Int = 0,
    @SerialName("data") val data: List<MatchDto> = emptyList(),
)

fun MatchDto.toDomain(): com.valoranttracker.shared.domain.model.UpcomingMatch {
    val domainStatus = when (status.lowercase()) {
        "live" -> MatchStatus.LIVE
        "upcoming" -> MatchStatus.UPCOMING
        "completed", "finished" -> MatchStatus.COMPLETED
        else -> MatchStatus.UNKNOWN
    }

    val domainTimestamp = if (timestamp > 0) timestamp else 0L
    val domainInstant = if (domainTimestamp > 0) {
        kotlinx.datetime.Instant.fromEpochSeconds(domainTimestamp)
    } else null

    val team1Dto = teams.getOrNull(0)
    val team2Dto = teams.getOrNull(1)

    return com.valoranttracker.shared.domain.model.UpcomingMatch(
        id = id,
        team1 = com.valoranttracker.shared.domain.model.TeamRef(
            id = team1Dto?.id ?: "",
            name = team1Dto?.name ?: "",
            logoUrl = team1Dto?.logo,
            country = team1Dto?.country,
            score = team1Dto?.score,
        ),
        team2 = com.valoranttracker.shared.domain.model.TeamRef(
            id = team2Dto?.id ?: "",
            name = team2Dto?.name ?: "",
            logoUrl = team2Dto?.logo,
            country = team2Dto?.country,
            score = team2Dto?.score,
        ),
        eventName = event,
        tournament = tournament,
        status = domainStatus,
        scheduledAt = domainInstant,
        timestamp = domainTimestamp,
        rawTimeLabel = timeLabel,
        matchImageUrl = img,
    )
}

fun List<MatchDto>.toDomainList(): List<com.valoranttracker.shared.domain.model.UpcomingMatch> {
    return this.mapNotNull { dto ->
        try {
            dto.toDomain()
        } catch (e: Exception) {
            null
        }
    }
}