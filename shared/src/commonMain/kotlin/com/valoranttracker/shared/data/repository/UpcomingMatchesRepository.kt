package com.valoranttracker.shared.data.repository

import com.valoranttracker.shared.data.api.VlrApi
import com.valoranttracker.shared.data.cache.MatchesCache
import com.valoranttracker.shared.domain.model.CachedMatches
import com.valoranttracker.shared.domain.model.RefreshPolicy
import com.valoranttracker.shared.domain.model.UpcomingMatch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class UpcomingMatchesRepository(
    private val api: VlrApi,
    private val cache: MatchesCache,
    private val teamId: String = "7967",
) {
    private val _matches = MutableStateFlow<List<UpcomingMatch>>(emptyList())
    val matches: StateFlow<List<UpcomingMatch>> = _matches.asStateFlow()

    private val _refreshState = MutableStateFlow(RefreshState())
    val refreshState: StateFlow<RefreshState> = _refreshState.asStateFlow()

    private var consecutiveFailures = 0
    private var lastRefreshed: Instant? = null

    data class RefreshState(
        val isRefreshing: Boolean = false,
        val lastError: String? = null,
        val lastRefreshedEpoch: Long? = null,
        val policy: RefreshPolicy = RefreshPolicy.SERVE_FORCE_REFRESH,
    )

    suspend fun initialize() {
        val cached = cache.get()
        if (cached != null && cached.teamId == teamId) {
            _matches.value = cached.matches
            consecutiveFailures = cached.consecutiveFailures
            lastRefreshed = Instant.fromEpochSeconds(cached.fetchedAtEpochSeconds)
            _refreshState.value = _refreshState.value.copy(lastRefreshedEpoch = cached.fetchedAtEpochSeconds)
        }
    }

    suspend fun syncIfNeeded(): List<UpcomingMatch> {
        val now = Clock.System.now()
        val policy =
            if (lastRefreshed != null) {
                calculateRefreshPolicy(lastRefreshed!!, consecutiveFailures, now)
            } else {
                RefreshPolicy.SERVE_FORCE_REFRESH
            }

        _refreshState.value = _refreshState.value.copy(policy = policy)

        return when (policy) {
            RefreshPolicy.SKIP_FRESH -> _matches.value
            RefreshPolicy.CIRCUIT_BREAKER_PAUSE -> _matches.value
            else -> performSync(policy, now)
        }
    }

    private suspend fun performSync(
        policy: RefreshPolicy,
        now: Instant,
    ): List<UpcomingMatch> {
        _refreshState.value = _refreshState.value.copy(isRefreshing = true, lastError = null)

        val result = api.getMatches(teamId)

        return result.fold(
            onSuccess = { fetchedMatches ->
                consecutiveFailures = 0
                val cached =
                    CachedMatches(
                        teamId = teamId,
                        matches = fetchedMatches,
                        fetchedAtEpochSeconds = now.epochSeconds,
                        consecutiveFailures = 0,
                    )
                cache.save(cached)
                _matches.value = fetchedMatches
                lastRefreshed = now
                _refreshState.value =
                    _refreshState.value.copy(
                        isRefreshing = false,
                        lastRefreshedEpoch = now.epochSeconds,
                    )
                fetchedMatches
            },
            onFailure = { error ->
                consecutiveFailures++
                _refreshState.value = _refreshState.value.copy(isRefreshing = false, lastError = error.message)
                _matches.value
            },
        )
    }

    suspend fun getCached(): List<UpcomingMatch> = _matches.value

    suspend fun forceRefresh(): List<UpcomingMatch> = performSync(RefreshPolicy.SERVE_FORCE_REFRESH, Clock.System.now())

    private fun calculateRefreshPolicy(
        fetchedAt: Instant,
        failures: Int,
        now: Instant,
    ): RefreshPolicy {
        val cacheAgeMs = now.toEpochMilliseconds() - fetchedAt.toEpochMilliseconds()
        val cacheAgeMinutes = cacheAgeMs / 60000

        if (failures >= 5) {
            return RefreshPolicy.CIRCUIT_BREAKER_PAUSE
        }

        return when {
            cacheAgeMinutes < 30 -> RefreshPolicy.SKIP_FRESH
            cacheAgeMinutes < 360 -> RefreshPolicy.SERVE_BACKGROUND
            else -> RefreshPolicy.SERVE_FORCE_REFRESH
        }
    }

    fun nextRefreshDelayMinutes(policy: RefreshPolicy): Long {
        val jitter = (-2..2).random()
        val base =
            when (policy) {
                RefreshPolicy.SKIP_FRESH -> 30L
                RefreshPolicy.SERVE_BACKGROUND -> 60L
                RefreshPolicy.SERVE_FORCE_REFRESH -> 60L
                RefreshPolicy.TIGHTEN_15MIN -> 15L
                RefreshPolicy.LOOSEN_6H -> 360L
                RefreshPolicy.CIRCUIT_BREAKER_PAUSE -> 60L
            }
        return base + jitter
    }
}
