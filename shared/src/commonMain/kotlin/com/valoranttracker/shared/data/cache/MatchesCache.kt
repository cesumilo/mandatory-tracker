package com.valoranttracker.shared.data.cache

import com.valoranttracker.shared.domain.model.CachedMatches

interface MatchesCache {
    suspend fun get(): CachedMatches?

    suspend fun save(matches: CachedMatches)

    suspend fun clear()
}

fun createInMemoryCache(): MatchesCache =
    object : MatchesCache {
        private var data: CachedMatches? = null

        override suspend fun get(): CachedMatches? = data

        override suspend fun save(matches: CachedMatches) {
            data = matches
        }

        override suspend fun clear() {
            data = null
        }
    }
